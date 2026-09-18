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
import com.datasync.components.CustomTextField;
import com.datasync.components.FilterComboBox;
import com.datasync.components.FullscreenJDialog;
import com.datasync.core.AiQuestionApiClient;
import com.datasync.model.AiEnvConfig;
import com.datasync.model.AiQuestion;
import com.datasync.util.ConfigUtil;
import com.datasync.util.ExcelQuestionUtil;
import com.datasync.util.LogUtil;
import com.datasync.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 问题批量录入管理对话框
 * <p>
 * 问题数据通过外部接口存取（环境 host + 接口路径，见 {@link AiEnvConfig}）：<br> 批量保存、更新、删除、列表查询均调用远端服务，问题列表只加载当前所选环境，全量拉取后本地做关键字过滤与分页。<br> 上屏：问题录入（右上角选择当前环境；Tab
 * 切换「界面手动录入」与「批量粘贴录入」两种方式，手动录入支持 Excel 模板导入）<br> 下屏：问题列表（仅展示当前环境、模糊查询、分页、复选框勾选与表头全选、行内查看详情/编辑/删除、双击编辑、批量触发训练、批量更新用户信息 / 训练参数、批量删除）
 *
 * @author liuweiping
 * @date 2026-09-16
 **/
public class AiQuestionMangerDialog extends FullscreenJDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(AiQuestionMangerDialog.class);
    
    /**
     * 手动录入默认行数
     */
    private static final int DEFAULT_ROW_COUNT = 5;
    
    /**
     * 手动录入问题输入框最小宽度（保证问题内容完整可见）
     */
    private static final int QUESTION_FIELD_MIN_WIDTH = 250;
    
    /**
     * 分类 / 开启训练 / 优先级别继承上一行的占位值
     */
    private static final String SAME_AS_ABOVE = "同上";
    
    /**
     * 默认分页大小
     */
    private static final int DEFAULT_PAGE_SIZE = 100;
    
    /**
     * 每页条数手动输入下限
     */
    private static final int MIN_PAGE_SIZE = 1;
    
    /**
     * 每页条数手动输入上限
     */
    private static final int MAX_PAGE_SIZE = 10000;
    
    /**
     * 触发训练可选智能体类型（随请求 agentType 字段提交给训练接口）
     */
    private static final String[] AGENT_TYPES = {"safety", "system", "ops", "auto"};
    
    /**
     * 触发训练默认智能体类型
     */
    private static final String DEFAULT_AGENT_TYPE = "auto";
    
    /**
     * 问题列表分类筛选下拉的『全部分类』选项（选中时不过滤分类）
     */
    private static final String ALL_CLASSIFY = "全部分类";
    
    /**
     * 问题列表排序：优先级降序（空视为 0），其次 ID 降序
     */
    private static final Comparator<AiQuestion> QUESTION_COMPARATOR = (a, b) -> {
        int priorityA = a.getPriority() != null ? a.getPriority() : 0;
        int priorityB = b.getPriority() != null ? b.getPriority() : 0;
        if (priorityA != priorityB) {
            return Integer.compare(priorityB, priorityA);
        }
        long idA = a.getId() != null ? a.getId() : 0L;
        long idB = b.getId() != null ? b.getId() : 0L;
        return Long.compare(idB, idA);
    };
    
    // ── 下屏表格列索引（表格不展示用户ID / 用户Session / 优先级，可在详情与编辑弹窗查看）──
    private static final int COL_CHECK = 0;
    
    private static final int COL_ID = 1;
    
    private static final int COL_QUESTION = 2;
    
    private static final int COL_CLASSIFY = 3;
    
    private static final int COL_TRAINING_PARAM = 4;
    
    private static final int COL_ANSWER = 5;
    
    private static final int COL_ENABLE = 6;
    
    private static final int COL_REMARK = 7;
    
    private static final int COL_ACTION = 8;
    
    /**
     * 复选框列固定宽度
     */
    private static final int CHECK_COLUMN_WIDTH = 46;
    
    // ── 操作列动作区域（单元格三等分：左查看详情、中编辑、右删除）──
    private static final int ACTION_VIEW = 0;
    
    private static final int ACTION_EDIT = 1;
    
    private static final int ACTION_DELETE = 2;
    
    // ── 上屏：当前环境选择（问题录入保存目标 + 下屏问题列表数据来源）──
    private JComboBox<AiEnvConfig> envCombo;
    
    /**
     * 程序化重建环境下拉时抑制选择事件（避免重复触发问题列表重新加载）
     */
    private boolean suppressEnvEvents = false;
    
    // ── 上屏：手动录入 ──
    private final List<QuestionEntryRow> entryRows = new ArrayList<>();
    
    private JPanel manualGridPanel;
    
    private List<String> classifySuggestions = new ArrayList<>();
    
    // ── 上屏：批量粘贴 ──
    private JTextArea pasteQuestionArea;
    
    private CustomTextField pasteUserIdField;
    
    private CustomTextField pasteUserSessionField;
    
    private JLabel parsePreviewLabel;
    
    private CustomTextField pasteClassifyField;
    
    private JTextArea pasteTrainingParamArea;
    
    // ── 下屏：问题列表 ──
    private CustomTextField searchField;
    
    /**
     * 分类筛选下拉（选项从已加载问题列表的分类动态生成，选中后本地过滤列表）
     */
    private JComboBox<String> classifyFilterCombo;
    
    /**
     * 程序化重建分类筛选下拉时抑制选择事件（避免逐项触发列表重新过滤）
     */
    private boolean suppressClassifyEvents = false;
    
    private JComboBox<String> pageSizeCombo;
    
    private JTable questionTable;
    
    private QuestionTableModel tableModel;
    
    /**
     * 操作列悬浮高亮所在行（-1 表示无）
     */
    private int hoverActionRow = -1;
    
    /**
     * 操作列悬浮高亮的动作区域（ACTION_VIEW / ACTION_EDIT / ACTION_DELETE，-1 表示无）
     */
    private int hoverActionZone = -1;
    
    private JButton firstPageBtn;
    
    private JButton prevPageBtn;
    
    private JButton nextPageBtn;
    
    private JButton lastPageBtn;
    
    private JLabel pageInfoLabel;
    
    private JLabel totalLabel;
    
    /**
     * 工具栏已勾选数量提示（跨页统计）
     */
    private JLabel checkedCountLabel;
    
    private int currentPage = 1;
    
    private int totalPages = 1;
    
    private long totalCount = 0;
    
    private int pageSize = DEFAULT_PAGE_SIZE;
    
    // ── 下屏：接口数据缓存（全量拉取后本地过滤分页）──
    private final List<AiQuestion> allQuestions = new ArrayList<>();
    
    /**
     * 问题缓存是否已从接口全量加载（false 表示尚未加载或已失效，刷新时需重新拉取）
     */
    private boolean questionsLoaded = false;
    
    /**
     * 是否有接口操作进行中（防止并发重复提交）
     */
    private boolean apiBusy = false;
    
    private JButton manualSaveBtn;
    
    private JButton pasteSaveBtn;
    
    /**
     * 手动录入页 Excel 导入按钮（接口操作进行中禁用）
     */
    private JButton importExcelBtn;
    
    // ── 状态栏 ──
    private JLabel statusLabel;
    
    /**
     * 主窗口引用，用于弹出环境管理对话框
     */
    private final Frame ownerFrame;
    
    public AiQuestionMangerDialog(Frame owner) {
        super("AIQUESTION", owner, "AI 问题管理", true, 1200, 780);
        this.ownerFrame = owner;
        SQLiteConfigUtil.getInstance().initialize();
        initUI();
        refreshEnvCombo();
        refreshQuestionTable();
    }
    
    // ────────── UI 初始化 ──────────
    
    /**
     * 双击切换全屏监听（与 F11 / Esc 快捷键等效）
     */
    private final MouseAdapter doubleClickFullscreenListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                toggleFullscreen();
            }
        }
    };
    
    /**
     * 递归为对话框内的空白 / 展示区域绑定双击全屏
     *
     * @param component 起始组件（对话框根组件会递归其全部子组件）
     */
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
    
    /**
     * 判断组件是否自身需要双击交互（这类组件不绑定双击全屏，避免抢占原有行为）
     * <p>
     * 表格（双击查看/编辑）、文本框（双击选词）、下拉框、按钮、滚动条、列表、选项卡、表头、 分割条（双击折叠）均需保留原有双击语义。
     */
    private static boolean isDoubleClickExcluded(Component component) {
        return component instanceof JTable || component instanceof JTextComponent || component instanceof JComboBox
                || component instanceof AbstractButton || component instanceof JScrollBar || component instanceof JList
                || component instanceof JTabbedPane || component instanceof JTableHeader || component.getClass().getName().contains("Divider");
    }
    
    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        getRootPane().setBorder(new EmptyBorder(10, 12, 10, 12));
        
        // ── 上屏：问题录入（环境选择 + 录入方式 Tab）──
        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        JLabel tipLabel = new JLabel("选择环境后录入问题（问题列表仅展示当前环境），支持手动多行录入、Excel 导入与一次性批量粘贴");
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
        // 切换当前环境后，问题列表重新加载该环境的数据
        envCombo.addActionListener(e -> onEnvSelectionChanged());
        JButton manageEnvBtn = ButtonFactory.createToolbar("管理环境");
        manageEnvBtn.addActionListener(e -> openEnvManageDialog());
        ChildLayoutPanel envPanel = new ChildLayoutPanel(new Insets(0, 4, 0, 4), ChildLayoutPanel.LayoutType.RIGHT);
        envPanel.add(new JLabel("环境："));
        envPanel.add(envCombo);
        envPanel.add(manageEnvBtn);
        headerPanel.add(envPanel, BorderLayout.EAST);
        topPanel.add(headerPanel, BorderLayout.NORTH);
        
        JTabbedPane entryTabs = new JTabbedPane();
        entryTabs.addTab("手动录入", buildManualEntryPanel());
        entryTabs.addTab("批量粘贴录入", buildPasteEntryPanel());
        topPanel.add(entryTabs, BorderLayout.CENTER);
        
        // ── 下屏：问题列表 ──
        JPanel bottomPanel = buildQuestionListPanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, bottomPanel);
        splitPane.setResizeWeight(0.25);
        splitPane.setDividerLocation(0.25);
        add(splitPane, BorderLayout.CENTER);
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        statusLabel.setToolTipText("双击空白区域可全屏 / 退出全屏（F11 / Esc）");
        add(statusLabel, BorderLayout.SOUTH);
        
        // 全部组件就绪后统一绑定：双击对话框内空白 / 展示区域切换全屏
        bindDoubleClickFullscreen(this);
    }
    
    // ────────── 上屏：界面手动录入 ──────────
    
    private JComponent buildManualEntryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(6, 8, 6, 8));
        
        manualGridPanel = new JPanel(new GridBagLayout());
        addManualGridHeader();
        for (int i = 0; i < DEFAULT_ROW_COUNT; i++) {
            addEntryRow();
        }
        
        JScrollPane scrollPane = new JScrollPane(manualGridPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        JLabel hintLabel = new JLabel(
                "提示：用户ID选填，问题为空的行自动忽略；第 2 行起用户ID / 分类 / 开启训练 / 优先级别支持『同上』继承上一行；支持 Excel 导入（先『下载模板』）");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(UiConstants.FONT_SANS_10);
        bottomPanel.add(hintLabel, BorderLayout.WEST);
        
        ChildLayoutPanel btnPanel = new ChildLayoutPanel(new Insets(2, 4, 2, 4), ChildLayoutPanel.LayoutType.RIGHT);
        JButton downloadTplBtn = ButtonFactory.createSecondary("下载模板");
        downloadTplBtn.addActionListener(e -> downloadExcelTemplate());
        importExcelBtn = ButtonFactory.createSecondary("导入 Excel");
        importExcelBtn.addActionListener(e -> importFromExcel());
        JButton addRowBtn = ButtonFactory.createSecondary("添加一行");
        addRowBtn.addActionListener(e -> addEntryRow());
        JButton clearBtn = ButtonFactory.createDestructive("清空");
        clearBtn.addActionListener(e -> resetManualRows());
        manualSaveBtn = ButtonFactory.createPrimary("批量保存");
        manualSaveBtn.addActionListener(e -> saveManualQuestions());
        btnPanel.add(downloadTplBtn);
        btnPanel.add(importExcelBtn);
        btnPanel.add(addRowBtn);
        btnPanel.add(clearBtn);
        btnPanel.add(manualSaveBtn);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 手动录入网格表头：问题、用户ID、分类、开启训练、优先级别、训练参数、固定答案、操作
     */
    private void addManualGridHeader() {
        String[] headers = {"问题", "用户ID", "分类", "开启训练", "优先级别", "训练参数", "固定答案", "操作"};
        double[] weights = {3.0, 0.4, 0.35, 0, 0, 0.5, 0.5, 0};
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < headers.length; i++) {
            JLabel label = new JLabel(headers[i]);
            label.setFont(UiConstants.FONT_SANS_12_BOLD);
            gbc.gridx = i;
            gbc.weightx = weights[i];
            manualGridPanel.add(label, gbc);
        }
    }
    
    /**
     * 在网格末尾追加一行录入组件
     */
    private void addEntryRow() {
        int rowIndex = entryRows.size();
        QuestionEntryRow row = new QuestionEntryRow(rowIndex);
        entryRows.add(row);
        addRowComponents(row, rowIndex);
        manualGridPanel.revalidate();
        manualGridPanel.repaint();
    }
    
    /**
     * 将一行录入组件放入网格对应行（gridy = 行号 + 1，第 0 行为表头）
     */
    private void addRowComponents(QuestionEntryRow row, int rowIndex) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = rowIndex + 1;
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        
        gbc.gridx = 0;
        gbc.weightx = 3.0;
        manualGridPanel.add(row.questionField, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.4;
        row.userIdField.setPreferredSize(new Dimension(140, 26));
        manualGridPanel.add(row.userIdField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.35;
        manualGridPanel.add(row.classifyCombo, gbc);
        gbc.gridx = 3;
        gbc.weightx = 0;
        row.enableTrainingCombo.setPreferredSize(new Dimension(92, 26));
        manualGridPanel.add(row.enableTrainingCombo, gbc);
        gbc.gridx = 4;
        gbc.weightx = 0;
        manualGridPanel.add(row.priorityField, gbc);
        gbc.gridx = 5;
        gbc.weightx = 0.5;
        manualGridPanel.add(row.trainingParamArea, gbc);
        gbc.gridx = 6;
        gbc.weightx = 0.5;
        manualGridPanel.add(row.answerArea, gbc);
        gbc.gridx = 7;
        gbc.weightx = 0;
        manualGridPanel.add(row.deleteRowBtn, gbc);
    }
    
    /**
     * 删除一行录入组件并重排剩余行（复用现有行组件，其它行已录入内容保持不变）
     */
    private void removeEntryRow(QuestionEntryRow target) {
        if (!entryRows.remove(target)) {
            return;
        }
        manualGridPanel.removeAll();
        addManualGridHeader();
        for (int i = 0; i < entryRows.size(); i++) {
            addRowComponents(entryRows.get(i), i);
        }
        manualGridPanel.revalidate();
        manualGridPanel.repaint();
    }
    
    /**
     * 清空并重建手动录入网格（恢复默认行数）
     */
    private void resetManualRows() {
        entryRows.clear();
        manualGridPanel.removeAll();
        addManualGridHeader();
        for (int i = 0; i < DEFAULT_ROW_COUNT; i++) {
            addEntryRow();
        }
        manualGridPanel.revalidate();
        manualGridPanel.repaint();
    }
    
    /**
     * 收集手动录入的问题，解析『同上』继承逻辑；校验失败的信息收集到 errors
     */
    private List<AiQuestion> collectManualQuestions(String envName, List<String> errors) {
        List<AiQuestion> questions = new ArrayList<>();
        String resolvedClassify = null;
        String resolvedUserId = null;
        int resolvedEnable = AiQuestion.TRAINING_ENABLED;
        int resolvedPriority = 0;
        for (int i = 0; i < entryRows.size(); i++) {
            QuestionEntryRow row = entryRows.get(i);
            String question = row.questionField.getText().trim();
            if (question.isEmpty()) {
                continue;
            }
            // 用户ID（非必填）：『同上』或留空时继承上一行的有效值，全部留空则不提交该字段
            String userId = row.userIdField.getText().trim();
            if (!userId.isEmpty() && !SAME_AS_ABOVE.equals(userId)) {
                resolvedUserId = userId;
            }
            // 分类：『同上』或留空时继承上一行的有效值
            String classify = editableComboText(row.classifyCombo).trim();
            if (!classify.isEmpty() && !SAME_AS_ABOVE.equals(classify)) {
                resolvedClassify = classify;
            }
            // 开启训练：『同上』时继承上一行的有效值
            Object enableSelection = row.enableTrainingCombo.getSelectedItem();
            String enableText = enableSelection != null ? enableSelection.toString() : "开启";
            if ("开启".equals(enableText)) {
                resolvedEnable = AiQuestion.TRAINING_ENABLED;
            } else if ("不开启".equals(enableText)) {
                resolvedEnable = AiQuestion.TRAINING_DISABLED;
            }
            // 优先级别：『同上』或留空时继承上一行的有效值
            String priorityText = row.priorityField.getText().trim();
            if (!priorityText.isEmpty() && !SAME_AS_ABOVE.equals(priorityText)) {
                try {
                    resolvedPriority = Integer.parseInt(priorityText);
                } catch (NumberFormatException ex) {
                    errors.add("第 " + (i + 1) + " 行：优先级别必须为整数（" + priorityText + "）");
                }
            }
            AiQuestion item = new AiQuestion();
            item.setEnvName(envName);
            item.setQuestion(question);
            item.setUserId(resolvedUserId);
            item.setQuestionClassify(resolvedClassify);
            item.setTrainingParam(row.trainingParamArea.getText().trim());
            item.setAnswer(row.answerArea.getText().trim());
            item.setEnableTraining(resolvedEnable);
            item.setPriority(resolvedPriority);
            questions.add(item);
        }
        return questions;
    }
    
    /**
     * 批量保存手动录入的问题（调用外部接口）
     */
    private void saveManualQuestions() {
        AiEnvConfig envConfig = requireSelectedEnvConfig();
        if (envConfig == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        List<AiQuestion> questions = collectManualQuestions(envConfig.getEnvName(), errors);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", errors), "录入有误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (questions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少录入一条问题", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runApiTask("批量保存", () -> {
            String message = AiQuestionApiClient.getInstance().saveQuestions(envConfig, questions);
            return () -> {
                resetManualRows();
                currentPage = 1;
                invalidateLoadedData();
                refreshQuestionTable();
                setStatus("手动录入成功保存 " + questions.size() + " 条问题到环境 [" + envConfig.getEnvName() + "]：" + message, true);
            };
        });
    }
    
    // ────────── 上屏：手动录入 Excel 导入 ──────────
    
    /**
     * 生成 Excel 导入模板并保存到用户选择的路径（含表头、示例数据与填写说明页）
     */
    private void downloadExcelTemplate() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存 Excel 导入模板");
        chooser.setSelectedFile(new File("AI问题导入模板.xlsx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File outputFile = chooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".xlsx")) {
            outputFile = new File(outputFile.getParentFile(), outputFile.getName() + ".xlsx");
        }
        if (outputFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？\n" + outputFile.getAbsolutePath(), "确认覆盖",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            ExcelQuestionUtil.generateTemplate(outputFile);
        } catch (Exception ex) {
            logger.error("生成 Excel 导入模板失败", ex);
            setStatus("模板下载失败：" + ex.getMessage(), false);
            JOptionPane.showMessageDialog(this, "生成模板失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setStatus("模板已保存：" + outputFile.getAbsolutePath(), true);
        int open = JOptionPane.showConfirmDialog(this, "模板已保存到：\n" + outputFile.getAbsolutePath() + "\n\n是否立即打开查看？", "下载成功",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (open == JOptionPane.YES_OPTION) {
            openOutputFile(outputFile);
        }
    }
    
    /**
     * 选择 Excel 文件（.xlsx / .xls）并导入：后台解析后回填手动录入网格，核对无误再点『批量保存』
     */
    private void importFromExcel() {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean hasInput = false;
        for (QuestionEntryRow row : entryRows) {
            if (!row.questionField.getText().trim().isEmpty()) {
                hasInput = true;
                break;
            }
        }
        if (hasInput) {
            int confirm = JOptionPane.showConfirmDialog(this, "当前手动录入的内容将被导入的数据覆盖，是否继续？", "确认导入", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要导入的 Excel 文件");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx, *.xls)", "xlsx", "xls"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File inputFile = chooser.getSelectedFile();
        apiBusy = true;
        setSaveButtonsEnabled(false);
        setStatus("正在解析 Excel 文件 " + inputFile.getName() + "…", true);
        new Thread(() -> {
            try {
                ExcelQuestionUtil.ParseResult parseResult = ExcelQuestionUtil.parseQuestions(inputFile);
                SwingUtilities.invokeLater(() -> {
                    apiBusy = false;
                    setSaveButtonsEnabled(true);
                    applyExcelParseResult(inputFile, parseResult);
                });
            } catch (Exception ex) {
                logger.error("解析 Excel 文件失败: {}", inputFile.getAbsolutePath(), ex);
                SwingUtilities.invokeLater(() -> {
                    apiBusy = false;
                    setSaveButtonsEnabled(true);
                    setStatus("Excel 导入失败：" + ex.getMessage(), false);
                    JOptionPane.showMessageDialog(this, "解析 Excel 文件失败：" + ex.getMessage()
                                    + "\n\n请确认文件为有效的 Excel（.xlsx / .xls）且未被其它程序占用，并按模板格式填写。", "导入失败",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "excel-import").start();
    }
    
    /**
     * 解析完成后处理：有数据错误时提示且不回填，全部正确时重建网格填充导入内容
     */
    private void applyExcelParseResult(File inputFile, ExcelQuestionUtil.ParseResult parseResult) {
        List<String> errors = parseResult.getErrors();
        if (!errors.isEmpty()) {
            String detail = errors.size() > 10 ? String.join("\n", errors.subList(0, 10)) + "\n…（共 " + errors.size() + " 处错误）"
                    : String.join("\n", errors);
            setStatus("Excel 导入失败：" + errors.size() + " 处数据有误，未导入任何数据", false);
            JOptionPane.showMessageDialog(this, "以下数据有误，请修正后重新导入（未导入任何数据）：\n\n" + detail, "导入有误",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> questions = parseResult.getQuestions();
        if (questions.isEmpty()) {
            setStatus("Excel 中未解析到问题数据：" + inputFile.getName(), false);
            JOptionPane.showMessageDialog(this, "未从文件中解析到任何问题，请确认已填写数据行（问题为空的行自动忽略）。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        fillManualRows(questions);
        setStatus("已从 " + inputFile.getName() + " 导入 " + questions.size() + " 条问题，核对无误后点击『批量保存』提交", true);
    }
    
    /**
     * 用导入数据重建手动录入网格并逐行填充（导入后可继续编辑，保存复用手动录入逻辑）
     */
    private void fillManualRows(List<AiQuestion> questions) {
        entryRows.clear();
        manualGridPanel.removeAll();
        addManualGridHeader();
        for (AiQuestion question : questions) {
            addEntryRow();
            QuestionEntryRow row = entryRows.get(entryRows.size() - 1);
            row.questionField.setText(nullToEmpty(question.getQuestion()));
            row.userIdField.setText(nullToEmpty(question.getUserId()));
            // 分类：与 collectManualQuestions 的读取路径一致，直接写入编辑器内容
            row.classifyCombo.getEditor().setItem(nullToEmpty(question.getQuestionClassify()));
            row.enableTrainingCombo.setSelectedItem(question.isTrainingEnabled() ? "开启" : "不开启");
            row.priorityField.setText(String.valueOf(question.getPriority() != null ? question.getPriority() : 0));
            row.trainingParamArea.setText(nullToEmpty(question.getTrainingParam()));
            row.answerArea.setText(nullToEmpty(question.getAnswer()));
        }
        manualGridPanel.revalidate();
        manualGridPanel.repaint();
    }
    
    /**
     * 用系统默认程序打开文件（模板下载后快速查看）
     */
    private void openOutputFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ex) {
            logger.warn("打开文件失败: {}", file.getAbsolutePath(), ex);
        }
    }
    
    // ────────── 上屏：批量粘贴录入 ──────────
    
    /**
     * 批量粘贴录入面板：左侧（问题内容）与右侧（归属信息、训练参数）左右分栏，底部操作按钮
     */
    private JComponent buildPasteEntryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPasteQuestionPanel(), buildPasteMetaPanel());
        splitPane.setResizeWeight(0.6);
        splitPane.setDividerLocation(0.6);
        panel.add(splitPane, BorderLayout.CENTER);
        
        ChildLayoutPanel btnPanel = new ChildLayoutPanel(new Insets(2, 4, 2, 4), ChildLayoutPanel.LayoutType.RIGHT);
        JButton clearBtn = ButtonFactory.createDestructive("清空");
        clearBtn.addActionListener(e -> clearPasteInputs());
        pasteSaveBtn = ButtonFactory.createPrimary("批量保存");
        pasteSaveBtn.addActionListener(e -> savePastedQuestions());
        btnPanel.add(clearBtn);
        btnPanel.add(pasteSaveBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 批量粘贴左屏：问题内容输入区 + 底部实时解析预览
     */
    private JPanel buildPasteQuestionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("问题内容"), new EmptyBorder(2, 8, 6, 8)));
        
        pasteQuestionArea = new JTextArea(8, 30);
        pasteQuestionArea.setLineWrap(true);
        pasteQuestionArea.setWrapStyleWord(true);
        pasteQuestionArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateParsePreview();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                updateParsePreview();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                updateParsePreview();
            }
        });
        panel.add(new JScrollPane(pasteQuestionArea), BorderLayout.CENTER);
        
        parsePreviewLabel = new JLabel("已识别 0 个问题");
        parsePreviewLabel.setForeground(Color.GRAY);
        panel.add(parsePreviewLabel, BorderLayout.SOUTH);
        return panel;
    }
    
    /**
     * 批量粘贴右屏：用户ID / 用户Session / 问题分类（适用全部问题，可留空）与训练参数
     */
    private JPanel buildPasteMetaPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("问题归属与训练参数"), new EmptyBorder(2, 8, 6, 8)));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        pasteUserIdField = new CustomTextField("归属用户ID（适用全部问题，可留空）");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("用户ID："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteUserIdField, gbc);
        
        pasteUserSessionField = new CustomTextField("用户Session（适用全部问题，可留空）");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("用户Session："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteUserSessionField, gbc);
        
        pasteClassifyField = new CustomTextField("问题的分类（适用全部问题，可留空）");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("问题分类："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteClassifyField, gbc);
        
        pasteTrainingParamArea = new JTextArea(3, 20);
        pasteTrainingParamArea.setLineWrap(true);
        pasteTrainingParamArea.setWrapStyleWord(true);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("训练参数："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(pasteTrainingParamArea), gbc);
        
        return panel;
    }
    
    /**
     * 解析批量粘贴的问题文本：仅按换行、中英文分号分隔（逗号、顿号与空格属于问题内容不分割），批内去重保序
     */
    private List<String> parseQuestions(String text) {
        List<String> questions = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return questions;
        }
        for (String token : text.split("[\\r\\n\\t;；]+")) {
            String question = token.trim();
            if (!question.isEmpty()) {
                questions.add(question);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(questions));
    }
    
    /**
     * 实时更新解析预览
     */
    private void updateParsePreview() {
        List<String> questions = parseQuestions(pasteQuestionArea.getText());
        parsePreviewLabel.setText("已识别 " + questions.size() + " 个问题（自动按 换行 / 分号 分割并去重，逗号、顿号与空格属于问题内容）");
        parsePreviewLabel.setForeground(questions.isEmpty() ? Color.GRAY : UiConstants.COLOR_SUCCESS);
    }
    
    /**
     * 批量保存粘贴录入的问题，其它参数走默认（开启训练、优先级别 0、答案为空，调用外部接口）
     */
    private void savePastedQuestions() {
        AiEnvConfig envConfig = requireSelectedEnvConfig();
        if (envConfig == null) {
            return;
        }
        List<String> questions = parseQuestions(pasteQuestionArea.getText());
        if (questions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先输入问题内容", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String userId = pasteUserIdField.getText().trim();
        String userSession = pasteUserSessionField.getText().trim();
        String classify = pasteClassifyField.getText().trim();
        String trainingParam = pasteTrainingParamArea.getText().trim();
        List<AiQuestion> items = new ArrayList<>();
        for (String question : questions) {
            AiQuestion item = new AiQuestion();
            item.setEnvName(envConfig.getEnvName());
            item.setQuestion(question);
            item.setUserId(userId.isEmpty() ? null : userId);
            item.setUserSession(userSession.isEmpty() ? null : userSession);
            item.setQuestionClassify(classify.isEmpty() ? null : classify);
            item.setTrainingParam(trainingParam);
            item.setAnswer("");
            item.setEnableTraining(AiQuestion.TRAINING_ENABLED);
            item.setPriority(0);
            items.add(item);
        }
        runApiTask("批量保存", () -> {
            String message = AiQuestionApiClient.getInstance().saveQuestions(envConfig, items);
            return () -> {
                clearPasteInputs();
                currentPage = 1;
                invalidateLoadedData();
                refreshQuestionTable();
                setStatus("批量粘贴成功保存 " + items.size() + " 条问题到环境 [" + envConfig.getEnvName() + "]：" + message, true);
            };
        });
    }
    
    private void clearPasteInputs() {
        pasteQuestionArea.setText("");
        pasteUserIdField.setText("");
        pasteUserSessionField.setText("");
        pasteClassifyField.setText("");
        pasteTrainingParamArea.setText("");
        updateParsePreview();
    }
    
    // ────────── 环境选择 ──────────
    
    /**
     * 打开环境管理对话框，关闭后刷新环境相关下拉与列表
     */
    private void openEnvManageDialog() {
        new AiEnvMangerDialog(ownerFrame).setVisible(true);
        refreshEnvCombo();
        invalidateLoadedData();
        refreshQuestionTable();
    }
    
    /**
     * 刷新上屏当前环境下拉；优先选中全局选中的环境（环境管理中持久化保存的）
     */
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
        // 重建下拉项期间抑制选择事件，避免逐项触发问题列表重新加载
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
    
    /**
     * 当前环境切换后的处理：持久化保存下拉选中的环境（下次打开界面默认选中），重置分页并重新加载该环境的问题列表
     */
    private void onEnvSelectionChanged() {
        if (suppressEnvEvents) {
            return;
        }
        persistSelectedEnv();
        currentPage = 1;
        invalidateLoadedData();
        refreshQuestionTable();
    }
    
    /**
     * 持久化下拉选中的环境为全局选中环境（SQLite 保存，下次打开本界面 / 进入环境管理时默认选中）
     */
    private void persistSelectedEnv() {
        if (!(envCombo.getSelectedItem() instanceof AiEnvConfig config) || Boolean.TRUE.equals(config.getSelected())) {
            return;
        }
        if (!ConfigUtil.updateSelectedAiEnv(config.getId())) {
            logger.error("持久化选中环境 [{}] 失败", config.getEnvName());
            setStatus("选中环境保存失败：" + config.getEnvName() + "（不影响当前使用）", false);
            return;
        }
        // 同步下拉列表内选中标志，避免重复写库
        for (int i = 0; i < envCombo.getItemCount(); i++) {
            envCombo.getItemAt(i).setSelected(false);
        }
        config.setSelected(true);
    }
    
    /**
     * 获取当前选中的保存环境配置，未选择时提示并返回 null
     */
    private AiEnvConfig requireSelectedEnvConfig() {
        Object selected = envCombo.getSelectedItem();
        if (!(selected instanceof AiEnvConfig config)) {
            JOptionPane.showMessageDialog(this, "请先在右上角选择问题保存环境", "提示", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return config;
    }
    
    /**
     * 按环境名称查找环境配置
     */
    private AiEnvConfig findEnvByName(String envName) {
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
    
    // ────────── 下屏：问题列表 ──────────
    
    private JPanel buildQuestionListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("问题列表"));
        
        // 工具栏：左侧筛选（模糊查询 + 分类 + 每页条数 + 查询按钮），右侧批量操作（已选计数 + 触发训练 / 批量更新 / 批量删除）
        JPanel toolbar = new JPanel(new BorderLayout(6, 2));
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        searchField = new CustomTextField("输入关键字模糊查询");
        searchField.setMinWidth(200);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                currentPage = 1;
                refreshQuestionTable();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                currentPage = 1;
                refreshQuestionTable();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                currentPage = 1;
                refreshQuestionTable();
            }
        });
        filterPanel.add(searchField);
        filterPanel.add(new JLabel("分类："));
        // 分类筛选：选项由已加载问题列表的分类动态生成，选中后本地过滤列表
        classifyFilterCombo = new JComboBox<>();
        classifyFilterCombo.addItem(ALL_CLASSIFY);
        classifyFilterCombo.setPreferredSize(new Dimension(140, 26));
        classifyFilterCombo.setToolTipText("按分类过滤问题列表，选项来自当前环境问题列表的分类");
        classifyFilterCombo.addActionListener(e -> onClassifyFilterChanged());
        filterPanel.add(classifyFilterCombo);
        filterPanel.add(new JLabel("每页："));
        // 可编辑下拉：既可选快捷值，也可手动输入每页条数（回车或失去焦点生效）
        pageSizeCombo = new JComboBox<>(new String[] {"100", "10", "20", "50"});
        pageSizeCombo.setEditable(true);
        pageSizeCombo.setPreferredSize(new Dimension(76, 26));
        pageSizeCombo.setToolTipText("选择或输入每页条数（" + MIN_PAGE_SIZE + " ~ " + MAX_PAGE_SIZE + "），回车或失去焦点生效");
        pageSizeCombo.setSelectedItem(String.valueOf(DEFAULT_PAGE_SIZE));
        pageSizeCombo.addActionListener(e -> applyPageSizeFromCombo());
        if (pageSizeCombo.getEditor().getEditorComponent() instanceof JTextField editorField) {
            editorField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    applyPageSizeFromCombo();
                }
            });
        }
        filterPanel.add(pageSizeCombo);
        filterPanel.add(new JLabel("条"));
        // 筛选动作按钮：胶囊描边样式（常态轻填充 + 彩色描边，悬浮 / 按下填充反白，与操作列按钮同一视觉语言）
        JButton refreshBtn = ButtonFactory.createPill("查 询", UiConstants.COLOR_SUCCESS, UiConstants.COLOR_SUCCESS_LIGHT);
        refreshBtn.addActionListener(e -> {
            currentPage = 1;
            invalidateLoadedData();
            refreshQuestionTable();
        });
        filterPanel.add(refreshBtn);
        
        // 右侧批量操作组：已选计数 + 触发训练 / 批量更新 / 批量删除（右对齐）
        checkedCountLabel = new JLabel("已选 0 条");
        checkedCountLabel.setForeground(Color.GRAY);
        checkedCountLabel.setFont(UiConstants.FONT_SANS_11);
        actionPanel.add(checkedCountLabel);
        JButton trainBtn = ButtonFactory.createPill("触发训练", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
        trainBtn.setToolTipText("触发勾选问题的训练（点击表头复选框可全选当前页）");
        trainBtn.addActionListener(e -> triggerTrainingQuestions());
        actionPanel.add(trainBtn);
        JButton batchUpdateBtn = ButtonFactory.createPill("批量更新", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
        batchUpdateBtn.setToolTipText("批量更新勾选问题的用户ID、用户Session与训练参数（点击表头复选框可全选当前页）");
        batchUpdateBtn.addActionListener(e -> batchUpdateUserQuestions());
        actionPanel.add(batchUpdateBtn);
        JButton batchDeleteBtn = ButtonFactory.createPill("批量删除", UiConstants.COLOR_DANGER, UiConstants.COLOR_DANGER_LIGHT);
        batchDeleteBtn.setToolTipText("批量删除勾选的问题，删除后不可恢复（点击表头复选框可全选当前页）");
        batchDeleteBtn.addActionListener(e -> batchDeleteQuestions());
        actionPanel.add(batchDeleteBtn);
        toolbar.add(filterPanel, BorderLayout.CENTER);
        toolbar.add(actionPanel, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);
        
        // 问题表格
        tableModel = new QuestionTableModel();
        questionTable = new JTable(tableModel);
        questionTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        questionTable.setRowHeight(28);
        questionTable.getTableHeader().setReorderingAllowed(false);
        int[] columnWidths = {CHECK_COLUMN_WIDTH, 50, 280, 90, 150, 150, 80, 110, 170};
        for (int i = 0; i < columnWidths.length; i++) {
            questionTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        // 复选框列：固定宽度，表头渲染全选复选框
        TableColumn checkColumn = questionTable.getColumnModel().getColumn(COL_CHECK);
        checkColumn.setMinWidth(CHECK_COLUMN_WIDTH);
        checkColumn.setMaxWidth(CHECK_COLUMN_WIDTH);
        checkColumn.setCellRenderer(new CheckBoxCellRenderer());
        checkColumn.setHeaderRenderer(new HeaderCheckBoxRenderer());
        questionTable.getColumnModel().getColumn(COL_ID).setCellRenderer(centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_QUESTION).setCellRenderer(new TextCellRenderer(48));
        questionTable.getColumnModel().getColumn(COL_CLASSIFY).setCellRenderer(new TextCellRenderer(12));
        questionTable.getColumnModel().getColumn(COL_TRAINING_PARAM).setCellRenderer(new TextCellRenderer(24));
        questionTable.getColumnModel().getColumn(COL_ANSWER).setCellRenderer(new TextCellRenderer(24));
        questionTable.getColumnModel().getColumn(COL_ENABLE).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground("开启".equals(value.toString()) ? UiConstants.COLOR_SUCCESS : Color.GRAY);
            return label;
        });
        questionTable.getColumnModel().getColumn(COL_REMARK).setCellRenderer(new TextCellRenderer(20));
        questionTable.getColumnModel().getColumn(COL_ACTION).setCellRenderer(new ActionCellRenderer());
        questionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleTableClick(e);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                updateActionHover(null);
            }
        });
        // 操作列悬浮高亮与手型光标反馈
        questionTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateActionHover(e.getPoint());
            }
        });
        questionTable.addMouseWheelListener(e -> updateActionHover(e.getPoint()));
        // 表头复选框：单击切换当前页全选 / 取消全选
        questionTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && questionTable.getTableHeader().columnAtPoint(e.getPoint()) == COL_CHECK) {
                    tableModel.toggleCheckCurrentPage();
                    updateCheckControls();
                }
            }
        });
        panel.add(new JScrollPane(questionTable), BorderLayout.CENTER);
        
        // 分页栏
        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        firstPageBtn = ButtonFactory.createToolbar("首页");
        prevPageBtn = ButtonFactory.createToolbar("上一页");
        nextPageBtn = ButtonFactory.createToolbar("下一页");
        lastPageBtn = ButtonFactory.createToolbar("末页");
        pageInfoLabel = new JLabel();
        totalLabel = new JLabel();
        firstPageBtn.addActionListener(e -> gotoPage(1));
        prevPageBtn.addActionListener(e -> gotoPage(currentPage - 1));
        nextPageBtn.addActionListener(e -> gotoPage(currentPage + 1));
        lastPageBtn.addActionListener(e -> gotoPage(totalPages));
        pagePanel.add(firstPageBtn);
        pagePanel.add(prevPageBtn);
        pagePanel.add(pageInfoLabel);
        pagePanel.add(nextPageBtn);
        pagePanel.add(lastPageBtn);
        pagePanel.add(Box.createHorizontalStrut(12));
        pagePanel.add(totalLabel);
        panel.add(pagePanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 表格点击处理：复选框列单击切换勾选；操作列三等分区域查看详情 / 编辑 / 删除；其它列双击编辑；空白区域双击切换全屏
     */
    private void handleTableClick(MouseEvent e) {
        int row = questionTable.rowAtPoint(e.getPoint());
        int column = questionTable.columnAtPoint(e.getPoint());
        if (row < 0 || column < 0 || row >= tableModel.getRowCount()) {
            // 双击表格空白区域（数据行以外的区域）切换全屏
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                toggleFullscreen();
            }
            return;
        }
        AiQuestion question = tableModel.getQuestionAt(row);
        if (column == COL_CHECK) {
            if (e.getClickCount() == 1) {
                tableModel.toggleChecked(row);
                updateCheckControls();
            }
        } else if (column == COL_ACTION) {
            Rectangle cellRect = questionTable.getCellRect(row, column, false);
            int zone = actionZoneAt(e.getX(), cellRect);
            if (zone == ACTION_VIEW) {
                showQuestionDetail(question);
            } else if (zone == ACTION_EDIT) {
                editQuestion(question);
            } else {
                deleteQuestion(question);
            }
        } else if (e.getClickCount() == 2) {
            editQuestion(question);
        }
    }
    
    /**
     * 判断横坐标落在操作单元格的哪个动作区域（三等分：左查看详情、中编辑、右删除）
     */
    private static int actionZoneAt(int x, Rectangle cellRect) {
        double third = cellRect.width / 3.0;
        double offset = x - cellRect.x;
        if (offset <= third) {
            return ACTION_VIEW;
        }
        return offset <= third * 2 ? ACTION_EDIT : ACTION_DELETE;
    }
    
    /**
     * 更新操作列悬浮高亮：鼠标位于操作列时按动作区域高亮对应按钮，并切换手型光标
     *
     * @param point 表格坐标下的鼠标位置，null 表示清除高亮
     */
    private void updateActionHover(Point point) {
        int row = -1;
        int zone = -1;
        boolean clickable = false;
        if (point != null) {
            int hitRow = questionTable.rowAtPoint(point);
            int hitColumn = questionTable.columnAtPoint(point);
            if (hitRow >= 0 && hitColumn == COL_ACTION) {
                row = hitRow;
                zone = actionZoneAt(point.x, questionTable.getCellRect(hitRow, COL_ACTION, false));
                clickable = true;
            } else if (hitRow >= 0 && hitColumn == COL_CHECK) {
                clickable = true;
            }
        }
        questionTable.setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (row == hoverActionRow && zone == hoverActionZone) {
            return;
        }
        int previousRow = hoverActionRow;
        hoverActionRow = row;
        hoverActionZone = zone;
        repaintActionCell(previousRow);
        repaintActionCell(row);
    }
    
    /**
     * 重绘指定行的操作单元格（行号无效时忽略）
     */
    private void repaintActionCell(int row) {
        if (row >= 0 && row < questionTable.getRowCount()) {
            questionTable.repaint(questionTable.getCellRect(row, COL_ACTION, false));
        }
    }
    
    /**
     * 刷新勾选相关界面：表头全选复选框状态与已勾选数量提示
     */
    private void updateCheckControls() {
        questionTable.getTableHeader().repaint();
        if (checkedCountLabel != null) {
            checkedCountLabel.setText("已选 " + tableModel.getCheckedCount() + " 条");
        }
    }
    
    /**
     * 刷新问题列表：缓存未加载或已失效时从接口重新拉取，否则直接本地过滤分页
     */
    private void refreshQuestionTable() {
        if (!questionsLoaded) {
            loadQuestionsFromApi();
            return;
        }
        applyLocalFilterAndPaging();
    }
    
    /**
     * 从外部接口拉取当前所选环境的问题列表（切换环境后重新加载，列表仅展示该环境数据）
     */
    private void loadQuestionsFromApi() {
        if (apiBusy) {
            return;
        }
        AiEnvConfig envConfig = envCombo.getSelectedItem() instanceof AiEnvConfig config ? config : null;
        if (envConfig == null) {
            questionsLoaded = true;
            allQuestions.clear();
            refreshClassifyFilterOptions();
            tableModel.setData(new ArrayList<>());
            tableModel.clearChecked();
            updateCheckControls();
            setStatus("请先在右上角选择环境", false);
            return;
        }
        apiBusy = true;
        setSaveButtonsEnabled(false);
        setStatus("正在加载环境 [" + envConfig.getEnvName() + "] 的问题列表…", true);
        new Thread(() -> {
            List<AiQuestion> loaded = new ArrayList<>();
            String error = null;
            try {
                for (AiQuestion item : AiQuestionApiClient.getInstance().listQuestions(envConfig)) {
                    item.setEnvName(envConfig.getEnvName());
                    loaded.add(item);
                }
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("加载环境 [{}] 问题列表失败", envConfig.getEnvName(), ex);
                error = "环境 [" + envConfig.getEnvName() + "] 问题列表加载失败：" + ex.getMessage();
            }
            String loadError = error;
            SwingUtilities.invokeLater(() -> {
                apiBusy = false;
                setSaveButtonsEnabled(true);
                questionsLoaded = true;
                allQuestions.clear();
                allQuestions.addAll(loaded);
                // 清理已勾选但已不存在的问题（保留跨页勾选）
                tableModel.retainChecked(allQuestions);
                classifySuggestions = extractClassifies(allQuestions);
                // 按最新列表分类重建分类筛选项（选中分类仍存在则保持，否则回到『全部分类』）
                refreshClassifyFilterOptions();
                if (loadError != null) {
                    setStatus(loadError, false);
                } else {
                    setStatus("环境 [" + envConfig.getEnvName() + "] 问题列表加载完成，共 " + loaded.size() + " 条", true);
                }
                applyLocalFilterAndPaging();
                // 加载期间用户切换了环境：立即重新加载切换后的环境
                if (envCombo.getSelectedItem() instanceof AiEnvConfig current && !current.getEnvName().equals(envConfig.getEnvName())) {
                    questionsLoaded = false;
                    refreshQuestionTable();
                }
            });
        }, "ai-question-load").start();
    }
    
    /**
     * 在缓存数据上执行本地关键字过滤、排序与分页展示
     */
    private void applyLocalFilterAndPaging() {
        String keyword = searchField.getText().trim().toLowerCase();
        String classifyFilter = selectedClassifyFilter();
        List<AiQuestion> filtered = new ArrayList<>();
        for (AiQuestion item : allQuestions) {
            if (matchesKeyword(item, keyword) && matchesClassify(item, classifyFilter)) {
                filtered.add(item);
            }
        }
        filtered.sort(QUESTION_COMPARATOR);
        totalCount = filtered.size();
        totalPages = (int) Math.max(1, (totalCount + pageSize - 1) / pageSize);
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        tableModel.setData(fromIndex < toIndex ? new ArrayList<>(filtered.subList(fromIndex, toIndex)) : new ArrayList<>());
        updatePaginationControls();
        updateCheckControls();
    }
    
    /**
     * 关键字模糊匹配问题内容与分类（关键字为空时全部匹配）
     */
    private boolean matchesKeyword(AiQuestion question, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String questionText = nullToEmpty(question.getQuestion()).toLowerCase();
        String classifyText = nullToEmpty(question.getQuestionClassify()).toLowerCase();
        return questionText.contains(keyword) || classifyText.contains(keyword);
    }
    
    /**
     * 从已加载的问题中提取去重分类（用于录入分类建议）
     */
    private List<String> extractClassifies(List<AiQuestion> questions) {
        LinkedHashSet<String> classifies = new LinkedHashSet<>();
        for (AiQuestion item : questions) {
            String classify = item.getQuestionClassify();
            if (classify != null && !classify.isBlank()) {
                classifies.add(classify.trim());
            }
        }
        return new ArrayList<>(classifies);
    }
    
    /**
     * 分类筛选下拉选中项变化：回到第一页并重新过滤列表
     */
    private void onClassifyFilterChanged() {
        if (suppressClassifyEvents) {
            return;
        }
        currentPage = 1;
        refreshQuestionTable();
    }
    
    /**
     * 按当前问题列表的分类重建分类筛选下拉项（选中分类仍存在则保持，否则回到『全部分类』）
     */
    private void refreshClassifyFilterOptions() {
        List<String> classifies = extractClassifies(allQuestions);
        Object previous = classifyFilterCombo.getSelectedItem();
        suppressClassifyEvents = true;
        try {
            classifyFilterCombo.removeAllItems();
            classifyFilterCombo.addItem(ALL_CLASSIFY);
            for (String classify : classifies) {
                classifyFilterCombo.addItem(classify);
            }
            if (previous != null && classifies.contains(previous.toString())) {
                classifyFilterCombo.setSelectedItem(previous);
            } else {
                classifyFilterCombo.setSelectedItem(ALL_CLASSIFY);
            }
        } finally {
            suppressClassifyEvents = false;
        }
    }
    
    /**
     * 当前选中的分类筛选值（null 表示『全部分类』，不参与过滤）
     */
    private String selectedClassifyFilter() {
        Object selected = classifyFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        return text == null || ALL_CLASSIFY.equals(text) ? null : text;
    }
    
    /**
     * 分类筛选精确匹配（下拉项为去重后的分类，问题分类按 trim 后比较）
     */
    private boolean matchesClassify(AiQuestion question, String classifyFilter) {
        if (classifyFilter == null) {
            return true;
        }
        return classifyFilter.equals(nullToEmpty(question.getQuestionClassify()).trim());
    }
    
    /**
     * 数据变更后失效问题缓存，下次刷新时重新从接口拉取
     */
    private void invalidateLoadedData() {
        questionsLoaded = false;
    }
    
    private void gotoPage(int page) {
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }
        if (page == currentPage) {
            return;
        }
        currentPage = page;
        refreshQuestionTable();
    }
    
    /**
     * 提交每页条数输入：合法则回到第一页并刷新列表，非法时恢复上一个有效值并提示
     */
    private void applyPageSizeFromCombo() {
        Object editorValue = pageSizeCombo.getEditor().getItem();
        String text = editorValue != null ? editorValue.toString().trim() : "";
        int size = -1;
        try {
            size = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            // 非数字输入按非法值处理，走下方回退分支
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            // 非法输入：恢复上一个有效值并提示
            pageSizeCombo.getEditor().setItem(String.valueOf(pageSize));
            setStatus("每页条数请输入 " + MIN_PAGE_SIZE + " ~ " + MAX_PAGE_SIZE + " 之间的整数", false);
            return;
        }
        if (size == pageSize) {
            return;
        }
        pageSize = size;
        currentPage = 1;
        refreshQuestionTable();
    }
    
    private void updatePaginationControls() {
        pageInfoLabel.setText("第 " + currentPage + " / " + totalPages + " 页");
        totalLabel.setText("共 " + totalCount + " 条");
        firstPageBtn.setEnabled(currentPage > 1);
        prevPageBtn.setEnabled(currentPage > 1);
        nextPageBtn.setEnabled(currentPage < totalPages);
        lastPageBtn.setEnabled(currentPage < totalPages);
    }
    
    /**
     * 查看问题详情（只读展示全部字段，长文本可滚动查看全文）
     */
    private void showQuestionDetail(AiQuestion question) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(nullToEmpty(question.getEnvName())));
        addFormRow(form, gbc, 1, new JLabel("ID："), new JLabel(question.getId() != null ? String.valueOf(question.getId()) : ""));
        addFormRow(form, gbc, 2, new JLabel("问题："), readonlyArea(nullToEmpty(question.getQuestion()), 3));
        addFormRow(form, gbc, 3, new JLabel("用户ID："), readonlyField(nullToEmpty(question.getUserId())));
        addFormRow(form, gbc, 4, new JLabel("用户Session："), readonlyArea(nullToEmpty(question.getUserSession()), 2));
        addFormRow(form, gbc, 5, new JLabel("分类："), readonlyField(nullToEmpty(question.getQuestionClassify())));
        addFormRow(form, gbc, 6, new JLabel("训练参数："), readonlyArea(nullToEmpty(question.getTrainingParam()), 4));
        addFormRow(form, gbc, 7, new JLabel("固定答案："), readonlyArea(nullToEmpty(question.getAnswer()), 4));
        addFormRow(form, gbc, 8, new JLabel("开启训练："), new JLabel(question.isTrainingEnabled() ? "开启" : "不开启"));
        addFormRow(form, gbc, 9, new JLabel("优先级别："), new JLabel(String.valueOf(question.getPriority() != null ? question.getPriority() : 0)));
        addFormRow(form, gbc, 10, new JLabel("备注："), readonlyArea(nullToEmpty(question.getRemark()), 3));
        JOptionPane.showMessageDialog(this, form, "问题详情", JOptionPane.PLAIN_MESSAGE);
    }
    
    /**
     * 编辑问题（保存时调用外部更新接口）
     */
    private void editQuestion(AiQuestion question) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextArea questionArea = new JTextArea(nullToEmpty(question.getQuestion()), 3, 30);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        JTextField userIdField = new JTextField(nullToEmpty(question.getUserId()), 20);
        JTextField userSessionField = new JTextField(nullToEmpty(question.getUserSession()), 20);
        JTextField classifyField = new JTextField(nullToEmpty(question.getQuestionClassify()), 20);
        JTextArea trainingParamArea = new JTextArea(nullToEmpty(question.getTrainingParam()), 4, 30);
        trainingParamArea.setLineWrap(true);
        trainingParamArea.setWrapStyleWord(true);
        JTextArea answerArea = new JTextArea(nullToEmpty(question.getAnswer()), 4, 30);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        JComboBox<String> enableCombo = new JComboBox<>(new String[] {"开启", "不开启"});
        enableCombo.setSelectedIndex(question.isTrainingEnabled() ? 0 : 1);
        JTextField priorityField = new JTextField(String.valueOf(question.getPriority() != null ? question.getPriority() : 0), 8);
        
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(nullToEmpty(question.getEnvName())));
        addFormRow(form, gbc, 1, new JLabel("问题："), new JScrollPane(questionArea));
        addFormRow(form, gbc, 2, new JLabel("用户ID："), userIdField);
        addFormRow(form, gbc, 3, new JLabel("用户Session："), userSessionField);
        addFormRow(form, gbc, 4, new JLabel("分类："), classifyField);
        addFormRow(form, gbc, 5, new JLabel("训练参数："), new JScrollPane(trainingParamArea));
        addFormRow(form, gbc, 6, new JLabel("固定答案："), new JScrollPane(answerArea));
        addFormRow(form, gbc, 7, new JLabel("开启训练："), enableCombo);
        addFormRow(form, gbc, 8, new JLabel("优先级别："), priorityField);
        
        int option = JOptionPane.showConfirmDialog(this, form, "编辑问题", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        String newQuestion = questionArea.getText().trim();
        if (newQuestion.isEmpty()) {
            JOptionPane.showMessageDialog(this, "问题内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int newPriority;
        try {
            newPriority = Integer.parseInt(priorityField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "优先级别必须为整数", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newClassify = classifyField.getText().trim();
        String newUserId = userIdField.getText().trim();
        String newUserSession = userSessionField.getText().trim();
        question.setQuestion(newQuestion);
        question.setUserId(newUserId.isEmpty() ? null : newUserId);
        question.setUserSession(newUserSession.isEmpty() ? null : newUserSession);
        question.setQuestionClassify(newClassify.isEmpty() ? null : newClassify);
        question.setTrainingParam(trainingParamArea.getText().trim());
        question.setAnswer(answerArea.getText().trim());
        question.setEnableTraining(enableCombo.getSelectedIndex() == 0 ? AiQuestion.TRAINING_ENABLED : AiQuestion.TRAINING_DISABLED);
        question.setPriority(newPriority);
        AiEnvConfig envConfig = findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(this, "未找到问题所属环境配置 [" + nullToEmpty(question.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (question.getId() == null) {
            JOptionPane.showMessageDialog(this, "该问题缺少 ID，无法调用更新接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        runApiTask("更新问题", () -> {
            String message = AiQuestionApiClient.getInstance().updateQuestion(envConfig, question);
            return () -> {
                invalidateLoadedData();
                refreshQuestionTable();
                setStatus("更新问题成功：" + abbreviate(newQuestion, 30) + "（" + message + "）", true);
            };
        });
    }
    
    /**
     * 删除问题（调用外部删除接口）
     */
    private void deleteQuestion(AiQuestion question) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(this, "未找到问题所属环境配置 [" + nullToEmpty(question.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (question.getId() == null) {
            JOptionPane.showMessageDialog(this, "该问题缺少 ID，无法调用删除接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定删除环境 [" + envConfig.getEnvName() + "] 的问题 [" + abbreviate(question.getQuestion(), 30) + "] 吗？", "确认删除",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        runApiTask("删除问题", () -> {
            String message = AiQuestionApiClient.getInstance().deleteQuestion(envConfig, question.getId());
            return () -> {
                invalidateLoadedData();
                refreshQuestionTable();
                setStatus("已删除问题：" + abbreviate(question.getQuestion(), 30) + "（" + message + "）", true);
            };
        });
    }
    
    /**
     * 触发训练已勾选的问题（勾选跨页 / 跨搜索保留；点击表头复选框可全选当前页）
     * <p>
     * 触发前校验勾选问题必须全部有归属用户ID，存在缺少用户ID的问题时不允许触发；<br> 确认弹窗中选择智能体类型（safety / system / ops / auto，默认 auto），随请求 agentType 字段提交；<br> 按问题所属环境分组调用对应环境配置的训练接口；
     * 单个环境失败不影响其它环境，全部环境完成后汇总提示结果。
     */
    private void triggerTrainingQuestions() {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在列表中勾选要触发训练的问题（点击表头复选框可全选当前页）", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 触发训练要求问题必须有归属用户ID：存在缺少用户ID的问题时不允许触发，提示先通过批量更新补充
        List<AiQuestion> missingUserIdQuestions = new ArrayList<>();
        for (AiQuestion question : checkedQuestions) {
            if (question.getUserId() == null || question.getUserId().isBlank()) {
                missingUserIdQuestions.add(question);
            }
        }
        if (!missingUserIdQuestions.isEmpty()) {
            int maxShown = 10;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < missingUserIdQuestions.size() && i < maxShown; i++) {
                AiQuestion question = missingUserIdQuestions.get(i);
                lines.add("　ID " + question.getId() + "：" + abbreviate(question.getQuestion(), 30));
            }
            if (missingUserIdQuestions.size() > maxShown) {
                lines.add("　…等共 " + missingUserIdQuestions.size() + " 条");
            }
            JOptionPane.showMessageDialog(this,
                    "选中的 " + checkedQuestions.size() + " 条问题中有 " + missingUserIdQuestions.size() + " 条缺少用户ID，不允许触发训练：\n"
                            + String.join("\n", lines) + "\n\n请先通过「批量更新」为这些问题补充用户ID后再触发训练。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 按问题所属环境分组收集 ID（同一环境合并为一次请求）
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : checkedQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(this, "未找到问题所属环境配置 [" + nullToEmpty(entry.getKey()) + "]", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            targets.add(envConfig);
            selectedTotal += entry.getValue().size();
            summaryLines.add("　环境 [" + envConfig.getEnvName() + "] " + entry.getValue().size() + " 条");
        }
        // 确认弹窗：勾选摘要 + 智能体类型下拉
        JComboBox<String> agentTypeCombo = new JComboBox<>(AGENT_TYPES);
        agentTypeCombo.setSelectedItem(DEFAULT_AGENT_TYPE);
        agentTypeCombo.setToolTipText("训练请求 agentType 字段值");
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        JLabel titleLabel = new JLabel("确定触发训练勾选的 " + selectedTotal + " 条问题吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        for (String line : summaryLines) {
            JLabel envLabel = new JLabel(line);
            envLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            confirmPanel.add(envLabel);
        }
        JPanel agentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        agentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        agentPanel.add(new JLabel("智能体类型："));
        agentPanel.add(agentTypeCombo);
        confirmPanel.add(Box.createVerticalStrut(8));
        confirmPanel.add(agentPanel);
        int confirm = JOptionPane.showConfirmDialog(this, confirmPanel, "确认触发训练", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        String agentType = (String) agentTypeCombo.getSelectedItem();
        runApiTask("触发训练", () -> {
            List<String> successDetails = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            for (AiEnvConfig envConfig : targets) {
                List<Long> ids = envIdGroups.get(envConfig.getEnvName());
                try {
                    String message = AiQuestionApiClient.getInstance().triggerTraining(envConfig, ids, agentType);
                    successCount += ids.size();
                    successDetails.add("环境 [" + envConfig.getEnvName() + "] " + ids.size() + " 条：" + message);
                } catch (AiQuestionApiClient.AiApiException ex) {
                    logger.error("环境 [{}] 触发训练失败", envConfig.getEnvName(), ex);
                    errors.add("环境 [" + envConfig.getEnvName() + "]：" + ex.getMessage());
                }
            }
            int successTotal = successCount;
            return () -> {
                // 全部成功时清空勾选；部分 / 全部失败时保留勾选便于修正后重试
                if (errors.isEmpty()) {
                    tableModel.clearChecked();
                }
                updateCheckControls();
                invalidateLoadedData();
                refreshQuestionTable();
                if (errors.isEmpty()) {
                    setStatus("触发训练成功：共 " + successTotal + " 条问题（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    setStatus("触发训练部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this,
                            "已成功触发 " + successTotal + " 条问题训练。\n\n以下环境触发失败：\n" + String.join("\n", errors), "触发训练结果",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    setStatus("触发训练失败：" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "触发训练失败：\n" + String.join("\n", errors), "错误", JOptionPane.ERROR_MESSAGE);
                }
            };
        });
    }
    
    /**
     * 批量更新已勾选问题的用户ID / 用户Session / 训练参数（勾选跨页 / 跨搜索保留）
     * <p>
     * 弹窗中填写的字段才会提交（留空表示不更新该字段，至少填写一项）；<br> 按问题所属环境分组调用批量更新接口，同一环境合并一次请求，单环境失败不影响其它环境。
     */
    private void batchUpdateUserQuestions() {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在列表中勾选要批量更新的问题（点击表头复选框可全选当前页）", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 按问题所属环境分组收集 ID（同一环境合并为一次请求）
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : checkedQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(this, "未找到问题所属环境配置 [" + nullToEmpty(entry.getKey()) + "]", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            targets.add(envConfig);
            selectedTotal += entry.getValue().size();
            summaryLines.add("　环境 [" + envConfig.getEnvName() + "] " + entry.getValue().size() + " 条");
        }
        // 确认弹窗：勾选摘要 + 用户ID / 用户Session / 训练参数 输入（留空表示不更新对应字段）
        // 输入区复用 addFormRow 两列 GridBagLayout：标签右对齐（冒号对齐）、输入框同列起始与等宽对齐
        JTextField userIdField = new JTextField(24);
        JTextField userSessionField = new JTextField(24);
        JTextArea trainingParamArea = new JTextArea(3, 24);
        trainingParamArea.setLineWrap(true);
        trainingParamArea.setWrapStyleWord(true);
        JPanel inputForm = new JPanel(new GridBagLayout());
        GridBagConstraints inputGbc = new GridBagConstraints();
        inputGbc.insets = new Insets(4, 4, 4, 4);
        inputGbc.anchor = GridBagConstraints.WEST;
        addFormRow(inputForm, inputGbc, 0, new JLabel("用户ID："), userIdField);
        addFormRow(inputForm, inputGbc, 1, new JLabel("用户Session："), userSessionField);
        addFormRow(inputForm, inputGbc, 2, new JLabel("训练参数："), new JScrollPane(trainingParamArea));
        inputForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel emptyHintLabel = new JLabel("留空表示不更新对应字段，三个字段至少填写一项");
        emptyHintLabel.setForeground(Color.GRAY);
        emptyHintLabel.setFont(UiConstants.FONT_SANS_11);
        emptyHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        JLabel titleLabel = new JLabel("确定批量更新勾选的 " + selectedTotal + " 条问题吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        for (String line : summaryLines) {
            JLabel envLabel = new JLabel(line);
            envLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            confirmPanel.add(envLabel);
        }
        confirmPanel.add(Box.createVerticalStrut(8));
        confirmPanel.add(inputForm);
        confirmPanel.add(Box.createVerticalStrut(4));
        confirmPanel.add(emptyHintLabel);
        String userId;
        String userSession;
        String trainingParam;
        while (true) {
            int confirm = JOptionPane.showConfirmDialog(this, confirmPanel, "确认批量更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            userId = userIdField.getText().trim();
            userSession = userSessionField.getText().trim();
            trainingParam = trainingParamArea.getText().trim();
            if (!userId.isEmpty() || !userSession.isEmpty() || !trainingParam.isEmpty()) {
                break;
            }
            JOptionPane.showMessageDialog(this, "用户ID、用户Session与训练参数不能同时留空，请至少填写一项", "提示", JOptionPane.WARNING_MESSAGE);
        }
        String targetUserId = userId.isEmpty() ? null : userId;
        String targetUserSession = userSession.isEmpty() ? null : userSession;
        String targetTrainingParam = trainingParam.isEmpty() ? null : trainingParam;
        runApiTask("批量更新", () -> {
            List<String> successDetails = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            for (AiEnvConfig envConfig : targets) {
                List<Long> ids = envIdGroups.get(envConfig.getEnvName());
                try {
                    String message = AiQuestionApiClient.getInstance()
                            .batchUpdateUser(envConfig, ids, targetUserId, targetUserSession, targetTrainingParam);
                    successCount += ids.size();
                    successDetails.add("环境 [" + envConfig.getEnvName() + "] " + ids.size() + " 条：" + message);
                } catch (AiQuestionApiClient.AiApiException ex) {
                    logger.error("环境 [{}] 批量更新失败", envConfig.getEnvName(), ex);
                    errors.add("环境 [" + envConfig.getEnvName() + "]：" + ex.getMessage());
                }
            }
            int successTotal = successCount;
            return () -> {
                // 全部成功时清空勾选；部分 / 全部失败时保留勾选便于修正后重试
                if (errors.isEmpty()) {
                    tableModel.clearChecked();
                }
                updateCheckControls();
                invalidateLoadedData();
                refreshQuestionTable();
                if (errors.isEmpty()) {
                    setStatus("批量更新成功：共 " + successTotal + " 条问题（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    setStatus("批量更新部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "已成功更新 " + successTotal + " 条问题。\n\n以下环境更新失败：\n" + String.join("\n", errors),
                            "批量更新结果", JOptionPane.WARNING_MESSAGE);
                } else {
                    setStatus("批量更新失败：" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "批量更新失败：\n" + String.join("\n", errors), "错误", JOptionPane.ERROR_MESSAGE);
                }
            };
        });
    }
    
    /**
     * 批量删除已勾选的问题（勾选跨页 / 跨搜索保留，删除后不可恢复）
     * <p>
     * 确认弹窗中展示各环境删除条数并附不可恢复警告；<br> 按问题所属环境分组调用删除接口，同一环境合并一次请求，单环境失败不影响其它环境。
     */
    private void batchDeleteQuestions() {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在列表中勾选要批量删除的问题（点击表头复选框可全选当前页）", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 按问题所属环境分组收集 ID（同一环境合并为一次请求）
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : checkedQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(this, "未找到问题所属环境配置 [" + nullToEmpty(entry.getKey()) + "]", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            targets.add(envConfig);
            selectedTotal += entry.getValue().size();
            summaryLines.add("　环境 [" + envConfig.getEnvName() + "] " + entry.getValue().size() + " 条");
        }
        // 确认弹窗：勾选摘要 + 不可恢复警告
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
        JLabel titleLabel = new JLabel("确定批量删除勾选的 " + selectedTotal + " 条问题吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        for (String line : summaryLines) {
            JLabel envLabel = new JLabel(line);
            envLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            confirmPanel.add(envLabel);
        }
        JLabel warningLabel = new JLabel("删除后不可恢复，请谨慎操作");
        warningLabel.setForeground(UiConstants.COLOR_DANGER_LIGHT);
        warningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(Box.createVerticalStrut(6));
        confirmPanel.add(warningLabel);
        int confirm = JOptionPane.showConfirmDialog(this, confirmPanel, "确认批量删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        runApiTask("批量删除", () -> {
            List<String> successDetails = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            for (AiEnvConfig envConfig : targets) {
                List<Long> ids = envIdGroups.get(envConfig.getEnvName());
                try {
                    String message = AiQuestionApiClient.getInstance().deleteQuestions(envConfig, ids);
                    successCount += ids.size();
                    successDetails.add("环境 [" + envConfig.getEnvName() + "] " + ids.size() + " 条：" + message);
                } catch (AiQuestionApiClient.AiApiException ex) {
                    logger.error("环境 [{}] 批量删除失败", envConfig.getEnvName(), ex);
                    errors.add("环境 [" + envConfig.getEnvName() + "]：" + ex.getMessage());
                }
            }
            int successTotal = successCount;
            return () -> {
                // 全部成功时清空勾选；部分 / 全部失败时保留勾选便于修正后重试
                if (errors.isEmpty()) {
                    tableModel.clearChecked();
                }
                updateCheckControls();
                invalidateLoadedData();
                refreshQuestionTable();
                if (errors.isEmpty()) {
                    setStatus("批量删除成功：共 " + successTotal + " 条问题（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    setStatus("批量删除部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "已成功删除 " + successTotal + " 条问题。\n\n以下环境删除失败：\n" + String.join("\n", errors),
                            "批量删除结果", JOptionPane.WARNING_MESSAGE);
                } else {
                    setStatus("批量删除失败：" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "批量删除失败：\n" + String.join("\n", errors), "错误", JOptionPane.ERROR_MESSAGE);
                }
            };
        });
    }
    
    // ────────── 辅助方法 ──────────
    
    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, JComponent field) {
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
    
    /**
     * 创建只读单行文本组件（固定列宽，长文本可滚动选中复制）
     */
    private static JTextField readonlyField(String text) {
        JTextField field = new JTextField(text, 24);
        field.setEditable(false);
        return field;
    }
    
    /**
     * 创建只读多行文本组件（自动换行，配合滚动条查看长文本全文）
     */
    private static JScrollPane readonlyArea(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        return new JScrollPane(area);
    }
    
    /**
     * 读取可编辑下拉框的当前输入值（支持手输与下拉选择）
     */
    private String editableComboText(JComboBox<String> combo) {
        Object editorItem = combo.getEditor().getItem();
        return editorItem != null ? editorItem.toString() : "";
    }
    
    private TableCellRenderer centeredRenderer() {
        return (table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value == null ? "" : value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        };
    }
    
    private static String nullToEmpty(String text) {
        return text != null ? text : "";
    }
    
    /**
     * 长文本单行截断（用于表格单元格与提示信息）
     */
    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String flattened = text.replace("\r", "").replace("\n", " ").trim();
        return flattened.length() <= maxLength ? flattened : flattened.substring(0, maxLength) + "…";
    }
    
    private void setStatus(String message, boolean success) {
        statusLabel.setText(LogUtil.logTime() + message);
        statusLabel.setForeground(success ? UiConstants.COLOR_SUCCESS : UiConstants.COLOR_ERROR);
    }
    
    /**
     * 后台线程执行接口调用，成功后在 EDT 回调刷新界面，失败时提示原因（期间禁用保存按钮防止重复提交）
     */
    private void runApiTask(String actionName, ApiCall apiCall) {
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
                });
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("{}失败", actionName, ex);
                SwingUtilities.invokeLater(() -> {
                    apiBusy = false;
                    setSaveButtonsEnabled(true);
                    String reason = ex.getMessage();
                    setStatus(actionName + "失败：" + reason, false);
                    JOptionPane.showMessageDialog(this, actionName + "失败：" + reason, "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ai-api-task").start();
    }
    
    private void setSaveButtonsEnabled(boolean enabled) {
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
    
    /**
     * 接口任务：后台执行并返回成功后需要在 EDT 执行的回调
     */
    @FunctionalInterface
    private interface ApiCall {
        
        Runnable call() throws AiQuestionApiClient.AiApiException;
    }
    
    // ────────── 手动录入单行组件 ──────────
    
    /**
     * 手动录入一行录入组件集合：问题、用户ID、分类、开启训练、优先级别、训练参数、固定答案、删除按钮
     */
    private class QuestionEntryRow {
        
        private final CustomTextField questionField;
        
        private final CustomTextField userIdField;
        
        private final FilterComboBox<String> classifyCombo;
        
        private final JComboBox<String> enableTrainingCombo;
        
        private final CustomTextField priorityField;
        
        private final JTextArea trainingParamArea;
        
        private final JTextArea answerArea;
        
        private final JButton deleteRowBtn;
        
        private QuestionEntryRow(int rowIndex) {
            questionField = new CustomTextField("输入问题");
            questionField.setMinWidth(QUESTION_FIELD_MIN_WIDTH);
            
            // 用户ID（非必填）：第 2 行起支持『同上』，留空则不提交该字段
            userIdField = new CustomTextField(rowIndex == 0 ? "用户ID（可留空）" : SAME_AS_ABOVE);
            
            // 分类：第 2 行起支持『同上』，同时提供历史分类建议
            classifyCombo = new FilterComboBox<>();
            if (rowIndex > 0) {
                classifyCombo.addItem(SAME_AS_ABOVE);
            }
            for (String classify : classifySuggestions) {
                classifyCombo.addItem(classify);
            }
            if (rowIndex > 0) {
                classifyCombo.setSelectedItem(SAME_AS_ABOVE);
            }
            
            // 开启训练：第 2 行起支持『同上』，默认开启
            enableTrainingCombo = new JComboBox<>();
            if (rowIndex > 0) {
                enableTrainingCombo.addItem(SAME_AS_ABOVE);
            }
            enableTrainingCombo.addItem("开启");
            enableTrainingCombo.addItem("不开启");
            if (rowIndex > 0) {
                enableTrainingCombo.setSelectedItem(SAME_AS_ABOVE);
            } else {
                enableTrainingCombo.setSelectedItem("开启");
            }
            
            // 优先级别：第 2 行起支持『同上』，默认 0
            priorityField = new CustomTextField(rowIndex == 0 ? "0" : SAME_AS_ABOVE);
            
            // 训练参数：多行文本
            trainingParamArea = new JTextArea(2, 10);
            trainingParamArea.setLineWrap(true);
            trainingParamArea.setWrapStyleWord(true);
            trainingParamArea.setFont(UiConstants.FONT_SANS_11);
            
            // 固定答案：多行文本（问题命中时优先返回的固定答案）
            answerArea = new JTextArea(2, 10);
            answerArea.setLineWrap(true);
            answerArea.setWrapStyleWord(true);
            answerArea.setFont(UiConstants.FONT_SANS_11);
            
            // 删除本行：点击后移除该行，其它行已录入内容保持不变
            deleteRowBtn = ButtonFactory.createLink("删除");
            deleteRowBtn.setToolTipText("删除本行");
            deleteRowBtn.addActionListener(e -> removeEntryRow(this));
        }
    }
    
    // ────────── 下屏表格模型与渲染器 ──────────
    
    /**
     * 问题列表表格模型
     */
    private static class QuestionTableModel extends AbstractTableModel {
        
        private final String[] columns = {"选择", "ID", "问题", "分类", "训练参数", "固定答案", "开启训练", "备注", "操作"};
        
        private final List<AiQuestion> questions = new ArrayList<>();
        
        /**
         * 已勾选问题的键集合（环境名#ID 复合键，翻页 / 搜索过滤后保留勾选）
         */
        private final Set<String> checkedKeys = new LinkedHashSet<>();
        
        void setData(List<AiQuestion> list) {
            questions.clear();
            questions.addAll(list);
            fireTableDataChanged();
        }
        
        AiQuestion getQuestionAt(int row) {
            return questions.get(row);
        }
        
        /**
         * 勾选键：环境名 + 问题 ID 组合（勾选状态按环境隔离）
         */
        private static String checkKey(AiQuestion question) {
            return question.getEnvName() + "#" + question.getId();
        }
        
        boolean isChecked(AiQuestion question) {
            return question.getId() != null && checkedKeys.contains(checkKey(question));
        }
        
        /**
         * 切换指定行的勾选状态（无 ID 的问题不可勾选）
         */
        void toggleChecked(int row) {
            AiQuestion question = questions.get(row);
            if (question.getId() == null) {
                return;
            }
            String key = checkKey(question);
            if (!checkedKeys.remove(key)) {
                checkedKeys.add(key);
            }
            fireTableCellUpdated(row, COL_CHECK);
        }
        
        /**
         * 当前页是否已全部勾选（无可勾选行时视为未全选）
         */
        boolean isCurrentPageAllChecked() {
            boolean hasCheckable = false;
            for (AiQuestion question : questions) {
                if (question.getId() == null) {
                    continue;
                }
                hasCheckable = true;
                if (!checkedKeys.contains(checkKey(question))) {
                    return false;
                }
            }
            return hasCheckable;
        }
        
        /**
         * 切换当前页全选：已全选则取消勾选，否则全部勾选
         */
        void toggleCheckCurrentPage() {
            if (isCurrentPageAllChecked()) {
                for (AiQuestion question : questions) {
                    if (question.getId() != null) {
                        checkedKeys.remove(checkKey(question));
                    }
                }
            } else {
                for (AiQuestion question : questions) {
                    if (question.getId() != null) {
                        checkedKeys.add(checkKey(question));
                    }
                }
            }
            if (!questions.isEmpty()) {
                fireTableRowsUpdated(0, questions.size() - 1);
            }
        }
        
        /**
         * 已勾选问题总数（跨页统计）
         */
        int getCheckedCount() {
            return checkedKeys.size();
        }
        
        /**
         * 从全量数据中收集已勾选的问题（跨页）
         */
        List<AiQuestion> collectChecked(List<AiQuestion> source) {
            List<AiQuestion> result = new ArrayList<>();
            for (AiQuestion question : source) {
                if (question.getId() != null && checkedKeys.contains(checkKey(question))) {
                    result.add(question);
                }
            }
            return result;
        }
        
        /**
         * 清理已勾选但已不存在的问题（数据刷新后调用）
         */
        void retainChecked(Collection<AiQuestion> validQuestions) {
            Set<String> validKeys = new HashSet<>();
            for (AiQuestion question : validQuestions) {
                if (question.getId() != null) {
                    validKeys.add(checkKey(question));
                }
            }
            checkedKeys.retainAll(validKeys);
        }
        
        /**
         * 清空全部勾选
         */
        void clearChecked() {
            checkedKeys.clear();
            if (!questions.isEmpty()) {
                fireTableRowsUpdated(0, questions.size() - 1);
            }
        }
        
        @Override
        public int getRowCount() {
            return questions.size();
        }
        
        @Override
        public int getColumnCount() {
            return columns.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columns[column];
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            AiQuestion question = questions.get(row);
            return switch (column) {
                case COL_CHECK -> question.getId() != null ? isChecked(question) : null;
                case COL_ID -> question.getId();
                case COL_QUESTION -> nullToEmpty(question.getQuestion());
                case COL_CLASSIFY -> nullToEmpty(question.getQuestionClassify());
                case COL_TRAINING_PARAM -> nullToEmpty(question.getTrainingParam());
                case COL_ANSWER -> nullToEmpty(question.getAnswer());
                case COL_ENABLE -> question.isTrainingEnabled() ? "开启" : "不开启";
                case COL_REMARK -> nullToEmpty(question.getRemark());
                default -> "";
            };
        }
    }
    
    /**
     * 长文本单元格渲染器：单行截断显示 + 悬浮提示展示全文
     */
    private static class TextCellRenderer extends DefaultTableCellRenderer {
        
        private final int maxLength;
        
        private TextCellRenderer(int maxLength) {
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
     * 复选框列渲染器：居中复选框，无 ID 的行置灰不可勾选 （点击切换由 {@link #handleTableClick} 处理，不进入单元格编辑）
     */
    private static class CheckBoxCellRenderer implements TableCellRenderer {
        
        private final JCheckBox checkBox = new JCheckBox();
        
        private CheckBoxCellRenderer() {
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
     * 表头复选框渲染器：勾选状态反映当前页是否已全选（点击切换由表头鼠标监听处理）
     */
    private class HeaderCheckBoxRenderer implements TableCellRenderer {
        
        private final JCheckBox checkBox = new JCheckBox();
        
        private HeaderCheckBoxRenderer() {
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            checkBox.setSelected(tableModel.isCurrentPageAllChecked());
            checkBox.setEnabled(tableModel.getRowCount() > 0);
            return checkBox;
        }
    }
    
    /**
     * 操作列渲染器：胶囊样式的详情 / 编辑 / 删除按钮，悬浮时按钮以饱和色填充并反白文字 （点击区域三等分：左查看详情、中编辑、右删除，与 {@link #actionZoneAt} 判定保持一致）
     */
    private class ActionCellRenderer implements TableCellRenderer {
        
        private final JPanel panel = new JPanel(new GridLayout(1, 3, 6, 0));
        
        private final ActionLabel viewLabel = new ActionLabel("详情", UiConstants.COLOR_SUCCESS_LIGHT, UiConstants.COLOR_SUCCESS);
        
        private final ActionLabel editLabel = new ActionLabel("编辑", UiConstants.COLOR_PRIMARY_LIGHT, UiConstants.COLOR_PRIMARY);
        
        private final ActionLabel deleteLabel = new ActionLabel("删除", UiConstants.COLOR_DANGER_LIGHT, UiConstants.COLOR_DANGER);
        
        private ActionCellRenderer() {
            viewLabel.setToolTipText("查看问题详情（全部字段）");
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(4, 6, 4, 6));
            panel.add(viewLabel);
            panel.add(editLabel);
            panel.add(deleteLabel);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            viewLabel.setHovered(row == hoverActionRow && hoverActionZone == ACTION_VIEW);
            editLabel.setHovered(row == hoverActionRow && hoverActionZone == ACTION_EDIT);
            deleteLabel.setHovered(row == hoverActionRow && hoverActionZone == ACTION_DELETE);
            return panel;
        }
    }
    
    /**
     * 操作按钮标签：圆角胶囊底 + 彩色描边，常态亮色文字、悬浮时饱和填充反白文字
     */
    private static class ActionLabel extends JLabel {
        
        /**
         * 胶囊圆角半径
         */
        private static final int ARC = 10;
        
        private final Color normalTextColor;
        
        private final Color normalFillColor;
        
        private final Color normalBorderColor;
        
        private final Color hoverFillColor;
        
        private boolean hovered;
        
        /**
         * @param text      按钮文字
         * @param textColor 常态文字色（暗色主题下用亮色变体保证清晰对比度）
         * @param fillColor 悬浮填充色（饱和主色，填充时文字反白）
         */
        private ActionLabel(String text, Color textColor, Color fillColor) {
            super(text, SwingConstants.CENTER);
            this.normalTextColor = textColor;
            this.normalFillColor = withAlpha(fillColor, 30);
            this.normalBorderColor = withAlpha(textColor, 110);
            this.hoverFillColor = fillColor;
            // 跟随 FlatLaf 默认字体（自动适配高 DPI 缩放，避免栅格字体发虚）
            Font uiFont = UIManager.getFont("Label.font");
            setFont(uiFont != null ? uiFont.deriveFont(Font.BOLD) : UiConstants.FONT_SANS_12_BOLD);
            setOpaque(false);
            setForeground(normalTextColor);
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
            // 文字单独开启抗锯齿绘制，保证小字号清晰
            Graphics2D textGraphics = (Graphics2D) g.create();
            try {
                textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paintComponent(textGraphics);
            } finally {
                textGraphics.dispose();
            }
        }
    }
}
