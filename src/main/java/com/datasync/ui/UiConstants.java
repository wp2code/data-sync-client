package com.datasync.ui;

import java.awt.*;
import java.util.Locale;

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
    
    /**
     * 成功色亮色变体（暗色主题下用作查看类按钮文字 / 描边）
     */
    public static final Color COLOR_SUCCESS_LIGHT = new Color(0x6EE7B7);
    
    /**
     * 中性动作色（工具栏中性按钮的悬浮填充色与描边基准色）
     */
    public static final Color COLOR_NEUTRAL = new Color(0x6B7280);
    
    /**
     * 中性动作亮色变体（暗色主题下用作中性按钮的文字与常态描边）
     */
    public static final Color COLOR_NEUTRAL_LIGHT = new Color(0x9CA3AF);
    
    /**
     * 待处理状态色（待训练等中间态状态文字，暗色主题下用亮琥珀色保证对比度）
     */
    public static final Color COLOR_PENDING = new Color(0xFBBF24);
    
    /**
     * 训练动作色（问题列表操作列训练按钮的悬浮填充色，饱和琥珀）
     */
    public static final Color COLOR_TRAINING = new Color(0xD97706);
    
    /**
     * 链接色（回复ID超链接文字，青绿色调，弱化蓝色饱和感）
     */
    public static final Color COLOR_LINK = new Color(0x0D9488);
    
    // ─── 字体 ───
    /**
     * 界面无衬线字体族：Windows 上优先「微软雅黑 UI」，中文小字号为轮廓渲染清晰；
     * 逻辑字体 SansSerif 在 Windows 会回退到点阵宋体，小字号发虚有锯齿。
     * 非Windows平台或字体缺失时回退 SansSerif（macOS / Linux 的逻辑字体映射本身清晰）
     */
    private static final String SANS_FONT_FAMILY = resolveSansFontFamily();
    
    public static final Font FONT_MONO_11 = new Font("Monospaced", Font.PLAIN, 11);
    
    public static final Font FONT_MONO_12 = new Font("Monospaced", Font.PLAIN, 12);
    
    public static final Font FONT_SANS_10 = new Font(SANS_FONT_FAMILY, Font.PLAIN, 10);
    
    public static final Font FONT_SANS_11 = new Font(SANS_FONT_FAMILY, Font.PLAIN, 11);
    
    public static final Font FONT_SANS_12 = new Font(SANS_FONT_FAMILY, Font.PLAIN, 12);
    
    public static final Font FONT_SANS_12_BOLD = new Font(SANS_FONT_FAMILY, Font.BOLD, 12);
    
    public static final Font FONT_SANS_BOLD_14 = new Font(SANS_FONT_FAMILY, Font.BOLD, 14);
    
    public static final Font FONT_SANS_BOLD_16 = new Font(SANS_FONT_FAMILY, Font.BOLD, 16);
    
    public static final Font FONT_SANS_BOLD_22 = new Font(SANS_FONT_FAMILY, Font.BOLD, 22);
    
    public static final Font FONT_SANS_BOLD_100 = new Font(SANS_FONT_FAMILY, Font.BOLD, 100);
    
    /**
     * 解析界面无衬线字体族：Windows 探测微软雅黑 UI 是否可用，可用则采用，否则回退逻辑字体 SansSerif
     */
    private static String resolveSansFontFamily() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if ("Microsoft YaHei UI".equals(family)) {
                    return family;
                }
            }
        }
        return "SansSerif";
    }
    
    // ─── 尺寸/超时 ───
    public static final int CONNECT_TIMEOUT_MS = 30_000;
}
