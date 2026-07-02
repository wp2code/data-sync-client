/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.components.combobox;

import java.awt.*;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-07-02
 **/
public class DbTypeListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        // 先调 super：拿到选中态背景、边框等默认行为
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        
        if (value instanceof IconItem) {
            IconItem item = (IconItem) value;
            setIcon(item.getIcon());
            setText(item.getText());
        } else {
            setIcon(null);
            setText(value == null ? "" : value.toString());
        }
        return this;
    }
}
