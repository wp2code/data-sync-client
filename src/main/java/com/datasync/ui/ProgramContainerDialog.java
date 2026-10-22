package com.datasync.ui;

import com.datasync.components.CustomTextField;
import com.datasync.components.FullscreenJDialog;
import com.datasync.components.OptionJPanel;
import com.datasync.core.ProcessContainerService;
import com.datasync.model.ProgramConfig;
import com.datasync.util.ConfigUtil;
import com.datasync.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * 程序容器对话框
 * <p>
 * 左侧程序列表（状态徽标）+ 右侧配置表单与控制台，支持启动/停止/重启与实时输出查看。
 * 进程会话由 {@link ProcessContainerService} 单例持有：关闭对话框进程继续后台运行，
 * 重新打开可恢复状态徽标与历史日志。
 *
 * @author liuweiping
 * @date 2026-10-19
 **/
public class ProgramContainerDialog extends FullscreenJDialog {
    
    private static final ProcessContainerService PROCESS_SERVICE = ProcessContainerService.getInstance();
    
    private final List<OptionJPanel> programPanelList = new ArrayList<>();
    
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
    
    private JTextField nameField;
    
    private CustomTextField programPathField;

    private CustomTextField argumentsField;
    
    private JTextField workDirField;
    
    private JTextField jrePathField;
    
    private JTextField pythonPathField;
    
    private JTextField remarkField;
    
    private JButton browseProgramBtn;
    
    private JButton browseWorkDirBtn;
    
    private JButton browseJreBtn;
    
    private JButton browsePythonBtn;

    /**
     * 程序类型下拉框：Java 程序 / Python 程序 / 其他程序
     */
    private JComboBox<String> typeComboBox;

    /**
     * 程序配置表单面板：行布局随程序类型动态重建
     */
    private JPanel formPanel;

    /**
     * 表单滚动容器：控制台双击全屏时从分割面板移除、恢复时放回
     */
    private JScrollPane formScroll;

    private JPanel programRow;

    private JPanel workDirRow;

    private JPanel jrePathRow;

    private JPanel pythonPathRow;

    private JPanel formBtnPanel;

    /**
     * 程序类型下拉标签与持久化值（下标一一对应）
     */
    private static final String[] TYPE_LABELS = {"Java 程序", "Python 程序", "其他程序"};

    private static final String[] TYPE_VALUES =
            {ProgramConfig.TYPE_JAVA, ProgramConfig.TYPE_PYTHON, ProgramConfig.TYPE_OTHER};

    /**
     * 程序性设置下拉选中项时，抑制触发表单重建的回调
     */
    private boolean suppressTypeAction;

    private JButton saveBtn;
    
    private JButton deleteBtn;
    
    private JButton startBtn;
    
    private JButton stopBtn;
    
    private JButton restartBtn;
    
    private JButton clearBtn;
    
    private JLabel statusLabel;
    
    private JTextArea consoleArea;
    
    /**
     * 右侧表单/控制台垂直分割：窗口打开时按比例定位（控制台占更大区域）
     */
    private JSplitPane rightSplitPane;
    
    private ProgramConfig selectedConfig;
    
    private OptionJPanel selectedPanel;
    
    /**
     * 当前已向服务注册回调的程序 id（切换选中项时先卸载旧回调）
     */
    private Long attachedId;
    
    /**
     * 控制台批量刷新定时器（200ms）
     */
    private Timer flushTimer;
    
