package com.datasync.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Insets;

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
     * 应用通用样式：关闭 focusPainted、统一 margin、设置手型光标。
     */
    private static void applyCommonStyle(JButton btn, Insets margin) {
        btn.setFocusPainted(false);
        btn.setMargin(margin);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}