package com.datasync.ui;

import com.datasync.components.CustomTextField;
import com.datasync.components.FilterComboBox;
import com.datasync.components.FullscreenJDialog;
import com.datasync.components.OptionJPanel;
import com.datasync.components.combobox.IconItem;
import com.datasync.components.combobox.IconJComboBox;
import com.datasync.core.DataSyncService;
import com.datasync.core.DbConnector;
import com.datasync.core.GitLabService;
import com.datasync.model.DataSource;
import com.datasync.model.DbType;
import com.datasync.model.FileParams;
import com.datasync.model.ProjectItem;
import com.datasync.model.Script;
import com.datasync.util.ConfigUtil;
import com.datasync.util.GlobalUtil;
import com.datasync.util.IconUtil;
import com.datasync.util.LogUtil;
import com.datasync.util.SQLiteConfigUtil;
import com.mysql.cj.util.StringUtils;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.RepositoryFileResponse;

/**
 * 脚本管理对话框
 * <p>
 * 支持：新建脚本、选中脚本在控制台预览、编辑保存、删除脚本、在目标库执行脚本。
 *
 * @author liuweiping
 * @date 2026-07-03
 **/
public class ScriptMangerDialog extends FullscreenJDialog {
    
    private final List<OptionJPanel> scriptPanelList = new ArrayList<>();
    
    private JPanel scriptListPanel;
    
    private CustomTextField searchField;
    
    private JTextArea consoleArea;
    
    //    private JTextArea resultArea;
    
    private JTabbedPane resultTabs;
    
    private IconJComboBox dataSourceCombo;
    
    private JPanel dbPanel;
    
    private JComboBox<String> dbCombo;
    
    private JPanel schemaPanel;
    
    private JComboBox<String> schemaCombo;
    
    private JMenuItem saveMenuItem;
    
    private JMenuItem deleteMenuItem;
    
    private JMenuItem uploadMenuItem;
    
    private JMenuItem syncMenuItem;
    
    private JMenuItem exportMenuItem;
    
    private JButton runBtn;
    
    private Script selectedScript;
    
    private OptionJPanel selectedPanel;
    