    public ProgramContainerDialog(Frame owner) {
        super("PROGRAM", owner, "程序容器", true, 1000, 840);
        SQLiteConfigUtil.getInstance().initialize();
        initUI();
        refreshProgramList();
        bindFullscreenShortcuts();
        // 关闭对话框仅销毁视图：停止定时器、卸载回调，进程继续后台运行
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            
            @Override
            public void windowOpened(WindowEvent e) {
                // 布局完成后按比例定位分割条：表单约 45%，控制台约 55%
                rightSplitPane.setDividerLocation(0.45);
            }
            
            @Override
            public void windowClosed(WindowEvent e) {
                cleanupOnClose();
            }
        });
        flushTimer = new Timer(200, e -> flushPendingOutput());
        flushTimer.start();
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
        leftTopPanel.add(searchField, BorderLayout.CENTER);
        leftPanel.add(leftTopPanel, BorderLayout.NORTH);
        
        programListPanel = new JPanel();
        programListPanel.setLayout(new BoxLayout(programListPanel, BoxLayout.Y_AXIS));
        programListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        programListPanel.setBackground(UIManager.getColor("Panel.background"));
        programListPanel.setFocusable(true);
        
        JScrollPane listScrollPane = new JScrollPane(programListPanel);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        
        JButton addBtn = ButtonFactory.createPrimary("新建程序");
        addBtn.addActionListener(e -> createNewProgram());
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leftBtnPanel.add(addBtn);
        leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);
        
        // ── 右侧：配置表单 + 控制台 ──
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createFormPanel(), createConsolePanel());
        // 窗口拉伸时额外空间 45% 给表单，其余归控制台，配置项尽量不被压缩
        rightSplitPane.setResizeWeight(0.45);
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
        // 表单置于可滚动容器：程序类型切换后行数变化或窗口较矮时，
        // 配置项可滚动查看，不会被控制台区域遮挡或裁切
        JScrollPane scroll = new JScrollPane(formPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("程序配置"));
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll = scroll;
        return scroll;
    }

    /**
     * 创建表单字段与行组件（仅初始化，不做布局）；行布局由 {@link #rebuildFormPanel()} 按程序类型动态生成
     */
    private void createFormFields() {
        typeComboBox = new JComboBox<>(TYPE_LABELS);
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
        workDirField = new JTextField();
        jrePathField = new JTextField();
        jrePathField.setToolTipText("可选：JDK/JRE 根目录（自动定位 bin/java）或直接指向 java 可执行文件；留空使用系统 PATH。仅对 java/javaw 命令生效");
        pythonPathField = new CustomTextField("如：D:\\proj\\venv 或 C:\\Python311\\python.exe");
        pythonPathField.setToolTipText("可选：Python 安装根目录、虚拟环境目录（自动定位 Scripts/python.exe 或 bin/python）"
                + "或直接指向 python 可执行文件；留空使用系统 PATH。对 python/python3 命令及 .py 入口脚本生效");
        remarkField = new JTextField();
        JTextField[] fields = {nameField, programPathField, argumentsField, workDirField, jrePathField, pythonPathField,
                remarkField};
        for (JTextField field : fields) {
            field.setFont(UiConstants.FONT_MONO_12);
        }
        updateTypeExamples();

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

        // 按钮行：删除 + 保存
        saveBtn = ButtonFactory.createPrimary("保存");
        saveBtn.addActionListener(e -> saveCurrentForm());
        deleteBtn = ButtonFactory.createDestructive("删除");
        deleteBtn.addActionListener(e -> {
            if (selectedConfig != null) {
                deleteProgram(selectedConfig);
            }
        });
        formBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        formBtnPanel.add(deleteBtn);
        formBtnPanel.add(saveBtn);
    }

    /**
     * 按当前程序类型重建表单行：Java 只显示 JRE/JDK、Python 只显示解释器行，
     * 避免两组环境字段同时堆叠导致布局拥挤
     */
    private void rebuildFormPanel() {
        formPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        // 加大行距，表单整体更宽松
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        List<JComponent[]> rows = new ArrayList<>();
        rows.add(new JComponent[]{formLabel("程序类型:"), typeComboBox});
        rows.add(new JComponent[]{formLabel("名称:"), nameField});
        rows.add(new JComponent[]{formLabel("程序/命令:"), programRow});
        rows.add(new JComponent[]{formLabel("参数:"), argumentsField});
        rows.add(new JComponent[]{formLabel("工作目录:"), workDirRow});
        if (ProgramConfig.TYPE_JAVA.equals(selectedTypeValue())) {
            rows.add(new JComponent[]{formLabel("JRE/JDK:"), jrePathRow});
        } else if (ProgramConfig.TYPE_PYTHON.equals(selectedTypeValue())) {
            rows.add(new JComponent[]{formLabel("Python:"), pythonPathRow});
        }
        rows.add(new JComponent[]{formLabel("备注:"), remarkField});

        int row = 0;
        for (JComponent[] cells : rows) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            formPanel.add(cells[0], gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            formPanel.add(cells[1], gbc);
            row++;
        }
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        formPanel.add(formBtnPanel, gbc);

        formPanel.revalidate();
        formPanel.repaint();
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
                programPathField.setToolTipText("填 Python 项目入口脚本路径（如 D:\\proj\\main.py），"
                        + "启动时自动前置配置的解释器（未配置则用系统 PATH 的 python）；"
                        + "也可填完整命令行，如 python -m uvicorn app:app --port 8000");
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
                programPathField.setToolTipText("可填完整命令行（首词为程序，如 D:\\server\\startup.bat）"
                        + "或单个程序/脚本路径，参数填到「参数」框；含空格的段用双引号包裹");
            }
        }
        argumentsField.setToolTipText("传给程序的运行参数，多个用空格分隔；"
                + (ProgramConfig.TYPE_JAVA.equals(selectedTypeValue())
                        ? "JVM 参数（如 -Xms512m -Dfile.encoding=UTF-8）需写在「程序/命令」中，置于 -jar 之前"
                        : "Python 项目的入口脚本可直接写在「程序/命令」中，此处填脚本参数"));
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
        btnPanel.add(startBtn);
        btnPanel.add(stopBtn);
        btnPanel.add(restartBtn);
        btnPanel.add(clearBtn);
        
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
        consoleArea.setToolTipText("双击全屏展示控制台，再次双击恢复");
        attachConsolePopupMenu();
        attachConsoleFullscreenToggle();
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        consolePanel.add(consoleToolbar, BorderLayout.NORTH);
        consolePanel.add(consoleScroll, BorderLayout.CENTER);
        
        return consolePanel;
    }
    
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
                JMenuItem copyItem = new JMenuItem("一键复制");
                copyItem.addActionListener(ev -> {
                    consoleArea.selectAll();
                    consoleArea.copy();
                    consoleArea.setCaretPosition(0);
                    JOptionPane.showMessageDialog(ProgramContainerDialog.this, "控制台内容已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
                });
                JMenuItem clearItem = new JMenuItem("清空控制台");
                clearItem.addActionListener(ev -> clearConsole());
                popup.add(copyItem);
                popup.add(clearItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }
    
    /**
     * 控制台是否处于双击全屏状态（表单区收起）
     */
    private boolean consoleFullscreen;

    /**
     * 控制台双击切换全屏展示：收起表单区（分割条拉到底），控制台占满窗口；再次双击恢复表单占比。
     * 手动拖动分割条后双击仍按标志切换，展示/恢复交替生效
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
     * 双击控制台切换全屏展示：直接移除表单区让控制台占满整个右侧区域；
     * 再双击放回表单并恢复约 45% 的占比。
     * 不用 setDividerLocation(1.0)——它受表单最小尺寸 clamp 且在布局变化后可能被重置，导致全屏不生效
     */
    private void toggleConsoleFullscreen() {
        consoleFullscreen = !consoleFullscreen;
        if (consoleFullscreen) {
            rightSplitPane.setTopComponent(null);
        } else {
            rightSplitPane.setTopComponent(formScroll);
            rightSplitPane.setDividerLocation(0.45);
        }
    }

    // ────────── 程序列表 ──────────
    
    private void refreshProgramList() {
        programListPanel.removeAll();
        programPanelList.clear();
        statusLabels.clear();
        
        List<ProgramConfig> configs = ConfigUtil.loadProgramConfigs();
        if (configs.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无程序）", SwingConstants.CENTER);
            emptyLabel.setFont(UiConstants.FONT_SANS_12);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            programListPanel.add(emptyLabel);
        } else {
            for (ProgramConfig config : configs) {
                OptionJPanel panel = createProgramPanel(config);
                programPanelList.add(panel);
                programListPanel.add(panel);
                programListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        
        filterProgramList();
        refreshAllStatusLabels();
        programListPanel.revalidate();
        programListPanel.repaint();
    }
    
    private void filterProgramList() {
        String keyword = searchField.getText().trim().toLowerCase();
        boolean anyVisible = false;
        for (int i = 0; i < programPanelList.size(); i++) {
            OptionJPanel panel = programPanelList.get(i);
            ProgramConfig config = (ProgramConfig) panel.getData();
            boolean visible = keyword.isEmpty() || config.getName().toLowerCase().contains(keyword);
            panel.setVisible(visible);
            // 同时控制分隔间距的可见性
            int spacerIndex = i * 2 + 1;
            if (spacerIndex < programListPanel.getComponentCount()) {
                programListPanel.getComponent(spacerIndex).setVisible(visible);
            }
            if (visible) {
                anyVisible = true;
            }
        }
        
        // 处理空提示
        for (Component comp : programListPanel.getComponents()) {
            if (comp instanceof JLabel label && "（暂无程序）".equals(label.getText())) {
                comp.setVisible(!anyVisible && keyword.isEmpty());
            }
        }
        programListPanel.revalidate();
        programListPanel.repaint();
    }
    
    private OptionJPanel createProgramPanel(ProgramConfig config) {
        OptionJPanel panel = new OptionJPanel(config.getName(), config.getProgramPath(), null);
        panel.setData(config);
        panel.setToolTipText(config.getProgramPath());
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
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
        return panel;
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
                JMenuItem editItem = new JMenuItem("编 辑");
                JMenuItem deleteItem = new JMenuItem("删 除");
                editItem.addActionListener(ev -> selectProgram(config, panel));
                deleteItem.addActionListener(ev -> deleteProgram(config));
                popup.add(editItem);
                popup.add(deleteItem);
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
        return !PROCESS_SERVICE.isRunning(config.getId())
                && config.getLastPid() != null
                && ProcessContainerService.isPidAlive(config.getLastPid());
    }
    
    // ────────── 表单编辑 ──────────
    
    private void fillFormFromConfig(ProgramConfig config) {
        applyTypeSelection(config.resolveProgramType());
        nameField.setText(config.getName());
        programPathField.setText(config.getProgramPath());
        argumentsField.setText(config.getArguments());
        workDirField.setText(config.getWorkDir());
        jrePathField.setText(config.getJrePath());
        pythonPathField.setText(config.getPythonPath());
        remarkField.setText(config.getRemark());
    }
    
    private void clearForm() {
        applyTypeSelection(ProgramConfig.TYPE_JAVA);
        nameField.setText("");
        programPathField.setText("");
        argumentsField.setText("");
        workDirField.setText("");
        jrePathField.setText("");
        pythonPathField.setText("");
        remarkField.setText("");
    }
    
    private void setFormEditable(boolean editable) {
        typeComboBox.setEnabled(editable);
        nameField.setEditable(editable);
        programPathField.setEditable(editable);
        argumentsField.setEditable(editable);
        workDirField.setEditable(editable);
        jrePathField.setEditable(editable);
        pythonPathField.setEditable(editable);
        remarkField.setEditable(editable);
        browseProgramBtn.setEnabled(editable);
        browseWorkDirBtn.setEnabled(editable);
        browseJreBtn.setEnabled(editable);
        browsePythonBtn.setEnabled(editable);
        saveBtn.setEnabled(editable);
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
     * 保存当前表单：新建程序插入并回填 id，已有程序更新；成功后刷新列表并保持选中。 校验失败或保存失败时弹出提示并返回 false。
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
        ProgramConfig sameName = ConfigUtil.loadProgramConfigByName(name);
        if (selectedConfig != null) {
            // 更新已有程序
            if (sameName != null && !sameName.getId().equals(selectedConfig.getId())) {
                JOptionPane.showMessageDialog(this, "已存在同名程序，请重新命名", "错误", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            selectedConfig.setName(name);
            selectedConfig.setProgramPath(programPath);
            selectedConfig.setProgramType(selectedTypeValue());
            selectedConfig.setArguments(argumentsField.getText().trim());
            selectedConfig.setWorkDir(workDirField.getText().trim());
            selectedConfig.setJrePath(jrePathField.getText().trim());
            selectedConfig.setPythonPath(pythonPathField.getText().trim());
            selectedConfig.setRemark(remarkField.getText().trim());
            if (!ConfigUtil.updateProgramConfig(selectedConfig)) {
                JOptionPane.showMessageDialog(this, "更新程序配置失败", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        } else {
            // 新建程序
            if (sameName != null) {
                JOptionPane.showMessageDialog(this, "已存在同名程序，请重新命名", "错误", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            ProgramConfig config = new ProgramConfig(name, programPath, argumentsField.getText().trim());
            config.setProgramType(selectedTypeValue());
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
    
    private void browseProgramPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择程序或脚本");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
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
    
    private void clearConsole() {
        pendingOutput.clear();
        consoleArea.setText("");
        if (selectedConfig != null) {
            PROCESS_SERVICE.clearLog(selectedConfig.getId());
        }
    }
    
    private void deleteProgram(ProgramConfig config) {
        boolean running = PROCESS_SERVICE.isRunning(config.getId()) || isExternallyRunning(config);
        String message = running
                ? "程序 [" + config.getName() + "] 正在运行，删除将停止该程序，确认删除？"
                : "确认删除程序 [" + config.getName() + "]？";
        if (JOptionPane.showConfirmDialog(this, message, "删除确认", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
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
     * 切换控制台监听目标：卸载旧程序回调 → 丢弃未刷新的旧输出 → 恢复新程序历史日志并注册回调。
     * 接管运行中的进程先建立外部会话并 tail 日志文件，历史与实时输出均可查看
     */
    private void attachConsoleListener(Long id) {
        if (attachedId != null) {
            PROCESS_SERVICE.detachListener(attachedId);
        }
        pendingOutput.clear();
        attachedId = id;
        if (id != null) {
            if (selectedConfig != null && selectedConfig.getId().equals(id)
                    && isExternallyRunning(selectedConfig)) {
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
        consoleArea.append(sb.toString());
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }
    
    /**
     * 按运行状态驱动按钮与表单启用态
     */
    private void updateActionState() {
        boolean hasSelection = selectedConfig != null;
        boolean running = hasSelection && PROCESS_SERVICE.isRunning(selectedConfig.getId());
        // 接管运行：应用重启后检测到上次启动的进程仍在后台，不能重复启动/重启，可停止与查看日志
        boolean externalRunning = hasSelection && !running && isExternallyRunning(selectedConfig);
        startBtn.setEnabled(hasSelection && !running && !externalRunning);
        stopBtn.setEnabled(running || externalRunning);
        restartBtn.setEnabled(running);
        clearBtn.setEnabled(hasSelection);
        deleteBtn.setEnabled(hasSelection);
        // 运行中的程序禁用编辑（含接管运行），防止配置与运行实例不一致
        setFormEditable(!hasSelection || (!running && !externalRunning));
        
        if (!hasSelection) {
            statusLabel.setText("（未选择程序）");
            statusLabel.setForeground(Color.GRAY);
        } else if (running) {
            statusLabel.setText("运行中 PID=" + PROCESS_SERVICE.getPid(selectedConfig.getId()));
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
     * 对话框关闭清理：停止定时器、卸载服务回调（进程继续后台运行）
     */
    private void cleanupOnClose() {
        if (flushTimer != null) {
            flushTimer.stop();
        }
        if (attachedId != null) {
            PROCESS_SERVICE.detachListener(attachedId);
            attachedId = null;
        }
    }
    
    /**
     * 绑定快捷键：Ctrl+S 保存程序配置
     */
    private void bindFullscreenShortcuts() {
        KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK, true);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlS, "toggleSaveProgram");
        getRootPane().getActionMap().put("toggleSaveProgram", new AbstractAction() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                saveCurrentForm();
            }
        });
    }
}
