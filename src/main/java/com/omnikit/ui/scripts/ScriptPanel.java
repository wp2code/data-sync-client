package com.omnikit.ui.scripts;

import com.mysql.cj.util.StringUtils;
import com.omnikit.components.AbsJPanl;
import com.omnikit.components.ButtonFactory;
import com.omnikit.components.CustomTextField;
import com.omnikit.components.FilterComboBox;
import com.omnikit.components.GroupDragSupport;
import com.omnikit.components.OptionJPanel;
import com.omnikit.components.combobox.IconItem;
import com.omnikit.components.combobox.IconJComboBox;
import com.omnikit.core.DbConnector;
import com.omnikit.core.GitLabService;
import com.omnikit.core.OmniKitService;
import com.omnikit.model.DataSource;
import com.omnikit.model.DbType;
import com.omnikit.model.FileParams;
import com.omnikit.model.ProjectItem;
import com.omnikit.model.Script;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.ConfigUtil;
import com.omnikit.util.GlobalUtil;
import com.omnikit.util.IconUtil;
import com.omnikit.util.LogUtil;
import com.omnikit.util.SQLiteConfigUtil;
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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.RepositoryFileResponse;

/**
 * 脚本管理面板
 * <p>
 * 支持：新建脚本、选中脚本在控制台预览、编辑保存、删除脚本、在目标库执行脚本。
 *
 * @author liuweiping
 * @date 2026-07-03
 **/
public class ScriptPanel extends AbsJPanl {
    
    public static final String NAME = "script";
    
    /**
     * 搜索区卡片名：搜索框
     */
    private static final String CARD_SEARCH = "search";
    
    /**
     * 搜索区卡片名：「移出分组」投放提示条（拖拽期间替换搜索框显示）
     */
    private static final String CARD_UNGROUP_HINT = "ungroupHint";
    
    /**
     * 脚本类型下拉文案：SQL 脚本（可绑定数据库类型并在目标库执行）
     */
    private static final String TYPE_LABEL_SQL = "SQL 脚本";
    
    /**
     * 脚本类型下拉文案：普通文件（仅编辑保存与 GitLab 同步，不连接数据库运行）
     */
    private static final String TYPE_LABEL_FILE = "普通文件";
    
    private final List<OptionJPanel> scriptPanelList = new ArrayList<>();
    
    /**
     * 脚本列表分组（按分组名聚合，组内保持配置加载顺序）
     */
    private final List<ScriptGroup> scriptGroups = new ArrayList<>();
    
    /**
     * 已折叠的分组名：仅浏览态生效，搜索时分组自动展开（不修改记忆状态）
     */
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    
    private JPanel scriptListPanel;
    
    /**
     * 列表滚动面板：分组增减后需连同它一起重算滚动范围，否则内容变高时滚动范围偏小，最后一项会被裁掉
     */
    private JScrollPane listScrollPane;
    
    private CustomTextField searchField;
    
    /**
     * 搜索区卡片容器：搜索框与「移出分组」提示条同区域切换，拖拽时不会改变列表可用高度
     */
    private JPanel searchCard;
    
    private CardLayout searchCardLayout;
    
    /**
     * 「移出分组」投放提示条：拖拽期间在同一区域替换搜索框显示，拖到此处即取消分组
     */
    private JLabel ungroupHint;
    
    /**
     * 当前列表中是否存在已分组脚本：全部未分组时移出分组无意义，拖拽期间不展示提示条
     */
    private boolean hasGroupedScripts;
    
    private JTextArea consoleArea;
    
    //    private JTextArea resultArea;
    
    private JTabbedPane resultTabs;
    
    /**
     * 编辑区（SQL 脚本为控制台，普通文件为文件内容）：普通文件不展示下方的运行结果与运行日志
     */
    private JPanel consolePanel;
    
    /**
     * 右侧上下分割面板：普通文件时把下方结果区整体移出，编辑区占满
     */
    private JSplitPane rightSplitPane;
    
    /**
     * 运行结果 + 运行日志分割面板：仅 SQL 脚本展示
     */
    private JSplitPane resultPanelSplitPane;
    
    private IconJComboBox dataSourceCombo;
    
    /**
     * 数据源选择区：普通文件脚本无需连接数据库，整块隐藏
     */
    private JPanel dataSourcePanel;
    
    /**
     * 编辑区工具栏：SQL 脚本放数据源/数据库/Schema 与运行、更多按钮；普通文件只保留更多按钮靠最右
     */
    private JPanel consoleToolbar;
    
    /**
     * 工具栏右侧按钮区（运行 + 更多操作）
     */
    private JPanel consoleBtnPanel;
    
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
    
