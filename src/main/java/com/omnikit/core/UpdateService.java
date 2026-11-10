package com.omnikit.core;

import com.omnikit.ui.UiConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 在线升级服务：基于 GitHub Releases 的版本检查、下载新 exe / 依赖库并完成替换 + 重启。
 *
 * <p>升级通道约定：</p>
 * <ul>
 *   <li>版本号使用 Git Tag（如 v1.1.0），与 GitHub Release 一一对应</li>
 *   <li>Release 必需资产：{@code OmniKit.exe}（升级目标）、可选的 {@code OmniKit.exe.sha256}（校验文件）</li>
 *   <li>Release 可选资产（依赖库升级通道）：{@code OmniKit-lib.zip}（lib/ 下全部第三方 jar）、
 *       可选的 {@code OmniKit-lib.zip.sha256}（校验文件）与 {@code OmniKit-lib.manifest}（依赖库清单）</li>
 *   <li>Release 的描述正文（body）即版本日志，展示在升级确认框中</li>
 * </ul>
 *
 * <p>更新原理：JAR 嵌入 exe（launch4j dontWrapJar=false），第三方依赖以 {@code lib/} 目录随 exe 发布。
 * 因此一次完整升级包含两部分：</p>
 * <ol>
 *   <li>exe：Windows 允许重命名正在运行的 exe，故将旧 exe 改名为 .old、新 exe 就位；</li>
 *   <li>lib：用清单（jar 名称 + 大小 + SHA-256）与本地 {@code lib/} 逐文件比对，仅当依赖发生变化
 *       （新增依赖、依赖版本变化、依赖被移除）时才下载 {@code OmniKit-lib.zip}，解压后就地同步
 *       （覆盖/新增远端 jar、删除远端已移除的 jar）。JRE 目录保持不变、无需升级。</li>
 * </ol>
 *
 * <p>exe 与 jar 都可能被杀毒/安全软件或运行中的 JVM 占用：此时降级为延迟替换——启动分离的
 * PowerShell 辅助进程，等本进程退出后完成全部替换并拉起新 exe。</p>
 */
public final class UpdateService {
    
    /**
     * GitHub 仓库 API 前缀（由 UiConstants.GITHUB_ADDR 推导）
     */
    public static final String REPO_API = UiConstants.GITHUB_ADDR.replace("https://github.com/", "https://api.github.com/repos/");
    
    /**
     * 最新 Release API
     */
    public static final String LATEST_RELEASE_API = REPO_API + "/releases/latest";
    
    /**
     * 自动检查间隔配置键（小时，存于 app_config 表）
     */
    public static final String CONFIG_KEY_CHECK_INTERVAL_HOURS = "update.check.interval.hours";
    
    /**
     * 上次自动检查时间配置键（epoch 毫秒，存于 app_config 表）
     */
    public static final String CONFIG_KEY_LAST_CHECK_TIME = "update.check.last.time";
    
    /**
     * GitHub Token 配置键（存于 app_config 表）
     */
    public static final String CONFIG_KEY_GITHUB_TOKEN = "update.github.token";
    
    /**
     * 自动检查间隔默认值：24 小时
     */
    public static final int DEFAULT_CHECK_INTERVAL_HOURS = 24;
    
    public static final String EXE_NAME = "OmniKit.exe";
    
    /**
     * 第三方依赖目录名（与 exe 同级）
     */
    public static final String LIB_DIR_NAME = "lib";
    
    /**
     * 依赖库升级包资产名：固定命名（不带版本号），客户端按名查找
     */
    public static final String LIB_ZIP_NAME = "OmniKit-lib.zip";
    
    /**
     * 依赖库清单资产名：每行 {@code <jarName>\t<size>\t<sha256>}
     */
    public static final String LIB_MANIFEST_NAME = "OmniKit-lib.manifest";
    
