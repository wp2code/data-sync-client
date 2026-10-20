package com.datasync.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datasync.model.ProgramConfig;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProcessContainerService 单元测试：进程启动/停止/重启、输出回调、退出码记录与日志缓冲上限。
 * <p>命令按操作系统自适应（Windows 用 ping/cmd，类 Unix 用 ping/seq），保证 CI 环境可运行。</p>
 * <p>服务为单例，每个用例使用独立 id，结束后统一 removeSession 清理进程。</p>
 */
@DisplayName("ProcessContainerService 程序容器服务")
class ProcessContainerServiceTest {

    private static final long TIMEOUT_MS = 20_000;

    private static final long POLL_INTERVAL_MS = 100;

    private final List<Long> usedIds = new CopyOnWriteArrayList<>();

    private final ProcessContainerService service = ProcessContainerService.getInstance();

    @AfterEach
    void cleanUpSessions() {
        for (Long id : usedIds) {
            service.removeSession(id);
        }
        usedIds.clear();
    }

    // ────────── 辅助方法 ──────────

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private ProgramConfig newConfig(long id, String programPath, String arguments) {
        usedIds.add(id);
        ProgramConfig config = new ProgramConfig("测试程序-" + id, programPath, arguments);
        config.setId(id);
        return config;
    }

    /**
     * 构造一次性 ping 命令：发 2 个包后自动退出
     */
    private ProgramConfig pingOnceConfig(long id) {
        return isWindows()
                ? newConfig(id, "ping", "-n 2 127.0.0.1")
                : newConfig(id, "ping", "-c 2 127.0.0.1");
    }

    /**
     * 构造长驻 ping 命令：发 100 个包（约 100 秒），足够测试停止逻辑
     */
    private ProgramConfig pingLongConfig(long id) {
        return isWindows()
                ? newConfig(id, "ping", "-n 100 127.0.0.1")
                : newConfig(id, "ping", "-c 100 127.0.0.1");
    }

