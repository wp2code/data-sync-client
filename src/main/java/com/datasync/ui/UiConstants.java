package com.datasync.ui;

import java.awt.*;

/**
 * UI 常量集中管理，消除散落的魔法字符串和硬编码颜色/字体。
 */
public final class UiConstants {
    
    private UiConstants() {
    }
    
    public static final String VERSION = "v1.0.0";
    
    public static final String GITHUB_ADDR = "https://github.com/wp2code/data-sync-client";
    
    // ─── 占位文本 ───
    public static final String PLACEHOLDER_SELECT_SOURCE = "请选择数据源";
    
    public static final String PLACEHOLDER_SELECT_DATABASE = "（请选择数据库）";
    
    public static final String PLACEHOLDER_NONE = "（无）";
    
    public static final String PLACEHOLDER_NO_MATCHING = "（无同类型配置）";
    
    public static final String PLACEHOLDER_CONNECT_FIRST = "（请先连接）";
    
    public static final String PLACEHOLDER_SELECT_SCHEMA = "（请先选择 Schema）";
    
    public static final String PLACEHOLDER_QUERYING = "（查询中…）";
    
    public static final String PLACEHOLDER_NO_TABLES = "（无表）";
    
    // ─── 日志前缀 ───
    public static final String LOG_CONNECT = "[CONNECT] ";
    
    public static final String LOG_DISCONNECT = "[DISCONNECT] ";
    
    public static final String LOG_REFRESH = "[REFRESH] ";
    
    public static final String LOG_SYNC = "[SYNC] ";
    
    public static final String LOG_EXPORT = "[EXPORT] ";
    
    public static final String LOG_DDL = "[DDL] ";
    
    public static final String LOG_ERROR = "[ERROR] ";
    
    public static final String LOG_WAIT = "[WAIT] ";
    
    public static final String LOG_SUCCESS = "[SUCCESS] ";
    
    public static final String LOG_FAILED = "[FAILED] ";
    
    // ─── 颜色 ───
    public static final Color COLOR_PRIMARY = new Color(0x4F46E5);
    
    public static final Color COLOR_SUCCESS = new Color(0x059669);
    
    public static final Color COLOR_LOG_BG = new Color(30, 30, 30);
    
    public static final Color COLOR_LOG_FG = new Color(200, 200, 200);
    
    public static final Color COLOR_CONNECTING = Color.ORANGE;
    
    public static final Color COLOR_CONNECTED = Color.GREEN.darker();
    
    public static final Color COLOR_ERROR = Color.RED;
    
    /**
     * 危险操作色（柔和红，用于删除类按钮的文字与描边）
     */
    public static final Color COLOR_DANGER = new Color(0xDC2626);
    
    /**
     * 主色亮色变体（暗色主题下用作按钮文字 / 描边，保证清晰对比度）
     */
    public static final Color COLOR_PRIMARY_LIGHT = new Color(0xA5B4FC);
    
    /**
     * 危险色亮色变体（暗色主题下用作删除类按钮文字 / 描边）
     */
    public static final Color COLOR_DANGER_LIGHT = new Color(0xF87171);
    
    // ─── 字体 ───
    public static final Font FONT_MONO_11 = new Font("Monospaced", Font.PLAIN, 11);
    
    public static final Font FONT_MONO_12 = new Font("Monospaced", Font.PLAIN, 12);
    
    public static final Font FONT_SANS_10 = new Font("SansSerif", Font.PLAIN, 10);
    
    public static final Font FONT_SANS_11 = new Font("SansSerif", Font.PLAIN, 11);
    
    public static final Font FONT_SANS_12 = new Font("SansSerif", Font.PLAIN, 12);
    
    public static final Font FONT_SANS_12_BOLD = new Font("SansSerif", Font.BOLD, 12);
    
    public static final Font FONT_SANS_BOLD_14 = new Font("SansSerif", Font.BOLD, 14);
    
    public static final Font FONT_SANS_BOLD_16 = new Font("SansSerif", Font.BOLD, 16);
    
    public static final Font FONT_SANS_BOLD_22 = new Font("SansSerif", Font.BOLD, 22);
    
    public static final Font FONT_SANS_BOLD_100 = new Font("SansSerif", Font.BOLD, 100);
    
    // ─── 尺寸/超时 ───
    public static final int CONNECT_TIMEOUT_MS = 30_000;
}
