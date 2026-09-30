/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.ui;

import com.datasync.components.ChildLayoutPanel;
import com.datasync.components.FullscreenJDialog;
import com.datasync.core.AiQuestionApiClient;
import com.datasync.model.AiEnvConfig;
import com.datasync.util.ConfigUtil;
import com.datasync.util.LogUtil;
import com.datasync.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 问题管理对话框（业务页签切换：问题训练 / 回复审计）
 * <p>
 * 本类作为主对话框壳，负责环境选择、页签切换、共享工具方法与共享内部类（渲染器/编辑器）。<br> 问题训练功能由 {@link QuestionTrainingPanel} 实现，回复审计功能由 {@link AnswerAuditPanel} 实现。
 *
 * @author liuweiping
 * @date 2026-09-16
 **/
public class AiQuestionMangerDialog extends FullscreenJDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(AiQuestionMangerDialog.class);
    
    // ── 共享常量 ──
    
    /**
     * 默认分页大小
     */
    static final int DEFAULT_PAGE_SIZE = 300;
    
    /**
     * 分页范围
     */
    static final String[] DEFAULT_PAGE_SCOPE = new String[] {"500", "300", "100", "50", "20", "10"};
    
    /**
     * 每页条数手动输入下限
     */
    static final int MIN_PAGE_SIZE = 1;
    
    /**
     * 每页条数手动输入上限
     */
    static final int MAX_PAGE_SIZE = 10000;
    
    /**
     * 详情 / 编辑弹窗文本滚动区的统一首选宽度
     */
    static final int DIALOG_SCROLL_WIDTH = 700;
    
    /**
     * 复选框列固定宽度
     */
    static final int CHECK_COLUMN_WIDTH = 46;
    
    // ── 操作列动作区域 ──
    static final int ACTION_VIEW = 0;
    
    static final int ACTION_EDIT = 1;
    
    static final int ACTION_TRAIN = 2;
    
    static final int ACTION_DELETE = 3;
    
    /**
     * 回复审计操作列删除区域值
     */
    static final int ANSWER_ACTION_DELETE = 2;
    
    // ── 业务页签 ──
    private static final int TAB_QUESTION_TRAINING = 0;
    
    private static final int TAB_ANSWER_AUDIT = 1;
    
    /**
     * 训练时间展示格式
     */
    private static final DateTimeFormatter TRAINING_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 训练时间仅展示时分秒格式
     */
    private static final DateTimeFormatter TRAINING_TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * 训练状态筛选项文字
     */
    private static final String[] TRAINING_STATUS_TEXTS = {"默认", "待训练", "成功", "失败", "同步回复成功", "训练中", "超时", "同步回复失败",
            "训练已提交"};
    
    // ── 共享字段（面板通过 dialog 引用访问）──
    
    JComboBox<AiEnvConfig> envCombo;
    
    private boolean suppressEnvEvents = false;
    
    List<String> userOptions = new ArrayList<>();
    
    List<String[]> projectOptions = new ArrayList<>();
    
    List<String> classifySuggestions = new ArrayList<>();
    
    boolean apiBusy = false;
    
    boolean pendingQuestionReload = false;
    
    boolean pendingAnswerReload = false;
    
    // ── 面板引用 ──
    private QuestionTrainingPanel questionTrainingPanel;
    
    private AnswerAuditPanel answerAuditPanel;
    
    // ── UI 组件 ──
    private JTabbedPane businessTabs;
    
    private JLabel statusLabel;
    
    // ── 保存按钮引用（setSaveButtonsEnabled 需要）──
    private JButton manualSaveBtn;
    
    private JButton pasteSaveBtn;
    
    private JButton importExcelBtn;
    
    /**
     * 主窗口引用
     */
    private final Frame ownerFrame;
    
    public AiQuestionMangerDialog(Frame owner) {
        super("AIQUESTION", owner, "AI 问题管理", true, 1280, 780);
        this.ownerFrame = owner;
        SQLiteConfigUtil.getInstance().initialize();
        initUI();
        refreshEnvCombo();
        refreshUserOptions();
        refreshProjectOptions();
        refreshQuestionTable();
    }
    
    // ────────── UI 初始化 ──────────
    
    /**
     * 供面板调用的全屏切换公开方法（父类 toggleFullscreen 为 protected）
     */
    public void toggleFullscreenDialog() {
        toggleFullscreen();
    }
    
    private final MouseAdapter doubleClickFullscreenListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                toggleFullscreen();
            }
        }
    };
    
    private void bindDoubleClickFullscreen(Component component) {
        if (!isDoubleClickExcluded(component)) {
            component.addMouseListener(doubleClickFullscreenListener);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                bindDoubleClickFullscreen(child);
            }
        }
    }
    
    private static boolean isDoubleClickExcluded(Component component) {
        return component instanceof JTable || component instanceof JTextComponent || component instanceof JComboBox
                || component instanceof AbstractButton || component instanceof JScrollBar || component instanceof JList
                || component instanceof JTabbedPane || component instanceof JTableHeader || component.getClass().getName().contains("Divider");
    }
    
    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        getRootPane().setBorder(new EmptyBorder(10, 12, 10, 12));
        
        // ── 顶栏：环境选择 ──
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        JLabel tipLabel = new JLabel("选择环境后切换页签进行问题训练与回复审计（列表仅展示当前环境数据）");
        tipLabel.setForeground(Color.GRAY);
        tipLabel.setToolTipText("双击此处可全屏 / 退出全屏（F11 / Esc）");
        headerPanel.setToolTipText(tipLabel.getToolTipText());
        headerPanel.add(tipLabel, BorderLayout.WEST);
        
        envCombo = new JComboBox<>();
        envCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getEnvName() + "（" + value.getHost() + "）");
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
                label.setOpaque(true);
            }
            return label;
        });
        envCombo.setPreferredSize(new Dimension(260, 28));
        envCombo.addActionListener(e -> onEnvSelectionChanged());
        JButton manageEnvBtn = ButtonFactory.createToolbar("管理环境");
        manageEnvBtn.addActionListener(e -> openEnvManageDialog());
        ChildLayoutPanel envPanel = new ChildLayoutPanel(new Insets(0, 4, 0, 4), ChildLayoutPanel.LayoutType.RIGHT);
        envPanel.add(new JLabel("环境："));
        envPanel.add(envCombo);
        envPanel.add(manageEnvBtn);
        headerPanel.add(envPanel, BorderLayout.EAST);
        
        // ── 业务页签 ──
        businessTabs = new JTabbedPane();
        questionTrainingPanel = new QuestionTrainingPanel(this);
        answerAuditPanel = new AnswerAuditPanel(this);
        businessTabs.addTab("问题训练", questionTrainingPanel.buildQuestionTrainingPanel());
        businessTabs.addTab("回复审计", answerAuditPanel.buildAnswerAuditPanel());
        businessTabs.addChangeListener(e -> onBusinessTabChanged());
        
        // 同步保存按钮引用（面板构建后才可用）
        manualSaveBtn = questionTrainingPanel.getManualSaveBtn();
        pasteSaveBtn = questionTrainingPanel.getPasteSaveBtn();
        importExcelBtn = questionTrainingPanel.getImportExcelBtn();
        
        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(businessTabs, BorderLayout.CENTER);
        add(topPanel, BorderLayout.CENTER);
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        statusLabel.setToolTipText("双击空白区域可全屏 / 退出全屏（F11 / Esc）");
        add(statusLabel, BorderLayout.SOUTH);
        
        bindDoubleClickFullscreen(this);
    }
    
    private void onBusinessTabChanged() {
        refreshActiveBusinessTab();
    }
    
    private void refreshActiveBusinessTab() {
        if (businessTabs != null && businessTabs.getSelectedIndex() == TAB_ANSWER_AUDIT) {
            refreshAnswerTable();
        } else {
            refreshQuestionTable();
        }
    }
    
    // ────────── 环境选择 ──────────
    
    private void openEnvManageDialog() {
        new AiEnvMangerDialog(ownerFrame).setVisible(true);
        refreshEnvCombo();
        refreshUserOptions();
        refreshProjectOptions();
        invalidateLoadedData();
        invalidateAnswers();
        refreshActiveBusinessTab();
    }
    
    private void refreshEnvCombo() {
        List<AiEnvConfig> configs = ConfigUtil.loadAiEnvConfigs();
        String previousName = envCombo.getSelectedItem() instanceof AiEnvConfig config ? config.getEnvName() : null;
        String globalSelectedName = null;
        for (AiEnvConfig config : configs) {
            if (Boolean.TRUE.equals(config.getSelected())) {
                globalSelectedName = config.getEnvName();
                break;
            }
        }
        suppressEnvEvents = true;
        try {
            envCombo.removeAllItems();
            for (AiEnvConfig config : configs) {
                envCombo.addItem(config);
            }
            if (!configs.isEmpty()) {
                String preferName = globalSelectedName != null ? globalSelectedName : previousName;
                boolean restored = false;
                if (preferName != null) {
                    for (AiEnvConfig config : configs) {
                        if (config.getEnvName().equals(preferName)) {
                            envCombo.setSelectedItem(config);
                            restored = true;
                            break;
                        }
                    }
                }
                if (!restored) {
                    envCombo.setSelectedIndex(0);
                }
            }
        } finally {
            suppressEnvEvents = false;
        }
    }
    
    private void onEnvSelectionChanged() {
        if (suppressEnvEvents) {
            return;
        }
        persistSelectedEnv();
        invalidateLoadedData();
        invalidateAnswers();
        refreshUserOptions();
        refreshProjectOptions();
        refreshActiveBusinessTab();
    }
    
    private void persistSelectedEnv() {
        if (!(envCombo.getSelectedItem() instanceof AiEnvConfig config) || Boolean.TRUE.equals(config.getSelected())) {
            return;
        }
        if (!ConfigUtil.updateSelectedAiEnv(config.getId())) {
            logger.error("持久化选中环境 [{}] 失败", config.getEnvName());
            setStatus("选中环境保存失败：" + config.getEnvName() + "（不影响当前使用）", false);
            return;
        }
        for (int i = 0; i < envCombo.getItemCount(); i++) {
            envCombo.getItemAt(i).setSelected(false);
        }
        config.setSelected(true);
    }
    
    AiEnvConfig requireSelectedEnvConfig() {
        Object selected = envCombo.getSelectedItem();
        if (!(selected instanceof AiEnvConfig config)) {
            JOptionPane.showMessageDialog(this, "请先在右上角选择问题保存环境", "提示", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return config;
    }
    
    AiEnvConfig findEnvByName(String envName) {
        if (envName == null) {
            return null;
        }
        for (AiEnvConfig config : ConfigUtil.loadAiEnvConfigs()) {
            if (envName.equals(config.getEnvName())) {
                return config;
            }
        }
        return null;
    }
    
    // ────────── 共享数据刷新 ──────────
    
    private void refreshUserOptions() {
        Object selected = envCombo.getSelectedItem();
        if (!(selected instanceof AiEnvConfig envConfig)) {
            return;
        }
        new Thread(() -> {
            List<String> fetched;
            try {
                fetched = AiQuestionApiClient.getInstance().listUsers(envConfig);
            } catch (AiQuestionApiClient.AiApiException e) {
                logger.warn("[AiApi] 获取用户列表失败: {}", e.getMessage());
                fetched = new ArrayList<>();
            }
            final List<String> users = fetched;
            SwingUtilities.invokeLater(() -> {
                userOptions.clear();
                userOptions.addAll(users);
                questionTrainingPanel.refreshUserOptionsInPanel();
                answerAuditPanel.refreshUserOptionsInPanel();
            });
        }, "refresh-user-options").start();
    }
    
    private void refreshProjectOptions() {
        Object selected = envCombo.getSelectedItem();
        if (!(selected instanceof AiEnvConfig envConfig)) {
            return;
        }
        new Thread(() -> {
            List<String[]> fetched;
            try {
                fetched = AiQuestionApiClient.getInstance().listProjects(envConfig);
            } catch (AiQuestionApiClient.AiApiException e) {
                logger.warn("[AiApi] 获取项目列表失败: {}", e.getMessage());
                fetched = new ArrayList<>();
            }
            final List<String[]> projects = fetched;
            SwingUtilities.invokeLater(() -> {
                projectOptions = projects;
                questionTrainingPanel.refreshProjectOptionsInPanel();
            });
        }, "refresh-project-options").start();
    }
    
    // ────────── 面板委托方法 ──────────
    
    void refreshQuestionTable() {
        if (questionTrainingPanel != null) {
            questionTrainingPanel.refreshQuestionTable();
        }
    }
    
    void refreshAnswerTable() {
        if (answerAuditPanel != null) {
            answerAuditPanel.refreshAnswerTable();
        }
    }
    
    void invalidateLoadedData() {
        if (questionTrainingPanel != null) {
            questionTrainingPanel.invalidateCache();
        }
    }
    
    void invalidateAnswers() {
        if (answerAuditPanel != null) {
            answerAuditPanel.invalidateCache();
        }
    }
    
    // ────────── 共享工具方法 ──────────
    
    static String nullToEmpty(String text) {
        return text != null ? text : "";
    }
    
    static String abbreviate(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String flattened = text.replace("\r", "").replace("\n", " ").trim();
        return flattened.length() <= maxLength ? flattened : flattened.substring(0, maxLength) + "…";
    }
    
    static String formatEpochMillis(Long millis) {
        if (millis == null || millis <= 0) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(TRAINING_TIME_FORMATTER);
    }
    
    /**
     * 将毫秒时间戳格式化为 HH:mm:ss（仅展示时间部分）
     */
    static String formatEpochMillisTime(Long millis) {
        if (millis == null || millis <= 0) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(TRAINING_TIME_ONLY_FORMATTER);
    }
    
    static String trainingStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        int index = status + 2;
        if (index < 0 || index >= TRAINING_STATUS_TEXTS.length) {
            return "";
        }
        return TRAINING_STATUS_TEXTS[index];
    }
    
    static Color trainingStatusColor(Integer status) {
        if (status == null) {
            return Color.GRAY;
        }
        return switch (status) {
            case com.datasync.model.AiQuestion.TRAINING_STATUS_INITIAL -> Color.GRAY;
            case com.datasync.model.AiQuestion.TRAINING_STATUS_PENDING -> UiConstants.COLOR_PENDING;
            case com.datasync.model.AiQuestion.TRAINING_STATUS_SUCCESS, com.datasync.model.AiQuestion.TRAINING_STATUS_SUCCESS_ANSWER ->
                    UiConstants.COLOR_SUCCESS;
            case com.datasync.model.AiQuestion.TRAINING_STATUS_FAILED, com.datasync.model.AiQuestion.TRAINING_STATUS_TIMEOUT,
                 com.datasync.model.AiQuestion.TRAINING_STATUS_SYNC_FAILED -> UiConstants.COLOR_DANGER;
            case com.datasync.model.AiQuestion.TRAINING_STATUS_RUNNING -> UiConstants.COLOR_PRIMARY;
            case com.datasync.model.AiQuestion.TRAINING_STATUS_SUBMITTED -> UiConstants.COLOR_LINK;
            default -> Color.GRAY;
        };
    }
    
    static String trainingCostText(com.datasync.model.AiQuestion question) {
        Long start = question.getStartTrainingTime();
        Long last = question.getLastTrainingTime();
        if (start == null || last == null || start <= 0 || last <= start) {
            return "";
        }
        return String.format("%.1f", (last - start) / 1000.0);
    }
    
    static String extractUserIdFromCombo(Object selectedItem) {
        if (selectedItem == null) {
            return "";
        }
        String text = selectedItem.toString().trim();
        if (text.isEmpty() || "同上".equals(text)) {
            return text;
        }
        int dashIndex = text.indexOf(" - ");
        return dashIndex > 0 ? text.substring(0, dashIndex).trim() : text;
    }
    
    static String formatUserOption(String userOption) {
        if (userOption == null) {
            return "";
        }
        int dashIndex = userOption.indexOf(" - ");
        if (dashIndex > 0) {
            String userId = userOption.substring(0, dashIndex).trim();
            String userName = userOption.substring(dashIndex + 3).trim();
            return userName + "（" + userId + "）";
        }
        return userOption;
    }
    
    /**
     * 根据原始 userId 查找用户名称并格式化为「用户名称（用户ID）」
     */
    static String formatUserIdDisplay(String userId, List<String> userOptions) {
        if (userId == null || userId.isEmpty()) {
            return "";
        }
        if (userOptions != null) {
            for (String option : userOptions) {
                if (option == null) {
                    continue;
                }
                String extracted = extractUserIdFromCombo(option);
                if (userId.equals(extracted)) {
                    int dashIndex = option.indexOf(" - ");
                    if (dashIndex > 0) {
                        String userName = option.substring(dashIndex + 3).trim();
                        return userName + "（" + userId + "）";
                    }
                    return userId;
                }
            }
        }
        return userId;
    }
    
    static String extractUserIdFromDisplayText(String displayText) {
        if (displayText == null) {
            return "";
        }
        int leftParen = displayText.lastIndexOf("（");
        int rightParen = displayText.lastIndexOf("）");
        if (leftParen > 0 && rightParen > leftParen) {
            return displayText.substring(leftParen + 1, rightParen).trim();
        }
        return displayText.trim();
    }
    
    String editableComboText(JComboBox<String> combo) {
        Object editorItem = combo.getEditor().getItem();
        return editorItem != null ? editorItem.toString() : "";
    }
    
    static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
    
    static String answerModifyText(String allowModify) {
        if (allowModify == null || allowModify.isBlank()) {
            return "";
        }
        return switch (allowModify.trim()) {
            case "1", "true" -> "允许修改";
            case "0", "false" -> "不允许修改";
            default -> allowModify;
        };
    }
    
    static String projectButtonLabel(List<String> selectedCodes) {
        if (selectedCodes == null || selectedCodes.isEmpty()) {
            return "请选择项目（可留空）";
        }
        return "已选 " + selectedCodes.size() + " 个项目";
    }
    
    void openOutputFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ex) {
            logger.warn("打开文件失败: {}", file.getAbsolutePath(), ex);
        }
    }
    
    Map<String, String> buildUserIdDisplayMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String userOption : userOptions) {
            int dashIndex = userOption.indexOf(" - ");
            if (dashIndex > 0) {
                String userId = userOption.substring(0, dashIndex).trim();
                String userName = userOption.substring(dashIndex + 3).trim();
                map.put(userId, userName + "（" + userId + "）");
            }
        }
        return map;
    }
    
    // ────────── 共享 UI 工具 ──────────
    
    void addFormRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(field, gbc);
    }
    
    static JTextField readonlyField(String text) {
        JTextField field = new JTextField(text, 24);
        field.setEditable(false);
        return field;
    }
    
    static JScrollPane readonlyArea(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setToolTipText("可拖选文字复制，或右键全选 / 复制");
        attachCopyMenu(area);
        return dialogScroll(area, rows);
    }
    
    private static void attachCopyMenu(JTextComponent textComponent) {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制");
        JMenuItem selectAllItem = new JMenuItem("全选");
        copyItem.addActionListener(e -> textComponent.copy());
        selectAllItem.addActionListener(e -> textComponent.selectAll());
        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                copyItem.setEnabled(textComponent.getSelectedText() != null);
            }
            
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }
            
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        popupMenu.add(copyItem);
        popupMenu.addSeparator();
        popupMenu.add(selectAllItem);
        textComponent.setComponentPopupMenu(popupMenu);
    }
    
    static JScrollPane dialogScroll(JTextArea area, int rows) {
        Font font = area.getFont() != null ? area.getFont() : UiConstants.FONT_SANS_12;
        int lineHeight = area.getFontMetrics(font).getHeight();
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(DIALOG_SCROLL_WIDTH, Math.max(rows, 1) * lineHeight + 6));
        return scroll;
    }
    
    TableCellRenderer centeredRenderer() {
        return (table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value == null ? "" : value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        };
    }
    
    static int actionZoneAt(int x, Rectangle cellRect, int zoneCount) {
        double zoneWidth = cellRect.width / (double) zoneCount;
        int zone = (int) ((x - cellRect.x) / zoneWidth);
        return Math.max(0, Math.min(zone, zoneCount - 1));
    }
    
    List<String> showProjectMultiSelectDialog(String title, List<String> currentSelected) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        List<JCheckBox> checkBoxes = new ArrayList<>();
        for (String[] project : projectOptions) {
            String code = project[0];
            String name = project[1];
            JCheckBox cb = new JCheckBox(code + (name.isEmpty() ? "" : " - " + name));
            cb.setSelected(currentSelected.contains(code));
            checkBoxes.add(cb);
            panel.add(cb);
        }
        if (projectOptions.isEmpty()) {
            panel.add(new JLabel("（暂无项目选项，请先确认环境已配置获取项目接口）"));
        }
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(380, Math.min(60 + checkBoxes.size() * 28, 360)));
        int result = JOptionPane.showConfirmDialog(this, scroll, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected()) {
                selected.add(projectOptions.get(i)[0]);
            }
        }
        return selected;
    }
    
    // ────────── API 任务框架 ──────────
    
    void setStatus(String message, boolean success) {
        statusLabel.setText(LogUtil.logTime() + message);
        statusLabel.setForeground(success ? UiConstants.COLOR_SUCCESS : UiConstants.COLOR_ERROR);
    }
    
    void runApiTask(String actionName, ApiCall apiCall) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        apiBusy = true;
        setSaveButtonsEnabled(false);
        setStatus("正在" + actionName + "…", true);
        new Thread(() -> {
            try {
                Runnable afterSuccess = apiCall.call();
                SwingUtilities.invokeLater(() -> {
                    apiBusy = false;
                    setSaveButtonsEnabled(true);
                    afterSuccess.run();
                    resumePendingReloads();
                });
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("{}失败", actionName, ex);
                SwingUtilities.invokeLater(() -> {
                    apiBusy = false;
                    setSaveButtonsEnabled(true);
                    String reason = ex.getMessage();
                    setStatus(actionName + "失败：" + reason, false);
                    JOptionPane.showMessageDialog(this, actionName + "失败：" + reason, "错误", JOptionPane.ERROR_MESSAGE);
                    resumePendingReloads();
                });
            }
        }, "ai-api-task").start();
    }
    
    void setSaveButtonsEnabled(boolean enabled) {
        if (manualSaveBtn != null) {
            manualSaveBtn.setEnabled(enabled);
        }
        if (pasteSaveBtn != null) {
            pasteSaveBtn.setEnabled(enabled);
        }
        if (importExcelBtn != null) {
            importExcelBtn.setEnabled(enabled);
        }
    }
    
    void resumePendingReloads() {
        if (apiBusy) {
            return;
        }
        if (pendingQuestionReload) {
            pendingQuestionReload = false;
            refreshQuestionTable();
        }
        if (pendingAnswerReload) {
            pendingAnswerReload = false;
            refreshAnswerTable();
        }
    }
    
    @FunctionalInterface
    interface ApiCall {
        
        Runnable call() throws AiQuestionApiClient.AiApiException;
    }
    
    // ────────── 共享内部类：渲染器 / 编辑器 ──────────
    
    /**
     * 所属用户单元格渲染器
     */
    static class UserIdCellRenderer extends DefaultTableCellRenderer {
        
        private final List<String> userOptions;
        
        UserIdCellRenderer(List<String> userOptions) {
            this.userOptions = userOptions;
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String userId = value == null ? "" : value.toString().trim();
            if (userId.isEmpty()) {
                setText("");
                setToolTipText(null);
            } else {
                String userName = lookupUserName(userId);
                String display = userName.isEmpty() ? userId : userName + "（" + userId + "）";
                setText(display);
                setToolTipText(display);
            }
            return component;
        }
        
        private String lookupUserName(String userId) {
            if (userId == null || userId.isEmpty()) {
                return "";
            }
            for (String option : userOptions) {
                if (option == null) {
                    continue;
                }
                String extracted = extractUserIdFromCombo(option);
                if (userId.equals(extracted)) {
                    int dashIndex = option.indexOf(" - ");
                    return dashIndex > 0 ? option.substring(dashIndex + 3).trim() : "";
                }
            }
            return "";
        }
    }
    
    /**
     * 长文本单元格渲染器
     */
    static class TextCellRenderer extends DefaultTableCellRenderer {
        
        private final int maxLength;
        
        TextCellRenderer(int maxLength) {
            this.maxLength = maxLength;
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : value.toString();
            setText(abbreviate(text, maxLength));
            setToolTipText(text.isEmpty() ? null : text);
            return component;
        }
    }
    
    /**
     * 只读文本单元格编辑器
     */
    static class SelectableTextEditor extends DefaultCellEditor {
        
        private Object originalValue;
        
        SelectableTextEditor() {
            super(new JTextField());
            JTextField field = (JTextField) getComponent();
            field.setEditable(false);
            field.setToolTipText("文字已全选：Ctrl+C 复制，可拖动选择部分文字，Esc 或点击其他区域退出");
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            originalValue = value;
            Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            SwingUtilities.invokeLater(((JTextField) component)::selectAll);
            return component;
        }
        
        @Override
        public Object getCellEditorValue() {
            return originalValue;
        }
    }
    
    /**
     * 回复ID单元格渲染器
     */
    static class AnswerIdCellRenderer extends DefaultTableCellRenderer {
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (value != null) {
                setForeground(isSelected ? table.getSelectionForeground() : UiConstants.COLOR_LINK);
                setToolTipText("点击查看回复详情");
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                setText("");
                setToolTipText(null);
            }
            return this;
        }
    }
    
    /**
     * 复选框列渲染器
     */
    static class CheckBoxCellRenderer implements TableCellRenderer {
        
        private final JCheckBox checkBox = new JCheckBox();
        
        CheckBoxCellRenderer() {
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
            checkBox.setOpaque(true);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            checkBox.setSelected(Boolean.TRUE.equals(value));
            checkBox.setEnabled(value != null);
            checkBox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return checkBox;
        }
    }
    
    /**
     * 表头复选框渲染器
     */
    static class HeaderCheckBoxRenderer implements TableCellRenderer {
        
        private final JCheckBox checkBox = new JCheckBox();
        
        private final java.util.function.BooleanSupplier allCheckedSupplier;
        
        private final java.util.function.IntSupplier rowCountSupplier;
        
        HeaderCheckBoxRenderer(java.util.function.BooleanSupplier allCheckedSupplier, java.util.function.IntSupplier rowCountSupplier) {
            this.allCheckedSupplier = allCheckedSupplier;
            this.rowCountSupplier = rowCountSupplier;
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            checkBox.setSelected(allCheckedSupplier.getAsBoolean());
            checkBox.setEnabled(rowCountSupplier.getAsInt() > 0);
            return checkBox;
        }
    }
    
    /**
     * 操作列渲染器
     */
    static class ActionCellRenderer implements TableCellRenderer {
        
        private final JPanel panel;
        
        private final ActionLabel viewLabel = new ActionLabel("详情", UiConstants.COLOR_SUCCESS_LIGHT, UiConstants.COLOR_SUCCESS);
        
        private final ActionLabel editLabel = new ActionLabel("编辑", UiConstants.COLOR_PRIMARY_LIGHT, UiConstants.COLOR_PRIMARY);
        
        private final ActionLabel trainLabel = new ActionLabel("训练", UiConstants.COLOR_PENDING, UiConstants.COLOR_TRAINING);
        
        private final ActionLabel deleteLabel = new ActionLabel("删除", UiConstants.COLOR_DANGER_LIGHT, UiConstants.COLOR_DANGER);
        
        private final java.util.function.IntSupplier hoverRowSupplier;
        
        private final java.util.function.IntSupplier hoverZoneSupplier;
        
        private final int deleteZone;
        
        private final boolean withTrainAction;
        
        ActionCellRenderer(java.util.function.IntSupplier hoverRowSupplier, java.util.function.IntSupplier hoverZoneSupplier, String viewTooltip,
                boolean withTrainAction, int deleteZone) {
            this.hoverRowSupplier = hoverRowSupplier;
            this.hoverZoneSupplier = hoverZoneSupplier;
            this.withTrainAction = withTrainAction;
            this.deleteZone = deleteZone;
            viewLabel.setToolTipText(viewTooltip);
            panel = new JPanel(new GridLayout(1, withTrainAction ? 4 : 3, 6, 0));
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(4, 10, 4, 10));
            panel.add(viewLabel);
            panel.add(editLabel);
            if (withTrainAction) {
                trainLabel.setToolTipText("触发该问题的训练");
                panel.add(trainLabel);
            }
            panel.add(deleteLabel);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int hoverRow = hoverRowSupplier.getAsInt();
            int hoverZone = hoverZoneSupplier.getAsInt();
            viewLabel.setHovered(row == hoverRow && hoverZone == ACTION_VIEW);
            editLabel.setHovered(row == hoverRow && hoverZone == ACTION_EDIT);
            if (withTrainAction) {
                trainLabel.setHovered(row == hoverRow && hoverZone == ACTION_TRAIN);
            }
            deleteLabel.setHovered(row == hoverRow && hoverZone == deleteZone);
            return panel;
        }
    }
    
    /**
     * 操作按钮标签
     */
    private static class ActionLabel extends JLabel {
        
        private static final int ARC = 10;
        
        private final Color normalTextColor;
        
        private final Color normalFillColor;
        
        private final Color normalBorderColor;
        
        private final Color hoverFillColor;
        
        private boolean hovered;
        
        private ActionLabel(String text, Color textColor, Color fillColor) {
            super(text, SwingConstants.CENTER);
            this.normalTextColor = textColor;
            this.normalFillColor = withAlpha(fillColor, 30);
            this.normalBorderColor = withAlpha(textColor, 110);
            this.hoverFillColor = fillColor;
            Font uiFont = UIManager.getFont("Label.font");
            setFont(uiFont != null ? uiFont.deriveFont(Font.BOLD) : UiConstants.FONT_SANS_12_BOLD);
            setOpaque(false);
            setForeground(normalTextColor);
            setBorder(new EmptyBorder(2, 4, 2, 4));
        }
        
        private static Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
        
        private void setHovered(boolean hovered) {
            if (this.hovered != hovered) {
                this.hovered = hovered;
                setForeground(hovered ? Color.WHITE : normalTextColor);
                repaint();
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                g2.setColor(hovered ? hoverFillColor : normalFillColor);
                g2.fillRoundRect(0, 0, width, height, ARC, ARC);
                g2.setColor(hovered ? hoverFillColor : normalBorderColor);
                g2.drawRoundRect(0, 0, width, height, ARC, ARC);
            } finally {
                g2.dispose();
            }
            Graphics2D textGraphics = (Graphics2D) g.create();
            try {
                textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                String text = getText();
                if (text != null) {
                    textGraphics.setColor(getForeground());
                    textGraphics.setFont(getFont());
                    FontMetrics metrics = textGraphics.getFontMetrics(getFont());
                    Insets insets = getInsets();
                    int availableWidth = getWidth() - insets.left - insets.right;
                    int availableHeight = getHeight() - insets.top - insets.bottom;
                    int textX = insets.left + Math.max(0, (availableWidth - metrics.stringWidth(text)) / 2);
                    int textY = insets.top + Math.max(0, (availableHeight - metrics.getHeight()) / 2) + metrics.getAscent();
                    textGraphics.drawString(text, textX, textY);
                }
            } finally {
                textGraphics.dispose();
            }
        }
    }
}
