/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.components;

import com.datasync.util.IconUtil;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-07-03
 **/
public class OptionJPanel extends JPanel {
    
    private final int cornerRadius = 15;
    
    private Color borderColor = null;   // null 表示无边框
    
    private final int borderThickness = 1;
    
    private final Color hoverColor = new Color(62, 78, 220);
    
    private final Color selectedColor = new Color(62, 78, 220);
    
    private boolean selected = false;
    
    private Object data;
    
    private final JLabel label;
    
    private final String fullText;
    
    private Runnable onClick;
    
    public OptionJPanel(String text) {
        this(text, IconUtil.createAppIcon());
    }
    
    public OptionJPanel(String text, Icon icon) {
        super(new BorderLayout(0, 0));
        this.fullText = text;
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        setOpaque(false);
        label = new JLabel(text, icon, SwingConstants.LEFT);
        add(label, BorderLayout.WEST);
        final Color background = getBackground();
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) {
                    setBackground(hoverColor);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) {
                    setBackground(background);
                }
                label.setToolTipText(null);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        };
        addMouseListener(adapter);
        label.addMouseListener(adapter);
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
        this.borderColor = selected ? selectedColor : null;
        setBackground(selected ? selectedColor : UIManager.getColor("Panel.background"));
        repaint();
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }
    
    public String getFullText() {
        return fullText;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        // 1. 圆角背景
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        
        // 2. 圆角边框（可选）
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderThickness));
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        }
        
        g2.dispose();
        super.paintComponent(g);
    }
}
