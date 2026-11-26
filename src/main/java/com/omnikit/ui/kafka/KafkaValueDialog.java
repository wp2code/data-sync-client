package com.omnikit.ui.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnikit.components.ButtonFactory;
import com.omnikit.components.FullscreenJDialog;
import com.omnikit.util.LogUtil;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * 消息 Value 详情弹窗 — 以「JSON 格式化」/「文本」两种形式展示消息内容， 支持复制，F11 全屏 / Esc 还原
 */
public class KafkaValueDialog extends FullscreenJDialog {
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    public KafkaValueDialog(java.awt.Frame owner, String title, String value) {
        super("KAFKA_VALUE", owner, title, true, 760, 520);
        
        JTabbedPane tabs = new JTabbedPane();
        
        // ── JSON 格式化视图：合法 JSON 时 pretty-print，否则提示原因 ──
        JTextArea jsonArea = LogUtil.createLogArea2();
        try {
            JsonNode root = JSON_MAPPER.readTree(value);
            String formatted = JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            jsonArea.setText(formatted);
        } catch (Exception ex) {
            jsonArea.setText("内容不是合法 JSON，无法格式化。\n原因：" + ex.getMessage());
        }
        jsonArea.setCaretPosition(0);
        JScrollPane jsonScroll = new JScrollPane(jsonArea);
        jsonScroll.setBorder(null);
        tabs.addTab("JSON 格式化", jsonScroll);
        
        // ── 文本视图：原始内容 ──
        JTextArea textArea = LogUtil.createLogArea2();
        textArea.setText(value == null ? "(null)" : value);
        textArea.setCaretPosition(0);
        JScrollPane textScroll = new JScrollPane(textArea);
        textScroll.setBorder(null);
        tabs.addTab("文本", textScroll);
        
        add(tabs, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        JLabel hintLabel = new JLabel("可选中复制；F11 全屏 / Esc 还原");
        hintLabel.setFont(com.omnikit.ui.UiConstants.FONT_SANS_10);
        bottomPanel.add(hintLabel, BorderLayout.WEST);
        
        // ── 操作按钮：复制当前视图内容 + 复制结果提示（紧邻按钮） + 关闭 ──
        JLabel copyTipLabel = new JLabel(" ");
        copyTipLabel.setFont(com.omnikit.ui.UiConstants.FONT_SANS_10);
        javax.swing.JButton copyBtn = ButtonFactory.createSuccess("复制内容");
        copyBtn.addActionListener(e -> {
            String content = tabs.getSelectedIndex() == 0 ? jsonArea.getText() : textArea.getText();
            if (content == null || content.isEmpty()) {
                return;
            }
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(content);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            copyTipLabel.setText("已复制");
            // 2 秒后自动清除提示
            javax.swing.Timer tipTimer = new javax.swing.Timer(2000, ev -> copyTipLabel.setText(" "));
            tipTimer.setRepeats(false);
            tipTimer.start();
        });
        javax.swing.JButton closeBtn = ButtonFactory.createSecondary("关闭");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        btnPanel.add(copyTipLabel);
        btnPanel.add(copyBtn);
        btnPanel.add(closeBtn);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }
}
