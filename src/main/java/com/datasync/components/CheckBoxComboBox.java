/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.components;

import com.datasync.ui.UiConstants;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

/**
 * 多选复选框下拉组件：外观模仿标准 JComboBox，点击后弹出带复选框的列表，支持多选。
 * <p>
 * 使用 UIManager 系统颜色确保与标准 JComboBox 风格一致；展示文本根据选中项动态变化： 无选中时显示默认占位文本；选中项较少时逐项列出；选中项较多时显示数量摘要。
 *
 * @param <T> 选项数据类型
 */
public class CheckBoxComboBox<T> extends JPanel {
    
    private final JTextField displayField;
    
    private final JButton arrowButton;
    
    private final JPopupMenu popup;
    
    private final JPanel checkBoxPanel;
    
    private final JCheckBox selectAllCheckBox;
    
    private final LinkedHashMap<T, JCheckBox> checkBoxMap = new LinkedHashMap<>();
    
    private String defaultText = "请选择";
    
    private Function<T, String> labelProvider = Object::toString;
    
    private boolean suppressEvents = false;
    
    private final List<ActionListener> actionListeners = new ArrayList<>();
    
    public CheckBoxComboBox() {
        super(new BorderLayout());
        setOpaque(false);
        // 从 UIManager 获取 JComboBox 相关颜色，确保风格统一
        final Color comboBackground = resolveColor("ComboBox.background", Color.WHITE);
        //        final Color comboForeground = resolveColor("ComboBox.foreground", Color.BLACK);
        //        final Color comboDisabledBg = resolveColor("ComboBox.disabledBackground", new Color(0xF0, 0xF0, 0xF0));
        final Color placeholderForeground = resolveColor("TextField.inactiveForeground", Color.GRAY);
        // 边框和箭头颜色使用固定值，避免 UIManager 返回白色导致不可见
        final Color comboBorderColor = nonWhiteColor("ComboBox.borderColor", new Color(0xAB, 0xAB, 0xAB));
        final Color comboArrowBg = nonWhiteColor("ComboBox.buttonBackground", new Color(0xE8, 0xE8, 0xE8));
        final Color comboArrowHoverBg = Color.GRAY;
        final Color arrowShadow = nonWhiteColor("ComboBox.buttonShadow", Color.DARK_GRAY);
        
        // 文本展示区域（模拟下拉框主体）
        displayField = new JTextField();
        displayField.setEditable(false);
        displayField.setFont(UiConstants.FONT_SANS_12);
        displayField.setBackground(comboBackground);
        displayField.setForeground(placeholderForeground);
        displayField.setBorder(new EmptyBorder(2, 6, 2, 4));
        
        displayField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        displayField.setOpaque(true);
        
        // 下拉箭头按钮（自绘，与 JComboBox 箭头区域一致）
        arrowButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(comboArrowHoverBg);
                } else {
                    g2.setColor(comboArrowBg);
                }
                g2.fillRect(0, 0, getWidth(), getHeight());
                // 绘制下拉箭头
                g2.setColor(arrowShadow);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int[] xPoints = {cx - 4, cx + 4, cx};
                int[] yPoints = {cy - 2, cy - 2, cy + 3};
                g2.fillPolygon(xPoints, yPoints, 3);
                g2.dispose();
            }
            
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(20, super.getPreferredSize().height);
            }
        };
        arrowButton.setFocusPainted(false);
        arrowButton.setBorderPainted(false);
        arrowButton.setContentAreaFilled(false);
        arrowButton.setOpaque(false);
        arrowButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // 箭头面板（带左边框分隔线，与 JComboBox 箭头分隔线一致）
        final JPanel arrowPanel = new JPanel(new BorderLayout());
        arrowPanel.add(arrowButton, BorderLayout.CENTER);
        arrowPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, comboBorderColor));
        arrowPanel.setOpaque(false);
        arrowPanel.setBackground(comboArrowBg);
        
        // 组合边框（模拟 JComboBox 外观）
        CompoundBorder border = new CompoundBorder(BorderFactory.createLineBorder(Color.GRAY), new EmptyBorder(1, 1, 1, 1));
        setBorder(border);
        add(displayField, BorderLayout.CENTER);
        add(arrowPanel, BorderLayout.EAST);
        
        // 弹出菜单
        popup = new JPopupMenu();
        popup.setBorderPainted(true);
        
        checkBoxPanel = new JPanel();
        checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));
        checkBoxPanel.setBorder(new EmptyBorder(4, 6, 4, 6));
        checkBoxPanel.setBackground(comboBackground);
        
        // 全选复选框
        selectAllCheckBox = new JCheckBox("全选");
        selectAllCheckBox.setFont(UiConstants.FONT_SANS_12_BOLD);
        selectAllCheckBox.setBackground(comboBackground);
        selectAllCheckBox.setOpaque(true);
        selectAllCheckBox.addActionListener(e -> {
            if (suppressEvents) {
                return;
            }
            boolean selectAll = selectAllCheckBox.isSelected();
            suppressEvents = true;
            try {
                for (JCheckBox cb : checkBoxMap.values()) {
                    cb.setSelected(selectAll);
                }
            } finally {
                suppressEvents = false;
            }
            updateDisplayText();
            fireActionPerformed();
        });
        checkBoxPanel.add(selectAllCheckBox);
        // 分隔线（紧凑间距）
        checkBoxPanel.add(Box.createVerticalStrut(1));
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        checkBoxPanel.add(separator);
        checkBoxPanel.add(Box.createVerticalStrut(1));
        
        JScrollPane scrollPane = new JScrollPane(checkBoxPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBackground(comboBackground);
        popup.add(scrollPane);
        
        // 点击事件
        arrowButton.addActionListener(e -> showPopup());
        displayField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPopup();
            }
        });
        // 点击整个面板也可触发
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPopup();
            }
        });
    }
    
    /**
     * 设置下拉选项（按传入顺序展示）
     *
     * @param items 选项列表
     */
    public void setItems(List<T> items) {
        checkBoxMap.clear();
        checkBoxPanel.removeAll();
        // 重新添加全选复选框和分隔线
        final Color cbBg = resolveColor("ComboBox.background", Color.WHITE);
        selectAllCheckBox.setBackground(cbBg);
        checkBoxPanel.add(selectAllCheckBox);
        checkBoxPanel.add(Box.createVerticalStrut(1));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        checkBoxPanel.add(sep);
        checkBoxPanel.add(Box.createVerticalStrut(1));
        if (items == null) {
            updateDisplayText();
            return;
        }
        for (T item : items) {
            JCheckBox checkBox = new JCheckBox(labelProvider.apply(item));
            checkBox.setFont(UiConstants.FONT_SANS_12);
            checkBox.setBackground(cbBg);
            checkBox.setOpaque(true);
            checkBox.addActionListener(e -> {
                if (suppressEvents) {
                    return;
                }
                syncSelectAllState();
                updateDisplayText();
                fireActionPerformed();
            });
            checkBoxPanel.add(checkBox);
            checkBoxMap.put(item, checkBox);
        }
        updateDisplayText();
    }
    
    /**
     * 设置默认占位文本（无选中项时显示）
     */
    public void setDefaultText(String text) {
        this.defaultText = text;
        updateDisplayText();
    }
    
    /**
     * 设置选项标签生成器（控制每个选项在下拉中的展示文本）
     */
    public void setLabelProvider(Function<T, String> provider) {
        this.labelProvider = provider != null ? provider : Object::toString;
        for (Map.Entry<T, JCheckBox> entry : checkBoxMap.entrySet()) {
            entry.getValue().setText(labelProvider.apply(entry.getKey()));
        }
        updateDisplayText();
    }
    
    /**
     * 返回当前所有选中项（按添加顺序）
     */
    public List<T> getSelectedItems() {
        List<T> selected = new ArrayList<>();
        for (Map.Entry<T, JCheckBox> entry : checkBoxMap.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }
    
    /**
     * 设置选中项（清除当前选中后勾选传入项）
     */
    public void setSelectedItems(List<T> items) {
        suppressEvents = true;
        try {
            for (JCheckBox cb : checkBoxMap.values()) {
                cb.setSelected(false);
            }
            if (items != null) {
                for (T item : items) {
                    JCheckBox cb = checkBoxMap.get(item);
                    if (cb != null) {
                        cb.setSelected(true);
                    }
                }
            }
            syncSelectAllStateSilent();
        } finally {
            suppressEvents = false;
        }
        updateDisplayText();
    }
    
    /**
     * 清除所有选中项
     */
    public void clearSelection() {
        setSelectedItems(null);
    }
    
    /**
     * 返回是否没有任何选中项
     */
    public boolean isSelectionEmpty() {
        for (JCheckBox cb : checkBoxMap.values()) {
            if (cb.isSelected()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 返回选中项数量
     */
    public int getSelectedCount() {
        int count = 0;
        for (JCheckBox cb : checkBoxMap.values()) {
            if (cb.isSelected()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 返回全部选项数量
     */
    public int getTotalCount() {
        return checkBoxMap.size();
    }
    
    /**
     * 添加选择变更监听器
     */
    public void addActionListener(ActionListener l) {
        actionListeners.add(l);
    }
    
    /**
     * 移除变更监听器
     */
    public void removeActionListener(ActionListener l) {
        actionListeners.remove(l);
    }
    
    /**
     * 设置组件首选大小（同时设置内部文本框）
     */
    @Override
    public void setPreferredSize(Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
        if (displayField != null) {
            displayField.setPreferredSize(preferredSize);
        }
    }
    
    /**
     * 设置提示文本
     */
    @Override
    public void setToolTipText(String text) {
        super.setToolTipText(text);
        if (displayField != null) {
            displayField.setToolTipText(text);
        }
        if (arrowButton != null) {
            arrowButton.setToolTipText(text);
        }
    }
    
    /**
     * 设置是否启用
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (displayField != null) {
            displayField.setEnabled(enabled);
            Color bg = resolveColor("ComboBox.background", Color.WHITE);
            Color disabledBg = resolveColor("ComboBox.disabledBackground", new Color(0xF0, 0xF0, 0xF0));
            displayField.setBackground(enabled ? bg : disabledBg);
        }
        if (arrowButton != null) {
            arrowButton.setEnabled(enabled);
        }
    }
    
    private void showPopup() {
        if (!isEnabled()) {
            return;
        }
        if (popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        // 弹出面板宽度至少与组件同宽
        int popupWidth = Math.max(getWidth(), 160);
        int maxVisibleItems = Math.min(checkBoxMap.size() + 1, 12);
        int itemHeight = 24;
        int popupHeight = maxVisibleItems * itemHeight + 12;
        popup.setPopupSize(popupWidth, popupHeight);
        popup.show(this, 0, getHeight());
    }
    
    private void updateDisplayText() {
        Color placeholderFg = resolveColor("TextField.inactiveForeground", Color.GRAY);
        Color comboFg = resolveColor("ComboBox.foreground", Color.BLACK);
        
        if (checkBoxMap.isEmpty()) {
            displayField.setText(defaultText);
            displayField.setForeground(placeholderFg);
            return;
        }
        List<T> selected = getSelectedItems();
        if (selected.isEmpty()) {
            displayField.setText(defaultText);
            displayField.setForeground(placeholderFg);
        } else if (selected.size() == 1) {
            displayField.setText(labelProvider.apply(selected.get(0)));
            displayField.setForeground(comboFg);
        } else if (selected.size() == checkBoxMap.size()) {
            displayField.setText("全部");
            displayField.setForeground(comboFg);
        } else if (selected.size() <= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selected.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(labelProvider.apply(selected.get(i)));
            }
            displayField.setText(sb.toString());
            displayField.setForeground(comboFg);
        } else {
            displayField.setText("已选 " + selected.size() + " 项");
            displayField.setForeground(comboFg);
        }
    }
    
    /**
     * 同步全选复选框状态（在单个选项变更时调用）
     */
    private void syncSelectAllState() {
        boolean allSelected = !checkBoxMap.isEmpty() && checkBoxMap.values().stream().allMatch(JCheckBox::isSelected);
        suppressEvents = true;
        try {
            selectAllCheckBox.setSelected(allSelected);
        } finally {
            suppressEvents = false;
        }
    }
    
    /**
     * 静默同步全选复选框状态（在 setSelectedItems 中调用，不触发事件）
     */
    private void syncSelectAllStateSilent() {
        boolean allSelected = !checkBoxMap.isEmpty() && checkBoxMap.values().stream().allMatch(JCheckBox::isSelected);
        selectAllCheckBox.setSelected(allSelected);
    }
    
    /**
     * 从 UIManager 获取颜色，获取不到或颜色过亮（接近白色）时返回默认值
     */
    private static Color nonWhiteColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        if (c == null) {
            return fallback;
        }
        // 如果颜色太亮（接近白色），使用回退色
        if (c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240) {
            return fallback;
        }
        return c;
    }
    
    /**
     * 从 UIManager 获取颜色，获取不到时返回默认值
     */
    private static Color resolveColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }
    
    private void fireActionPerformed() {
        if (suppressEvents) {
            return;
        }
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "selectionChanged");
        for (ActionListener listener : actionListeners) {
            listener.actionPerformed(event);
        }
    }
}
