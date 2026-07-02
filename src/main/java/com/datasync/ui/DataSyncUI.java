package com.datasync.ui;

import com.datasync.components.ChildLayoutPanel;
import com.datasync.components.CustomTextField;
import com.datasync.components.LinkJLabel;
import com.datasync.components.combobox.DbTypeListCellRenderer;
import com.datasync.core.ConnectionWrapper;
import com.datasync.core.DataSource;
import com.datasync.core.DataSyncService;
import com.datasync.core.DbConnector;
import com.datasync.core.DbType;
import com.datasync.core.Side;
import com.datasync.util.ConfigUtil;
import com.datasync.util.GlobalUtil;
import com.datasync.util.IconUtil;
import com.datasync.util.LogUtil;
import com.datasync.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;

/**
 * Swing 主界面 — 聚焦数据同步操作，数据源配置通过管理对话框维护
 */
public class DataSyncUI extends JFrame {
    
    // ────────── 源库选择组件 ──────────
    private JComboBox<String> srcConfigCombo;
    
    private JLabel srcInfoLabel;
    
    // ────────── 目标库选择组件 ──────────
    private JComboBox<String> tgtConfigCombo;
    
    private JLabel tgtInfoLabel;
    
    // ────────── 同步操作组件 ──────────
    private JComboBox<String> srcSyncSchemaCombo;
    
    private JPanel srcSyncTablePanel;
    
    private JComboBox<String> tgtSyncSchemaCombo;
    
    private JPanel tgtSyncTablePanel;
    
    private JLabel srcSchemaLabel;
    
    private JLabel tgtSchemaLabel;
    
    private JButton syncButton;
    
    private LinkJLabel diffLink;
    
    private JCheckBox truncateCheckBox;
    
    private JPanel srcSchemaPanel;
    
    private JPanel tgtSchemaPanel;
    
    private JPanel srcTablePanel;
    
    private JPanel tgtTablePanel;
    
    // ────────── 日志组件 ──────────
    private JEditorPane logArea;
    
    // ────────── 当前连接 ──────────
    private volatile ConnectionWrapper srcConn;
    
    private volatile ConnectionWrapper tgtConn;
    
    private boolean suppressComboEvents = false; // 程序性刷新下拉时抑制事件
    
    // ────────── 服务 ──────────
    private final DataSyncService syncService = new DataSyncService();
    
    // ────────── 构造方法 ──────────
    
    public DataSyncUI() {
        initUI();
        SQLiteConfigUtil.getInstance().initialize();
        refreshConfigCombos();
    }
    
    // ────────── UI 初始化 ──────────
    
