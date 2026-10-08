package com.datasync.core;

import com.datasync.ui.UiConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.IntConsumer;

/**
 * 在线升级服务：基于 GitHub Releases 的版本检查、下载新 exe 并完成替换 + 重启。
 *
 * <p>升级通道约定：</p>
 * <ul>
 *   <li>版本号使用 Git Tag（如 v1.1.0），与 GitHub Release 一一对应</li>
 *   <li>Release 附带资产：{@code DataSync.exe}（升级目标）、可选的 {@code DataSync.exe.sha256}（校验文件）</li>
 *   <li>Release 的描述正文（body）即版本日志，展示在升级确认框中</li>
 * </ul>
 *
 * <p>更新原理：JAR 嵌入 exe（launch4j dontWrapJar=false），故更新即替换整个 exe；
 * JRE 目录保持不变、无需升级。Windows 允许重命名正在运行的 exe，因此将旧 exe 改名为 .old、
 * 新 exe 就位后拉起新进程并退出旧进程。</p>
 */
public final class UpdateService {

    /** GitHub 仓库 API 前缀（由 UiConstants.GITHUB_ADDR 推导） */
    public static final String REPO_API = UiConstants.GITHUB_ADDR.replace("https://github.com/", "https://api.github.com/repos/");

    /** 最新 Release API */
    public static final String LATEST_RELEASE_API = REPO_API + "/releases/latest";

    /** 自动检查间隔配置键（小时，存于 app_config 表） */
    public static final String CONFIG_KEY_CHECK_INTERVAL_HOURS = "update.check.interval.hours";

    /** 上次自动检查时间配置键（epoch 毫秒，存于 app_config 表） */
    public static final String CONFIG_KEY_LAST_CHECK_TIME = "update.check.last.time";

    /** GitHub Token 配置键（存于 app_config 表） */
    public static final String CONFIG_KEY_GITHUB_TOKEN = "update.github.token";

    /** 自动检查间隔默认值：24 小时 */
    public static final int DEFAULT_CHECK_INTERVAL_HOURS = 24;

