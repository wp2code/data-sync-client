package com.datasync.ui;

import com.datasync.components.ChildLayoutPanel;
import com.datasync.components.DbTypeTableCellRenderer;
import com.datasync.components.FullscreenJDialog;
import com.datasync.components.combobox.IconItem;
import com.datasync.components.combobox.IconJComboBox;
import com.datasync.core.DbConnector;
import com.datasync.model.DataSource;
import com.datasync.model.DbType;
import com.datasync.util.ConfigUtil;
import com.datasync.util.IconUtil;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

/**
 * 数据源管理对话框 — 独立窗口集中管理数据源配置的增删改查
 */
public class DataSourceManagerDialog extends FullscreenJDialog {
    
    private final JTable configTable;
    
    private final ConfigTableModel tableModel;
    
    // 编辑表单
    private final JTextField editNameField;
    
    private final IconJComboBox editDbTypeCombo;
    
    private final JTextField editHostField;
    
    private final JTextField editPortField;
    
    private final JTextField editDbNameField;
    
    private final JLabel editSchemaLabel;
    
    private final JTextField editSchemaField;
    
    private final JTextField editUserField;
    
    private final JPasswordField editPassField;
    
    private final JButton testBtn;
    
    private String editingOriginalName;  // 修改模式下保存的原始名称，用于 UPDATE
    
    private final JTextArea statusArea;
    
    private final JScrollPane statusAreaScrollPane;
    
