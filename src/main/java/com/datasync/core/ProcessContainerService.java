package com.datasync.core;

import com.datasync.model.ProgramConfig;
import com.datasync.util.ConfigUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序容器服务：统一管理外部进程的生命周期（启动/停止/重启）与输出日志缓冲。
 *
 * <p>核心设计：进程会话挂在本服务单例上，对话框只是视图——关闭对话框进程继续后台运行，
 * 重新打开可恢复状态与日志；应用退出时由 ShutdownHook 调用 {@link #stopAll()} 统一清理。</p>
 *
 * <p>线程约定：{@link #start}/{@link #stop}/{@link #restart} 均为阻塞方法（stop 最多等待约 3 秒），
 * 禁止在 EDT 直接调用；输出回调与状态回调在后台线程触发，UI 侧需自行切换回事件分发线程。</p>
 */
public class ProcessContainerService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessContainerService.class);

    private static final ProcessContainerService INSTANCE = new ProcessContainerService();

    /**
     * 单个进程会话的日志缓冲上限（行），超出丢弃最旧行，防止长驻进程 OOM
     */
    private static final int MAX_LOG_LINES = 2000;

    /**
     * 优雅停止等待时长：destroy() 后等待进程自行退出，超时则 destroyForcibly()
     */
    private static final long GRACEFUL_STOP_TIMEOUT_MS = 3_000;

    /**
     * 子进程输出编码自动探测的回退基准：Windows 控制台程序通常为 GBK，优先取 JVM 的 native.encoding；
     * 探测时开头字节能通过 UTF-8 严格校验则改用 UTF-8
     */
    private static final Charset OUTPUT_CHARSET = resolveOutputCharset();

    /**
     * 当前系统是否 Windows（决定 JDK/JRE 目录下的 java 可执行文件名）
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /**
     * 应用退出时是否停止运行中的程序：true 由 ShutdownHook 调用 {@link #stopAll()} 清理；
     * false 表示用户选择保持程序后台运行，退出时不做任何干预
     * -- GETTER --
     *  应用退出时是否停止运行中的程序（默认 true）；主窗口退出弹窗中选择「保持程序运行」时置为 false
     
     */
    @Setter
    @Getter
    private volatile boolean stopProcessesOnExit = true;

    private final Map<Long, ProcessSession> sessions = new ConcurrentHashMap<>();

    private ProcessContainerService() {
    }

    public static ProcessContainerService getInstance() {
        return INSTANCE;
    }

    /**
     * 进程会话：封装进程、配置、有界日志缓冲与 UI 注册的回调。
     * 会话在进程退出后保留（日志与退出码供对话框重开时恢复），下次启动复用并追加日志。
     */
    private static final class ProcessSession {

        final ProgramConfig config;

        final ArrayDeque<String> logBuffer = new ArrayDeque<>();

        volatile Process process;

        volatile long pid = -1;

        volatile Integer exitCode;

        volatile Consumer<String> outputConsumer;

        volatile Runnable stateChangedCallback;

        ProcessSession(ProgramConfig config) {
            this.config = config;
        }

        boolean isAlive() {
            Process p = process;
            return p != null && p.isAlive();
        }
    }

    // ────────── 启动 / 停止 / 重启 ──────────

    /**
     * 启动指定程序配置对应的进程，并注册输出与状态回调。
     *
     * @param config           已保存的程序配置（id 不能为空）
     * @param onOutput         输出行回调（后台线程触发，可为 null）
     * @param onStateChanged   进程退出等状态变化回调（后台线程触发，可为 null）
     * @return 启动成功返回 null，失败返回错误提示（可直接展示给用户）
     */
    public String start(ProgramConfig config, Consumer<String> onOutput, Runnable onStateChanged) {
        if (config == null || config.getId() == null) {
            return "程序配置尚未保存，请先保存";
        }
        if (config.getProgramPath() == null || config.getProgramPath().isBlank()) {
            return "程序/命令不能为空";
        }
        long id = config.getId();
        ProcessSession existing = sessions.get(id);
        if (existing != null && existing.isAlive()) {
            return "程序已在运行中，请先停止";
        }
        // 上次启动的进程仍在后台运行（应用重启后的接管场景），阻止重复启动
        Long lastPid = config.getLastPid();
        if (lastPid != null && isPidAlive(lastPid)) {
            return "程序已在后台运行中（PID=" + lastPid + "），请先停止该进程";
        }

        String jreError = validateJrePath(config);
        if (jreError != null) {
            return jreError;
        }

        List<String> command = buildCommand(config);
        ProcessBuilder pb = new ProcessBuilder(command);
        // 合并 stderr 到 stdout，UI 只需消费一个流
        pb.redirectErrorStream(true);
        if (config.getWorkDir() != null && !config.getWorkDir().isBlank()) {
            File dir = new File(config.getWorkDir());
            if (dir.isDirectory()) {
                pb.directory(dir);
            }
        }

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            logger.error("[ProgramContainer] 启动程序失败: {}", config.getName(), e);
            return "启动失败: " + e.getMessage();
        }

        // 复用已有会话（保留历史日志），否则新建
        ProcessSession session = existing != null ? existing : new ProcessSession(config);
        if (existing == null) {
            sessions.put(id, session);
        }
        synchronized (session) {
            session.outputConsumer = onOutput;
            session.stateChangedCallback = onStateChanged;
            session.process = process;
            session.pid = process.pid();
            session.exitCode = null;
        }

        // 输出读取线程：必须持续读取，否则子进程输出缓冲写满会卡死
        Thread reader = new Thread(() -> readOutput(session, process), "program-container-reader-" + id);
        reader.setDaemon(true);
        reader.start();

        // 退出监听线程：触发状态回调
        Thread watcher = new Thread(() -> watchExit(session, process), "program-container-watcher-" + id);
        watcher.setDaemon(true);
        watcher.start();

        appendSystemLog(session, "[容器] 已启动 PID=" + process.pid() + ": " + String.join(" ", command));
        logger.info("[ProgramContainer] 程序已启动: {} (PID={})", config.getName(), process.pid());
        // 持久化进程号：应用重启后用于检测程序是否仍在后台运行（写入失败不影响启动）
        try {
            ConfigUtil.updateProgramPid(id, process.pid());
        } catch (Exception e) {
            logger.warn("[ProgramContainer] 记录进程号失败: {}", config.getName(), e);
        }
        return null;
    }

    /**
     * 停止运行中的指定进程：destroy() → 等待 3 秒 → 仍未退出则 destroyForcibly()。
     * 阻塞方法，禁止在 EDT 调用；进程退出后由监听线程触发状态回调。
     */
    public void stop(long id) {
        ProcessSession session = sessions.get(id);
        if (session == null) {
            return;
        }
        Process process = session.process;
        if (process == null || !process.isAlive()) {
            return;
        }
        destroyProcess(process);
    }

    /**
     * 停止上次启动、应用重启后仍在后台运行的外部进程（仅有 PID、无 Process 句柄）：
     * 通过 ProcessHandle destroy → 等待 3 秒 → 仍未退出则 destroyForcibly。 
     * 进程已不存在返回 false；停止成功后清空持久化的进程号。
     * 阻塞方法，禁止在 EDT 调用
     */
    public boolean stopExternalProcess(long pid) {
        ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
        if (handle == null || !handle.isAlive()) {
            return false;
        }
        handle.destroy();
        try {
            handle.onExit().get(GRACEFUL_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("[ProgramContainer] 外部进程 {} 优雅停止超时，强制结束", pid);
            handle.destroyForcibly();
            try {
                handle.onExit().get(2, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                logger.warn("[ProgramContainer] 外部进程 {} 强制结束后仍未退出", pid);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                logger.warn("[ProgramContainer] 等待外部进程 {} 退出异常", pid, ex);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.destroyForcibly();
        } catch (ExecutionException e) {
            logger.warn("[ProgramContainer] 等待外部进程 {} 退出异常，尝试强制结束", pid, e);
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        }
        sessions.values().stream()
                .filter(s -> s.config.getLastPid() != null && s.config.getLastPid() == pid)
                .forEach(s -> {
                    try {
                        ConfigUtil.updateProgramPid(s.config.getId(), null);
                    } catch (Exception ex) {
                        logger.warn("[ProgramContainer] 清空进程号失败: {}", s.config.getName(), ex);
                    }
                });
        return true;
    }

    /**
     * destroy → 优雅等待 → 超时强制结束
     */
    private void destroyProcess(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(GRACEFUL_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                logger.warn("[ProgramContainer] 进程 {} 优雅停止超时，强制结束", process.pid());
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /**
     * 重启指定程序：先停止（阻塞至退出），再按会话内保存的配置与回调重新启动。
     *
     * @return 重启成功返回 null，失败返回错误提示
     */
    public String restart(long id) {
        ProcessSession session = sessions.get(id);
        if (session == null) {
            return "程序尚未启动过，无法重启";
        }
        Consumer<String> outputConsumer = session.outputConsumer;
        Runnable stateChangedCallback = session.stateChangedCallback;
        stop(id);
        return start(session.config, outputConsumer, stateChangedCallback);
    }

    /**
     * 应用退出时按需清理全部进程：先对所有运行中进程并行 destroy，再统一等待，超时强制结束
     */
    public void stopAll() {
        List<Process> alive = new ArrayList<>();
        for (ProcessSession session : sessions.values()) {
            Process p = session.process;
            if (p != null && p.isAlive()) {
                p.destroy();
                alive.add(p);
            }
        }
        long deadline = System.currentTimeMillis() + GRACEFUL_STOP_TIMEOUT_MS;
        for (Process p : alive) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining > 0) {
                    p.waitFor(remaining, TimeUnit.MILLISECONDS);
                }
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
        if (!alive.isEmpty()) {
            logger.info("[ProgramContainer] 应用退出，已清理 {} 个运行中的进程", alive.size());
        }
    }

    /**
     * 判断指定进程号当前是否存活（用于检测上次启动、应用重启后仍在后台运行的程序）
     */
    public static boolean isPidAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    // ────────── 状态查询 ──────────

    public boolean isRunning(long id) {
        ProcessSession session = sessions.get(id);
        return session != null && session.isAlive();
    }

    /**
     * 是否存在运行中的程序（主窗口退出弹窗判断用）
     */
    public boolean hasRunningProcesses() {
        return countRunningProcesses() > 0;
    }

    /**
     * 运行中的程序数量（退出弹窗提示用）
     */
    public long countRunningProcesses() {
        return sessions.values().stream().filter(ProcessSession::isAlive).count();
    }
    
    /**
     * 获取最近一次运行的退出码，运行中或从未运行返回 null
     */
    public Integer getExitCode(long id) {
        ProcessSession session = sessions.get(id);
        return session != null ? session.exitCode : null;
    }

    /**
     * 获取最近一次运行的进程号，从未运行返回 -1
     */
    public long getPid(long id) {
        ProcessSession session = sessions.get(id);
        return session != null ? session.pid : -1;
    }

    /**
     * 获取日志缓冲快照（对话框重开或切换选中项时恢复历史日志）
     */
    public List<String> getLogBuffer(long id) {
        ProcessSession session = sessions.get(id);
        if (session == null) {
            return List.of();
        }
        synchronized (session.logBuffer) {
            return new ArrayList<>(session.logBuffer);
        }
    }

    // ────────── 回调与日志缓冲管理 ──────────

    /**
     * 注册/替换指定程序的输出与状态回调（对话框切换选中项或重新打开时调用）
     */
    public void attachListener(long id, Consumer<String> onOutput, Runnable onStateChanged) {
        ProcessSession session = sessions.get(id);
        if (session != null) {
            session.outputConsumer = onOutput;
            session.stateChangedCallback = onStateChanged;
        }
    }

    /**
     * 卸载指定程序的回调（进程继续后台运行，仅断开视图通知）
     */
    public void detachListener(long id) {
        ProcessSession session = sessions.get(id);
        if (session != null) {
            session.outputConsumer = null;
            session.stateChangedCallback = null;
        }
    }

    /**
     * 清空指定程序的日志缓冲（配合界面「清空」按钮）
     */
    public void clearLog(long id) {
        ProcessSession session = sessions.get(id);
        if (session != null) {
            synchronized (session.logBuffer) {
                session.logBuffer.clear();
            }
        }
    }

    /**
     * 删除程序配置时调用：先停止运行中的进程，再移除会话与日志缓冲
     */
    public void removeSession(long id) {
        stop(id);
        sessions.remove(id);
    }

    // ────────── 内部实现 ──────────

    /**
     * 组装启动命令。「程序/命令」支持两种写法：
     * <ul>
     *   <li>单个程序/脚本：可执行文件、bat/sh 脚本路径（可含空格，以文件存在性判定），或 PATH 中命令名</li>
     *   <li>完整命令行：如 {@code java -jar app.jar}，首词为程序，其余作为前置参数</li>
     * </ul>
     * Windows 下 .bat/.cmd 脚本需经 cmd.exe /c 解释执行；「参数」与命令行中双引号包裹的段视为整体
     * （支持含空格的类路径，如 -cp "lib/*;app.jar"）；显式参数字段追加在末尾。
     */
    private List<String> buildCommand(ProgramConfig config) {
        String field = config.getProgramPath().trim();
        String program = stripOuterQuotes(field);
        List<String> leadingArgs = List.of();
        // 含空格且整体不是已存在的文件 → 按完整命令行拆分：首词为程序，其余为前置参数
        if (program.contains(" ") && !isExistingFile(program)) {
            List<String> tokens = tokenize(field);
            program = tokens.get(0);
            leadingArgs = tokens.subList(1, tokens.size());
        }
        // 配置了 JRE/JDK 路径且程序为 java/javaw 命令时，替换为指定环境下的可执行文件
        String javaExe = resolveConfiguredJavaExecutable(config, program);
        if (javaExe != null) {
            program = javaExe;
        }
        List<String> command = new ArrayList<>();
        String lower = program.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".bat") || lower.endsWith(".cmd")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(program);
        command.addAll(leadingArgs);
        String arguments = config.getArguments();
        if (arguments != null && !arguments.isBlank()) {
            command.addAll(tokenize(arguments.trim()));
        }
        return command;
    }
    
    /**
     * 判断路径是否指向已存在的文件（用于区分带空格的文件路径与完整命令行）
     */
    static boolean isExistingFile(String path) {
        return new File(path).isFile();
    }
    
    /**
     * 解析程序字段首词：整体去引号后若是完整命令行则取首词，否则返回去引号后的整体
     */
    static String extractProgramField(String field) {
        String program = stripOuterQuotes(field);
        if (program.contains(" ") && !isExistingFile(program)) {
            return tokenize(field).get(0);
        }
        return program;
    }
    
    /**
     * 判断程序名是否为纯 java/javaw 命令（无路径分隔符）；带显式路径的视为用户已指定环境，不替换
     */
    static boolean isPlainJavaCommand(String program) {
        String lower = program.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "java", "java.exe", "javaw", "javaw.exe" -> true;
            default -> false;
        };
    }
    
    /**
     * 若配置了 JRE/JDK 路径且程序为 java/javaw 命令，返回指定环境下的 java 可执行文件路径；否则返回 null
     */
    static String resolveConfiguredJavaExecutable(ProgramConfig config, String program) {
        String jrePath = config.getJrePath();
        if (jrePath == null || jrePath.isBlank() || !isPlainJavaCommand(program)) {
            return null;
        }
        return resolveJavaExecutable(jrePath);
    }
    
    /**
     * 将 JRE/JDK 路径解析为 java 可执行文件：指向文件直接使用；指向 JDK/JRE 根目录则定位 bin/java
     * （Windows 为 java.exe）。无法定位返回 null
     */
    static String resolveJavaExecutable(String jrePath) {
        String path = stripOuterQuotes(jrePath.trim());
        File file = new File(path);
        if (file.isFile()) {
            return file.getAbsolutePath();
        }
        if (file.isDirectory()) {
            File candidate = new File(file, "bin" + File.separator + (IS_WINDOWS ? "java.exe" : "java"));
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }
    
    /**
     * 启动前校验 JRE/JDK 路径：仅在程序为 java/javaw 命令且配置了路径时校验，
     * 无法定位可执行文件则返回错误提示，否则返回 null
     */
    private String validateJrePath(ProgramConfig config) {
        String jrePath = config.getJrePath();
        if (jrePath == null || jrePath.isBlank() || !isPlainJavaCommand(extractProgramField(config.getProgramPath().trim()))) {
            return null;
        }
        if (resolveJavaExecutable(jrePath) == null) {
            return "JRE/JDK 路径无效：未在 " + stripOuterQuotes(jrePath.trim()) + " 下找到 bin/java 可执行文件";
        }
        return null;
    }
    
    /**
     * 去除字符串首尾成对的双引号（用户粘贴 Windows 路径时常带引号）
     */
    static String stripOuterQuotes(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
    
    /**
     * 引号感知分词：按空白拆分，双引号内的空白不拆分且引号本身去除。 用于解析完整命令行与参数中的含空格路径，如 {@code -cp "lib/*;app.jar"}。
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * 持续按行读取子进程合并输出，写入有界缓冲并分发给 UI 回调。
     * 输出编码：显式指定则直接使用；否则用 {@link EncodingDetectReader} 自动探测——
     * ASCII 直通显示，首个非 ASCII 行做 UTF-8 严格校验（通过则按 UTF-8 解码，
     * 否则按系统编码 GBK），避免开头大量 ASCII（如启动 banner）导致 UTF-8 程序后续中文乱码
     */
    private void readOutput(ProcessSession session, Process process) {
        Charset explicit = resolveSessionCharset(session.config);
        Reader base = explicit != null
                ? new InputStreamReader(process.getInputStream(), explicit)
                : new EncodingDetectReader(process.getInputStream(), OUTPUT_CHARSET);
        try (BufferedReader reader = new BufferedReader(base)) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLog(session, line);
            }
        } catch (IOException ignored) {
            // 进程被销毁时输出流关闭，属正常退出路径
        }
    }
    
    /**
     * 解析会话输出解码编码：显式指定则使用（非法回退自动探测）；返回 null 表示自动探测
     */
    private static Charset resolveSessionCharset(ProgramConfig config) {
        String name = config.getOutputCharset();
        if (name == null || name.isBlank() || "AUTO".equalsIgnoreCase(name.trim())) {
            return null;
        }
        try {
            return Charset.forName(name.trim());
        } catch (Exception e) {
            logger.warn("[ProgramContainer] 输出编码 {} 非法，回退自动探测", name);
            return null;
        }
    }
    
    /**
     * 从缓冲字节判定输出编码：截到最后一个 ASCII 字节（避免多字节序列被截断导致误判）后做
     * UTF-8 严格校验，通过则 UTF-8，否则回退系统编码。纯 ASCII 或无 ASCII 锚点时无法可靠
     * 判定，均回退系统编码
     */
    static Charset detectCharset(byte[] bytes, Charset fallback) {
        boolean hasNonAscii = false;
        int lastAscii = -1;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] >= 0) {
                lastAscii = i;
            } else {
                hasNonAscii = true;
            }
        }
        if (!hasNonAscii || lastAscii < 0) {
            return fallback;
        }
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes, 0, lastAscii + 1));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return fallback;
        }
    }

    /**
     * 监听进程退出：记录退出码、写入系统日志行并触发状态回调。
     * 通过比对 process 引用防止与新一轮启动竞态（restart 场景）。
     */
    private void watchExit(ProcessSession session, Process process) {
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (session) {
            if (session.process == process) {
                session.exitCode = code;
            }
        }
        if (session.process == process) {
            appendSystemLog(session, "[容器] 进程已退出，退出码=" + code);
            logger.info("[ProgramContainer] 程序已退出: {} (退出码={})", session.config.getName(), code);
            // 清空持久化进程号，下次启动重新写入（restart 竞态时新进程已接管引用，不会误清）
            try {
                ConfigUtil.updateProgramPid(session.config.getId(), null);
            } catch (Exception e) {
                logger.warn("[ProgramContainer] 清空进程号失败: {}", session.config.getName(), e);
            }
            Runnable callback = session.stateChangedCallback;
            if (callback != null) {
                callback.run();
            }
        }
    }

    /**
     * 追加一行日志到有界缓冲并分发（后台读取线程调用）
     */
    private void appendLog(ProcessSession session, String line) {
        synchronized (session.logBuffer) {
            session.logBuffer.addLast(line);
            if (session.logBuffer.size() > MAX_LOG_LINES) {
                session.logBuffer.removeFirst();
            }
        }
        Consumer<String> consumer = session.outputConsumer;
        if (consumer != null) {
            consumer.accept(line);
        }
    }

    /**
     * 追加容器系统日志（启动/退出等生命周期事件）
     */
    private void appendSystemLog(ProcessSession session, String message) {
        appendLog(session, message);
    }

    private static Charset resolveOutputCharset() {
        String name = System.getProperty("native.encoding");
        if (name != null && !name.isBlank()) {
            try {
                return Charset.forName(name.trim());
            } catch (Exception ignored) {
                // 非法编码名则回退默认编码
            }
        }
        return Charset.defaultCharset();
    }
}
