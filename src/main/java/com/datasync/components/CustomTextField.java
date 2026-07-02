package com.datasync.components;

import com.datasync.ui.UiConstants;
import java.awt.*;
import java.awt.event.*;
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
    
    private final Font placeholderFont = UiConstants.FONT_SANS_11;
    
    /**
     * 基于 placeholder 文本宽度和字体计算的首选尺寸
     */
    private final Dimension preferredSize;
    
    public CustomTextField(String placeholder) {
        setFont(UiConstants.FONT_SANS_11);
        this.placeholder = placeholder != null ? placeholder : "请输入内容";
        // 根据 placeholder 文本宽度 + 内边距计算首选尺寸，避免父容器变化后尺寸失效
        FontMetrics fm = getFontMetrics(getFont());
        int textWidth = fm.stringWidth(this.placeholder);
        int textHeight = fm.getHeight();
        this.preferredSize = new Dimension(textWidth + 16, textHeight + 8);
        // 焦点变化时更新placeholder显示状态
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                showPlaceholder = false;
                repaint(0, 0, getWidth(), getHeight());
            }
            
            @Override
            public void focusLost(FocusEvent e) {
                showPlaceholder = getText().isEmpty();
                repaint(0, 0, getWidth(), getHeight());
                
            }
        });
        
        // 文本变化时更新placeholder显示状态
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                showPlaceholder = getText().isEmpty() && !hasFocus();
                repaint(0, 0, getWidth(), getHeight());
                
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                showPlaceholder = getText().isEmpty() && !hasFocus();
                repaint(0, 0, getWidth(), getHeight());
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
    
    @Override
    public Dimension getPreferredSize() {
        return preferredSize != null ? preferredSize : super.getPreferredSize();
    }
    
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }
    
}
