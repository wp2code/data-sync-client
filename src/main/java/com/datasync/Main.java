package com.datasync;

import com.datasync.ui.DataSyncUI;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

/**
 * DataSync Client 程序启动入口
 * 配置 FlatLaf 暗色主题，通过 Swing 事件线程启动主界面
 */
public class Main {

    public static void main(String[] args) {
        // 设置 FlatLaf 暗色主题（现代化界面风格）
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            // 微调部分 UI 默认值
            UIManager.put("Component.focusWidth", 1.5f);
            UIManager.put("Button.arc", 6);
            UIManager.put("Component.arc", 6);
            UIManager.put("TextComponent.arc", 6);
        } catch (Exception e) {
            System.err.println("FlatLaf 主题加载失败，回退到系统默认样式: " + e.getMessage());
        }

        // 通过 Swing 事件分发线程启动 GUI，保障线程安全
        SwingUtilities.invokeLater(() -> {
            DataSyncUI ui = new DataSyncUI();
            ui.setVisible(true);
        });
    }
}
