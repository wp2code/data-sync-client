package com.omnikit.core;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

/**
 * 内置 HTTP 静态文件服务（http-server）。
 * <p>
 * 基于 JDK 自带的 {@code com.sun.net.httpserver}，把指定目录以静态站点方式对外提供访问： 支持 index.html 默认页、目录列表、常见 MIME 类型与访问日志，无需依赖 Node/Python 等外部运行时。
 * <p>
 * 线程约定：{@link #start()} / {@link #stop()} 为阻塞方法，禁止在 EDT 调用； 请求由内部守护线程池处理，访问日志通过回调在请求线程发出，调用方需自行保证线程安全。
 *
 * @author liuweiping
 * @date 2026-10-30
 **/
public class HttpStaticFileServer {
    
    /**
     * 未指定端口时的默认监听端口
     */
    public static final int DEFAULT_PORT = 8080;
    
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private static final String[] INDEX_FILES = {"index.html", "index.htm"};
    
    /**
     * 代理请求超时（秒）
     */
    private static final int PROXY_TIMEOUT_SECONDS = 30;

    /**
     * 代理转发允许的最大请求体（10MB），超出返回 413，避免异常大包耗尽内存
     */
    private static final int MAX_PROXY_BODY_BYTES = 10 * 1024 * 1024;

