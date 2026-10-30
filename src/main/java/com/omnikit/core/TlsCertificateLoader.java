package com.omnikit.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * HTTPS/TLS 证书加载器：把 PEM 证书与私钥装配成 {@link SSLContext}，供 {@link HttpStaticFileServer} 以 HTTPS 方式对外提供服务。
 * <p>
 * 支持的 PEM 片段：
 * <ul>
 *   <li>证书：{@code -----BEGIN CERTIFICATE-----}（多段即视为证书链，leaf 取第一段）</li>
 *   <li>私钥：{@code -----BEGIN PRIVATE KEY-----}（PKCS#8）或 {@code -----BEGIN RSA PRIVATE KEY-----}（PKCS#1，内部转换为 PKCS#8）；
 *   加密私钥（{@code ENCRYPTED PRIVATE KEY}）与带口令的文件不支持</li>
 * </ul>
 * JDK 17 无 PEM 解析 API，此处按块正则 + Base64 手工解析，不依赖第三方库。
 *
 * @author liuweiping
 * @date 2026-10-30
 **/
public final class TlsCertificateLoader {
    
    /**
     * PEM 块：标签（如 CERTIFICATE）与 Base64 主体
     */
    private record PemBlock(String label, byte[] der) {
    }
    
    /**
     * PEM 块正则：按名称成对匹配 BEGIN/END，支持带空格的标签（如 RSA PRIVATE KEY）
     */
    private static final Pattern PEM_BLOCK = Pattern.compile("-----BEGIN ([A-Za-z0-9 ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);
    
    /**
     * 私钥算法候选：PKCS#8 本身携带算法 OID，此处按常见顺序逐个尝试，直到某个算法解析成功
     */
    private static final List<String> KEY_ALGORITHMS = List.of("RSA", "EC", "Ed25519", "Ed448", "XDH", "DSA");
    
    /**
     * rsaEncryption OID：把 PKCS#1 私钥包装成 PKCS#8 时用到
     */
    private static final String RSA_OID = "1.2.840.113549.1.1.1";
    
    private static final char[] NO_PASSWORD = new char[0];
    
    private TlsCertificateLoader() {
    }
    
    /**
     * 加载证书与私钥并构建 {@code TLS} 协议的 SSLContext。
     *
     * @param certFile PEM 证书文件（可含证书链）
     * @param keyFile  PEM 私钥文件（未加密的 PKCS#8 或 RSA PKCS#1）
     * @return 可直接交给 {@code HttpsServer} 使用的上下文
     * @throws TlsLoadException 文件缺失、格式非法或组装失败时抛出（message 可直接展示给用户）
     */
    public static SSLContext createContext(File certFile, File keyFile) throws TlsLoadException {
        requireRegularFile(certFile, "TLS 证书");
        requireRegularFile(keyFile, "TLS 私钥");
        List<X509Certificate> chain = loadCertificates(certFile);
        PrivateKey privateKey = loadPrivateKey(keyFile);
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, NO_PASSWORD);
            store.setKeyEntry("omnikit-https", privateKey, NO_PASSWORD, chain.toArray(X509Certificate[]::new));
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, NO_PASSWORD);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            return context;
        } catch (Exception e) {
            throw new TlsLoadException("装配 SSLContext 失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 读取证书文件中的全部 X.509 证书（按顺序构成证书链），无有效证书时报错
     */
    private static List<X509Certificate> loadCertificates(File file) throws TlsLoadException {
        List<X509Certificate> chain = new ArrayList<>();
        CertificateFactory factory;
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            throw new TlsLoadException("不支持 X.509 证书解析：" + e.getMessage(), e);
        }
        for (PemBlock block : readPemBlocks(file)) {
            if (!"CERTIFICATE".equals(block.label()) && !"X509 CERTIFICATE".equals(block.label())
                    && !"TRUSTED CERTIFICATE".equals(block.label())) {
                continue;
            }
            try {
                chain.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(block.der())));
            } catch (Exception e) {
                throw new TlsLoadException("证书文件解析失败（不是有效的 X.509 证书）：" + file.getAbsolutePath(), e);
            }
        }
        if (chain.isEmpty()) {
            throw new TlsLoadException("证书文件中未找到 PEM 证书块（需 -----BEGIN CERTIFICATE-----）：" + file.getAbsolutePath());
        }
        return chain;
    }
    
    /**
     * 读取私钥文件中的私钥片段：跳过证书与其它无关块，取首个可识别的私钥块
     */
    private static PrivateKey loadPrivateKey(File file) throws TlsLoadException {
        for (PemBlock block : readPemBlocks(file)) {
            switch (block.label()) {
                case "PRIVATE KEY" -> {
                    return generatePrivateKey(block.der(), file);
                }
                case "RSA PRIVATE KEY" -> {
                    return generatePrivateKey(pkcs1ToPkcs8(block.der()), file);
                }
                case "ENCRYPTED PRIVATE KEY" -> throw new TlsLoadException(
                        "私钥文件带口令保护，请提供未加密的 PEM 私钥：" + file.getAbsolutePath());
                case "EC PRIVATE KEY" -> throw new TlsLoadException(
                        "不支持 SEC1 格式的 EC 私钥，请用 openssl pkcs8 -topk8 转换为 PKCS#8：" + file.getAbsolutePath());
                default -> {
                    // 证书等无关块：继续向后查找私钥
                }
            }
        }
        throw new TlsLoadException(
                "私钥文件中未找到 PEM 私钥块（需 -----BEGIN PRIVATE KEY----- 或 -----BEGIN RSA PRIVATE KEY-----）：" + file.getAbsolutePath());
    }
    
    /**
     * 按 PKCS#8 编码生成私钥：编码本身带算法 OID，逐个算法尝试直到成功
     */
    private static PrivateKey generatePrivateKey(byte[] pkcs8, File file) throws TlsLoadException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
        for (String algorithm : KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (Exception ignored) {
                // 算法不匹配：尝试下一个候选
            }
        }
        throw new TlsLoadException("私钥解析失败（格式损坏或算法不受支持）：" + file.getAbsolutePath());
    }
    
    /**
     * 把 RSA PKCS#1 私钥包装为 PKCS#8 结构：
     * {@code SEQUENCE { version 0, AlgorithmIdentifier { rsaEncryption, NULL }, OCTET STRING { PKCS#1 } }}
     */
    private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] algorithmId = der(0x30, concat(der(0x06, encodeOid(RSA_OID)), der(0x05, new byte[0])));
        return der(0x30, concat(der(0x02, new byte[] {0}), concat(algorithmId, der(0x04, pkcs1))));
    }
    
    /**
     * DER 编码：标签 + 长度（短形式或长形式）+ 内容
     */
    private static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = content.length;
        if (length <= 0x7F) {
            out.write(length);
        } else {
            byte[] lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
            int start = 0;
            while (start < lengthBytes.length - 1 && lengthBytes[start] == 0) {
                start++;
            }
            out.write(0x80 | (lengthBytes.length - start));
            out.write(lengthBytes, start, lengthBytes.length - start);
        }
        out.write(content, 0, content.length);
        return out.toByteArray();
    }
    
    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    
    /**
     * OID 点分十进制 → DER 内容字节（前两段合并为 40*arc1+arc2，其余按 base-128 变长编码）
     */
    private static byte[] encodeOid(String dotted) {
        String[] arcs = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBase128(out, Long.parseLong(arcs[0]) * 40 + Long.parseLong(arcs[1]));
        for (int i = 2; i < arcs.length; i++) {
            writeBase128(out, Long.parseLong(arcs[i]));
        }
        return out.toByteArray();
    }
    
    private static void writeBase128(ByteArrayOutputStream out, long value) {
        byte[] buffer = new byte[10];
        int index = buffer.length - 1;
        buffer[index] = (byte) (value & 0x7F);
        while ((value >>>= 7) > 0) {
            buffer[--index] = (byte) ((value & 0x7F) | 0x80);
        }
        out.write(buffer, index, buffer.length - index);
    }
    
    /**
     * 读取文件中的全部 PEM 块：按 ISO-8859-1 读取（PEM 内容为 ASCII，避免 UTF-8 严格解码失败）
     */
    private static List<PemBlock> readPemBlocks(File file) throws TlsLoadException {
        String text;
        try {
            text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new TlsLoadException("读取 TLS 文件失败：" + file.getAbsolutePath() + " " + e.getMessage(), e);
        }
        List<PemBlock> blocks = new ArrayList<>();
        Matcher matcher = PEM_BLOCK.matcher(text);
        while (matcher.find()) {
            String body = matcher.group(2).replaceAll("\\s", "");
            try {
                blocks.add(new PemBlock(matcher.group(1).trim(), Base64.getDecoder().decode(body)));
            } catch (IllegalArgumentException e) {
                throw new TlsLoadException("PEM 块 Base64 解码失败（" + matcher.group(1).trim() + "）：" + file.getAbsolutePath(), e);
            }
        }
        return blocks;
    }
    
    private static void requireRegularFile(File file, String label) throws TlsLoadException {
        if (file == null || !file.isFile()) {
            throw new TlsLoadException(label + "文件不存在: " + (file == null ? "(空)" : file.getAbsolutePath()));
        }
    }
    
    /**
     * TLS 配置/证书加载失败：message 为可直接展示给用户的中文提示
     */
    public static final class TlsLoadException extends Exception {
        
        public TlsLoadException(String message) {
            super(message);
        }
        
        public TlsLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
