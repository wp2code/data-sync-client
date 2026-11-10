package com.omnikit.ui.programs;

import com.omnikit.components.AbsJPanl;
import com.omnikit.components.ButtonFactory;
import com.omnikit.components.CustomTextField;
import com.omnikit.components.GroupDragSupport;
import com.omnikit.components.OptionJPanel;
import com.omnikit.components.combobox.IconItem;
import com.omnikit.components.combobox.IconJComboBox;
import com.omnikit.core.HttpServerOptions;
import com.omnikit.core.HttpStaticFileServer;
import com.omnikit.core.ProcessContainerService;
import com.omnikit.model.ProgramConfig;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.ConfigUtil;
import com.omnikit.util.GlobalUtil;
import com.omnikit.util.IconUtil;
import com.omnikit.util.ProgramConfigTransferUtil;
import com.omnikit.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序容器面板
 * <p>
 * 左侧程序列表（状态徽标）+ 右侧配置表单与控制台，支持启动/停止/重启与实时输出查看。 进程会话由 {@link ProcessContainerService} 单例持有：进程继续后台运行， 重新打开可恢复状态徽标与历史日志。
 *
 * @author liuweiping
 * @date 2026-10-19
 **/
public class ProgramContainerPanel extends AbsJPanl {
    
    private static final Logger logger = LoggerFactory.getLogger(ProgramContainerPanel.class);
    
    public static final String NAME = "container";
    
    /**
     * 搜索区卡片名：搜索框
     */
    private static final String CARD_SEARCH = "search";
    
    /**
     * 搜索区卡片名：「移出分组」投放提示条（拖拽期间替换搜索框显示）
     */
    private static final String CARD_UNGROUP_HINT = "ungroupHint";
    
    private static final ProcessContainerService PROCESS_SERVICE = ProcessContainerService.getInstance();
    
    private final List<OptionJPanel> programPanelList = new ArrayList<>();
    
    /**
     * 程序列表分组（按分组名聚合，组内保持配置加载顺序）
     */
    private final List<ProgramGroup> programGroups = new ArrayList<>();
    
    /**
     * 已折叠的分组名：仅浏览态生效，搜索时分组自动展开（不修改记忆状态）
     */
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    
    /**
     * 程序 id → 列表项右侧状态标签（EDT 独占访问）
     */
    private final Map<Long, JLabel> statusLabels = new HashMap<>();
    
    /**
     * 输出行缓冲队列：服务回调线程只入队，Swing Timer 在 EDT 批量取出刷新，避免高频输出卡死 EDT
     */
    private final ConcurrentLinkedQueue<String> pendingOutput = new ConcurrentLinkedQueue<>();
    
    private JPanel programListPanel;
    
    private CustomTextField searchField;
    
    /**
     * 搜索区卡片容器：搜索框与「移出分组」提示条同区域切换，拖拽时不会改变列表可用高度
     */
    private JPanel searchCard;
    
    private CardLayout searchCardLayout;
    
    /**
     * 列表滚动面板：分组增减后需连同它一起重算滚动范围，否则内容变高时滚动范围偏小，最后一项会被裁掉
     */
    private JScrollPane listScrollPane;
    
    /**
     * 「移出分组」投放提示条：拖拽期间在同一区域替换搜索框显示，拖到此处即取消分组
     */
    private JLabel ungroupHint;
    
    /**
     * 当前列表中是否存在已分组程序：全部未分组时移出分组无意义，拖拽期间不展示提示条
     */
    private boolean hasGroupedPrograms;
    
    private JTextField nameField;
    
    /**
     * 分组名称：可编辑下拉框，候选项为已有分组，也可直接输入新分组名
     */
    private JComboBox<String> groupField;
    
    private CustomTextField programPathField;
    
    private CustomTextField argumentsField;
    
    /**
     * JVM 参数（仅 Java 程序显示）：启动时插在 java 之后、-jar/主类之前
     */
    private CustomTextField jvmArgsField;
    
    /**
     * 环境变量：KEY=VALUE，多个用分号分隔，启动时注入子进程环境
     */
    private CustomTextField envVarsField;
    
    private JTextField workDirField;
    
    private JTextField jrePathField;
    
    private JTextField pythonPathField;
    
    private JTextField remarkField;
    
    private JButton browseProgramBtn;
    
    private JButton browseWorkDirBtn;
    
    private JButton browseJreBtn;
    
    private JButton browsePythonBtn;
    
    /**
     * 程序类型下拉框：Java 程序 / Python 程序 / 其他程序（带类型图标）
     */
    private IconJComboBox typeComboBox;
    
    /**
     * 程序配置表单面板：行布局随程序类型动态重建
     */
    private JPanel formPanel;
    
    /**
     * 表单滚动容器：控制台双击全屏时从分割面板移除、恢复时放回
     */
    private JScrollPane formScroll;
    
    private JPanel programRow;
    
    /**
     * 程序类型行：类型下拉框 + 帮助按钮（切换类型后点帮助即可查看该类型的说明与示例）
     */
    private JPanel typeRow;
    
    private JPanel workDirRow;
    
    private JPanel jrePathRow;
    
    private JPanel pythonPathRow;
    
    /**
     * 常用参数标签行：按程序类型重建，点击标签把参数名写入对应输入框（Java 写「JVM 参数」，其它类型写「参数」）
     */
    private JPanel quickArgTagRow;
    
    /**
     * 「启动参数与环境变量」折叠区标题行：独占一行，标题块靠右（最右侧为 ▸/▾ 箭头）
     */
    private JPanel advancedHeader;
    
    /**
     * 折叠区展开箭头：收起 / 展开两种图标
     */
    private JLabel advancedArrow;
    
    /**
     * 折叠区摘要：收起时显示已填写的字段名（如「已填: 参数、环境变量」），展开时隐藏
     */
    private JLabel advancedSummaryLabel;
    
    /**
     * 折叠区是否展开：默认收起，跨程序切换与程序类型切换保持
     */
    private boolean advancedExpanded;
    
    /**
     * 表单行默认间距
     */
    private static final Insets FORM_ROW_INSETS = new Insets(8, 10, 8, 10);
    
    /**
     * 常用参数标签行间距：上间距收小，使标签紧贴上方输入框（Java 为 JVM 参数框，其余类型为参数框）
     */
    private static final Insets FORM_TAG_ROW_INSETS = new Insets(0, 10, 8, 10);
    
    /**
     * Java（Spring Boot）程序常用 JVM 系统属性名：写在「JVM 参数」框，插在 java 之后、-jar/主类之前。 与 --server.port 这类命令行参数相比，-D 系统属性不进 args，严格模式的命令行解析器不会报 UnrecognizedOption
     */
    private static final String ARG_SERVER_PORT = "-Dserver.port=";
    
    private static final String ARG_SPRING_PROFILES = "-Dspring.profiles.active=";
    
    /**
     * 日志级别参数：按 Spring Boot 约定写根日志级别，可自行改成 -Dlogging.level.<包名>=
     */
    private static final String ARG_LOGGING_LEVEL = "-Dlogging.level.root=";
    
    private JPanel formBtnPanel;
    
    /**
     * 程序类型下拉标签与持久化值（下标一一对应）
     */
    private static final String[] TYPE_LABELS = {"Java 程序", "Python 程序", "HTTP 服务", "其他程序"};
    
    private static final String[] TYPE_VALUES = {ProgramConfig.TYPE_JAVA, ProgramConfig.TYPE_PYTHON, ProgramConfig.TYPE_HTTP_SERVER,
            ProgramConfig.TYPE_OTHER};
    
    /**
     * 程序性设置下拉选中项时，抑制触发表单重建的回调
     */
    private boolean suppressTypeAction;
    
    private JButton saveBtn;
    
    private JButton deleteBtn;
    
    /**
     * 导出按钮：仅选中程序时可用（导出该程序配置）
     */
    private JButton exportBtn;
    
    /**
     * 导入按钮：仅「新增程序」状态可用（把文件中的配置载入表单）
     */
    private JButton importBtn;
    
    /**
     * 重置按钮：放弃未保存的修改（已选程序恢复为保存时的配置，新增程序清空表单）
     */
    private JButton resetBtn;
    
    private JButton startBtn;
    
    private JButton stopBtn;
    
    private JButton restartBtn;
    
    private JButton clearBtn;
    
    /**
     * 内置 HTTP 服务的浏览器访问按钮（仅 HTTP 服务运行中可用）
     */
    private JButton openBtn;
    
    /**
     * 全屏日志按钮：在独立窗口中查看日志，内容与控制台实时同步
     */
    private JButton fullscreenLogBtn;
    
    private JLabel statusLabel;
    
    private JTextArea consoleArea;
    
    /**
     * 全屏日志窗口：同一次只保留一个实例，重复点击时置顶复用
     */
    private ConsoleLogDialog consoleLogDialog;
    
    /**
     * 程序类型帮助弹窗：同一次只保留一个实例，重复点击时刷新内容并置顶，不写入控制台与全屏日志
     */
    private ProgramHelpDialog helpDialog;
    
    /**
     * 右侧表单/控制台垂直分割：分割条按表单首选高度自适应定位（配置区完整展示不滚动，控制台占剩余空间）
     */
    private JSplitPane rightSplitPane;
    
    /**
     * 控制台最小保留高度：窗口过矮时表单退化为滚动展示，保证控制台始终可用
     */
    private static final int MIN_CONSOLE_HEIGHT = 160;
    
    private ProgramConfig selectedConfig;
    
    private OptionJPanel selectedPanel;
    
    /**
     * 当前已向服务注册回调的程序 id（切换选中项时先卸载旧回调）
     */
    private Long attachedId;
    
    /**
     * 控制台批量刷新定时器（200ms）
     */
    private final Timer flushTimer;
    
    public ProgramContainerPanel() {
        SQLiteConfigUtil.getInstance().initialize();
        initUI();
        refreshProgramList();
        bindSaveShortcut();
        // 布局完成后按表单实际高度定位分割条：配置区完整展示，不出滚动条
        SwingUtilities.invokeLater(this::fitFormDivider);
        flushTimer = new Timer(200, e -> flushPendingOutput());
        flushTimer.start();
    }
    
    @Override
    public void destroy() {
        if (flushTimer != null && flushTimer.isRunning()) {
            flushTimer.stop();
        }
    }
    
    @Override
    public void refresh() {
        if (flushTimer != null && !flushTimer.isRunning()) {
            flushTimer.start();
        }
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        
        // ── 左侧面板：程序列表 ──
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("程序列表"));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        
        JPanel leftTopPanel = new JPanel(new BorderLayout(5, 0));
        leftTopPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        searchField = new CustomTextField("输入关键字搜索");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterProgramList();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterProgramList();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterProgramList();
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
        
        // 宽度始终跟随滚动容器（不允许横向滚动），由 OptionJPanel 内部按实际宽度截断长文本
        programListPanel = new ScrollablePanel();
        programListPanel.setLayout(new BoxLayout(programListPanel, BoxLayout.Y_AXIS));
        programListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        programListPanel.setBackground(UIManager.getColor("Panel.background"));
        programListPanel.setFocusable(true);
        
