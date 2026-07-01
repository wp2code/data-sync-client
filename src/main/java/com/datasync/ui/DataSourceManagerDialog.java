package com.datasync.ui;

import com.datasync.core.DataSource;
import com.datasync.core.DbConnector;
import com.datasync.util.ConfigUtil;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

/**
 * 数据源管理对话框 — 独立窗口集中管理数据源配置的增删改查
 */
public class DataSourceManagerDialog extends JDialog {
    
    private final JTable configTable;
    
    private final ConfigTableModel tableModel;
    
    // 编辑表单
    private final JTextField editNameField;
    
    private final JComboBox<String> editDbTypeCombo;
    
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
    
    public DataSourceManagerDialog(Frame owner) {
        super(owner, "数据源管理", true);
        this.editingOriginalName = null;
        
        setSize(780, 720);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12, 12));
        getRootPane().setBorder(new EmptyBorder(16, 16, 16, 16));
        
        // ── 顶部：列表 + 操作按钮 ──
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(new TitledBorder("已保存的数据源"));
        
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
        
        configTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = configTable.getSelectedRow();
                if (row >= 0) {
                    loadConfigToForm(tableModel.getDataSourceAt(row));
                }
            }
        });
        
        JScrollPane tableScroll = new JScrollPane(configTable);
        tableScroll.setPreferredSize(new Dimension(680, 125));
        topPanel.add(tableScroll, BorderLayout.CENTER);
        
        // 表格右侧按钮
        JPanel tableBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        final JButton newBtn = new JButton("新增");
        final JButton deleteBtn = new JButton("删除");
        tableBtnPanel.add(newBtn);
        tableBtnPanel.add(deleteBtn);
        topPanel.add(tableBtnPanel, BorderLayout.SOUTH);
        
        add(topPanel, BorderLayout.NORTH);
        
        // ── 中部：编辑表单 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("数据源详情"), new EmptyBorder(8, 8, 8, 8)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 12, 8, 12);
        
        editNameField = new JTextField(20);
        editDbTypeCombo = new JComboBox<>(new String[] {"PostgreSQL", "MySQL"});
        editDbTypeCombo.setPreferredSize(new Dimension(200, 26));
        editHostField = new JTextField("localhost", 20);
        editPortField = new JTextField("5432", 8);
        editDbNameField = new JTextField(20);
        editSchemaField = new JTextField("public", 20);
        editUserField = new JTextField(20);
        editPassField = new JPasswordField(20);
        
        editSchemaLabel = new JLabel("Schema：");
        
        editDbTypeCombo.addActionListener(e -> {
            String type = (String) editDbTypeCombo.getSelectedItem();
            editPortField.setText(DataSource.getDefaultPort(type != null ? type.toLowerCase() : "PostgreSQL"));
            // 仅在选中 PostgreSQL 时显示 Schema 行
            boolean isPg = "PostgreSQL".equals(type);
            editSchemaLabel.setVisible(isPg);
            editSchemaField.setVisible(isPg);
        });
        
        int row = 0;
        addFormRow(formPanel, gbc, row++, "配置名称：", editNameField);
        addFormRow(formPanel, gbc, row++, "数据库类型：", editDbTypeCombo);
        addFormRow(formPanel, gbc, row++, "主机地址：", editHostField);
        addFormRow(formPanel, gbc, row++, "端　　口：", editPortField);
        addFormRow(formPanel, gbc, row++, "数据库名：", editDbNameField);
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
        addFormRow(formPanel, gbc, row++, "用户名：", editUserField);
        addFormRow(formPanel, gbc, row++, "密　　码：", editPassField);
        
        // 在表单底部添加弹性空白，让表单内容靠上对齐
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createVerticalGlue(), gbc);
        
        add(formPanel, BorderLayout.CENTER);
        
        // ── 底部：操作按钮 + 状态 ──
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        statusArea = new JTextArea(2, 50);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusArea.setBackground(new Color(40, 40, 40));
        statusArea.setForeground(new Color(180, 180, 180));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setPreferredSize(new Dimension(680, 40));
        bottomPanel.add(statusScroll, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        testBtn = new JButton("测试连接");
        final JButton saveBtn = new JButton("保存");
        final JButton cancelBtn = new JButton("取消");
        btnPanel.add(testBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        bottomPanel.add(btnPanel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // ── 事件绑定 ──
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
        String displayType = ds.getDbType().equalsIgnoreCase("postgresql") ? "PostgreSQL" : "MySQL";
        editDbTypeCombo.setSelectedItem(displayType);
        editHostField.setText(ds.getHost());
        editPortField.setText(ds.getPort());
        editDbNameField.setText(ds.getDbName());
        editSchemaField.setText(ds.getSchema() != null ? ds.getSchema() : "public");
        // 根据数据库类型设置 Schema 行可见性
        boolean isPg = "postgresql".equalsIgnoreCase(ds.getDbType());
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
        String type = (String) editDbTypeCombo.getSelectedItem();
        ds.setDbType(type != null ? type.toLowerCase() : "mysql");
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
                JOptionPane.showMessageDialog(this, result, "连接测试",
                        result.startsWith("[SUCCESS]") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
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
        statusArea.setText(msg);
    }
}