    private static final String EXE_NAME = "DataSync.exe";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单次 API 请求超时（GitHub 网络不佳时需给足时间） */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /** 下载响应超时（等待响应头的时限，不含传输过程） */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);

    /** 网络异常自动重试次数（含首次，共尝试 3 次） */
    private static final int MAX_ATTEMPTS = 3;

    /** 重试间隔基数（毫秒），逐次翻倍：1s、2s */
    private static final long RETRY_BACKOFF_MS = 1_000;

    /** 新版本信息 */
    public record UpdateInfo(String version, String url, String sha256, String notes) {
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private UpdateService() {
    }

    public static UpdateService create() {
        return new UpdateService();
    }

    /**
     * 发送 HTTP 请求，网络类异常（超时 / 连接重置 / DNS 失败等 IOException）自动重试，业务错误（404 等）不重试。
     *
     * @param what    请求描述，用于日志
     * @param timeout 单次请求超时
     */
    private <T> HttpResponse<T> sendWithRetry(HttpRequest.Builder requestBuilder,
                                              HttpResponse.BodyHandler<T> bodyHandler,
                                              Duration timeout,
                                              String what) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = requestBuilder.timeout(timeout).GET().build();
                return http.send(request, bodyHandler);
            } catch (java.io.IOException e) {
                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoff = RETRY_BACKOFF_MS * (1L << (attempt - 1));
                    logUpdate(what + " 第 " + attempt + "/" + MAX_ATTEMPTS + " 次请求失败（"
                            + e.getClass().getSimpleName() + ": " + e.getMessage() + "），"
                            + backoff + "ms 后重试");
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    logUpdate(what + " 重试 " + MAX_ATTEMPTS + " 次均失败: " + e);
                }
            }
        }
        throw lastFailure;
    }

    /**
     * 获取自动检查间隔（小时）。优先级：启动参数 -Ddatasync.update.intervalHours &gt; app_config 配置 &gt; 默认 24。
     * 配置为 0 表示每次启动都检查。
     */
    public static int getCheckIntervalHours() {
        String prop = System.getProperty("datasync.update.intervalHours");
        if (prop != null) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (NumberFormatException ignored) {
                // 参数非法则继续走配置
            }
        }
        return com.datasync.util.SQLiteConfigUtil.getInstance()
                .getIntAppConfig(CONFIG_KEY_CHECK_INTERVAL_HOURS, DEFAULT_CHECK_INTERVAL_HOURS);
    }

    /**
     * 判断是否满足自动检查条件：距上次检查超过 N 小时（N 可配置，默认 24，0 表示总是检查）。
     * 手动点击「检查更新」不受此限制。
     */
    public static boolean shouldAutoCheck() {
        int intervalHours = getCheckIntervalHours();
        if (intervalHours <= 0) {
            return true;
        }
        String last = com.datasync.util.SQLiteConfigUtil.getInstance()
                .getAppConfig(CONFIG_KEY_LAST_CHECK_TIME, "");
        long lastMillis;
        try {
            lastMillis = Long.parseLong(last.trim());
        } catch (NumberFormatException e) {
            // 无记录，视为从未检查过
            return true;
        }
        return System.currentTimeMillis() - lastMillis >= intervalHours * 3600_000L;
    }

    /**
     * 获取 GitHub Token，用于 API 认证（提高速率限制）。
     * 优先级：启动参数 -Ddatasync.update.githubToken &gt; app_config 表 &gt; version.properties 默认值
     */
    public static String getGitHubToken() {
        // 1. 启动参数优先
        String prop = System.getProperty("datasync.update.githubToken");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        // 2. app_config 表
        String dbToken = com.datasync.util.SQLiteConfigUtil.getInstance()
                .getAppConfig(CONFIG_KEY_GITHUB_TOKEN, "");
        if (!dbToken.isBlank()) {
            return dbToken.trim();
        }
        // 3. version.properties 默认值（构建时注入）
        try (java.io.InputStream is = UpdateService.class.getClassLoader()
                .getResourceAsStream("version.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String token = props.getProperty("github.token", "");
                if (!token.isBlank() && !"${githubToken}".equals(token)) {
                    return token.trim();
                }
            }
        } catch (java.io.IOException ignored) {
        }
        return "";
    }

    /**
     * 记录本次检查时间（每次实际检查后调用）。
     */
    public static void markChecked() {
        com.datasync.util.SQLiteConfigUtil.getInstance()
                .setAppConfig(CONFIG_KEY_LAST_CHECK_TIME, String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 查询 GitHub 最新 Release。
     * 注意：GitHub API 匿名限额为每 IP 60 次/小时，足够手动+启动检查使用。
     */
    public UpdateInfo checkForUpdate() throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DataSync-Updater");
        // 添加 Authorization 头（如果配置了 token）
        String token = getGitHubToken();
        if (!token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = sendWithRetry(requestBuilder,
                HttpResponse.BodyHandlers.ofString(), REQUEST_TIMEOUT, "查询最新 Release");
        if (response.statusCode() == 404) {
            throw new IllegalStateException("GitHub 上尚未发布任何 Release");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub API 请求失败，HTTP " + response.statusCode());
        }
        JsonNode release = MAPPER.readTree(response.body());
        String version = release.path("tag_name").asText("");
        if (version.isBlank()) {
            throw new IllegalStateException("Release 缺少 tag_name");
        }

        // 定位 DataSync.exe 资产下载地址
        String exeUrl = null;
        String sha256AssetUrl = null;
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText();
            if (EXE_NAME.equals(name)) {
                exeUrl = asset.path("browser_download_url").asText();
            } else if ((EXE_NAME + ".sha256").equals(name)) {
                sha256AssetUrl = asset.path("browser_download_url").asText();
            }
        }
        if (exeUrl == null) {
            throw new IllegalStateException("Release " + version + " 未附带 " + EXE_NAME + " 资产");
        }

        // 读取校验文件内容（若提供）
        String sha256 = "";
        if (sha256AssetUrl != null) {
            try {
                HttpResponse<String> shaResponse = sendWithRetry(
                        HttpRequest.newBuilder(URI.create(sha256AssetUrl)),
                        HttpResponse.BodyHandlers.ofString(), REQUEST_TIMEOUT, "读取 SHA-256 校验文件");
                if (shaResponse.statusCode() == 200) {
                    // 格式："<hex>  DataSync.exe" 或纯 hex
                    sha256 = shaResponse.body().trim().split("\\s+")[0].toLowerCase();
                }
            } catch (Exception e) {
                // 校验文件拉取失败不阻断更新，仅跳过校验并记录
                logUpdate("SHA-256 校验文件获取失败，本次更新将跳过校验: " + e);
            }
        }
        return new UpdateInfo(version, exeUrl, sha256, release.path("body").asText(""));
    }

    /**
     * 判断远端版本是否高于本地版本（按数字段逐段比较，忽略 "v" 前缀）。
     */
    public static boolean isNewerVersion(String remote, String local) {
        int[] a = parseVersion(remote);
        int[] b = parseVersion(local);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String cleaned = version == null ? "" : version.trim().replaceFirst("^[vV]", "");
        String[] parts = cleaned.split("[^0-9]+");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    /**
     * 定位当前运行的 DataSync.exe：
     * 优先从 JVM 可执行文件路径向上找（launch4j 捆绑 JRE 时路径为 &lt;app&gt;/jre/bin/javaw.exe），
     * 再从工作目录向上找。开发环境（gradlew run / java -jar）找不到时返回 null。
     */
    public Path currentExePath() {
        Path javaPath = ProcessHandle.current().info().command().map(Path::of).orElse(null);
        if (javaPath != null) {
            Path dir = javaPath.getParent();
            while (dir != null) {
                Path exe = dir.resolve(EXE_NAME);
                if (Files.isRegularFile(exe)) {
                    return exe;
                }
                dir = dir.getParent();
            }
        }
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path exe = dir.resolve(EXE_NAME);
            if (Files.isRegularFile(exe)) {
                return exe;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * 下载新版本 exe，校验后替换当前 exe 并重启。
     * 成功时本方法不会返回（进程退出）。
     *
     * @param progress 下载进度回调（0-100，可为 null）
     */
    public void downloadAndApply(UpdateInfo info, IntConsumer progress) throws Exception {
        Path exe = currentExePath();
        if (exe == null) {
            throw new IllegalStateException("未找到 " + EXE_NAME + "（当前非 exe 方式运行），请手动下载更新：" + info.url());
        }
        logUpdate("开始更新 " + UiConstants.VERSION + " -> " + info.version() + "，当前 exe: " + exe);
        Path dir = exe.getParent();
        Path newFile = dir.resolve(EXE_NAME + ".new");
        Path oldFile = dir.resolve(EXE_NAME + ".old");

        // 1. 下载到内存字节数组：使用 BodyHandlers.ofByteArray 而非 ofInputStream，
        //    可规避 publishing stream 在 HTTP/2 流中断 / 302 重定向场景下抛出
        //    "java.io.IOException: closed" 的问题（GitHub 资产下载会触发跨域重定向）。
        HttpRequest.Builder downloadBuilder = HttpRequest.newBuilder(URI.create(info.url()))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "DataSync-Updater");
        HttpResponse<byte[]> response = sendWithRetry(downloadBuilder,
                HttpResponse.BodyHandlers.ofByteArray(), DOWNLOAD_TIMEOUT, "下载新版本 exe");
        if (response.statusCode() != 200) {
            throw new IllegalStateException("下载失败，HTTP " + response.statusCode());
        }
        byte[] data = response.body();
        if (data == null || data.length == 0) {
            // ofByteArray 收到空响应体通常是 GitHub 防爬或网络中间设备拦截，直接抛出由 sendWithRetry 重试
            throw new java.io.IOException("下载失败，响应体为空（可能被网络拦截）");
        }
        long expected = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        if (expected > 0 && data.length != expected) {
            // 防止下载不完整时把损坏 .new 留给后续步骤
            throw new java.io.IOException("下载不完整，期望 " + expected + " 字节，实际收到 " + data.length + " 字节");
        }
        if (progress != null) {
            progress.accept(90); // 90%：已成功接收全部字节，提示用户已开始落盘
        }
        try {
            Files.write(newFile, data);
        } catch (java.io.IOException e) {
            Files.deleteIfExists(newFile);
            logUpdate("写入新版本失败: " + e);
            throw e;
        }
        if (progress != null) {
            progress.accept(100);
        }

        // 2. SHA-256 校验（Release 提供 .sha256 资产时）
        if (!info.sha256().isBlank()) {
            String actual = sha256Hex(newFile);
            if (!actual.equalsIgnoreCase(info.sha256())) {
                Files.deleteIfExists(newFile);
                throw new IllegalStateException("SHA-256 校验失败：期望 " + info.sha256() + "，实际 " + actual);
            }
        }

        // 3. 替换：正在运行的 exe 允许重命名，但不允许覆盖/删除
        Files.deleteIfExists(oldFile);
        Files.move(exe, oldFile);
        try {
            Files.move(newFile, exe, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // 替换失败则回滚，保证旧版本可用
            Files.move(oldFile, exe, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }

        // 4. 启动新版本并退出当前进程
        logUpdate("更新就位，开始重启: " + exe);
        if (!restartApplication(dir)) {
            // 更新已完成（新 exe 已就位），仅自动重启失败：提示用户手动启动，不能回滚
            throw new RestartException("新版本已更新完成，但自动重启失败，请关闭当前窗口后手动启动 " + EXE_NAME);
        }
        // 给新进程完成 JVM 初始化的时间，避免旧进程过早退出影响其启动
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }

    /**
     * 重启失败异常：exe 已替换成功，仅自动拉起新进程失败，用户手动启动即可。
     */
    public static class RestartException extends IllegalStateException {

        public RestartException(String message) {
            super(message);
        }
    }

    /**
     * 启动新版 exe，成功返回 true。
     * 依次尝试两种方式：
     * 1. 直接启动（输出重定向到 DISCARD，避免旧进程退出后管道断裂导致新进程阻塞/退出）
     * 2. 通过 cmd start 完全脱离父子进程关系启动（兼容部分安全软件/权限拦截场景）
     */
    private static boolean restartApplication(Path dir) {
        // 方式一：直接启动
        try {
            new ProcessBuilder(dir.resolve(EXE_NAME).toString())
                    .directory(dir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            logUpdate("重启方式一（直接启动）成功");
            return true;
        } catch (Exception e) {
            logUpdate("重启方式一失败: " + e);
        }
        // 方式二：cmd start 分离启动（支持路径含空格）
        try {
            new ProcessBuilder("cmd", "/c", "start", "", "/D", dir.toString(), EXE_NAME)
                    .directory(dir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            logUpdate("重启方式二（cmd start）成功");
            return true;
        } catch (Exception e) {
            logUpdate("重启方式二失败: " + e);
        }
        return false;
    }

    /**
     * 追加更新日志到 {应用目录}/data/update.log，便于排查更新/重启问题（失败不影响主流程）。
     */
    private static void logUpdate(String message) {
        try {
            Path exe = new UpdateService().currentExePath();
            Path baseDir = exe != null ? exe.getParent() : Path.of(System.getProperty("user.dir"));
            Path logFile = baseDir.resolve("data").resolve("update.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, java.time.LocalDateTime.now() + " " + message + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志失败不影响更新流程
        }
    }

    /**
     * 清理上次升级遗留的 .old / .new 文件（应用启动时调用）。
     */
    public static void cleanupObsoleteFiles() {
        try {
            Path exe = new UpdateService().currentExePath();
            if (exe == null) {
                return;
            }
            Files.deleteIfExists(exe.getParent().resolve(EXE_NAME + ".old"));
            Files.deleteIfExists(exe.getParent().resolve(EXE_NAME + ".new"));
        } catch (Exception ignored) {
            // 清理失败不影响启动
        }
    }

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