    public ScriptPanel() {
        SQLiteConfigUtil.getInstance().initialize();
        initUI();
        refreshScriptList();
        bindSaveShortcut();
        // 避免搜索框默认获得焦点，将焦点移到脚本列表
        SwingUtilities.invokeLater(() -> scriptListPanel.requestFocusInWindow());
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    /**
     * 切换回本卡片时刷新：其他页面（如数据同步保存表 DDL 脚本）可能新增了脚本， 重新加载列表并保持当前选中项与未保存的编辑内容
     */
    @Override
    public void refresh() {
        if (consoleArea == null) {
            return;
        }
        Script current = selectedScript;
        String editingContent = current != null ? consoleArea.getText() : null;
        int caretPosition = current != null ? consoleArea.getCaretPosition() : 0;
        refreshScriptList();
        if (current == null) {
            return;
        }
        for (OptionJPanel panel : scriptPanelList) {
            Script reloaded = (Script) panel.getData();
            if (reloaded.getId() != null && reloaded.getId().equals(current.getId())) {
                selectScript(reloaded, panel);
                // 还原未保存的编辑内容与光标位置，避免刷新列表时丢失正在编辑的文本
                if (editingContent != null) {
                    consoleArea.setText(editingContent);
                    consoleArea.setCaretPosition(Math.min(caretPosition, editingContent.length()));
                }
                final OptionJPanel matched = panel;
                SwingUtilities.invokeLater(() -> {
                    listScrollPane.validate();
                    scriptListPanel.scrollRectToVisible(matched.getBounds());
                });
                return;
            }
        }
        // 原选中脚本已不在库中（被删除）：清空右侧编辑区
        selectedPanel = null;
        selectedScript = null;
        consoleArea.setText("");
        updateEditState();
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
        searchField.getDocument().addDocumentListener(new DocumentListener() {
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
        // 拖拽期间在同一区域把搜索框切换为「移出分组」提示条：不占额外高度，不会把列表最后一项挤出可视区
        searchCardLayout = new CardLayout();
        searchCard = new JPanel(searchCardLayout);
        searchCard.setOpaque(false);
        searchCard.add(searchField, CARD_SEARCH);
        ungroupHint = createUngroupHint();
        searchCard.add(ungroupHint, CARD_UNGROUP_HINT);
        leftTopPanel.add(searchCard, BorderLayout.CENTER);
        leftPanel.add(leftTopPanel, BorderLayout.NORTH);
        
        // 宽度始终跟随滚动容器（不允许横向滚动），避免横向滚动条占掉视口高度而压住列表最后一项
        scriptListPanel = new ScrollablePanel();
        scriptListPanel.setLayout(new BoxLayout(scriptListPanel, BoxLayout.Y_AXIS));
        scriptListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        scriptListPanel.setBackground(UIManager.getColor("Panel.background"));
        scriptListPanel.setFocusable(true);
        
        listScrollPane = new JScrollPane(scriptListPanel);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        // 列表空白处作为「移出分组」投放区：拖到分组标题与组内项以外的空白区域即取消分组
        GroupDragSupport.enableGroupTarget(scriptListPanel, "", null, listScrollPane);
        GroupDragSupport.enableGroupTarget(listScrollPane.getViewport(), "", null, listScrollPane);
        
        JButton addBtn = ButtonFactory.createPrimary("新建脚本");
        addBtn.addActionListener(e -> createNewScript());
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leftBtnPanel.add(addBtn);
        leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);
        
        // ── 右侧：控制台 + 运行结果 ──
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        
        // 控制台
        consolePanel = new JPanel(new BorderLayout(5, 5));
        consolePanel.setBorder(BorderFactory.createTitledBorder("控制台"));
        
        consoleToolbar = new JPanel(new GridBagLayout());
        consoleToolbar.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // 运行数据源选择
        dataSourcePanel = new JPanel(new BorderLayout(5, 0));
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
        consoleBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        runBtn = ButtonFactory.createPrimary("运行");
        runBtn.addActionListener(e -> runSelectedScript());
        consoleBtnPanel.add(runBtn);
        
        // 更多操作下拉
        JButton moreBtn = ButtonFactory.createToolbar("更多操作 ▼");
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
        moreBtn.addActionListener(e -> moreMenu.show(moreBtn, 0, moreBtn.getHeight()));
        consoleBtnPanel.add(moreBtn);
        
        layoutConsoleToolbar();
        
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
        resultPanelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultTabs, resultScroll);
        resultPanelSplitPane.setResizeWeight(1);
        attachResultAreaPopupMenu(consoleArea, "清空控制台", true);
        attachResultAreaPopupMenu(resultTabs, "清空结果", false);
        attachResultAreaPopupMenu(LogUtil.SCRIPT_LOG_AREA, "清空日志", false);
        rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, consolePanel, resultPanelSplitPane);
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
        scriptGroups.clear();
        
        List<Script> scripts = ConfigUtil.loadAllScripts();
        hasGroupedScripts = false;
        for (Script script : scripts) {
            if (!script.resolveGroupName().isEmpty()) {
                hasGroupedScripts = true;
                break;
            }
        }
        // 列表刷新说明数据已变更（拖拽已结束），统一恢复搜索框，避免提示条残留
        showSearchCard();
        if (scripts.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无脚本）", SwingConstants.CENTER);
            emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            scriptListPanel.add(emptyLabel);
        } else {
            Map<String, List<Script>> grouped = groupScripts(scripts);
            for (Map.Entry<String, List<Script>> entry : grouped.entrySet()) {
                // 未分组（键为空串）不显示分组标题，直接展开在列表末尾
                addScriptGroup(entry.getKey(), entry.getValue(), entry.getKey().isEmpty());
            }
        }
        
        filterScriptList();
        revalidateList();
    }
    
