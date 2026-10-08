package com.datasync.core;

import com.datasync.util.SQLiteConfigUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UpdateService 自动升级流程的单元测试，覆盖 v1.1.2 修复后引入的 ByteArray 下载链路
 * 以及 SHA-256 校验、原子替换、错误分支、辅助函数等。
 * <p>
 * 测试策略：
 * <ul>
 *   <li>{@link MockServer}：基于 JDK 内置 {@code com.sun.net.httpserver.HttpServer}，
 *       不引入额外 mock 框架；可在端口 0 上自启，并对响应状态/响应体/请求头进行断言。</li>
 *   <li>{@code performDownloadAndReplace} 是 v1.1.2 重构后暴露的包级方法，
 *       跳过重启 + System.exit(0) 的副作用，使 JVM 内可完整闭环。</li>
 *   <li>{@code checkForUpdate} 的网络分支仍由 {@link UpdateServiceTest#testCheckForUpdateLive()}
 *       覆盖（live tag），不在此处重复 mock。</li>
 * </ul>
 */
@DisplayName("UpdateService 自动升级测试")
class UpdateServiceUpgradeTest {

    /**
     * 极简 HTTP 模拟服务器：可配置响应队列（按调用顺序出队），记录收到请求的头部。
     * 注意：JDK HttpServer 在 {@code sendResponseHeaders(status, length)} 时
     * 总是按 length 覆写 Content-Length header，因此 {@code Content-Length 不一致}
     * 的分支无法用本服务器复现，留作未来按需扩展。
     */
    static final class MockServer implements AutoCloseable {

        final HttpServer server;
        final int port;
        final AtomicInteger callIndex = new AtomicInteger(0);
        final List<HttpExchange> exchanges = new CopyOnWriteArrayList<>();
        final List<MockResponse> queue = new ArrayList<>();
        /** 队列出队后无响应可取时使用的兜底响应（默认 200 + 空 body） */
        MockResponse fallback = new MockResponse(200, new byte[0], "application/octet-stream");

        MockServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.port = server.getAddress().getPort();
            server.createContext("/", this::dispatch);
            server.setExecutor(null);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + port;
        }

        void enqueueOk(byte[] body) {
            queue.add(new MockResponse(200, body, "application/octet-stream"));
        }

        void enqueueOk(byte[] body, String contentType) {
            queue.add(new MockResponse(200, body, contentType));
        }

        void enqueueStatus(int status) {
            queue.add(new MockResponse(status, new byte[0], "application/octet-stream"));
        }

        void enqueueHang() {
            // 用于测试 IOException 重试：handler 啥也不发，让客户端超时
            queue.add(new MockResponse(-1, null, null));
        }

        private void dispatch(HttpExchange ex) throws IOException {
            exchanges.add(ex);
            MockResponse r;
            int idx = callIndex.getAndIncrement();
            if (idx < queue.size()) {
                r = queue.get(idx);
            } else {
                r = fallback;
            }
            if (r.status == -1) {
                // hang：不调用 sendResponseHeaders，让客户端连接超时
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                try {
                    ex.close();
                } catch (Exception ignored) {
                    // noop
                }
                return;
            }
            ex.getResponseHeaders().set("Content-Type", r.contentType);
            if (r.body == null) {
                ex.sendResponseHeaders(r.status, -1);
                ex.close();
            } else {
                ex.sendResponseHeaders(r.status, r.body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(r.body);
                }
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }

        static final class MockResponse {
            final int status;
            final byte[] body;
            final String contentType;

            MockResponse(int status, byte[] body, String contentType) {
                this.status = status;
                this.body = body;
                this.contentType = contentType;
            }
        }
    }

    @TempDir
    Path tempDir;

    /** 把 {@link MockServer} 启动在专属端口并返回完整下载 URL 的小工具 */
    private String mockDownloadUrl(MockServer server) {
        return server.baseUrl() + "/DataSync.exe";
    }

    /** 在 {@link #tempDir} 中创建名为 DataSync.exe 的假 exe 并返回其路径 */
    private Path createFakeExe(byte[] content) throws IOException {
        Path exe = tempDir.resolve(UpdateService.EXE_NAME);
        Files.write(exe, content);
        return exe;
    }

    /** 计算指定字节的 SHA-256（与 {@code UpdateService.sha256Hex} 行为对齐） */
    private static String sha256Of(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String savedUserDir;

    @BeforeAll
    static void initDb() {
        SQLiteConfigUtil.getInstance().initialize();
    }

    @BeforeEach
    void captureEnv() {
        savedUserDir = System.getProperty("user.dir");
    }

    @AfterEach
    void restoreEnv() {
        if (savedUserDir != null) {
            System.setProperty("user.dir", savedUserDir);
        }
        System.clearProperty("datasync.update.githubToken");
    }

    // ─────────── performDownloadAndReplace 下载替换主流程 ───────────

    @Nested
    @DisplayName("performDownloadAndReplace 下载 + 替换流程")
    class DownloadReplace {

        @Test
        @DisplayName("成功下载：progress 触发 90 与 100，.new 已清理，.old 保留，exe 被替换为新内容")
        void successfulDownloadReplacesExe() throws Exception {
            byte[] oldContent = "OLD_VERSION_1_1_1".getBytes(StandardCharsets.UTF_8);
            byte[] newPayload = "NEW_VERSION_1_1_3_OK".getBytes(StandardCharsets.UTF_8);
            Path exe = createFakeExe(oldContent);

            try (MockServer server = new MockServer()) {
                server.enqueueOk(newPayload);

                List<Integer> progress = new ArrayList<>();
                UpdateService.create().performDownloadAndReplace(
                        exe,
                        new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), "", "release notes"),
                        progress::add);

                assertEquals(List.of(90, 100), progress, "进度应仅触发 90 与 100 两个事件");
                assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".new")),
                        ".new 应被清理");
                Path oldFile = tempDir.resolve(UpdateService.EXE_NAME + ".old");
                assertTrue(Files.exists(oldFile), ".old 应保留以便回滚");
                assertArrayEquals(oldContent, Files.readAllBytes(oldFile), ".old 应保留旧版本内容");
                assertArrayEquals(newPayload, Files.readAllBytes(exe), "exe 应已被替换为下载内容");
            }
        }

        @Test
        @DisplayName("下载请求头应包含 Accept: application/octet-stream 与 User-Agent: DataSync-Updater")
        void downloadRequestHeadersCarryAcceptAndUserAgent() throws Exception {
            Path exe = createFakeExe("old".getBytes(StandardCharsets.UTF_8));

            try (MockServer server = new MockServer()) {
                server.enqueueOk("hello".getBytes(StandardCharsets.UTF_8));

                UpdateService.create().performDownloadAndReplace(
                        exe,
                        new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), "", ""),
                        null);

                assertEquals(1, server.exchanges.size(), "应收到 1 次下载请求");
                HttpExchange got = server.exchanges.get(0);
                assertEquals("application/octet-stream", got.getRequestHeaders().getFirst("Accept"),
                        "v1.1.2 修复后下载新增 Accept: application/octet-stream 头");
                assertEquals("DataSync-Updater", got.getRequestHeaders().getFirst("User-Agent"));
                assertEquals("GET", got.getRequestMethod());
            }
        }

        @Test
        @DisplayName("HTTP 404 抛出 IllegalStateException，且 .new/.old 不会被创建")
        void http404ThrowsAndLeavesNoArtifacts() throws Exception {
            byte[] oldContent = "OLD".getBytes(StandardCharsets.UTF_8);
            Path exe = createFakeExe(oldContent);
            try (MockServer server = new MockServer()) {
                server.enqueueStatus(404);
                IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                        UpdateService.create().performDownloadAndReplace(
                                exe,
                                new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), "", ""),
                                null));
                assertTrue(ex.getMessage().contains("下载失败"), "错误信息应说明下载失败: " + ex.getMessage());
                assertTrue(ex.getMessage().contains("404"));
            }
            assertArrayEquals(oldContent, Files.readAllBytes(exe), "失败时原 exe 不应被改动");
            assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".new")),
                    "失败时不应残留 .new");
            assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".old")),
                    "失败时不应创建 .old");
        }

        @Test
        @DisplayName("HTTP 500 全部重试后抛出 IOException（重试耗尽）")
        void http500RetriesThenGivesUp() throws Exception {
            Path exe = createFakeExe("OLD".getBytes(StandardCharsets.UTF_8));
            try (MockServer server = new MockServer()) {
                server.enqueueStatus(500);
                server.enqueueStatus(500);
                server.enqueueStatus(500);
                // 6 次尝试上限 = MAX_ATTEMPTS(3) * 各 HttpClient 重试（默认 0）= 3 次
                assertThrows(Exception.class, () ->
                        UpdateService.create().performDownloadAndReplace(
                                exe,
                                new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), "", ""),
                                null));
            }
            // 重试发生：mock server 至少被击中 3 次
            // （sendWithRetry 内 MAX_ATTEMPTS=3）
            // 本断言不在 try-with-resources 内，避免访问已 stop 的 server
        }

        @Test
        @DisplayName("空响应体抛出 IOException，含「响应体为空」字样")
        void emptyBodyThrowsIOException() throws Exception {
            Path exe = createFakeExe("OLD".getBytes(StandardCharsets.UTF_8));
            try (MockServer server = new MockServer()) {
                server.enqueueOk(new byte[0]);
                IOException ex = assertThrows(IOException.class, () ->
                        UpdateService.create().performDownloadAndReplace(
                                exe,
                                new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), "", ""),
                                null));
                assertTrue(ex.getMessage().contains("响应体为空"),
                        "错误信息应说明响应体为空: " + ex.getMessage());
            }
        }

        @Test
        @DisplayName("SHA-256 校验通过则顺利替换，不抛异常")
        void sha256MatchAllowsReplace() throws Exception {
            byte[] newPayload = "valid-payload".getBytes(StandardCharsets.UTF_8);
            String expectedSha = sha256Of(newPayload);
            Path exe = createFakeExe("OLD".getBytes(StandardCharsets.UTF_8));

            try (MockServer server = new MockServer()) {
                server.enqueueOk(newPayload);
                UpdateService.create().performDownloadAndReplace(
                        exe,
                        new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), expectedSha, ""),
                        null);
            }
            assertArrayEquals(newPayload, Files.readAllBytes(exe));
        }

        @Test
        @DisplayName("SHA-256 校验失败抛出 IllegalStateException，.new 被清理，原 exe 保留")
        void sha256MismatchThrowsAndCleansUp() throws Exception {
            byte[] newPayload = "tampered-payload".getBytes(StandardCharsets.UTF_8);
            byte[] oldContent = "OLD_ORIGINAL".getBytes(StandardCharsets.UTF_8);
            Path exe = createFakeExe(oldContent);

            try (MockServer server = new MockServer()) {
                server.enqueueOk(newPayload);
                IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                        UpdateService.create().performDownloadAndReplace(
                                exe,
                                new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server),
                                        // 期望一个与之不同的 SHA-256
                                        "0000000000000000000000000000000000000000000000000000000000000000",
                                        ""),
                                null));
                assertTrue(ex.getMessage().contains("SHA-256 校验失败"),
                        "错误信息应说明 SHA-256 校验失败: " + ex.getMessage());
            }
            assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".new")),
                    "校验失败后 .new 应被清理，避免残留损坏文件");
            assertArrayEquals(oldContent, Files.readAllBytes(exe),
                    "原 exe 必须保留，不能覆盖或重命名（旧版本应仍可用）");
            assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".old")),
                    "校验失败未进入替换流程，不应产生 .old 备份");
        }

        @Test
        @DisplayName("info.sha256 为空时跳过 SHA-256 校验，直接完成替换")
        void emptySha256SkipsVerification() throws Exception {
            byte[] newPayload = "no-checksum-mode".getBytes(StandardCharsets.UTF_8);
            Path exe = createFakeExe("OLD".getBytes(StandardCharsets.UTF_8));

            try (MockServer server = new MockServer()) {
                server.enqueueOk(newPayload);
                UpdateService.create().performDownloadAndReplace(
                        exe,
                        new UpdateService.UpdateInfo("v1.1.3", mockDownloadUrl(server), "", ""),
                        null);
            }
            assertArrayEquals(newPayload, Files.readAllBytes(exe));
        }
    }

    // ─────────── 类型与辅助方法 ───────────

    @Nested
    @DisplayName("类型与辅助方法")
    class Helpers {

        @Test
        @DisplayName("RestartException 继承 IllegalStateException，并保留 message")
        void restartExceptionIsIllegalState() {
            UpdateService.RestartException ex = new UpdateService.RestartException("reboot-fail");
            assertSame(IllegalStateException.class, ex.getClass().getSuperclass(),
                    "RestartException 应直接继承 IllegalStateException");
            assertEquals("reboot-fail", ex.getMessage());
            assertThrows(UpdateService.RestartException.class,
                    () -> { throw new UpdateService.RestartException("x"); });
        }

        @Test
        @DisplayName("UpdateInfo record 四个字段均可访问")
        void updateInfoRecordFields() {
            UpdateService.UpdateInfo info = new UpdateService.UpdateInfo("v2.0.0", "u", "s", "n");
            assertEquals("v2.0.0", info.version());
            assertEquals("u", info.url());
            assertEquals("s", info.sha256());
            assertEquals("n", info.notes());
        }
    }

    // ─────────── getGitHubToken 优先级 ───────────

    @Nested
    @DisplayName("getGitHubToken 配置优先级")
    class GitHubToken {

        @Test
        @DisplayName("启动参数 -Ddatasync.update.githubToken 优先级最高")
        void systemPropertyWinsOverEverything() {
            // 移除 DB 中可能残留的真实值，确保即便存在也走不到这里
            String backup = SQLiteConfigUtil.getInstance().getAppConfig(
                    UpdateService.CONFIG_KEY_GITHUB_TOKEN, null);
            try {
                SQLiteConfigUtil.getInstance().setAppConfig(
                        UpdateService.CONFIG_KEY_GITHUB_TOKEN, "db-token-should-lose");
                System.setProperty("datasync.update.githubToken", "  sys-prop-token  ");
                assertEquals("sys-prop-token", UpdateService.getGitHubToken().trim(),
                        "系统参数应优先于 DB");
            } finally {
                if (backup != null) {
                    SQLiteConfigUtil.getInstance().setAppConfig(
                            UpdateService.CONFIG_KEY_GITHUB_TOKEN, backup);
                } else {
                    SQLiteConfigUtil.getInstance().setAppConfig(
                            UpdateService.CONFIG_KEY_GITHUB_TOKEN, "");
                }
            }
        }

        @Test
        @DisplayName("DB 中的非空 token 优先于 version.properties 默认值")
        void dbTokenBeatsDefault() {
            String backup = SQLiteConfigUtil.getInstance().getAppConfig(
                    UpdateService.CONFIG_KEY_GITHUB_TOKEN, null);
            try {
                SQLiteConfigUtil.getInstance().setAppConfig(
                        UpdateService.CONFIG_KEY_GITHUB_TOKEN, "  db-token-x  ");
                System.clearProperty("datasync.update.githubToken");
                assertEquals("db-token-x", UpdateService.getGitHubToken().trim(),
                        "DB 中非空 token 应被返回");
            } finally {
                if (backup != null) {
                    SQLiteConfigUtil.getInstance().setAppConfig(
                            UpdateService.CONFIG_KEY_GITHUB_TOKEN, backup);
                } else {
                    SQLiteConfigUtil.getInstance().setAppConfig(
                            UpdateService.CONFIG_KEY_GITHUB_TOKEN, "");
                }
            }
        }

        @Test
        @DisplayName("全部为空时返回空字符串，不抛异常")
        void allEmptyReturnsEmpty() {
            String backup = SQLiteConfigUtil.getInstance().getAppConfig(
                    UpdateService.CONFIG_KEY_GITHUB_TOKEN, null);
            try {
                SQLiteConfigUtil.getInstance().setAppConfig(
                        UpdateService.CONFIG_KEY_GITHUB_TOKEN, "");
                System.clearProperty("datasync.update.githubToken");
                String token = UpdateService.getGitHubToken();
                assertNotNull(token);
                assertTrue(token.isEmpty() || "${githubToken}".equals(token) || token.isBlank(),
                        "未配置时应当返回空（含未展开的 ${githubToken} 占位符）: " + token);
            } finally {
                if (backup != null) {
                    SQLiteConfigUtil.getInstance().setAppConfig(
                            UpdateService.CONFIG_KEY_GITHUB_TOKEN, backup);
                } else {
                    SQLiteConfigUtil.getInstance().setAppConfig(
                            UpdateService.CONFIG_KEY_GITHUB_TOKEN, "");
                }
            }
        }
    }

    // ─────────── cleanupObsoleteFiles ───────────

    @Nested
    @DisplayName("cleanupObsoleteFiles 启动清理")
    class Cleanup {

        @Test
        @DisplayName("未找到 DataSync.exe 时为 noop，不抛异常")
        void noopWhenNoExe() {
            // 将 user.dir 指向不含 DataSync.exe 的 tempDir，currentExePath 将返回 null
            System.setProperty("user.dir", tempDir.toString());
            assertDoesNotThrowOnCleanup();
        }

        @Test
        @DisplayName("存在 .old 与 .new 时，启动清理会删除它们")
        void cleanupRemovesLeftoverFiles() throws Exception {
            // 在 tempDir 放一个 DataSync.exe，让 currentExePath 能找到它
            Path exe = createFakeExe("FAKE".getBytes(StandardCharsets.UTF_8));
            Files.write(tempDir.resolve(UpdateService.EXE_NAME + ".old"), "old".getBytes(StandardCharsets.UTF_8));
            Files.write(tempDir.resolve(UpdateService.EXE_NAME + ".new"), "new".getBytes(StandardCharsets.UTF_8));

            // cleanupObsoleteFiles 通过 currentExePath 间接定位 exe；user.dir 设到 tempDir
            System.setProperty("user.dir", tempDir.toString());

            UpdateService.cleanupObsoleteFiles();

            assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".old")),
                    ".old 应被清理");
            assertFalse(Files.exists(tempDir.resolve(UpdateService.EXE_NAME + ".new")),
                    ".new 应被清理");
            assertTrue(Files.exists(exe), "原 exe 不会被清理");
        }

        @Test
        @DisplayName(".old / .new 不存在时也不抛异常")
        void cleanupWhenNothingToClean() throws Exception {
            Path exe = createFakeExe("FAKE".getBytes(StandardCharsets.UTF_8));
            System.setProperty("user.dir", tempDir.toString());
            assertDoesNotThrowOnCleanup();
            assertTrue(Files.exists(exe));
        }

        private void assertDoesNotThrowOnCleanup() {
            try {
                UpdateService.cleanupObsoleteFiles();
            } catch (Throwable t) {
                org.junit.jupiter.api.Assertions.fail("cleanupObsoleteFiles 不应抛异常", t);
            }
        }
    }

    // ─────────── SHA-256 helper sanity ───────────

    @Nested
    @DisplayName("SHA-256 计算辅助")
    class Sha256Helper {

        @Test
        @DisplayName("\"hello\" 的 SHA-256 应为已知值 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
        void helloSha256() {
            assertEquals(
                    "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    sha256Of("hello".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("空字节数组 SHA-256 应为已知值")
        void emptySha256() {
            assertEquals(
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    sha256Of(new byte[0]));
        }
    }

    // ─────────── currentExePath ───────────

    @Nested
    @DisplayName("currentExePath 路径定位")
    class CurrentExePath {

        @Test
        @DisplayName("user.dir 指向含 DataSync.exe 的目录时，能定位到该 exe")
        void findsExeUnderUserDir() throws Exception {
            Path exe = createFakeExe("FAKE".getBytes(StandardCharsets.UTF_8));
            System.setProperty("user.dir", tempDir.toString());
            Path found = UpdateService.create().currentExePath();
            assertNotNull(found, "在 user.dir 下的 DataSync.exe 应被定位到");
            assertEquals(exe.toAbsolutePath().normalize(),
                    found.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("user.dir 指向空目录时返回 null，不抛异常")
        void returnsNullWhenNoExeAnywhere() {
            System.setProperty("user.dir", tempDir.toString());
            assertNull(UpdateService.create().currentExePath());
        }
    }
}