    /**
     * 轮询等待条件成立，超时返回 false
     */
    private static boolean await(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ────────── 启动与输出 ──────────

    @Test
    @DisplayName("start：一次性命令输出逐行回调、退出码记录、状态流转正确")
    void testStartOnceCommand() {
        ProgramConfig config = pingOnceConfig(9001L);
        List<String> outputLines = new CopyOnWriteArrayList<>();
        AtomicBoolean stateChanged = new AtomicBoolean(false);

        String error = service.start(config, outputLines::add, () -> stateChanged.set(true));
        assertNull(error, "合法命令启动应返回 null");

        assertTrue(service.isRunning(config.getId()), "启动后应处于运行状态");
        assertTrue(service.getPid(config.getId()) > 0, "应记录有效进程号");

        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出并记录退出码");
        assertEquals(0, service.getExitCode(config.getId()), "正常退出的 ping 退出码应为 0");
        assertFalse(service.isRunning(config.getId()), "退出后不应处于运行状态");
        assertFalse(outputLines.isEmpty(), "应通过回调收到至少一行输出");
        assertTrue(stateChanged.get(), "进程退出应触发状态变化回调");

        List<String> buffer = service.getLogBuffer(config.getId());
        assertTrue(buffer.size() > 1, "日志缓冲应包含启动系统日志与输出内容");
        assertTrue(buffer.stream().anyMatch(line -> line.startsWith("[容器] 已启动")), "缓冲应包含启动系统日志");
        assertTrue(buffer.stream().anyMatch(line -> line.contains("退出码=0")), "缓冲应包含退出系统日志");
    }

    @Test
    @DisplayName("start：stderr 合并到 stdout 输出流（java -version 输出到 stderr）")
    void testStderrMerged() {
        String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator
                + (isWindows() ? "java.exe" : "java");
        ProgramConfig config = newConfig(9002L, javaBin, "-version");
        List<String> outputLines = new CopyOnWriteArrayList<>();

        String error = service.start(config, outputLines::add, null);
        assertNull(error, "java -version 应能正常启动");

        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
        // -version 输出到 stderr，经 redirectErrorStream 合并后应能读到版本信息
        assertTrue(outputLines.stream().anyMatch(line -> line.contains("version")), "stderr 输出应被合并读取到");
    }

    @Test
    @DisplayName("start：非法命令返回错误提示且不建立会话")
    void testStartInvalidCommand() {
        ProgramConfig config = newConfig(9003L, "definitely-not-exist-command-xyz", null);
        String error = service.start(config, null, null);
        assertNotNull(error, "非法命令应返回错误信息");
        assertFalse(error.isBlank(), "错误信息不应为空");
        assertFalse(service.isRunning(config.getId()), "启动失败不应处于运行状态");
    }
    
    @Test
    @DisplayName("start：程序/命令填完整命令行（如 java -jar app.jar）自动拆分首词为程序")
    void testStartFullCommandLine() {
        String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator
                + (isWindows() ? "java.exe" : "java");
        // 模拟用户在程序/命令字段填入完整命令行：程序与参数写在一起
        ProgramConfig config = newConfig(9010L, javaBin + " -version", null);
        List<String> outputLines = new CopyOnWriteArrayList<>();
        
        String error = service.start(config, outputLines::add, null);
        assertNull(error, "完整命令行应自动拆分并成功启动");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
        assertTrue(outputLines.stream().anyMatch(line -> line.contains("version")), "拆分后参数应生效并收到输出");
    }
    
    @Test
    @DisplayName("start：程序/命令填带首尾引号的路径自动去引号")
    void testStartQuotedPath() {
        String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator
                + (isWindows() ? "java.exe" : "java");
        ProgramConfig config = newConfig(9011L, "\"" + javaBin + "\"", "-version");
        
        String error = service.start(config, null, null);
        assertNull(error, "带引号路径去引号后应能正常启动");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
    }
    
    @Test
    @DisplayName("start：参数中双引号包裹的段去引号后作为整体传递")
    void testStartQuotedArgument() {
        String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator
                + (isWindows() ? "java.exe" : "java");
        // 引号应被去除后传给 java；若引号被原样传入，java 会报 Unrecognized option 而非正常退出
        ProgramConfig config = newConfig(9012L, javaBin, "\"-version\"");
        
        String error = service.start(config, null, null);
        assertNull(error, "带引号参数应能正常启动");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
        assertEquals(0, service.getExitCode(config.getId()), "引号去除后 -version 应被 java 正常识别");
    }
    
    // ────────── 命令分词 ──────────
    
    @Test
    @DisplayName("tokenize：按空白分词，双引号内空白不拆分且引号去除")
    void testTokenize() {
        assertEquals(List.of("java", "-jar", "app.jar"), ProcessContainerService.tokenize("java -jar app.jar"));
        assertEquals(List.of("-cp", "lib/*;app.jar", "Main"), ProcessContainerService.tokenize("-cp \"lib/*;app.jar\" Main"));
        assertEquals(List.of("C:\\Program Files\\app.exe", "-x"),
                ProcessContainerService.tokenize("\"C:\\Program Files\\app.exe\" -x"));
        assertTrue(ProcessContainerService.tokenize("   ").isEmpty(), "纯空白应分词为空");
    }
    
    @Test
    @DisplayName("stripOuterQuotes：仅去除首尾成对引号")
    void testStripOuterQuotes() {
        assertEquals("C:\\my app\\run.exe", ProcessContainerService.stripOuterQuotes("\"C:\\my app\\run.exe\""));
        assertEquals("java -version", ProcessContainerService.stripOuterQuotes("java -version"));
        assertEquals("\"", ProcessContainerService.stripOuterQuotes("\""));
    }

    @Test
    @DisplayName("start：重复启动运行中的程序返回错误")
    void testStartDuplicate() {
        ProgramConfig config = pingLongConfig(9004L);
        String firstError = service.start(config, null, null);
        assertNull(firstError, "首次启动应成功");
        assertTrue(await(() -> service.isRunning(config.getId())), "应进入运行状态");

        String secondError = service.start(config, null, null);
        assertNotNull(secondError, "运行中重复启动应返回错误");
    }

    // ────────── 停止与重启 ──────────

    @Test
    @DisplayName("stop：长驻进程被停止后状态翻转并记录退出码")
    void testStopRunningProcess() {
        ProgramConfig config = pingLongConfig(9005L);
        assertNull(service.start(config, null, null), "启动应成功");
        assertTrue(await(() -> service.isRunning(config.getId())), "应进入运行状态");

        service.stop(config.getId());
        assertFalse(service.isRunning(config.getId()), "stop 返回后进程应已终止");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "停止后应记录退出码");
    }