    /**
     * 刷新列表尺寸与滚动范围：分组标题/条目的增减会改变内容高度， 需连同滚动视口一起重算，否则滚动条范围偏小会让最后一项显示不全
     */
    private void revalidateList() {
        scriptListPanel.revalidate();
        if (listScrollPane != null) {
            listScrollPane.revalidate();
        }
        scriptListPanel.repaint();
    }
    
    private void filterScriptList() {
        String keyword = searchField.getText().trim().toLowerCase(Locale.ROOT);
        boolean anyVisible = false;
        for (ScriptGroup group : scriptGroups) {
            int total = group.items.size();
            int visibleCount = 0;
            for (int i = 0; i < total; i++) {
                OptionJPanel panel = group.items.get(i);
                Script script = (Script) panel.getData();
                boolean visible = keyword.isEmpty() || matchesKeyword(script, group.name, keyword);
                panel.setVisible(visible);
                // 同时控制分隔间距的可见性
                group.spacers.get(i).setVisible(visible);
                if (visible) {
                    visibleCount++;
                }
            }
            boolean groupVisible = visibleCount > 0;
            // 搜索时自动展开命中的分组，折叠状态仅在无关键字浏览时生效
            boolean collapsed = keyword.isEmpty() && collapsedGroups.contains(group.name);
            group.header.setVisible(groupVisible && !group.flat);
            group.body.setVisible(groupVisible && !collapsed);
            group.arrowLabel.setIcon(IconUtil.getGroupArrowIcon(!collapsed));
            // 有关键字时展示命中数/总数，便于判断分组内是否还有隐藏项
            group.countLabel.setText(visibleCount == total ? String.valueOf(total) : visibleCount + "/" + total);
            if (groupVisible) {
                anyVisible = true;
            }
        }
        
        // 处理空提示
        for (Component comp : scriptListPanel.getComponents()) {
            if (comp instanceof JLabel label && "（暂无脚本）".equals(label.getText())) {
                comp.setVisible(!anyVisible && keyword.isEmpty());
            }
        }
        revalidateList();
    }
    
    /**
     * 按分组名称聚合脚本：组间按名称排序（中文按拼音），未分组的脚本用空串作键固定追加到最后；
     * 组内保持配置加载顺序
     */
    private Map<String, List<Script>> groupScripts(List<Script> scripts) {
        Map<String, List<Script>> sorted = new TreeMap<>(Collator.getInstance(Locale.CHINA));
        List<Script> ungrouped = new ArrayList<>();
        for (Script script : scripts) {
            String groupName = script.resolveGroupName();
            if (groupName.isEmpty()) {
                ungrouped.add(script);
            } else {
                sorted.computeIfAbsent(groupName, key -> new ArrayList<>()).add(script);
            }
        }
        Map<String, List<Script>> grouped = new LinkedHashMap<>(sorted);
        if (!ungrouped.isEmpty()) {
            grouped.put("", ungrouped);
        }
        return grouped;
    }
    
    /**
     * 构建一个分组（可折叠标题 + 组内脚本项）：flat 表示未分组，不加标题、不缩进，脚本项直接平铺展开
     */
    private void addScriptGroup(String groupName, List<Script> scripts, boolean flat) {
        // 箭头图标 + 分组图标 + 分组名
        JLabel arrowLabel = new JLabel(IconUtil.getGroupArrowIcon(true));
        JLabel titleLabel = new JLabel(groupName, IconUtil.getScriptsGroupIcon(), SwingConstants.LEFT);
        titleLabel.setFont(UiConstants.FONT_SANS_12.deriveFont(Font.BOLD));
        titleLabel.setIconTextGap(6);
        JLabel countLabel = new JLabel(String.valueOf(scripts.size()));
        countLabel.setFont(UiConstants.FONT_SANS_11);
        countLabel.setForeground(Color.GRAY);
        
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titlePanel.setOpaque(false);
        titlePanel.add(arrowLabel);
        titlePanel.add(titleLabel);
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(4, 2, 4, 6));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        header.add(titlePanel, BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);
        
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 组内脚本项右缩进，与分组标题形成层级
        body.setBorder(new EmptyBorder(0, flat ? 0 : 10, 0, 0));
        
