package com.omnikit.ui.datasync;

import com.omnikit.components.ChildLayoutPanel;
import com.omnikit.components.CustomTextField;
import com.omnikit.components.FullscreenJDialog;
import com.omnikit.components.OptionJPanel;
import com.omnikit.components.combobox.IconItem;
import com.omnikit.components.combobox.IconJComboBox;
import com.omnikit.core.DbConnector;
import com.omnikit.model.DataSource;
import com.omnikit.model.DbType;
import com.omnikit.components.ButtonFactory;
import com.omnikit.util.ConfigUtil;
import com.omnikit.util.GlobalUtil;
import com.omnikit.util.IconUtil;
import com.omnikit.util.LogUtil;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * 数据源管理对话框 — 左右布局：左侧为数据源列表（卡片式，参照程序容器）， 右侧为编辑/新增/删除表单及执行日志
 */
public class DataSourceManagerDialog extends FullscreenJDialog {
    
    private final List<OptionJPanel> itemPanelList = new ArrayList<>();
    
    private JPanel dataSourceListPanel;
    
    private CustomTextField searchField;
    
    /**
     * 当前选中的数据源配置与列表项
     */
    private DataSource selectedDataSource;
    
    private OptionJPanel selectedPanel;
    
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
    
    
    private final JScrollPane statusAreaScrollPane;
    