    @Test
    @DisplayName("restart：重启后进程号变化且仍在运行")
    void testRestart() {
        ProgramConfig config = pingLongConfig(9006L);
        assertNull(service.start(config, null, null), "启动应成功");
        assertTrue(await(() -> service.isRunning(config.getId())), "应进入运行状态");
        long oldPid = service.getPid(config.getId());

        String error = service.restart(config.getId());
        assertNull(error, "重启应成功");
        assertTrue(service.isRunning(config.getId()), "重启后应处于运行状态");
        assertTrue(service.getPid(config.getId()) > 0, "重启后应记录新进程号");
        assertTrue(service.getPid(config.getId()) != oldPid, "重启后进程号应发生变化");
    }

    @Test
    @DisplayName("restart：从未启动过的程序返回错误提示")
    void testRestartNeverStarted() {
        String error = service.restart(9999L);
        assertNotNull(error, "从未启动过的程序重启应返回错误");
    }

    // ────────── 日志缓冲上限 ──────────

    @Test
    @DisplayName("日志缓冲上限：超量输出只保留最近 2000 行")
    void testLogBufferBound() {
        // Windows: cmd for 循环快速输出 3000 行；类 Unix: seq 输出 3000 行
        ProgramConfig config = isWindows()
                ? newConfig(9007L, "cmd.exe", "/c for /l %i in (1,1,3000) do @echo line%i")
                : newConfig(9007L, "seq", "1 3000");

        assertNull(service.start(config, null, null), "启动应成功");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");

        List<String> buffer = service.getLogBuffer(config.getId());
        assertEquals(2000, buffer.size(), "输出 3000+ 行后缓冲应精确保持在 2000 行上限");
        assertTrue(buffer.contains("line3000"), "最新输出应保留在缓冲中");
        assertFalse(buffer.contains("line2"), "最旧的输出行应被丢弃");
        assertFalse(buffer.contains("line1"), "最旧的输出行应被丢弃");
    }

    // ────────── 会话与回调管理 ──────────

    @Test
    @DisplayName("attach/detach：切换监听不影响进程后台运行")
    void testAttachDetachListener() {
        ProgramConfig config = pingLongConfig(9008L);
        assertNull(service.start(config, null, null), "启动应成功");
        assertTrue(await(() -> service.isRunning(config.getId())), "应进入运行状态");

        AtomicReference<String> received = new AtomicReference<>();
        service.attachListener(config.getId(), received::set, null);
        service.detachListener(config.getId());
        // detach 后卸载回调，进程应继续运行
        assertTrue(service.isRunning(config.getId()), "卸载回调后进程应继续后台运行");
    }

    @Test
    @DisplayName("clearLog：清空后缓冲为空且进程不受影响")
    void testClearLog() {
        ProgramConfig config = pingOnceConfig(9009L);
        assertNull(service.start(config, null, null), "启动应成功");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
        assertFalse(service.getLogBuffer(config.getId()).isEmpty(), "退出后缓冲应有历史日志");

        service.clearLog(config.getId());
        assertTrue(service.getLogBuffer(config.getId()).isEmpty(), "清空后缓冲应为空");
    }

    // ────────── JRE/JDK 路径 ──────────

    /**
     * 当前 JVM 对应的 java 可执行文件绝对路径
     */
    private static String currentJavaExecutable() {
        return System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator
                + (isWindows() ? "java.exe" : "java");
    }

    @Test
    @DisplayName("start：配置 JRE/JDK 路径后 java 命令使用该环境下的可执行文件")
    void testStartWithJrePath() {
        String expectedExe = currentJavaExecutable();
        ProgramConfig config = newConfig(9013L, "java", "-version");
        config.setJrePath(System.getProperty("java.home"));
        List<String> outputLines = new CopyOnWriteArrayList<>();

        String error = service.start(config, outputLines::add, null);
        assertNull(error, "配置有效 JRE 路径应能正常启动");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
        assertEquals(0, service.getExitCode(config.getId()), "使用指定 JRE 运行 -version 应正常退出");
        assertTrue(outputLines.stream().anyMatch(line -> line.contains("version")), "应收到 java -version 输出");
        assertTrue(service.getLogBuffer(config.getId()).stream()
                        .anyMatch(line -> line.startsWith("[容器] 已启动") && line.contains(expectedExe)),
                "启动日志应包含指定 JRE 下的 java 可执行文件路径");
    }

