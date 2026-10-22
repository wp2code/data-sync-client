package com.datasync.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * 子进程输出编码探测 Reader：ASCII 字节在 UTF-8/GBK 等常见编码下无差异，先行直通显示；
 * 首次遇到非 ASCII 字节时以该行为界做 UTF-8 严格校验判定——通过则按 UTF-8 解码，
 * 否则回退系统编码（{@code native.encoding}，中文 Windows 通常为 GBK）。
 * <p>
 * 解决程序开头输出大量 ASCII（如启动 banner、日志框架头）时按固定窗口提前判定为系统编码、
 * 后续 UTF-8 输出中文乱码的问题；显式指定输出编码的场景不走本类，直接用 InputStreamReader。
 */
final class EncodingDetectReader extends Reader {

    /**
     * 单批读取与解码的字节缓冲大小
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 判定缓冲上限（字节）：首个非 ASCII 批次追加到行尾时超过即强制判定，防止无换行的超长输出无限缓冲
     */
    private static final int PROBE_LIMIT = 64 * 1024;

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final InputStream in;

    /**
     * 自动探测的回退编码（native.encoding，中文 Windows 通常为 GBK）
     */
    private final Charset fallback;

    /**
     * 判定后的解码器；null 表示尚未遇到非 ASCII 字节，处于 ASCII 直通阶段
     */
    private CharsetDecoder decoder;

    /**
     * 待解码字节：判定批次暂存 / 解码残余（多字节字符被批边界切断时留待与后续字节拼接）
     */
    private byte[] pending = EMPTY_BYTES;

    EncodingDetectReader(InputStream in, Charset fallback) {
        this.in = in;
        this.fallback = fallback;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (decoder == null) {
            // 直通路径产出的字符数等于字节数，缓冲不得超过调用方容量，否则写入 cbuf 越界
            byte[] tmp = new byte[Math.min(len, BUFFER_SIZE)];
            int n = in.read(tmp);
            if (n == -1) {
                return -1;
            }
            if (indexOfNonAscii(tmp, n) < 0) {
                // 纯 ASCII：各编码下无差异，直接转换返回
                for (int i = 0; i < n; i++) {
                    cbuf[off + i] = (char) (tmp[i] & 0xFF);
                }
                return n;
            }
            // 首个非 ASCII 字节：追加读取到行尾获得完整多字节序列后判定编码并锁定解码器
            byte[] probe = appendToLineEnd(tmp, n);
            decoder = ProcessContainerService.detectCharset(probe, fallback).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            pending = probe;
        }
        return decode(cbuf, off, len);
    }

    /**
     * 用已锁定的解码器解码：消化判定批次暂存字节与后续新字节（残余多字节自动拼接），
     * 至少产出一个字符或到达流末尾才返回
     */
    private int decode(char[] cbuf, int off, int len) throws IOException {
        CharBuffer out = CharBuffer.wrap(cbuf, off, len);
        while (out.position() == 0) {
            byte[] chunk = readChunk();
            if (chunk == null) {
                decoder.decode(ByteBuffer.allocate(0), out, true);
                return out.position() == 0 ? -1 : out.position();
            }
            ByteBuffer bb = ByteBuffer.wrap(chunk);
            decoder.decode(bb, out, false);
            if (bb.hasRemaining()) {
                // 多字节字符被批边界切断的残余，留待与后续字节拼接续解
                pending = new byte[bb.remaining()];
                bb.get(pending);
            } else {
                pending = EMPTY_BYTES;
            }
        }
        return out.position();
    }

    /**
     * 取下一批待解码字节：阻塞读取新字节，与残余多字节（含判定批次未消化部分）拼接后返回；
     * 流末尾且无残余返回 null。每轮循环必然消费新字节或产出字符，保证 decode 无自旋死循环
     */
    private byte[] readChunk() throws IOException {
        byte[] tmp = new byte[BUFFER_SIZE];
        int n = in.read(tmp);
        if (n == -1) {
            if (pending.length > 0) {
                // 流末尾：先消化残余（含判定批次），下一轮再返回 null 走 flush
                byte[] rest = pending;
                pending = EMPTY_BYTES;
                return rest;
            }
            return null;
        }
        if (pending.length == 0) {
            byte[] chunk = new byte[n];
            System.arraycopy(tmp, 0, chunk, 0, n);
            return chunk;
        }
        byte[] chunk = new byte[pending.length + n];
        System.arraycopy(pending, 0, chunk, 0, pending.length);
        System.arraycopy(tmp, 0, chunk, pending.length, n);
        pending = EMPTY_BYTES;
        return chunk;
    }

    /**
     * 以触发批次为基础追加读取，直到最后一个非 ASCII 字节之后出现 ASCII 字节（如行尾换行）
     * 或达到判定上限/流末尾，保证截断判定时多字节序列完整
     */
    private byte[] appendToLineEnd(byte[] first, int n) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(first, 0, n);
        while (buf.size() < PROBE_LIMIT && !hasCompleteTail(buf)) {
            byte[] tmp = new byte[BUFFER_SIZE];
            int r = in.read(tmp);
            if (r == -1) {
                break;
            }
            buf.write(tmp, 0, r);
        }
        return buf.toByteArray();
    }

    /**
     * 最后一个非 ASCII 字节之后是否已出现 ASCII 字节；无任何非 ASCII 视为完整。
     * 若非 ASCII 后紧跟批尾（多字节序列可能被切断），需继续读取追加，否则按截断判定会误判
     */
    private static boolean hasCompleteTail(ByteArrayOutputStream buf) {
        byte[] bytes = buf.toByteArray();
        int lastNonAscii = -1;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0) {
                lastNonAscii = i;
            }
        }
        if (lastNonAscii < 0) {
            return true;
        }
        for (int i = lastNonAscii + 1; i < bytes.length; i++) {
            if (bytes[i] >= 0) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfNonAscii(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            if (bytes[i] < 0) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /**
     * 已从底层读出但仍在内部缓冲等待与后续字节拼接的残余字节数
     * （多字节字符被流尾切断时 > 0）。调用方据此回退字节偏移，
     * 避免跨轮续读时从多字节字符中间开始导致乱码
     */
    int pendingByteCount() {
        return decoder == null ? 0 : pending.length;
    }
}
