package com.omnikit.ui.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnikit.components.ButtonFactory;
import com.omnikit.components.FullscreenJDialog;
import com.omnikit.ui.UiConstants;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * JSON 内容全屏编辑弹窗 — 大面积文本域编辑推送页的 JSON 内容， 支持 F11 全屏 / Esc 还原；「确定回填」后内容由外层写回输入框
 */
public class KafkaJsonEditDialog extends FullscreenJDialog {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final JTextArea editArea;

    private final JLabel tipLabel = new JLabel(" ");

    /**
     * 是否点击了「确定回填」（取消或直接关闭时为 false）
     */
    private boolean confirmed = false;

    public KafkaJsonEditDialog(java.awt.Frame owner, String content) {
        super("KAFKA_JSON_EDIT", owner, "JSON 内容编辑", true, 860, 750);

        editArea = new JTextArea();
        editArea.setFont(UiConstants.FONT_MONO_12);
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setBackground(UiConstants.COLOR_LOG_BG);
        editArea.setForeground(UiConstants.COLOR_LOG_FG);
        editArea.setText(content);
        editArea.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(editArea);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        tipLabel.setFont(UiConstants.FONT_SANS_10);
        bottomPanel.add(tipLabel, BorderLayout.WEST);

        javax.swing.JButton formatBtn = ButtonFactory.createSecondary("格式化 JSON");
        formatBtn.addActionListener(e -> formatJson());
        javax.swing.JButton cancelBtn = ButtonFactory.createSecondary("取消");
        cancelBtn.addActionListener(e -> dispose());
        javax.swing.JButton okBtn = ButtonFactory.createPrimary("确定回填");
        okBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JPanel btnPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        btnPanel.add(formatBtn);
        btnPanel.add(cancelBtn);
        btnPanel.add(okBtn);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okBtn);
    }

    /**
     * 编辑内容格式化（pretty print）；内容不是合法 JSON 时仅提示，不修改原文
     */
    private void formatJson() {
        String text = editArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(text);
            editArea.setText(JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            editArea.setCaretPosition(0);
            tipLabel.setText("格式化完成");
        } catch (Exception ex) {
            tipLabel.setText("不是合法 JSON：" + ex.getMessage());
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getEditedText() {
        return editArea.getText();
    }
}