    @Test
    @DisplayName("resolveJavaExecutable：目录定位 bin/java、文件直接使用、无效路径返回 null")
    void testResolveJavaExecutable() {
        String expected = currentJavaExecutable();
        String javaHome = System.getProperty("java.home");

        assertEquals(expected, ProcessContainerService.resolveJavaExecutable(javaHome),
                "指向 JDK/JRE 根目录应定位到 bin/java 可执行文件");
        assertEquals(expected, ProcessContainerService.resolveJavaExecutable("\"" + javaHome + "\""),
                "带引号路径应先去引号再解析");
        assertEquals(expected, ProcessContainerService.resolveJavaExecutable(expected),
                "直接指向 java 可执行文件应返回绝对路径");
        assertNull(ProcessContainerService.resolveJavaExecutable("definitely-not-a-jre-dir-xyz"),
                "不存在的路径应返回 null");
    }

    @Test
    @DisplayName("isPlainJavaCommand：仅整词匹配 java/javaw 命令，带路径的不匹配")
    void testIsPlainJavaCommand() {
        assertTrue(ProcessContainerService.isPlainJavaCommand("java"), "裸 java 命令应匹配");
        assertTrue(ProcessContainerService.isPlainJavaCommand("JAVA.EXE"), "大小写不敏感");
        assertTrue(ProcessContainerService.isPlainJavaCommand("javaw"), "javaw 命令应匹配");
        assertFalse(ProcessContainerService.isPlainJavaCommand(currentJavaExecutable()),
                "带路径的 java 不视为纯命令名");
        assertFalse(ProcessContainerService.isPlainJavaCommand("javascript"), "非整词不匹配");
    }

    @Test
    @DisplayName("start：无效 JRE 路径返回错误且不创建进程")
    void testStartInvalidJrePath() {
        ProgramConfig config = newConfig(9014L, "java", "-version");
        config.setJrePath("definitely-not-a-jre-dir-xyz");

        String error = service.start(config, null, null);
        assertNotNull(error, "无效 JRE 路径应返回错误信息");
        assertTrue(error.contains("JRE/JDK"), "错误信息应提示 JRE/JDK 路径无效");
        assertFalse(service.isRunning(config.getId()), "校验失败不应启动进程");
        assertEquals(-1, service.getPid(config.getId()), "校验失败不应创建进程");
    }

    @Test
    @DisplayName("start：非 java 程序忽略 JRE 路径配置照常启动")
    void testJrePathIgnoredForNonJava() {
        ProgramConfig config = pingOnceConfig(9015L);
        config.setJrePath("definitely-not-a-jre-dir-xyz");

        String error = service.start(config, null, null);
        assertNull(error, "非 java 程序应忽略无效 JRE 路径并正常启动");
        assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
    }

    // ────────── 应用退出清理 ──────────

    @Test
    @DisplayName("countRunningProcesses：运行中计数随启动与停止变化")
    void testCountRunningProcesses() {
        assertEquals(0, service.countRunningProcesses(), "无会话时运行计数应为 0");

        ProgramConfig config = pingLongConfig(9016L);
        assertNull(service.start(config, null, null), "启动应成功");
        assertTrue(await(() -> service.isRunning(config.getId())), "应进入运行状态");
        assertTrue(service.hasRunningProcesses(), "存在运行中进程时 hasRunningProcesses 应为 true");
        assertEquals(1, service.countRunningProcesses(), "运行中进程计数应为 1");

        service.stop(config.getId());
        assertTrue(await(() -> service.countRunningProcesses() == 0),
                "停止后运行计数应回落到 0");
        assertFalse(service.hasRunningProcesses(), "全部停止后 hasRunningProcesses 应为 false");
    }

    @Test
    @DisplayName("stopProcessesOnExit：默认开启，可切换为保持后台运行")
    void testStopProcessesOnExitFlag() {
        assertTrue(service.isStopProcessesOnExit(), "默认应在退出时停止程序");
        service.setStopProcessesOnExit(false);
        assertFalse(service.isStopProcessesOnExit(), "选择保持运行后标志应为 false");
        service.setStopProcessesOnExit(true);
        assertTrue(service.isStopProcessesOnExit(), "恢复默认后标志应为 true");
    }