    /**
     * 依赖库暂存目录后缀：先解压到 lib.new，校验通过后再同步进 lib
     */
    private static final String LIB_STAGING_SUFFIX = ".new";
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * 单次 API 请求超时（GitHub 网络不佳时需给足时间）
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    
    /**
     * 下载响应超时（等待响应头的时限，不含传输过程）
     */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
    
    /**
     * 网络异常自动重试次数（含首次，共尝试 3 次）
     */
    private static final int MAX_ATTEMPTS = 3;
    
    /**
     * 重试间隔基数（毫秒），逐次翻倍：1s、2s
     */
    private static final long RETRY_BACKOFF_MS = 1_000;
    
    /**
     * 依赖库清单条目：jar 名称、字节大小、SHA-256
     */
    public record LibEntry(String name, long size, String sha256) {
    
    }
    
    /**
     * 新版本信息
     *
     * @param version   远端版本号（Tag）
     * @param url       exe 下载地址
     * @param sha256    exe 的 SHA-256（空串表示不校验）
     * @param notes     版本日志
     * @param libUrl    依赖库包下载地址（空串表示本次无依赖库升级）
     * @param libSha256 依赖库包的 SHA-256（空串表示不校验）
     * @param libEntries 依赖库清单（空表示无法判断依赖库是否需要升级）
     */
    public record UpdateInfo(String version, String url, String sha256, String notes, String libUrl, String libSha256,
            List<LibEntry> libEntries) {
        
        public UpdateInfo {
            libUrl = libUrl == null ? "" : libUrl;
            libSha256 = libSha256 == null ? "" : libSha256;
            libEntries = libEntries == null ? List.of() : List.copyOf(libEntries);
        }
        
        /**
         * 仅 exe 的升级信息（旧调用方兼容）
         */
        public UpdateInfo(String version, String url, String sha256, String notes) {
            this(version, url, sha256, notes, "", "", List.of());
        }
        
        /**
         * 本次升级是否携带可用的依赖库升级包
         */
        public boolean hasLibAsset() {
            return !libUrl.isBlank() && !libEntries.isEmpty();
        }
    }
    
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20))
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
    private <T> HttpResponse<T> sendWithRetry(HttpRequest.Builder requestBuilder, HttpResponse.BodyHandler<T> bodyHandler, Duration timeout,
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
                    logUpdate(what + " 第 " + attempt + "/" + MAX_ATTEMPTS + " 次请求失败（" + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "），" + backoff + "ms 后重试");
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
     * 获取自动检查间隔（小时）。优先级：启动参数 -Domnikit.update.intervalHours &gt; app_config 配置 &gt; 默认 24。 配置为 0 表示每次启动都检查。
     */
    public static int getCheckIntervalHours() {
        String prop = System.getProperty("omnikit.update.intervalHours");
        if (prop != null) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (NumberFormatException ignored) {
                // 参数非法则继续走配置
            }
        }
        return com.omnikit.util.SQLiteConfigUtil.getInstance().getIntAppConfig(CONFIG_KEY_CHECK_INTERVAL_HOURS, DEFAULT_CHECK_INTERVAL_HOURS);
    }
    
    /**
     * 判断是否满足自动检查条件：距上次检查超过 N 小时（N 可配置，默认 24，0 表示总是检查）。 手动点击「检查更新」不受此限制。
     */
    public static boolean shouldAutoCheck() {
        int intervalHours = getCheckIntervalHours();
        if (intervalHours <= 0) {
            return true;
        }
        String last = com.omnikit.util.SQLiteConfigUtil.getInstance().getAppConfig(CONFIG_KEY_LAST_CHECK_TIME, "");
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
     * 获取 GitHub Token，用于 API 认证（提高速率限制）。 优先级：启动参数 -Domnikit.update.githubToken &gt; app_config 表 &gt; version.properties 默认值
     */
    public static String getGitHubToken() {
        // 1. 启动参数优先
        String prop = System.getProperty("omnikit.update.githubToken");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        // 2. app_config 表
        String dbToken = com.omnikit.util.SQLiteConfigUtil.getInstance().getAppConfig(CONFIG_KEY_GITHUB_TOKEN, "");
        if (!dbToken.isBlank()) {
            return dbToken.trim();
        }
        // 3. version.properties 默认值（构建时注入）
        try (java.io.InputStream is = UpdateService.class.getClassLoader().getResourceAsStream("version.properties")) {
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
        com.omnikit.util.SQLiteConfigUtil.getInstance().setAppConfig(CONFIG_KEY_LAST_CHECK_TIME, String.valueOf(System.currentTimeMillis()));
    }
    
    /**
     * 查询 GitHub 最新 Release。 注意：GitHub API 匿名限额为每 IP 60 次/小时，足够手动+启动检查使用。
     */
    public UpdateInfo checkForUpdate() throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API)).header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OmniKit-Updater");
        // 添加 Authorization 头（如果配置了 token）
        String token = getGitHubToken();
        if (!token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = sendWithRetry(requestBuilder, HttpResponse.BodyHandlers.ofString(), REQUEST_TIMEOUT, "查询最新 Release");
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
        
        // 定位 exe / 依赖库相关资产下载地址
        String exeUrl = null;
        String sha256AssetUrl = null;
        String libUrl = null;
        String libSha256AssetUrl = null;
        String libManifestUrl = null;
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText();
            String url = asset.path("browser_download_url").asText();
            if (EXE_NAME.equals(name)) {
                exeUrl = url;
            } else if ((EXE_NAME + ".sha256").equals(name)) {
                sha256AssetUrl = url;
            } else if (LIB_ZIP_NAME.equals(name)) {
                libUrl = url;
            } else if ((LIB_ZIP_NAME + ".sha256").equals(name)) {
                libSha256AssetUrl = url;
            } else if (LIB_MANIFEST_NAME.equals(name)) {
                libManifestUrl = url;
            }
        }
        if (exeUrl == null) {
            throw new IllegalStateException("Release " + version + " 未附带 " + EXE_NAME + " 资产");
        }
        
        // 读取 exe 校验文件内容（若提供）
        String sha256 = sha256AssetUrl == null ? "" : parseSha256Asset(readTextAsset(sha256AssetUrl, "SHA-256 校验文件"));
        
        // 依赖库升级通道：需同时提供清单与升级包，缺任一则本次仅升级 exe（兼容旧 Release）
        String libSha256 = libSha256AssetUrl == null ? "" : parseSha256Asset(readTextAsset(libSha256AssetUrl, "依赖库校验文件"));
        List<LibEntry> libEntries = libManifestUrl == null ? List.of() : parseLibManifest(readTextAsset(libManifestUrl, "依赖库清单"));
        if (libUrl == null || libEntries.isEmpty()) {
            if (libUrl != null || libManifestUrl != null) {
                logUpdate("依赖库清单或升级包不完整（清单 " + libEntries.size() + " 项），本次仅升级 exe");
            }
            libUrl = null;
            libSha256 = "";
            libEntries = List.of();
        }
        return new UpdateInfo(version, exeUrl, sha256, release.path("body").asText(""), libUrl == null ? "" : libUrl, libSha256, libEntries);
    }
    
    /**
     * 读取 Release 中的文本资产（校验文件 / 依赖库清单）。失败不阻断升级，仅记录日志并返回空串。
     */
    private String readTextAsset(String url, String what) {
        try {
            HttpResponse<String> response = sendWithRetry(HttpRequest.newBuilder(URI.create(url)), HttpResponse.BodyHandlers.ofString(),
                    REQUEST_TIMEOUT, "读取 " + what);
            return response.statusCode() == 200 ? response.body() : "";
        } catch (Exception e) {
            logUpdate(what + "获取失败，本次更新将跳过对应步骤: " + e);
            return "";
        }
    }
    
    /**
     * 解析校验文件：兼容 "<hex>  OmniKit.exe" 与纯 hex 两种格式
     */
    static String parseSha256Asset(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.trim().split("\\s+")[0].toLowerCase();
    }
    
    /**
     * 解析依赖库清单：每行 {@code <jarName>\t<size>\t<sha256>}，非法行与不安全文件名忽略。
     */
    static List<LibEntry> parseLibManifest(String text) {
        List<LibEntry> entries = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return entries;
        }
        for (String line : text.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 3 || !isSafeJarName(parts[0])) {
                continue;
            }
            try {
                entries.add(new LibEntry(parts[0], Long.parseLong(parts[1]), parts[2].toLowerCase()));
            } catch (NumberFormatException ignored) {
                // 大小非法视为损坏行，跳过
            }
        }
        return entries;
    }
    
    /**
     * 仅接受 lib 根目录下的 .jar 文件名，防止清单/压缩包内的路径穿越写入任意位置
     */
    private static boolean isSafeJarName(String name) {
        return name != null && name.endsWith(".jar") && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && !name.contains("..");
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
     * 定位当前运行的 OmniKit.exe： 优先从 JVM 可执行文件路径向上找（launch4j 捆绑 JRE 时路径为 &lt;app&gt;/jre/bin/javaw.exe）， 再从工作目录向上找。开发环境（gradlew run / java -jar）找不到时返回
     * null。
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
     * 下载新版本 exe（及依赖库），校验后替换并重启。 成功时本方法不会返回（进程退出）。
     *
     * @param progress 下载进度回调（0-100，可为 null）
     */
    public void downloadAndApply(UpdateInfo info, IntConsumer progress) throws Exception {
        Path exe = currentExePath();
        if (exe == null) {
            throw new IllegalStateException("未找到 " + EXE_NAME + "（当前非 exe 方式运行），请手动下载更新：" + info.url());
        }
        logUpdate("开始更新 " + UiConstants.VERSION + " -> " + info.version() + "，当前 exe: " + exe);
        logUpdate("下载地址 " + info.url());
        if (info.hasLibAsset()) {
            logUpdate("本次 Release 提供依赖库升级包（清单 " + info.libEntries().size() + " 个 jar）: " + info.libUrl());
        }
        // 仅执行下载 + 校验 + 替换（不含重启），便于单元测试覆盖
        boolean inPlace = performDownloadAndReplace(exe, info, progress);

        if (!inPlace) {
            // 延迟替换模式：exe / 依赖库被占用，辅助脚本会在本进程退出后完成替换并拉起新 exe
            logUpdate("exe 或依赖库被占用，已安排延迟替换：本进程退出后由辅助脚本完成更新与重启");
            System.exit(0);
            return;
        }

        // 4. 启动新版本并退出当前进程
        Path dir = exe.getParent();
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
     * 下载 + SHA-256 校验 + 原子替换（exe 与可选的依赖库）。包级可见，便于 {@code UpdateServiceUpgradeTest} 直接驱动测试
     * 而不触发 {@link #restartApplication(Path)} / {@link System#exit(int)}。
     *
     * @param exe      当前 exe 路径（待替换目标）
     * @param info     远端版本信息
     * @param progress 下载进度回调（0-100，可为 null）
     * @return {@code true} 表示新 exe（及依赖库）已就地替换完成（调用方可直接重启）；
     *         {@code false} 表示 exe / jar 被安全软件等占用、已转为延迟替换模式
     *         （辅助脚本会在本进程退出后完成替换并拉起新 exe，调用方直接退出即可）
     */
    boolean performDownloadAndReplace(Path exe, UpdateInfo info, IntConsumer progress) throws Exception {
        Path dir = exe.getParent();
        Path newFile = dir.resolve(EXE_NAME + ".new");
        Path oldFile = dir.resolve(EXE_NAME + ".old");
        Path libDir = dir.resolve(LIB_DIR_NAME);
        Path libStaging = dir.resolve(LIB_DIR_NAME + LIB_STAGING_SUFFIX);

        // 1. 下载到内存字节数组：使用 BodyHandlers.ofByteArray 而非 ofInputStream，
        //    可规避 publishing stream 在 HTTP/2 流中断 / 302 重定向场景下抛出
        //    "java.io.IOException: closed" 的问题（GitHub 资产下载会触发跨域重定向）。
        byte[] data = downloadBytes(info.url(), "下载新版本 exe");
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

        // 2. exe SHA-256 校验（Release 提供 .sha256 资产时）
        if (!info.sha256().isBlank()) {
            String actual = sha256Hex(newFile);
            if (!actual.equalsIgnoreCase(info.sha256())) {
                Files.deleteIfExists(newFile);
                throw new IllegalStateException("SHA-256 校验失败：期望 " + info.sha256() + "，实际 " + actual);
            }
        }

        // 3. 依赖库：清单与本地 lib 逐文件比对，仅在依赖发生变化（新增/版本变化/移除）时才下载升级包，
        //    先解压到 lib.new 暂存目录，全部校验通过后再同步，避免半成品污染 lib
        Path stagedLib = null;
        if (info.hasLibAsset()) {
            if (needsLibUpdate(libDir, info.libEntries())) {
                try {
                    stagedLib = downloadAndStageLib(dir, info, progress);
                } catch (Exception e) {
                    // 依赖库阶段失败则不再进入替换流程，清理已下载的 exe.new 与暂存目录，旧版本保持可用
                    Files.deleteIfExists(newFile);
                    deleteRecursively(libStaging);
                    throw e;
                }
            } else {
                logUpdate("依赖库与远端清单一致，本次跳过依赖库升级");
            }
        } else {
            logUpdate("本次 Release 未提供依赖库清单/升级包，仅升级 exe");
        }
        if (progress != null) {
            progress.accept(100);
        }

        // 4. 依赖库就地同步：先于 exe 替换，保证新 exe 启动时看到的已是新依赖；
        //    jar 被运行中的 JVM 或安全软件占用则降级为：辅助进程在进程退出后完成 lib 同步 + exe 替换 + 重启
        if (stagedLib != null && !syncLibInPlace(libDir, stagedLib)) {
            logUpdate("依赖库文件被占用，转为延迟升级：本进程退出后由辅助进程完成依赖库同步与重启");
            scheduleDeferredSwap(dir, exe, newFile, oldFile, libStaging, info.libEntries().stream().map(LibEntry::name).toList());
            return false;
        }

        // 5. 替换 exe：正在运行的 exe 允许重命名，但不允许覆盖/删除；
        //    杀毒/安全软件以独占句柄打开 exe 时连重命名也会被拒（共享冲突），
        //    先多次重试规避瞬时锁定，仍失败则降级为延迟替换
        try {
            Files.deleteIfExists(oldFile);
        } catch (java.io.IOException e) {
            // 遗留 .old 被占用不致命，继续尝试带替换的改名
            logUpdate("清理遗留 .old 失败（继续尝试替换）: " + e);
        }
        if (!renameExeToOldWithRetry(exe, oldFile)) {
            logUpdate("当前 exe 被占用无法改名，转为延迟替换模式");
            scheduleDeferredSwap(dir, exe, newFile, oldFile, null, List.of());
            return false;
        }
        try {
            Files.move(newFile, exe, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // 替换失败则回滚，保证旧版本可用
            Files.move(oldFile, exe, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }
        return true;
    }

    /**
     * 下载二进制资产到内存（含重试、空响应与长度校验）。
     *
     * @param what 日志中的动作描述
     */
    private byte[] downloadBytes(String url, String what) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/octet-stream")
                .header("User-Agent", "OmniKit-Updater");
        HttpResponse<byte[]> response = sendWithRetry(builder, HttpResponse.BodyHandlers.ofByteArray(), DOWNLOAD_TIMEOUT, what);
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
            // 防止下载不完整时把损坏文件留给后续步骤
            throw new java.io.IOException("下载不完整，期望 " + expected + " 字节，实际收到 " + data.length + " 字节");
        }
        return data;
    }

    /**
     * 判断本地 lib 是否需要升级：远端清单中的 jar 缺失、大小或 SHA-256 不符，
     * 或本地存在远端已移除的多余 jar。
     *
     * @param libDir 本地 lib 目录（可能不存在）
     * @param remote 远端依赖库清单
     */
    static boolean needsLibUpdate(Path libDir, List<LibEntry> remote) {
        if (remote == null || remote.isEmpty()) {
            return false;
        }
        Set<String> remoteNames = new HashSet<>();
        for (LibEntry entry : remote) {
            remoteNames.add(entry.name());
            Path local = libDir.resolve(entry.name());
            if (!Files.isRegularFile(local)) {
                return true;
            }
            try {
                if (Files.size(local) != entry.size()) {
                    return true;
                }
                if (!entry.sha256().isBlank() && !sha256Hex(local).equalsIgnoreCase(entry.sha256())) {
                    return true;
                }
            } catch (Exception e) {
                // 无法读取（被占用/权限）时按需要升级处理
                return true;
            }
        }
        if (!Files.isDirectory(libDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(libDir)) {
            return stream.map(path -> path.getFileName().toString()).anyMatch(name -> isSafeJarName(name) && !remoteNames.contains(name));
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * 下载依赖库升级包、校验 SHA-256 并解压到暂存目录 {@code lib.new}。
     *
     * @return 暂存目录（已包含全部待同步 jar）
     */
    Path downloadAndStageLib(Path dir, UpdateInfo info, IntConsumer progress) throws Exception {
        Path staging = dir.resolve(LIB_DIR_NAME + LIB_STAGING_SUFFIX);
        deleteRecursively(staging);
        byte[] data = downloadBytes(info.libUrl(), "下载依赖库升级包");
        if (!info.libSha256().isBlank()) {
            String actual = sha256Hex(data);
            if (!actual.equalsIgnoreCase(info.libSha256())) {
                throw new IllegalStateException("依赖库 SHA-256 校验失败：期望 " + info.libSha256() + "，实际 " + actual);
            }
        }
        Files.createDirectories(staging);
        int count = extractJars(data, staging);
        if (count == 0) {
            deleteRecursively(staging);
            throw new IllegalStateException("依赖库升级包内容为空（未包含任何 jar）");
        }
        if (progress != null) {
            progress.accept(95); // 95%：依赖库已就绪，即将开始同步
        }
        logUpdate("依赖库升级包已解压 " + count + " 个 jar 到 " + staging);
        return staging;
    }

    /**
     * 将升级包中的 jar 解压到目标目录：仅接受根级 {@code *.jar}，忽略目录与其他文件，防止路径穿越。
     * 包级可见，便于 {@code UpdateServiceUpgradeTest} 直接验证。
     */
    static int extractJars(byte[] zipData, Path targetDir) throws java.io.IOException {
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = Path.of(entry.getName()).getFileName().toString();
                if (!isSafeJarName(name)) {
                    continue;
                }
                Files.copy(zip, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    /**
     * 将暂存目录中的 jar 就地同步到 lib：覆盖/新增远端 jar、删除远端已移除的 jar。
     * jar 被占用时会重试，仍失败返回 false（调用方转延迟同步）。
     */
    static boolean syncLibInPlace(Path libDir, Path staging) {
        final int maxAttempts = 5;
        List<String> names;
        try (Stream<Path> stream = Files.list(staging)) {
            names = stream.map(path -> path.getFileName().toString()).filter(UpdateService::isSafeJarName).toList();
        } catch (java.io.IOException e) {
            logUpdate("读取依赖库暂存目录失败: " + e);
            return false;
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.createDirectories(libDir);
                for (String name : names) {
                    Path source = staging.resolve(name);
                    if (Files.isRegularFile(source)) {
                        Files.move(source, libDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                try (Stream<Path> stream = Files.list(libDir)) {
                    List<Path> stale = stream.filter(path -> {
                        String name = path.getFileName().toString();
                        return isSafeJarName(name) && !names.contains(name);
                    }).toList();
                    for (Path path : stale) {
                        Files.deleteIfExists(path);
                    }
                }
                deleteRecursively(staging);
                logUpdate("依赖库同步完成，共 " + names.size() + " 个 jar");
                return true;
            } catch (java.io.IOException e) {
                logUpdate("依赖库同步失败（第 " + attempt + "/" + maxAttempts + " 次）: " + e);
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /** 递归删除目录（失败忽略，仅用于清理暂存数据） */
    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                    // 被占用则保留，下次启动再清理
                }
            }
        } catch (java.io.IOException ignored) {
            // 无删除权限时忽略
        }
    }

    /**
     * 将当前 exe 改名为 .old，带重试。共享冲突/安全软件锁定通常是瞬时的，短间隔多次尝试可绕过；
     * 其他 IO 错误（如磁盘满、权限）重试无意义，直接放弃转入延迟替换。
     */
    private static boolean renameExeToOldWithRetry(Path exe, Path oldFile) {
        final int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.move(exe, oldFile, StandardCopyOption.REPLACE_EXISTING);
                if (attempt > 1) {
                    logUpdate("exe 改名 .old 于第 " + attempt + " 次重试成功");
                }
                return true;
            } catch (java.io.IOException e) {
                logUpdate("exe 改名 .old 失败（第 " + attempt + "/" + maxAttempts + " 次）: " + e);
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 延迟替换兜底：exe / jar 被占用导致无法立即就位时，启动一个完全分离的 PowerShell 辅助进程，
     * 等待本进程退出（文件锁必然释放）后再完成可选的替换动作并拉起新 exe：
     * <ul>
     *   <li>{@code newFile} 非空：exe → .old、.new → exe</li>
     *   <li>{@code stagingLib} 非空：将暂存目录中的 jar 同步进 lib，并按清单删除远端已移除的 jar</li>
     * </ul>
     * Windows 环境可用；辅助进程启动失败则抛出异常，由上层提示用户手动更新。
     *
     * @param keepNames 远端清单中的 jar 名称（决定 lib 内哪些 jar 需要保留）
     */
    private static void scheduleDeferredSwap(Path dir, Path exe, Path newFile, Path oldFile, Path stagingLib, List<String> keepNames)
            throws java.io.IOException {
        long pid = ProcessHandle.current().pid();
        Path targetExe = exe != null ? exe : dir.resolve(EXE_NAME);
        StringBuilder script = new StringBuilder("$ErrorActionPreference='Continue';")
                .append("Wait-Process -Id ").append(pid).append(" -ErrorAction SilentlyContinue;")
                .append("Start-Sleep -Milliseconds 300;");
        if (newFile != null) {
            script.append("$exe='").append(psQuote(targetExe)).append("';$new='").append(psQuote(newFile)).append("';$old='")
                    .append(psQuote(oldFile)).append("';")
                    .append("for($i=0;$i -lt 20;$i++){ try{ Move-Item -LiteralPath $exe -Destination $old -Force -ErrorAction Stop; break")
                    .append(" } catch { Start-Sleep -Milliseconds 500 } };")
                    .append("Move-Item -LiteralPath $new -Destination $exe -Force;");
        }
        if (stagingLib != null) {
            Path libDir = dir.resolve(LIB_DIR_NAME);
            script.append("$lib='").append(psQuote(libDir)).append("';$stage='").append(psQuote(stagingLib)).append("';")
                    .append("$keep=@(").append(psQuotedList(keepNames)).append(");")
                    .append("New-Item -ItemType Directory -Path $lib -Force | Out-Null;")
                    .append("for($i=0;$i -lt 20;$i++){ try{")
                    .append(" foreach($n in $keep){ $src=Join-Path $stage $n; if(Test-Path -LiteralPath $src){")
                    .append(" Move-Item -LiteralPath $src -Destination (Join-Path $lib $n) -Force -ErrorAction Stop } };")
                    .append(" Get-ChildItem -LiteralPath $lib -Filter *.jar -File | Where-Object { $keep -notcontains $_.Name }")
                    .append(" | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force -ErrorAction Stop }; break")
                    .append(" } catch { Start-Sleep -Milliseconds 500 } };")
                    .append("Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue;");
        }
        script.append("Start-Process -FilePath '").append(psQuote(targetExe)).append("' -WorkingDirectory '").append(psQuote(dir)).append("'");
        Process helper = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script.toString())
                .directory(dir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        logUpdate("延迟替换辅助进程已启动 pid=" + helper.pid() + "，等待本进程(" + pid + ")退出后执行替换");
    }

    /** PowerShell 数组字面量：'a.jar','b.jar' */
    private static String psQuotedList(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        return names.stream().map(name -> "'" + name.replace("'", "''") + "'").collect(java.util.stream.Collectors.joining(","));
    }

    /** PowerShell 单引号字符串字面量转义：' → '' */
    private static String psQuote(Path path) {
        return path.toString().replace("'", "''");
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
     * 启动新版 exe，成功返回 true。 依次尝试两种方式： 1. 直接启动（输出重定向到 DISCARD，避免旧进程退出后管道断裂导致新进程阻塞/退出） 2. 通过 cmd start 完全脱离父子进程关系启动（兼容部分安全软件/权限拦截场景）
     */
    private static boolean restartApplication(Path dir) {
        // 方式一：直接启动
        try {
            new ProcessBuilder(dir.resolve(EXE_NAME).toString()).directory(dir.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            logUpdate("重启方式一（直接启动）成功");
            return true;
        } catch (Exception e) {
            logUpdate("重启方式一失败: " + e);
        }
        // 方式二：cmd start 分离启动（支持路径含空格）
        try {
            new ProcessBuilder("cmd", "/c", "start", "", "/D", dir.toString(), EXE_NAME).directory(dir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            logUpdate("重启方式二（cmd start）成功");
            return true;
        } catch (Exception e) {
            logUpdate("重启方式二失败: " + e);
        }
        return false;
    }
    
    /**
     * 追加更新日志到 {应用目录}/logs/update.log，便于排查更新/重启问题（失败不影响主流程）。
     */
    private static void logUpdate(String message) {
        try {
            Path exe = new UpdateService().currentExePath();
            Path baseDir = exe != null ? exe.getParent() : Path.of(System.getProperty("user.dir"));
            Path logFile = baseDir.resolve("logs").resolve("update.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, java.time.LocalDateTime.now() + " " + message + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志失败不影响更新流程
        }
    }
    
    /**
     * 清理上次升级遗留的 .old / .new / lib.new 文件（应用启动时调用）。
     */
    public static void cleanupObsoleteFiles() {
        try {
            Path exe = new UpdateService().currentExePath();
            if (exe == null) {
                return;
            }
            Files.deleteIfExists(exe.getParent().resolve(EXE_NAME + ".old"));
            Files.deleteIfExists(exe.getParent().resolve(EXE_NAME + ".new"));
            deleteRecursively(exe.getParent().resolve(LIB_DIR_NAME + LIB_STAGING_SUFFIX));
        } catch (Exception ignored) {
            // 清理失败不影响启动
        }
    }
    
    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
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
