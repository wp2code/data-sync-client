/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.components;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import lombok.Getter;
import lombok.Setter;

/**
 * @author liuweiping
 * @date 2026-07-03
 **/
public class OptionJPanel extends JPanel {
    
    private Color borderColor = null;   // null 表示无边框
    
    private final Color hoverColor = new Color(62, 78, 220);
    
    private final Color selectedColor = new Color(62, 78, 220);
    
    private boolean selected = false;
    @Getter
    @Setter
    private Object data;
    
    @Getter
    @Setter
    private Runnable onClick;
    
    private final JLabel label;
    
    
    public OptionJPanel(String text, String remark, Icon icon) {
        super(new BorderLayout(5, 0));
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        setOpaque(false);
        
        // 图标始终在最左侧第一列
        JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER);
        iconLabel.setOpaque(false);
        add(iconLabel, BorderLayout.WEST);
        
        // 文本区域：主文本在上，备注在下；无备注时主文本垂直居中，与图标居中对齐
        JPanel textPanel = new JPanel(new GridBagLayout());
        textPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        
        label = new JLabel(text);
        gbc.gridy = 0;
        gbc.weighty = 1;
        textPanel.add(label, gbc);
        
        JLabel remarkLabel;
        if (remark != null && !remark.isEmpty()) {
            remarkLabel = new JLabel(remark);
            remarkLabel.setForeground(new Color(160, 160, 160));
            remarkLabel.setFont(remarkLabel.getFont().deriveFont(Font.PLAIN, remarkLabel.getFont().getSize() - 1f));
            remarkLabel.setOpaque(false);
            gbc.gridy = 1;
            gbc.gridx = 0;
            gbc.weighty = 0;
            gbc.insets = new Insets(0, 0, 0, 0);
            textPanel.add(remarkLabel, gbc);
        } else {
            remarkLabel = null;
        }
        
        add(textPanel, BorderLayout.CENTER);
        
        final Color background = getBackground();
        final Cursor cursor = getCursor();
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) {
                    setBackground(hoverColor);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) {
                    setBackground(background);
                    setCursor(cursor);
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
        iconLabel.addMouseListener(adapter);
        if (remarkLabel != null) {
            remarkLabel.addMouseListener(adapter);
        }
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
        this.borderColor = selected ? selectedColor : null;
        setBackground(selected ? selectedColor : UIManager.getColor("Panel.background"));
        repaint();
    }
    
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        // 1. 圆角背景
        g2.setColor(getBackground());
        final int cornerRadius = 15;
        g2.fillRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        
        // 2. 圆角边框（可选）
        if (borderColor != null) {
            g2.setColor(borderColor);
            final int borderThickness = 1;
            g2.setStroke(new BasicStroke(borderThickness));
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        }
        
        g2.dispose();
        super.paintComponent(g);
    }
}