    // ────────── 进程号持久化与接管 ──────────

    @Test
    @DisplayName("isPidAlive：当前进程存活，不存在的进程号返回 false")
    void testIsPidAlive() {
        assertTrue(ProcessContainerService.isPidAlive(ProcessHandle.current().pid()),
                "当前测试 JVM 进程应判定为存活");
        assertFalse(ProcessContainerService.isPidAlive(999_999_999L),
                "不存在的进程号应判定为不存活");
    }

    @Test
    @DisplayName("start：上次启动的进程仍在后台运行时阻止重复启动")
    void testStartBlockedByAliveExternalPid() {
        // 用当前测试 JVM 的 PID 模拟「应用重启后上次启动的进程仍在运行」的接管场景
        ProgramConfig config = newConfig(9017L, "ping", "-n 2 127.0.0.1");
        config.setLastPid(ProcessHandle.current().pid());

        String error = service.start(config, null, null);
        assertNotNull(error, "接管运行中的程序不应允许重复启动");
        assertTrue(error.contains("PID=" + ProcessHandle.current().pid()), "错误信息应包含存活进程号");
        assertFalse(service.isRunning(config.getId()), "不应建立会话");
    }

    @Test
    @DisplayName("stopExternalProcess：停止外部进程后返回 true，再次停止返回 false")
    void testStopExternalProcess() throws Exception {
        // 直接拉起一个不经服务管理的长驻进程，模拟应用重启后的外部运行进程
        Process external = isWindows()
                ? new ProcessBuilder("ping", "-n", "100", "127.0.0.1").start()
                : new ProcessBuilder("ping", "-c", "100", "127.0.0.1").start();
        long pid = external.pid();
        assertTrue(external.isAlive(), "外部进程应先处于运行状态");

        assertTrue(service.stopExternalProcess(pid), "存活的外部进程应停止成功");
        assertTrue(await(() -> !external.isAlive()), "停止后外部进程应退出");
        assertFalse(service.stopExternalProcess(pid), "进程已不存在时应返回 false");
    }

    // ────────── 输出编码自动探测 ──────────

