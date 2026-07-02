/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.components.combobox;

import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-07-02
 **/
public class IconJComboBox extends JComboBox<IconItem> {
    
    public IconJComboBox() {
        super();
        setRenderer(new IconListRenderer());
    }
    
    @Override
    public void addItem(IconItem item) {
        super.addItem(item);
    }
    
    @Override
    public IconItem getSelectedItem() {
        return (IconItem) dataModel.getSelectedItem();
    }
}
