package com.datasync.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/**
 * 日志格式化与输出工具类。 负责生成带时间戳的日志行、追加到 JEditorPane、清空日志区域。
 */
public final class LogUtil {
    
    private LogUtil() {
    }
    
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 生成带时间戳的日志行
     */
    public static String logTimestamp(String prefix, String message) {
        String timestamp = LOG_DATE_FORMAT.format(new Date());
        return String.format("[%s] %s%s", timestamp, prefix, message);
    }
    
    /**
     * 生成纯日志行（无时间戳）
     */
    public static String logLine(String message) {
        return message;
    }
    
    /**
     * 追加日志到 JEditorPane（HTML 格式）
     */
    public static void appendLog(String message, JEditorPane logArea) {
        if (logArea == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            HTMLDocument doc = (HTMLDocument) logArea.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) logArea.getEditorKit();
            try {
                kit.insertHTML(doc, doc.getLength(), message + "<br>", 0, 0, null);
                // 自动滚动到底部
                logArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException | IOException e) {
                System.err.println("日志追加失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 清空日志区域
     */
    public static void clearLog(JEditorPane logArea) {
        if (logArea == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            logArea.setText("<html><body></body></html>");
        });
    }
    
    /**
     * 去除 HTML 标签，返回纯文本
     */
    public static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
    }
}