    @Test
    @DisplayName("detectCharset：UTF-8 判定为 UTF-8，GBK 回退系统编码，纯 ASCII 回退")
    void testDetectCharset() throws Exception {
        java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
        // 带 ASCII 前缀模拟真实日志行，保证有无 ASCII 锚点两种情形都被覆盖
        byte[] utf8Bytes = "log 中文输出测试\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] gbkBytes = "log 中文输出测试\n".getBytes(gbk);
        byte[] asciiBytes = "plain ascii log\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertEquals(java.nio.charset.StandardCharsets.UTF_8,
                ProcessContainerService.detectCharset(utf8Bytes, gbk), "合法 UTF-8 多字节内容应判定为 UTF-8");
        assertEquals(gbk, ProcessContainerService.detectCharset(gbkBytes, gbk),
                "GBK 字节无法通过 UTF-8 严格校验，应回退系统编码");
        assertEquals(gbk, ProcessContainerService.detectCharset(asciiBytes, gbk),
                "纯 ASCII 内容两种编码无差异，应回退系统编码");
        assertEquals(gbk, ProcessContainerService.detectCharset("中文".getBytes(gbk), gbk),
                "无 ASCII 锚点的字节无法可靠判定，应回退系统编码");
    }

    @Test
    @DisplayName("start：UTF-8 输出的程序中文不乱码（长 ASCII 前缀后自动探测）")
    void testStartOutputUtf8Chinese() throws Exception {
        String message = "中文输出测试";
        // 源码用 unicode 转义，保证源文件纯 ASCII 不受 javac 源码编码影响
        StringBuilder escaped = new StringBuilder();
        for (char c : message.toCharArray()) {
            escaped.append(String.format("\\u%04x", (int) c));
        }
        java.nio.file.Path src = java.nio.file.Files.createTempFile("PrintCn", ".java");
        // 先输出约 1800 字节 ASCII（超旧 512 字节探测窗口，模拟启动 banner/日志框架头），再输出 UTF-8 中文
        StringBuilder srcBody = new StringBuilder(
                "public class PrintCn { public static void main(String[] args) {");
        for (int i = 0; i < 60; i++) {
            srcBody.append(" System.out.println(\"ascii-banner-line-").append(i).append(" 0123456789\");");
        }
        srcBody.append(" System.out.println(\"").append(escaped).append("\"); } }");
        java.nio.file.Files.writeString(src, srcBody.toString());
        try {
            String javaBin = currentJavaExecutable();
            // -Dfile.encoding=UTF-8：JDK 17 管道输出跟随 file.encoding，模拟 UTF-8 输出的程序
            ProgramConfig config = newConfig(9019L, javaBin + " -Dfile.encoding=UTF-8 \"" + src + "\"", null);
            List<String> outputLines = new CopyOnWriteArrayList<>();

            String error = service.start(config, outputLines::add, null);
            assertNull(error, "临时 Java 程序应能启动");
            assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
            assertTrue(outputLines.stream().anyMatch(line -> line.contains(message)),
                    "长 ASCII 前缀后的 UTF-8 中文应被自动探测并正确解码: " + outputLines);
        } finally {
            java.nio.file.Files.deleteIfExists(src);
        }
    }

    @Test
    @DisplayName("start：系统编码输出的程序中文不乱码（自动回退）")
    void testStartOutputDefaultCharsetChinese() throws Exception {
        String message = "系统编码中文输出";
        StringBuilder escaped = new StringBuilder();
        for (char c : message.toCharArray()) {
            escaped.append(String.format("\\u%04x", (int) c));
        }
        java.nio.file.Path src = java.nio.file.Files.createTempFile("PrintNative", ".java");
        java.nio.file.Files.writeString(src,
                "public class PrintNative { public static void main(String[] args) { System.out.println(\""
                        + escaped + "\"); } }");
        try {
            String javaBin = currentJavaExecutable();
            // 不带 -Dfile.encoding 覆盖：JDK 17 管道输出默认跟随系统编码，模拟传统 GBK 控制台程序
            ProgramConfig config = newConfig(9021L, javaBin + " \"" + src + "\"", null);
            List<String> outputLines = new CopyOnWriteArrayList<>();

            String error = service.start(config, outputLines::add, null);
            assertNull(error, "临时 Java 程序应能启动");
            assertTrue(await(() -> service.getExitCode(config.getId()) != null), "应等待到进程退出");
            assertTrue(outputLines.stream().anyMatch(line -> line.contains(message)),
                    "系统编码输出应回退系统编码正确解码: " + outputLines);
        } finally {
            java.nio.file.Files.deleteIfExists(src);
        }
    }

    @Test
    @DisplayName("EncodingDetectReader：长 ASCII 前缀后接 UTF-8/GBK 中文均可正确解码，跨批切断不乱")
    void testEncodingDetectReaderMixed() throws Exception {
        java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
        StringBuilder banner = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            banner.append("ascii-banner-line-").append(i).append(" 0123456789\n");
        }
        String text = banner + "中文输出测试\n";

        // UTF-8 输出：ASCII 直通后首个非 ASCII 行判定为 UTF-8
        EncodingDetectReader utf8Reader = new EncodingDetectReader(
                new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), gbk);
        assertEquals(text, readAll(utf8Reader), "长 ASCII 前缀后的 UTF-8 中文应正确解码");

        // GBK 输出：UTF-8 严格校验不过，回退系统编码
        EncodingDetectReader gbkReader = new EncodingDetectReader(
                new java.io.ByteArrayInputStream(text.getBytes(gbk)), gbk);
        assertEquals(text, readAll(gbkReader), "长 ASCII 前缀后的 GBK 中文应回退系统编码解码");

        // 纯 ASCII：直通无差异
        EncodingDetectReader asciiReader = new EncodingDetectReader(
                new java.io.ByteArrayInputStream(banner.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                gbk);
        assertEquals(banner.toString(), readAll(asciiReader), "纯 ASCII 应直通无差异");

        // 多字节字符跨批切断：强制底层每批只给 1 字节，验证残余字节与后续批次拼接
        EncodingDetectReader tinyReader = new EncodingDetectReader(
                new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                    @Override
                    public int read(byte[] b, int off, int len) {
                        // ByteArrayInputStream 不会抛 IOException，无需捕获
                        return super.read(b, off, Math.min(len, 1));
                    }
                }, gbk);
        assertEquals(text, readAll(tinyReader), "多字节序列跨批切断应正确拼接解码");
    }

    private static String readAll(java.io.Reader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[64];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        reader.close();
        return sb.toString();
    }
}
