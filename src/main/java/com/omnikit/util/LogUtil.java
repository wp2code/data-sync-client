package com.omnikit.util;

import com.omnikit.ui.UiConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志格式化与输出工具类。 负责生成带时间戳的日志行、追加到 JEditorPane、清空日志区域。
 */
public final class LogUtil {
    
    private LogUtil() {
    }
    
    public static JEditorPane DATA_SYNC_UI_LOG_AREA = createLogArea();
    
    public static JEditorPane SCRIPT_LOG_AREA = createLogArea();
    
    public static JTextArea DATA_SOURCE_LOG_AREA = createLogArea2();
    
    /**
     *
     */
    public static JEditorPane DIFF_SYNC_LOG_AREA = createLogArea();
    
    private static final Logger logger = LoggerFactory.getLogger(LogUtil.class);
    
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    
    
    public static JEditorPane createLogArea() {
        JEditorPane logArea = new JEditorPane();
        logArea.setEditable(false);
        logArea.setContentType("text/html");
        logArea.setFont(UiConstants.FONT_MONO_12);
        logArea.setBackground(UiConstants.COLOR_LOG_BG);
        logArea.setForeground(UiConstants.COLOR_LOG_FG);
        return logArea;
    }
    
    public static JTextArea createLogArea2() {
        JTextArea statusArea = new JTextArea();
        statusArea.setRows(3);
        statusArea.setEditable(false);
        statusArea.setFont(UiConstants.FONT_MONO_11);
        statusArea.setBackground(UiConstants.COLOR_LOG_BG);
        statusArea.setForeground(UiConstants.COLOR_LOG_FG);
        return statusArea;
    }
    
    /**
     * 追加日志到 JEditorPane（HTML 格式）
     */
    public static void appendLog(String message, Object logArea) {
        boolean isNeedAppTime = !message.contains("<html>") && !hasTimestamp(message);
        if (isNeedAppTime) {
            message = logTime() + message;
        }
        final String finalMessage = message;
        if (logArea instanceof JEditorPane jEditorPane) {
            SwingUtilities.invokeLater(() -> {
                HTMLDocument doc = (HTMLDocument) jEditorPane.getDocument();
                HTMLEditorKit kit = (HTMLEditorKit) jEditorPane.getEditorKit();
                try {
                    kit.insertHTML(doc, doc.getLength(), finalMessage + "<br>", 0, 0, null);
                    // 自动滚动到底部
                    jEditorPane.setCaretPosition(doc.getLength());
                } catch (BadLocationException | IOException e) {
                    logger.error("日志追加失败", e);
                }
            });
        }
        if (logArea instanceof JTextArea jTextArea) {
            SwingUtilities.invokeLater(() -> jTextArea.append(finalMessage + "\n"));
        }
    }
    
    public static String logTime() {
        return "[" + LOG_DATE_FORMAT.format(new Date()) + "]";
    }
    
    /**
     * 为日志区域附加右键弹出菜单（清空日志）
     */
    public static void attachClearPopupMenu(JComponent logArea) {
        logArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
            
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem clearItem = new JMenuItem("清空日志");
                    clearItem.addActionListener(ev -> clearLog(logArea));
                    popup.add(clearItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
    
    /**
     * 清空日志区域
     */
    public static void clearLog(Object logArea) {
        if (logArea instanceof JEditorPane jEditorPane) {
            SwingUtilities.invokeLater(() -> {
                jEditorPane.setText("<html><body></body></html>");
            });
            return;
        }
        if (logArea instanceof JTextArea jTextArea) {
            SwingUtilities.invokeLater(() -> {
                jTextArea.setText("");
            });
        }
    }
    
    /**
     * 判断消息是否已带时间戳前缀（如 [12:30:01.123]）
     */
    private static boolean hasTimestamp(String message) {
        return message.startsWith("[") && message.length() > 1 && !isNotNumber(message.charAt(1));
    }
    
    /**
     * 为消息补充时间戳前缀（已有时不重复添加）
     */
    private static String withTime(String message) {
        return hasTimestamp(message) ? message : logTime() + message;
    }
    
    public static String success(String message) {
        logger.info(message);
        return """
                <html><body><span style="color: green;font-weight: bold;">%s</span></body></html>")
                """.formatted(withTime(message));
    }
    
    public static String failed(String message) {
        logger.error(message);
        return """
                <html><body><span style="color: red;">%s</span></body></html>")
                """.formatted(withTime(message));
    }
    
    public static String warn(String message) {
        logger.warn(message);
        return """
                <html><body><span style="color: orange;">%s</span></body></html>")
                """.formatted(withTime(message));
    }
    
    public static boolean isNotNumber(Character ch) {
        return !Character.isDigit(ch);
    }
}