    public ScriptMangerDialog(Frame owner) {
        super("SCRIPT", owner, "脚本管理", true, 900, 720);
        SQLiteConfigUtil.getInstance().initialize();
        initUI();
        refreshScriptList();
        bindFullscreenShortcuts();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // 避免搜索框默认获得焦点，将焦点移到脚本列表
                scriptListPanel.requestFocusInWindow();
            }
        });
    }
    
    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        
        // ── 左侧面板：脚本列表 ──
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("脚本列表"));
        leftPanel.setPreferredSize(new Dimension(260, 0));
        
        JPanel leftTopPanel = new JPanel(new BorderLayout(5, 0));
        leftTopPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        searchField = new CustomTextField("输入关键字搜索");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterScriptList();
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterScriptList();
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterScriptList();
            }
        });
        leftTopPanel.add(searchField, BorderLayout.CENTER);
        leftPanel.add(leftTopPanel, BorderLayout.NORTH);
        
        scriptListPanel = new JPanel();
        scriptListPanel.setLayout(new BoxLayout(scriptListPanel, BoxLayout.Y_AXIS));
        scriptListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        scriptListPanel.setBackground(UIManager.getColor("Panel.background"));
        scriptListPanel.setFocusable(true);
        
        JScrollPane listScrollPane = new JScrollPane(scriptListPanel);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        
        JButton addBtn = new JButton("新建脚本");
        addBtn.addActionListener(e -> createNewScript());
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leftBtnPanel.add(addBtn);
        leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);
        
        // ── 右侧：控制台 + 运行结果 ──
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        
        // 控制台
        JPanel consolePanel = new JPanel(new BorderLayout(5, 5));
        consolePanel.setBorder(BorderFactory.createTitledBorder("控制台"));
        
        JPanel consoleToolbar = new JPanel(new GridBagLayout());
        consoleToolbar.setBorder(new EmptyBorder(5, 5, 5, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        
        // 运行数据源选择
        JPanel dataSourcePanel = new JPanel(new BorderLayout(5, 0));
        dataSourceCombo = new IconJComboBox();
        dataSourceCombo.addItem(new IconItem(null, "（请选择数据源）"));
        dataSourceCombo.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                onDataSourceSelected();
            }
        });
        dataSourcePanel.add(dataSourceCombo, BorderLayout.CENTER);
        
        // 数据库选择
        dbPanel = new JPanel(new BorderLayout(5, 0));
        dbPanel.setVisible(false);
        dbCombo = new JComboBox<>();
        dbCombo.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                onDatabaseSelected();
            }
        });
        dbPanel.add(dbCombo, BorderLayout.CENTER);
        
        // Schema 选择（仅 PostgreSQL）
        schemaPanel = new JPanel(new BorderLayout(5, 0));
        schemaPanel.setVisible(false);
        schemaCombo = new JComboBox<>();
        schemaCombo.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                updateRunButtonState();
            }
        });
        schemaPanel.add(schemaCombo, BorderLayout.CENTER);
        
        // 按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        runBtn = new JButton("运行");
        runBtn.addActionListener(e -> runSelectedScript());
        btnPanel.add(runBtn);
        
        // 更多操作下拉
        JButton moreBtn = new JButton("更多操作 ▼");
        JPopupMenu moreMenu = new JPopupMenu();
        saveMenuItem = new JMenuItem("保存脚本(Ctrl+S)");
        saveMenuItem.addActionListener(e -> saveSelectedScript());
        deleteMenuItem = new JMenuItem("删除脚本");
        deleteMenuItem.addActionListener(e -> deleteSelectedScript(null));
        
        exportMenuItem = new JMenuItem("导出脚本");
        exportMenuItem.addActionListener(e -> exportSelectedScript());
        JMenuItem clearConsoleItem = new JMenuItem("清空控制台");
        clearConsoleItem.addActionListener(e -> consoleArea.setText(""));
        JMenuItem clearResultItem = new JMenuItem("清空结果和日志");
        clearResultItem.addActionListener(e -> {
            LogUtil.clearLog(LogUtil.SCRIPT_LOG_AREA);
            resultTabs.removeAll();
        });
        JMenuItem fullscreenItem = new JMenuItem("全屏（F11）/ 退出全屏（Esc）");
        fullscreenItem.addActionListener(e -> toggleFullscreen());
        moreMenu.add(saveMenuItem);
        moreMenu.add(deleteMenuItem);
        moreMenu.add(exportMenuItem);
        uploadMenuItem = new JMenuItem("上传GitLab");
        uploadMenuItem.addActionListener(e -> uploadSelectedScript());
        uploadMenuItem.setEnabled(false);
        syncMenuItem = new JMenuItem("同步GitLab");
        syncMenuItem.addActionListener(e -> syncSelectedScript());
        syncMenuItem.setEnabled(false);
        moreMenu.addSeparator();
        moreMenu.add(uploadMenuItem);
        moreMenu.add(syncMenuItem);
        moreMenu.addSeparator();
        moreMenu.add(clearConsoleItem);
        moreMenu.add(clearResultItem);
        moreMenu.addSeparator();
        moreMenu.add(fullscreenItem);
        moreBtn.addActionListener(e -> moreMenu.show(moreBtn, 0, moreBtn.getHeight()));
        btnPanel.add(moreBtn);
        
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        consoleToolbar.add(dataSourcePanel, gbc);
        gbc.gridx = 1;
        consoleToolbar.add(dbPanel, gbc);
        gbc.gridx = 2;
        consoleToolbar.add(schemaPanel, gbc);
        gbc.gridx = 3;
        gbc.weightx = 0.0;
        consoleToolbar.add(btnPanel, gbc);
        
        consoleArea = new JTextArea();
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        consoleArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        consolePanel.add(consoleToolbar, BorderLayout.NORTH);
        consolePanel.add(consoleScroll, BorderLayout.CENTER);
        
        // 运行结果
        resultTabs = new JTabbedPane();
        resultTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        resultTabs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultTabs.setBorder(BorderFactory.createTitledBorder("运行结果"));
        JScrollPane resultScroll = new JScrollPane(LogUtil.SCRIPT_LOG_AREA);
        resultScroll.setBorder(BorderFactory.createTitledBorder("运行日志"));
        resultScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        resultScroll.setPreferredSize(new Dimension(0, 80));
        JSplitPane resultPanelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultTabs, resultScroll);
        resultPanelSplitPane.setResizeWeight(1);
        attachResultAreaPopupMenu(consoleArea, "清空控制台", true);
        attachResultAreaPopupMenu(resultTabs, "清空结果", false);
        attachResultAreaPopupMenu(LogUtil.SCRIPT_LOG_AREA, "清空日志", false);
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, consolePanel, resultPanelSplitPane);
        rightSplitPane.setResizeWeight(0.6);
        rightPanel.add(rightSplitPane, BorderLayout.CENTER);
        JSplitPane rootSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        rootSplit.setDividerLocation(240);
        rootSplit.setResizeWeight(0.25);
        
        add(rootSplit, BorderLayout.CENTER);
        updateEditState();
    }
    
    private void refreshScriptList() {
        scriptListPanel.removeAll();
        scriptPanelList.clear();
        
        List<Script> scripts = ConfigUtil.loadAllScripts();
        if (scripts.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无脚本）", SwingConstants.CENTER);
            emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            scriptListPanel.add(emptyLabel);
        } else {
            for (Script script : scripts) {
                OptionJPanel panel = createScriptPanel(script);
                scriptPanelList.add(panel);
                scriptListPanel.add(panel);
                scriptListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        
        filterScriptList();
        scriptListPanel.revalidate();
        scriptListPanel.repaint();
    }
    
    private void filterScriptList() {
        String keyword = searchField.getText().trim().toLowerCase();
        boolean anyVisible = false;
        for (int i = 0; i < scriptPanelList.size(); i++) {
            OptionJPanel panel = scriptPanelList.get(i);
            Script script = (Script) panel.getData();
            boolean visible = keyword.isEmpty() || script.getScriptName().toLowerCase().contains(keyword);
            panel.setVisible(visible);
            // 同时控制分隔间距的可见性
            int spacerIndex = i * 2 + 1;
            if (spacerIndex < scriptListPanel.getComponentCount()) {
                scriptListPanel.getComponent(spacerIndex).setVisible(visible);
            }
            if (visible) {
                anyVisible = true;
            }
        }
        
        // 处理空提示
        for (Component comp : scriptListPanel.getComponents()) {
            if (comp instanceof JLabel label && "（暂无脚本）".equals(label.getText())) {
                comp.setVisible(!anyVisible && keyword.isEmpty());
            }
        }
        scriptListPanel.revalidate();
        scriptListPanel.repaint();
    }
    
    private OptionJPanel createScriptPanel(Script script) {
        OptionJPanel panel = new OptionJPanel(script.getScriptName(), script.getRemark(), IconUtil.getDbTypeIcon(script.getDbType()));
        panel.setData(script);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setOnClick(() -> selectScript(script, panel));
        attachScriptPopupMenu(panel, script, panel);
        return panel;
    }
    
    private void attachResultAreaPopupMenu(Component comp, String title, boolean copy) {
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
                if (e.isPopupTrigger()) {
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem editItem = new JMenuItem(title);
                    if (comp instanceof JTextArea textArea) {
                        editItem.addActionListener(ev -> textArea.setText(""));
                        if (copy) {
                            JMenuItem copyItem = new JMenuItem("一键复制");
                            copyItem.addActionListener(ee -> {
                                textArea.selectAll();
                                textArea.copy();
                                textArea.setCaretPosition(0);
                                JOptionPane.showMessageDialog(comp.getParent(), "脚本已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
                            });
                            popup.add(copyItem);
                        }
                    }
                    if (comp instanceof JEditorPane editorPane) {
                        editItem.addActionListener(ev -> LogUtil.clearLog(editorPane));
                    }
                    if (comp instanceof JTabbedPane tabbedPane) {
                        editItem.addActionListener(ev -> tabbedPane.removeAll());
                    }
                    popup.add(editItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
    
    private void attachScriptPopupMenu(Component comp, Script script, OptionJPanel panel) {
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
                if (e.isPopupTrigger()) {
                    selectScript(script, panel);
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem editItem = new JMenuItem("编 辑");
                    JMenuItem deleteItem = new JMenuItem("删 除");
                    editItem.addActionListener(ev -> editScript(script, panel));
                    deleteItem.addActionListener(ev -> deleteSelectedScript(script));
                    popup.add(editItem);
                    popup.add(deleteItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachScriptPopupMenu(child, script, panel);
            }
        }
    }
    
    private void editScript(Script script, OptionJPanel panel) {
        if (showScriptMetaDialog(script, "编辑脚本")) {
            return;
        }
        
        String newName = script.getScriptName();
        Script existing = ConfigUtil.loadScriptByName(newName);
        if (existing != null && !existing.getId().equals(script.getId())) {
            JOptionPane.showMessageDialog(this, "已存在同名脚本，请重新命名", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!StringUtils.isNullOrEmpty(script.getFilePath()) && !script.getFilePath().endsWith(".sql")) {
            JOptionPane.showMessageDialog(this, "文件必须是.sql格式", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (ConfigUtil.updateScript(script)) {
            refreshScriptList();
            for (OptionJPanel p : scriptPanelList) {
                Script s = (Script) p.getData();
                if (s.getId().equals(script.getId())) {
                    selectScript(s, p);
                    break;
                }
            }
            logResult(LogUtil.success("更新脚本 [" + newName + "] 成功！"));
        } else {
            JOptionPane.showMessageDialog(this, "更新脚本失败", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void selectScript(Script script, OptionJPanel panel) {
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = panel;
        selectedPanel.setSelected(true);
        selectedScript = script;
        refreshDataSourceCombo();
        if (script != null) {
            uploadMenuItem.setEnabled(!StringUtils.isNullOrEmpty(script.getProjectOrId()) && !StringUtils.isNullOrEmpty(script.getBranch())
                    && !StringUtils.isNullOrEmpty(script.getFilePath()));
            syncMenuItem.setEnabled(uploadMenuItem.isEnabled());
            consoleArea.setText(script.getContent());
        }
        consoleArea.setCaretPosition(0);
        updateEditState();
    }
    
    private void refreshDataSourceCombo() {
        DbType selectedDbType = selectedScript != null ? selectedScript.getDbType() : null;
        String previousSelection = dataSourceCombo.getSelectedItem() != null ? dataSourceCombo.getSelectedItem().getText() : null;
        resetTargetSelectionState();
        dataSourceCombo.removeAllItems();
        dataSourceCombo.addItem(new IconItem(null, "（请选择数据源）"));
        
        List<String> names = ConfigUtil.loadAllSourceNames();
        boolean hasMatch = false;
        for (String name : names) {
            DataSource ds = ConfigUtil.loadDataSourceByName(name);
            if (ds != null && selectedDbType != null && selectedDbType.getKey().equalsIgnoreCase(ds.getDbType())) {
                dataSourceCombo.addItem(new IconItem(IconUtil.getDbTypeIcon(ds.getDbTypeEnum()), name));
                hasMatch = true;
            }
        }
        if (!hasMatch) {
            dataSourceCombo.addItem(new IconItem(null, "（无匹配数据源）"));
        }
        
        // 尝试恢复之前的选择
        if (previousSelection != null && !previousSelection.startsWith("（")) {
            for (int i = 0; i < dataSourceCombo.getItemCount(); i++) {
                if (previousSelection.equals(dataSourceCombo.getItemAt(i).getText())) {
                    dataSourceCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        dataSourceCombo.setSelectedIndex(0);
    }
    
    private void resetTargetSelectionState() {
        dbCombo.removeAllItems();
        dbCombo.addItem("（请选择数据库）");
        schemaCombo.removeAllItems();
        schemaCombo.addItem("（请选择 Schema）");
        dbPanel.setVisible(false);
        schemaPanel.setVisible(false);
        updateRunButtonState();
    }
    
    private void onDataSourceSelected() {
        resetTargetSelectionState();
        IconItem item = dataSourceCombo.getSelectedItem();
        if (item == null || item.getText().startsWith("（")) {
            return;
        }
        DataSource ds = ConfigUtil.loadDataSourceByName(item.getText());
        if (ds == null || !ds.isValid()) {
            return;
        }
        loadDatabasesForSelectedSource(ds);
    }
    
    private void loadDatabasesForSelectedSource(DataSource ds) {
        dbPanel.setVisible(true);
        dbCombo.removeAllItems();
        dbCombo.addItem("（查询中...）");
        dbCombo.setEnabled(false);
        schemaPanel.setVisible(false);
        
        new Thread(() -> {
            List<String> databases = DbConnector.fetchDatabases(ds);
            SwingUtilities.invokeLater(() -> {
                dbCombo.setEnabled(true);
                String defaultDb = ds.getDbName();
                dbCombo.removeAllItems();
                dbCombo.addItem("（请选择数据库）");
                for (String db : databases) {
                    dbCombo.addItem(db);
                }
                if (defaultDb != null) {
                    for (int i = 0; i < dbCombo.getItemCount(); i++) {
                        if (defaultDb.equals(dbCombo.getItemAt(i))) {
                            dbCombo.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                if (dbCombo.getSelectedIndex() <= 0 && dbCombo.getItemCount() > 1) {
                    dbCombo.setSelectedIndex(1);
                }
                onDatabaseSelected();
            });
        }).start();
    }
    
    private void onDatabaseSelected() {
        Object dbItem = dbCombo.getSelectedItem();
        boolean hasDb = dbItem != null && !dbItem.toString().startsWith("（");
        if (!hasDb) {
            schemaPanel.setVisible(false);
            updateRunButtonState();
            return;
        }
        
        IconItem dsItem = dataSourceCombo.getSelectedItem();
        if (dsItem == null || dsItem.getText().startsWith("（")) {
            schemaPanel.setVisible(false);
            updateRunButtonState();
            return;
        }
        DataSource ds = ConfigUtil.loadDataSourceByName(dsItem.getText());
        if (ds == null || !ds.isValid()) {
            schemaPanel.setVisible(false);
            updateRunButtonState();
            return;
        }
        
        ds.setDbName(dbItem.toString());
        if (ds.isPostgresql()) {
            loadSchemasForSelectedDatabase(ds);
        } else {
            schemaPanel.setVisible(false);
            updateRunButtonState();
        }
    }
    
    private void loadSchemasForSelectedDatabase(DataSource ds) {
        schemaPanel.setVisible(true);
        schemaCombo.removeAllItems();
        schemaCombo.addItem("（查询中...）");
        schemaCombo.setEnabled(false);
        
        new Thread(() -> {
            List<String> schemas = DbConnector.fetchSchemas(ds);
            SwingUtilities.invokeLater(() -> {
                schemaCombo.setEnabled(true);
                String savedSchema = ds.getSchema();
                schemaCombo.removeAllItems();
                schemaCombo.addItem("（请选择 Schema）");
                for (String schema : schemas) {
                    schemaCombo.addItem(schema);
                }
                if (savedSchema != null) {
                    for (int i = 0; i < schemaCombo.getItemCount(); i++) {
                        if (savedSchema.equals(schemaCombo.getItemAt(i))) {
                            schemaCombo.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                if (schemaCombo.getSelectedIndex() <= 0 && schemaCombo.getItemCount() > 1) {
                    schemaCombo.setSelectedIndex(1);
                }
                updateRunButtonState();
            });
        }).start();
    }
    
    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(label, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }
    
    /**
     * 打开脚本元信息编辑对话框，包含名称、数据库类型、GitLab 配置、项目、分支、文件路径、备注。 若用户点击确认并校验通过，则把值写入传入的 script 对象并返回 true。
     */
    private boolean showScriptMetaDialog(Script script, String title) {
        JTextField nameField = new JTextField(script.getScriptName() != null ? script.getScriptName() : "", 30);
        IconJComboBox dbTypeInput = new IconJComboBox();
        dbTypeInput.addItem(DbType.POSTGRESQL_ITEM);
        dbTypeInput.addItem(DbType.MYSQL_ITEM);
        dbTypeInput.setSelectedItem(DbType.getIconItem(script.getDbType() != null ? script.getDbType().getKey() : null));
        //项目
        FilterComboBox<ProjectItem> projectCombo = new FilterComboBox<>();
        projectCombo.setEditable(false);
        projectCombo.setEnabled(GitLabService.getInstance().isLogin());
        //分支
        FilterComboBox<String> branchCombo = new FilterComboBox<>();
        branchCombo.setEditable(false);
        branchCombo.setEnabled(false);
        JTextField filePathField = new JTextField(script.getFilePath() != null ? script.getFilePath() : "", 30);
        JTextArea remarkArea = new JTextArea(script.getRemark() != null ? script.getRemark() : "", 4, 30);
        remarkArea.setLineWrap(true);
        remarkArea.setWrapStyleWord(true);
        JScrollPane remarkScroll = new JScrollPane(remarkArea);
        this.loadProjectsForConfig(projectCombo, branchCombo, script.getProjectOrId());
        projectCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                ProjectItem item = (ProjectItem) projectCombo.getSelectedItem();
                if (item != null && (item.getId() != null || item.getPathWithNamespace() != null)) {
                    loadBranchesForProject(item.getProjectOrId(), branchCombo, script.getBranch());
                } else {
                    branchCombo.clearAllItems();
                    branchCombo.setEnabled(false);
                }
            }
        });
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(panel, gbc, 0, new JLabel("脚本名称:"), nameField);
        addFormRow(panel, gbc, 1, new JLabel("数据库类型:"), dbTypeInput);
        addFormRow(panel, gbc, 2, new JLabel("项目:"), projectCombo);
        addFormRow(panel, gbc, 3, new JLabel("分支:"), branchCombo);
        addFormRow(panel, gbc, 4, new JLabel("文件路径（.sql）:"), filePathField);
        
        // 备注：允许垂直方向压缩/伸展
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("备注:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(remarkScroll, gbc);
        
        int option = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return true;
        }
        
        String newName = nameField.getText().trim();
        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "脚本名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return true;
        }
        
        IconItem selectedDbType = dbTypeInput.getSelectedItem();
        ProjectItem selectedProject = (ProjectItem) projectCombo.getSelectedItem();
        String selectedBranch = (String) branchCombo.getSelectedItem();
        script.setScriptName(newName);
        script.setGitLabConfigId(selectedProject != null ? selectedProject.getConfigId() : null);
        script.setDbType(selectedDbType != null ? DbType.fromString(selectedDbType.getText()) : DbType.MYSQL);
        script.setProjectOrId(selectedProject != null && selectedProject.getId() != null ? selectedProject.getProjectOrId() : null);
        script.setBranch(selectedBranch != null && !selectedBranch.startsWith("（") ? selectedBranch : null);
        final String filePath = filePathField.getText().trim();
        if (!StringUtils.isNullOrEmpty(filePath) && !filePath.endsWith(".sql")) {
            JOptionPane.showMessageDialog(this, "文件必须是.sql格式", "错误", JOptionPane.ERROR_MESSAGE);
            return true;
        }
        script.setFilePath(filePathField.getText().trim());
        script.setRemark(remarkArea.getText().trim());
        return false;
    }
    
    private void loadProjectsForConfig(FilterComboBox<ProjectItem> projectCombo, FilterComboBox<String> branchCombo, String preselectProjectOrId) {
        projectCombo.clearAllItems();
        String msg = GitLabService.getInstance().isLogin() ? "（查询中...）" : "(未登录GitLabl)";
        projectCombo.addItem(new ProjectItem(null, null, msg));
        projectCombo.setEnabled(false);
        branchCombo.clearAllItems();
        branchCombo.setEnabled(false);
        if(!GitLabService.getInstance().isLogin()){
            return;
        }
        new Thread(() -> {
            List<Project> projects = GitLabService.getInstance().getProjectList();
            SwingUtilities.invokeLater(() -> {
                try {
                    List<ProjectItem> items = new ArrayList<>();
                    if (projects.isEmpty()) {
                        items.add(new ProjectItem(null, null, "（无项目）"));
                        projectCombo.setAllItems(items);
                        projectCombo.setEnabled(false);
                        return;
                    }
                    items.add(new ProjectItem(null, null, "（请选择项目）"));
                    ProjectItem matched = null;
                    for (Project p : projects) {
                        ProjectItem item = new ProjectItem(p.getId(), p.getPathWithNamespace(), p.getName());
                        if (GitLabService.getInstance().getGitLabAuthConfig() != null) {
                            item.setConfigId(GitLabService.getInstance().getGitLabAuthConfig().getId());
                        }
                        items.add(item);
                        if (preselectProjectOrId != null && preselectProjectOrId.equals(item.getProjectOrId())) {
                            matched = item;
                        }
                    }
                    projectCombo.setAllItems(items);
                    projectCombo.setEnabled(true);
                    if (matched != null) {
                        projectCombo.setSelectedItem(matched);
                    } else {
                        projectCombo.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        projectCombo.clearAllItems();
                        projectCombo.addItem(new ProjectItem(null, null, "（加载失败）"));
                        JOptionPane.showMessageDialog(this, "加载 GitLab 项目失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
        }).start();
    }
    
    private void loadBranchesForProject(String projectOrId, FilterComboBox<String> branchCombo, String preselectBranch) {
        branchCombo.clearAllItems();
        branchCombo.addItem("（查询中...）");
        branchCombo.setEnabled(false);
        new Thread(() -> {
            try {
                List<Branch> branches = GitLabService.getInstance().getBranchList(projectOrId);
                SwingUtilities.invokeLater(() -> {
                    List<String> items = new ArrayList<>();
                    if (branches.isEmpty()) {
                        items.add("（无分支）");
                        branchCombo.setAllItems(items);
                        branchCombo.setEnabled(false);
                        return;
                    }
                    items.add("（请选择分支）");
                    String matched = null;
                    for (Branch b : branches) {
                        String name = b.getName();
                        items.add(name);
                        if (preselectBranch != null && preselectBranch.equals(name)) {
                            matched = name;
                        }
                    }
                    branchCombo.setAllItems(items);
                    branchCombo.setEnabled(true);
                    if (matched != null) {
                        branchCombo.setSelectedItem(matched);
                    } else {
                        branchCombo.setSelectedIndex(0);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    branchCombo.clearAllItems();
                    branchCombo.addItem("（加载失败）");
                    branchCombo.setEnabled(false);
                    JOptionPane.showMessageDialog(this, "加载分支失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
    
    private void createNewScript() {
        Script script = new Script();
        if (showScriptMetaDialog(script, "新建脚本")) {
            return;
        }
        
        String name = script.getScriptName();
        if (ConfigUtil.loadScriptByName(name) != null) {
            JOptionPane.showMessageDialog(this, "已存在同名脚本，请重新命名", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (!ConfigUtil.saveScript(script)) {
            JOptionPane.showMessageDialog(this, "新建脚本失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!StringUtils.isNullOrEmpty(script.getFilePath()) && !script.getFilePath().endsWith(".sql")) {
            JOptionPane.showMessageDialog(this, "文件必须是.sql格式", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 重新加载以获取数据库生成的 id
        Script saved = ConfigUtil.loadScriptByName(name);
        refreshScriptList();
        if (saved != null) {
            for (OptionJPanel panel : scriptPanelList) {
                if (saved.getScriptName().equals(((Script) panel.getData()).getScriptName())) {
                    selectScript(saved, panel);
                    break;
                }
            }
        }
        logResult(LogUtil.success("新建脚本 [" + name + "] 成功"));
    }
    
    //同步GitLab文件到本地
    private void syncSelectedScript() {
        if (selectedScript == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            if (!GitLabService.getInstance().isLogin()) {
                JOptionPane.showMessageDialog(this, "GitLab 未登录或配置无效", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "GitLab 未登录或配置无效：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (StringUtils.isNullOrEmpty(selectedScript.getProjectOrId()) || StringUtils.isNullOrEmpty(selectedScript.getBranch())
                || StringUtils.isNullOrEmpty(selectedScript.getFilePath())) {
            JOptionPane.showMessageDialog(this, "请先配置 GitLab 项目、分支和文件路径", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new Thread(() -> {
            try {
                RepositoryFile file = GitLabService.getInstance().getFile(selectedScript.getProjectOrId(),
                        GlobalUtil.getFullFilePath(selectedScript.getFilePath(), selectedScript.getScriptName() + ".sql"),
                        selectedScript.getBranch());
                String content = new String(Base64.getDecoder().decode(file.getContent()), StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(() -> {
                    consoleArea.setText(content);
                    selectedScript.setContent(content);
                    ConfigUtil.updateScript(selectedScript);
                    logResult(LogUtil.success("从 GitLab 同步脚本 [" + selectedScript.getScriptName() + "] 成功"));
                    JOptionPane.showMessageDialog(this, "同步 GitLab 文件成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logResult(LogUtil.failed("从 GitLab 同步脚本 [" + selectedScript.getScriptName() + "] 失败：" + ex.getMessage()));
                    JOptionPane.showMessageDialog(this, "同步 GitLab 文件失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
    
    //上传文件到GitLab
    private void uploadSelectedScript() {
        if (selectedScript == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            if (!GitLabService.getInstance().isLogin()) {
                JOptionPane.showMessageDialog(this, "GitLab 未登录或配置无效", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "GitLab 未登录或配置无效：" + ex.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String content = consoleArea.getText();
        if (StringUtils.isNullOrEmpty(content)) {
            JOptionPane.showMessageDialog(this, "脚本内容为空，无需上传", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (StringUtils.isNullOrEmpty(selectedScript.getProjectOrId()) || StringUtils.isNullOrEmpty(selectedScript.getBranch())
                || StringUtils.isNullOrEmpty(selectedScript.getFilePath())) {
            JOptionPane.showMessageDialog(this, "请先配置 GitLab 项目、分支和文件路径", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new Thread(() -> {
            try {
                FileParams fileParams = new FileParams();
                fileParams.setProjectOrId(selectedScript.getProjectOrId());
                fileParams.setBranch(selectedScript.getBranch());
                fileParams.setFilePath(selectedScript.getFilePath());
                fileParams.setFileName(selectedScript.getScriptName() + ".sql");
                fileParams.setCommitMessage(selectedScript.getFilePath());
                fileParams.setContent(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
                fileParams.setEncoding("base64");
                final RepositoryFileResponse orUpdateFile = GitLabService.getInstance().createOrUpdateFile(fileParams);
                SwingUtilities.invokeLater(() -> {
                    logResult("上传脚本 [" + selectedScript.getScriptName() + "] 到 GitLab 成功! 分支：" + orUpdateFile.getBranch() + " 地址："
                            + orUpdateFile.getFilePath());
                    JOptionPane.showMessageDialog(this, "上传 GitLab 成功！地址：" + orUpdateFile.getFilePath(), "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logResult("上传脚本 [" + selectedScript.getScriptName() + "] 到 GitLab 失败：" + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "上传 GitLab 失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }
    
    private void saveSelectedScript() {
        if (selectedScript == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        selectedScript.setContent(consoleArea.getText());
        if (ConfigUtil.updateScript(selectedScript)) {
            logResult(LogUtil.success("保存脚本 [" + selectedScript.getScriptName() + "] 成功"));
        } else {
            JOptionPane.showMessageDialog(this, "保存脚本失败", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void deleteSelectedScript(Script script) {
        Long id;
        String scriptName;
        if (script != null) {
            id = script.getId();
            scriptName = script.getScriptName();
        } else {
            if (selectedScript == null) {
                JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            scriptName = selectedScript.getScriptName();
            id = selectedScript.getId();
        }
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除脚本 [" + scriptName + "] 吗？", "确认删除", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        if (ConfigUtil.deleteScript(id)) {
            if (script == null || (selectedScript != null && script.getId().equals(selectedScript.getId()))) {
                selectedScript = null;
                selectedPanel = null;
                consoleArea.setText("");
            }
            refreshScriptList();
            updateEditState();
            logResult(LogUtil.success("删除脚本成功"));
        } else {
            JOptionPane.showMessageDialog(this, "删除脚本失败", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void exportSelectedScript() {
        if (selectedScript == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String content = selectedScript.getContent();
        if (content == null || content.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "脚本内容为空，无需导出", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出脚本为 SQL 文件");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQL 文件 (*.sql)", "sql"));
        chooser.setSelectedFile(new File(selectedScript.getScriptName() + ".sql"));
        
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = chooser.getSelectedFile();
        String fileName = file.getName();
        if (!fileName.toLowerCase().endsWith(".sql")) {
            file = new File(file.getParentFile(), fileName + ".sql");
        }
        
        try (java.io.FileWriter writer = new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content);
            logResult(LogUtil.success("导出脚本成功: " + file.getAbsolutePath()));
            JOptionPane.showMessageDialog(this, "导出成功：\n" + file.getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            logResult(LogUtil.failed("导出脚本失败: " + ex.getMessage()));
            JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void runSelectedScript() {
        if (selectedScript == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String content = consoleArea.getText().trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "脚本内容为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        IconItem dsItem = dataSourceCombo.getSelectedItem();
        if (dsItem == null || dsItem.getText().startsWith("（")) {
            JOptionPane.showMessageDialog(this, "请先选择运行数据源", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        DataSource dataSource = ConfigUtil.loadDataSourceByName(dsItem.getText());
        if (dataSource == null || !dataSource.isValid()) {
            JOptionPane.showMessageDialog(this, "所选数据源无效", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Object dbItem = dbCombo.getSelectedItem();
        if (dbPanel.isVisible() && dbItem != null && !dbItem.toString().startsWith("（")) {
            dataSource.setDbName(dbItem.toString());
        }
        Object schemaItem = schemaCombo.getSelectedItem();
        if (schemaPanel.isVisible() && schemaItem != null && !schemaItem.toString().startsWith("（")) {
            dataSource.setSchema(schemaItem.toString());
        }
        
        DbType scriptDbType = selectedScript.getDbType();
        if (scriptDbType == null || !scriptDbType.getKey().equalsIgnoreCase(dataSource.getDbType())) {
            JOptionPane.showMessageDialog(this, "脚本数据库类型 [" + scriptDbType + "] 与数据源类型 [" + dataSource.getDbType() + "] 不一致",
                    "类型不匹配", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        saveMenuItem.setEnabled(false);
        deleteMenuItem.setEnabled(false);
        runBtn.setEnabled(false);
        String runInfo = "数据源 [" + dataSource.getSourceName() + "] 数据库 [" + dataSource.getDbName() + "]";
        if (dataSource.isPostgresql() && schemaItem != null && !schemaItem.toString().startsWith("（")) {
            runInfo += " Schema [" + schemaItem + "]";
        }
        logResult("开始执行脚本 [" + selectedScript.getScriptName() + "] " + runInfo + "...");
        
        new Thread(() -> {
            Connection conn = null;
            int success = 0;
            int failed = 0;
            int queryIndex = 0;
            SwingUtilities.invokeLater(() -> resultTabs.removeAll());
            try {
                conn = DbConnector.getConnection(dataSource);
                List<String> statements = GlobalUtil.splitSqlStatements(content);
                for (String sql : statements) {
                    if (sql.isEmpty()) {
                        continue;
                    }
                    try (Statement stmt = conn.createStatement()) {
                        boolean isQuery = stmt.execute(sql);
                        if (isQuery) {
                            try (ResultSet rs = stmt.getResultSet()) {
                                ResultSetMetaData meta = rs.getMetaData();
                                int colCount = meta.getColumnCount();
                                Object[] columnNames = new Object[colCount];
                                for (int i = 1; i <= colCount; i++) {
                                    columnNames[i - 1] = meta.getColumnLabel(i);
                                }
                                List<Object[]> rows = new ArrayList<>();
                                int displayCount = 0;
                                int totalCount = 0;
                                while (rs.next()) {
                                    totalCount++;
                                    if (displayCount < DataSyncService.LIMIT_QUERY_COUNT) {
                                        displayCount++;
                                        Object[] row = new Object[colCount];
                                        for (int i = 1; i <= colCount; i++) {
                                            row[i - 1] = rs.getObject(i);
                                        }
                                        rows.add(row);
                                    }
                                }
                                Object[][] data = rows.toArray(new Object[0][]);
                                queryIndex++;
                                int tabIndex = queryIndex;
                                int finalTotalCount = totalCount;
                                SwingUtilities.invokeLater(() -> {
                                    addResultTab("结果 " + tabIndex + " (" + finalTotalCount + ")", data, columnNames);
                                });
                                logResult("[QUERY] " + GlobalUtil.truncateSql(sql) + " | 共 " + totalCount + " 条，显示前 " + displayCount + " 条");
                                success++;
                            }
                        } else {
                            int updateCount = stmt.getUpdateCount();
                            success++;
                            logResult(LogUtil.success(
                                    "[OK] " + GlobalUtil.truncateSql(sql) + (updateCount >= 0 ? " | 影响 " + updateCount + " 行" : "")));
                        }
                    } catch (SQLException ex) {
                        failed++;
                        logResult(LogUtil.failed("[FAILED] " + ex.getMessage() + " | SQL: " + GlobalUtil.truncateSql(sql)));
                    }
                }
                logResult(LogUtil.success("执行完成: 成功 " + success + " 条, 失败 " + failed + " 条"));
            } catch (Exception ex) {
                logResult(LogUtil.failed("[ERROR] 连接或执行异常: " + ex.getMessage()));
            } finally {
                DbConnector.closeQuietly(conn);
                SwingUtilities.invokeLater(() -> {
                    updateEditState();
                    updateRunButtonState();
                });
            }
        }).start();
    }
    
    /**
     * 向结果区域添加一个 SELECT 查询结果 tab。
     */
    private void addResultTab(String title, Object[][] data, Object[] columnNames) {
        JTable table = new JTable(new DefaultTableModel(data, columnNames));
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        resultTabs.addTab(title, scroll);
        resultTabs.setSelectedIndex(resultTabs.getTabCount() - 1);
    }
    
    private void updateEditState() {
        boolean hasSelection = selectedScript != null;
        consoleArea.setEnabled(hasSelection);
        saveMenuItem.setEnabled(hasSelection);
        deleteMenuItem.setEnabled(hasSelection);
        exportMenuItem.setEnabled(hasSelection);
        updateRunButtonState();
    }
    
    private void updateRunButtonState() {
        boolean hasScript = selectedScript != null;
        IconItem dsItem = dataSourceCombo.getSelectedItem();
        boolean hasDataSource = dsItem != null && !dsItem.getText().startsWith("（");
        if (!hasDataSource) {
            runBtn.setEnabled(false);
            return;
        }
        Object dbItem = dbCombo.getSelectedItem();
        boolean hasDb = !dbPanel.isVisible() || (dbItem != null && !dbItem.toString().startsWith("（"));
        Object schemaItem = schemaCombo.getSelectedItem();
        boolean hasSchema = !schemaPanel.isVisible() || (schemaItem != null && !schemaItem.toString().startsWith("（"));
        runBtn.setEnabled(hasScript && hasDb && hasSchema);
    }
    
    private void logResult(String msg) {
        LogUtil.appendLog(msg, LogUtil.SCRIPT_LOG_AREA);
    }
    
    /**
     * 绑定全屏快捷键：Ctrl+s 保存脚本
     */
    private void bindFullscreenShortcuts() {
        KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK, true);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlS, "toggleSaveScript");
        getRootPane().getActionMap().put("toggleSaveScript", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveSelectedScript();
            }
        });
    }
}