    public DataSourceManagerDialog(Frame owner) {
        super("DATASOURCE", owner, "数据源管理", true, 780, 720);
        this.editingOriginalName = null;
        
//        setLayout(new BorderLayout(12, 12));
        getRootPane().setBorder(new EmptyBorder(16, 16, 16, 16));
        
        // ── 顶部：列表 + 操作按钮 ──
        JPanel topDataPanel = new JPanel(new BorderLayout(0, 0));
        topDataPanel.setBorder(BorderFactory.createTitledBorder("已保存的数据源"));
        tableModel = new ConfigTableModel();
        configTable = new JTable(tableModel);
        configTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configTable.setRowHeight(22);
        configTable.getTableHeader().setReorderingAllowed(false);
        configTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        configTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        configTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        configTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        
        configTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        configTable.getColumnModel().getColumn(1).setCellRenderer(new DbTypeTableCellRenderer());
        configTable.getColumnModel().getColumn(2).setCellRenderer((table1, value, isSelected, hasFocus, row, column1) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        });
        configTable.getColumnModel().getColumn(3).setCellRenderer((table1, value, isSelected, hasFocus, row, column1) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        });
        configTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = configTable.getSelectedRow();
                if (row >= 0) {
                    loadConfigToForm(tableModel.getDataSourceAt(row));
                }
            }
        });
        JScrollPane tableScroll = new JScrollPane(configTable);
        topDataPanel.add(tableScroll, BorderLayout.CENTER);
        // 表格右侧按钮
        ChildLayoutPanel tableBtnPanel = new ChildLayoutPanel(new Insets(5, 0, 5, 5), ChildLayoutPanel.LayoutType.RIGHT);
        final JButton newBtn = new JButton("新增");
        final JButton deleteBtn = new JButton("删除");
        tableBtnPanel.add(newBtn);
        tableBtnPanel.add(deleteBtn);
        topDataPanel.add(tableBtnPanel, BorderLayout.SOUTH);
        
        // ── 中部：编辑表单 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("数据源详情"), new EmptyBorder(8, 8, 8, 8)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 12, 8, 12);
        
        editNameField = new JTextField(20);
        editDbTypeCombo = new IconJComboBox();
        editDbTypeCombo.addItem(DbType.POSTGRESQL_ITEM);
        editDbTypeCombo.addItem(DbType.MYSQL_ITEM);
        editDbTypeCombo.setPreferredSize(new Dimension(200, 26));
        editHostField = new JTextField("localhost", 20);
        editPortField = new JTextField("5432", 8);
        editDbNameField = new JTextField(20);
        editSchemaField = new JTextField("public", 20);
        editUserField = new JTextField(20);
        editPassField = new JPasswordField(20);
        
        editSchemaLabel = new JLabel("        模式：");
        
        editDbTypeCombo.addActionListener(e -> {
            IconItem item = editDbTypeCombo.getSelectedItem();
            if (item != null) {
                final String type = item.getText();
                DbType dbType = DbType.fromString(type);
                editPortField.setText(String.valueOf(dbType.getDefaultPort()));
                // 仅在选中 PostgreSQL 时显示 Schema 行
                boolean isPg = dbType == DbType.POSTGRESQL;
                editSchemaLabel.setVisible(isPg);
                editSchemaField.setVisible(isPg);
            }
        });
        
        int row = 0;
        addFormRow(formPanel, gbc, row++, "   配置名称：", editNameField);
        addFormRow(formPanel, gbc, row++, "数据库类型：", editDbTypeCombo);
        addFormRow(formPanel, gbc, row++, "  主机地址：", editHostField);
        addFormRow(formPanel, gbc, row++, "  端　　口：", editPortField);
        addFormRow(formPanel, gbc, row++, "  数据库名：", editDbNameField);
        // Schema 行：手动添加以便单独控制 label/field 可见性
        {
            gbc.gridy = row++;
            gbc.gridx = 0;
            gbc.weightx = 0.12;
            gbc.ipady = 4;
            gbc.anchor = GridBagConstraints.EAST;
            editSchemaLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
            formPanel.add(editSchemaLabel, gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.88;
            gbc.anchor = GridBagConstraints.WEST;
            formPanel.add(editSchemaField, gbc);
            gbc.ipady = 0;
        }
        addFormRow(formPanel, gbc, row++, "     用户名：", editUserField);
        addFormRow(formPanel, gbc, row++, "   密　　码：", editPassField);
        
        // 在表单底部添加弹性空白，让表单内容靠上对齐
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createVerticalGlue(), gbc);
        JSplitPane mianJsplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topDataPanel, formPanel);
        mianJsplitPane.setResizeWeight(1);
        add(mianJsplitPane, BorderLayout.CENTER);
        // ── 底部：操作按钮 + 状态 ──
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        
        statusArea = new JTextArea(2, 50);
        statusArea.setEditable(false);
        statusArea.setFont(UiConstants.FONT_MONO_11);
        statusArea.setBackground(UiConstants.COLOR_LOG_BG);
        statusArea.setForeground(UiConstants.COLOR_LOG_FG);
        statusAreaScrollPane = new JScrollPane(statusArea);
        statusAreaScrollPane.setBorder(BorderFactory.createTitledBorder("日志"));
        bottomPanel.add(statusAreaScrollPane, BorderLayout.CENTER);
        ChildLayoutPanel btnPanel = new ChildLayoutPanel();
        testBtn = new JButton("测试连接");
        final JButton saveBtn = new JButton("保存");
        final JButton cancelBtn = new JButton("取消");
        final JButton clearBtn = new JButton("清空日志");
        btnPanel.add(clearBtn);
        btnPanel.add(testBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        bottomPanel.add(btnPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
        // ── 事件绑定 ──
        clearBtn.addActionListener(e -> statusArea.setText(""));
        newBtn.addActionListener(e -> startNew());
        deleteBtn.addActionListener(e -> deleteSelected());
        saveBtn.addActionListener(e -> saveCurrent());
        cancelBtn.addActionListener(e -> cancelEdit());
        testBtn.addActionListener(e -> testConnection());
        
        // 初始加载
        refreshTable();
    }
    
    // ────────── 表格模型 ──────────
    
    private static class ConfigTableModel extends AbstractTableModel {
        
        private final String[] columns = {"名称", "类型", "主机", "端口", "数据库"};
        
        private final List<DataSource> dataSources = new ArrayList<>();
        
        void setData(List<DataSource> list) {
            dataSources.clear();
            dataSources.addAll(list);
            fireTableDataChanged();
        }
        
        DataSource getDataSourceAt(int row) {
            return dataSources.get(row);
        }
        
        @Override
        public int getRowCount() {
            return dataSources.size();
        }
        
        @Override
        public int getColumnCount() {
            return columns.length;
        }
        
        @Override
        public String getColumnName(int col) {
            return columns[col];
        }
        
        @Override
        public Object getValueAt(int row, int col) {
            DataSource ds = dataSources.get(row);
            return switch (col) {
                case 0 -> ds.getSourceName();
                case 1 -> ds.getDbType().substring(0, 1).toUpperCase() + ds.getDbType().substring(1);
                case 2 -> ds.getHost();
                case 3 -> ds.getPort();
                case 4 -> ds.getDbName();
                default -> "";
            };
        }
    }
    
    // ────────── 操作方法 ──────────
    
    private void refreshTable() {
        List<DataSource> all = new ArrayList<>();
        for (String name : ConfigUtil.loadAllSourceNames()) {
            DataSource ds = ConfigUtil.loadDataSourceByName(name);
            if (ds != null) {
                all.add(ds);
            }
        }
        tableModel.setData(all);
    }
    
    private void loadConfigToForm(DataSource ds) {
        editingOriginalName = ds.getSourceName();
        editNameField.setText(ds.getSourceName());
        DbType dbType = ds.getDbTypeEnum();
        editDbTypeCombo.setSelectedItem(dbType == DbType.POSTGRESQL ? DbType.POSTGRESQL_ITEM : DbType.MYSQL_ITEM);
        editHostField.setText(ds.getHost());
        editPortField.setText(ds.getPort());
        editDbNameField.setText(ds.getDbName());
        editSchemaField.setText(ds.getSchema() != null ? ds.getSchema() : "public");
        // 根据数据库类型设置 Schema 行可见性
        boolean isPg = ds.isPostgresql();
        editSchemaLabel.setVisible(isPg);
        editSchemaField.setVisible(isPg);
        editUserField.setText(ds.getUsername());
        editPassField.setText(ds.getPassword());
        setStatus("已加载配置: " + ds.getSourceName());
    }
    
    private void startNew() {
        editingOriginalName = null;
        editNameField.setText("");
        editDbTypeCombo.setSelectedIndex(0);
        editHostField.setText("localhost");
        editPortField.setText("5432");
        editDbNameField.setText("");
        editSchemaField.setText("public");
        editSchemaLabel.setVisible(true);
        editSchemaField.setVisible(true);
        editUserField.setText("");
        editPassField.setText("");
        configTable.clearSelection();
        setStatus("新建数据源配置…");
    }
    
    private DataSource buildFromForm() {
        DataSource ds = new DataSource();
        final IconItem selectedItem = editDbTypeCombo.getSelectedItem();
        if (selectedItem != null) {
            ds.setDbTypeEnum(DbType.fromString(selectedItem.getText().trim()));
        }
        ds.setHost(editHostField.getText().trim());
        ds.setPort(editPortField.getText().trim());
        ds.setDbName(editDbNameField.getText().trim());
        ds.setSchema(editSchemaField.getText().trim());
        ds.setUsername(editUserField.getText().trim());
        ds.setPassword(new String(editPassField.getPassword()));
        return ds;
    }
    
    private void saveCurrent() {
        String name = editNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写配置名称", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        DataSource ds = buildFromForm();
        ds.setSourceName(name);
        
        boolean ok;
        if (editingOriginalName != null && !editingOriginalName.equals(name)) {
            // 名称变更：先删旧再插新
            ConfigUtil.deleteDataSource(editingOriginalName);
            ok = ConfigUtil.saveDataSource(ds);
        } else if (editingOriginalName != null) {
            // 同名称更新
            ok = ConfigUtil.updateDataSource(ds);
        } else {
            // 新建
            ok = ConfigUtil.saveDataSource(ds);
        }
        
        if (ok) {
            setStatus("[OK] 配置 \"" + name + "\" 已保存");
            editingOriginalName = name;
            refreshTable();
            // 选中刚保存的行
            selectByName(name);
        } else {
            setStatus("[FAIL] 保存失败，配置名称可能重复: " + name);
        }
    }
    
    private void selectByName(String name) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (name.equals(tableModel.getValueAt(i, 0))) {
                configTable.setRowSelectionInterval(i, i);
                return;
            }
        }
    }
    
    private void deleteSelected() {
        int row = configTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先在表格中选择要删除的数据源", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "确认删除数据源 \"" + name + "\" 吗？\n此操作不可撤销。", "确认删除",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        ConfigUtil.deleteDataSource(name);
        setStatus("[OK] 已删除配置: " + name);
        startNew();
        refreshTable();
    }
    
    private void closeDialog() {
        dispose();
    }
    
    private void cancelEdit() {
        int row = configTable.getSelectedRow();
        if (row >= 0) {
            loadConfigToForm(tableModel.getDataSourceAt(row));
        } else {
            startNew();
        }
        closeDialog();
    }
    
    private void testConnection() {
        DataSource ds = buildFromForm();
        if (!ds.isValid()) {
            setStatus("[FAIL] 参数不完整，请填写所有必填项");
            return;
        }
        testBtn.setEnabled(false);
        setStatus("测试连接中…");
        new Thread(() -> {
            String result = DbConnector.testConnection(ds);
            SwingUtilities.invokeLater(() -> {
                setStatus(result);
                testBtn.setEnabled(true);
                boolean isSuccess = result.startsWith("[SUCCESS]");
                if (isSuccess) {
                    JOptionPane.showMessageDialog(this, result, "连接测试", JOptionPane.INFORMATION_MESSAGE, IconUtil.success());
                } else {
                    JOptionPane.showMessageDialog(this, result, "连接测试", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }
    
    // ────────── 辅助方法 ──────────
    
    private void addFormRow(JPanel panel, GridBagConstraints gbc, int y, String label, JComponent field) {
        gbc.gridy = y;
        gbc.gridx = 0;
        gbc.weightx = 0.12;
        gbc.ipady = 4;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel jLabel = new JLabel(label);
        jLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
        panel.add(jLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 0.88;
        gbc.ipady = 4;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
        gbc.ipady = 0; // 恢复默认，避免影响后续弹性空白行
    }
    
    private void setStatus(String msg) {
        msg = "[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg;
        String text = statusArea.getText().trim();
        if (!text.isEmpty()) {
            text = msg + "\n" + text;
        } else {
            text = msg;
        }
        statusArea.setCaretPosition(0);
        statusArea.setText(text);
        if (statusAreaScrollPane != null) {
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = statusAreaScrollPane.getVerticalScrollBar();
                vertical.setValue(0);
            });
        }
    }
    
}