    /**
     * 逐跳首部：转发请求/回写响应时均需剔除，避免破坏连接语义。content-length 由中转侧按实际 body 重新计算； content-encoding 需保留——body 原样透传，压缩标记必须与之一致
     */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te",
            "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
    
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"), Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "application/javascript; charset=UTF-8"), Map.entry("mjs", "application/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"), Map.entry("map", "application/json; charset=UTF-8"),
            Map.entry("xml", "application/xml; charset=UTF-8"), Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("md", "text/markdown; charset=UTF-8"), Map.entry("csv", "text/csv; charset=UTF-8"), Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("bmp", "image/bmp"), Map.entry("ico", "image/x-icon"), Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"), Map.entry("ttf", "font/ttf"), Map.entry("otf", "font/otf"),
            Map.entry("eot", "application/vnd.ms-fontobject"), Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
            Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"), Map.entry("pdf", "application/pdf"),
            Map.entry("wasm", "application/wasm"), Map.entry("zip", "application/zip"), Map.entry("gz", "application/gzip"),
            Map.entry("tar", "application/x-tar"), Map.entry("7z", "application/x-7z-compressed"));
    
    /**
     * 服务根目录（对外暴露的静态文件目录）
     */
    private final File rootDir;

    /**
     * 根目录的规范化绝对路径：请求解析时复用，避免每次请求重复计算
     */
    private final Path rootPath;
    
    @Getter
    private final int port;
    
    /**
     * 监听地址（空串表示监听全部网卡）
     */
    private final String host;
    
    /**
     * 代理目标地址（空串表示未启用）：本地文件不存在时把请求转发到该地址
     */
    private final String proxyTarget;
    
    /**
     * 接口前缀：命中前缀的请求无条件走代理（不被本地同名静态文件遮蔽）
     */
    private final List<String> proxyPrefixes;
    
    /**
     * 是否开启 CORS（所有响应附带 CORS 头，OPTIONS 预检本地应答）
     */
    private final boolean corsEnabled;
    
    /**
     * CORS 响应头（名称 → 值）
     */
    private final Map<String, String> corsHeaders;
    
    /**
     * 是否启用 TLS（HTTPS）：开启后以 {@link HttpsServer} 监听
     */
    private final boolean tlsEnabled;
    
    /**
     * TLS 证书文件路径（PEM），相对路径优先按服务根目录解析
     */
    private final String certPath;
    
    /**
     * TLS 私钥文件路径（PEM），相对路径优先按服务根目录解析
     */
    private final String keyPath;
    
    /**
     * 日志行回调（可为 null）：启动/停止与访问日志均通过该回调输出
     */
    private final Consumer<String> logConsumer;
    
    /**
     * 代理转发客户端：构造期创建（避免请求线程与 stop 争抢锁），其线程为守护线程
     */
    private final HttpClient proxyClient;
    
    private volatile HttpServer server;
    
    private ExecutorService executor;
    
    public HttpStaticFileServer(File rootDir, HttpServerOptions options, Consumer<String> logConsumer) {
        this.rootDir = rootDir;
        this.rootPath = rootDir == null ? null : rootDir.toPath().toAbsolutePath().normalize();
        this.port = options.getPort();
        this.host = options.getHost();
        this.proxyTarget = options.getProxy();
        this.proxyPrefixes = options.getProxyPrefixes();
        this.corsEnabled = options.isCorsEnabled();
        this.corsHeaders = options.getCorsHeaders();
        this.tlsEnabled = options.isTlsEnabled();
        this.certPath = options.getCertPath();
        this.keyPath = options.getKeyPath();
        this.logConsumer = logConsumer;
        this.proxyClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }
    
    /**
     * 启动静态文件服务。
     *
     * @return 启动成功返回 null，失败返回可直接展示给用户的错误提示
     */
    public synchronized String start() {
        if (server != null) {
            return (tlsEnabled ? "HTTPS" : "HTTP") + " 服务已在运行中，请先停止";
        }
        if (rootDir == null || !rootDir.isDirectory()) {
            return "HTTP 服务目录无效或不存在: " + (rootDir == null ? "(空)" : rootDir.getAbsolutePath());
        }
        try {
            executor = Executors.newFixedThreadPool(4, new ServerThreadFactory());
            HttpServer created = createServer();
            created.createContext("/", this::handle);
            created.setExecutor(executor);
            created.start();
            server = created;
        } catch (TlsCertificateLoader.TlsLoadException e) {
            shutdownExecutor();
            return "启动 HTTPS 服务失败：" + e.getMessage();
        } catch (UnknownHostException e) {
            shutdownExecutor();
            return "启动 " + serviceLabel() + "服务失败：监听地址无效 " + host;
        } catch (IOException e) {
            shutdownExecutor();
            String message = e.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("address already in use")) {
                return "启动 " + serviceLabel() + "服务失败：端口 " + port + " 已被占用";
            }
            return "启动 " + serviceLabel() + "服务失败：" + (message == null ? e.getClass().getSimpleName() : message);
        }
        log(describeStart());
        return null;
    }

    /**
     * 创建监听服务：启用 TLS 时返回装配好 SSLContext 的 HttpsServer，否则返回普通 HttpServer
     */
    private HttpServer createServer() throws IOException, TlsCertificateLoader.TlsLoadException {
        InetSocketAddress address = bindAddress();
        if (!tlsEnabled) {
            return HttpServer.create(address, 0);
        }
        File cert = resolveTlsFile(certPath, HttpServerOptions.DEFAULT_CERT_FILE);
        File key = resolveTlsFile(keyPath, HttpServerOptions.DEFAULT_KEY_FILE);
        SSLContext ssl = TlsCertificateLoader.createContext(cert, key);
        HttpsServer https = HttpsServer.create(address, 0);
        https.setHttpsConfigurator(new HttpsConfigurator(ssl));
        return https;
    }
    
    /**
     * 解析 TLS 文件路径：绝对路径直接使用；相对路径优先按服务根目录解析（证书常与静态站点同目录）， 否则按进程工作目录解析
     */
    private File resolveTlsFile(String configured, String defaultName) {
        String value = configured == null || configured.isBlank() ? defaultName : configured.trim();
        File file = new File(value);
        if (!file.isAbsolute() && rootDir != null) {
            File candidate = new File(rootDir, value);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return file;
    }
    
    /**
     * 启动失败提示中的服务名前缀：启用 TLS 时为「HTTPS 」，否则为「HTTP 」
     */
    private String serviceLabel() {
        return tlsEnabled ? "HTTPS " : "HTTP ";
    }

    /**
     * 启动摘要：地址、根目录、代理、CORS 与 TLS 配置
     */
    private String describeStart() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("[http-server] 服务已启动 ").append(getUrl()).append("，根目录: ").append(rootDir.getAbsolutePath());
        if (tlsEnabled) {
            sb.append("，TLS: 已启用（证书 ").append(resolveTlsFile(certPath, HttpServerOptions.DEFAULT_CERT_FILE).getAbsolutePath())
                    .append("，私钥 ").append(resolveTlsFile(keyPath, HttpServerOptions.DEFAULT_KEY_FILE).getAbsolutePath()).append('）');
        }
        if (isProxyEnabled()) {
            sb.append("，代理: ").append(proxyTarget)
                    .append(proxyPrefixes.isEmpty()
                            ? "（本地文件不存在或接口请求时转发）"
                            : "（接口前缀 " + String.join(",", proxyPrefixes) + " 及本地文件不存在时转发）");
        }
        if (corsEnabled) {
            sb.append("，CORS: 已开启（");
            for (Map.Entry<String, String> entry : corsHeaders.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('、');
            }
            sb.setLength(sb.length() - 1);
            sb.append('）');
        }
        return sb.toString();
    }
    
    /**
     * 停止服务：关闭监听与请求线程池。阻塞方法，禁止在 EDT 调用
     */
    public synchronized void stop() {
        HttpServer current = server;
        server = null;
        if (current == null) {
            return;
        }
        current.stop(0);
        shutdownExecutor();
        log("[http-server] 服务已停止");
    }
    
    public boolean isRunning() {
        return server != null;
    }
    
    /**
     * 监听地址：未配置地址（或通配 0.0.0.0 / :: / *）时监听全部网卡，否则绑定到指定地址
     */
    private InetSocketAddress bindAddress() throws UnknownHostException {
        // 未配置或通配：交由系统选择（监听全部网卡），本机与局域网均可访问
        if (host.isBlank() || "*".equals(host)) {
            return new InetSocketAddress(port);
        }
        return new InetSocketAddress(InetAddress.getByName(host), port);
    }
    
    /**
     * 服务访问地址：配置了具体地址时用该地址，通配监听时展示 localhost；启用 TLS 时为 https
     */
    public String getUrl() {
        return (tlsEnabled ? "https://" : "http://") + displayHost() + ":" + port + "/";
    }
    
    private String displayHost() {
        if (host.isBlank() || "*".equals(host) || "0.0.0.0".equals(host) || "::".equals(host)) {
            return "localhost";
        }
        // IPv6 文本地址需加方括号
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }
    
    // ────────── 请求处理 ──────────
    
    /**
     * 统一请求入口，按以下顺序处理：
     * <ol>
     *   <li>开启 CORS 时的 OPTIONS 预检 → 本地应答 204 并带 CORS 头</li>
     *   <li>配置了代理且命中接口前缀（--proxy-prefix）→ 直接转发，不被本地同名静态文件遮蔽</li>
     *   <li>非 GET/HEAD 的接口请求（POST/PUT/DELETE 等）→ 配置代理则转发，否则 405</li>
     *   <li>GET/HEAD → 静态文件：目录优先返回 index 页，否则目录列表；本地文件不存在且配置代理时转发</li>
     * </ol>
     */
    private void handle(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        String rawPath = exchange.getRequestURI() == null ? "/" : exchange.getRequestURI().getRawPath();
        long begin = System.currentTimeMillis();
        // status 0 表示未产生响应（连接中断），size 为负表示响应体大小未知（代理转发）
        int status = 0;
        long size = -1;
        boolean proxied = false;
        try {
            if (corsEnabled && "OPTIONS".equals(method)) {
                // 跨域预检：无需后端参与，本地直接应答
                status = sendPreflight(exchange);
            } else if (isProxyEnabled() && matchesProxyPrefix(rawPath)) {
                // 接口前缀命中：无条件转发（含 GET），本地静态资源不参与
                status = proxyRequest(exchange, method, rawPath);
                proxied = true;
            } else if (!"GET".equals(method) && !"HEAD".equals(method)) {
                if (isProxyEnabled()) {
                    // 接口写请求：整体转发给后端
                    status = proxyRequest(exchange, method, rawPath);
                    proxied = true;
                } else {
                    status = sendText(exchange, 405, "405 Method Not Allowed");
                }
            } else {
                File file = resolveTarget(rawPath);
                if (file == null) {
                    status = sendText(exchange, 403, "403 Forbidden");
                } else if (!file.exists()) {
                    if (isProxyEnabled()) {
                        // 本地无该文件且配置了代理：转发到目标地址（前端静态资源 + 后端接口联调）
                        status = proxyRequest(exchange, method, rawPath);
                        proxied = true;
                    } else {
                        status = sendText(exchange, 404, "404 Not Found: " + decode(rawPath));
                    }
                } else if (file.isDirectory()) {
                    File index = findIndexFile(file);
                    if (index != null) {
                        size = index.length();
                        status = sendFile(exchange, index, method);
                    } else {
                        byte[] body = buildDirectoryListing(file, rawPath);
                        size = body.length;
                        status = sendBytes(exchange, 200, "text/html; charset=UTF-8", body, method);
                    }
                } else {
                    size = file.length();
                    status = sendFile(exchange, file, method);
                }
            }
        } catch (IOException e) {
            // 客户端提前断开等情形：不向控制台抛异常，仅结束本次交换
            status = 0;
        } finally {
            exchange.close();
            logAccess(exchange, method, rawPath, status, size, begin, proxied);
        }
    }
    
    /**
     * 是否配置了代理地址
     */
    public boolean isProxyEnabled() {
        return !proxyTarget.isBlank();
    }
    
    /**
     * 代理转发：把原始请求的方法、接口路径与查询串、请求头与请求体完整发给代理目标， 回写上游状态码、响应头与响应体。转发失败返回 502，地址非法返回 400
     */
    private int proxyRequest(HttpExchange exchange, String method, String rawPath) {
        String target = buildProxyTarget(exchange, rawPath);
        byte[] requestBody;
        try {
            // 请求体仅对带 body 的方法读取（GET/HEAD/TRACE 无 body）
            requestBody = hasRequestBody(method) ? readRequestBody(exchange) : new byte[0];
        } catch (IOException e) {
            return sendTextQuietly(exchange, 400, "400 Bad Request: 读取请求体失败");
        }
        if (requestBody.length > MAX_PROXY_BODY_BYTES) {
            return sendTextQuietly(exchange, 413, "413 Payload Too Large: 请求体超过 " + formatSize(MAX_PROXY_BODY_BYTES));
        }
        HttpResponse<byte[]> response;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(PROXY_TIMEOUT_SECONDS));
            copyRequestHeaders(exchange, builder);
            // Builder 无 HEAD() 便捷方法，且 GET 不允许带 body，分别处理
            if ("HEAD".equals(method)) {
                builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method,
                        requestBody.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(requestBody));
            }
            response = proxyClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IllegalArgumentException e) {
            return sendTextQuietly(exchange, 400, "400 Bad Request: 代理地址非法 " + target);
        } catch (IOException e) {
            log("[http-server] 代理转发失败 " + method + " " + target + ": " + e.getMessage());
            return sendTextQuietly(exchange, 502, "502 Bad Gateway: 代理请求失败 " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("[http-server] 代理转发被中断 " + target);
            return sendTextQuietly(exchange, 502, "502 Bad Gateway: 代理请求被中断");
        }
        int status = response.statusCode();
        byte[] body = response.body() == null ? new byte[0] : response.body();
        copyResponseHeaders(exchange, response.headers().map());
        applyCors(exchange);
        try {
            if (status == 204 || status == 304 || "HEAD".equals(method) || body.length == 0) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } catch (IOException e) {
            // 客户端提前断开：不算转发失败，返回 0 由外层按「连接中断」记录
            return 0;
        }
        return status;
    }
    
    /**
     * 请求路径是否命中配置的接口前缀：路径等于前缀或以「前缀 + /」开头。 前缀为 / 时表示全部请求走代理
     */
    private boolean matchesProxyPrefix(String rawPath) {
        if (proxyPrefixes.isEmpty()) {
            return false;
        }
        if (proxyPrefixes.contains("/")) {
            return true;
        }
        String path = decode(rawPath);
        for (String prefix : proxyPrefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 是否存在请求体：GET/HEAD/TRACE 不带 body
     */
    private static boolean hasRequestBody(String method) {
        return switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) {
            case "GET", "HEAD", "TRACE" -> false;
            default -> true;
        };
    }
    
    /**
     * 读取原始请求体（接口调用的 JSON/表单等），原样转发给代理目标
     */
    private static byte[] readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            // 多读 1 字节用于判定超限，避免无上限读入内存
            return in.readNBytes(MAX_PROXY_BODY_BYTES + 1);
        }
    }
    
    /**
     * 拼接代理目标地址：代理基地址 + 原始接口路径 + 原始查询串（保留编码，不做二次编解码）
     */
    private String buildProxyTarget(HttpExchange exchange, String rawPath) {
        String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        String query = exchange.getRequestURI() == null ? null : exchange.getRequestURI().getRawQuery();
        return proxyTarget + path + (query == null || query.isEmpty() ? "" : "?" + query);
    }
    
    /**
     * 复制客户端请求头到代理请求（剔除逐跳首部）；同名多值用逗号拼接
     */
    private void copyRequestHeaders(HttpExchange exchange, HttpRequest.Builder builder) {
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            for (String value : values) {
                builder.header(name, value);
            }
        });
    }
    
    /**
     * 复制上游响应头到客户端响应（剔除逐跳首部与长度相关首部，长度由本地重新写入）
     */
    private void copyResponseHeaders(HttpExchange exchange, Map<String, List<String>> headers) {
        headers.forEach((name, values) -> {
            if (name == null || HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            for (String value : values) {
                exchange.getResponseHeaders().add(name, value);
            }
        });
    }
    
    /**
     * 代理异常场景的兜底响应：响应写入失败（客户端断开）时静默返回状态码
     */
    private int sendTextQuietly(HttpExchange exchange, int status, String text) {
        try {
            return sendText(exchange, status, text);
        } catch (IOException e) {
            return status;
        }
    }
    
    /**
     * 附加 CORS 响应头：已存在的同名头（如后端已返回）不覆盖，避免重复
     */
    private void applyCors(HttpExchange exchange) {
        if (!corsEnabled) {
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        corsHeaders.forEach((name, value) -> {
            if (!headers.containsKey(name)) {
                headers.add(name, value);
            }
        });
    }
    
    /**
     * CORS 预检应答：204 + CORS 头，并补齐预检必需的 Allow-Methods / Allow-Headers / Max-Age
     */
    private int sendPreflight(HttpExchange exchange) {
        applyCors(exchange);
        Headers headers = exchange.getResponseHeaders();
        if (!headers.containsKey("Access-Control-Allow-Methods")) {
            headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        }
        if (!headers.containsKey("Access-Control-Allow-Headers")) {
            headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
        if (!headers.containsKey("Access-Control-Max-Age")) {
            headers.add("Access-Control-Max-Age", "86400");
        }
        try {
            // 204/304 不允许带响应体：长度传 -1
            exchange.sendResponseHeaders(204, -1);
        } catch (IOException e) {
            // 客户端提前断开：状态码仍按 204 记录
        }
        return 204;
    }
    
    /**
     * 解析请求路径对应的本地文件：解码后按根目录解析并规范化，越界（路径穿越）返回 null
     */
    private File resolveTarget(String rawPath) {
        if (rootPath == null) {
            return null;
        }
        // 必须去掉前导分隔符：URI 路径以 / 开头，直接 resolve 会被当作绝对路径而绕过根目录
        String relative = stripLeadingSeparators(decode(rawPath));
        Path resolved;
        try {
            resolved = rootPath.resolve(relative).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
        return resolved.startsWith(rootPath) ? resolved.toFile() : null;
    }
    
    private static String stripLeadingSeparators(String path) {
        int index = 0;
        while (index < path.length() && (path.charAt(index) == '/' || path.charAt(index) == '\\')) {
            index++;
        }
        return path.substring(index);
    }
    
    private static File findIndexFile(File dir) {
        for (String name : INDEX_FILES) {
            File candidate = new File(dir, name);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }
    
    private int sendFile(HttpExchange exchange, File file, String method) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        applyCors(exchange);
        long length = file.length();
        if ("HEAD".equals(method)) {
            exchange.sendResponseHeaders(200, -1);
            return 200;
        }
        exchange.sendResponseHeaders(200, length);
        try (OutputStream out = exchange.getResponseBody();
                FileInputStream in = new FileInputStream(file)) {
            in.transferTo(out);
        }
        return 200;
    }
    
    private int sendText(HttpExchange exchange, int status, String text) throws IOException {
        return sendBytes(exchange, status, "text/plain; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8), exchange.getRequestMethod());
    }
    
    private int sendBytes(HttpExchange exchange, int status, String contentType, byte[] body, String method) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        applyCors(exchange);
        if ("HEAD".equals(method)) {
            exchange.sendResponseHeaders(status, -1);
            return status;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        return status;
    }
    
    /**
     * 生成目录列表页面（UTF-8 编码的简单 HTML），含上级目录链接与子项大小
     */
    private byte[] buildDirectoryListing(File dir, String rawPath) {
        String requestPath = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        if (!requestPath.endsWith("/")) {
            requestPath = requestPath + "/";
        }
        // 标题展示解码后的路径（中文目录名可读），链接仍用编码后的 requestPath
        String title = "目录: " + decode(requestPath);
        StringBuilder sb = new StringBuilder(256);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>").append(escape(title))
                .append("</title></head><body style=\"font-family:sans-serif\"><h3>").append(escape(title)).append("</h3><ul>");
        if (!"/".equals(requestPath)) {
            sb.append("<li><a href=\"").append(escape(parentPath(requestPath))).append("\">../</a></li>");
        }
        File[] children = dir.listFiles();
        if (children != null) {
            Arrays.sort(children, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File child : children) {
                String name = child.getName();
                String href = requestPath + encodeSegment(name);
                sb.append("<li><a href=\"").append(escape(href)).append("\">").append(escape(name));
                if (child.isDirectory()) {
                    sb.append("/</a></li>");
                } else {
                    sb.append("</a> <span style=\"color:#888\">(").append(formatSize(child.length())).append(")</span></li>");
                }
            }
        }
        sb.append("</ul></body></html>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * 请求路径的上一级（保证以 / 结尾）
     */
    private static String parentPath(String requestPath) {
        String path = requestPath.endsWith("/") ? requestPath.substring(0, requestPath.length() - 1) : requestPath;
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash + 1);
    }
    
    private void logAccess(HttpExchange exchange, String method, String rawPath, int status, long size, long begin, boolean proxied) {
        String client = exchange.getRemoteAddress() == null ? "-" : exchange.getRemoteAddress().getAddress().getHostAddress();
        StringBuilder sb = new StringBuilder(64);
        sb.append('[').append(LocalTime.now().format(CLOCK)).append("] ").append(client).append(' ').append(method == null ? "-" : method).append(' ')
                .append(decode(rawPath));
        if (status > 0) {
            sb.append(" → ").append(status);
            if (proxied) {
                sb.append(" (代理)");
            }
            if (size > 0) {
                sb.append(' ').append(formatSize(size));
            }
        } else {
            sb.append(" → 连接中断");
        }
        sb.append(' ').append(System.currentTimeMillis() - begin).append("ms");
        log(sb.toString());
    }
    
    private void log(String line) {
        if (logConsumer != null) {
            logConsumer.accept(line);
        }
    }
    
    private void shutdownExecutor() {
        ExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
    }
    
    // ────────── 工具方法 ──────────
    
    private static String decode(String rawPath) {
        if (rawPath == null) {
            return "/";
        }
        try {
            String decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
            return decoded.isEmpty() ? "/" : decoded;
        } catch (IllegalArgumentException e) {
            return rawPath;
        }
    }
    
    private static String encodeSegment(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }
    
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    
    private static String contentType(File file) {
        try {
            String probed = Files.probeContentType(file.toPath());
            if (probed != null && !probed.isBlank()) {
                return probed.startsWith("text/") ? probed + "; charset=UTF-8" : probed;
            }
        } catch (IOException ignored) {
            // 探测失败时按扩展名映射
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String mapped = MIME_TYPES.get(name.substring(dot + 1));
            if (mapped != null) {
                return mapped;
            }
        }
        return "application/octet-stream";
    }
    
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
    }
    
    /**
     * 服务线程工厂：守护线程，避免请求线程阻塞应用退出
     */
    private static final class ServerThreadFactory implements ThreadFactory {
        
        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r, "http-server-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
