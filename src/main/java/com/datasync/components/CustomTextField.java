package com.datasync.components;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * @author liuweiping
 * @date 2026-06-30
 **/
public class CustomTextField extends JTextField {

    private final String placeholder;

    private boolean showPlaceholder = true;

    private final Color placeholderColor = Color.GRAY;

    private final Font placeholderFont;

    public CustomTextField(String placeholder) {
        setFont(new Font("SansSerif", Font.PLAIN, 11));
        this.placeholder = placeholder != null ? placeholder : "请输入内容";
        placeholderFont = new Font("SansSerif", Font.PLAIN, 11);

        // 焦点变化时更新placeholder显示状态
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                showPlaceholder = false;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                showPlaceholder = getText().isEmpty();
                repaint();
            }
        });

        // 文本变化时更新placeholder显示状态
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                showPlaceholder = getText().isEmpty() && !hasFocus();
                repaint();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                showPlaceholder = getText().isEmpty() && !hasFocus();
                repaint();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (showPlaceholder && getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setFont(placeholderFont);
            g2.setColor(placeholderColor);
            FontMetrics fm = g2.getFontMetrics();
            int x = getInsets().left;
            int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(placeholder, x, y);
        }
    }

}
