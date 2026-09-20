package com.datasync;

import com.datasync.ui.DataSyncUI;
import com.datasync.ui.UiConstants;
import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * DataSync Client 程序启动入口
 * 配置 FlatLaf 暗色主题，通过 Swing 事件线程启动主界面
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("DataSync Client 启动");
        // 开启全局文本灰度抗锯齿，保证中文小字号边缘平滑（暗色主题下无亚像素彩边）
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        // 设置 FlatLaf 暗色主题（现代化界面风格）
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            // 微调部分 UI 默认值
            UIManager.put("Component.focusWidth", 1.5f);
            UIManager.put("Button.arc", 6);
            UIManager.put("Component.arc", 6);
            UIManager.put("TextComponent.arc", 6);
            // 弹窗与未走 ButtonFactory 的按钮也使用统一界面字体，避免中文回退点阵字体
            UIManager.put("Button.font", UiConstants.FONT_SANS_12);
            UIManager.put("OptionPane.messageFont", UiConstants.FONT_SANS_12);
            UIManager.put("OptionPane.buttonFont", UiConstants.FONT_SANS_12);
            logger.debug("FlatLaf 暗色主题加载成功");
        } catch (Exception e) {
            logger.error("FlatLaf 主题加载失败，回退到系统默认样式", e);
        }

        // 通过 Swing 事件分发线程启动 GUI，保障线程安全
        SwingUtilities.invokeLater(() -> {
            DataSyncUI ui = new DataSyncUI();
            ui.setVisible(true);
        });
    }
}
