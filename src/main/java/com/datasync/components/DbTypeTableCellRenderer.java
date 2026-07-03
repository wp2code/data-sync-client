package com.datasync.components;

import com.datasync.model.DbType;
import com.datasync.util.IconUtil;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * JTable "类型" 列显示数据库类型图标 + 名称
 *
 * @author liuweiping
 * @date 2026-07-02
 **/
public class DbTypeTableCellRenderer extends DefaultTableCellRenderer {
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof String typeStr) {
            DbType dbType = DbType.fromString(typeStr);
            label.setIcon(IconUtil.getDbTypeIcon(dbType));
            label.setIconTextGap(4);
        }
        return label;
    }
}
