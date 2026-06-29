package com.datasync.util;

import com.datasync.core.DataSource;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

/**
 * 配置持久化与通用工具类 提供数据源配置的增删改查快捷方法，桥接 SQLiteConfigUtil
 */
public class ConfigUtil {
    
    private static final SQLiteConfigUtil sqlite = SQLiteConfigUtil.getInstance();
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private ConfigUtil() {
        // 工具类禁止实例化
    }
    
    /**
     * 工具启动时初始化 SQLite 数据库
     */
    public static void initialize() {
        sqlite.initialize();
    }
    
    /**
     * 保存数据源配置到本地 SQLite
     */
    public static boolean saveDataSource(DataSource ds) {
        return sqlite.saveDataSource(ds);
    }
    
    /**
     * 加载所有已保存的数据源名称列表
     */
    public static java.util.List<String> loadAllSourceNames() {
        return sqlite.loadAllSourceNames();
    }
    
    /**
     * 根据名称加载数据源完整配置
     */
    public static DataSource loadDataSourceByName(String sourceName) {
        return sqlite.loadDataSourceByName(sourceName);
    }
    
    /**
     * 更新已保存的数据源配置
     */
    public static boolean updateDataSource(DataSource ds) {
        return sqlite.updateDataSource(ds);
    }
    
    /**
     * 删除指定名称的数据源配置
     */
    public static boolean deleteDataSource(String sourceName) {
        return sqlite.deleteDataSource(sourceName);
    }
    
    /**
     * 格式化当前时间为日志时间戳 [HH:mm:ss]
     */
    public static String logTimestamp() {
        return "[" + LocalDateTime.now().format(TIME_FORMAT) + "] ";
    }
    
    /**
     * 生成带时间戳的日志行
     */
    public static String logLine(String message) {
        return message;
    }
    
    // ────────── 日志辅助 ──────────
    
    private static final StringBuilder logBuffer = new StringBuilder();
    
    public static void clearLog(JEditorPane logArea) {
        logBuffer.setLength(0);
        logArea.setText("");
    }
    
    public static void appendLog(String msg, JEditorPane logArea) {
        try {
            // 去除 HTML 标签，提取纯文本
            String plainText = stripHtml(msg);
            logBuffer.append(logTimestamp()).append(plainText).append("\n");
            logArea.setText(logBuffer.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } catch (Exception e) {
            logArea.setText(logArea.getText() + "\n" + msg);
        }
    }
    
    /**
     * 去除 HTML 标签，提取纯文本内容
     */
    private static String stripHtml(String html) {
        if (html == null) return "";
        // 移除所有 HTML 标签
        return html.replaceAll("<[^>]+>", "").trim();
    }
    
    /**
     * 用代码绘制应用图标（64x64），避免依赖 SVG 解析。 蓝紫渐变圆角矩形 + 双向同步箭头。
     */
    public static Image createAppIcon() {
        int size = 64;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 圆角矩形背景
        g2.setColor(new Color(0x4F46E5));
        GradientPaint gp = new GradientPaint(0, 0, new Color(0x4F46E5), size, size, new Color(0x7C3AED));
        g2.setPaint(gp);
        g2.fillRoundRect(4, 4, 56, 56, 14, 14);
        
        // 双向同步箭头（白色）
        g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Color.WHITE);
        
        // 左上箭头
        g2.drawLine(22, 36, 14, 28);
        g2.drawLine(14, 28, 22, 20);
        g2.drawLine(14, 28, 32, 28);
        
        // 右下箭头
        g2.drawLine(42, 28, 50, 36);
        g2.drawLine(50, 36, 42, 44);
        g2.drawLine(50, 36, 32, 36);
        
        g2.dispose();
        return img;
    }
}