        listScrollPane = new JScrollPane(programListPanel);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // 内容宽度跟随视口，明确禁用横向滚动条（横向滚动条会占掉视口高度，压住列表最后一项）
        listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        // 列表空白处作为「移出分组」投放区：拖到分组标题与组内项以外的空白区域即取消分组
        GroupDragSupport.enableGroupTarget(programListPanel, "", null, listScrollPane);
        GroupDragSupport.enableGroupTarget(listScrollPane.getViewport(), "", null, listScrollPane);
        
        JButton addBtn = ButtonFactory.createPrimary("新增程序");
        addBtn.addActionListener(e -> createNewProgram());
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leftBtnPanel.add(addBtn);
        leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);
        
        // ── 右侧：配置表单 + 控制台 ──
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createFormPanel(), createConsolePanel());
        // 拉伸/收缩时额外空间全部归控制台，配置区始终保持表单首选高度（不出滚动条）
        rightSplitPane.setResizeWeight(0.0);
        // 窗口尺寸变化时重新按表单高度定位分割条，避免配置区被裁切出现滚动条
        rightSplitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitFormDivider();
            }
        });
        rightPanel.add(rightSplitPane, BorderLayout.CENTER);
        
        JSplitPane rootSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        rootSplit.setDividerLocation(270);
        rootSplit.setResizeWeight(0.25);
        
        add(rootSplit, BorderLayout.CENTER);
        updateActionState();
    }
    
    // ────────── 配置表单 ──────────
    
    private JComponent createFormPanel() {
        formPanel = new JPanel(new GridBagLayout());
        createFormFields();
        rebuildFormPanel();
        // 表单置于可滚动容器：仅作为窗口过矮时的兜底（分割条正常按表单首选高度定位，不出现滚动条）
        JScrollPane scroll = new JScrollPane(formPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("程序配置"));
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll = scroll;
        return scroll;
    }
    
    /**
     * 按表单首选高度定位分割条：配置区完整展示不出现滚动条，剩余高度全部给控制台。 窗口过矮时以控制台最小高度兜底，配置区退化为滚动展示。 控制台双击全屏状态或尚未完成布局时跳过
     */
    private void fitFormDivider() {
        if (consoleFullscreen || formScroll == null || rightSplitPane.getHeight() <= 0) {
            return;
        }
        int formHeight = formScroll.getPreferredSize().height;
        int maxFormHeight = rightSplitPane.getHeight() - rightSplitPane.getDividerSize() - MIN_CONSOLE_HEIGHT;
        rightSplitPane.setDividerLocation(Math.min(formHeight, maxFormHeight));
    }
    
    /**
     * 创建表单字段与行组件（仅初始化，不做布局）；行布局由 {@link #rebuildFormPanel()} 按程序类型动态生成
     */
    private void createFormFields() {
        typeComboBox = new IconJComboBox();
        for (int i = 0; i < TYPE_LABELS.length; i++) {
            typeComboBox.addItem(new IconItem(IconUtil.getProgramTypeIcon(TYPE_VALUES[i]), TYPE_LABELS[i], TYPE_VALUES[i]));
        }
        typeComboBox.setToolTipText("选择程序类型：决定命令组装方式与下方显示的运行环境字段");
        typeComboBox.addActionListener(e -> {
            if (!suppressTypeAction) {
                rebuildFormPanel();
                updateTypeExamples();
            }
        });
        
        nameField = new JTextField();
        programPathField = new CustomTextField("如：java -jar D:\\app\\ebx-pawn.jar");
        argumentsField = new CustomTextField("如：--server.port=8080 --spring.profiles.active=prod");
        jvmArgsField = new CustomTextField("如：-Xms512m -Xmx1024m -Dserver.port=8080");
        jvmArgsField.setToolTipText("可选，仅 Java 程序：JVM 参数插在 java 之后、-jar/主类之前。"
                + "程序自带命令行解析时，用 -Dserver.port=8080 这类系统属性代替 --server.port=8080，避免被当作非法选项");
        envVarsField = new CustomTextField("如：SERVER_PORT=8080;LOGGING_LEVEL_ROOT=info");
        envVarsField.setToolTipText("可选，启动时注入子进程环境变量：每项 KEY=VALUE，多个用分号分隔；"
                + "留空则仅继承系统环境。Spring Boot 可用 SERVER_PORT、LOGGING_LEVEL_ROOT 等环境变量名");
        workDirField = new JTextField();
        jrePathField = new JTextField();
        jrePathField.setToolTipText("可选：JDK/JRE 根目录（自动定位 bin/java）或直接指向 java 可执行文件；留空使用系统 PATH。仅对 java/javaw 命令生效");
        pythonPathField = new CustomTextField("如：D:\\proj\\venv 或 C:\\Python311\\python.exe");
        pythonPathField.setToolTipText("可选：Python 安装根目录、虚拟环境目录（自动定位 Scripts/python.exe 或 bin/python）"
                + "或直接指向 python 可执行文件；留空使用系统 PATH。对 python/python3 命令及 .py 入口脚本生效");
        remarkField = new JTextField();
        JTextField[] fields = {nameField, programPathField, argumentsField, jvmArgsField, envVarsField, workDirField, jrePathField,
                pythonPathField, remarkField};
        for (JTextField field : fields) {
            field.setFont(UiConstants.FONT_MONO_12);
        }
        // 分组下拉：字体与高度对齐其他输入框，故在其他字段创建之后初始化
        groupField = createGroupComboBox();
        updateTypeExamples();
        
        // 程序类型行：类型下拉框 + 帮助按钮
        final JButton helpBtn = ButtonFactory.createToolbar("帮助");
        helpBtn.addActionListener(e -> printTypeHelp());
        typeRow = new JPanel(new BorderLayout(5, 0));
        typeRow.add(typeComboBox, BorderLayout.CENTER);
        typeRow.add(helpBtn, BorderLayout.EAST);
        
        // 程序/命令行：输入框 + 浏览按钮
        browseProgramBtn = ButtonFactory.createToolbar("浏览...");
        browseProgramBtn.addActionListener(e -> browseProgramPath());
        programRow = new JPanel(new BorderLayout(5, 0));
        programRow.add(programPathField, BorderLayout.CENTER);
        programRow.add(browseProgramBtn, BorderLayout.EAST);
        
        // 工作目录行：输入框 + 浏览按钮
        browseWorkDirBtn = ButtonFactory.createToolbar("浏览...");
        browseWorkDirBtn.addActionListener(e -> browseWorkDir());
        workDirRow = new JPanel(new BorderLayout(5, 0));
        workDirRow.add(workDirField, BorderLayout.CENTER);
        workDirRow.add(browseWorkDirBtn, BorderLayout.EAST);
        
        // JRE/JDK 行：输入框 + 目录浏览按钮（仅 Java 程序显示）
        browseJreBtn = ButtonFactory.createToolbar("浏览...");
        browseJreBtn.addActionListener(e -> browseJrePath());
        jrePathRow = new JPanel(new BorderLayout(5, 0));
        jrePathRow.add(jrePathField, BorderLayout.CENTER);
        jrePathRow.add(browseJreBtn, BorderLayout.EAST);
        
        // Python 解释器行：输入框 + 浏览按钮（仅 Python 程序显示）
        browsePythonBtn = ButtonFactory.createToolbar("浏览...");
        browsePythonBtn.addActionListener(e -> browsePythonPath());
        pythonPathRow = new JPanel(new BorderLayout(5, 0));
        pythonPathRow.add(pythonPathField, BorderLayout.CENTER);
        pythonPathRow.add(browsePythonBtn, BorderLayout.EAST);
        
        // 折叠区标题：JVM 参数 / 参数 / 环境变量 收进同一区块，标题块右对齐（最右侧为 ▸/▾ 箭头），默认收起
        advancedArrow = new JLabel(IconUtil.getGroupArrowIcon(false));
        JLabel advancedTitle = new JLabel("启动参数与环境变量");
        advancedTitle.setFont(UiConstants.FONT_SANS_12.deriveFont(Font.BOLD));
        advancedTitle.setIconTextGap(6);
        advancedSummaryLabel = new JLabel();
        advancedSummaryLabel.setFont(UiConstants.FONT_SANS_11);
        advancedSummaryLabel.setForeground(Color.GRAY);
        JPanel advancedTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        advancedTitlePanel.setOpaque(false);
        // 顺序固定为「摘要 → 标题 → 箭头」：标题与箭头靠右固定，摘要变长时向左伸展，标题不会左右跳动
        advancedTitlePanel.add(advancedSummaryLabel);
        advancedTitlePanel.add(advancedTitle);
        advancedTitlePanel.add(advancedArrow);
        advancedHeader = new JPanel(new BorderLayout(4, 0));
        advancedHeader.setOpaque(false);
        advancedHeader.setBorder(new EmptyBorder(2, 0, 2, 0));
        advancedHeader.add(advancedTitlePanel, BorderLayout.EAST);
        // 只响应右侧标题块的点击：左侧留白不触发折叠，避免误操作
        attachAdvancedToggle(advancedTitlePanel);
        
        // 按钮行：导入 / 导出 + 删除 + 保存（导入导出针对当前编辑的程序，与选中状态联动）
        importBtn = ButtonFactory.createToolbar("导入");
        importBtn.setToolTipText("新增程序时把 JSON 文件中的程序配置载入表单，确认后点保存");
        importBtn.addActionListener(e -> importProgramConfig());
        exportBtn = ButtonFactory.createToolbar("导出");
        exportBtn.setToolTipText("把选中程序的配置导出为 JSON 文件，便于在其他机器导入");
        exportBtn.addActionListener(e -> exportProgramConfig());
        resetBtn = ButtonFactory.createToolbar("重置");
        resetBtn.setToolTipText("放弃未保存的修改：已选程序恢复为保存时的配置，新增程序清空表单");
        resetBtn.addActionListener(e -> resetForm());
        saveBtn = ButtonFactory.createPrimary("保存");
        saveBtn.addActionListener(e -> saveCurrentForm());
        deleteBtn = ButtonFactory.createDestructive("删除");
        deleteBtn.addActionListener(e -> {
            if (selectedConfig != null) {
                deleteProgram(selectedConfig);
            }
        });
        formBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        formBtnPanel.add(importBtn);
        formBtnPanel.add(exportBtn);
        formBtnPanel.add(resetBtn);
        formBtnPanel.add(deleteBtn);
        formBtnPanel.add(saveBtn);
    }
    
    /**
     * 创建分组下拉框：可编辑，候选项为已有分组（随列表刷新同步），也可直接输入新分组名
     */
    private JComboBox<String> createGroupComboBox() {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        comboBox.setToolTipText("可选：下拉选择已有分组，也可直接输入新分组名；列表支持拖拽加入或移除分组");
        comboBox.setFont(UiConstants.FONT_MONO_12);
        if (comboBox.getEditor().getEditorComponent() instanceof JComponent editor) {
            editor.setFont(UiConstants.FONT_MONO_12);
        }
        // 高度与同排文本输入框对齐，避免表单行高参差
        comboBox.setPreferredSize(new Dimension(120, nameField.getPreferredSize().height));
        return comboBox;
    }
    
    /**
     * 组装「常用参数标签」的表格行：按类型重建标签，左侧留空占位使标签与输入框左边缘对齐
     */
    private JComponent[] createArgTagRowEntry(String typeValue) {
        quickArgTagRow = createArgTagRow(typeValue);
        return new JComponent[] {new JLabel(), quickArgTagRow};
    }
    
    /**
     * 创建常用参数标签行：按程序类型列出常用参数，标签展示中文名，点击把对应参数名写入参数输入框，取值直接在输入框补全。 Java 的常用参数是 -D 系统属性，写入「JVM 参数」框；其它类型写入「参数」框
     */
    private JPanel createArgTagRow(String typeValue) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        // 每项为「标签文字, 写入参数框的参数名, 提示说明」
        String[][] tags = switch (typeValue) {
            case ProgramConfig.TYPE_JAVA -> new String[][] {
                    {"端口", ARG_SERVER_PORT, "点击写入 " + ARG_SERVER_PORT + "，再在「JVM 参数」框补全端口号"},
                    {"配置环境", ARG_SPRING_PROFILES, "点击写入 " + ARG_SPRING_PROFILES + "，再在「JVM 参数」框补全环境名"},
                    {"日志级别", ARG_LOGGING_LEVEL,
                            "点击写入 " + ARG_LOGGING_LEVEL + "，再在「JVM 参数」框补全日志级别（TRACE/DEBUG/INFO/WARN/ERROR）"}};
            case ProgramConfig.TYPE_PYTHON -> new String[][] {
                    {"主机", "--host", "点击写入 --host（空格分隔），再在「参数」框补全监听地址"},
                    {"端口", "--port", "点击写入 --port（空格分隔），再在「参数」框补全端口号"},
                    {"热重载", "--reload", "点击写入 --reload，开启代码热重载"}};
            case ProgramConfig.TYPE_HTTP_SERVER -> new String[][] {
                    {"端口", "--port", "点击写入 --port（空格分隔），再在「参数」框补全端口号"},
                    {"监听地址", "--address", "点击写入 --address（空格分隔），再在「参数」框补全监听地址"},
                    {"代理", "--proxy", "点击写入 --proxy（空格分隔），再在「参数」框补全代理地址"},
                    {"跨域", "--cors", "点击写入 --cors，开启 CORS"},
                    {"HTTPS", "-S", "点击写入 -S，启用 HTTPS"}};
            default -> new String[][] {
                    {"端口", "--port", "点击写入 --port（空格分隔），再在「参数」框补全端口号"},
                    {"配置文件", "--config", "点击写入 --config（空格分隔），再在「参数」框补全配置文件路径"}};
        };
        // Java 的常用参数是 -D 系统属性，写入「JVM 参数」框；其它类型写入「参数」框
        JTextField targetField = ProgramConfig.TYPE_JAVA.equals(typeValue) ? jvmArgsField : argumentsField;
        for (String[] tag : tags) {
            JButton button = createArgTag(tag[0], tag[1], tag[2], targetField);
            row.add(button);
        }
        return row;
    }
    
    /**
     * 创建一个常用参数标签：小号按钮，点击把对应参数名写入目标输入框
     */
    private JButton createArgTag(String label, String argumentKey, String tooltip, JTextField targetField) {
        // 小号标签：字号与内边距都比普通工具栏按钮小，避免占宽、压窄参数输入框
        JButton tag = ButtonFactory.createToolbar(label);
        tag.setFont(UiConstants.FONT_SANS_11);
        tag.setMargin(new Insets(0, 6, 0, 6));
        tag.setToolTipText(tooltip);
        tag.addActionListener(e -> insertArgumentKey(targetField, argumentKey));
        return tag;
    }
    
    /**
     * 把参数名写入目标输入框：已包含该参数时只在原处定位光标，不再重复追加；未包含时追加参数名（Java 带等号，其余类型带空格分隔），并把光标定位到可补全取值的位置
     */
    private void insertArgumentKey(JTextField targetField, String argumentKey) {
        String current = targetField.getText();
        // 参数名去掉结尾的 '='：写入时可能带等号，但已有参数可能是「--name value」的空格写法
        String optionName = argumentKey.endsWith("=") ? argumentKey.substring(0, argumentKey.length() - 1) : argumentKey;
        // 只认「行首或空白之后」开始的完整参数名（后面紧跟 '='、空白或结尾），避免 -Dserver.port= 里误匹配出 server.port 之类片段
        Matcher matcher = Pattern.compile("(?<!\\S)" + Pattern.quote(optionName) + "(?=\\s|=|$)").matcher(current);
        targetField.requestFocusInWindow();
        if (!matcher.find()) {
            // 非 Java 标签写入「参数名 + 空格」，便于直接在空格后补值；Java 的 -DName= 写法则直接补在等号后
            String key = argumentKey.endsWith("=") ? argumentKey : argumentKey + " ";
            // 已有内容末尾是空白时不再补分隔空格，避免出现连续空格
            String separator = current.isEmpty() || Character.isWhitespace(current.charAt(current.length() - 1)) ? "" : " ";
            String inserted = current + separator + key;
            targetField.setText(inserted);
            targetField.setCaretPosition(inserted.length());
            return;
        }
        targetField.setCaretPosition(findArgumentValueEnd(current, matcher.end()));
    }
    
    /**
     * 定位参数取值的末尾：{@code --name=value} 取到 value 末尾；{@code --name value} 且后面不是另一个以 '-' 开头的参数时取到该值末尾；否则停在参数名之后
     */
    private int findArgumentValueEnd(String text, int optionEnd) {
        int index;
        if (optionEnd < text.length() && text.charAt(optionEnd) == '=') {
            index = optionEnd + 1;
        } else {
            // 空格写法：跳过空白后若已到结尾则停在空白之后方便补值，若已是下一个参数则停在参数名之后
            int valueStart = optionEnd;
            while (valueStart < text.length() && Character.isWhitespace(text.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= text.length()) {
                return valueStart;
            }
            if (text.charAt(valueStart) == '-') {
                return optionEnd;
            }
            index = valueStart;
        }
        while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }
    
    /**
     * 读取分组下拉框当前文本（取编辑器内容而非选中项，支持输入新分组名），去首尾空白
     */
    private String groupFieldText() {
        Object item = groupField.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }
    
    /**
     * 同步分组下拉候选项：取已有程序的分组名（去重后按拼音排序），当前输入内容保持不变
     */
    private void syncGroupOptions(List<ProgramConfig> configs) {
        Set<String> names = new TreeSet<>(Collator.getInstance(Locale.CHINA));
        for (ProgramConfig config : configs) {
            String groupName = config.resolveGroupName();
            if (!groupName.isEmpty()) {
                names.add(groupName);
            }
        }
        // 重建候选项会清空编辑器内容，先备份后回填
        String current = groupFieldText();
        groupField.removeAllItems();
        for (String name : names) {
            groupField.addItem(name);
        }
        groupField.getEditor().setItem(current);
    }
    
    /**
     * 按当前程序类型重建表单行：Java 只显示 JRE/JDK、Python 只显示解释器行， 避免两组环境字段同时堆叠导致布局拥挤；
     * 「JVM 参数」「参数」「环境变量」收进同一折叠区，默认收起，展开状态跨类型/程序切换保持
     */
    private void rebuildFormPanel() {
        formPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        // 行距默认宽松，标签行单独收紧（见下）
        gbc.insets = FORM_ROW_INSETS;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // HTTP 服务：根目录即「程序/命令」、端口即「参数」，无需工作目录与运行环境字段
        boolean httpServer = ProgramConfig.TYPE_HTTP_SERVER.equals(selectedTypeValue());
        List<JComponent[]> rows = new ArrayList<>();
        // 折叠区内的行：展开时才加入布局，收起时仅保留标题行
        List<JComponent[]> advancedRows = new ArrayList<>();
        rows.add(new JComponent[] {formLabel("程序类型:"), typeRow});
        rows.add(new JComponent[] {formLabel("名称:"), nameField});
        if (httpServer) {
            rows.add(new JComponent[] {formLabel("服务目录:"), programRow});
            rows.add(new JComponent[] {formLabel("参数:"), argumentsField});
            rows.add(createArgTagRowEntry(ProgramConfig.TYPE_HTTP_SERVER));
        } else {
            boolean java = ProgramConfig.TYPE_JAVA.equals(selectedTypeValue());
            rows.add(new JComponent[] {formLabel("程序/命令:"), programRow});
            // 折叠区行序与最终命令行顺序一致：JVM 参数 → 常用参数标签 → 参数 → 环境变量
            if (java) {
                advancedRows.add(new JComponent[] {formLabel("JVM 参数:"), jvmArgsField});
                // Java 的常用参数是 -D 系统属性，标签行紧贴 JVM 参数框下方
                advancedRows.add(createArgTagRowEntry(selectedTypeValue()));
            }
            advancedRows.add(new JComponent[] {formLabel("参数:"), argumentsField});
            // 常用参数标签独占一行且不影响输入框与其他行的对齐，其余类型紧贴参数框下方
            if (!java) {
                advancedRows.add(createArgTagRowEntry(selectedTypeValue()));
            }
            advancedRows.add(new JComponent[] {formLabel("环境变量:"), envVarsField});
            // 折叠区标题紧接「程序/命令」下方，默认收起；展开后插入参数行（Java 额外含 JVM 参数与标签行）
            rows.add(new JComponent[] {advancedHeader, null});
            if (advancedExpanded) {
                rows.addAll(advancedRows);
            }
            rows.add(new JComponent[] {formLabel("工作目录:"), workDirRow});
            if (java) {
                rows.add(new JComponent[] {formLabel("JRE/JDK:"), jrePathRow});
            } else if (ProgramConfig.TYPE_PYTHON.equals(selectedTypeValue())) {
                rows.add(new JComponent[] {formLabel("Python:"), pythonPathRow});
            }
        }
        rows.add(new JComponent[] {formLabel("分组:"), groupField});
        rows.add(new JComponent[] {formLabel("备注:"), remarkField});
        
        int row = 0;
        for (JComponent[] cells : rows) {
            // 折叠区标题行没有右侧控件，独占整行（仅右侧标题块响应点击）
            boolean headerRow = cells[1] == null;
            // 标签行紧贴上方的参数输入框（Java 为 JVM 参数框）：使用更小的上间距，其余行使用默认间距
            gbc.insets = cells[1] == quickArgTagRow ? FORM_TAG_ROW_INSETS : FORM_ROW_INSETS;
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = headerRow ? 2 : 1;
            gbc.weightx = headerRow ? 1 : 0;
            formPanel.add(cells[0], gbc);
            if (!headerRow) {
                gbc.gridx = 1;
                gbc.weightx = 1;
                formPanel.add(cells[1], gbc);
            }
            row++;
        }
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        formPanel.add(formBtnPanel, gbc);
        
        // 箭头与「已填: …」摘要随展开状态更新（摘要仅在收起时显示）
        advancedArrow.setIcon(IconUtil.getGroupArrowIcon(advancedExpanded));
        updateAdvancedSummary();
        formPanel.revalidate();
        formPanel.repaint();
        // 行数随程序类型变化（Java/Python 多一行），布局完成后重新适配分割条高度
        SwingUtilities.invokeLater(this::fitFormDivider);
    }
    
    /**
     * 展开 / 收起「启动参数与环境变量」折叠区：仅切换布局，折叠区内输入内容不受影响
     */
    private void toggleAdvancedSection() {
        advancedExpanded = !advancedExpanded;
        rebuildFormPanel();
    }
    
    /**
     * 确保折叠区处于展开状态：校验失败需要用户修改折叠区字段时调用，避免提示指向看不见的输入框
     */
    private void ensureAdvancedExpanded() {
        if (!advancedExpanded) {
            advancedExpanded = true;
            rebuildFormPanel();
        }
    }
    
    /**
     * 折叠区内是否有已填内容：JVM 参数（仅 Java）/ 参数 / 环境变量 任一非空
     */
    private boolean hasAdvancedValues() {
        boolean java = ProgramConfig.TYPE_JAVA.equals(selectedTypeValue());
        return (java && !jvmArgsField.getText().isBlank()) || !argumentsField.getText().isBlank()
                || !envVarsField.getText().isBlank();
    }
    
    /**
     * 为折叠区标题及其子组件绑定展开/收起点击：子组件命中点击时不冒泡到父组件，需逐个绑定
     */
    private void attachAdvancedToggle(Component comp) {
        comp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        comp.addMouseListener(new MouseAdapter() {
            
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleAdvancedSection();
            }
        });
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachAdvancedToggle(child);
            }
        }
    }
    
    /**
     * 更新折叠区标题左侧的「已填: …」摘要：收起时提示哪些字段已有内容，避免隐藏的配置被遗漏；展开时字段本身可见，不再重复提示
     */
    private void updateAdvancedSummary() {
        if (advancedSummaryLabel == null) {
            return;
        }
        List<String> filled = new ArrayList<>();
        if (ProgramConfig.TYPE_JAVA.equals(selectedTypeValue()) && !jvmArgsField.getText().isBlank()) {
            filled.add("JVM 参数");
        }
        if (!argumentsField.getText().isBlank()) {
            filled.add("参数");
        }
        if (!envVarsField.getText().isBlank()) {
            filled.add("环境变量");
        }
        boolean show = !advancedExpanded && !filled.isEmpty();
        advancedSummaryLabel.setText(show ? "（已填: " + String.join("、", filled) + "）" : "");
        advancedSummaryLabel.setVisible(show);
    }
    
    /**
     * 获取当前选中的程序类型持久化值，未选中时默认 Java
     */
    private String selectedTypeValue() {
        int index = typeComboBox.getSelectedIndex();
        return index >= 0 ? TYPE_VALUES[index] : ProgramConfig.TYPE_JAVA;
    }
    
    /**
     * 程序性设置程序类型（不触发表单重建），选中项发生变化返回 true
     */
    private boolean selectType(String typeValue) {
        int target = -1;
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i].equals(typeValue)) {
                target = i;
                break;
            }
        }
        if (target < 0 || target == typeComboBox.getSelectedIndex()) {
            return false;
        }
        suppressTypeAction = true;
        try {
            typeComboBox.setSelectedIndex(target);
        } finally {
            suppressTypeAction = false;
        }
        return true;
    }
    
    /**
     * 应用程序类型选择：类型变化时同步重建表单行与示例提示
     */
    private void applyTypeSelection(String typeValue) {
        if (selectType(typeValue)) {
            rebuildFormPanel();
            updateTypeExamples();
        }
    }
    
    /**
     * 按程序类型更新「程序/命令」与「参数」的占位示例与填写提示
     */
    private void updateTypeExamples() {
        switch (selectedTypeValue()) {
            case ProgramConfig.TYPE_PYTHON -> {
                programPathField.setPlaceholder("如：D:\\proj\\main.py 或 python -m uvicorn app:app");
                argumentsField.setPlaceholder("如：--host 0.0.0.0 --port 8000");
                programPathField.setToolTipText("填 Python 项目入口脚本路径（如 D:\\proj\\main.py），" + "启动时自动前置配置的解释器（未配置则用系统 PATH 的 python）；"
                        + "也可填完整命令行，如 python -m uvicorn app:app --port 8000");
            }
            case ProgramConfig.TYPE_HTTP_SERVER -> {
                programPathField.setPlaceholder("如：D:\\dist 或 D:\\www\\static");
                argumentsField.setPlaceholder("如：-p 8080 -a 127.0.0.1 -S -C cert.pem -K key.pem");
                programPathField.setToolTipText(
                        "静态文件服务根目录：启动后以 http://localhost:<端口>/ 对外提供该目录下文件的访问，" + "支持 index.html 默认页与目录浏览；"
                                + "也可放 TLS 证书文件（cert.pem / key.pem），配合 -S 参数以 https:// 访问");
            }
            case ProgramConfig.TYPE_JAVA -> {
                programPathField.setPlaceholder("如：java -jar D:\\app\\ebx-pawn.jar");
                argumentsField.setPlaceholder("如：--server.port=8080 --spring.profiles.active=prod");
                programPathField.setToolTipText("可填完整命令行（如 java -jar app.jar）或单个 jar/bat 路径，"
                        + "参数填到「参数」框；含空格的段用双引号包裹，如：java -cp \"lib/*;app.jar\" com.demo.Main");
            }
            default -> {
                programPathField.setPlaceholder("如：D:\\server\\startup.bat 或 D:\\tools\\app.exe");
                argumentsField.setPlaceholder("如：--port=8080");
                programPathField.setToolTipText(
                        "可填完整命令行（首词为程序，如 D:\\server\\startup.bat）" + "或单个程序/脚本路径，参数填到「参数」框；含空格的段用双引号包裹");
            }
        }
        if (ProgramConfig.TYPE_HTTP_SERVER.equals(selectedTypeValue())) {
            argumentsField.setToolTipText(
                    "<html>HTTP 服务启动参数，多个用空格分隔：<br>" + "-p / --port 端口号（1-65535，留空默认 " + HttpStaticFileServer.DEFAULT_PORT
                            + "）<br>" + "-a / --address 监听与访问地址（如 127.0.0.1、localhost、0.0.0.0；留空监听全部网卡）<br>"
                            + "-P / --proxy 代理地址：接口请求（任意方法）与本地文件不存在的请求均转发，" + "携带原始接口路径、查询串、请求头与请求体<br>"
                            + "--proxy-prefix /api：命中前缀的请求无条件走代理，不被本地同名文件遮蔽（多个用逗号分隔）<br>"
                            + "--cors[=headers]：开启 CORS（默认 Access-Control-Allow-Origin: * 并本地应答预检）；" + "可传逗号分隔的头列表，每项为「名称」或「名称:值」<br>"
                            + "-S / --tls / --ssl：启用 HTTPS（TLS/SSL），访问地址变为 https://<br>"
                            + "-C / --cert 证书文件路径（PEM，默认 cert.pem）；-K / --key 私钥文件路径（PEM，默认 key.pem，"
                            + "需未加密的 PKCS#8 或 RSA PKCS#1）<br>" + "证书与私钥的相对路径优先按服务根目录解析；自签证书可用 openssl req -x509 -newkey rsa:2048 -nodes "
                            + "-keyout key.pem -out cert.pem -days 365 生成（浏览器会提示不受信任，确认后继续访问即可）<br>"
                            + "示例：--port=8080 --cors=Access-Control-Allow-Origin:http://localhost:5173,"
                            + "Access-Control-Allow-Credentials:true<br>" + "示例：-S -C D:\\cert\\server.pem -K D:\\cert\\server.key</html>");
        } else {
            argumentsField.setToolTipText("传给程序的运行参数，多个用空格分隔；" + (ProgramConfig.TYPE_JAVA.equals(selectedTypeValue())
                    ? "JVM 参数（如 -Xms512m -Dfile.encoding=UTF-8）填到「JVM 参数」框，启动时插在 java 之后、-jar 之前"
                    : "Python 项目的入口脚本可直接写在「程序/命令」中，此处填脚本参数"));
        }
    }
    
    private JLabel formLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setFont(UiConstants.FONT_SANS_12);
        label.setPreferredSize(new Dimension(78, 25));
        return label;
    }
    
    // ────────── 控制台 ──────────
    
    private JPanel createConsolePanel() {
        JPanel consolePanel = new JPanel(new BorderLayout(5, 5));
        consolePanel.setBorder(BorderFactory.createTitledBorder("控制台"));
        
        JPanel consoleToolbar = new JPanel(new BorderLayout(5, 0));
        consoleToolbar.setBorder(new EmptyBorder(5, 8, 5, 8));
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        startBtn = ButtonFactory.createSuccess("启动");
        startBtn.addActionListener(e -> startProgram());
        stopBtn = ButtonFactory.createDestructive("停止");
        stopBtn.addActionListener(e -> stopProgram());
        restartBtn = ButtonFactory.createWarning("重启");
        restartBtn.addActionListener(e -> restartProgram());
        clearBtn = ButtonFactory.createToolbar("清空");
        clearBtn.addActionListener(e -> clearConsole());
        // 全屏日志：独立窗口展示，与控制台共用同一文档实时同步
        fullscreenLogBtn = ButtonFactory.createToolbar("全屏日志");
        fullscreenLogBtn.setToolTipText("在独立窗口中全屏查看日志，内容与控制台实时同步");
        fullscreenLogBtn.addActionListener(e -> openConsoleLogDialog());
        // 内置 HTTP 服务专用：在默认浏览器中打开服务地址
        openBtn = ButtonFactory.createToolbar("访问");
        openBtn.setToolTipText("在默认浏览器中打开当前 HTTP 服务地址");
        openBtn.addActionListener(e -> openInBrowser());
        btnPanel.add(startBtn);
        btnPanel.add(stopBtn);
        btnPanel.add(restartBtn);
        btnPanel.add(clearBtn);
        btnPanel.add(openBtn);
        btnPanel.add(fullscreenLogBtn);
        
        statusLabel = new JLabel("（未选择程序）");
        statusLabel.setFont(UiConstants.FONT_SANS_12);
        statusLabel.setForeground(Color.GRAY);
        
        consoleToolbar.add(btnPanel, BorderLayout.WEST);
        consoleToolbar.add(statusLabel, BorderLayout.EAST);
        
        consoleArea = new JTextArea();
        consoleArea.setFont(UiConstants.FONT_MONO_12);
        consoleArea.setBackground(UiConstants.COLOR_LOG_BG);
        consoleArea.setForeground(UiConstants.COLOR_LOG_FG);
        consoleArea.setCaretColor(UiConstants.COLOR_LOG_FG);
        consoleArea.setEditable(false);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        consoleArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        consoleArea.setToolTipText("双击展示放大，再次双击恢复");
        attachConsolePopupMenu();
        attachConsoleFullscreenToggle();
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        consolePanel.add(consoleToolbar, BorderLayout.NORTH);
        consolePanel.add(consoleScroll, BorderLayout.CENTER);
        
        return consolePanel;
    }
    
    /**
     * 控制台右键菜单：复制选中 / 一键复制全部 / 清空日志，与全屏日志窗口的右键菜单保持一致
     */
    private void attachConsolePopupMenu() {
        consoleArea.addMouseListener(new MouseAdapter() {
            
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
                JPopupMenu popup = new JPopupMenu();
                // 复制选中：无选中内容时置灰，避免误以为已复制全文
                JMenuItem copySelectionItem = new JMenuItem("复制选中");
                copySelectionItem.setEnabled(consoleArea.getSelectionStart() != consoleArea.getSelectionEnd());
                copySelectionItem.addActionListener(ev -> consoleArea.copy());
                JMenuItem copyAllItem = new JMenuItem("一键复制全部");
                copyAllItem.addActionListener(ev -> {
                    consoleArea.selectAll();
                    consoleArea.copy();
                    consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
                    JOptionPane.showMessageDialog(ProgramContainerPanel.this, "控制台内容已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
                });
                JMenuItem clearItem = new JMenuItem("清空日志");
                clearItem.addActionListener(ev -> clearConsole());
                popup.add(copySelectionItem);
                popup.add(copyAllItem);
                popup.addSeparator();
                popup.add(clearItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }
    
    /**
     * 打开全屏日志窗口：复用同一实例避免重复弹窗；窗口已关闭则新建。 页签包含「当前选中程序」与「所有运行中/接管运行的程序」，最终激活的是当前选中程序的页签。 窗口内的启动/停止/重启与页签切换回调均转交面板处理，与控制台使用同一套逻辑
     */
    private void openConsoleLogDialog() {
        if (selectedConfig == null) {
            // 兜底：按钮已置灰，仍防止其它入口在未选程序时打开窗口
            JOptionPane.showMessageDialog(this, "请先在左侧选择一个程序，再查看全屏日志", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (consoleLogDialog != null && consoleLogDialog.isShowing()) {
            consoleLogDialog.toFront();
            showRunningProgramTabs();
            showCurrentConsoleLogTab();
            return;
        }
        Frame owner = SwingUtilities.getWindowAncestor(this) instanceof Frame frame ? frame : null;
        consoleLogDialog = new ConsoleLogDialog(owner, "全屏日志", new ConsoleLogDialog.ProgramActions() {
            
            @Override
            public void start() {
                startProgram();
            }
            
            @Override
            public void stop() {
                stopProgram();
            }
            
            @Override
            public void restart() {
                restartProgram();
            }
            
            @Override
            public void selectProgram(Long programId) {
                selectProgramById(programId);
            }
        }, this::clearConsole);
        // 默认以全屏状态打开，ESC/F11/按钮可还原为默认尺寸
        consoleLogDialog.showFullscreen();
        // 先补齐运行中/接管运行程序的页签（不抢焦点），再切到当前选中程序的页签
        showRunningProgramTabs();
        showCurrentConsoleLogTab();
        // 初始按钮启用态按当前程序状态推送
        updateActionState();
    }
    
    /**
     * 补齐「运行中 / 接管运行」程序的页签：运行状态与程序列表徽标同一判定，内容取服务端日志缓冲快照。 页签在后台创建，不切换当前选中页签；点开此类页签会选中对应程序并接管实时输出
     */
    private void showRunningProgramTabs() {
        if (consoleLogDialog == null || !consoleLogDialog.isShowing()) {
            return;
        }
        for (OptionJPanel panel : programPanelList) {
            ProgramConfig config = (ProgramConfig) panel.getData();
            if (config == null) {
                continue;
            }
            boolean running = PROCESS_SERVICE.isRunning(config.getId());
            boolean externalRunning = !running && isExternallyRunning(config);
            if (!running && !externalRunning) {
                continue;
            }
            List<String> logs = PROCESS_SERVICE.getLogBuffer(config.getId());
            if (logs.isEmpty()) {
                // 接管运行等尚无内存会话的程序：只读日志文件末尾若干行，不建会话、不改回调
                logs = PROCESS_SERVICE.readLogFileTail(config.getId());
            }
            String text = logs.isEmpty() ? "" : String.join("\n", logs) + "\n";
            consoleLogDialog.showLogInBackground(config.getId(), config.getName(), text);
        }
    }
    
    /**
     * 按 id 选中程序列表中的程序：供全屏日志窗口页签切换时回调，未找到（已被删除）返回 false
     */
    private boolean selectProgramById(Long id) {
        if (id == null) {
            return false;
        }
        // 已是当前选中项直接返回，避免页签同步重复触发选中流程
        if (selectedConfig != null && id.equals(selectedConfig.getId())) {
            return true;
        }
        for (OptionJPanel panel : programPanelList) {
            ProgramConfig config = (ProgramConfig) panel.getData();
            if (config != null && id.equals(config.getId())) {
                selectProgram(config, panel);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 把当前控制台内容同步到全屏日志窗口：程序运行日志进该程序自己的页签（标签只有程序名称）， 未选中程序时进「未选择程序」占位页签。帮助内容不经过控制台与全屏日志，由独立的帮助弹窗展示
     */
    private void showCurrentConsoleLogTab() {
        if (consoleLogDialog == null || !consoleLogDialog.isShowing()) {
            return;
        }
        if (selectedConfig != null) {
            consoleLogDialog.showLog(selectedConfig.getId(), selectedConfig.getName(), consoleArea.getText());
            return;
        }
        consoleLogDialog.showLog(null, "未选择程序", consoleArea.getText());
    }
    
    /**
     * 控制台是否处于双击全屏状态（表单区收起）
     */
    private boolean consoleFullscreen;
    
    /**
     * 控制台双击切换全屏展示：收起表单区（分割条拉到底），控制台占满窗口；再次双击恢复表单占比。 手动拖动分割条后双击仍按标志切换，展示/恢复交替生效
     */
    private void attachConsoleFullscreenToggle() {
        consoleArea.addMouseListener(new MouseAdapter() {
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleConsoleFullscreen();
                }
            }
        });
    }
    
    /**
     * 双击控制台切换全屏展示：直接移除表单区让控制台占满整个右侧区域； 再双击放回表单并按表单高度重新定位分割条。 不用 setDividerLocation(1.0)——它受表单最小尺寸 clamp 且在布局变化后可能被重置，导致全屏不生效
     */
    private void toggleConsoleFullscreen() {
        consoleFullscreen = !consoleFullscreen;
        if (consoleFullscreen) {
            rightSplitPane.setTopComponent(null);
        } else {
            rightSplitPane.setTopComponent(formScroll);
            SwingUtilities.invokeLater(this::fitFormDivider);
        }
    }
    
    // ────────── 程序列表 ──────────
    
    private void refreshProgramList() {
        programListPanel.removeAll();
        programPanelList.clear();
        programGroups.clear();
        statusLabels.clear();
        
        List<ProgramConfig> configs = ConfigUtil.loadProgramConfigs();
        hasGroupedPrograms = false;
        for (ProgramConfig config : configs) {
            if (!config.resolveGroupName().isEmpty()) {
                hasGroupedPrograms = true;
                break;
            }
        }
        // 列表刷新说明数据已变更（拖拽已结束），统一恢复搜索框，避免提示条残留
        showSearchCard();
        syncGroupOptions(configs);
        if (configs.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无程序）", SwingConstants.CENTER);
            emptyLabel.setFont(UiConstants.FONT_SANS_12);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            programListPanel.add(emptyLabel);
        } else {
            Map<String, List<ProgramConfig>> grouped = groupProgramConfigs(configs);
            for (Map.Entry<String, List<ProgramConfig>> entry : grouped.entrySet()) {
                // 未分组（键为空串）不显示分组标题，直接展开在列表末尾
                addProgramGroup(entry.getKey(), entry.getValue(), entry.getKey().isEmpty());
            }
        }
        
        filterProgramList();
        refreshAllStatusLabels();
        revalidateList();
    }
    
    /**
     * 刷新列表尺寸与滚动范围：分组标题/条目的增减会改变内容高度， 需连同滚动视口一起重算，否则滚动条范围偏小会让最后一项显示不全
     */
    private void revalidateList() {
        programListPanel.revalidate();
        if (listScrollPane != null) {
            listScrollPane.revalidate();
        }
        programListPanel.repaint();
    }
    
    private void filterProgramList() {
        String keyword = searchField.getText().trim().toLowerCase(Locale.ROOT);
        boolean anyVisible = false;
        for (ProgramGroup group : programGroups) {
            int total = group.items.size();
            int visibleCount = 0;
            for (int i = 0; i < total; i++) {
                OptionJPanel panel = group.items.get(i);
                ProgramConfig config = (ProgramConfig) panel.getData();
                boolean visible = keyword.isEmpty() || matchesKeyword(config, group.name, keyword);
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
        for (Component comp : programListPanel.getComponents()) {
            if (comp instanceof JLabel label && "（暂无程序）".equals(label.getText())) {
                comp.setVisible(!anyVisible && keyword.isEmpty());
            }
        }
        revalidateList();
    }
    
    /**
     * 按分组名称聚合程序配置：组间按名称排序（中文按拼音），未分组的程序用空串作键固定追加到最后；
     * 组内保持配置加载顺序
     */
    private Map<String, List<ProgramConfig>> groupProgramConfigs(List<ProgramConfig> configs) {
        Map<String, List<ProgramConfig>> sorted = new TreeMap<>(Collator.getInstance(Locale.CHINA));
        List<ProgramConfig> ungrouped = new ArrayList<>();
        for (ProgramConfig config : configs) {
            String groupName = config.resolveGroupName();
            if (groupName.isEmpty()) {
                ungrouped.add(config);
            } else {
                sorted.computeIfAbsent(groupName, key -> new ArrayList<>()).add(config);
            }
        }
        Map<String, List<ProgramConfig>> grouped = new LinkedHashMap<>(sorted);
        if (!ungrouped.isEmpty()) {
            grouped.put("", ungrouped);
        }
        return grouped;
    }
    
    /**
     * 构建一个分组（可折叠标题 + 组内程序项）：flat 表示未分组，不加标题、不缩进，程序项直接平铺展开
     */
    private void addProgramGroup(String groupName, List<ProgramConfig> configs, boolean flat) {
        // 箭头图标 + 分组图标 + 分组名
        JLabel arrowLabel = new JLabel(IconUtil.getGroupArrowIcon(true));
        JLabel titleLabel = new JLabel(groupName, IconUtil.getGroupIcon(), SwingConstants.LEFT);
        titleLabel.setFont(UiConstants.FONT_SANS_12.deriveFont(Font.BOLD));
        titleLabel.setIconTextGap(6);
        JLabel countLabel = new JLabel(String.valueOf(configs.size()));
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
        // 组内程序项右缩进，与分组标题形成层级
        body.setBorder(new EmptyBorder(0, flat ? 0 : 10, 0, 0));
        
        ProgramGroup group = new ProgramGroup(groupName, flat, header, arrowLabel, countLabel, body);
        for (ProgramConfig config : configs) {
            OptionJPanel panel = createProgramPanel(config);
            Component spacer = Box.createRigidArea(new Dimension(0, 8));
            body.add(panel);
            body.add(spacer);
            programPanelList.add(panel);
            group.items.add(panel);
            group.spacers.add(spacer);
        }
        programGroups.add(group);
        
        if (flat) {
            // 未分组区：拖到此处即移出分组（大面积铺色反而干扰视线，不高亮，依靠顶部提示条引导）
            GroupDragSupport.enableGroupTarget(body, "", null, listScrollPane);
            programListPanel.add(body);
        } else {
            // 拖到分组标题或组内任意位置都加入该分组，统一高亮标题行
            GroupDragSupport.enableGroupTarget(header, groupName, header, listScrollPane);
            GroupDragSupport.enableGroupTarget(body, groupName, header, listScrollPane);
            attachGroupToggle(header, group);
            programListPanel.add(header);
            programListPanel.add(body);
        }
    }
    
    /**
     * 为分组标题及其子组件绑定折叠/展开点击：子组件命中点击时不冒泡到父组件，需逐个绑定
     */
    private void attachGroupToggle(Component comp, ProgramGroup group) {
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
    private void toggleGroup(ProgramGroup group) {
        if (!collapsedGroups.remove(group.name)) {
            collapsedGroups.add(group.name);
        }
        filterProgramList();
    }
    
    /**
     * 关键字匹配：程序名称或所属分组名称包含关键字（忽略大小写）
     */
    private boolean matchesKeyword(ProgramConfig config, String groupName, String keyword) {
        return config.getName().toLowerCase(Locale.ROOT).contains(keyword) || groupName.toLowerCase(Locale.ROOT).contains(keyword);
    }
    
    private OptionJPanel createProgramPanel(ProgramConfig config) {
        // 按程序类型显示图标：Java / Python / 其他
        OptionJPanel panel = new OptionJPanel(config.getName(), config.getProgramPath(), IconUtil.getProgramTypeIcon(config.resolveProgramType()));
        panel.setData(config);
        panel.setToolTipText(config.getProgramPath());
        panel.fitPreferredHeight();
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setOnClick(() -> selectProgram(config, panel));
        
        // 右侧状态徽标：● 运行中 / ○ 已停止
        JLabel stateLabel = new JLabel("○ 已停止");
        stateLabel.setFont(UiConstants.FONT_SANS_11);
        stateLabel.setForeground(Color.GRAY);
        stateLabel.setOpaque(false);
        stateLabel.addMouseListener(new MouseAdapter() {
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (panel.getOnClick() != null) {
                    panel.getOnClick().run();
                }
            }
        });
        panel.add(stateLabel, BorderLayout.EAST);
        statusLabels.put(config.getId(), stateLabel);
        
        attachProgramPopupMenu(panel, config, panel);
        // 可拖到分组标题/组内容区加入分组，拖到列表空白区移出分组
        GroupDragSupport.enableDragSource(panel, groupName -> moveProgramToGroup(config, groupName),
                !config.resolveGroupName().isEmpty(), this::onProgramDragStateChanged);
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
     * 拖拽状态回调：拖动已分组程序时把搜索框切换为「移出分组」提示条，拖拽结束或拖动未分组程序时恢复搜索框
     */
    private void onProgramDragStateChanged(boolean dragging, boolean grouped) {
        if (searchCardLayout == null) {
            return;
        }
        if (dragging && grouped && hasGroupedPrograms) {
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
     * 拖拽移动到分组：仅更新分组字段（不刷新更新时间，避免列表重排），成功后刷新列表并保持选中
     */
    private void moveProgramToGroup(ProgramConfig config, String groupName) {
        String target = groupName == null ? "" : groupName.trim();
        if (target.equals(config.resolveGroupName())) {
            return;
        }
        if (!ConfigUtil.updateProgramGroupName(config.getId(), target)) {
            JOptionPane.showMessageDialog(this, "移动到分组失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 拖入的组若处于折叠状态，自动展开，避免刚拖入的程序被隐藏
        collapsedGroups.remove(target);
        refreshProgramList();
        for (OptionJPanel panel : programPanelList) {
            ProgramConfig reloaded = (ProgramConfig) panel.getData();
            if (reloaded.getId().equals(config.getId())) {
                selectProgram(reloaded, panel);
                // 列表刚重建、布局尚未完成，延后布局后滚动，保证拖入分组的程序一定落在可视区内
                SwingUtilities.invokeLater(() -> {
                    listScrollPane.validate();
                    programListPanel.scrollRectToVisible(panel.getBounds());
                });
                return;
            }
        }
    }
    
    private void attachProgramPopupMenu(Component comp, ProgramConfig config, OptionJPanel panel) {
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
                selectProgram(config, panel);
                JPopupMenu popup = new JPopupMenu();
                GlobalUtil.addMenuItem(popup, "编 辑", () -> selectProgram(config, panel));
                GlobalUtil.addMenuItem(popup, "复制新增", () -> duplicateProgram(config));
                GlobalUtil.addMenuItem(popup, "删 除", () -> deleteProgram(config));
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachProgramPopupMenu(child, config, panel);
            }
        }
    }
    
    private void selectProgram(ProgramConfig config, OptionJPanel panel) {
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = panel;
        selectedPanel.setSelected(true);
        selectedConfig = config;
        fillFormFromConfig(config);
        attachConsoleListener(config.getId());
        updateActionState();
    }
    
    private void refreshAllStatusLabels() {
        for (OptionJPanel panel : programPanelList) {
            ProgramConfig config = (ProgramConfig) panel.getData();
            if (config == null) {
                continue;
            }
            JLabel stateLabel = statusLabels.get(config.getId());
            if (stateLabel == null) {
                continue;
            }
            if (PROCESS_SERVICE.isRunning(config.getId())) {
                stateLabel.setText("● 运行中");
                stateLabel.setForeground(UiConstants.COLOR_SUCCESS_LIGHT);
                stateLabel.setToolTipText(null);
            } else if (isExternallyRunning(config)) {
                stateLabel.setText("● 接管运行");
                stateLabel.setForeground(UiConstants.COLOR_PENDING);
                stateLabel.setToolTipText("上次启动的进程 PID=" + config.getLastPid() + " 仍在后台运行，可查看日志或停止");
            } else {
                stateLabel.setText("○ 已停止");
                stateLabel.setForeground(Color.GRAY);
                stateLabel.setToolTipText(null);
            }
        }
    }
    
    /**
     * 程序是否处于「接管运行」状态：无内存会话，但上次启动的进程经应用重启后仍在后台运行
     */
    private boolean isExternallyRunning(ProgramConfig config) {
        return !PROCESS_SERVICE.isRunning(config.getId()) && config.getLastPid() != null && ProcessContainerService.isPidAlive(config.getLastPid());
    }
    
    // ────────── 表单编辑 ──────────
    
    private void fillFormFromConfig(ProgramConfig config) {
        applyTypeSelection(config.resolveProgramType());
        nameField.setText(config.getName());
        groupField.getEditor().setItem(config.resolveGroupName());
        programPathField.setText(config.getProgramPath());
        argumentsField.setText(config.getArguments());
        jvmArgsField.setText(config.getJvmArgs());
        envVarsField.setText(config.getEnvVars());
        workDirField.setText(config.getWorkDir());
        jrePathField.setText(config.getJrePath());
        pythonPathField.setText(config.getPythonPath());
        remarkField.setText(config.getRemark());
        // 切换选中程序或载入导入配置后同步折叠区摘要（收起时标题右侧可见）
        updateAdvancedSummary();
    }
    
    private void clearForm() {
        applyTypeSelection(ProgramConfig.TYPE_JAVA);
        nameField.setText("");
        groupField.getEditor().setItem("");
        programPathField.setText("");
        argumentsField.setText("");
        jvmArgsField.setText("");
        envVarsField.setText("");
        workDirField.setText("");
        jrePathField.setText("");
        pythonPathField.setText("");
        remarkField.setText("");
        updateAdvancedSummary();
    }
    
    /**
     * 重置表单：已选程序恢复为保存时的配置（丢弃未保存的修改），新增程序清空表单； 运行中的程序表单为只读态，重置按钮已置灰，不会误改配置
     */
    private void resetForm() {
        if (selectedConfig != null) {
            fillFormFromConfig(selectedConfig);
        } else {
            clearForm();
        }
        nameField.requestFocusInWindow();
    }
    
    private void setFormEditable(boolean editable) {
        typeComboBox.setEnabled(editable);
        nameField.setEditable(editable);
        groupField.setEnabled(editable);
        programPathField.setEditable(editable);
        argumentsField.setEditable(editable);
        jvmArgsField.setEditable(editable);
        envVarsField.setEditable(editable);
        setArgTagsEnabled(editable);
        workDirField.setEditable(editable);
        jrePathField.setEditable(editable);
        pythonPathField.setEditable(editable);
        remarkField.setEditable(editable);
        browseProgramBtn.setEnabled(editable);
        browseWorkDirBtn.setEnabled(editable);
        browseJreBtn.setEnabled(editable);
        browsePythonBtn.setEnabled(editable);
        saveBtn.setEnabled(editable);
        resetBtn.setEnabled(editable);
    }
    
    /**
     * 同步常用参数标签的可用状态：标签行随程序类型重建，禁用时需按当前行内容逐个设置
     */
    private void setArgTagsEnabled(boolean enabled) {
        if (quickArgTagRow == null) {
            return;
        }
        for (Component comp : quickArgTagRow.getComponents()) {
            comp.setEnabled(enabled);
        }
    }
    
    private void createNewProgram() {
        // 控制台处于双击全屏状态时自动恢复表单区，便于填写新建配置
        if (consoleFullscreen) {
            toggleConsoleFullscreen();
        }
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = null;
        selectedConfig = null;
        attachConsoleListener(null);
        clearForm();
        updateActionState();
        nameField.requestFocusInWindow();
    }
    
    /**
     * 复制新增：以指定程序配置为模板填充新建表单，名称自动追加「-副本」后缀。 表单不关联已有程序（selectedConfig 置空），保存时按新增程序插入。
     */
    private void duplicateProgram(ProgramConfig config) {
        // 控制台处于双击全屏状态时自动恢复表单区，便于填写新配置
        if (consoleFullscreen) {
            toggleConsoleFullscreen();
        }
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPanel = null;
        selectedConfig = null;
        attachConsoleListener(null);
        fillFormFromConfig(config);
        nameField.setText(uniqueProgramName(config.getName() + "-副本"));
        updateActionState();
        nameField.requestFocusInWindow();
    }
    
    /**
     * 生成全局唯一的程序名称：baseName 已被占用时依次追加 -2、-3…，判定规则与保存校验一致（不区分大小写）
     */
    private String uniqueProgramName(String baseName) {
        String candidate = baseName.trim();
        if (!ConfigUtil.isProgramNameTaken(candidate, null)) {
            return candidate;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String next = candidate + "-" + suffix;
            if (!ConfigUtil.isProgramNameTaken(next, null)) {
                return next;
            }
        }
        return candidate + "-" + System.currentTimeMillis();
    }
    
    /**
     * 保存当前表单：新增程序插入并回填 id，已有程序更新；成功后刷新列表并保持选中。 校验失败或保存失败时弹出提示并返回 false。
     */
    private boolean saveCurrentForm() {
        String name = nameField.getText().trim();
        String programPath = programPathField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "程序名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (programPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "程序/命令不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        // HTTP 服务：目录必须真实存在，端口需为合法端口号
        if (ProgramConfig.TYPE_HTTP_SERVER.equals(selectedTypeValue()) && !validateHttpServerForm(programPath)) {
            return false;
        }
        // 环境变量格式：KEY=VALUE，多个用分号分隔；格式错误时保存前就拦下，避免启动才报错
        String envError = ProcessContainerService.validateEnvVars(envVarsField.getText());
        if (envError != null) {
            // 折叠区收起时先展开，保证用户能看到出错的输入框
            ensureAdvancedExpanded();
            JOptionPane.showMessageDialog(this, "环境变量格式有误：" + envError, "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        // 名称全局唯一（不区分大小写）：修改时排除自身
        if (ConfigUtil.isProgramNameTaken(name, selectedConfig == null ? null : selectedConfig.getId())) {
            JOptionPane.showMessageDialog(this, "已存在同名程序（名称不区分大小写），请重新命名", "错误", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (selectedConfig != null) {
            // 更新已有程序
            selectedConfig.setName(name);
            selectedConfig.setGroupName(groupFieldText());
            selectedConfig.setProgramPath(programPath);
            selectedConfig.setProgramType(selectedTypeValue());
            selectedConfig.setArguments(argumentsField.getText().trim());
            selectedConfig.setJvmArgs(jvmArgsField.getText().trim());
            selectedConfig.setEnvVars(envVarsField.getText().trim());
            selectedConfig.setWorkDir(workDirField.getText().trim());
            selectedConfig.setJrePath(jrePathField.getText().trim());
            selectedConfig.setPythonPath(pythonPathField.getText().trim());
            selectedConfig.setRemark(remarkField.getText().trim());
            if (!ConfigUtil.updateProgramConfig(selectedConfig)) {
                JOptionPane.showMessageDialog(this, "更新程序配置失败", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } else {
            // 新增程序
            ProgramConfig config = new ProgramConfig(name, programPath, argumentsField.getText().trim());
            config.setJvmArgs(jvmArgsField.getText().trim());
            config.setEnvVars(envVarsField.getText().trim());
            config.setProgramType(selectedTypeValue());
            config.setGroupName(groupFieldText());
            config.setWorkDir(workDirField.getText().trim());
            config.setJrePath(jrePathField.getText().trim());
            config.setPythonPath(pythonPathField.getText().trim());
            config.setRemark(remarkField.getText().trim());
            if (!ConfigUtil.saveProgramConfig(config)) {
                JOptionPane.showMessageDialog(this, "保存程序配置失败", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            selectedConfig = config;
        }
        
        refreshProgramList();
        for (OptionJPanel panel : programPanelList) {
            ProgramConfig config = (ProgramConfig) panel.getData();
            if (config.getId().equals(selectedConfig.getId())) {
                selectProgram(config, panel);
                break;
            }
        }
        return true;
    }
    
    /**
     * 校验 HTTP 服务表单：服务目录必须存在且为目录，启动参数（端口、代理地址等）需可解析
     */
    private boolean validateHttpServerForm(String rootPath) {
        File root = new File(rootPath);
        if (!root.isDirectory()) {
            JOptionPane.showMessageDialog(this, "HTTP 服务目录不存在或不是目录：" + rootPath, "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        String invalid = HttpServerOptions.parse(argumentsField.getText()).invalidMessage();
        if (invalid != null) {
            JOptionPane.showMessageDialog(this, "HTTP 服务参数有误：" + invalid, "提示", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }
    
    private void browseProgramPath() {
        // HTTP 服务的「程序/命令」字段语义为服务根目录，浏览时选择目录
        boolean httpServer = ProgramConfig.TYPE_HTTP_SERVER.equals(selectedTypeValue());
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(httpServer ? "选择 HTTP 服务根目录" : "选择程序或脚本");
        chooser.setFileSelectionMode(httpServer ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            programPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void browseWorkDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择工作目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            workDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void browseJrePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 JDK/JRE 根目录（也可手动输入 java 可执行文件路径）");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            jrePathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void browsePythonPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 Python 安装目录、虚拟环境目录，或 python 可执行文件");
        // 目录（venv/安装根目录）与可执行文件（python.exe）均可能，故允许两者
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pythonPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    // ────────── 配置导入导出 ──────────
    
    /**
     * 导出选中程序的配置为 JSON 文件：未选中程序时按钮不可用，默认文件名取程序名称，覆盖前二次确认
     */
    private void exportProgramConfig() {
        if (selectedConfig == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出程序配置：" + selectedConfig.getName());
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件 (*.json)", ProgramConfigTransferUtil.FILE_EXTENSION));
        chooser.setSelectedFile(new File(ProgramConfigTransferUtil.defaultFileName(selectedConfig.getName())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = ProgramConfigTransferUtil.withJsonExtension(chooser.getSelectedFile());
        if (file.exists() && JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？\n" + file.getAbsolutePath(), "导出程序配置",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ProgramConfigTransferUtil.exportToFile(file, List.of(selectedConfig));
        } catch (IOException e) {
            logger.error("[ProgramContainer] 导出程序配置失败: {}", file.getAbsolutePath(), e);
            JOptionPane.showMessageDialog(this, "导出失败：" + e.getMessage(), "导出程序配置", JOptionPane.ERROR_MESSAGE);
            return;
        }
        logger.info("[ProgramContainer] 已导出程序配置: {} -> {}", selectedConfig.getName(), file.getAbsolutePath());
        int choice = JOptionPane.showConfirmDialog(this,
                "已导出程序 [" + selectedConfig.getName() + "] 的配置到：\n" + file.getAbsolutePath() + "\n\n是否打开所在文件夹？", "导出成功",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            openDirectory(file.getParentFile());
        }
    }
    
    /**
     * 把 JSON 文件中的程序配置载入表单：仅在「新增程序」状态（未选中任何程序）可用， 文件含多条时由用户选择一条，载入后仍需点「保存」才写入配置库
     */
    private void importProgramConfig() {
        if (selectedConfig != null) {
            return;
        }
        // 表单已有输入时先确认覆盖，避免误丢已录入内容
        boolean formDirty = !nameField.getText().isBlank() || !programPathField.getText().isBlank();
        if (formDirty && JOptionPane.showConfirmDialog(this, "导入会覆盖当前表单内容，是否继续？", "导入程序配置",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要导入到表单的程序配置 JSON 文件");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON 文件 (*.json)", ProgramConfigTransferUtil.FILE_EXTENSION));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        ProgramConfigTransferUtil.ParseResult parsed;
        try {
            parsed = ProgramConfigTransferUtil.parse(file);
        } catch (IOException e) {
            logger.error("[ProgramContainer] 解析程序配置失败: {}", file.getAbsolutePath(), e);
            JOptionPane.showMessageDialog(this, "导入失败：" + e.getMessage(), "导入程序配置", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<ProgramConfig> configs = parsed.getConfigs();
        if (configs.isEmpty()) {
            JOptionPane.showMessageDialog(this, describeErrors("文件中没有可导入的程序配置，请检查格式", parsed.getErrors()),
                    "导入程序配置", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProgramConfig imported = configs.size() == 1 ? configs.get(0) : chooseImportProgram(configs);
        if (imported == null) {
            return;
        }
        // 载入表单：保持「新增」状态（selectedConfig 为空），保存时才写入配置库
        if (consoleFullscreen) {
            toggleConsoleFullscreen();
        }
        fillFormFromConfig(imported);
        // 导入的配置带参数类字段时自动展开折叠区，便于逐项核对后再保存
        if (hasAdvancedValues()) {
            ensureAdvancedExpanded();
        }
        String name = imported.getName();
        boolean renamed = ConfigUtil.isProgramNameTaken(name, null);
        if (renamed) {
            // 与现有程序重名：自动加后缀保证直接保存不会失败，用户仍可再改
            name = uniqueProgramName(name + "-副本");
            nameField.setText(name);
        }
        nameField.requestFocusInWindow();
        updateActionState();
        StringBuilder summary = new StringBuilder("已载入「" + name + "」到表单，请确认后点「保存」生效。");
        if (renamed) {
            summary.append("\n名称与现有程序重复，已自动改为「").append(name).append("」，可自行调整。");
        }
        if (configs.size() > 1) {
            summary.append("\n文件共 ").append(configs.size()).append(" 个程序配置，本次仅载入所选的这一个。");
        }
        JOptionPane.showMessageDialog(this, describeErrors(summary.toString(), parsed.getErrors()), "导入程序配置",
                parsed.getErrors().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        logger.info("[ProgramContainer] 已载入程序配置到表单: {} (来源: {})", name, file.getName());
    }
    
    /**
     * 文件含多个程序配置时让用户选择载入哪一个，取消返回 null
     */
    private ProgramConfig chooseImportProgram(List<ProgramConfig> configs) {
        String[] options = new String[configs.size()];
        for (int i = 0; i < configs.size(); i++) {
            ProgramConfig config = configs.get(i);
            String groupName = config.resolveGroupName();
            options[i] = groupName.isEmpty() ? config.getName() : config.getName() + "（" + groupName + "）";
        }
        Object choice = JOptionPane.showInputDialog(this, "文件包含 " + configs.size() + " 个程序配置，请选择要导入的那一个：",
                "选择要导入的程序", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == null) {
            return null;
        }
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(choice)) {
                return configs.get(i);
            }
        }
        return null;
    }
    
    /**
     * 拼接提示文本：错误项最多展示 10 条，超出时只提示总条数
     */
    private String describeErrors(String headline, List<String> errors) {
        if (errors.isEmpty()) {
            return headline;
        }
        StringBuilder text = new StringBuilder(headline).append("\n");
        int limit = Math.min(errors.size(), 10);
        for (int i = 0; i < limit; i++) {
            text.append("· ").append(errors.get(i)).append("\n");
        }
        if (errors.size() > limit) {
            text.append("· …（共 ").append(errors.size()).append(" 项）\n");
        }
        return text.toString();
    }
    
    /**
     * 打开目录（导出成功后定位文件更方便），环境不支持或打开失败时静默忽略
     */
    private void openDirectory(File dir) {
        if (dir == null || !Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return;
        }
        try {
            Desktop.getDesktop().open(dir);
        } catch (IOException e) {
            logger.warn("[ProgramContainer] 打开导出目录失败: {}", dir, e);
        }
    }
    
    // ────────── 进程控制 ──────────
    
    private void startProgram() {
        if (selectedConfig == null) {
            return;
        }
        // 启动前先保存表单最新内容，保证所见即所得
        if (!saveCurrentForm()) {
            return;
        }
        ProgramConfig config = selectedConfig;
        // 启动前清空该程序的旧日志（界面缓冲 + 服务端日志缓冲）
        clearProgramLogs(config.getId());
        startBtn.setEnabled(false);
        new Thread(() -> {
            String error = PROCESS_SERVICE.start(config, this::onOutputLine, this::onStateChanged);
            SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    JOptionPane.showMessageDialog(this, error, "启动失败", JOptionPane.WARNING_MESSAGE);
                }
                updateActionState();
            });
        }, "program-container-start").start();
    }
    
    private void stopProgram() {
        if (selectedConfig == null) {
            return;
        }
        ProgramConfig config = selectedConfig;
        boolean inMemoryRunning = PROCESS_SERVICE.isRunning(config.getId());
        Long externalPid = !inMemoryRunning && isExternallyRunning(config) ? config.getLastPid() : null;
        stopBtn.setEnabled(false);
        startBtn.setEnabled(false);
        restartBtn.setEnabled(false);
        // stop/stopExternalProcess 均为阻塞方法（最多等待约 3 秒），放后台线程执行避免卡界面
        new Thread(() -> {
            if (inMemoryRunning) {
                PROCESS_SERVICE.stop(config.getId());
            } else if (externalPid != null) {
                PROCESS_SERVICE.stopExternalProcess(externalPid);
            }
            SwingUtilities.invokeLater(this::updateActionState);
        }, "program-container-stop").start();
    }
    
    private void restartProgram() {
        if (selectedConfig == null) {
            return;
        }
        long id = selectedConfig.getId();
        // 重启前清空该程序的旧日志（界面缓冲 + 服务端日志缓冲）
        clearProgramLogs(id);
        restartBtn.setEnabled(false);
        new Thread(() -> {
            String error = PROCESS_SERVICE.restart(id);
            SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    JOptionPane.showMessageDialog(this, error, "重启失败", JOptionPane.WARNING_MESSAGE);
                }
                updateActionState();
            });
        }, "program-container-restart").start();
    }
    
    /**
     * 在默认浏览器中打开当前运行的内置 HTTP 服务地址；不支持桌面浏览时提示手动访问
     */
    private void openInBrowser() {
        if (selectedConfig == null) {
            return;
        }
        String url = PROCESS_SERVICE.getHttpServerUrl(selectedConfig.getId());
        if (url == null) {
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(this, "当前环境不支持自动打开浏览器，请手动访问：" + url, "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new Thread(() -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(
                        () -> JOptionPane.showMessageDialog(this, "打开浏览器失败，请手动访问：" + url, "错误", JOptionPane.ERROR_MESSAGE));
            }
        }, "program-container-open-browser").start();
    }
    
    /**
     * 展示程序类型配置帮助：含字段说明、参数说明与完整示例（含实际执行的命令行）。
     * <p>
     * 帮助在独立弹窗中展示，不写入控制台、也不进全屏日志窗口，因此查看帮助不会冲掉正在看的运行日志。 初始类型按界面「程序类型」下拉框当前值确定（选中程序时即为该程序的类型），弹窗内可直接切换类型
     */
    private void printTypeHelp() {
        String type = selectedTypeValue();
        if (helpDialog != null && helpDialog.isShowing()) {
            helpDialog.showHelpFor(type);
            helpDialog.toFront();
            return;
        }
        Frame owner = SwingUtilities.getWindowAncestor(this) instanceof Frame frame ? frame : null;
        helpDialog = new ProgramHelpDialog(owner, type);
        helpDialog.setVisible(true);
    }
    
    private void clearConsole() {
        pendingOutput.clear();
        consoleArea.setText("");
        if (selectedConfig != null) {
            PROCESS_SERVICE.clearLog(selectedConfig.getId());
        }
        showCurrentConsoleLogTab();
    }
    
    /**
     * 清空指定程序的全部日志记录：界面待刷新缓冲、控制台内容与服务端日志缓冲。 供「清空」按钮外的启动/重启流程复用，保证每次运行从空白控制台开始
     */
    private void clearProgramLogs(long id) {
        pendingOutput.clear();
        consoleArea.setText("");
        PROCESS_SERVICE.clearLog(id);
        // 同步清空磁盘日志文件记录（data/program-logs/<id>.log）
        PROCESS_SERVICE.clearLogFile(id);
        showCurrentConsoleLogTab();
    }
    
    private void deleteProgram(ProgramConfig config) {
        boolean running = PROCESS_SERVICE.isRunning(config.getId()) || isExternallyRunning(config);
        String message = running ? "程序 [" + config.getName() + "] 正在运行，删除将停止该程序，确认删除？" : "确认删除程序 [" + config.getName() + "]？";
        if (JOptionPane.showConfirmDialog(this, message, "删除确认", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        // 二次确认
        String secondMessage = "删除后配置不可恢复，请再次确认删除 [" + config.getName() + "]？";
        if (JOptionPane.showConfirmDialog(this, secondMessage, "二次确认", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) {
            return;
        }
        long id = config.getId();
        Long externalPid = !PROCESS_SERVICE.isRunning(id) && isExternallyRunning(config) ? config.getLastPid() : null;
        new Thread(() -> {
            // 先停止并移除进程会话（含接管的外部进程），再删除配置
            PROCESS_SERVICE.removeSession(id);
            if (externalPid != null) {
                PROCESS_SERVICE.stopExternalProcess(externalPid);
            }
            SwingUtilities.invokeLater(() -> {
                if (!ConfigUtil.deleteProgramConfig(id)) {
                    JOptionPane.showMessageDialog(this, "删除程序配置失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
                if (selectedConfig != null && selectedConfig.getId().equals(id)) {
                    selectedConfig = null;
                    selectedPanel = null;
                    attachConsoleListener(null);
                    clearForm();
                }
                refreshProgramList();
                updateActionState();
            });
        }, "program-container-delete").start();
    }
    
    // ────────── 控制台输出与状态刷新 ──────────
    
    /**
     * 切换控制台监听目标：卸载旧程序回调 → 丢弃未刷新的旧输出 → 恢复新程序历史日志并注册回调。 接管运行中的进程先建立外部会话并 tail 日志文件，历史与实时输出均可查看
     */
    private void attachConsoleListener(Long id) {
        if (attachedId != null) {
            PROCESS_SERVICE.detachListener(attachedId);
        }
        pendingOutput.clear();
        attachedId = id;
        if (id != null) {
            if (selectedConfig != null && selectedConfig.getId().equals(id) && isExternallyRunning(selectedConfig)) {
                PROCESS_SERVICE.attachExternal(id, selectedConfig, this::onOutputLine, this::onStateChanged);
            }
            List<String> logs = PROCESS_SERVICE.getLogBuffer(id);
            String text = logs.isEmpty() ? "" : String.join("\n", logs) + "\n";
            consoleArea.setText(text);
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            PROCESS_SERVICE.attachListener(id, this::onOutputLine, this::onStateChanged);
        } else {
            consoleArea.setText("");
        }
        // 全屏日志窗口已打开时切换到对应程序的页签，其它程序已打开的页签内容保留不替换
        showCurrentConsoleLogTab();
    }
    
    /**
     * 输出行回调（服务读取线程触发）：仅入队，由 Swing Timer 批量刷新
     */
    private void onOutputLine(String line) {
        pendingOutput.offer(line);
    }
    
    /**
     * 状态变化回调（服务监听线程触发）：切回 EDT 刷新状态徽标与按钮
     */
    private void onStateChanged() {
        SwingUtilities.invokeLater(() -> {
            refreshAllStatusLabels();
            updateActionState();
        });
    }
    
    /**
     * 定时批量刷新控制台（200ms），同时作为列表状态徽标的安全网刷新
     */
    private void flushPendingOutput() {
        refreshAllStatusLabels();
        if (pendingOutput.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = pendingOutput.poll()) != null) {
            sb.append(line).append('\n');
        }
        String text = sb.toString();
        consoleArea.append(text);
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        // 全屏日志窗口已打开时同步追加到当前程序的页签
        if (consoleLogDialog != null && consoleLogDialog.isShowing()) {
            consoleLogDialog.appendLog(attachedId, text);
        }
    }
    
    /**
     * 按运行状态驱动按钮与表单启用态
     */
    private void updateActionState() {
        boolean hasSelection = selectedConfig != null;
        boolean running = hasSelection && PROCESS_SERVICE.isRunning(selectedConfig.getId());
        // 接管运行：应用重启后检测到上次启动的进程仍在后台，不能重复启动/重启，可停止与查看日志
        boolean externalRunning = hasSelection && !running && isExternallyRunning(selectedConfig);
        // 内置 HTTP 服务运行中获取访问地址（非 HTTP 服务返回 null）
        String httpUrl = hasSelection ? PROCESS_SERVICE.getHttpServerUrl(selectedConfig.getId()) : null;
        startBtn.setEnabled(hasSelection && !running && !externalRunning);
        stopBtn.setEnabled(running || externalRunning);
        restartBtn.setEnabled(running);
        clearBtn.setEnabled(hasSelection);
        deleteBtn.setEnabled(hasSelection);
        // 导出针对选中程序；导入只能在新增程序（未选中）时把配置载入表单
        exportBtn.setEnabled(hasSelection);
        importBtn.setEnabled(!hasSelection);
        // 全屏日志按程序展示：未选中程序时不可点击
        fullscreenLogBtn.setEnabled(hasSelection);
        openBtn.setEnabled(httpUrl != null);
        // 运行中的程序禁用编辑（含接管运行），防止配置与运行实例不一致
        setFormEditable(!hasSelection || (!running && !externalRunning));
        // 全屏日志窗口已打开时同步启动/停止/重启按钮的启用态
        if (consoleLogDialog != null && consoleLogDialog.isShowing()) {
            consoleLogDialog.updateProgramActions(hasSelection, running, externalRunning);
        }
        
        if (!hasSelection) {
            statusLabel.setText("（未选择程序）");
            statusLabel.setForeground(Color.GRAY);
        } else if (running) {
            String url = PROCESS_SERVICE.getHttpServerUrl(selectedConfig.getId());
            // 内置 HTTP 服务无 PID，展示访问地址更有意义
            statusLabel.setText(url != null ? "运行中 " + url : "运行中 PID=" + PROCESS_SERVICE.getPid(selectedConfig.getId()));
            statusLabel.setForeground(UiConstants.COLOR_SUCCESS_LIGHT);
        } else if (externalRunning) {
            statusLabel.setText("运行中 PID=" + selectedConfig.getLastPid() + "（接管）");
            statusLabel.setForeground(UiConstants.COLOR_PENDING);
        } else {
            Integer exitCode = PROCESS_SERVICE.getExitCode(selectedConfig.getId());
            statusLabel.setText(exitCode != null ? "已退出（退出码=" + exitCode + "）" : "已停止");
            statusLabel.setForeground(Color.GRAY);
        }
    }
    
    /**
     * 绑定快捷键：Ctrl+S 保存程序配置
     */
    private void bindSaveShortcut() {
        KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK, true);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlS, "toggleSaveProgram");
        getActionMap().put("toggleSaveProgram", new AbstractAction() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                saveCurrentForm();
            }
        });
    }
    
    /**
     * 程序列表分组：可折叠标题 + 组内程序项容器；items 与 spacers 一一对应，供搜索过滤与折叠控制可见性
     */
    private static class ProgramGroup {
        
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
        
        ProgramGroup(String name, boolean flat, JPanel header, JLabel arrowLabel, JLabel countLabel, JPanel body) {
            this.name = name;
            this.flat = flat;
            this.header = header;
            this.arrowLabel = arrowLabel;
            this.countLabel = countLabel;
            this.body = body;
        }
    }
    
    /**
     * 宽度跟随滚动容器的列表面板：禁止横向滚动， 保证列表项内的长文本（如程序路径）按实际可用宽度截断显示
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
