package com.datasync.ui;

import com.datasync.components.CustomTextField;
import com.datasync.components.FullscreenJDialog;
import com.datasync.components.OptionJPanel;
import com.datasync.components.combobox.IconItem;
import com.datasync.components.combobox.IconJComboBox;
import com.datasync.core.DbConnector;
import com.datasync.model.DataSource;
import com.datasync.model.DbType;
import com.datasync.model.Script;
import com.datasync.util.ConfigUtil;
import com.datasync.util.IconUtil;
import com.datasync.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

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
    
    private JTextArea resultArea;
    
    private JTabbedPane resultTabs;
    
    private IconJComboBox dataSourceCombo;
    
    private JPanel dbPanel;
    
    private JComboBox<String> dbCombo;
    
    private JPanel schemaPanel;
    
    private JComboBox<String> schemaCombo;
    
    private JMenuItem saveMenuItem;
    
    private JMenuItem deleteMenuItem;
    
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
            resultArea.setText("");
            resultTabs.removeAll();
        });
        JMenuItem fullscreenItem = new JMenuItem("全屏（F11）/ 退出全屏（Esc）");
        fullscreenItem.addActionListener(e -> toggleFullscreen());
        moreMenu.add(saveMenuItem);
        moreMenu.add(deleteMenuItem);
        moreMenu.add(exportMenuItem);
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
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("运行日志"));
        resultScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        resultScroll.setPreferredSize(new Dimension(0, 80));
        JSplitPane resultPanelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultTabs, resultScroll);
        resultPanelSplitPane.setResizeWeight(1);
        attachResultAreaPopupMenu(consoleArea, "清空控制台", true);
        attachResultAreaPopupMenu(resultTabs, "清空结果", false);
        attachResultAreaPopupMenu(resultArea, "清空日志", false);
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
        JTextField nameInput = new JTextField(script.getScriptName());
        IconJComboBox dbTypeInput = new IconJComboBox();
        dbTypeInput.addItem(DbType.POSTGRESQL_ITEM);
        dbTypeInput.addItem(DbType.MYSQL_ITEM);
        dbTypeInput.setSelectedItem(DbType.getIconItem(script.getDbType()));
        JTextArea remarkInput = new JTextArea(script.getRemark(), 5, 10);
        remarkInput.setWrapStyleWord(true);
        remarkInput.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(remarkInput);
        Object[] message = {"脚本名称:", nameInput, "数据库类型:", dbTypeInput, "备注:", scrollPane};
        int option = JOptionPane.showConfirmDialog(this, message, "编辑脚本", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        
        String newName = nameInput.getText().trim();
        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "脚本名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Script existing = ConfigUtil.loadScriptByName(newName);
        if (existing != null && !existing.getId().equals(script.getId())) {
            JOptionPane.showMessageDialog(this, "已存在同名脚本，请重新命名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        final IconItem selectedItem = dbTypeInput.getSelectedItem();
        assert selectedItem != null;
        final String newDbType = selectedItem.getText().trim();
        final String newRemark = remarkInput.getText().trim();
        boolean changed =
                !newName.equals(script.getScriptName()) || !newDbType.equalsIgnoreCase(script.getDbType()) || !newRemark.equals(script.getRemark());
        if (!changed) {
            return;
        }
        
        script.setScriptName(newName);
        script.setDbType(newDbType);
        script.setRemark(newRemark);
        if (ConfigUtil.updateScript(script)) {
            refreshScriptList();
            for (OptionJPanel p : scriptPanelList) {
                Script s = (Script) p.getData();
                if (s.getId().equals(script.getId())) {
                    selectScript(s, p);
                    break;
                }
            }
            logResult("更新脚本 [" + newName + "] 成功！");
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
        consoleArea.setText(script.getContent());
        consoleArea.setCaretPosition(0);
        updateEditState();
    }
    
    private void refreshDataSourceCombo() {
        String selectedDbType = selectedScript != null ? selectedScript.getDbType() : null;
        String previousSelection = dataSourceCombo.getSelectedItem() != null ? dataSourceCombo.getSelectedItem().getText() : null;
        resetTargetSelectionState();
        dataSourceCombo.removeAllItems();
        dataSourceCombo.addItem(new IconItem(null, "（请选择数据源）"));
        
        List<String> names = ConfigUtil.loadAllSourceNames();
        boolean hasMatch = false;
        for (String name : names) {
            DataSource ds = ConfigUtil.loadDataSourceByName(name);
            if (ds != null && selectedDbType != null && selectedDbType.equalsIgnoreCase(ds.getDbType())) {
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
    
    private void createNewScript() {
        JTextField nameFieldInput = new JTextField();
        IconJComboBox dbTypeInput = new IconJComboBox();
        dbTypeInput.addItem(DbType.POSTGRESQL_ITEM);
        dbTypeInput.addItem(DbType.MYSQL_ITEM);
        JTextArea remarkInput = new JTextArea(5, 10);
        remarkInput.setWrapStyleWord(true);
        remarkInput.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(remarkInput);
        Object[] message = {"脚本名称:", nameFieldInput, "数据库类型:", dbTypeInput, "备注:", scrollPane};
        int option = JOptionPane.showConfirmDialog(this, message, "新建脚本", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        
        String name = nameFieldInput.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "脚本名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (ConfigUtil.loadScriptByName(name) != null) {
            JOptionPane.showMessageDialog(this, "已存在同名脚本，请重新命名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        IconItem dbType = dbTypeInput.getSelectedItem();
        assert dbType != null;
        Script script = new Script(name, dbType.getText(), "");
        script.setRemark(remarkInput.getText().trim());
        if (!ConfigUtil.saveScript(script)) {
            JOptionPane.showMessageDialog(this, "新建脚本失败", "错误", JOptionPane.ERROR_MESSAGE);
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
        logResult("新建脚本 [" + name + "] 成功");
    }
    
    private void saveSelectedScript() {
        if (selectedScript == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个脚本", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        selectedScript.setContent(consoleArea.getText());
        if (ConfigUtil.updateScript(selectedScript)) {
            logResult("保存脚本 [" + selectedScript.getScriptName() + "] 成功");
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
            logResult("删除脚本成功");
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
            logResult("导出脚本成功: " + file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "导出成功：\n" + file.getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
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
        
        String scriptDbType = selectedScript.getDbType();
        if (!scriptDbType.equalsIgnoreCase(dataSource.getDbType())) {
            JOptionPane.showMessageDialog(this, "脚本数据库类型 [" + scriptDbType + "] 与数据源类型 [" + dataSource.getDbType() + "] 不一致",
                    "类型不匹配", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        saveMenuItem.setEnabled(false);
        deleteMenuItem.setEnabled(false);
        runBtn.setEnabled(false);
        String runInfo = "数据源 [" + dataSource.getSourceName() + "] 数据库 [" + dataSource.getDbName() + "]";
        if (dataSource.isPostgresql() && schemaItem != null && !schemaItem.toString().startsWith("（")) {
            runInfo += " Schema [" + schemaItem.toString() + "]";
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
                List<String> statements = splitSqlStatements(content);
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
                                    if (displayCount < 5000) {
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
                                logResult("[QUERY] " + truncateSql(sql) + " | 共 " + totalCount + " 条，显示前 " + displayCount + " 条");
                                success++;
                            }
                        } else {
                            int updateCount = stmt.getUpdateCount();
                            success++;
                            logResult("[OK] " + truncateSql(sql) + (updateCount >= 0 ? " | 影响 " + updateCount + " 行" : ""));
                        }
                    } catch (SQLException ex) {
                        failed++;
                        logResult("[FAILED] " + ex.getMessage() + " | SQL: " + truncateSql(sql));
                    }
                }
                logResult("执行完成: 成功 " + success + " 条, 失败 " + failed + " 条");
            } catch (Exception ex) {
                logResult("[ERROR] 连接或执行异常: " + ex.getMessage());
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
        SwingUtilities.invokeLater(() -> {
            resultArea.append(
                    "[" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + msg + "\n");
            resultArea.setCaretPosition(resultArea.getDocument().getLength());
        });
    }
    
    /**
     * 拆分 SQL 脚本为独立语句。 支持跳过 -- 行注释、
     */
    private List<String> splitSqlStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';
            
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
                continue;
            }
            
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String sql = current.toString().trim();
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
                current = new StringBuilder();
                continue;
            }
            
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }
    
    private String truncateSql(String sql) {
        if (sql == null) {
            return "";
        }
        String s = sql.replaceAll("\\s+", " ").trim();
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
    
    /**
     * 绑定全屏快捷键：F11 切换全屏，Esc 退出全屏。
     */
    private void bindFullscreenShortcuts() {
        //        KeyStroke f11 = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0);
        //        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(f11, "toggleFullscreen");
        //        getRootPane().getActionMap().put("toggleFullscreen", new javax.swing.AbstractAction() {
        //            @Override
        //            public void actionPerformed(java.awt.event.ActionEvent e) {
        //                toggleFullscreen();
        //            }
        //        });
        //
        //        KeyStroke esc = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        //        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "exitFullscreen");
        //        getRootPane().getActionMap().put("exitFullscreen", new javax.swing.AbstractAction() {
        //            @Override
        //            public void actionPerformed(java.awt.event.ActionEvent e) {
        //                if (fullscreen) {
        //                    toggleFullscreen();
        //                }
        //            }
        //        });
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
