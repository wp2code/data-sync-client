package com.omnikit.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * HTTP 静态服务的启动参数：从程序配置的「参数」文本解析而来。
 * <p>
 * 支持写法（与 node http-server 的习惯一致）：
 * <ul>
 *   <li>{@code 8080} — 直接写端口号</li>
 *   <li>{@code -p 8080} / {@code --port 8080} / {@code --port=8080} — 指定监听端口</li>
 *   <li>{@code -a 127.0.0.1} / {@code --address 127.0.0.1} / {@code --host=localhost} — 指定监听/访问地址，
 *   缺省监听全部网卡（访问地址展示为 localhost）</li>
 *   <li>{@code -P http://127.0.0.1:9000} / {@code --proxy http://127.0.0.1:9000} / {@code --proxy=http://...}
 *   — 本地文件不存在时把请求转发到该地址（前端静态资源 + 后端接口联调场景）</li>
 *   <li>{@code --proxy-prefix /api} / {@code --proxy-prefix=/api,/admin} — 接口前缀：命中前缀的请求无条件走代理，
 *   不被本地同名静态文件遮蔽；可重复配置或用逗号分隔多个</li>
 *   <li>{@code --cors} / {@code --cors=headers} — 开启 CORS：默认响应
 *   {@code Access-Control-Allow-Origin: *} 等头，并本地应答 OPTIONS 预检； 可选传入逗号分隔的 CORS 头列表，每项形如
 *   {@code 名称} 或 {@code 名称:值}（如
 *   {@code --cors=Access-Control-Allow-Origin:http://localhost:5173,Access-Control-Allow-Credentials:true}），
 *   只写名称时按内置默认值填充</li>
 *   <li>{@code -S} / {@code --tls} / {@code --ssl} — 启用 HTTPS（TLS/SSL）</li>
 *   <li>{@code -C cert.pem} / {@code --cert cert.pem} / {@code --cert=cert.pem} — TLS 证书文件路径（PEM，默认
 *   {@code cert.pem}）；相对路径优先按服务根目录解析</li>
 *   <li>{@code -K key.pem} / {@code --key key.pem} / {@code --key=key.pem} — TLS 私钥文件路径（PEM，默认
 *   {@code key.pem}）；相对路径优先按服务根目录解析，支持未加密的 PKCS#8 或 RSA PKCS#1 私钥</li>
 * </ul>
 * 代理转发对方法无限制：GET/HEAD/POST/PUT/DELETE 等接口请求均会携带原始路径、查询串、请求头与请求体转发。
 * </ul>
 * 无法识别或取值非法的片段记录到 {@link #getInvalidOptions()}，由调用方决定是否拦截启动。
 *
 * @author liuweiping
 * @date 2026-10-30
 **/
@Getter
public class HttpServerOptions {
    
    /**
     * 主机名/地址的合法字符（语法层面校验，真实解析延迟到启动时，避免解析阻塞 EDT）
     */
    private static final String HOST_PATTERN = "[A-Za-z0-9._:\\[\\]\\-]+";
    
    /**
     * 未指定 {@code --cert} 时的默认 TLS 证书文件名
     */
    public static final String DEFAULT_CERT_FILE = "cert.pem";
    
    /**
     * 未指定 {@code --key} 时的默认 TLS 私钥文件名
     */
    public static final String DEFAULT_KEY_FILE = "key.pem";
    
    private final int port;
    
    /**
     * 监听地址（IP 或主机名），空串表示监听全部网卡
     */
    private final String host;
    
    /**
     * 代理目标地址（已去掉末尾 /），未配置为空串
     */
    private final String proxy;
    
    /**
     * 接口前缀：命中前缀的请求无条件走代理（需配合代理地址使用）
     */
    private final List<String> proxyPrefixes;
    
    /**
     * 是否开启 CORS（开启后所有响应附带 CORS 头，并本地应答 OPTIONS 预检）；对外判断见 {@link #isCorsEnabled()}
     */
    @Getter(AccessLevel.NONE)
    private final boolean cors;
    
    /**
     * CORS 响应头（名称 → 值），开启 CORS 时非空
     */
    private final Map<String, String> corsHeaders;
    
    /**
     * 是否启用 TLS/SSL（HTTPS）；对外判断见 {@link #isTlsEnabled()}
     */
    @Getter(AccessLevel.NONE)
    private final boolean tls;
    
    /**
     * TLS 证书文件路径（PEM），未指定为默认 {@value #DEFAULT_CERT_FILE}
     */
    private final String certPath;
    
    /**
     * TLS 私钥文件路径（PEM），未指定为默认 {@value #DEFAULT_KEY_FILE}
     */
    private final String keyPath;
    
    /**
     * 非法参数列表；对外只读视图见 {@link #getInvalidOptions()}
     */
    @Getter(AccessLevel.NONE)
    private final List<String> invalidOptions;
    
    public HttpServerOptions(int port, String host, String proxy, List<String> proxyPrefixes, boolean cors, Map<String, String> corsHeaders,
            boolean tls, String certPath, String keyPath, List<String> invalidOptions) {
        this.port = port;
        this.host = host == null ? "" : host;
        this.proxy = proxy == null ? "" : proxy;
        this.proxyPrefixes = proxyPrefixes == null ? List.of() : List.copyOf(proxyPrefixes);
        this.cors = cors;
        this.corsHeaders = corsHeaders == null ? Map.of() : Map.copyOf(corsHeaders);
        this.tls = tls;
        this.certPath = certPath == null || certPath.isBlank() ? DEFAULT_CERT_FILE : certPath.trim();
        this.keyPath = keyPath == null || keyPath.isBlank() ? DEFAULT_KEY_FILE : keyPath.trim();
        this.invalidOptions = invalidOptions == null ? List.of() : List.copyOf(invalidOptions);
    }
    
    /**
     * 解析参数文本：空白分词，支持 {@code --name=value} 与 {@code --name value} 两种写法。 非法片段不抛异常，收集到 invalidOptions
     */
    public static HttpServerOptions parse(String arguments) {
        int port = HttpStaticFileServer.DEFAULT_PORT;
        String host = "";
        String proxy = "";
        List<String> prefixes = new ArrayList<>();
        boolean cors = false;
        Map<String, String> corsHeaders = new LinkedHashMap<>();
        boolean tls = false;
        String cert = null;
        String key = null;
        List<String> invalid = new ArrayList<>();
        if (arguments != null && !arguments.isBlank()) {
            // 队列消费：选项取值时直接出队，避免回改循环索引
            Deque<String> tokens = new ArrayDeque<>(Arrays.asList(arguments.trim().split("\\s+")));
            while (!tokens.isEmpty()) {
                String token = tokens.poll();
                int eq = token.indexOf('=');
                String name = eq > 0 ? token.substring(0, eq) : token;
                String inlineValue = eq > 0 ? token.substring(eq + 1) : null;
                switch (name) {
                    case "-p", "--port" -> {
                        Integer parsed = parsePort(takeValue(tokens, inlineValue));
                        if (parsed != null) {
                            port = parsed;
                        } else {
                            invalid.add("--port 需要 1-65535 的端口号");
                        }
                    }
                    case "-a", "--address", "--host" -> {
                        String value = takeValue(tokens, inlineValue);
                        if (isHostText(value)) {
                            host = value.trim();
                        } else {
                            invalid.add("--address 需要合法的 IP 或主机名（如 127.0.0.1、0.0.0.0、localhost）");
                        }
                    }
                    case "-P", "--proxy" -> {
                        String value = takeValue(tokens, inlineValue);
                        if (isHttpUrl(value)) {
                            proxy = trimTrailingSlash(value);
                        } else {
                            invalid.add("--proxy 需要 http:// 或 https:// 开头的地址");
                        }
                    }
                    case "--proxy-prefix", "--proxy-api", "--api-prefix" -> {
                        List<String> parsed = parsePrefixes(takeValue(tokens, inlineValue));
                        if (parsed.isEmpty()) {
                            invalid.add("--proxy-prefix 需要接口路径前缀（如 /api，多个用逗号分隔）");
                        } else {
                            prefixes.addAll(parsed);
                        }
                    }
                    // 不带 -c 简写：与 http-server 的 -c（缓存秒数）语义冲突
                    case "--cors", "--enable-cors" -> {
                        // 值可选：单独出现用默认 CORS 头，--cors=headers / --cors headers 用指定头列表
                        String value = takeOptionalValue(tokens, inlineValue);
                        if (isOffFlag(value)) {
                            cors = false;
                            corsHeaders.clear();
                        } else {
                            cors = true;
                            Map<String, String> parsed = isCorsFlagOnly(value) ? defaultCorsHeaders() : parseCorsHeaders(value);
                            corsHeaders.putAll(parsed.isEmpty() ? defaultCorsHeaders() : parsed);
                        }
                    }
                    case "-S", "--tls", "--ssl" -> {
                        // TLS 开关：值可选（--tls / --tls=true 启用，--tls=false 关闭）
                        tls = !isOffFlag(takeOptionalValue(tokens, inlineValue));
                    }
                    case "-C", "--cert", "--certificate" -> {
                        String value = takeValue(tokens, inlineValue);
                        if (isPathText(value)) {
                            cert = value.trim();
                        } else {
                            invalid.add("--cert 需要 PEM 证书文件路径（如 cert.pem、D:\\cert\\server.pem）");
                        }
                    }
                    case "-K", "--key", "--private-key" -> {
                        String value = takeValue(tokens, inlineValue);
                        if (isPathText(value)) {
                            key = value.trim();
                        } else {
                            invalid.add("--key 需要 PEM 私钥文件路径（如 key.pem、D:\\cert\\server.key）");
                        }
                    }
                    default -> {
                        if (inlineValue != null) {
                            invalid.add("不支持的参数: " + name);
                        } else if (token.matches("\\d+")) {
                            // 裸端口号：仅在未通过选项指定过端口时生效
                            Integer parsed = parsePort(token);
                            if (parsed == null) {
                                invalid.add("端口超出范围: " + token);
                            } else if (port == HttpStaticFileServer.DEFAULT_PORT) {
                                port = parsed;
                            }
                        } else {
                            invalid.add("无法识别的参数: " + token);
                        }
                    }
                }
            }
        }
        if (!prefixes.isEmpty() && proxy.isBlank()) {
            invalid.add("--proxy-prefix 需配合 --proxy 代理地址使用");
        }
        // 指定了证书/私钥即视为启用 TLS（无需再写 -S/--tls）
        if ((cert != null || key != null) && !tls) {
            tls = true;
        }
        return new HttpServerOptions(port, host, proxy, prefixes, cors, corsHeaders, tls, cert, key, invalid);
    }
    
    public boolean isProxyEnabled() {
        return !proxy.isBlank();
    }
    
    public boolean isCorsEnabled() {
        return cors;
    }
    
    /**
     * 是否启用 TLS/SSL（HTTPS）
     */
    public boolean isTlsEnabled() {
        return tls;
    }
    
    /**
     * 非法参数列表（只读视图）
     */
    public List<String> getInvalidOptions() {
        return Collections.unmodifiableList(invalidOptions);
    }
    
    /**
     * 非法参数汇总（可直接展示给用户），无非法项返回 null
     */
    public String invalidMessage() {
        return invalidOptions.isEmpty() ? null : String.join("；", invalidOptions);
    }
    
    /**
     * 取选项值：优先 {@code --name=value} 的内联值，否则出队下一个 token
     */
    private static String takeValue(Deque<String> tokens, String inlineValue) {
        return inlineValue != null ? inlineValue : tokens.poll();
    }
    
    /**
     * 取可选值（如 {@code --cors}）：下一个 token 是选项（以 - 开头）时不消耗
     */
    private static String takeOptionalValue(Deque<String> tokens, String inlineValue) {
        if (inlineValue != null) {
            return inlineValue;
        }
        String next = tokens.peek();
        return next != null && !next.startsWith("-") ? tokens.poll() : null;
    }
    
    private static Integer parsePort(String value) {
        if (value == null || !value.matches("\\d+")) {
            return null;
        }
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 默认 CORS 响应头：允许任意来源的常用跨域配置
     */
    private static Map<String, String> defaultCorsHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.put("Access-Control-Max-Age", "86400");
        return headers;
    }
    
    /**
     * 解析 CORS 头列表：逗号分隔，每项 {@code 名称} 或 {@code 名称:值}（值可含 : ，如 http://host:5173）； 只写名称时按内置默认值填充
     */
    private static Map<String, String> parseCorsHeaders(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String item : value.split(",")) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int colon = entry.indexOf(':');
            String name = colon > 0 ? entry.substring(0, colon).trim() : entry;
            String headerValue = colon > 0 ? entry.substring(colon + 1).trim() : "";
            if (headerValue.isEmpty()) {
                headerValue = defaultCorsValue(name);
            }
            headers.put(name, headerValue);
        }
        return headers;
    }
    
    /**
     * 只写头名时的默认取值；未知头给空串（响应头值为空，浏览器忽略）
     */
    private static String defaultCorsValue(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "access-control-allow-origin" -> "*";
            case "access-control-allow-methods" -> "GET, POST, PUT, DELETE, OPTIONS, HEAD";
            case "access-control-allow-headers" -> "Content-Type, Authorization";
            case "access-control-max-age" -> "86400";
            default -> "";
        };
    }
    
    /**
     * 是否仅作为开关出现（无值或 true/on/1）
     */
    private static boolean isCorsFlagOnly(String value) {
        return value == null || value.isBlank() || "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value);
    }
    
    /**
     * 是否显式关闭开关（false/off/0/no）
     */
    private static boolean isOffFlag(String value) {
        return value != null && ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "0".equals(value)
                || "no".equalsIgnoreCase(value));
    }
    
    /**
     * 路径参数校验：非空且不以选项前缀 - 开头（避免吃掉后继选项）
     */
    private static boolean isPathText(String value) {
        return value != null && !value.trim().isBlank() && value.charAt(0) != '-';
    }
    
    /**
     * 解析接口前缀：逗号分隔，缺 / 自动补齐，去掉结尾 /（根路径 / 表示全部请求走代理）
     */
    private static List<String> parsePrefixes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String prefix = item.trim();
            if (prefix.isEmpty()) {
                continue;
            }
            if (!prefix.startsWith("/")) {
                prefix = "/" + prefix;
            }
            while (prefix.length() > 1 && prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            result.add(prefix);
        }
        return result;
    }
    
    /**
     * 地址语法校验：仅判断字符是否合法（含 IP、IPv6 方括号形式与主机名），不做 DNS 解析
     */
    private static boolean isHostText(String value) {
        return value != null && value.trim().matches(HOST_PATTERN);
    }
    
    private static boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
    
    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
