package com.datasync.components;

import java.awt.*;
import javax.swing.*;

/**
 * 子组件布局面板 支持左对齐、居中、右对齐布局
 *
 * @author liuweiping
 * @date 2026-07-02
 **/
public class ChildLayoutPanel extends JPanel {
    
    private final GridBagConstraints gbc;
    
    private int gridx;
    
    private final LayoutType layoutType;
    
    public ChildLayoutPanel() {
        this(null, ChildLayoutPanel.LayoutType.RIGHT);
    }
    
    public ChildLayoutPanel(LayoutType layoutType) {
        this(null, layoutType);
    }
    
    public ChildLayoutPanel(Insets insets, LayoutType layoutType) {
        super(new GridBagLayout());
        this.layoutType = layoutType;
        gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        if (layoutType == LayoutType.RIGHT) {
            gbc.anchor = GridBagConstraints.EAST;
            gbc.insets = insets != null ? insets : new Insets(0, 4, 0, 0);
            gbc.weightx = 1.0;
            add(new JLabel(), gbc);
            gbc.weightx = 0;
        } else if (layoutType == LayoutType.LEFT) {
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = insets != null ? insets : new Insets(0, 0, 0, 4);
            gbc.weightx = 0;
            add(new JLabel(), gbc);
        } else {
            gbc.insets = insets != null ? insets : new Insets(0, 2, 0, 2);
        }
    }
    
    @Override
    public Component add(Component comp) {
        gridx++;
        gbc.gridx = gridx;
        if (layoutType == LayoutType.LEFT) {
            refreshLayout(comp);
        } else {
            add(comp, gbc);
        }
        return comp;
    }
    
    public enum LayoutType {
        LEFT,
        CENTER,
        RIGHT
    }
    
    private void refreshLayout(Component comp) {
        gbc.weightx = 0;
        int lastIndex = gridx - 1;
        final Component lastComponent = this.getComponent(lastIndex);
        this.remove(lastIndex);
        add(comp, gbc);
        gbc.weightx = 1.0;
        add(lastComponent, gbc);
    }
}
