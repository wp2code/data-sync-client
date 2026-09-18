package com.datasync.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * 按钮样式工厂：按语义分类统一样式，避免散落的 setFont/setBackground。
 *
 * <p>分类与典型用法：
 * <ul>
 *   <li>{@link #createPrimary(String) PRIMARY}：主要操作（保存、确认、登录&保存、批量保存、运行、开始同步、应用到目标库）</li>
 *   <li>{@link #createDestructive(String) DESTRUCTIVE}：危险操作（删除、清空、重置）</li>
 *   <li>{@link #createSecondary(String) SECONDARY}：次要操作（取消、关闭）</li>
 *   <li>{@link #createToolbar(String) TOOLBAR}：工具栏 / 导航 / 分页</li>
 *   <li>{@link #createLink(String) LINK}：内嵌链接式小按钮（列选择的全选、行内删除）</li>
 *   <li>{@link #createPill(String, Color, Color) PILL}：胶囊描边按钮（工具栏动作按钮，悬浮 / 按下填充反白）</li>
 * </ul>
 *
 * <p>实现说明：
 * <ul>
 *   <li>PRIMARY 使用 COLOR_PRIMARY 作背景 + 白字，对 FlatDarkLaf 暗色主题同样有对比度</li>
 *   <li>DESTRUCTIVE / LINK 在暗色主题使用 COLOR_DANGER_LIGHT 作前景，浅色主题使用 COLOR_DANGER</li>
 *   <li>统一关闭 focusPainted（FlatLaf 已有 focusWidth 高亮，避免重复方框）</li>
 *   <li>统一设置 HAND_CURSOR，提示可点击</li>
 *   <li>SECONDARY / TOOLBAR 仅统一字体、margin 与光标，背景沿用 FlatLaf 主题</li>
 * </ul>
 */
public final class ButtonFactory {

    private ButtonFactory() {
    }

    /** 主操作按钮：主色填充背景 + 白字 + 加粗 */
    public static JButton createPrimary(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UiConstants.FONT_SANS_12_BOLD);
        btn.setBackground(UiConstants.COLOR_PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        applyCommonStyle(btn, new Insets(4, 14, 4, 14));
        return btn;
    }
    
    /** 危险操作按钮：主色填充背景 + 白字 + 加粗
     * @param text
     * @return
     */
    public static JButton createDanger(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UiConstants.FONT_SANS_12_BOLD);
        btn.setBackground(UiConstants.COLOR_DANGER_LIGHT);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        applyCommonStyle(btn, new Insets(4, 14, 4, 14));
        return btn;
    }

    /** 危险操作按钮：透明背景 + 危险色文字（暗色主题用浅红，浅色主题用深红） */
    public static JButton createDestructive(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UiConstants.FONT_SANS_12);
        btn.setForeground(UiConstants.COLOR_DANGER_LIGHT);
        btn.setOpaque(false);
        applyCommonStyle(btn, new Insets(2, 10, 2, 10));
        return btn;
    }

    /** 次要操作按钮：默认外观 + 12pt + 标准间距 */
    public static JButton createSecondary(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UiConstants.FONT_SANS_12);
        applyCommonStyle(btn, new Insets(2, 10, 2, 10));
        return btn;
    }

    /** 工具栏按钮：默认外观 + 11pt，用于导航 / 侧边工具 / 刷新等 */
    public static JButton createToolbar(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UiConstants.FONT_SANS_11);
        applyCommonStyle(btn, new Insets(2, 8, 2, 8));
        return btn;
    }

    /** 链接式小按钮：11pt + 危险色文字 + 紧凑 margin，适合内嵌操作（行内删除） */
    public static JButton createLink(String text) {
        JButton btn = new JButton(text);
        btn.setFont(UiConstants.FONT_SANS_11);
        btn.setForeground(UiConstants.COLOR_DANGER_LIGHT);
        btn.setOpaque(false);
        applyCommonStyle(btn, new Insets(1, 6, 1, 6));
        return btn;
    }

    /**
     * 胶囊描边按钮：常态半透明填充 + 彩色描边 + 亮色文字，悬浮 / 按下时饱和色填充并反白文字
     * <p>
     * 与问题列表操作列的胶囊按钮（详情 / 编辑 / 删除）保持同一视觉语言，用于工具栏中的动作按钮。
     *
     * @param text        按钮文字
     * @param accent      饱和强调色（悬浮 / 按下填充色与描边基准色）
     * @param accentLight 常态文字色（暗色主题下用同色系亮色变体保证对比度）
     */
    public static JButton createPill(String text, Color accent, Color accentLight) {
        return new PillButton(text, accent, accentLight);
    }

    /**
     * 应用通用样式：关闭 focusPainted、统一 margin、设置手型光标。
     */
    private static void applyCommonStyle(JButton btn, Insets margin) {
        btn.setFocusPainted(false);
        btn.setMargin(margin);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * 胶囊按钮实现：自绘圆角底与描边，状态反馈由按钮模型（rollover / pressed）驱动
     */
    private static final class PillButton extends JButton {

        /**
         * 胶囊圆角半径
         */
        private static final int ARC = 14;

        /**
         * 常态填充色透明度（强调色叠加在主题背景上的轻填充）
         */
        private static final int FILL_ALPHA = 36;

        /**
         * 常态描边色透明度
         */
        private static final int BORDER_ALPHA = 120;

        private final Color accent;

        private final Color accentLight;

        private PillButton(String text, Color accent, Color accentLight) {
            super(text);
            this.accent = accent;
            this.accentLight = accentLight;
            setFont(UiConstants.FONT_SANS_12_BOLD);
            setForeground(accentLight);
            // 背景与边框由 paintComponent 自绘，避免主题默认绘制叠加
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(4, 14, 4, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean rollover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                if (pressed) {
                    g2.setColor(accent.darker());
                } else if (rollover) {
                    g2.setColor(accent);
                } else {
                    g2.setColor(withAlpha(accent, FILL_ALPHA));
                }
                g2.fillRoundRect(0, 0, width, height, ARC, ARC);
                g2.setColor(rollover || pressed ? accent : withAlpha(accentLight, BORDER_ALPHA));
                g2.drawRoundRect(0, 0, width, height, ARC, ARC);
            } finally {
                g2.dispose();
            }
            setForeground(rollover || pressed ? Color.WHITE : accentLight);
            // 文字单独开启抗锯齿绘制，保证小字号清晰
            Graphics2D textGraphics = (Graphics2D) g.create();
            try {
                textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paintComponent(textGraphics);
            } finally {
                textGraphics.dispose();
            }
        }

        private static Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
    }
}