    public DataSourceManagerDialog(Frame owner) {
        super("DATASOURCE", owner, "数据源管理", true, 960, 640);
        this.editingOriginalName = null;
        
        getRootPane().setBorder(new EmptyBorder(16, 16, 16, 16));
        
        // ── 左侧：数据源列表 ──
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("数据源列表"));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        
        // 顶部：关键字搜索
        JPanel leftTopPanel = new JPanel(new BorderLayout(5, 0));
        leftTopPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        searchField = new CustomTextField("输入关键字搜索");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterList();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterList();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterList();
            }
        });
        leftTopPanel.add(searchField, BorderLayout.CENTER);
        leftPanel.add(leftTopPanel, BorderLayout.NORTH);
        
        // 中部：卡片式列表项（宽度始终跟随滚动容器，长文本由 OptionJPanel 内部截断）
        dataSourceListPanel = new ScrollablePanel();
        dataSourceListPanel.setLayout(new BoxLayout(dataSourceListPanel, BoxLayout.Y_AXIS));
        dataSourceListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        dataSourceListPanel.setBackground(UIManager.getColor("Panel.background"));
        dataSourceListPanel.setFocusable(true);
        JScrollPane listScroll = new JScrollPane(dataSourceListPanel);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        
        // 底部：新增按钮
        JButton newBtn = ButtonFactory.createPrimary("新增数据源");
        newBtn.addActionListener(e -> startNew());
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leftBtnPanel.add(newBtn);
        leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);
        
        // ── 右侧：编辑表单 ──
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
                final String type = item.text();
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
        
        // ── 右侧底部：日志 + 操作按钮 ──
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        statusAreaScrollPane = new JScrollPane(LogUtil.DATA_SOURCE_LOG_AREA);
        statusAreaScrollPane.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("日志"), new EmptyBorder(0, 1, 0, 1)));
        bottomPanel.add(statusAreaScrollPane, BorderLayout.CENTER);
        LogUtil.attachClearPopupMenu(LogUtil.DATA_SOURCE_LOG_AREA);
        ChildLayoutPanel btnPanel = new ChildLayoutPanel();
        final JButton deleteBtn = ButtonFactory.createDestructive("删除");
        testBtn = ButtonFactory.createSecondary("测试连接");
        final JButton saveBtn = ButtonFactory.createPrimary("保存");
        btnPanel.add(deleteBtn);
        btnPanel.add(testBtn);
        btnPanel.add(saveBtn);
        bottomPanel.add(btnPanel, BorderLayout.SOUTH);
        
        // ── 右侧整体：表单 + 底部日志/按钮 ──
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formPanel, bottomPanel);
        rightSplitPane.setResizeWeight(1);
        rightSplitPane.setDividerSize(0);
        rightSplitPane.setBorder(null);
        
        // ── 主布局：左右分割 ──
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplitPane);
        mainSplitPane.setDividerLocation(270);
        mainSplitPane.setResizeWeight(0.25);
        mainSplitPane.setDividerSize(6);
        add(mainSplitPane, BorderLayout.CENTER);
        // ── 事件绑定 ──
        deleteBtn.addActionListener(e -> deleteDataSource(selectedDataSource));
        saveBtn.addActionListener(e -> saveCurrent());
        testBtn.addActionListener(e -> testConnection());
        
        // 初始加载
        refreshList();
    }
    
    // ────────── 列表 ──────────
    
    private void refreshList() {
        String selectedName = selectedDataSource != null ? selectedDataSource.getSourceName() : null;
        dataSourceListPanel.removeAll();
        itemPanelList.clear();
        
        List<DataSource> all = new ArrayList<>();
        for (String name : ConfigUtil.loadAllSourceNames()) {
            DataSource ds = ConfigUtil.loadDataSourceByName(name);
            if (ds != null) {
                all.add(ds);
            }
        }
        if (all.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无数据源）", SwingConstants.CENTER);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            dataSourceListPanel.add(emptyLabel);
        } else {
            for (DataSource ds : all) {
                OptionJPanel panel = createItemPanel(ds);
                itemPanelList.add(panel);
                dataSourceListPanel.add(panel);
                dataSourceListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        
        filterList();
        // 恢复选中项
        if (selectedName != null) {
            selectByName(selectedName);
        }
        dataSourceListPanel.revalidate();
        dataSourceListPanel.repaint();
    }
    
    private OptionJPanel createItemPanel(DataSource ds) {
        String remark = ds.getDbType() + " · " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
        OptionJPanel panel = new OptionJPanel(ds.getSourceName(), remark, IconUtil.getDbTypeIcon(ds.getDbTypeEnum()));
        panel.setData(ds);
        panel.setToolTipText(remark);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setOnClick(() -> selectDataSource(ds, panel));
        attachItemPopupMenu(panel, ds, panel);
        return panel;
    }
    
    private void attachItemPopupMenu(Component comp, DataSource ds, OptionJPanel panel) {
        comp.addMouseListener(new MouseAdapter() {
            
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
            
            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                selectDataSource(ds, panel);
                JPopupMenu popup = new JPopupMenu();
                GlobalUtil.addMenuItem(popup, "编 辑", () -> selectDataSource(ds, panel));
                GlobalUtil.addMenuItem(popup, "复制新增", () -> duplicateDataSource(ds));
                GlobalUtil.addMenuItem(popup, "删 除", () -> deleteDataSource(ds));
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachItemPopupMenu(child, ds, panel);
            }
        }
    }
    
    private void filterList() {
        String keyword = searchField.getText().trim().toLowerCase();
        boolean anyVisible = false;
        for (int i = 0; i < itemPanelList.size(); i++) {
            OptionJPanel panel = itemPanelList.get(i);
            DataSource ds = (DataSource) panel.getData();
            boolean visible = keyword.isEmpty() || ds.getSourceName().toLowerCase().contains(keyword)
                    || ds.getHost().toLowerCase().contains(keyword);
            panel.setVisible(visible);
            // 同时控制分隔间距的可见性
            int spacerIndex = i * 2 + 1;
            if (spacerIndex < dataSourceListPanel.getComponentCount()) {
                dataSourceListPanel.getComponent(spacerIndex).setVisible(visible);
            }
            if (visible) {
                anyVisible = true;
            }
        }
        
        // 处理空提示
        for (Component comp : dataSourceListPanel.getComponents()) {
            if (comp instanceof JLabel label && "（暂无数据源）".equals(label.getText())) {
                comp.setVisible(!anyVisible && keyword.isEmpty());
            }
        }
        dataSourceListPanel.revalidate();
        dataSourceListPanel.repaint();
    }
    
    private void selectDataSource(DataSource ds, OptionJPanel panel) {
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = panel;
        selectedPanel.setSelected(true);
        selectedDataSource = ds;
        loadConfigToForm(ds);
    }
    
    private void selectByName(String name) {
        for (OptionJPanel panel : itemPanelList) {
            DataSource ds = (DataSource) panel.getData();
            if (name.equals(ds.getSourceName())) {
                selectDataSource(ds, panel);
                return;
            }
        }
    }
    
    // ────────── 表单编辑 ──────────
    
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
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = null;
        selectedDataSource = null;
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
        setStatus("新建数据源配置…");
        editNameField.requestFocusInWindow();
    }
    
    /**
     * 复制新增：以指定数据源为模板填充新建表单，名称自动追加「-副本」后缀。 表单不关联已有配置（selectedDataSource 置空），保存时按新建插入
     */
    private void duplicateDataSource(DataSource ds) {
        // 取消列表选中态，使保存按新建处理
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = null;
        selectedDataSource = null;
        loadConfigToForm(ds);
        editingOriginalName = null;
        String newName = ds.getSourceName() + "-副本";
        editNameField.setText(newName);
        setStatus("复制数据源配置 \"" + ds.getSourceName() + "\"，请编辑后保存");
        editNameField.requestFocusInWindow();
    }
    
    private DataSource buildFromForm() {
        DataSource ds = new DataSource();
        final IconItem selectedItem = editDbTypeCombo.getSelectedItem();
        if (selectedItem != null) {
            ds.setDbTypeEnum(DbType.fromString(selectedItem.text().trim()));
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
            refreshList();
            // 选中刚保存的项
            selectByName(name);
        } else {
            setStatus("[FAIL] 保存失败，配置名称可能重复: " + name);
        }
    }
    
    private void deleteDataSource(DataSource ds) {
        if (ds == null) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的数据源", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = ds.getSourceName();
        int confirm = JOptionPane.showConfirmDialog(this, "确认删除数据源 \"" + name + "\" 吗？\n此操作不可撤销。", "确认删除",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        ConfigUtil.deleteDataSource(name);
        setStatus("[OK] 已删除配置: " + name);
        if (selectedDataSource != null && selectedDataSource.getSourceName().equals(name)) {
            selectedDataSource = null;
            selectedPanel = null;
            startNew();
        }
        refreshList();
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
        LogUtil.appendLog(msg, LogUtil.DATA_SOURCE_LOG_AREA);
        if (statusAreaScrollPane != null) {
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = statusAreaScrollPane.getVerticalScrollBar();
                vertical.setValue(0);
            });
        }
    }
    
    /**
     * 宽度跟随滚动容器的列表面板：禁止横向滚动， 保证列表项内的长文本（如连接信息）按实际可用宽度截断显示
     */
    private static class ScrollablePanel extends JPanel implements Scrollable {
        
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }
        
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }
        
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