    private void initUI() {
        setTitle("DataSync Client — 数据同步工具 v1.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        
        setAppIcon();
        
        // 窗口关闭时释放所有连接
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection(Side.SOURCE, true);
                closeConnection(Side.TARGET, true);
            }
        });
        
        add(createHeaderPanel(), BorderLayout.NORTH);
        
        // 中间区域：源库 + 同步按钮 + 目标库，使用 GridBagLayout 等宽自适应
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBorder(new EmptyBorder(5, 12, 5, 12));
        
        JPanel srcPanel = createDbSidePanel(Side.SOURCE);
        JPanel syncPanel = createSyncActionPanel();
        JPanel tgtPanel = createDbSidePanel(Side.TARGET);
        
        GridBagConstraints gbcCenter = new GridBagConstraints();
        gbcCenter.fill = GridBagConstraints.BOTH;
        gbcCenter.weighty = 1.0;
        gbcCenter.insets = new Insets(0, 0, 0, 0);
        
        gbcCenter.gridx = 0;
        gbcCenter.weightx = 1;
        centerPanel.add(srcPanel, gbcCenter);
        
        gbcCenter.gridx = 1;
        gbcCenter.weightx = 0;
        gbcCenter.insets = new Insets(0, 5, 0, 5);
        centerPanel.add(syncPanel, gbcCenter);
        
        gbcCenter.gridx = 2;
        gbcCenter.weightx = 1;
        gbcCenter.insets = new Insets(0, 0, 0, 0);
        centerPanel.add(tgtPanel, gbcCenter);
        
        add(centerPanel, BorderLayout.CENTER);
        add(createLogPanel(), BorderLayout.SOUTH);
    }
    
    // ────────── 图标 ──────────
    
    private void setAppIcon() {
        try {
            setIconImage(IconUtil.createAppIcon().getImage());
        } catch (Exception ignored) {
        }
    }
    
    // ────────── 头部面板 ──────────
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(12, 20, 8, 20));
        
        JLabel title = new JLabel("DataSync Client");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(title, BorderLayout.WEST);
        
        JButton manageBtn = new JButton("管理数据源");
        manageBtn.addActionListener(e -> openDataSourceManager());
        panel.add(manageBtn, BorderLayout.EAST);
        
        JLabel subtitle = new JLabel("不同环境数据同步工具");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(Color.GRAY);
        panel.add(subtitle, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ────────── 左右两侧面板 ──────────
    
    /**
     * 创建源库或目标库的侧边面板，包含：数据源选择、Schema（单选）、表（多选）、刷新连接
     */
    private JPanel createDbSidePanel(Side side) {
        String title = side == Side.SOURCE ? "源数据库 (Source)" : "目标数据库 (Target)";
        Color accentColor = side == Side.SOURCE ? new Color(0x4F46E5) : new Color(0x059669); // 源=蓝紫, 目标=绿
        JPanel sourcePanel = new JPanel(new BorderLayout(5, 5));
        TitledBorder titledBorder = BorderFactory.createTitledBorder(title);
        titledBorder.setTitleColor(accentColor);
        titledBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 13));
        sourcePanel.setBorder(BorderFactory.createCompoundBorder(titledBorder, new EmptyBorder(6, 8, 6, 8)));
        // ── 标签固定宽度 ──
        Dimension labelDim = new Dimension(55, 25);
        
        // ── 数据源选择行 ──
        JPanel configRow = new JPanel(new BorderLayout(5, 0));
        
        JLabel configLabel = new JLabel("数据源:", SwingConstants.RIGHT);
        configLabel.setFont(UiConstants.FONT_MONO_12);
        configLabel.setPreferredSize(labelDim);
        configRow.add(configLabel, BorderLayout.WEST);
        JComboBox<String> configCombo = new JComboBox<>();
        configCombo.setFont(UiConstants.FONT_MONO_12);
        configRow.add(configCombo, BorderLayout.CENTER);
        
        // ── Schema 选择行（单选）──
        JPanel schemaRow = new JPanel(new BorderLayout(5, 0));
        JLabel schemaLabel = new JLabel("模式:", SwingConstants.RIGHT);
        schemaLabel.setFont(UiConstants.FONT_MONO_12);
        schemaLabel.setPreferredSize(labelDim);
        schemaRow.add(schemaLabel, BorderLayout.WEST);
        JComboBox<String> schemaCombo = new JComboBox<>(new String[] {UiConstants.PLACEHOLDER_CONNECT_FIRST});
        schemaCombo.setEditable(false); // 仅单选，不允许编辑
        schemaCombo.setFont(UiConstants.FONT_MONO_12);
        schemaRow.add(schemaCombo, BorderLayout.CENTER);
        // ── 连接状态标签 ──
        JLabel infoLabel = new JLabel(UiConstants.PLACEHOLDER_SELECT_SOURCE, SwingConstants.CENTER);
        infoLabel.setIcon(null);
        infoLabel.setFont(UiConstants.FONT_MONO_12);
        infoLabel.setForeground(Color.GRAY);
        infoLabel.setBorder(new EmptyBorder(4, 0, 2, 0));
        // ── 按钮行 ──
        final JPanel btnRow = new ChildLayoutPanel();
        JPanel tableRow = null;
        JPanel selectRow = null;
        JPanel tableCheckPanel = null;
        if (side == Side.SOURCE) {
            selectRow = new ChildLayoutPanel();
            JButton selectAllTablesBtn = new JButton("全选");
            selectAllTablesBtn.setFont(UiConstants.FONT_SANS_11);
            JButton deselectTablesBtn = new JButton("取消选中");
            deselectTablesBtn.setFont(UiConstants.FONT_SANS_11);
            // ── 表筛选框──
            final JTextField searchText = new CustomTextField("输入关键字过滤表");
            searchText.setPreferredSize(new Dimension(150, 25));
            selectRow.add(searchText);
            selectRow.add(deselectTablesBtn);
            selectRow.add(selectAllTablesBtn);
            // ── 表选择行（复选框多选）──
            tableRow = new JPanel(new BorderLayout(5, 0));
            JLabel tableLabel = new JLabel("表:", SwingConstants.RIGHT);
            tableLabel.setFont(UiConstants.FONT_MONO_12);
            tableLabel.setPreferredSize(labelDim);
            tableRow.add(tableLabel, BorderLayout.WEST);
            // 使用 JPanel + JCheckBox 实现复选框多选
            tableCheckPanel = new JPanel();
            tableCheckPanel.setLayout(new BoxLayout(tableCheckPanel, BoxLayout.Y_AXIS));
            JScrollPane tableScrollPane = new JScrollPane(tableCheckPanel);
            tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            tableRow.add(tableScrollPane, BorderLayout.CENTER);
            final JPanel finalTableCheckPanelForExport = tableCheckPanel;
            searchText.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    filterTables(searchText, finalTableCheckPanelForExport);
                }
                
                @Override
                public void removeUpdate(DocumentEvent e) {
                    filterTables(searchText, finalTableCheckPanelForExport);
                }
                
                @Override
                public void changedUpdate(DocumentEvent e) {
                    filterTables(searchText, finalTableCheckPanelForExport);
                }
            });
            final JButton exportScriptBtn = new JButton("导出结构SQL");
            exportScriptBtn.setFont(UiConstants.FONT_SANS_11);
            exportScriptBtn.addActionListener(e -> DataSyncUI.this.exportStructureScript(finalTableCheckPanelForExport));
            btnRow.add(exportScriptBtn);
            final JButton exportBtn = new JButton("导出数据SQL");
            exportBtn.setFont(UiConstants.FONT_SANS_11);
            exportBtn.addActionListener(e -> DataSyncUI.this.exportInsertScript(finalTableCheckPanelForExport));
            selectAllTablesBtn.addActionListener(e -> selectAllTables(finalTableCheckPanelForExport));
            deselectTablesBtn.addActionListener(e -> clearTableSelection(finalTableCheckPanelForExport));
            btnRow.add(exportBtn);
        }
        JButton refreshBtn = new JButton("刷新连接");
        refreshBtn.setFont(UiConstants.FONT_SANS_11);
        refreshBtn.addActionListener(e -> refreshConnection(side, infoLabel));
        btnRow.add(refreshBtn);
        // ── 组装面板：使用 GridBagLayout 让下拉框宽度一致 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 5, 0);
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.gridy = 0;
        formPanel.add(configRow, gbc);
        gbc.gridy = 1;
        formPanel.add(schemaRow, gbc);
        gbc.fill = GridBagConstraints.BOTH;
        if (selectRow != null) {
            gbc.gridy = 2;
            gbc.weighty = 0; // 表列表行占用剩余垂直空间
            formPanel.add(selectRow, gbc);
        }
        if (tableRow != null) {
            gbc.gridy = 3;
            gbc.weighty = 1.0; // 表列表行占用剩余垂直空间
            formPanel.add(tableRow, gbc);
        }
        gbc.gridy = side == Side.SOURCE ? 4 : 3;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(btnRow, gbc);
        
        sourcePanel.add(formPanel, BorderLayout.CENTER);
        sourcePanel.add(infoLabel, BorderLayout.SOUTH);
        
        // ── configCombo ActionListener ──
        configCombo.addActionListener(e -> {
            if (suppressComboEvents) {
                return;
            }
            Object sel = configCombo.getSelectedItem();
            if (sel != null && !UiConstants.PLACEHOLDER_SELECT_SOURCE.equals(sel.toString()) && !UiConstants.PLACEHOLDER_NONE.equals(sel.toString())
                    && !UiConstants.PLACEHOLDER_NO_MATCHING.equals(sel.toString())) {
                DataSource ds = ConfigUtil.loadDataSourceByName(sel.toString());
                final String srcDataSource = GlobalUtil.getSrcDataSource();
                if (ds != null && (srcDataSource == null || !srcDataSource.equals(ds.getSourceName()))) {
                    doAutoConnect(ds, title, side, infoLabel);
                }
            } else {
                infoLabel.setIcon(null);
                infoLabel.setText(UiConstants.PLACEHOLDER_SELECT_SOURCE);
                infoLabel.setFont(UiConstants.FONT_SANS_11);
                infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
                infoLabel.setForeground(Color.GRAY);
                closeConnection(side, false);
            }
            if (side == Side.SOURCE) {
                onSourceConfigChanged();
            }
        });
        
        // ── Schema ItemListener ──
        schemaCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                onSchemaSelected(side);
            }
        });
        
        // ── 保存引用 ──
        if (side == Side.SOURCE) {
            srcConfigCombo = configCombo;
            srcInfoLabel = infoLabel;
            srcSyncSchemaCombo = schemaCombo;
            srcSyncTablePanel = tableCheckPanel;
            srcSchemaLabel = schemaLabel;
            srcSchemaPanel = schemaRow;
            srcTablePanel = tableRow;
        } else {
            tgtConfigCombo = configCombo;
            tgtInfoLabel = infoLabel;
            tgtSyncSchemaCombo = schemaCombo;
            tgtSyncTablePanel = tableCheckPanel;
            tgtSchemaLabel = schemaLabel;
            tgtSchemaPanel = schemaRow;
            tgtTablePanel = tableRow;
        }
        
        return sourcePanel;
    }
    
    private void filterTables(JTextField searchText, JPanel tableCheckPanel) {
        String keyword = searchText.getText().trim().toLowerCase();
        for (Component comp : tableCheckPanel.getComponents()) {
            if (comp instanceof JCheckBox) {
                JCheckBox cb = (JCheckBox) comp;
                boolean visible = keyword.isEmpty() || cb.getText().toLowerCase().contains(keyword);
                cb.setVisible(visible);
            }
        }
        tableCheckPanel.revalidate();
        tableCheckPanel.repaint();
    }
    // ────────── 自动连接 ──────────
    
    /**
     * 关闭指定端的旧连接
     */
    private void closeConnection(Side side, boolean isCloseWindow) {
        ConnectionWrapper oldConn = side == Side.SOURCE ? srcConn : tgtConn;
        if (oldConn != null) {
            String sideLabel = side.label();
            if (oldConn.getDataSource() != null) {
                sideLabel += "[" + oldConn.getDataSource().getSourceName() + "]";
            }
            appendLog(LogUtil.logLine(UiConstants.LOG_DISCONNECT + "正在关闭" + sideLabel + "连接…"));
            DbConnector.closeQuietly(oldConn.getConnection());
            if (side == Side.SOURCE) {
                srcConn = null;
                if (isCloseWindow) {
                    GlobalUtil.removeSrcDataSource();
                }
            } else {
                tgtConn = null;
                if (isCloseWindow) {
                    GlobalUtil.removeTargetDataSource();
                }
            }
            appendLog(LogUtil.logLine(UiConstants.LOG_DISCONNECT + sideLabel + "连接已关闭"));
        }
    }
    
    /**
     * 选中数据源后自动连接：先关闭旧连接，再建立新连接
     */
    private void doAutoConnect(DataSource ds, String title, Side side, JLabel infoLabel) {
        if (ds == null || !ds.isValid()) {
            return;
        }
        
        // 先关闭旧连接
        closeConnection(side, false);
        
        appendLog(LogUtil.logLine(UiConstants.LOG_CONNECT + "正在连接 " + title + "…"));
        infoLabel.setIcon(null);
        infoLabel.setText("连接中…");
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setForeground(Color.ORANGE);
        final ConnectThread connectThread = new ConnectThread(this, ds, infoLabel, side, false);
        connectThread.start();
    }
    
    // ────────── 中间同步操作面板 ──────────
    
    private JPanel createSyncActionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(40, 15, 40, 15));
        
        // 同步图标/方向指示
        JLabel arrowLabel = new JLabel("→");
        arrowLabel.setFont(new Font("SansSerif", Font.BOLD, 72));
        arrowLabel.setForeground(new Color(0x4F46E5));
        arrowLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        syncButton = new JButton("开始同步");
        diffLink = new LinkJLabel("比较结构差异", null);
        diffLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        diffLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                compareTableStructure();
            }
        });
        syncButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        syncButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        syncButton.addActionListener(e -> startSync());
        
        truncateCheckBox = new JCheckBox("同步前先清空目标表");
        truncateCheckBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        truncateCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        truncateCheckBox.setToolTipText("勾选后，同步数据前会先清空（TRUNCATE）目标表所有数据");
        
        panel.add(Box.createVerticalGlue());
        panel.add(arrowLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 15)));
        panel.add(truncateCheckBox);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(syncButton);
        panel.add(Box.createRigidArea(new Dimension(0, 8)));
        panel.add(diffLink);
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }
    
    // ────────── 日志面板 ──────────
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("运行日志"), new EmptyBorder(5, 12, 8, 12)));
        panel.setPreferredSize(new Dimension(850, 350));
        
        logArea = new JEditorPane();
        logArea.setEditable(false);
        logArea.setContentType("text/html");
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(200, 200, 200));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JButton clearBtn = new JButton("清空日志");
        clearBtn.addActionListener(e -> {
            LogUtil.clearLog(logArea);
        });
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(clearBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ────────── 数据源管理对话框 ──────────
    
    private void openDataSourceManager() {
        DataSourceManagerDialog dialog = new DataSourceManagerDialog(this);
        dialog.setVisible(true);
        // 对话框关闭后刷新主页下拉框
        refreshConfigCombos();
    }
    
    // ────────── 配置下拉刷新 ──────────
    
    private void refreshConfigCombos() {
        suppressComboEvents = true;
        try {
            refreshSingleCombo(srcConfigCombo, null, null);
            // 目标库根据源库类型过滤，同时排除和源库同名的配置
            String srcType = getSelectedSourceDbType();
            DataSource srcDs = getSelectedSource(Side.SOURCE);
            String excludeName = srcDs != null ? srcDs.getSourceName() : null;
            refreshSingleCombo(tgtConfigCombo, srcType, excludeName);
        } finally {
            suppressComboEvents = false;
        }
    }
    
    private void refreshSingleCombo(JComboBox<String> combo, String filterDbType, String excludeName) {
        String selected = (String) combo.getSelectedItem();
        combo.removeAllItems();
        // 始终在第一项添加提示
        combo.addItem(UiConstants.PLACEHOLDER_SELECT_SOURCE);
        List<String> names = ConfigUtil.loadAllSourceNames();
        List<String> filtered = new ArrayList<>();
        for (String name : names) {
            // 排除指定名称
            if (excludeName != null && excludeName.equals(name)) {
                continue;
            }
            if (filterDbType == null) {
                filtered.add(name);
            } else {
                DataSource ds = ConfigUtil.loadDataSourceByName(name);
                if (ds != null && filterDbType.equalsIgnoreCase(ds.getDbType())) {
                    filtered.add(name);
                }
            }
        }
        if (filtered.isEmpty()) {
            combo.addItem(filterDbType != null ? UiConstants.PLACEHOLDER_NO_MATCHING : UiConstants.PLACEHOLDER_NONE);
        } else {
            filtered.forEach(combo::addItem);
        }
        // 尝试恢复之前的选中项（被排除的项不能恢复），否则选中提示项
        if (selected != null && !selected.equals(excludeName) && !UiConstants.PLACEHOLDER_SELECT_SOURCE.equals(selected)
                && !UiConstants.PLACEHOLDER_NONE.equals(selected) && !UiConstants.PLACEHOLDER_NO_MATCHING.equals(selected)) {
            combo.setSelectedItem(selected);
        } else {
            combo.setSelectedItem(UiConstants.PLACEHOLDER_SELECT_SOURCE);
        }
    }
    
    // ────────── 获取选中数据源 ──────────
    
    private DataSource getSelectedSource(Side side) {
        JComboBox<String> combo = side == Side.SOURCE ? srcConfigCombo : tgtConfigCombo;
        Object sel = combo.getSelectedItem();
        if (sel == null || UiConstants.PLACEHOLDER_SELECT_SOURCE.equals(sel.toString()) || UiConstants.PLACEHOLDER_NONE.equals(sel.toString())
                || UiConstants.PLACEHOLDER_NO_MATCHING.equals(sel.toString())) {
            return null;
        }
        return ConfigUtil.loadDataSourceByName(sel.toString());
    }
    
    /**
     * 获取当前源库的数据库类型（mysql / postgresql），未选择时返回 null
     */
    private String getSelectedSourceDbType() {
        DataSource ds = getSelectedSource(Side.SOURCE);
        return ds != null ? ds.getDbType() : null;
    }
    
    /**
     * 源库配置变更时的联动处理： 1. MySQL 隐藏 Schema 行，PostgreSQL 显示 2. 过滤目标库下拉只显示同类型配置
     */
    private void onSourceConfigChanged() {
        String srcType = getSelectedSourceDbType();
        boolean isPostgres = DbType.fromString(srcType) == DbType.POSTGRESQL;
        // 显示/隐藏 Schema 行
        srcSchemaPanel.setVisible(isPostgres);
        tgtSchemaPanel.setVisible(isPostgres);
        
        // 如果目标库已选中但类型与源库不一致，关闭目标连接
        DataSource tgtDs = getSelectedSource(Side.TARGET);
        if (tgtDs != null && tgtConn != null && srcType != null && !srcType.equalsIgnoreCase(tgtDs.getDbType())) {
            closeConnection(Side.TARGET, false);
            tgtInfoLabel.setIcon(null);
            tgtInfoLabel.setText(UiConstants.PLACEHOLDER_SELECT_SOURCE);
            tgtInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            tgtInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            tgtInfoLabel.setForeground(Color.GRAY);
        }
        
        // 过滤目标库下拉：同类型 + 排除与源库同名
        DataSource srcDs = getSelectedSource(Side.SOURCE);
        String excludeName = srcDs != null ? srcDs.getSourceName() : null;
        suppressComboEvents = true;
        try {
            refreshSingleCombo(tgtConfigCombo, srcType, excludeName);
        } finally {
            suppressComboEvents = false;
        }
        
        // 清空同步面板
        if (!isPostgres) {
            srcSyncSchemaCombo.setSelectedItem("");
            tgtSyncSchemaCombo.setSelectedItem("");
        }
        String tableHint = isPostgres ? UiConstants.PLACEHOLDER_SELECT_SCHEMA : UiConstants.PLACEHOLDER_CONNECT_FIRST;
        setTableCheckItems(srcSyncTablePanel, tableHint);
        setTableCheckItems(tgtSyncTablePanel, tableHint);
    }
    
    // ────────── Schema → Table 联动 ──────────
    
    private void onSchemaSelected(Side side) {
        JComboBox<String> schemaCombo = side == Side.SOURCE ? srcSyncSchemaCombo : tgtSyncSchemaCombo;
        JPanel tablePanel = side == Side.SOURCE ? srcSyncTablePanel : tgtSyncTablePanel;
        Object sel = schemaCombo.getSelectedItem();
        if (sel == null) {
            return;
        }
        String schema = sel.toString().trim();
        if (schema.isEmpty() || schema.startsWith("（")) {
            // 未选中有效 Schema，清空表列表
            setTableCheckItems(tablePanel, UiConstants.PLACEHOLDER_SELECT_SCHEMA);
            return;
        }
        
        DataSource ds = getSelectedSource(side);
        if (ds == null) {
            return;
        }
        ds.setSchema(schema);
        
        // 切换已有连接的 search_path，确保后续查询使用用户选中的 schema
        ConnectionWrapper connWrapper = side == Side.SOURCE ? srcConn : tgtConn;
        if (connWrapper != null && connWrapper.getConnection() != null && ds.isPostgresql()) {
            try {
                connWrapper.getConnection().createStatement().execute("SET search_path TO " + schema + ", public");
            } catch (Exception ignored) {
                // 忽略 SET 失败
            }
        }
        
        fetchTablesInBackground(tablePanel, ds, schema);
    }
    
    private void fetchTablesInBackground(JPanel tablePanel, DataSource ds, String schema) {
        setTableCheckItems(tablePanel, UiConstants.PLACEHOLDER_QUERYING);
        new Thread(() -> {
            List<String> tables = DbConnector.fetchTables(ds, schema);
            SwingUtilities.invokeLater(() -> {
                if (tables.isEmpty()) {
                    setTableCheckItems(tablePanel, UiConstants.PLACEHOLDER_NO_TABLES);
                } else {
                    setTableCheckItems(tablePanel, tables.toArray(new String[0]));
                }
            });
        }).start();
    }
    
    /**
     * 导出选中表的 CREATE TABLE DDL（结构SQL），先弹窗预览，支持一键复制和导出到文件
     */
    private void exportStructureScript(JPanel tablePanel) {
        List<String> checkedTables = getCheckedTables(tablePanel);
        if (checkedTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先勾选需要导出的表", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DataSource ds = getSelectedSource(Side.SOURCE);
        if (ds == null || !ds.isValid()) {
            JOptionPane.showMessageDialog(this, "请先选择并连接源数据库", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        ConnectionWrapper wrapper = srcConn;
        if (wrapper == null || wrapper.getConnection() == null) {
            JOptionPane.showMessageDialog(this, "源数据库未连接，请先连接", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 设置 Schema（PostgreSQL）
        boolean isPg = ds.isPostgresql();
        String schema = null;
        if (isPg) {
            Object schemaObj = srcSyncSchemaCombo.getSelectedItem();
            if (schemaObj == null || schemaObj.toString().startsWith("（")) {
                JOptionPane.showMessageDialog(this, "请先选择 Schema", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            schema = schemaObj.toString().trim();
        }
        
        appendLog(LogUtil.logLine(UiConstants.LOG_EXPORT + "正在生成 " + checkedTables.size() + " 个表的 DDL…"));
        
        // 生成所有表的 DDL
        StringBuilder allDdl = new StringBuilder();
        allDdl.append("-- ============================================\n");
        allDdl.append("-- DataSync Client 导出的表结构 DDL 脚本\n");
        allDdl.append("-- 数据库: ").append(ds.getDbType().toUpperCase()).append(" | ").append(ds.getHost()).append(":").append(ds.getPort())
                .append("/").append(ds.getDbName()).append("\n");
        allDdl.append("-- 表数量: ").append(checkedTables.size()).append("\n");
        allDdl.append("-- ============================================\n\n");
        allDdl.append("SET FOREIGN_KEY_CHECKS = 0;\n\n");
        
        int successCount = 0;
        for (String tableName : checkedTables) {
            String ddl = syncService.generateCreateTableDDL(ds, tableName, schema);
            allDdl.append(ddl);
            if (!ddl.startsWith("-- [ERROR]")) {
                successCount++;
            }
        }
        
        allDdl.append("SET FOREIGN_KEY_CHECKS = 1;\n");
        
        final int finalSuccessCount = successCount;
        final DataSource finalDs = ds;
        final String finalSchema = schema;
        SwingUtilities.invokeLater(() -> {
            appendLog(LogUtil.logLine(
                    "<html><body><span style=\"color: green;\">" + UiConstants.LOG_EXPORT + "DDL 生成完成，共 " + finalSuccessCount + "/"
                            + checkedTables.size() + " 个表</span></body></html>"));
            showDdlPreviewDialog(allDdl.toString(), finalDs, finalSchema, checkedTables);
        });
    }
    
    /**
     * 弹出 DDL 预览对话框，支持一键复制和导出 SQL 文件
     */
    private void showDdlPreviewDialog(String ddl, DataSource ds, String schema, List<String> tableNames) {
        JDialog dialog = new JDialog(this,
                "表结构 DDL 预览 — " + ds.getDbType().toUpperCase() + " | " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName(), false);
        dialog.setSize(780, 600);
        dialog.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        
        // ── 标题 ──
        JLabel titleLabel = new JLabel("共 " + tableNames.size() + " 个表: " + String.join(", ", tableNames));
        titleLabel.setFont(UiConstants.FONT_SANS_12_BOLD);
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // ── DDL 文本区域 ──
        JTextArea textArea = new JTextArea(ddl);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setTabSize(4);
        textArea.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // ── 按钮栏 ──
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        JButton copyBtn = new JButton("一键复制");
        copyBtn.setFont(UiConstants.FONT_SANS_12);
        copyBtn.addActionListener(e -> {
            textArea.selectAll();
            textArea.copy();
            textArea.setCaretPosition(0);
            JOptionPane.showMessageDialog(dialog, "DDL 已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
        });
        
        JButton exportBtn = new JButton("导出 SQL 文件");
        exportBtn.setFont(UiConstants.FONT_SANS_12);
        exportBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("导出表结构 SQL 文件");
            String defaultName = (tableNames.size() == 1 ? tableNames.get(0) : ds.getDbName()) + "_structure.sql";
            fileChooser.setSelectedFile(new java.io.File(defaultName));
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQL 脚本文件 (*.sql)", "sql"));
            if (fileChooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            java.io.File outputFile = fileChooser.getSelectedFile();
            if (!outputFile.getName().toLowerCase().endsWith(".sql")) {
                outputFile = new java.io.File(outputFile.getAbsolutePath() + ".sql");
            }
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(outputFile), java.nio.charset.StandardCharsets.UTF_8))) {
                writer.write(ddl);
                JOptionPane.showMessageDialog(dialog, "导出成功！\n保存至: " + outputFile.getAbsolutePath(), "导出完成",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "导出失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JButton closeBtn = new JButton("关闭");
        closeBtn.setFont(UiConstants.FONT_SANS_12);
        closeBtn.addActionListener(e -> dialog.dispose());
        
        btnPanel.add(copyBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(closeBtn);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);
        
        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }
    
    /**
     * 设置复选框面板的内容。单字符串参数显示为提示标签，字符串数组则显示为复选框列表。
     */
    private void setTableCheckItems(JPanel panel, String... items) {
        if (panel == null) {
            return;
        }
        panel.removeAll();
        if (items.length == 1 && items[0] != null && items[0].startsWith("（")) {
            // 提示文字
            JLabel hint = new JLabel(items[0], SwingConstants.CENTER);
            hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
            hint.setForeground(Color.GRAY);
            panel.add(hint);
        } else {
            for (String item : items) {
                JCheckBox checkBox = new JCheckBox(item);
                checkBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
                //                checkBox.setBackground(Color.WHITE);
                panel.add(checkBox);
            }
        }
        panel.revalidate();
        panel.repaint();
    }
    
    /**
     * 获取面板中所有勾选的复选框文本。
     */
    private List<String> getCheckedTables(JPanel panel) {
        List<String> checked = new ArrayList<>();
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JCheckBox) {
                JCheckBox cb = (JCheckBox) comp;
                if (cb.isSelected()) {
                    checked.add(cb.getText());
                }
            }
        }
        return checked;
    }
    
    /**
     * 全选面板中所有复选框。
     */
    private void selectAllTables(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JCheckBox) {
                ((JCheckBox) comp).setSelected(true);
            }
        }
    }
    
    /**
     * 取消面板中所有复选框的选中状态。
     */
    private void clearTableSelection(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JCheckBox) {
                ((JCheckBox) comp).setSelected(false);
            }
        }
    }
    
    /**
     * 导出选中表的 INSERT SQL 脚本到 .sql 文件，支持选择指定列
     */
    private void exportInsertScript(JPanel tablePanel) {
        List<String> checkedTables = getCheckedTables(tablePanel);
        if (checkedTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先勾选需要导出的表", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DataSource ds = getSelectedSource(Side.SOURCE);
        if (ds == null || !ds.isValid()) {
            JOptionPane.showMessageDialog(this, "请先选择并连接源数据库", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 检查连接是否可用
        ConnectionWrapper wrapper = srcConn;
        if (wrapper == null || wrapper.getConnection() == null) {
            JOptionPane.showMessageDialog(this, "源数据库未连接，请先连接", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 设置 Schema（PostgreSQL）
        boolean isPg = ds.isPostgresql();
        if (isPg) {
            Object schemaObj = srcSyncSchemaCombo.getSelectedItem();
            if (schemaObj == null || schemaObj.toString().startsWith("（")) {
                JOptionPane.showMessageDialog(this, "请先选择 Schema", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ds.setSchema(schemaObj.toString().trim());
        }
        
        // ── 列选择对话框（按表区分）──
        Map<String, List<String>> tableColumnMap = showColumnSelectionDialog(ds, checkedTables);
        if (tableColumnMap == null) {
            return; // 用户取消
        }
        
        // 选择保存路径
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出 INSERT SQL 脚本");
        fileChooser.setSelectedFile(new java.io.File(checkedTables.size() == 1 ? checkedTables.get(0) + ".sql" : ds.getDbName() + "_export.sql"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQL 脚本文件 (*.sql)", "sql"));
        
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        java.io.File outputFile = fileChooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".sql")) {
            outputFile = new java.io.File(outputFile.getAbsolutePath() + ".sql");
        }
        
        final java.io.File finalFile = outputFile;
        final DataSource finalDs = ds;
        final Map<String, List<String>> finalTableColumnMap = tableColumnMap;
        
        syncButton.setEnabled(false);
        appendLog(LogUtil.logLine(UiConstants.LOG_EXPORT + "开始导出 " + checkedTables.size() + " 个表的 INSERT 脚本…"));
        
        new Thread(() -> {
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(finalFile), java.nio.charset.StandardCharsets.UTF_8))) {
                
                // 文件头
                writer.write("-- ============================================\n");
                writer.write("-- DataSync Client 导出的 INSERT SQL 脚本\n");
                writer.write("-- 数据库: " + finalDs.getDbType().toUpperCase() + " | " + finalDs.getHost() + ":" + finalDs.getPort() + "/"
                        + finalDs.getDbName() + "\n");
                writer.write(
                        "-- 导出时间: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                + "\n");
                writer.write("-- 表数量: " + checkedTables.size() + "\n");
                writer.write("-- ============================================\n\n");
                writer.write("SET FOREIGN_KEY_CHECKS = 0;\n\n");
                
                int totalRows = 0;
                for (int i = 0; i < checkedTables.size(); i++) {
                    String tableName = checkedTables.get(i);
                    final int idx = i;
                    SwingUtilities.invokeLater(() -> appendLog(LogUtil.logLine(
                            UiConstants.LOG_EXPORT + "正在导出表 [" + (idx + 1) + "/" + checkedTables.size() + "]: " + tableName + "…")));
                    
                    List<String> cols = finalTableColumnMap.get(tableName);
                    String script = syncService.exportInsertScript(finalDs, tableName, wrapper, cols);
                    writer.write(script);
                    writer.write("\n");
                    
                    // 统计行数
                    int rows = countLines(script) - 7;
                    if (rows > 0) {
                        totalRows += rows;
                    }
                }
                
                writer.write("SET FOREIGN_KEY_CHECKS = 1;\n");
                
                final int finalTotalRows = totalRows;
                SwingUtilities.invokeLater(() -> {
                    appendLog(LogUtil.logLine("<html><body><span style=\"color: green; font-weight: bold;\">" + UiConstants.LOG_EXPORT + "导出完成！共 "
                            + checkedTables.size() + " 个表, " + finalTotalRows + " 条数据 → " + finalFile.getAbsolutePath()
                            + "</span></body></html>"));
                    syncButton.setEnabled(true);
                    JOptionPane.showMessageDialog(DataSyncUI.this,
                            "导出成功！\n" + checkedTables.size() + " 个表, 共 " + finalTotalRows + " 条数据\n保存至: " + finalFile.getAbsolutePath(),
                            "导出完成", JOptionPane.INFORMATION_MESSAGE);
                });
                
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(LogUtil.logLine("<html><body><span style=\"color: red;\">" + UiConstants.LOG_EXPORT + "导出失败: " + ex.getMessage()
                            + "</span></body></html>"));
                    syncButton.setEnabled(true);
                    JOptionPane.showMessageDialog(DataSyncUI.this, "导出失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
    
    /**
     * 弹出列选择对话框，每张表独立展示列列表和勾选状态
     *
     * @param ds     数据源配置
     * @param tables 选中的表名列表
     * @return Map<表名, 该表选中的列列表>（空列表=该表全选），取消返回 null
     */
    private Map<String, List<String>> showColumnSelectionDialog(DataSource ds, List<String> tables) {
        String schema = ds.isPostgresql() ? ds.getSchema() : null;
        
        // 获取每张表的列信息
        java.util.LinkedHashMap<String, List<String>> allTableColumns = new java.util.LinkedHashMap<>();
        for (String table : tables) {
            List<String> cols = DbConnector.fetchColumns(ds, table, schema);
            allTableColumns.put(table, cols);
        }
        
        // 检查是否有任何表的列信息
        boolean hasAnyColumns = false;
        for (List<String> cols : allTableColumns.values()) {
            if (!cols.isEmpty()) {
                hasAnyColumns = true;
                break;
            }
        }
        if (!hasAnyColumns) {
            // 无法获取列信息，直接导出所有列
            java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
            for (String table : tables) {
                result.put(table, new ArrayList<>());
            }
            return result;
        }
        
        // 构建对话框
        JDialog dialog = new JDialog(this, "选择导出列", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(480, 520);
        dialog.setLocationRelativeTo(this);
        
        // 提示标签
        JLabel hintLabel = new JLabel("按表选择要导出的列（默认全选）：");
        hintLabel.setBorder(new EmptyBorder(10, 12, 5, 12));
        hintLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        dialog.add(hintLabel, BorderLayout.NORTH);
        
        // 按表分组展示列
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        // 存储所有复选框引用: 表名 → 该表的复选框列表
        LinkedHashMap<String, List<JCheckBox>> allCheckBoxes = new LinkedHashMap<>();
        
        for (java.util.Map.Entry<String, List<String>> entry : allTableColumns.entrySet()) {
            String tableName = entry.getKey();
            List<String> columns = entry.getValue();
            
            // 表名标题
            JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            titleRow.setPreferredSize(new Dimension(400, 28));
            titleRow.setMinimumSize(new Dimension(200, 28));
            JLabel tableTitle = new JLabel("▸ " + tableName + "（" + columns.size() + " 列）");
            tableTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
            tableTitle.setForeground(new Color(0x4F46E5));
            tableTitle.setVerticalAlignment(SwingConstants.CENTER);
            titleRow.add(tableTitle);
            
            // 该表的全选/全不选按钮
            if (columns.size() > 3) {
                JButton tbSelectAll = new JButton("全选");
                tbSelectAll.setFont(new Font("SansSerif", Font.PLAIN, 10));
                JButton tbDeselectAll = new JButton("全不选");
                tbDeselectAll.setFont(new Font("SansSerif", Font.PLAIN, 10));
                titleRow.add(tbSelectAll);
                titleRow.add(tbDeselectAll);
                
                // 延迟绑定（此时 checkBoxes 还未创建）
                final String finalTableName = tableName;
                tbSelectAll.addActionListener(e -> {
                    List<JCheckBox> cbs = allCheckBoxes.get(finalTableName);
                    if (cbs != null) {
                        for (JCheckBox cb : cbs) {
                            cb.setSelected(true);
                        }
                    }
                });
                tbDeselectAll.addActionListener(e -> {
                    List<JCheckBox> cbs = allCheckBoxes.get(finalTableName);
                    if (cbs != null) {
                        for (JCheckBox cb : cbs) {
                            cb.setSelected(false);
                        }
                    }
                });
            }
            mainPanel.add(titleRow);
            // 该表的列复选框（流式排列）
            if (columns.isEmpty()) {
                JLabel noColLabel = new JLabel("   （无法获取列信息）");
                noColLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
                noColLabel.setForeground(Color.GRAY);
                mainPanel.add(noColLabel);
                allCheckBoxes.put(tableName, new ArrayList<>());
            } else {
                JPanel colPanel = new JPanel();
                colPanel.setLayout(new BoxLayout(colPanel, BoxLayout.Y_AXIS));
                List<JCheckBox> checkBoxes = new ArrayList<>();
                for (String col : columns) {
                    JCheckBox cb = new JCheckBox(col, true); // 默认全选
                    cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                    colPanel.add(cb);
                    checkBoxes.add(cb);
                }
                // 用 FlowLayout.LEFT 包装确保靠左
                JPanel colWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                colWrapper.setOpaque(false);
                colWrapper.add(colPanel);
                mainPanel.add(colWrapper);
                allCheckBoxes.put(tableName, checkBoxes);
            }
            
        }
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(new EmptyBorder(0, 10, 0, 10));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dialog.add(scrollPane, BorderLayout.CENTER);
        
        // 底部按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton cancelIncrementBtn = new JButton("取消自增列");
        cancelIncrementBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cancelIncrementBtn.addActionListener(e -> {
            for (java.util.Map.Entry<String, List<String>> entry : allTableColumns.entrySet()) {
                String tableName = entry.getKey();
                String autoCol = DbConnector.fetchAutoIncrementColumn(ds, tableName, schema);
                if (autoCol != null) {
                    List<JCheckBox> cbs = allCheckBoxes.get(tableName);
                    if (cbs != null) {
                        for (JCheckBox cb : cbs) {
                            if (cb.getText().equalsIgnoreCase(autoCol)) {
                                cb.setSelected(false);
                                break;
                            }
                        }
                    }
                }
            }
        });
        btnPanel.add(cancelIncrementBtn);
        //        JButton selectAllBtn = new JButton("全部全选");
        //        selectAllBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        //        selectAllBtn.addActionListener(e -> {
        //            for (List<JCheckBox> cbs : allCheckBoxes.values()) {
        //                for (JCheckBox cb : cbs) {
        //                    cb.setSelected(true);
        //                }
        //            }
        //        });
        
        //        JButton deselectAllBtn = new JButton("全部取消");
        //        deselectAllBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        //        deselectAllBtn.addActionListener(e -> {
        //            for (List<JCheckBox> cbs : allCheckBoxes.values()) {
        //                for (JCheckBox cb : cbs) {
        //                    cb.setSelected(false);
        //                }
        //            }
        //        });
        
        JButton okBtn = new JButton("确定导出");
        okBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cancelBtn.addActionListener(e -> dialog.dispose());
        //        btnPanel.add(selectAllBtn);
        //        btnPanel.add(deselectAllBtn);
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        
        // 结果容器
        @SuppressWarnings("unchecked") final java.util.Map<String, List<String>>[] result = new HashMap[] {null};
        okBtn.addActionListener(e -> {
            java.util.Map<String, List<String>> selected = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, List<JCheckBox>> entry : allCheckBoxes.entrySet()) {
                String tableName = entry.getKey();
                List<JCheckBox> cbs = entry.getValue();
                List<String> allCols = allTableColumns.get(tableName);
                // 如果该表无列信息 → 传空列表表示全选
                if (allCols.isEmpty()) {
                    selected.put(tableName, new ArrayList<>());
                    continue;
                }
                List<String> tableSelected = new ArrayList<>();
                for (JCheckBox cb : cbs) {
                    if (cb.isSelected()) {
                        tableSelected.add(cb.getText());
                    }
                }
                // 校验：每张表必须至少选中一列
                if (tableSelected.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "表 [" + tableName + "] 未选择任何列，请至少选择一列！", "校验失败",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                // 如果该表的列全部选中 → 传空列表表示全选
                if (tableSelected.size() == allCols.size()) {
                    selected.put(tableName, new ArrayList<>());
                } else {
                    selected.put(tableName, tableSelected);
                }
            }
            result[0] = selected;
            dialog.dispose();
        });
        
        dialog.setVisible(true);
        return result[0];
    }
    
    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
    
    private void fetchSchemasInBackground(JComboBox<String> schemaCombo, DataSource ds) {
        new Thread(() -> {
            List<String> schemas = DbConnector.fetchSchemas(ds);
            SwingUtilities.invokeLater(() -> {
                if (schemas.isEmpty()) {
                    schemas.add("public");
                }
                // 在最前面插入占位项，默认不选中任何 Schema
                schemas.add(0, "（请选择 Schema）");
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(schemas.toArray(new String[0]));
                schemaCombo.setModel(model);
                // 如果 DataSource 中已保存了 schema 配置，自动选中它
                String savedSchema = ds.getSchema();
                if (savedSchema != null && !savedSchema.isBlank() && !"public".equals(savedSchema)) {
                    // 检查该 schema 是否在列表中
                    for (int i = 1; i < schemas.size(); i++) {
                        if (savedSchema.equals(schemas.get(i))) {
                            schemaCombo.setSelectedItem(savedSchema);
                            break;
                        }
                    }
                }
            });
        }).start();
    }
    
    private static void setModelQuietly(JComboBox<String> combo, DefaultComboBoxModel<String> model) {
        combo.setModel(model);
    }
    
    /**
     * 刷新连接：先关闭旧连接，再重新连接数据库，然后重新加载元数据
     */
    private void refreshConnection(Side side, JLabel infoLabel) {
        String sideLabel = side.label();
        DataSource ds = getSelectedSource(side);
        if (ds == null || !ds.isValid()) {
            appendLog(LogUtil.logLine("<html><body><span style=\"color: red;\">" + UiConstants.LOG_REFRESH + sideLabel
                    + "：未选择有效数据源，跳过刷新</span></body></html>"));
            return;
        }
        
        appendLog(LogUtil.logLine(UiConstants.LOG_REFRESH + "正在刷新" + sideLabel + "连接…"));
        
        // 重置 Schema 和表选择
        JComboBox<String> schemaCombo = side == Side.SOURCE ? srcSyncSchemaCombo : tgtSyncSchemaCombo;
        JPanel tablePanel = side == Side.SOURCE ? srcSyncTablePanel : tgtSyncTablePanel;
        boolean isPg = ds.isPostgresql();
        if (isPg) {
            setModelQuietly(schemaCombo, new DefaultComboBoxModel<>(new String[] {UiConstants.PLACEHOLDER_QUERYING}));
            setTableCheckItems(tablePanel, UiConstants.PLACEHOLDER_SELECT_SCHEMA);
        } else {
            setModelQuietly(schemaCombo, new DefaultComboBoxModel<>(new String[] {UiConstants.PLACEHOLDER_CONNECT_FIRST}));
            setTableCheckItems(tablePanel, UiConstants.PLACEHOLDER_QUERYING);
        }
        
        // 先关闭旧连接
        closeConnection(side, false);
        // 重新连接
        infoLabel.setIcon(null);
        infoLabel.setText("连接中…");
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setForeground(Color.ORANGE);
        final ConnectThread connectThread = new ConnectThread(this, ds, infoLabel, side, true);
        connectThread.start();
    }
    
    // ────────── 同步执行 ──────────
    
    private void startSync() {
        final DataSource source = getSelectedSource(Side.SOURCE);
        final DataSource target = getSelectedSource(Side.TARGET);
        if (source == null) {
            JOptionPane.showMessageDialog(this, "请先选择源数据库", "参数不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target == null) {
            JOptionPane.showMessageDialog(this, "请先选择目标数据库", "参数不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 源和目标不能是同一个数据源
        String srcName = source.getSourceName();
        String tgtName = target.getSourceName();
        if (srcName != null && srcName.equals(tgtName)) {
            JOptionPane.showMessageDialog(this, "源数据库和目标数据库不能是同一个数据源", "配置错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean isPostgres = source.isPostgresql();
        // ── 获取复选框选中的源表列表 ──
        List<String> srcTables = getCheckedTables(srcSyncTablePanel);
        String srcSchema = "";
        String tgtSchema = "";
        if (isPostgres) {
            Object srcSchemaObj = srcSyncSchemaCombo.getSelectedItem();
            Object tgtSchemaObj = tgtSyncSchemaCombo.getSelectedItem();
            srcSchema = srcSchemaObj != null ? srcSchemaObj.toString().trim() : "";
            tgtSchema = tgtSchemaObj != null ? tgtSchemaObj.toString().trim() : "";
            final int selectedIndex = tgtSyncSchemaCombo.getSelectedIndex();
            if (selectedIndex == 0) {
                JOptionPane.showMessageDialog(this, "请选择目标Schema", "参数不完整", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!srcSchema.equals(tgtSchema)) {
                JOptionPane.showMessageDialog(this, "选择的目标数据库模式和源数据库模式不一致", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (srcTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请勾选源表", "参数不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        source.setSchema(srcSchema);
        target.setSchema(tgtSchema);
        
        if (!source.isValid()) {
            JOptionPane.showMessageDialog(this, "源数据库参数不完整", "参数不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!target.isValid()) {
            JOptionPane.showMessageDialog(this, "目标数据库参数不完整", "参数不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // ── 确认弹窗 ──
        StringBuilder confirmMsg = new StringBuilder("确认同步以下 " + srcTables.size() + " 个表？\n\n");
        for (int i = 0; i < srcTables.size(); i++) {
            confirmMsg.append("  • ").append(srcTables.get(i));
            
            if (i < srcTables.size() - 1) {
                confirmMsg.append("\n");
            }
        }
        
        if (truncateCheckBox.isSelected()) {
            confirmMsg.append("\n\n⚠ 已勾选\"同步前清空目标表\"，目标表现有数据将被删除！");
        }
        int confirm = JOptionPane.showConfirmDialog(this, confirmMsg.toString(), "确认同步", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        
        syncButton.setEnabled(false);
        final boolean truncateBeforeSync = truncateCheckBox.isSelected();
        final List<String> finalSrcTables = new ArrayList<>(srcTables);
        final String finalSrcSchema = srcSchema;
        final String finalTgtSchema = tgtSchema;
        
        appendLog(LogUtil.logLine(UiConstants.LOG_SYNC + "======== 开始批量同步 " + finalSrcTables.size() + " 个表 ========"));
        if (truncateBeforeSync) {
            appendLog(LogUtil.logLine(UiConstants.LOG_SYNC + "已开启\"同步前清空目标表\"，将先清空目标表数据"));
        }
        
        Consumer<String> logConsumer = msg -> SwingUtilities.invokeLater(() -> appendLog(LogUtil.logLine(msg)));
        
        // 捕获当前连接引用
        final ConnectionWrapper[] wrappers = new ConnectionWrapper[] {srcConn, tgtConn};
        
        new Thread(() -> {
            int totalSyncedRows = 0;
            int failedTables = 0;
            
            try {
                ConnectionWrapper srcWrapper = waitForConnection(wrappers[0], Side.SOURCE);
                ConnectionWrapper tgtWrapper = waitForConnection(wrappers[1], Side.TARGET);
                
                for (int i = 0; i < finalSrcTables.size(); i++) {
                    String tableName = finalSrcTables.get(i);
                    String logPrefix =
                            isPostgres ? "[" + (i + 1) + "/" + finalSrcTables.size() + "] 源[" + source.getSourceName() + "][" + finalSrcSchema + "."
                                    + tableName + "] → 目标[" + target.getSourceName() + "][" + finalTgtSchema + "." + tableName + "]"
                                    : "[" + (i + 1) + "/" + finalSrcTables.size() + "] 源[" + tableName + "] → 目标[" + tableName + "]";
                    appendLog(LogUtil.logLine(UiConstants.LOG_SYNC + logPrefix + " 开始…"));
                    
                    int rows = syncService.syncTableWithConn(source, target, tableName, tableName, truncateBeforeSync, srcWrapper, tgtWrapper,
                            logConsumer);
                    if (rows >= 0) {
                        totalSyncedRows += rows;
                        appendLog(LogUtil.logLine(UiConstants.LOG_SYNC + logPrefix + " 完成，同步 " + rows + " 条"));
                    } else {
                        failedTables++;
                        appendLog(LogUtil.logLine(UiConstants.LOG_SYNC + logPrefix + " 失败！"));
                    }
                }
                
                final int finalTotalRows = totalSyncedRows;
                final int finalFailed = failedTables;
                SwingUtilities.invokeLater(() -> {
                    if (finalFailed == 0) {
                        JOptionPane.showMessageDialog(this, "全部同步完成！\n" + finalSrcTables.size() + " 个表，共同步 " + finalTotalRows + " 条数据",
                                "同步成功", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "同步结束（部分失败）\n成功: " + (finalSrcTables.size() - finalFailed) + " 个表, 共 " + finalTotalRows + " 条\n失败: "
                                        + finalFailed + " 个表，请查看日志详情", "同步结果", JOptionPane.WARNING_MESSAGE);
                    }
                    syncButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(LogUtil.logLine(UiConstants.LOG_ERROR + "同步异常: " + ex.getMessage()));
                    syncButton.setEnabled(true);
                });
            }
        }).start();
    }
    
    // ────────── 连接等待 ──────────
    
    /**
     * 等待异步连接就绪。如果已有连接则直接返回；否则轮询等待直到连接建立或超时。
     *
     * @param current 当前已知的连接引用
     * @param side    数据库侧
     * @return 就绪的 ConnectionWrapper，超时仍为 null
     */
    private ConnectionWrapper waitForConnection(ConnectionWrapper current, Side side) {
        if (current != null && current.getConnection() != null) {
            return current;
        }
        
        String sideLabel = side.label();
        appendLog(LogUtil.logLine(UiConstants.LOG_WAIT + "等待" + sideLabel + "连接就绪…"));
        
        long deadline = System.currentTimeMillis() + UiConstants.CONNECT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ConnectionWrapper conn = side == Side.SOURCE ? srcConn : tgtConn;
            if (conn != null && conn.getConnection() != null) {
                appendLog(LogUtil.logLine(UiConstants.LOG_WAIT + sideLabel + "连接已就绪"));
                return conn;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 超时：返回 null，syncTableWithConn 内部会自动创建连接
        appendLog(LogUtil.logLine(UiConstants.LOG_WAIT + sideLabel + "连接等待超时，将自动创建新连接"));
        return null;
    }
    
    // ────────── 连接全局访问 ──────────
    
    /**
     * 获取源数据库当前连接（可能为 null）
     */
    public ConnectionWrapper getSrcConn() {
        return srcConn;
    }
    
    /**
     * 获取目标数据库当前连接（可能为 null）
     */
    public ConnectionWrapper getTgtConn() {
        return tgtConn;
    }
    
    // ────────── 日志辅助 ──────────
    
    
    public void appendLog(String msg) {
        LogUtil.appendLog(msg, logArea);
    }
    
    public static class ConnectThread extends Thread {
        
        private final DataSyncUI dataSyncUI;
        
        private final DataSource ds;
        
        private final JLabel infoLabel;
        
        private final Side side;
        
        private final boolean isReset;
        
        public ConnectThread(DataSyncUI dataSyncUI, DataSource ds, JLabel infoLabel, Side side, boolean isReset) {
            this.dataSyncUI = dataSyncUI;
            this.ds = ds;
            this.infoLabel = infoLabel;
            this.side = side;
            this.isReset = isReset;
        }
        
        @Override
        public void run() {
            try {
                Connection conn = DbConnector.getConnection(ds);
                SwingUtilities.invokeLater(() -> {
                    String result;
                    if (isReset) {
                        result = UiConstants.LOG_REFRESH + UiConstants.LOG_SUCCESS + "重新连接成功[" + ds.getSourceName() + "] → " + ds.getDbType()
                                .toUpperCase() + " " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
                    } else {
                        result = UiConstants.LOG_CONNECT + UiConstants.LOG_SUCCESS + "连接成功[" + ds.getSourceName() + "] → " + ds.getDbType()
                                .toUpperCase() + " " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
                    }
                    dataSyncUI.appendLog(LogUtil.logLine(result));
                    // 保存连接
                    if (side == Side.SOURCE) {
                        dataSyncUI.srcConn = new ConnectionWrapper(ds, conn);
                        GlobalUtil.addSrcDataSource(ds);
                    } else {
                        dataSyncUI.tgtConn = new ConnectionWrapper(ds, conn);
                        GlobalUtil.addTargetDataSource(ds);
                    }
                    infoLabel.setIcon(IconUtil.getDbTypeIcon(ds.getDbTypeEnum()));
                    infoLabel.setText(ds.getDbType().toUpperCase() + " | " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName());
                    infoLabel.setFont(UiConstants.FONT_SANS_11);
                    infoLabel.setHorizontalAlignment(SwingConstants.LEADING);
                    infoLabel.setForeground(UiConstants.COLOR_CONNECTED);
                    
                    // 加载元数据
                    boolean isPg = ds.isPostgresql();
                    JComboBox<String> sCombo = side == Side.SOURCE ? dataSyncUI.srcSyncSchemaCombo : dataSyncUI.tgtSyncSchemaCombo;
                    JPanel tPanel = side == Side.SOURCE ? dataSyncUI.srcSyncTablePanel : dataSyncUI.tgtSyncTablePanel;
                    
                    // 重置为默认状态
                    if (isPg) {
                        setModelQuietly(sCombo, new DefaultComboBoxModel<>(new String[] {UiConstants.PLACEHOLDER_QUERYING}));
                        dataSyncUI.setTableCheckItems(tPanel, UiConstants.PLACEHOLDER_SELECT_SCHEMA);
                    } else {
                        setModelQuietly(sCombo, new DefaultComboBoxModel<>(new String[] {UiConstants.PLACEHOLDER_CONNECT_FIRST}));
                        dataSyncUI.setTableCheckItems(tPanel, UiConstants.PLACEHOLDER_QUERYING);
                    }
                    
                    if (isPg) {
                        dataSyncUI.fetchSchemasInBackground(sCombo, ds);
                    } else {
                        dataSyncUI.fetchTablesInBackground(tPanel, ds, null);
                    }
                });
            } catch (Exception e) {
                String failMsg = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    if (isReset) {
                        dataSyncUI.appendLog(LogUtil.logLine(UiConstants.LOG_REFRESH + UiConstants.LOG_FAILED + "连接失败" + failMsg));
                    } else {
                        dataSyncUI.appendLog(LogUtil.logLine(UiConstants.LOG_CONNECT + UiConstants.LOG_FAILED + "连接失败" + failMsg));
                    }
                    infoLabel.setIcon(null);
                    infoLabel.setText("连接失败");
                    infoLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                    infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    infoLabel.setForeground(UiConstants.COLOR_ERROR);
                });
            }
        }
    }
    
    // ────────── 表结构比较 ──────────
    
    /**
     * 比较源库和目标库选中表的结构差异，弹窗展示差异并生成 ALTER TABLE 脚本
     */
    private void compareTableStructure() {
        // 获取勾选的源表列表
        List<String> srcTables = getCheckedTables(srcSyncTablePanel);
        if (srcTables.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在源库勾选需要比较的表", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        DataSource source = getSelectedSource(Side.SOURCE);
        DataSource target = getSelectedSource(Side.TARGET);
        if (source == null || !source.isValid()) {
            JOptionPane.showMessageDialog(this, "请先选择并连接源数据库", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target == null || !target.isValid()) {
            JOptionPane.showMessageDialog(this, "请先选择并连接目标数据库", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 等待连接就绪
        ConnectionWrapper srcWrapper = waitForConnection(srcConn, Side.SOURCE);
        ConnectionWrapper tgtWrapper = waitForConnection(tgtConn, Side.TARGET);
        if (srcWrapper == null || srcWrapper.getConnection() == null) {
            JOptionPane.showMessageDialog(this, "源数据库未连接，请先连接", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (tgtWrapper == null || tgtWrapper.getConnection() == null) {
            JOptionPane.showMessageDialog(this, "目标数据库未连接，请先连接", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String srcSchema = getSelectedSchema(Side.SOURCE);
        String tgtSchema = getSelectedSchema(Side.TARGET);
        
        // 将用户选择的 schema 设置到 DataSource 对象上，确保连接使用正确的 schema
        if (srcSchema != null) {
            source.setSchema(srcSchema);
            if (!srcSchema.equals(tgtSchema)) {
                JOptionPane.showMessageDialog(this, "选择的目标数据库模式和源数据库模式不一致", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (tgtSchema != null) {
            target.setSchema(tgtSchema);
        }
        // 后台执行比较
        new Thread(() -> {
            try {
                StringBuilder allScripts = new StringBuilder();
                StringBuilder summaryDetailHtml = new StringBuilder();
                summaryDetailHtml.append("<html><body style='font-family:SansSerif;font-size:12px;'>");
                boolean hasAnyDiff = false;
                int totalDiffs = 0;
                
                for (String tableName : srcTables) {
                    List<DbConnector.ColumnDetail> srcCols = DbConnector.fetchColumnDetails(source, tableName, srcSchema);
                    List<DbConnector.ColumnDetail> tgtCols = DbConnector.fetchColumnDetails(target, tableName, tgtSchema);
                    
                    if (tgtCols.isEmpty()) {
                        // 目标表不存在
                        summaryDetailHtml.append("<p style='color:red;'><b>").append(tableName).append("</b>: 目标表不存在，需要创建</p>");
                        hasAnyDiff = true;
                        totalDiffs++;
                        continue;
                    }
                    
                    // 列差异
                    List<DataSyncService.ColumnDiff> diffs = syncService.compareTableStructure(srcCols, tgtCols, source, target);
                    
                    // 索引差异
                    List<DbConnector.IndexDetail> srcIndexes = DbConnector.fetchIndexes(source, tableName, srcSchema);
                    List<DbConnector.IndexDetail> tgtIndexes = DbConnector.fetchIndexes(target, tableName, tgtSchema);
                    List<DataSyncService.IndexDiff> indexDiffs = syncService.compareIndexes(srcIndexes, tgtIndexes, target.getDbType(), tgtSchema);
                    
                    boolean hasColDiff = !diffs.isEmpty();
                    boolean hasIdxDiff = !indexDiffs.isEmpty();
                    
                    if (!hasColDiff && !hasIdxDiff) {
                        summaryDetailHtml.append("<p style='color:green;'><b>").append(tableName).append("</b>: 结构一致 ✓</p>");
                    } else {
                        hasAnyDiff = true;
                        int tableDiffs = diffs.size() + indexDiffs.size();
                        totalDiffs += tableDiffs;
                        summaryDetailHtml.append("<p><b>").append(tableName).append("</b>: ").append(tableDiffs).append(" 处差异（列 ")
                                .append(diffs.size()).append(" / 索引 ").append(indexDiffs.size()).append("）</p>");
                        
                        // 列差异详情
                        if (hasColDiff) {
                            summaryDetailHtml.append("<ul>");
                            for (DataSyncService.ColumnDiff diff : diffs) {
                                String icon = diff.type == DataSyncService.DiffType.ADD_COLUMN ? "+"
                                        : diff.type == DataSyncService.DiffType.DROP_COLUMN ? "-"
                                                : diff.type == DataSyncService.DiffType.COMMENT_DIFF ? "💬" : "~";
                                String color = diff.type == DataSyncService.DiffType.ADD_COLUMN ? "green"
                                        : diff.type == DataSyncService.DiffType.DROP_COLUMN ? "red"
                                                : diff.type == DataSyncService.DiffType.COMMENT_DIFF ? "#6B7280" : "orange";
                                summaryDetailHtml.append("<li style='color:").append(color).append(";'>").append(icon).append(" ")
                                        .append(diff.type.getLabel()).append(": <code>").append(diff.columnName).append("</code>");
                                if (diff.type == DataSyncService.DiffType.MODIFY_COLUMN && diff.tgtColumn != null) {
                                    summaryDetailHtml.append(" (").append(diff.tgtColumn.dataType)
                                            .append(diff.tgtColumn.columnSize > 0 ? "(" + diff.tgtColumn.columnSize + ")" : "").append(" → ")
                                            .append(diff.srcColumn.dataType)
                                            .append(diff.srcColumn.columnSize > 0 ? "(" + diff.srcColumn.columnSize + ")" : "").append(")");
                                }
                                if (diff.type == DataSyncService.DiffType.COMMENT_DIFF && diff.tgtColumn != null && diff.srcColumn != null) {
                                    String tgtCmt =
                                            diff.tgtColumn.comment != null && !diff.tgtColumn.comment.isBlank() ? diff.tgtColumn.comment : "(空)";
                                    String srcCmt =
                                            diff.srcColumn.comment != null && !diff.srcColumn.comment.isBlank() ? diff.srcColumn.comment : "(空)";
                                    summaryDetailHtml.append(" (注释: \"").append(tgtCmt).append("\" → \"").append(srcCmt).append("\")");
                                }
                                summaryDetailHtml.append("</li>");
                            }
                            summaryDetailHtml.append("</ul>");
                        }
                        
                        // 索引差异详情
                        if (hasIdxDiff) {
                            summaryDetailHtml.append("<ul>");
                            for (DataSyncService.IndexDiff idxDiff : indexDiffs) {
                                String icon = idxDiff.type == DataSyncService.DiffType.ADD_INDEX ? "+"
                                        : idxDiff.type == DataSyncService.DiffType.DROP_INDEX ? "-" : "~";
                                String color = idxDiff.type == DataSyncService.DiffType.ADD_INDEX ? "green"
                                        : idxDiff.type == DataSyncService.DiffType.DROP_INDEX ? "red" : "orange";
                                summaryDetailHtml.append("<li style='color:").append(color).append(";'>").append(icon).append(" ")
                                        .append(idxDiff.type.getLabel()).append(": <code>").append(idxDiff.indexName).append("</code>").append(" (")
                                        .append(idxDiff.getColumnNames()).append(")");
                                if (idxDiff.isUnique()) {
                                    summaryDetailHtml.append(" <b>UNIQUE</b>");
                                }
                                summaryDetailHtml.append("</li>");
                            }
                            summaryDetailHtml.append("</ul>");
                        }
                        
                        // 生成 ALTER TABLE 脚本（含索引差异）
                        String alterScript = syncService.generateAlterScript(tableName, diffs, target.getDbType(), tgtSchema, indexDiffs);
                        allScripts.append(alterScript).append("\n");
                    }
                }
                summaryDetailHtml.append("</body></html>");
                StringBuilder summaryHtml = new StringBuilder();
                summaryHtml.append("<html><body style='font-family:SansSerif;font-size:13px;'>共检查 <b>").append(srcTables.size())
                        .append("</b> 个表");
                if (hasAnyDiff) {
                    summaryHtml.append("，发现 <b style='color:red;'>").append(totalDiffs).append("</b> 处差异");
                } else {
                    summaryHtml.append("，结构全部一致");
                }
                summaryHtml.append("</body></html>");
                final boolean finalHasDiff = hasAnyDiff;
                final String finalSummaryDetail = summaryDetailHtml.toString();
                final String finalSummary = summaryHtml.toString();
                final String finalScripts = allScripts.toString();
                
                SwingUtilities.invokeLater(() -> showDiffResultDialog(finalSummaryDetail, finalSummary, finalScripts, finalHasDiff, target));
                
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(LogUtil.logLine(UiConstants.LOG_ERROR + "表结构比较失败: " + ex.getMessage()));
                    JOptionPane.showMessageDialog(this, "比较失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
    
    /**
     * 获取当前选中的 Schema（PostgreSQL）或 null（MySQL）
     */
    private String getSelectedSchema(Side side) {
        JComboBox<String> combo = side == Side.SOURCE ? srcSyncSchemaCombo : tgtSyncSchemaCombo;
        if (combo == null) {
            return null;
        }
        Object item = combo.getSelectedItem();
        if (item == null) {
            return null;
        }
        String schema = item.toString().trim();
        // 排除占位文本
        if (schema.startsWith("（")) {
            return null;
        }
        return schema;
    }
    
    /**
     * 弹窗展示结构差异结果和 ALTER 脚本
     */
    private void showDiffResultDialog(String summaryDetailHtml, String summaryHtml, String alterScript, boolean hasDiff, DataSource target) {
        JDialog dialog = new JDialog(this, "表结构差异比较结果", false);
        dialog.setSize(850, 750);
        dialog.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        
        // 底部面板：差异摘要 + 按钮
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("差异摘要"));
        JEditorPane summaryPane = new JEditorPane();
        summaryPane.setContentType("text/html");
        summaryPane.setEditable(false);
        summaryPane.setText(summaryHtml);
        topPanel.add(summaryPane, BorderLayout.NORTH);
        JEditorPane summaryDetailPane = new JEditorPane();
        summaryDetailPane.setContentType("text/html");
        summaryDetailPane.setEditable(false);
        summaryDetailPane.setText(summaryDetailHtml);
        JScrollPane summaryScroll = new JScrollPane(summaryDetailPane);
        summaryScroll.setPreferredSize(new Dimension(810, 150));
        topPanel.add(summaryScroll, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        if (hasDiff && !alterScript.trim().isEmpty()) {
            // 中部：使用 JSplitPane 上下分栏（脚本 + 日志）
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setResizeWeight(0.5);
            
            // 上侧：ALTER TABLE 脚本
            JTextArea scriptArea = new JTextArea();
            scriptArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
            scriptArea.setEditable(false);
            scriptArea.setText(alterScript);
            scriptArea.setBackground(new Color(30, 30, 30));
            JScrollPane scriptScroll = new JScrollPane(scriptArea);
            scriptScroll.setBorder(BorderFactory.createTitledBorder("ALTER TABLE 同步脚本"));
            
            // 下侧：执行日志
            JTextArea execLogArea = new JTextArea();
            execLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
            execLogArea.setEditable(false);
            execLogArea.setBackground(new Color(30, 30, 30));
            JScrollPane execLogScroll = new JScrollPane(execLogArea);
            execLogScroll.setBorder(BorderFactory.createTitledBorder("执行日志"));
            
            splitPane.setTopComponent(scriptScroll);
            splitPane.setBottomComponent(execLogScroll);
            mainPanel.add(splitPane, BorderLayout.CENTER);
            
            // Consumer 用于向弹窗日志区域追加日志
            Consumer<String> dialogLogConsumer = msg -> SwingUtilities.invokeLater(() -> {
                execLogArea.append(msg + "\n");
                // 自动滚动到底部
                execLogArea.setCaretPosition(execLogArea.getDocument().getLength());
            });
            
            // 按钮行
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
            JButton clearBtn = new JButton("清空日志");
            clearBtn.addActionListener(e -> {
                execLogArea.setCaretPosition(0);
                execLogArea.setText("");
            });
            btnPanel.add(clearBtn);
            JButton copyBtn = new JButton("复制脚本到剪贴板");
            copyBtn.addActionListener(e -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(alterScript), null);
                JOptionPane.showMessageDialog(dialog, "已复制到剪贴板！", "提示", JOptionPane.INFORMATION_MESSAGE);
            });
            btnPanel.add(copyBtn);
            
            JButton applyBtn = new JButton("应用到目标库");
            applyBtn.setBackground(new Color(0x3E4EDC));
            applyBtn.setForeground(Color.WHITE);
            applyBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "确定要在目标库[" + target.getSourceName() + "]执行以下 ALTER TABLE 脚本吗？\n此操作将修改目标库表结构！", "确认执行",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    applyBtn.setEnabled(false);
                    copyBtn.setEnabled(false);
                    dialogLogConsumer.accept("[INFO] ======== 开始执行结构同步脚本 ========");
                    executeAlterScript(alterScript, target.getDbType(), dialogLogConsumer, () -> {
                        SwingUtilities.invokeLater(() -> {
                            applyBtn.setEnabled(true);
                            copyBtn.setEnabled(true);
                        });
                    });
                }
            });
            btnPanel.add(applyBtn);
            btnPanel.add(Box.createHorizontalStrut(20));
            JButton closeBtn = new JButton("关闭");
            closeBtn.addActionListener(e -> dialog.dispose());
            btnPanel.add(closeBtn);
            
            bottomPanel.add(btnPanel, BorderLayout.SOUTH);
        } else {
            JButton closeBtn = new JButton("关闭");
            closeBtn.addActionListener(e -> dialog.dispose());
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            btnPanel.add(closeBtn);
            bottomPanel.add(btnPanel, BorderLayout.SOUTH);
        }
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        dialog.setContentPane(mainPanel);
        dialog.setVisible(true);
    }
    
    /**
     * 在目标库执行 ALTER TABLE 脚本
     *
     * @param alterScript ALTER TABLE 脚本
     * @param tgtDbType   目标库类型
     * @param logConsumer 日志回调（输出到弹窗日志区域）
     * @param onComplete  执行完成回调（恢复按钮状态）
     */
    private void executeAlterScript(String alterScript, String tgtDbType, Consumer<String> logConsumer, Runnable onComplete) {
        ConnectionWrapper wrapper = tgtConn;
        if (wrapper == null || wrapper.getConnection() == null) {
            logConsumer.accept("[ERROR] 目标库未连接");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        
        new Thread(() -> {
            Connection conn = wrapper.getConnection();
            try (Statement stmt = conn.createStatement()) {
                conn.setAutoCommit(false);
                
                // 按分号拆分 SQL 语句，跳过空行和注释
                String[] rawStatements = alterScript.split(";");
                int executed = 0;
                int failed = 0;
                String currentPgTable = null; // PG 模式下跟踪当前表名
                
                for (String raw : rawStatements) {
                    // 清洗每段：去掉注释行，提取真正的 SQL
                    String sql = cleanSql(raw);
                    if (sql == null) {
                        continue; // 纯注释或空
                    }
                    
                    // 处理 PG 的多条语句合并（用 \n    缩进分隔的子语句，如 ALTER COLUMN）
                    if (DbType.fromString(tgtDbType) == DbType.POSTGRESQL) {
                        // 如果是完整的 ALTER TABLE 语句，记录表名
                        if (sql.startsWith("ALTER TABLE")) {
                            currentPgTable = extractTableNameFromAlter(sql);
                        }
                        
                        // COMMENT ON COLUMN / CREATE INDEX / DROP INDEX 是独立语句，直接执行
                        if (sql.startsWith("COMMENT ON COLUMN") || sql.startsWith("CREATE ") || sql.startsWith("DROP INDEX")) {
                            try {
                                stmt.executeUpdate(sql);
                                executed++;
                                logConsumer.accept("[OK] " + truncateSql(sql));
                            } catch (SQLException ex) {
                                failed++;
                                logConsumer.accept("[FAILED] " + ex.getMessage() + " | SQL: " + truncateSql(sql));
                            }
                            continue;
                        }
                        
                        // 按 \n    (4空格缩进) 拆分为独立子语句
                        String[] subStatements = sql.split("\n    ");
                        for (int i = 0; i < subStatements.length; i++) {
                            String subSql = subStatements[i].trim();
                            if (subSql.isEmpty()) {
                                continue;
                            }
                            
                            if (i == 0) {
                                // 第一条子语句：保持原样（可能是完整 ALTER TABLE 或独立子句）
                                // 如果不是 ALTER TABLE 也不是 COMMENT ON COLUMN，需要补表名前缀
                                if (!subSql.startsWith("ALTER TABLE") && !subSql.startsWith("COMMENT ON COLUMN") && currentPgTable != null) {
                                    subSql = "ALTER TABLE " + currentPgTable + " " + subSql;
                                }
                            } else {
                                // 后续子语句：补 "ALTER TABLE xxx " 前缀
                                if (currentPgTable != null) {
                                    subSql = "ALTER TABLE " + currentPgTable + " " + subSql;
                                }
                            }
                            
                            try {
                                stmt.executeUpdate(subSql);
                                executed++;
                                logConsumer.accept("[OK] " + truncateSql(subSql));
                            } catch (SQLException ex) {
                                failed++;
                                logConsumer.accept("[FAILED] " + ex.getMessage() + " | SQL: " + truncateSql(subSql));
                            }
                        }
                    } else {
                        // MySQL 模式：直接执行
                        try {
                            stmt.executeUpdate(sql);
                            executed++;
                            logConsumer.accept("[OK] " + truncateSql(sql));
                        } catch (SQLException ex) {
                            failed++;
                            logConsumer.accept("[FAILED] " + ex.getMessage() + " | SQL: " + truncateSql(sql));
                        }
                    }
                }
                
                conn.commit();
                final int finalExecuted = executed;
                final int finalFailed = failed;
                SwingUtilities.invokeLater(() -> {
                    logConsumer.accept("[STRUCT SYNC] 执行完成: 成功 " + finalExecuted + " 条, 失败 " + finalFailed + " 条");
                    if (finalFailed == 0) {
                        logConsumer.accept("[SUCCESS] 表结构同步成功！共执行 " + finalExecuted + " 条 ALTER 语句");
                    } else {
                        logConsumer.accept("[WARN] 部分语句执行失败，成功: " + finalExecuted + " 条, 失败: " + finalFailed + " 条");
                    }
                    // 同时写入主窗口日志
                    appendLog(LogUtil.logLine("[STRUCT SYNC] 执行完成: 成功 " + finalExecuted + " 条, 失败 " + finalFailed + " 条"));
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
                
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                SwingUtilities.invokeLater(() -> {
                    logConsumer.accept("[ERROR] 结构同步失败: " + ex.getMessage());
                    appendLog(LogUtil.logLine(UiConstants.LOG_ERROR + "结构同步失败: " + ex.getMessage()));
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }).start();
    }
    
    /**
     * 从 ALTER TABLE 语句中提取完整表名（含 schema 前缀） 如 ALTER TABLE "public"."users" → "public"."users"
     */
    private String extractTableNameFromAlter(String alterSql) {
        String upper = alterSql.toUpperCase();
        int idx = upper.indexOf("ALTER TABLE ");
        if (idx < 0) {
            return null;
        }
        String rest = alterSql.substring(idx + 12).trim();
        
        // 提取到下一个空格或行尾（即完整表名部分）
        int spaceIdx = rest.indexOf(' ');
        if (spaceIdx > 0) {
            rest = rest.substring(0, spaceIdx);
        }
        // 去掉末尾可能的分号
        if (rest.endsWith(";")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }
    
    /**
     * 截断 SQL 用于日志显示
     */
    private String truncateSql(String sql) {
        return sql.length() > 80 ? sql.substring(0, 77) + "..." : sql;
    }
    
    /**
     * 清洗按分号拆分后的 SQL 片段：去掉注释行，提取真正的 SQL 语句。 返回 null 表示该片段是纯注释或空白，应跳过。
     */
    private String cleanSql(String rawFragment) {
        if (rawFragment == null) {
            return null;
        }
        // 按行拆分，过滤掉注释行和空行
        String[] lines = rawFragment.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append("\n");
            }
            result.append(line); // 保留原始缩进
        }
        String sql = result.toString().trim();
        return sql.isEmpty() ? null : sql;
    }
}