        ScriptGroup group = new ScriptGroup(groupName, flat, header, arrowLabel, countLabel, body);
        for (Script script : scripts) {
            OptionJPanel panel = createScriptPanel(script);
            Component spacer = Box.createRigidArea(new Dimension(0, 8));
            body.add(panel);
            body.add(spacer);
            scriptPanelList.add(panel);
            group.items.add(panel);
            group.spacers.add(spacer);
        }
        scriptGroups.add(group);
        
        if (flat) {
            // 未分组区：拖到此处即移出分组（大面积铺色反而干扰视线，不高亮，依靠顶部提示条引导）
            GroupDragSupport.enableGroupTarget(body, "", null, listScrollPane);
            scriptListPanel.add(body);
        } else {
            // 拖到分组标题或组内任意位置都加入该分组，统一高亮标题行
            GroupDragSupport.enableGroupTarget(header, groupName, header, listScrollPane);
            GroupDragSupport.enableGroupTarget(body, groupName, header, listScrollPane);
            attachGroupToggle(header, group);
            scriptListPanel.add(header);
            scriptListPanel.add(body);
        }
    }
    
    /**
     * 为分组标题及其子组件绑定折叠/展开点击：子组件命中点击时不冒泡到父组件，需逐个绑定
     */
    private void attachGroupToggle(Component comp, ScriptGroup group) {
        comp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        comp.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleGroup(group);
            }
        });
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachGroupToggle(child, group);
            }
        }
    }
    
    /**
     * 折叠 / 展开分组：折叠状态按分组名记忆，搜索框有内容时分组始终展开且不改变记忆状态
     */
    private void toggleGroup(ScriptGroup group) {
        if (!collapsedGroups.remove(group.name)) {
            collapsedGroups.add(group.name);
        }
        filterScriptList();
    }
    
    /**
     * 关键字匹配：脚本名称或所属分组名称包含关键字（忽略大小写）
     */
    private boolean matchesKeyword(Script script, String groupName, String keyword) {
        return script.getScriptName().toLowerCase(Locale.ROOT).contains(keyword)
                || groupName.toLowerCase(Locale.ROOT).contains(keyword);
    }
    
    private OptionJPanel createScriptPanel(Script script) {
        // 图标：SQL 脚本按数据库类型，普通文件使用普通脚本图标
        javax.swing.Icon icon = script.isSqlScript() ? IconUtil.getDbTypeIcon(script.getDbType())
                : IconUtil.getScriptTypeIcon(Script.TYPE_FILE);
        OptionJPanel panel = new OptionJPanel(script.getScriptName(), script.getRemark(), icon);
        panel.setData(script);
        panel.fitPreferredHeight();
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setOnClick(() -> selectScript(script, panel));
        attachScriptPopupMenu(panel, script, panel);
        // 可拖到分组标题/组内容区加入分组，拖到列表空白区移出分组
        GroupDragSupport.enableDragSource(panel, groupName -> moveScriptToGroup(script, groupName),
                !script.resolveGroupName().isEmpty(), this::onScriptDragStateChanged);
        return panel;
    }
    
    /**
     * 创建「移出分组」投放提示条：虚线框 + 居中提示文字，仅拖拽期间显示
     */
    private JLabel createUngroupHint() {
        JLabel hint = new JLabel("拖到此处移出分组", SwingConstants.CENTER);
        hint.setFont(UiConstants.FONT_SANS_11);
        hint.setForeground(UiConstants.COLOR_NEUTRAL_LIGHT);
        hint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(UiConstants.COLOR_NEUTRAL_LIGHT, 1f, 4f, 3f, true),
                new EmptyBorder(3, 0, 3, 0)));
        hint.setToolTipText("拖到此处取消分组，恢复为未分组");
        GroupDragSupport.enableGroupTarget(hint, "", hint);
        return hint;
    }
    
    /**
     * 拖拽状态回调：拖动已分组脚本时把搜索框切换为「移出分组」提示条，拖拽结束或拖动未分组脚本时恢复搜索框
     */
    private void onScriptDragStateChanged(boolean dragging, boolean grouped) {
        if (searchCardLayout == null) {
            return;
        }
        if (dragging && grouped && hasGroupedScripts) {
            searchCardLayout.show(searchCard, CARD_UNGROUP_HINT);
        } else {
            showSearchCard();
        }
    }
    
    /**
     * 恢复搜索框卡片（拖拽结束或无可移出分组时）
     */
    private void showSearchCard() {
        if (searchCardLayout != null) {
            searchCardLayout.show(searchCard, CARD_SEARCH);
        }
    }
    
    /**
     * 拖拽移动到分组：仅更新分组字段（不刷新更新时间，避免列表重排），成功后刷新列表并保持选中；
     * 拖动当前脚本时保留控制台未保存的编辑内容
     */
    private void moveScriptToGroup(Script script, String groupName) {
        String target = groupName == null ? "" : groupName.trim();
        if (target.equals(script.resolveGroupName())) {
            return;
        }
        String editingContent = script == selectedScript ? consoleArea.getText() : null;
        if (!ConfigUtil.updateScriptGroupName(script.getId(), target)) {
            JOptionPane.showMessageDialog(this, "移动到分组失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 拖入的组若处于折叠状态，自动展开，避免刚拖入的脚本被隐藏
        collapsedGroups.remove(target);
        refreshScriptList();
        for (OptionJPanel panel : scriptPanelList) {
            Script reloaded = (Script) panel.getData();
            if (reloaded.getId().equals(script.getId())) {
                selectScript(reloaded, panel);
                // 列表刚重建、布局尚未完成，延后布局后滚动，保证拖入分组的脚本一定落在可视区内
                SwingUtilities.invokeLater(() -> {
                    listScrollPane.validate();
                    scriptListPanel.scrollRectToVisible(panel.getBounds());
                });
                break;
            }
        }
        if (editingContent != null) {
            consoleArea.setText(editingContent);
        }
        logResult(LogUtil.success("脚本 [" + script.getScriptName() + "] 移动到分组 [" + (target.isEmpty() ? "未分组" : target) + "]"));
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
                            // 复制选中：只复制当前选区，未选中时置灰（全选已包含在下方「一键复制」中）
                            JMenuItem copySelectionItem = new JMenuItem("复制选中");
                            String selectedText = textArea.getSelectedText();
                            copySelectionItem.setEnabled(selectedText != null && !selectedText.isEmpty());
                            copySelectionItem.addActionListener(ee -> textArea.copy());
                            popup.add(copySelectionItem);
                            // 运行选中内容：仅 SQL 脚本且有选中时提供
                            if (selectedScript != null && selectedScript.isSqlScript() && selectedText != null && !selectedText.isBlank()) {
                                JMenuItem runSelectionItem = new JMenuItem("运行选中内容");
                                runSelectionItem.addActionListener(ee -> runSelectedScript());
                                popup.add(runSelectionItem);
                            }
                            popup.addSeparator();
                            JMenuItem copyItem = new JMenuItem("一键复制");
                            copyItem.addActionListener(ee -> {
                                textArea.selectAll();
                                textArea.copy();
                                textArea.setCaretPosition(0);
                                JOptionPane.showMessageDialog(comp.getParent(), "内容已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
                            });
                            popup.add(copyItem);
                            popup.addSeparator();
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
                    GlobalUtil.addMenuItem(popup, "编 辑", () -> editScript(script, panel));
                    GlobalUtil.addMenuItem(popup, "删 除", () -> deleteSelectedScript(script));
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
        if (script != null && script.isSqlScript()) {
            refreshDataSourceCombo();
        } else {
            // 普通文件不连接数据库，清理数据源选择状态
            resetTargetSelectionState();
        }
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
        String previousSelection = dataSourceCombo.getSelectedItem() != null ? dataSourceCombo.getSelectedItem().text() : null;
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
                if (previousSelection.equals(dataSourceCombo.getItemAt(i).text())) {
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
        if (item == null || item.text().startsWith("（")) {
            return;
        }
        DataSource ds = ConfigUtil.loadDataSourceByName(item.text());
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
        if (dsItem == null || dsItem.text().startsWith("（")) {
            schemaPanel.setVisible(false);
            updateRunButtonState();
            return;
        }
        DataSource ds = ConfigUtil.loadDataSourceByName(dsItem.text());
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
    
    /**
     * 表单标签：右对齐，保证冒号纵向成列
     */
    private JLabel formLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setFont(UiConstants.FONT_SANS_12);
        return label;
    }
    
    /**
     * 统一一组标签的宽度（取最宽者），使各输入框左侧起点一致
     */
    private void alignLabelWidth(List<JLabel> labels) {
        int maxWidth = 0;
        for (JLabel label : labels) {
            maxWidth = Math.max(maxWidth, label.getPreferredSize().width);
        }
        Dimension size = new Dimension(maxWidth, 25);
        for (JLabel label : labels) {
            label.setPreferredSize(size);
            label.setMinimumSize(size);
        }
    }
    
    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(label, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }
    
    /**
     * 打开脚本元信息编辑对话框，包含名称、数据库类型、GitLab 配置、项目、分支、文件路径、备注。 若用户点击确认并校验通过，则把值写入传入的 script 对象并返回 true。
     */
    private boolean showScriptMetaDialog(Script script, String title) {
        JTextField nameField = new JTextField(script.getScriptName() != null ? script.getScriptName() : "", 30);
        // 脚本类型：SQL 脚本可绑数据库类型并运行，普通文件仅编辑/保存/同步（带图标下拉）
        IconItem sqlTypeItem = new IconItem(IconUtil.getScriptTypeIcon(Script.TYPE_SQL), TYPE_LABEL_SQL, Script.TYPE_SQL);
        IconItem fileTypeItem = new IconItem(IconUtil.getScriptTypeIcon(Script.TYPE_FILE), TYPE_LABEL_FILE, Script.TYPE_FILE);
        IconJComboBox scriptTypeInput = new IconJComboBox();
        scriptTypeInput.addItem(sqlTypeItem);
        scriptTypeInput.addItem(fileTypeItem);
        // 直接用带图标的候选项作为初始选中项，否则选中项无图标（仅列表项有）
        scriptTypeInput.setSelectedItem(script.isSqlScript() ? sqlTypeItem : fileTypeItem);
        scriptTypeInput.setToolTipText("普通文件仅用于编辑、保存与 GitLab 同步，不连接数据库运行");
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
        projectCombo.setEditable(false);
        branchCombo.setEnabled(false);
        JTextField filePathField = new JTextField(script.getFilePath() != null ? script.getFilePath() : "", 30);
        filePathField.setToolTipText("仓库内文件路径，如 docs/ddl/user.sql；末段带扩展名视为完整文件路径，否则视为目录");
        // 分组：可编辑下拉框，候选项为已有分组，也可直接输入新分组名
        JComboBox<String> groupInput = createGroupComboBox(script.getGroupName());
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
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(10, 12, 5, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        
        JLabel nameLabel = formLabel("脚本名称:");
        JLabel scriptTypeLabel = formLabel("脚本类型:");
        JLabel dbTypeLabel = formLabel("数据库类型:");
        JLabel projectLabel = formLabel("项目:");
        JLabel branchLabel = formLabel("分支:");
        JLabel filePathLabel = formLabel("文件路径:");
        JLabel groupLabel = formLabel("分组:");
        JLabel remarkLabel = formLabel("备注:");
        remarkLabel.setVerticalAlignment(SwingConstants.TOP);
        alignLabelWidth(List.of(nameLabel, scriptTypeLabel, dbTypeLabel, projectLabel, branchLabel, filePathLabel, groupLabel, remarkLabel));
        
        // 表单行按脚本类型重建：数据库类型行仅 SQL 脚本生成（GridBagLayout 不支持隐藏行，只能重建）
        Runnable rebuildFormRows = new Runnable() {
            @Override
            public void run() {
                boolean sqlScript = Script.TYPE_SQL.equals(selectedScriptType(scriptTypeInput));
                formPanel.removeAll();
                int gridy = 0;
                addFormRow(formPanel, gbc, gridy++, nameLabel, nameField);
                addFormRow(formPanel, gbc, gridy++, scriptTypeLabel, scriptTypeInput);
                if (sqlScript) {
                    addFormRow(formPanel, gbc, gridy++, dbTypeLabel, dbTypeInput);
                }
                addFormRow(formPanel, gbc, gridy++, projectLabel, projectCombo);
                addFormRow(formPanel, gbc, gridy++, branchLabel, branchCombo);
                addFormRow(formPanel, gbc, gridy++, filePathLabel, filePathField);
                addFormRow(formPanel, gbc, gridy++, groupLabel, groupInput);
                
                // 备注：允许垂直方向压缩/伸展
                gbc.gridx = 0;
                gbc.gridy = gridy;
                gbc.weightx = 0;
                gbc.weighty = 1;
                gbc.fill = GridBagConstraints.NONE;
                gbc.anchor = GridBagConstraints.NORTHEAST;
                formPanel.add(remarkLabel, gbc);
                
                gbc.gridx = 1;
                gbc.weightx = 1;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.anchor = GridBagConstraints.WEST;
                formPanel.add(remarkScroll, gbc);
                
                formPanel.revalidate();
                formPanel.repaint();
                Window window = SwingUtilities.getWindowAncestor(formPanel);
                if (window != null) {
                    // 行数变化后按新的首选尺寸重新适配对话框
                    window.pack();
                }
            }
        };
        rebuildFormRows.run();
        scriptTypeInput.addActionListener(e -> rebuildFormRows.run());
        
        int option = JOptionPane.showConfirmDialog(this, formPanel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
        boolean sqlScript = Script.TYPE_SQL.equals(selectedScriptType(scriptTypeInput));
        script.setScriptName(newName);
        script.setScriptType(sqlScript ? Script.TYPE_SQL : Script.TYPE_FILE);
        script.setGitLabConfigId(selectedProject != null ? selectedProject.getConfigId() : null);
        script.setDbType(selectedDbType != null ? DbType.fromString(selectedDbType.text()) : DbType.MYSQL);
        script.setProjectOrId(selectedProject != null && selectedProject.getId() != null ? selectedProject.getProjectOrId() : null);
        script.setBranch(selectedBranch != null && !selectedBranch.startsWith("（") ? selectedBranch : null);
        final String filePath = filePathField.getText().trim();
        if (sqlScript && !StringUtils.isNullOrEmpty(filePath) && !filePath.toLowerCase(Locale.ROOT).endsWith(".sql")) {
            JOptionPane.showMessageDialog(this, "SQL 脚本的文件必须是.sql格式", "错误", JOptionPane.ERROR_MESSAGE);
            return true;
        }
        script.setFilePath(filePathField.getText().trim());
        script.setGroupName(groupComboText(groupInput));
        script.setRemark(remarkArea.getText().trim());
        return false;
    }
    
    /**
     * 读取脚本类型下拉当前选中值（候选项的 IconItem.userData 存 {@link Script#TYPE_SQL} / {@link Script#TYPE_FILE}）， 未选中时按 SQL 脚本处理
     */
    private String selectedScriptType(IconJComboBox comboBox) {
        IconItem item = comboBox.getSelectedItem();
        return item == null || item.userData() == null ? Script.TYPE_SQL : item.userData().toString();
    }
    
    /**
     * 创建分组下拉框：可编辑，候选项为已有脚本分组（去重后按拼音排序），也可直接输入新分组名
     */
    private JComboBox<String> createGroupComboBox(String currentGroup) {
        Set<String> names = new TreeSet<>(Collator.getInstance(Locale.CHINA));
        for (Script script : ConfigUtil.loadAllScripts()) {
            String groupName = script.resolveGroupName();
            if (!groupName.isEmpty()) {
                names.add(groupName);
            }
        }
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        comboBox.setToolTipText("可选：下拉选择已有分组，也可直接输入新分组名；列表支持拖拽加入或移除分组");
        for (String name : names) {
            comboBox.addItem(name);
        }
        // 可编辑下拉框的取值来自编辑器，初始化时直接写入编辑器
        comboBox.getEditor().setItem(currentGroup == null ? "" : currentGroup);
        return comboBox;
    }
    
    /**
     * 读取分组下拉框当前文本（取编辑器内容而非选中项，支持输入新分组名），去首尾空白
     */
    private String groupComboText(JComboBox<String> comboBox) {
        Object item = comboBox.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }
    
    private void loadProjectsForConfig(FilterComboBox<ProjectItem> projectCombo, FilterComboBox<String> branchCombo, String preselectProjectOrId) {
        projectCombo.clearAllItems();
        String msg = GitLabService.getInstance().isLogin() ? "（查询中...）" : "(未登录GitLabl)";
        projectCombo.addItem(new ProjectItem(null, null, msg));
        projectCombo.setEnabled(false);
        branchCombo.clearAllItems();
        branchCombo.setEnabled(false);
        if (!GitLabService.getInstance().isLogin()) {
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
        int confirm = JOptionPane.showConfirmDialog(this, "确定同步覆盖当前脚本吗？", "确认同步", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        new Thread(() -> {
            try {
                RepositoryFile file = GitLabService.getInstance().getFile(selectedScript.getProjectOrId(),
                        GlobalUtil.getFullFilePath(selectedScript.getFilePath(), selectedScript.resolveFileName()),
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
        int confirm = JOptionPane.showConfirmDialog(this, "确定上传替换线上脚本吗？", "确认上传", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
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
                fileParams.setFileName(selectedScript.resolveFileName());
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
        boolean sqlScript = selectedScript.isSqlScript();
        chooser.setDialogTitle(sqlScript ? "导出脚本为 SQL 文件" : "导出文件");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (sqlScript) {
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQL 文件 (*.sql)", "sql"));
        }
        chooser.setSelectedFile(new File(selectedScript.resolveFileName()));
        
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = chooser.getSelectedFile();
        // SQL 脚本统一以 .sql 结尾；普通文件保留输入的文件名，不强制扩展名
        if (sqlScript && !file.getName().toLowerCase(Locale.ROOT).endsWith(".sql")) {
            file = new File(file.getParentFile(), file.getName() + ".sql");
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
        
        if (!selectedScript.isSqlScript()) {
            JOptionPane.showMessageDialog(this, "普通文件脚本不支持运行，仅可编辑保存", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 有选中文本时只运行选中部分，否则运行全部内容
        String selectedText = consoleArea.getSelectedText();
        boolean runSelection = selectedText != null && !selectedText.isBlank();
        String content = (runSelection ? selectedText : consoleArea.getText()).trim();
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(this, runSelection ? "选中的内容为空" : "脚本内容为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        IconItem dsItem = dataSourceCombo.getSelectedItem();
        if (dsItem == null || dsItem.text().startsWith("（")) {
            JOptionPane.showMessageDialog(this, "请先选择运行数据源", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        DataSource dataSource = ConfigUtil.loadDataSourceByName(dsItem.text());
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
        logResult("开始执行脚本 [" + selectedScript.getScriptName() + "] " + runInfo + (runSelection ? "（仅执行选中内容）" : "") + "...");
        
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
                                    if (displayCount < OmniKitService.LIMIT_QUERY_COUNT) {
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
        updateScriptTypeState();
        updateRunButtonState();
    }
    
    /**
     * 按脚本类型切换右侧区域：SQL 脚本展示数据源选择与运行结果/运行日志； 普通文件只在编辑区编辑内容，不需要运行、结果与日志
     */
    private void updateScriptTypeState() {
        boolean sqlScript = selectedScript == null || selectedScript.isSqlScript();
        dataSourcePanel.setVisible(sqlScript);
        runBtn.setVisible(sqlScript);
        if (!sqlScript) {
            dbPanel.setVisible(false);
            schemaPanel.setVisible(false);
        }
        consolePanel.setBorder(BorderFactory.createTitledBorder(sqlScript ? "控制台" : "文件内容"));
        layoutConsoleToolbar();
        if (rightSplitPane == null) {
            return;
        }
        // 结果区整体移出分割面板（仅 setVisible 仍会占位），再切回时重新挂上并恢复默认分割位
        if (sqlScript) {
            if (rightSplitPane.getBottomComponent() == null) {
                rightSplitPane.setBottomComponent(resultPanelSplitPane);
                SwingUtilities.invokeLater(() -> rightSplitPane.setDividerLocation(0.6));
            }
        } else {
            rightSplitPane.setBottomComponent(null);
        }
    }
    
    /**
     * 按脚本类型重建编辑区工具栏：SQL 脚本为「数据源 + 数据库 + Schema + 按钮」；普通文件只剩操作按钮，独占整行并靠最右
     */
    private void layoutConsoleToolbar() {
        if (consoleToolbar == null || consoleBtnPanel == null) {
            return;
        }
        boolean sqlScript = selectedScript == null || selectedScript.isSqlScript();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        consoleToolbar.removeAll();
        if (sqlScript) {
            gbc.gridx = 0;
            gbc.weightx = 1.0;
            consoleToolbar.add(dataSourcePanel, gbc);
            gbc.gridx = 1;
            consoleToolbar.add(dbPanel, gbc);
            gbc.gridx = 2;
            consoleToolbar.add(schemaPanel, gbc);
            gbc.gridx = 3;
            gbc.weightx = 0.0;
            consoleToolbar.add(consoleBtnPanel, gbc);
        } else {
            // 普通文件：按钮区占满整行，FlowLayout.RIGHT 使「更多操作」贴最右
            gbc.gridx = 0;
            gbc.weightx = 1.0;
            gbc.anchor = GridBagConstraints.EAST;
            consoleToolbar.add(consoleBtnPanel, gbc);
        }
        consoleToolbar.revalidate();
        consoleToolbar.repaint();
    }
    
    private void updateRunButtonState() {
        boolean hasScript = selectedScript != null && selectedScript.isSqlScript();
        IconItem dsItem = dataSourceCombo.getSelectedItem();
        boolean hasDataSource = dsItem != null && !dsItem.text().startsWith("（");
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
     * 绑定快捷键：Ctrl+S 保存脚本
     */
    private void bindSaveShortcut() {
        KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK, true);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlS, "toggleSaveScript");
        getActionMap().put("toggleSaveScript", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveSelectedScript();
            }
        });
    }
    
    /**
     * 脚本列表分组：可折叠标题 + 组内脚本项容器；items 与 spacers 一一对应，供搜索过滤与折叠控制可见性
     */
    private static class ScriptGroup {
        
        private final String name;
        
        /**
         * 平铺模式（未分组）：不显示标题、不缩进
         */
        private final boolean flat;
        
        private final JPanel header;
        
        private final JLabel arrowLabel;
        
        private final JLabel countLabel;
        
        private final JPanel body;
        
        private final List<OptionJPanel> items = new ArrayList<>();
        
        private final List<Component> spacers = new ArrayList<>();
        
        ScriptGroup(String name, boolean flat, JPanel header, JLabel arrowLabel, JLabel countLabel, JPanel body) {
            this.name = name;
            this.flat = flat;
            this.header = header;
            this.arrowLabel = arrowLabel;
            this.countLabel = countLabel;
            this.body = body;
        }
    }
    
    /**
     * 宽度跟随滚动容器的列表面板：禁止横向滚动， 保证列表项内的长文本按实际可用宽度截断显示，且不会因横向滚动条占位而压住最后一项
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
