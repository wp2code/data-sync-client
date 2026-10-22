/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.ui;

import com.datasync.components.CheckBoxComboBox;
import com.datasync.components.ChildLayoutPanel;
import com.datasync.components.CustomTextField;
import com.datasync.components.FilterComboBox;
import com.datasync.core.AiQuestionApiClient;
import com.datasync.model.AiEnvConfig;
import com.datasync.model.AiMetaConfig;
import com.datasync.model.AiQuestion;
import com.datasync.util.ExcelExportUtil;
import com.datasync.util.ExcelQuestionUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 问题训练页签面板：上屏问题录入（手动录入 / 批量粘贴）+ 下屏问题列表（筛选、分页、CRUD、训练触发、批量操作、导出）
 *
 * @author liuweiping
 * @date 2026-09-16
 */
public class QuestionTrainingPanel {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionTrainingPanel.class);
    
    // ── 常量 ──
    private static final int DEFAULT_ROW_COUNT = 5;
    
    private static final int QUESTION_FIELD_MIN_WIDTH = 250;
    
    private static final String SAME_AS_ABOVE = "同上";
    
    private static final String[] AGENT_TYPES = {"safety", "system", "ops", "auto"};
    
    private static final String DEFAULT_AGENT_TYPE = "auto";
    
    /**
     * 触发训练弹窗训练模型 / 意图分析模型下拉的首项：表示不由客户端指定该参数（整体清空输入框时不提交该字段）
     */
    private static final String AUTO_META_OPTION = "auto";
    
    /**
     * 触发训练弹窗 AI 元数据下拉宽度（避免选项异步回填前弹窗被压缩得过窄）
     */
    private static final int META_COMBO_WIDTH = 240;
    
    private static final String AGENT_TYPE_TIP = "下拉选择或手动输入智能体类型（首项 auto 为默认，选项取自获取AI元数据接口 agentList，展示「名称（描述）」，接口未返回时回退内置类型）；不允许为空";
    
    private static final String CORE_LLM_TIP = "下拉选择或手动输入训练模型名称（首项 auto 为默认，选项取自获取AI元数据接口 coreLlmNameList）；不允许为空";
    
    private static final String INTENTION_LLM_TIP = "下拉选择或手动输入意图分析模型名称（首项 auto 为默认，选项取自获取AI元数据接口 intentionLlmNameList）；不允许为空";
    
    private static final String ALL_CLASSIFY = "全部分类";
    
    private static final String ALL_TRAINING_STATUS = "全部状态";
    
    private static final String ALL_PROJECT = "全部项目";
    
    /**
     * 批量更新「所属项目」下拉首项：表示保留原有项目不变（该字段不提交）
     */
    private static final String BATCH_NO_UPDATE_OPTION = "不更新";
    
    private static final String ALL_USER = "全部用户";
    
    /**
     * 用户筛选「全局」选项实际传给后端的参数值（与 {@link AiQuestion#GLOBAL_DISPLAY_TEXT} 配对使用）
     */
    private static final String GLOBAL_USER_FILTER_VALUE = "0";
    
    private static final String ALL_AUTO_TRAINING = "全部";
    
    private static final String AUTO_TRAINING_ALLOW = "允许";
    
    private static final String AUTO_TRAINING_NOT_ALLOW = "不允许";
    
    private static final String[] TRAINING_STATUS_TEXTS = {"默认", "待训练", "成功", "失败", "同步回复成功", "训练中", "超时", "同步回复失败",
            "训练已提交", "训练中止"};
    
    private static final int[] TRAINING_STATUS_VALUES = {AiQuestion.TRAINING_STATUS_INITIAL, AiQuestion.TRAINING_STATUS_PENDING, 0, 1, 2, 3, 4, 5, 6,
            AiQuestion.TRAINING_STATUS_STOPPED};
    
    /**
     * 智能体类型 / 训练模型 / 意图识别模型为空时的展示文案
     */
    private static final String DEFAULT_DISPLAY_TEXT = "默认";
    
    
    private static final Comparator<AiQuestion> QUESTION_COMPARATOR = (a, b) -> {
        if (a.getUpdateTime() != null && b.getUpdateTime() != null) {
            return b.getUpdateTime().compareTo(a.getUpdateTime());
        }
        if (a.getUpdateTime() != null) {
            return -1;
        }
        if (b.getUpdateTime() != null) {
            return 1;
        }
        long idA = a.getId() != null ? a.getId() : 0L;
        long idB = b.getId() != null ? b.getId() : 0L;
        return Long.compare(idB, idA);
    };
    
    // ── 表格列索引 ──
    private static final int COL_CHECK = 0;
    
    private static final int COL_ID = 1;
    
    private static final int COL_QUESTION = 2;
    
    private static final int COL_CLASSIFY = 3;
    
    private static final int COL_USER = 4;
    
    private static final int COL_PROJECT = 5;
    
    private static final int COL_TRAINING_PARAM = 6;
    
    private static final int COL_TRAINING_NO = 7;
    
    private static final int COL_ANSWER = 8;
    
    private static final int COL_ANSWER_ID = 9;
    
    private static final int COL_AUTO_TRAINING = 10;
    
    private static final int COL_TRAINING_MODEL = 11;
    
    private static final int COL_TRAINING_STATUS = 12;
    
    private static final int COL_SUBMIT_TRAINING_TIME = 13;
    
    private static final int COL_START_TRAINING_TIME = 14;
    
    private static final int COL_LAST_TRAINING_TIME = 15;
    
    private static final int COL_TRAINING_COST = 16;
    
    private static final int COL_REMARK = 17;
    
    private static final int COL_ACTION = 18;
    
    private static final int ACTION_COLUMN_WIDTH = 260;
    
    // ── 字段 ──
    private final AiQuestionMangerDialog dialog;
    
    private final List<QuestionEntryRow> entryRows = new ArrayList<>();
    
    private JPanel manualGridPanel;
    
    private JTextArea pasteQuestionArea;
    
    private FilterComboBox<String> pasteUserIdField;
    
    private final List<String> pasteSelectedProjects = new ArrayList<>();
    
    private JButton pasteProjectBtn;
    
    private JLabel parsePreviewLabel;
    
    private FilterComboBox<String> pasteClassifyCombo;
    
    private JComboBox<String> pasteAutoTrainingCombo;
    
    private JTextArea pasteTrainingParamArea;
    
    private CustomTextField searchField;
    
    private JComboBox<String> classifyFilterCombo;
    
    private boolean suppressClassifyEvents = false;
    
    private JComboBox<String> projectFilterCombo;
    
    private boolean suppressProjectFilterEvents = false;
    
    private JComboBox<String> userFilterCombo;
    
    private boolean suppressUserFilterEvents = false;
    
    private CheckBoxComboBox<Integer> statusFilterCombo;
    
    private boolean suppressQuestionFilterEvents = false;
    
    private JComboBox<String> autoTrainingFilterCombo;
    
    private JComboBox<String> pageSizeCombo;
    
    private JTable questionTable;
    
    private QuestionTableModel tableModel;
    
    private int hoverActionRow = -1;
    
    private int hoverActionZone = -1;
    
    private JButton firstPageBtn;
    
    private JButton prevPageBtn;
    
    private JButton nextPageBtn;
    
    private JButton lastPageBtn;
    
    private JLabel pageInfoLabel;
    
    private JLabel totalLabel;
    
    private JLabel checkedCountLabel;
    
    private int currentPage = 1;
    
    private int totalPages = 1;
    
    private long totalCount = 0;
    
    private int pageSize = AiQuestionMangerDialog.DEFAULT_PAGE_SIZE;
    
    private final List<AiQuestion> allQuestions = new ArrayList<>();
    
    private boolean questionsLoaded = false;
    
    private Set<Integer> loadedStatusFilter;
    
    private JButton manualSaveBtn;
    
    private JButton pasteSaveBtn;
    
    private JButton importExcelBtn;
    
    /**
     * 训练耗时查询弹窗（单实例复用）
     */
    private TrainingCastTimeDialog trainingCastTimeDialog;
    
    /**
     * 分页栏实时训练耗时标签（弹窗自动刷新且批次训练进行中时展示）
     */
    private JLabel trainingCostLabel;
    
    /**
     * 触发训练弹窗打开时构建的 AI 元数据下拉组（元数据异步返回后回填选项，避免弹窗先于接口返回弹出时下拉为空）
     */
    private MetaComboGroup openMetaComboGroup;
    
    // ────────── 构造 ──────────
    
    QuestionTrainingPanel(AiQuestionMangerDialog dialog) {
        this.dialog = dialog;
    }
    
    // ────────── 面板构建 ──────────
    
    JComponent buildQuestionTrainingPanel() {
        JPanel entryPanel = new JPanel(new BorderLayout(6, 0));
        JTabbedPane entryTabs = new JTabbedPane();
        entryTabs.setTabPlacement(JTabbedPane.LEFT);
        entryTabs.addTab("手动录入", buildManualEntryPanel());
        entryTabs.addTab("批量粘贴录入", buildPasteEntryPanel());
        entryTabs.setSelectedIndex(1);
        entryPanel.add(entryTabs, BorderLayout.CENTER);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, entryPanel, buildQuestionListPanel());
        splitPane.setResizeWeight(0.25);
        splitPane.setDividerLocation(0.25);
        return splitPane;
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
        JLabel hintLabel = new JLabel("提示：所属用户选填，问题为空的行自动忽略；支持 Excel 导入（先『下载模板』）");
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
    
    private void addManualGridHeader() {
        String[] headers = {"问题", "所属用户", "所属项目", "分类", "自动训练", "训练参数", "操作"};
        double[] weights = {3.0, 0.4, 0.35, 0.35, 0, 0.5, 0};
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
    
    private void addEntryRow() {
        int rowIndex = entryRows.size();
        QuestionEntryRow row = new QuestionEntryRow(rowIndex);
        entryRows.add(row);
        addRowComponents(row, rowIndex);
        manualGridPanel.revalidate();
        manualGridPanel.repaint();
    }
    
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
        row.userIdField.setPreferredSize(new Dimension(160, 26));
        manualGridPanel.add(row.userIdField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.35;
        if (row.projectSameAsAbove != null) {
            JPanel projectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            projectPanel.setOpaque(false);
            projectPanel.add(row.projectBtn);
            projectPanel.add(row.projectSameAsAbove);
            manualGridPanel.add(projectPanel, gbc);
        } else {
            manualGridPanel.add(row.projectBtn, gbc);
        }
        gbc.gridx = 3;
        gbc.weightx = 0.35;
        manualGridPanel.add(row.classifyCombo, gbc);
        gbc.gridx = 4;
        gbc.weightx = 0;
        row.autoTrainingCombo.setPreferredSize(new Dimension(92, 26));
        manualGridPanel.add(row.autoTrainingCombo, gbc);
        gbc.gridx = 5;
        gbc.weightx = 0.5;
        manualGridPanel.add(row.trainingParamArea, gbc);
        gbc.gridx = 6;
        gbc.weightx = 0;
        manualGridPanel.add(row.deleteRowBtn, gbc);
    }
    
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
    
    private List<AiQuestion> collectManualQuestions(String envName, List<String> errors) {
        List<AiQuestion> questions = new ArrayList<>();
        String resolvedClassify = null;
        String resolvedUserId = null;
        int resolvedAutoTraining = AiQuestion.AUTO_TRAINING_ALLOWED;
        List<String> resolvedProjectCodes = null;
        for (QuestionEntryRow row : entryRows) {
            String question = row.questionField.getText().trim();
            if (question.isEmpty()) {
                continue;
            }
            String userId = AiQuestionMangerDialog.extractUserIdFromCombo(row.userIdField.getSelectedItem());
            if (!userId.isEmpty() && !SAME_AS_ABOVE.equals(userId)) {
                resolvedUserId = userId;
            }
            if (row.projectSameAsAbove != null && row.projectSameAsAbove.isSelected()) {
                // 继承上一行的 resolvedProjectCodes
            } else if (!row.selectedProjectCodes.isEmpty()) {
                resolvedProjectCodes = new ArrayList<>(row.selectedProjectCodes);
            }
            String classify = dialog.editableComboText(row.classifyCombo).trim();
            if (!classify.isEmpty() && !SAME_AS_ABOVE.equals(classify)) {
                resolvedClassify = classify;
            }
            Object autoTrainingSelection = row.autoTrainingCombo.getSelectedItem();
            String autoTrainingText = autoTrainingSelection != null ? autoTrainingSelection.toString() : "允许";
            if ("允许".equals(autoTrainingText)) {
                resolvedAutoTraining = AiQuestion.AUTO_TRAINING_ALLOWED;
            } else if ("不允许".equals(autoTrainingText)) {
                resolvedAutoTraining = AiQuestion.AUTO_TRAINING_NOT_ALLOWED;
            }
            AiQuestion item = new AiQuestion();
            item.setEnvName(envName);
            item.setQuestion(question);
            item.setUserId(resolvedUserId);
            item.setProjectCodeList(resolvedProjectCodes == null || resolvedProjectCodes.isEmpty() ? null : new ArrayList<>(resolvedProjectCodes));
            item.setQuestionClassify(resolvedClassify);
            item.setTrainingParam(row.trainingParamArea.getText().trim());
            item.setAnswer("");
            item.setEnableTraining(AiQuestion.TRAINING_ENABLED);
            item.setAllowAutoTraining(resolvedAutoTraining);
            item.setPriority(0);
            questions.add(item);
        }
        return questions;
    }
    
    private void saveManualQuestions() {
        AiEnvConfig envConfig = dialog.requireSelectedEnvConfig();
        if (envConfig == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        List<AiQuestion> questions = collectManualQuestions(envConfig.getEnvName(), errors);
        if (!errors.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, String.join("\n", errors), "录入有误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (questions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请至少录入一条问题", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        dialog.runApiTask("批量保存", () -> {
            String message = AiQuestionApiClient.getInstance().saveQuestions(envConfig, questions);
            return () -> {
                resetManualRows();
                currentPage = 1;
                dialog.invalidateLoadedData();
                dialog.refreshQuestionTable();
                dialog.setStatus("手动录入成功保存 " + questions.size() + " 条问题到环境 [" + envConfig.getEnvName() + "]：" + message, true);
            };
        });
    }
    
    // ────────── Excel 导入导出 ──────────
    
    private void downloadExcelTemplate() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("保存 Excel 导入模板");
        chooser.setSelectedFile(new File("AI问题导入模板.xlsx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File outputFile = chooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".xlsx")) {
            outputFile = new File(outputFile.getParentFile(), outputFile.getName() + ".xlsx");
        }
        if (outputFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(dialog, "文件已存在，是否覆盖？\n" + outputFile.getAbsolutePath(), "确认覆盖",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            ExcelQuestionUtil.generateTemplate(outputFile);
        } catch (Exception ex) {
            logger.error("生成 Excel 导入模板失败", ex);
            dialog.setStatus("模板下载失败：" + ex.getMessage(), false);
            JOptionPane.showMessageDialog(dialog, "生成模板失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dialog.setStatus("模板已保存：" + outputFile.getAbsolutePath(), true);
        int open = JOptionPane.showConfirmDialog(dialog, "模板已保存到：\n" + outputFile.getAbsolutePath() + "\n\n是否立即打开查看？", "下载成功",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (open == JOptionPane.YES_OPTION) {
            dialog.openOutputFile(outputFile);
        }
    }
    
    private void importFromExcel() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
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
            int confirm = JOptionPane.showConfirmDialog(dialog, "当前手动录入的内容将被导入的数据覆盖，是否继续？", "确认导入",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要导入的 Excel 文件");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx, *.xls)", "xlsx", "xls"));
        if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File inputFile = chooser.getSelectedFile();
        dialog.apiBusy = true;
        dialog.setSaveButtonsEnabled(false);
        dialog.setStatus("正在解析 Excel 文件 " + inputFile.getName() + "…", true);
        new Thread(() -> {
            try {
                ExcelQuestionUtil.ParseResult parseResult = ExcelQuestionUtil.parseQuestions(inputFile);
                SwingUtilities.invokeLater(() -> {
                    dialog.apiBusy = false;
                    dialog.setSaveButtonsEnabled(true);
                    applyExcelParseResult(inputFile, parseResult);
                    dialog.resumePendingReloads();
                });
            } catch (Exception ex) {
                logger.error("解析 Excel 文件失败: {}", inputFile.getAbsolutePath(), ex);
                SwingUtilities.invokeLater(() -> {
                    dialog.apiBusy = false;
                    dialog.setSaveButtonsEnabled(true);
                    dialog.setStatus("Excel 导入失败：" + ex.getMessage(), false);
                    JOptionPane.showMessageDialog(dialog, "解析 Excel 文件失败：" + ex.getMessage()
                                    + "\n\n请确认文件为有效的 Excel（.xlsx / .xls）且未被其它程序占用，并按模板格式填写。", "导入失败",
                            JOptionPane.ERROR_MESSAGE);
                    dialog.resumePendingReloads();
                });
            }
        }, "excel-import").start();
    }
    
    private void applyExcelParseResult(File inputFile, ExcelQuestionUtil.ParseResult parseResult) {
        List<String> errors = parseResult.getErrors();
        if (!errors.isEmpty()) {
            String detail = errors.size() > 10 ? String.join("\n", errors.subList(0, 10)) + "\n…（共 " + errors.size() + " 处错误）"
                    : String.join("\n", errors);
            dialog.setStatus("Excel 导入失败：" + errors.size() + " 处数据有误，未导入任何数据", false);
            JOptionPane.showMessageDialog(dialog, "以下数据有误，请修正后重新导入（未导入任何数据）：\n\n" + detail, "导入有误",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> questions = parseResult.getQuestions();
        if (questions.isEmpty()) {
            dialog.setStatus("Excel 中未解析到问题数据：" + inputFile.getName(), false);
            JOptionPane.showMessageDialog(dialog, "未从文件中解析到任何问题，请确认已填写数据行（问题为空的行自动忽略）。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        fillManualRows(questions);
        dialog.setStatus("已从 " + inputFile.getName() + " 导入 " + questions.size() + " 条问题，核对无误后点击『批量保存』提交", true);
    }
    
    private void fillManualRows(List<AiQuestion> questions) {
        entryRows.clear();
        manualGridPanel.removeAll();
        addManualGridHeader();
        List<String> prevProjectCodes = null;
        for (AiQuestion question : questions) {
            addEntryRow();
            QuestionEntryRow row = entryRows.get(entryRows.size() - 1);
            row.questionField.setText(AiQuestionMangerDialog.nullToEmpty(question.getQuestion()));
            row.userIdField.getEditor().setItem(AiQuestionMangerDialog.nullToEmpty(question.getUserId()));
            row.classifyCombo.getEditor().setItem(AiQuestionMangerDialog.nullToEmpty(question.getQuestionClassify()));
            row.trainingParamArea.setText(AiQuestionMangerDialog.nullToEmpty(question.getTrainingParam()));
            List<String> currentCodes = question.getProjectCodeList();
            if (row.projectSameAsAbove != null && currentCodes != null && currentCodes.equals(prevProjectCodes)) {
                // 保持『同上』选中状态
            } else if (row.projectSameAsAbove != null && currentCodes == null && prevProjectCodes == null) {
                // 都为空，保持『同上』
            } else {
                if (row.projectSameAsAbove != null) {
                    row.projectSameAsAbove.setSelected(false);
                    row.projectBtn.setEnabled(true);
                }
                if (currentCodes != null && !currentCodes.isEmpty()) {
                    row.selectedProjectCodes.addAll(currentCodes);
                    row.projectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(currentCodes));
                }
            }
            prevProjectCodes = currentCodes;
        }
        manualGridPanel.revalidate();
        manualGridPanel.repaint();
    }
    
    private void exportQuestionsToExcel() {
        if (allQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "当前没有可导出的问题数据", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String keyword = searchField.getText().trim().toLowerCase();
        String classifyFilter = selectedClassifyFilter();
        String projectFilter = selectedProjectFilter();
        String userFilter = selectedUserFilter();
        Set<Integer> statusFilter = selectedTrainingStatusFilter();
        Integer autoTrainingFilter = selectedAutoTrainingFilter();
        List<AiQuestion> filtered = new ArrayList<>();
        for (AiQuestion item : allQuestions) {
            if (matchesKeyword(item, keyword) && matchesClassify(item, classifyFilter) && matchesProject(item, projectFilter) && matchesUserFilter(
                    item, userFilter) && matchesTrainingStatus(item, statusFilter) && matchesAutoTraining(item, autoTrainingFilter)) {
                filtered.add(item);
            }
        }
        if (filtered.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "当前筛选条件下没有可导出的问题", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> exportData;
        int checkedCount = tableModel.getCheckedCount();
        if (checkedCount > 0) {
            String[] options = {"仅导出勾选（" + checkedCount + " 条）", "导出全部筛选（" + filtered.size() + " 条）", "取消"};
            int choice = JOptionPane.showOptionDialog(dialog, "已勾选 " + checkedCount + " 条数据，请选择导出范围：", "导出范围",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.CANCEL_OPTION || choice < 0) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                exportData = tableModel.collectChecked(filtered);
            } else {
                exportData = filtered;
            }
        } else {
            exportData = filtered;
        }
        if (exportData.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "没有可导出的数据", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        exportData.sort(QUESTION_COMPARATOR);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出问题列表");
        chooser.setSelectedFile(new File("问题列表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx"));
        chooser.setFileFilter(new FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx"));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File outputFile = chooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".xlsx")) {
            outputFile = new File(outputFile.getParentFile(), outputFile.getName() + ".xlsx");
        }
        if (outputFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(dialog, "文件已存在，是否覆盖？\n" + outputFile.getAbsolutePath(), "确认覆盖",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            Map<String, String> userIdDisplay = dialog.buildUserIdDisplayMap();
            ExcelExportUtil.exportQuestions(outputFile, exportData, userIdDisplay);
        } catch (Exception ex) {
            logger.error("导出问题列表失败", ex);
            dialog.setStatus("导出失败：" + ex.getMessage(), false);
            JOptionPane.showMessageDialog(dialog, "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dialog.setStatus("问题列表已导出：" + outputFile.getAbsolutePath() + "（共 " + exportData.size() + " 条）", true);
        int open = JOptionPane.showConfirmDialog(dialog, "导出成功！\n" + outputFile.getAbsolutePath() + "\n\n是否立即打开查看？", "导出完成",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (open == JOptionPane.YES_OPTION) {
            dialog.openOutputFile(outputFile);
        }
    }
    
    // ────────── 上屏：批量粘贴录入 ──────────
    
    // ────────── 训练耗时查询 ──────────
    
    /**
     * 打开训练耗时查询弹窗（默认取最新批次号，按默认间隔自动开启自动刷新）
     */
    private void openTrainingCastTimeDialog() {
        AiEnvConfig envConfig = dialog.envCombo.getSelectedItem() instanceof AiEnvConfig config ? config : null;
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "请先在右上角选择环境", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String latestTrainingNo = latestTrainingNo(envConfig);
        if (trainingCastTimeDialog == null || !trainingCastTimeDialog.isDisplayable()) {
            trainingCastTimeDialog = new TrainingCastTimeDialog(dialog, envConfig, latestTrainingNo);
        } else {
            trainingCastTimeDialog.updateEnvConfig(envConfig);
        }
        trainingCastTimeDialog.setBatchInProgressChecker(this::isBatchTrainingInProgress);
        trainingCastTimeDialog.setAutoRefreshListener(new TrainingCastTimeDialog.AutoRefreshListener() {
            
            @Override
            public void onAutoRefreshed(String trainingNo, AiQuestionApiClient.TrainingCastTime castTime) {
                // 训练未完成时在分页栏实时展示（finishTraining=false 表示批次仍在训练）；maxLastTime 缺失时展示统计中占位
                if (!castTime.finishTraining()) {
                    if (castTime.maxLastTime() > 0) {
                        trainingCostLabel.setText("批次 [" + trainingNo + "] 训练耗时：" + formatCastTime(castTime));
                    } else {
                        trainingCostLabel.setText("批次 [" + trainingNo + "] 训练耗时统计中…");
                    }
                    trainingCostLabel.setVisible(true);
                } else {
                    trainingCostLabel.setVisible(false);
                }
            }
            
            @Override
            public void onAutoRefreshStopped(String trainingNo) {
                trainingCostLabel.setVisible(false);
            }
        });
        trainingCastTimeDialog.beginAutoRefresh(latestTrainingNo);
        trainingCastTimeDialog.setVisible(true);
        trainingCastTimeDialog.toFront();
    }
    
    /**
     * 获取最新批次号：取已加载问题中更新时间最新（其次 ID 最大）的有批次号问题，无批次号时返回 null
     */
    private String latestTrainingNo(AiEnvConfig envConfig) {
        List<AiQuestion> aiQuestions = new ArrayList<>();
        AiQuestion latest = null;
        try {
            aiQuestions = AiQuestionApiClient.getInstance().listQuestions(envConfig, null);
        } catch (Exception ex) {
            logger.error("[latestTrainingNo] query questions error", ex);
        }
        for (AiQuestion question : aiQuestions) {
            if (question.getTrainingNo() == null || question.getTrainingNo().isBlank()) {
                continue;
            }
            if (latest == null || isNewerThan(question, latest)) {
                latest = question;
            }
        }
        return latest != null ? latest.getTrainingNo().trim() : null;
    }
    
    /**
     * 判断批次下是否存在进行中的训练（待训练 / 训练中 / 训练已提交，仅这些状态触发自动刷新）
     */
    private boolean isBatchTrainingInProgress(String trainingNo) {
        for (AiQuestion question : allQuestions) {
            if (trainingNo.equals(question.getTrainingNo() == null ? "" : question.getTrainingNo().trim()) && isTrainingInProgress(question)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断问题训练是否进行中（待训练 / 训练中 / 训练已提交）
     */
    private boolean isTrainingInProgress(AiQuestion question) {
        Integer status = question.getTrainingStatus();
        return status != null && (status == AiQuestion.TRAINING_STATUS_PENDING || status == AiQuestion.TRAINING_STATUS_RUNNING
                || status == AiQuestion.TRAINING_STATUS_SUBMITTED);
    }
    
    /**
     * 训练耗时格式化：毫秒差值换算为秒，超过 1 分时附加多单位换算（分 / 小时）
     */
    private String formatCastTime(AiQuestionApiClient.TrainingCastTime castTime) {
        long millis = Math.max(0, castTime.maxLastTime() - castTime.minInitTime());
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + " 秒";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder text = new StringBuilder(seconds + " 秒（");
        if (hours > 0) {
            text.append(hours).append(" 小时 ");
        }
        if (hours > 0 || minutes > 0) {
            text.append(minutes).append(" 分 ");
        }
        text.append(secs).append(" 秒）");
        return text.toString();
    }
    
    /**
     * 判断问题 a 是否比 b 更新（训练触发时间优先，其次更新时间，最后问题 ID）
     */
    private boolean isNewerThan(AiQuestion a, AiQuestion b) {
        Long initA = a.getInitTrainingTime();
        Long initB = b.getInitTrainingTime();
        if (initA != null && initB != null) {
            if (!initA.equals(initB)) {
                return initA > initB;
            }
        }
        if (a.getUpdateTime() != null && b.getUpdateTime() != null) {
            return a.getUpdateTime().isAfter(b.getUpdateTime());
        }
        long idA = a.getId() != null ? a.getId() : 0L;
        long idB = b.getId() != null ? b.getId() : 0L;
        return idA > idB;
    }
    
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
    
    private JPanel buildPasteMetaPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("问题归属与训练参数"), new EmptyBorder(2, 8, 6, 8)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        pasteUserIdField = new FilterComboBox<>();
        for (String userOption : dialog.userOptions) {
            pasteUserIdField.addItem(userOption);
        }
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("   所属用户："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteUserIdField, gbc);
        
        pasteProjectBtn = ButtonFactory.createSecondary(AiQuestionMangerDialog.projectButtonLabel(pasteSelectedProjects));
        pasteProjectBtn.setToolTipText("点击选择所属项目（可多选，适用全部问题）");
        pasteProjectBtn.addActionListener(e -> {
            List<String> result = dialog.showProjectMultiSelectDialog("选择所属项目", pasteSelectedProjects);
            if (result != null) {
                pasteSelectedProjects.clear();
                pasteSelectedProjects.addAll(result);
                pasteProjectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(pasteSelectedProjects));
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("所属项目："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteProjectBtn, gbc);
        
        pasteClassifyCombo = new FilterComboBox<>();
        for (String classify : dialog.classifySuggestions) {
            pasteClassifyCombo.addItem(classify);
        }
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("      分类："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteClassifyCombo, gbc);
        
        pasteAutoTrainingCombo = new JComboBox<>(new String[] {"允许", "不允许"});
        pasteAutoTrainingCombo.setSelectedItem("允许");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("自动训练："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pasteAutoTrainingCombo, gbc);
        
        pasteTrainingParamArea = new JTextArea(3, 20);
        pasteTrainingParamArea.setLineWrap(true);
        pasteTrainingParamArea.setWrapStyleWord(true);
        gbc.gridx = 0;
        gbc.gridy = 4;
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
    
    private void updateParsePreview() {
        List<String> questions = parseQuestions(pasteQuestionArea.getText());
        parsePreviewLabel.setText("已识别 " + questions.size() + " 个问题（自动按 换行 / 分号 分割并去重，逗号、顿号与空格属于问题内容）");
        parsePreviewLabel.setForeground(questions.isEmpty() ? Color.GRAY : UiConstants.COLOR_SUCCESS);
    }
    
    private void savePastedQuestions() {
        AiEnvConfig envConfig = dialog.requireSelectedEnvConfig();
        if (envConfig == null) {
            return;
        }
        List<String> questions = parseQuestions(pasteQuestionArea.getText());
        if (questions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先输入问题内容", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String userId = AiQuestionMangerDialog.extractUserIdFromCombo(pasteUserIdField.getSelectedItem());
        String classify = dialog.editableComboText(pasteClassifyCombo).trim();
        String trainingParam = pasteTrainingParamArea.getText().trim();
        int autoTraining =
                "允许".equals(pasteAutoTrainingCombo.getSelectedItem()) ? AiQuestion.AUTO_TRAINING_ALLOWED : AiQuestion.AUTO_TRAINING_NOT_ALLOWED;
        List<AiQuestion> items = new ArrayList<>();
        for (String question : questions) {
            AiQuestion item = new AiQuestion();
            item.setEnvName(envConfig.getEnvName());
            item.setQuestion(question);
            item.setUserId(userId.isEmpty() ? null : userId);
            item.setProjectCodeList(pasteSelectedProjects.isEmpty() ? null : new ArrayList<>(pasteSelectedProjects));
            item.setQuestionClassify(classify.isEmpty() ? null : classify);
            item.setTrainingParam(trainingParam);
            item.setAnswer("");
            item.setEnableTraining(AiQuestion.TRAINING_ENABLED);
            item.setAllowAutoTraining(autoTraining);
            item.setPriority(0);
            items.add(item);
        }
        dialog.runApiTask("批量保存", () -> {
            String message = AiQuestionApiClient.getInstance().saveQuestions(envConfig, items);
            return () -> {
                pasteQuestionArea.setText("");
                updateParsePreview();
                currentPage = 1;
                dialog.invalidateLoadedData();
                dialog.refreshQuestionTable();
                dialog.setStatus("批量粘贴成功保存 " + items.size() + " 条问题到环境 [" + envConfig.getEnvName() + "]：" + message, true);
            };
        });
    }
    
    private void clearPasteInputs() {
        pasteQuestionArea.setText("");
        pasteUserIdField.setSelectedItem(null);
        pasteUserIdField.getEditor().setItem("");
        pasteSelectedProjects.clear();
        pasteProjectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(pasteSelectedProjects));
        pasteClassifyCombo.getEditor().setItem("");
        pasteAutoTrainingCombo.setSelectedItem("允许");
        pasteTrainingParamArea.setText("");
        updateParsePreview();
    }
    
    // ────────── 用户 / 项目下拉刷新 ──────────
    
    void refreshUserOptionsInPanel() {
        // 更新批量粘贴下拉
        pasteUserIdField.removeAllItems();
        for (String userOption : dialog.userOptions) {
            pasteUserIdField.addItem(userOption);
        }
        // 更新手动录入各行下拉
        for (QuestionEntryRow row : entryRows) {
            boolean hadSameAsAbove = row.userIdField.getItemCount() > 0 && SAME_AS_ABOVE.equals(String.valueOf(row.userIdField.getItemAt(0)));
            Object currentSelection = row.userIdField.getSelectedItem();
            row.userIdField.removeAllItems();
            if (hadSameAsAbove) {
                row.userIdField.addItem(SAME_AS_ABOVE);
            }
            for (String userOption : dialog.userOptions) {
                row.userIdField.addItem(userOption);
            }
            if (currentSelection != null) {
                row.userIdField.setSelectedItem(currentSelection);
            }
        }
        // 更新问题列表用户筛选下拉
        if (userFilterCombo != null) {
            Object previousUserFilter = userFilterCombo.getSelectedItem();
            suppressUserFilterEvents = true;
            try {
                userFilterCombo.removeAllItems();
                userFilterCombo.addItem(ALL_USER);
                userFilterCombo.addItem(AiQuestion.GLOBAL_DISPLAY_TEXT);
                for (String userOption : dialog.userOptions) {
                    userFilterCombo.addItem(AiQuestionMangerDialog.formatUserOption(userOption));
                }
                if (previousUserFilter != null) {
                    userFilterCombo.setSelectedItem(previousUserFilter);
                }
            } finally {
                suppressUserFilterEvents = false;
            }
        }
    }
    
    void refreshProjectOptionsInPanel() {
        pasteSelectedProjects.clear();
        pasteProjectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(pasteSelectedProjects));
        for (QuestionEntryRow row : entryRows) {
            row.selectedProjectCodes.clear();
            row.projectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(row.selectedProjectCodes));
        }
    }
    
    // ────────── AI 元数据下拉（触发训练弹窗） ──────────
    
    /**
     * 用当前环境的 AI 元数据回填触发训练弹窗下拉选项（弹窗打开期间异步加载完成时调用；未打开弹窗时忽略）
     */
    void refreshAiMetaOptionsInPanel() {
        MetaComboGroup group = openMetaComboGroup;
        if (group == null) {
            return;
        }
        AiMetaConfig config = dialog.aiMetaConfig;
        fillAgentTypeCombo(group.agentTypeCombo, config.agentOptions());
        fillMetaCombo(group.coreLlmCombo, config.coreLlmNameOptions());
        fillMetaCombo(group.intentionLlmCombo, config.intentionLlmNameOptions());
    }
    
    /**
     * 构建智能体类型可编辑下拉（支持下拉选中与手动输入，选项取自 AI 元数据接口 agentList，选中项展示 name（desc））
     */
    private static JComboBox<AgentOption> createAgentTypeCombo(List<AiMetaConfig.Agent> agents) {
        JComboBox<AgentOption> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setPreferredSize(new Dimension(META_COMBO_WIDTH, 26));
        combo.setToolTipText(AGENT_TYPE_TIP);
        fillAgentTypeCombo(combo, agents);
        return combo;
    }
    
    /**
     * 回填智能体类型下拉选项（始终以 auto 为首项；接口未返回 agentList 时回退内置类型；保留用户主动选择 / 手动输入内容）
     */
    private static void fillAgentTypeCombo(JComboBox<AgentOption> combo, List<AiMetaConfig.Agent> agents) {
        String typed = agentTypeText(combo);
        combo.removeAllItems();
        for (AgentOption option : toAgentOptions(agents)) {
            combo.addItem(option);
        }
        // 默认 / 清空 → 始终回落 auto（首项）
        if (typed.isEmpty() || DEFAULT_AGENT_TYPE.equals(typed)) {
            combo.setSelectedIndex(0);
            return;
        }
        // 用户主动选择或手动输入了非 auto 的值：按名称匹配选项
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).name().equals(typed)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        // 手动输入未匹配到任何选项：保留输入内容（提交时若为空仍会回退为 auto）
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
        combo.getEditor().setItem(typed);
    }
    
    /**
     * 转换接口 agentList 为智能体类型下拉项（始终将 auto 作为首项，重复 name 去重；无有效项时回退内置类型）
     */
    private static List<AgentOption> toAgentOptions(List<AiMetaConfig.Agent> agents) {
        List<AgentOption> options = new ArrayList<>();
        // 首项固定为 auto（默认智能体类型）
        options.add(new AgentOption(DEFAULT_AGENT_TYPE, null));
        if (agents != null) {
            for (AiMetaConfig.Agent agent : agents) {
                if (agent == null || agent.getName() == null || agent.getName().isBlank()) {
                    continue;
                }
                String name = agent.getName().trim();
                if (DEFAULT_AGENT_TYPE.equals(name)) {
                    continue;
                }
                boolean exists = options.stream().anyMatch(option -> option.name().equals(name));
                if (!exists) {
                    options.add(new AgentOption(name, agent.getDesc()));
                }
            }
        }
        if (options.size() == 1) {
            // 接口无有效数据时回退内置类型（auto 已加入，去重）
            for (String type : AGENT_TYPES) {
                if (DEFAULT_AGENT_TYPE.equals(type)) {
                    continue;
                }
                options.add(new AgentOption(type, null));
            }
        }
        return options;
    }
    
    /**
     * 读取智能体类型下拉的提交值（下拉选中取选项 name，手动输入取输入文本；展示文本含描述时回退为 name）
     */
    private static String agentTypeText(JComboBox<AgentOption> combo) {
        Object value = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        if (value instanceof AgentOption option) {
            return option.name();
        }
        String text = value == null ? "" : value.toString().trim();
        for (int i = 0; i < combo.getItemCount(); i++) {
            AgentOption option = combo.getItemAt(i);
            if (option != null && option.displayText().equals(text)) {
                return option.name();
            }
        }
        return text;
    }
    
    /**
     * 构建 AI 元数据可编辑下拉（支持下拉选中与手动输入，首项 auto 为默认；提交时不允许为空，自动回退为 auto）
     */
    private static JComboBox<String> createMetaCombo(List<String> options, String toolTip) {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setPreferredSize(new Dimension(META_COMBO_WIDTH, 26));
        combo.setToolTipText(toolTip);
        fillMetaCombo(combo, options);
        return combo;
    }
    
    /**
     * 回填可编辑下拉选项（首项 auto 为默认；空值 / 显式选中 auto 一律回落到 auto；保留用户主动下拉选中 / 手动输入的其他值）
     */
    private static void fillMetaCombo(JComboBox<String> combo, List<String> options) {
        String current = comboText(combo);
        combo.removeAllItems();
        combo.addItem(AUTO_META_OPTION);
        if (options != null) {
            for (String option : options) {
                if (option != null && !option.isBlank()) {
                    combo.addItem(option);
                }
            }
        }
        if (current.isEmpty() || AUTO_META_OPTION.equals(current)) {
            // 默认 / 清空 / 显式回到 auto：始终默认选中 auto
            combo.setSelectedIndex(0);
        } else {
            combo.getEditor().setItem(current);
        }
    }
    
    /**
     * 读取可编辑下拉的当前文本（下拉选中与手动输入均取编辑器内容；空值返回空串）
     */
    private static String comboText(JComboBox<String> combo) {
        Object value = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return value == null ? "" : value.toString().trim();
    }
    
    /**
     * 兜底为 auto（智能体类型 / 训练模型 / 意图分析模型提交时不允许为空）
     */
    private static String requireAuto(String value) {
        return value == null || value.isBlank() ? AUTO_META_OPTION : value.trim();
    }
    
    /**
     * 触发训练参数表单行（标签 + 输入组件）
     */
    private static void addParamRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }
    
    /**
     * 触发训练弹窗 AI 元数据下拉组（智能体类型 / 训练模型 / 意图分析模型）
     */
    private static final class MetaComboGroup {
        
        private final JComboBox<AgentOption> agentTypeCombo;
        
        private final JComboBox<String> coreLlmCombo;
        
        private final JComboBox<String> intentionLlmCombo;
        
        MetaComboGroup(JComboBox<AgentOption> agentTypeCombo, JComboBox<String> coreLlmCombo, JComboBox<String> intentionLlmCombo) {
            this.agentTypeCombo = agentTypeCombo;
            this.coreLlmCombo = coreLlmCombo;
            this.intentionLlmCombo = intentionLlmCombo;
        }
    }
    
    /**
     * 智能体类型下拉项（name：提交的智能体类型；desc：下拉展示的智能体描述）
     */
    private record AgentOption(String name, String desc) {
        
        /**
         * 下拉展示文本（描述为空时仅展示智能体名称）
         */
        String displayText() {
            return desc == null || desc.isBlank() ? name : name + "（" + desc.trim() + "）";
        }
        
        @Override
        public String toString() {
            return displayText();
        }
    }
    
    void refreshPasteClassifySuggestions() {
        if (pasteClassifyCombo != null) {
            Object currentPasteClassify = pasteClassifyCombo.getEditor().getItem();
            pasteClassifyCombo.removeAllItems();
            for (String classify : dialog.classifySuggestions) {
                pasteClassifyCombo.addItem(classify);
            }
            if (currentPasteClassify != null) {
                pasteClassifyCombo.getEditor().setItem(currentPasteClassify);
            }
        }
        for (QuestionEntryRow row : entryRows) {
            Object currentClassify = row.classifyCombo.getEditor().getItem();
            boolean hasSameAsAbove = row.classifyCombo.getItemCount() > 0 && SAME_AS_ABOVE.equals(String.valueOf(row.classifyCombo.getItemAt(0)));
            row.classifyCombo.removeAllItems();
            if (hasSameAsAbove) {
                row.classifyCombo.addItem(SAME_AS_ABOVE);
            }
            for (String classify : dialog.classifySuggestions) {
                row.classifyCombo.addItem(classify);
            }
            if (currentClassify != null) {
                row.classifyCombo.getEditor().setItem(currentClassify);
            }
        }
    }
    
    JButton getManualSaveBtn() {
        return manualSaveBtn;
    }
    
    JButton getPasteSaveBtn() {
        return pasteSaveBtn;
    }
    
    JButton getImportExcelBtn() {
        return importExcelBtn;
    }
    
    // ────────── 下屏：问题列表 ──────────
    
    private JPanel buildQuestionListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("问题列表"));
        
        JPanel toolbar = new JPanel(new BorderLayout(0, 2));
        JPanel filterRow1 = new JPanel(new BorderLayout());
        JPanel filterLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JPanel filterRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filterRow2.setVisible(false);
        searchField = new CustomTextField("模糊匹配问题/固定回复/批次号");
        searchField.setMinWidth(200);
        searchField.setToolTipText("按关键字模糊匹配问题、固定回复、批次号");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearchFieldChanged();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearchFieldChanged();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearchFieldChanged();
            }
        });
        filterLeft.add(searchField);
        filterLeft.add(new JLabel("分类："));
        classifyFilterCombo = new JComboBox<>();
        classifyFilterCombo.addItem(ALL_CLASSIFY);
        classifyFilterCombo.setPreferredSize(new Dimension(140, 26));
        classifyFilterCombo.setToolTipText("按分类过滤问题列表，选项来自当前环境问题列表的分类");
        classifyFilterCombo.addActionListener(e -> onClassifyFilterChanged());
        filterLeft.add(classifyFilterCombo);
        filterLeft.add(new JLabel("训练状态："));
        statusFilterCombo = new CheckBoxComboBox<>();
        List<Integer> statusValues = new ArrayList<>();
        for (int value : TRAINING_STATUS_VALUES) {
            statusValues.add(value);
        }
        statusFilterCombo.setItems(statusValues);
        statusFilterCombo.setLabelProvider(v -> {
            for (int i = 0; i < TRAINING_STATUS_VALUES.length; i++) {
                if (TRAINING_STATUS_VALUES[i] == v) {
                    return TRAINING_STATUS_TEXTS[i];
                }
            }
            return String.valueOf(v);
        });
        statusFilterCombo.setDefaultText(ALL_TRAINING_STATUS);
        statusFilterCombo.setPreferredSize(new Dimension(126, 26));
        statusFilterCombo.setToolTipText("按训练状态过滤问题列表（可多选，不选则显示全部）");
        statusFilterCombo.addActionListener(e -> onTrainingStatusFilterChanged());
        filterLeft.add(statusFilterCombo);
        JButton refreshBtn = ButtonFactory.createPill("查询", UiConstants.COLOR_SUCCESS, UiConstants.COLOR_SUCCESS_LIGHT);
        refreshBtn.addActionListener(e -> {
            currentPage = 1;
            dialog.invalidateLoadedData();
            dialog.refreshQuestionTable();
        });
        filterLeft.add(refreshBtn);
        JButton resetBtn = ButtonFactory.createPill("重置", UiConstants.COLOR_NEUTRAL, UiConstants.COLOR_NEUTRAL_LIGHT);
        resetBtn.setToolTipText("清空关键字并恢复分类 / 项目 / 用户 / 训练状态 / 自动训练为全部，同时清空勾选，回到第一页重新查询");
        resetBtn.addActionListener(e -> resetQuestionFilters());
        filterLeft.add(resetBtn);
        JLabel moreConditionLink = new JLabel("<html><a href=\"#\">更多条件 &#9660;</a></html>");
        moreConditionLink.setToolTipText("展开 / 收起项目、用户、自动训练筛选条件");
        moreConditionLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        final String expandedText = "<html><a href=\"#\">收起条件 &#9650;</a></html>";
        final String collapsedText = "<html><a href=\"#\">更多条件 &#9660;</a></html>";
        moreConditionLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean show = !filterRow2.isVisible();
                filterRow2.setVisible(show);
                moreConditionLink.setText(show ? expandedText : collapsedText);
                toolbar.revalidate();
                toolbar.repaint();
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                moreConditionLink.setForeground(new Color(59, 72, 221));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                moreConditionLink.setForeground(new Color(0x1A, 0x73, 0xE8));
            }
        });
        filterLeft.add(moreConditionLink);
        
        checkedCountLabel = new JLabel("已选 0 条");
        checkedCountLabel.setForeground(Color.GRAY);
        checkedCountLabel.setFont(UiConstants.FONT_SANS_11);
        filterRight.add(checkedCountLabel);
        JButton trainBtn = ButtonFactory.createPill("批量训练", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
        trainBtn.setToolTipText("触发勾选问题的训练，「训练中 / 训练已提交」状态的问题不允许再次触发（点击表头复选框可全选当前页）");
        trainBtn.addActionListener(e -> triggerTrainingQuestions());
        filterRight.add(trainBtn);
        JButton stopTrainBtn = ButtonFactory.createPill("停止训练", UiConstants.COLOR_DANGER, UiConstants.COLOR_DANGER_LIGHT);
        stopTrainBtn.setToolTipText(
                "停止勾选问题的训练，仅「待训练 / 训练中 / 训练已提交」状态的问题可停止，其余状态自动跳过（点击表头复选框可全选当前页）");
        stopTrainBtn.addActionListener(e -> batchStopTrainingQuestions());
        filterRight.add(stopTrainBtn);
        JButton batchUpdateBtn = ButtonFactory.createPill("批量更新", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
        batchUpdateBtn.setToolTipText("批量更新勾选问题的所属用户、所属项目、训练参数与自动训练（点击表头复选框可全选当前页）");
        batchUpdateBtn.addActionListener(e -> batchUpdateUserQuestions());
        filterRight.add(batchUpdateBtn);
        JButton batchDeleteBtn = ButtonFactory.createPill("批量删除", UiConstants.COLOR_DANGER, UiConstants.COLOR_DANGER_LIGHT);
        batchDeleteBtn.setToolTipText("批量删除勾选的问题，删除后不可恢复（点击表头复选框可全选当前页）");
        batchDeleteBtn.addActionListener(e -> batchDeleteQuestions());
        filterRight.add(batchDeleteBtn);
        
        filterRow1.add(filterLeft, BorderLayout.WEST);
        filterRow1.add(filterRight, BorderLayout.EAST);
        toolbar.add(filterRow1, BorderLayout.NORTH);
        filterRow2.add(new JLabel("项目："));
        projectFilterCombo = new JComboBox<>();
        projectFilterCombo.addItem(ALL_PROJECT);
        projectFilterCombo.setPreferredSize(new Dimension(160, 26));
        projectFilterCombo.setToolTipText("按项目过滤问题列表，选项来自当前环境问题列表的项目名称");
        projectFilterCombo.addActionListener(e -> onProjectFilterChanged());
        filterRow2.add(projectFilterCombo);
        filterRow2.add(new JLabel("用户："));
        userFilterCombo = new JComboBox<>();
        userFilterCombo.addItem(ALL_USER);
        userFilterCombo.addItem(AiQuestion.GLOBAL_DISPLAY_TEXT);
        for (String userOption : dialog.userOptions) {
            userFilterCombo.addItem(AiQuestionMangerDialog.formatUserOption(userOption));
        }
        userFilterCombo.setPreferredSize(new Dimension(160, 26));
        userFilterCombo.setToolTipText("按用户过滤问题列表，选项来自当前环境用户列表");
        userFilterCombo.addActionListener(e -> {
            if (!suppressUserFilterEvents) {
                resetCheckedForListRefresh();
                currentPage = 1;
                applyLocalFilterAndPaging();
            }
        });
        filterRow2.add(userFilterCombo);
        filterRow2.add(new JLabel("自动训练："));
        autoTrainingFilterCombo = new JComboBox<>(new String[] {ALL_AUTO_TRAINING, AUTO_TRAINING_ALLOW, AUTO_TRAINING_NOT_ALLOW});
        autoTrainingFilterCombo.setPreferredSize(new Dimension(100, 26));
        autoTrainingFilterCombo.setToolTipText("按自动训练状态过滤问题列表");
        autoTrainingFilterCombo.addActionListener(e -> {
            if (!suppressQuestionFilterEvents) {
                resetCheckedForListRefresh();
                currentPage = 1;
                applyLocalFilterAndPaging();
            }
        });
        filterRow2.add(autoTrainingFilterCombo);
        toolbar.add(filterRow2, BorderLayout.SOUTH);
        panel.add(toolbar, BorderLayout.NORTH);
        
        // 问题表格
        tableModel = new QuestionTableModel();
        questionTable = new JTable(tableModel);
        questionTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        questionTable.setRowHeight(28);
        questionTable.getTableHeader().setReorderingAllowed(false);
        int[] columnWidths = {AiQuestionMangerDialog.CHECK_COLUMN_WIDTH, 50, 280, 90, 120, 100, 150, 110, 150, 90, 80, 90, 90, 140, 140, 140, 100,
                110, ACTION_COLUMN_WIDTH};
        for (int i = 0; i < columnWidths.length; i++) {
            questionTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        TableColumn checkColumn = questionTable.getColumnModel().getColumn(COL_CHECK);
        checkColumn.setMinWidth(AiQuestionMangerDialog.CHECK_COLUMN_WIDTH);
        checkColumn.setMaxWidth(AiQuestionMangerDialog.CHECK_COLUMN_WIDTH);
        checkColumn.setCellRenderer(new AiQuestionMangerDialog.CheckBoxCellRenderer());
        checkColumn.setHeaderRenderer(
                new AiQuestionMangerDialog.HeaderCheckBoxRenderer(() -> tableModel.isCurrentPageAllChecked(), () -> tableModel.getRowCount()));
        TableColumn actionColumn = questionTable.getColumnModel().getColumn(COL_ACTION);
        actionColumn.setMinWidth(ACTION_COLUMN_WIDTH);
        actionColumn.setMaxWidth(ACTION_COLUMN_WIDTH);
        questionTable.getColumnModel().getColumn(COL_ID).setCellRenderer(dialog.centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_PROJECT).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(16));
        questionTable.getColumnModel().getColumn(COL_QUESTION).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(48));
        questionTable.getColumnModel().getColumn(COL_CLASSIFY).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(12));
        questionTable.getColumnModel().getColumn(COL_USER).setCellRenderer(new AiQuestionMangerDialog.UserIdCellRenderer(dialog.userOptions));
        questionTable.getColumnModel().getColumn(COL_TRAINING_PARAM).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(24));
        questionTable.getColumnModel().getColumn(COL_TRAINING_NO).setCellRenderer(dialog.centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_ANSWER).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(24));
        AiQuestionMangerDialog.SelectableTextEditor questionTextEditor = new AiQuestionMangerDialog.SelectableTextEditor();
        questionTable.getColumnModel().getColumn(COL_QUESTION).setCellEditor(questionTextEditor);
        questionTable.getColumnModel().getColumn(COL_ANSWER).setCellEditor(questionTextEditor);
        questionTable.getColumnModel().getColumn(COL_TRAINING_NO).setCellEditor(questionTextEditor);
        questionTable.getColumnModel().getColumn(COL_ANSWER_ID).setCellRenderer(new AiQuestionMangerDialog.AnswerIdCellRenderer());
        questionTable.getColumnModel().getColumn(COL_TRAINING_STATUS).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            Integer status = (Integer) value;
            JLabel label = new JLabel(AiQuestionMangerDialog.trainingStatusText(status));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(AiQuestionMangerDialog.trainingStatusColor(status));
            return label;
        });
        questionTable.getColumnModel().getColumn(COL_TRAINING_MODEL).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            String trainingModel = value != null ? value.toString() : null;
            JLabel label = new JLabel(AiQuestionMangerDialog.trainingModelText(trainingModel));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(AiQuestionMangerDialog.trainingModelColor(trainingModel));
            return label;
        });
        questionTable.getColumnModel().getColumn(COL_SUBMIT_TRAINING_TIME).setCellRenderer(dialog.centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_START_TRAINING_TIME).setCellRenderer(dialog.centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_LAST_TRAINING_TIME).setCellRenderer(dialog.centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_TRAINING_COST).setCellRenderer(dialog.centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_AUTO_TRAINING).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground("允许".equals(value.toString()) ? UiConstants.COLOR_SUCCESS : UiConstants.COLOR_DANGER);
            return label;
        });
        questionTable.getColumnModel().getColumn(COL_REMARK).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(20));
        questionTable.getColumnModel().getColumn(COL_ACTION).setCellRenderer(
                new AiQuestionMangerDialog.ActionCellRenderer(() -> hoverActionRow, () -> hoverActionZone, "查看问题详情（全部字段）", true,
                        AiQuestionMangerDialog.ACTION_DELETE));
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
        questionTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateActionHover(e.getPoint());
            }
        });
        questionTable.addMouseWheelListener(e -> updateActionHover(e.getPoint()));
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
        JPanel pagePanel = new JPanel(new BorderLayout(8, 2));
        // 左侧：操作按钮（导出 + 训练耗时  + 训练耗时实时标签）
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton exportQuestionBtn = ButtonFactory.createToolbar("导出");
        exportQuestionBtn.setToolTipText("将当前筛选后的全部问题导出为 Excel 文件");
        exportQuestionBtn.addActionListener(e -> exportQuestionsToExcel());
        actionPanel.add(exportQuestionBtn);
        actionPanel.add(Box.createHorizontalStrut(8));
        JButton castTimeBtn = ButtonFactory.createToolbar("训练耗时");
        castTimeBtn.setToolTipText("按批次号实时查询训练耗时（支持手动查询与自动刷新）");
        castTimeBtn.addActionListener(e -> openTrainingCastTimeDialog());
        actionPanel.add(castTimeBtn);
        actionPanel.add(Box.createHorizontalStrut(8));
        trainingCostLabel = new JLabel();
        trainingCostLabel.setFont(UiConstants.FONT_SANS_11);
        trainingCostLabel.setForeground(UiConstants.COLOR_NEUTRAL_LIGHT);
        trainingCostLabel.setVisible(false);
        actionPanel.add(trainingCostLabel);
        pagePanel.add(actionPanel, BorderLayout.WEST);
        // 右侧：分页按钮 + 每页组件（统一靠右）
        JPanel pageRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
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
        pageRightPanel.add(firstPageBtn);
        pageRightPanel.add(prevPageBtn);
        pageRightPanel.add(pageInfoLabel);
        pageRightPanel.add(nextPageBtn);
        pageRightPanel.add(lastPageBtn);
        pageRightPanel.add(Box.createHorizontalStrut(12));
        pageRightPanel.add(totalLabel);
        pageRightPanel.add(Box.createHorizontalStrut(12));
        pageRightPanel.add(new JLabel("每页："));
        pageSizeCombo = new JComboBox<>(AiQuestionMangerDialog.DEFAULT_PAGE_SCOPE);
        pageSizeCombo.setEditable(true);
        pageSizeCombo.setPreferredSize(new Dimension(76, 26));
        pageSizeCombo.setToolTipText(
                "选择或输入每页条数（" + AiQuestionMangerDialog.MIN_PAGE_SIZE + " ~ " + AiQuestionMangerDialog.MAX_PAGE_SIZE + "），回车或失去焦点生效");
        pageSizeCombo.setSelectedItem(String.valueOf(AiQuestionMangerDialog.DEFAULT_PAGE_SIZE));
        pageSizeCombo.addActionListener(e -> applyPageSizeFromCombo());
        if (pageSizeCombo.getEditor().getEditorComponent() instanceof JTextField editorField) {
            editorField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    applyPageSizeFromCombo();
                }
            });
        }
        pageRightPanel.add(pageSizeCombo);
        pageRightPanel.add(new JLabel("条"));
        pagePanel.add(pageRightPanel, BorderLayout.EAST);
        panel.add(pagePanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ────────── 表格交互 ──────────
    
    private void handleTableClick(MouseEvent e) {
        int row = questionTable.rowAtPoint(e.getPoint());
        int column = questionTable.columnAtPoint(e.getPoint());
        if (row < 0 || column < 0 || row >= tableModel.getRowCount()) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                dialog.toggleFullscreenDialog();
            }
            return;
        }
        AiQuestion question = tableModel.getQuestionAt(row);
        if (column == COL_CHECK) {
            if (e.getClickCount() == 1) {
                tableModel.toggleChecked(row);
                updateCheckControls();
            }
        } else if (column == COL_ANSWER_ID) {
            if (e.getClickCount() == 1 && question.getAnswerId() != null) {
                viewAnswerInfo(question);
            }
        } else if (column == COL_ACTION) {
            Rectangle cellRect = questionTable.getCellRect(row, column, false);
            int zone = AiQuestionMangerDialog.actionZoneAt(e.getX(), cellRect, 4);
            if (zone == AiQuestionMangerDialog.ACTION_VIEW) {
                showQuestionDetail(question);
            } else if (zone == AiQuestionMangerDialog.ACTION_EDIT) {
                editQuestion(question);
            } else if (zone == AiQuestionMangerDialog.ACTION_TRAIN) {
                trainSingleQuestion(question);
            } else {
                deleteQuestion(question);
            }
        } else if (e.getClickCount() == 2) {
            if (column != COL_QUESTION && column != COL_ANSWER && column != COL_TRAINING_NO) {
                editQuestion(question);
            }
        }
    }
    
    private void updateActionHover(Point point) {
        int row = -1;
        int zone = -1;
        boolean clickable = false;
        if (point != null) {
            int hitRow = questionTable.rowAtPoint(point);
            int hitColumn = questionTable.columnAtPoint(point);
            if (hitRow >= 0 && hitColumn == COL_ACTION) {
                row = hitRow;
                zone = AiQuestionMangerDialog.actionZoneAt(point.x, questionTable.getCellRect(hitRow, COL_ACTION, false), 4);
                clickable = true;
            } else if (hitRow >= 0 && hitColumn == COL_CHECK) {
                clickable = true;
            } else if (hitRow >= 0 && hitRow < tableModel.getRowCount() && hitColumn == COL_ANSWER_ID
                    && tableModel.getQuestionAt(hitRow).getAnswerId() != null) {
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
    
    private void repaintActionCell(int row) {
        if (row >= 0 && row < questionTable.getRowCount()) {
            questionTable.repaint(questionTable.getCellRect(row, COL_ACTION, false));
        }
    }
    
    private void updateCheckControls() {
        questionTable.getTableHeader().repaint();
        if (checkedCountLabel != null) {
            checkedCountLabel.setText("已选 " + tableModel.getCheckedCount() + " 条");
        }
    }
    
    // ────────── 问题列表加载与筛选 ──────────
    
    void invalidateCache() {
        questionsLoaded = false;
    }
    
    void refreshQuestionTable() {
        if (!questionsLoaded || !Objects.equals(loadedStatusFilter, selectedTrainingStatusFilter())) {
            loadQuestionsFromApi();
            return;
        }
        applyLocalFilterAndPaging();
    }
    
    private void loadQuestionsFromApi() {
        if (dialog.apiBusy) {
            dialog.pendingQuestionReload = true;
            return;
        }
        dialog.pendingQuestionReload = false;
        AiEnvConfig envConfig = dialog.envCombo.getSelectedItem() instanceof AiEnvConfig config ? config : null;
        if (envConfig == null) {
            questionsLoaded = true;
            loadedStatusFilter = selectedTrainingStatusFilter();
            allQuestions.clear();
            refreshClassifyFilterOptions();
            refreshProjectFilterOptions();
            tableModel.setData(new ArrayList<>());
            tableModel.clearChecked();
            updateCheckControls();
            dialog.setStatus("请先在右上角选择环境", false);
            return;
        }
        Set<Integer> selectedStatuses = selectedTrainingStatusFilter();
        Integer apiStatusFilter = selectedStatuses.size() == 1 ? selectedStatuses.iterator().next() : null;
        dialog.apiBusy = true;
        dialog.setSaveButtonsEnabled(false);
        dialog.setStatus("正在加载环境 [" + envConfig.getEnvName() + "] 的问题列表…", true);
        new Thread(() -> {
            List<AiQuestion> loaded = new ArrayList<>();
            String error = null;
            try {
                for (AiQuestion item : AiQuestionApiClient.getInstance().listQuestions(envConfig, apiStatusFilter)) {
                    item.setEnvName(envConfig.getEnvName());
                    loaded.add(item);
                }
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("加载环境 [{}] 问题列表失败", envConfig.getEnvName(), ex);
                error = "环境 [" + envConfig.getEnvName() + "] 问题列表加载失败：" + ex.getMessage();
            }
            String loadError = error;
            SwingUtilities.invokeLater(() -> {
                dialog.apiBusy = false;
                dialog.setSaveButtonsEnabled(true);
                questionsLoaded = true;
                loadedStatusFilter = selectedTrainingStatusFilter();
                allQuestions.clear();
                allQuestions.addAll(loaded);
                // 只要重新查询（重新加载列表），勾选状态一律重置
                tableModel.clearChecked();
                dialog.classifySuggestions = extractClassifies(allQuestions);
                refreshPasteClassifySuggestions();
                refreshClassifyFilterOptions();
                refreshProjectFilterOptions();
                if (loadError != null) {
                    dialog.setStatus(loadError, false);
                } else {
                    dialog.setStatus("环境 [" + envConfig.getEnvName() + "] 问题列表加载完成，共 " + loaded.size() + " 条", true);
                }
                applyLocalFilterAndPaging();
                boolean envChanged =
                        dialog.envCombo.getSelectedItem() instanceof AiEnvConfig current && !current.getEnvName().equals(envConfig.getEnvName());
                if (envChanged || !Objects.equals(selectedTrainingStatusFilter(), loadedStatusFilter)) {
                    questionsLoaded = false;
                    refreshQuestionTable();
                }
                dialog.resumePendingReloads();
            });
        }, "ai-question-load").start();
    }
    
    private void applyLocalFilterAndPaging() {
        String keyword = searchField.getText().trim().toLowerCase();
        String classifyFilter = selectedClassifyFilter();
        String projectFilter = selectedProjectFilter();
        String userFilter = selectedUserFilter();
        Set<Integer> statusFilter = selectedTrainingStatusFilter();
        Integer autoTrainingFilter = selectedAutoTrainingFilter();
        List<AiQuestion> filtered = new ArrayList<>();
        for (AiQuestion item : allQuestions) {
            if (matchesKeyword(item, keyword) && matchesClassify(item, classifyFilter) && matchesProject(item, projectFilter) && matchesUserFilter(
                    item, userFilter) && matchesTrainingStatus(item, statusFilter) && matchesAutoTraining(item, autoTrainingFilter)) {
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
     * 触发查询或筛选过滤列表时重置勾选状态（翻页 / 每页条数变化除外）
     */
    private void resetCheckedForListRefresh() {
        tableModel.clearChecked();
        updateCheckControls();
    }
    
    private void onClassifyFilterChanged() {
        if (suppressClassifyEvents) {
            return;
        }
        resetCheckedForListRefresh();
        currentPage = 1;
        refreshQuestionTable();
    }
    
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
    
    private String selectedClassifyFilter() {
        Object selected = classifyFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        return text == null || ALL_CLASSIFY.equals(text) ? null : text;
    }
    
    private boolean matchesClassify(AiQuestion question, String classifyFilter) {
        if (classifyFilter == null) {
            return true;
        }
        return classifyFilter.equals(AiQuestionMangerDialog.nullToEmpty(question.getQuestionClassify()).trim());
    }
    
    private List<String> extractProjects(List<AiQuestion> questions) {
        LinkedHashSet<String> projects = new LinkedHashSet<>();
        for (AiQuestion item : questions) {
            String projectName = item.getProjectName();
            if (projectName != null && !projectName.isBlank()) {
                projects.add(projectName.trim());
            }
        }
        return new ArrayList<>(projects);
    }
    
    private void refreshProjectFilterOptions() {
        List<String> projects = extractProjects(allQuestions);
        Object previous = projectFilterCombo.getSelectedItem();
        suppressProjectFilterEvents = true;
        try {
            projectFilterCombo.removeAllItems();
            projectFilterCombo.addItem(ALL_PROJECT);
            for (String project : projects) {
                projectFilterCombo.addItem(project);
            }
            if (previous != null && projects.contains(previous.toString())) {
                projectFilterCombo.setSelectedItem(previous);
            } else {
                projectFilterCombo.setSelectedItem(ALL_PROJECT);
            }
        } finally {
            suppressProjectFilterEvents = false;
        }
    }
    
    private void onProjectFilterChanged() {
        if (suppressProjectFilterEvents) {
            return;
        }
        resetCheckedForListRefresh();
        currentPage = 1;
        refreshQuestionTable();
    }
    
    private String selectedProjectFilter() {
        Object selected = projectFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        return text == null || ALL_PROJECT.equals(text) ? null : text;
    }
    
    private boolean matchesProject(AiQuestion question, String projectFilter) {
        if (projectFilter == null) {
            return true;
        }
        return projectFilter.equals(AiQuestionMangerDialog.nullToEmpty(question.getProjectName()).trim());
    }
    
    /**
     * 所属项目展示文案：无项目名称或项目编码为 -1 时显示「全局」（列表、详情、导出共用）。<br> 注意编码判空与展示判定要分开：这里不用 {@code AiQuestion.isGlobalCode}，因为它把 null / 空编码也算作全局， 会覆盖掉「接口没返回
     * projectCode 但名称正常」的项目。
     */
    private static String projectDisplayText(AiQuestion question) {
        String name = AiQuestionMangerDialog.nullToEmpty(question.getProjectName()).trim();
        if (name.isEmpty() || AiQuestion.GLOBAL_CODE.equals(AiQuestionMangerDialog.nullToEmpty(question.getProjectCode()).trim())) {
            return AiQuestion.GLOBAL_DISPLAY_TEXT;
        }
        return name;
    }
    
    /**
     * 所属用户展示文案：用户ID 为空或 -1 时显示「全局」。<br> 返回原始用户ID，列表由 {@code UserIdCellRenderer} 补成「用户名称（用户ID）」；「全局」在映射里查不到，会原样透出。
     */
    private static String userDisplayText(AiQuestion question) {
        return AiQuestion.isGlobalCode(question.getUserId()) ? AiQuestion.GLOBAL_DISPLAY_TEXT
                : AiQuestionMangerDialog.nullToEmpty(question.getUserId()).trim();
    }
    
    private String selectedUserFilter() {
        Object selected = userFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        if (text == null || ALL_USER.equals(text)) {
            return null;
        }
        // 选中「全局」时传参数 0
        if (AiQuestion.GLOBAL_DISPLAY_TEXT.equals(text)) {
            return GLOBAL_USER_FILTER_VALUE;
        }
        return AiQuestionMangerDialog.extractUserIdFromDisplayText(text);
    }
    
    private boolean matchesUserFilter(AiQuestion question, String userIdFilter) {
        if (userIdFilter == null) {
            return true;
        }
        // 选中「全局」（传参 0）时匹配全部全局记录：0、-1 或空
        if (AiQuestion.isGlobalCode(userIdFilter)) {
            return AiQuestion.isGlobalCode(AiQuestionMangerDialog.nullToEmpty(question.getUserId()).trim());
        }
        return userIdFilter.equals(AiQuestionMangerDialog.nullToEmpty(question.getUserId()).trim());
    }
    
    private void onSearchFieldChanged() {
        if (suppressQuestionFilterEvents) {
            return;
        }
        resetCheckedForListRefresh();
        currentPage = 1;
        refreshQuestionTable();
    }
    
    private void onTrainingStatusFilterChanged() {
        if (suppressQuestionFilterEvents) {
            return;
        }
        resetCheckedForListRefresh();
        currentPage = 1;
        refreshQuestionTable();
    }
    
    private void resetQuestionFilters() {
        suppressQuestionFilterEvents = true;
        suppressClassifyEvents = true;
        suppressProjectFilterEvents = true;
        suppressUserFilterEvents = true;
        try {
            searchField.setText("");
            statusFilterCombo.clearSelection();
            classifyFilterCombo.setSelectedItem(ALL_CLASSIFY);
            projectFilterCombo.setSelectedItem(ALL_PROJECT);
            userFilterCombo.setSelectedItem(ALL_USER);
            autoTrainingFilterCombo.setSelectedItem(ALL_AUTO_TRAINING);
        } finally {
            suppressClassifyEvents = false;
            suppressProjectFilterEvents = false;
            suppressUserFilterEvents = false;
            suppressQuestionFilterEvents = false;
        }
        // 筛选条件重置后，勾选的行（已选 N 条）也应一并清空
        tableModel.clearChecked();
        updateCheckControls();
        currentPage = 1;
        dialog.invalidateLoadedData();
        refreshQuestionTable();
    }
    
    private Set<Integer> selectedTrainingStatusFilter() {
        if (statusFilterCombo == null) {
            return Set.of();
        }
        return new HashSet<>(statusFilterCombo.getSelectedItems());
    }
    
    private boolean matchesTrainingStatus(AiQuestion question, Set<Integer> statusFilter) {
        if (statusFilter == null || statusFilter.isEmpty()) {
            return true;
        }
        return statusFilter.contains(question.getTrainingStatus());
    }
    
    private Integer selectedAutoTrainingFilter() {
        if (autoTrainingFilterCombo == null) {
            return null;
        }
        Object selected = autoTrainingFilterCombo.getSelectedItem();
        if (selected == null || ALL_AUTO_TRAINING.equals(selected.toString())) {
            return null;
        }
        return AUTO_TRAINING_ALLOW.equals(selected.toString()) ? AiQuestion.AUTO_TRAINING_ALLOWED : AiQuestion.AUTO_TRAINING_NOT_ALLOWED;
    }
    
    private boolean matchesAutoTraining(AiQuestion question, Integer autoTrainingFilter) {
        if (autoTrainingFilter == null) {
            return true;
        }
        Integer value = question.getAllowAutoTraining();
        if (value == null) {
            value = AiQuestion.AUTO_TRAINING_ALLOWED;
        }
        return autoTrainingFilter.equals(value);
    }
    
    private boolean matchesKeyword(AiQuestion question, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String questionText = AiQuestionMangerDialog.nullToEmpty(question.getQuestion()).toLowerCase();
        String answerText = AiQuestionMangerDialog.nullToEmpty(question.getAnswer()).toLowerCase();
        String trainingNoText = AiQuestionMangerDialog.nullToEmpty(question.getTrainingNo()).toLowerCase();
        return questionText.contains(keyword) || answerText.contains(keyword) || trainingNoText.contains(keyword);
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
    
    private void applyPageSizeFromCombo() {
        Object editorValue = pageSizeCombo.getEditor().getItem();
        String text = editorValue != null ? editorValue.toString().trim() : "";
        int size = -1;
        try {
            size = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
        }
        if (size < AiQuestionMangerDialog.MIN_PAGE_SIZE || size > AiQuestionMangerDialog.MAX_PAGE_SIZE) {
            pageSizeCombo.getEditor().setItem(String.valueOf(pageSize));
            dialog.setStatus("每页条数请输入 " + AiQuestionMangerDialog.MIN_PAGE_SIZE + " ~ " + AiQuestionMangerDialog.MAX_PAGE_SIZE + " 之间的整数",
                    false);
            return;
        }
        if (size == pageSize) {
            return;
        }
        pageSize = size;
        resetCheckedForListRefresh();
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
    
    // ────────── 问题 CRUD ──────────
    
    private void showQuestionDetail(AiQuestion question) {
        // ── 上栏：上下流式布局（核心信息：可复制字段） ──
        JPanel upperPanel = new JPanel();
        upperPanel.setLayout(new BoxLayout(upperPanel, BoxLayout.Y_AXIS));
        upperPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        
        JPanel questionRow = buildLabeledDetailField("问题：",
                buildBorderlessCopyableField(AiQuestionMangerDialog.nullToEmpty(question.getQuestion())));
        JPanel answerRow = buildLabeledDetailField("固定回复：",
                AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(question.getAnswer()), 8));
        JPanel paramRow = buildLabeledDetailField("训练参数：",
                AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(question.getTrainingParam()), 4));
        JPanel remarkRow = buildLabeledDetailField("备注：",
                AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(question.getRemark()), 2));
        // 纵向对齐所有 label（取最大宽度统一设置 preferredSize）
        alignDetailFieldLabels(questionRow, answerRow, paramRow, remarkRow);
        
        upperPanel.add(questionRow);
        upperPanel.add(Box.createVerticalStrut(3));
        upperPanel.add(answerRow);
        upperPanel.add(Box.createVerticalStrut(3));
        upperPanel.add(paramRow);
        upperPanel.add(Box.createVerticalStrut(3));
        upperPanel.add(remarkRow);
        
        // ── 下栏：网格布局（一行 2 个值）其它基本信息 ──
        JPanel lowerPanel = new JPanel(new GridLayout(0, 2, 8, 4));
        lowerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        
        addDetailInfoRow(lowerPanel, "ID：", question.getId() != null ? String.valueOf(question.getId()) : "");
        // 所属项目 / 所属用户：为空或 -1 时统一显示「全局」（与列表、导出共用同一套展示规则）
        addDetailInfoRow(lowerPanel, "所属项目：", projectDisplayText(question));
        addDetailInfoRow(lowerPanel, "所属用户：", AiQuestion.isGlobalCode(question.getUserId()) ? AiQuestion.GLOBAL_DISPLAY_TEXT
                : AiQuestionMangerDialog.formatUserIdDisplay(AiQuestionMangerDialog.nullToEmpty(question.getUserId()), dialog.userOptions));
        addDetailInfoRow(lowerPanel, "分类：", AiQuestionMangerDialog.nullToEmpty(question.getQuestionClassify()));
        addDetailInfoRow(lowerPanel, "回复ID：", question.getAnswerId() != null ? String.valueOf(question.getAnswerId()) : "");
        addDetailInfoRow(lowerPanel, "开启训练：", question.isTrainingEnabled() ? "开启" : "不开启");
        addDetailInfoRow(lowerPanel, "训练状态：", AiQuestionMangerDialog.trainingStatusText(question.getTrainingStatus()));
        addDetailInfoRow(lowerPanel, "训练模式：", AiQuestionMangerDialog.trainingModelText(question.getTrainingModel()));
        addDetailInfoRow(lowerPanel, "智能体类型：", AiQuestionMangerDialog.nullToEmpty(question.getAgentType()), DEFAULT_DISPLAY_TEXT);
        addDetailInfoRow(lowerPanel, "训练模型：", AiQuestionMangerDialog.nullToEmpty(question.getCoreLlmName()), DEFAULT_DISPLAY_TEXT);
        addDetailInfoRow(lowerPanel, "意图识别模型：", AiQuestionMangerDialog.nullToEmpty(question.getIntentionLlmName()), DEFAULT_DISPLAY_TEXT);
        addDetailInfoRow(lowerPanel, "重试次数：", question.getRetryCount() == null ? "" : String.valueOf(question.getRetryCount()));
        addDetailInfoRow(lowerPanel, "训练触发时间：", AiQuestionMangerDialog.formatEpochMillis(question.getInitTrainingTime()));
        addDetailInfoRow(lowerPanel, "训练提交时间：", AiQuestionMangerDialog.formatEpochMillis(question.getSubmitTrainingTime()));
        addDetailInfoRow(lowerPanel, "训练开始时间：", AiQuestionMangerDialog.formatEpochMillis(question.getStartTrainingTime()));
        // 训练状态为「超时(4)」时：标签改为「训练超时时间」，值依然取 lastTrainingTime（即后端记录的超时时刻）
        String lastTrainingTimeLabel =
                question.getTrainingStatus() != null && question.getTrainingStatus() == AiQuestion.TRAINING_STATUS_TIMEOUT ? "训练超时时间："
                        : "训练完成时间：";
        addDetailInfoRow(lowerPanel, lastTrainingTimeLabel, AiQuestionMangerDialog.formatEpochMillis(question.getLastTrainingTime()));
        String trainingCost = AiQuestionMangerDialog.trainingCostText(question);
        addDetailInfoRow(lowerPanel, "训练耗时：", trainingCost.isEmpty() ? "" : trainingCost + " 秒");
        addDetailInfoRow(lowerPanel, "创建时间：", AiQuestionMangerDialog.formatLocalDateTime(question.getCreateTime()));
        addDetailInfoRow(lowerPanel, "更新时间：", AiQuestionMangerDialog.formatLocalDateTime(question.getUpdateTime()));
        addDetailInfoRow(lowerPanel, "批次号：", buildBorderlessCopyableField(AiQuestionMangerDialog.nullToEmpty(question.getTrainingNo())));
        addDetailInfoRow(lowerPanel, "任务ID：", buildBorderlessCopyableField(AiQuestionMangerDialog.nullToEmpty(question.getTrainingTaskId())));
        addDetailInfoRow(lowerPanel, "自动训练：", question.isAutoTrainingAllowed() ? "允许" : "不允许");
        
        // ── 上下分割条：用户可拖拽调节上下栏高度 ──
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upperPanel, new JScrollPane(lowerPanel));
        splitPane.setResizeWeight(0.55);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(splitPane, BorderLayout.CENTER);
        root.setPreferredSize(new Dimension(820, 640));
        JOptionPane.showMessageDialog(dialog, root, "问题详情-环境[" + question.getEnvName() + "]", JOptionPane.PLAIN_MESSAGE);
    }
    
    /**
     * 上栏使用：标签在左（顶部对齐），字段在右，使用 GridBagLayout 严格控制列宽
     */
    private JPanel buildLabeledDetailField(String labelText, JComponent field) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, 8);
        JLabel label = new JLabel(labelText);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
        return panel;
    }
    
    /**
     * 上栏使用：对齐多个"标签 + 字段"行中 label 的宽度，使所有 label 纵向对齐
     */
    private void alignDetailFieldLabels(JPanel... rows) {
        int maxWidth = 0;
        for (JPanel row : rows) {
            Component[] components = row.getComponents();
            if (components.length == 0 || !(components[0] instanceof JLabel label)) {
                continue;
            }
            maxWidth = Math.max(maxWidth, label.getPreferredSize().width);
        }
        for (JPanel row : rows) {
            Component[] components = row.getComponents();
            if (components.length == 0 || !(components[0] instanceof JLabel label)) {
                continue;
            }
            Dimension d = new Dimension(maxWidth, label.getPreferredSize().height);
            // preferredSize / minimumSize / maximumSize 三者统一，避免被布局拉伸或压缩
            label.setPreferredSize(d);
            label.setMinimumSize(d);
            label.setMaximumSize(d);
        }
        // 强制所有 row 重新布局，确保 label 新尺寸生效
        for (JPanel row : rows) {
            row.invalidate();
            row.validate();
            row.repaint();
        }
    }
    
    /**
     * 可复制字段：无边框但仍可选中复制（Ctrl+C / 拖选复制）。用于问题、批次号、任务ID 等长字段。<br> 前景色取主题默认文字色，保证 dark 主题下与其它值文本亮度一致。
     */
    private JTextField buildBorderlessCopyableField(String text) {
        JTextField field = new JTextField(text);
        field.setEditable(false);
        field.setBorder(null);
        field.setOpaque(false);
        Color defaultFg = UIManager.getColor("Label.foreground");
        if (defaultFg != null) {
            field.setForeground(defaultFg);
        }
        field.setToolTipText("可拖选文字复制，或按 Ctrl+C");
        return field;
    }
    
    /**
     * 下栏使用：标签（浅中性色）+ 值，组成一个网格单元
     */
    private void addDetailInfoRow(JPanel panel, String labelText, String value) {
        addDetailInfoRow(panel, labelText, new JLabel(value != null ? value : ""));
    }
    
    /**
     * 下栏使用：标签（浅中性色）+ 值，组成一个网格单元；值为空字符串时回退到 {@code emptyFallback}
     */
    private void addDetailInfoRow(JPanel panel, String labelText, String value, String emptyFallback) {
        String display = (value == null || value.isEmpty()) && emptyFallback != null ? emptyFallback : value;
        addDetailInfoRow(panel, labelText, new JLabel(display != null ? display : ""));
    }
    
    /**
     * 下栏使用：标签（浅中性色）+ 自定义值组件（如可复制的 JTextField），组成一个网格单元。<br> 标签用 {@code COLOR_NEUTRAL_LIGHT}（比 Color.GRAY 更亮，dark 主题下不偏暗）；值组件用主题默认文字色（最亮），与下栏其它行
     * JLabel 值一致。
     */
    private void addDetailInfoRow(JPanel panel, String labelText, JComponent valueComponent) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        JLabel label = new JLabel(labelText);
        label.setForeground(UiConstants.COLOR_NEUTRAL_LIGHT);
        row.add(label, BorderLayout.WEST);
        Color defaultFg = UIManager.getColor("Label.foreground");
        if (defaultFg != null) {
            valueComponent.setForeground(defaultFg);
        }
        row.add(valueComponent, BorderLayout.CENTER);
        panel.add(row);
    }
    
    private void viewAnswerInfo(AiQuestion question) {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = dialog.findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(question.getEnvName()) + "]",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Long answerId = question.getAnswerId();
        String questionText = question.getQuestion();
        String fixedAnswer = question.getAnswer();
        dialog.runApiTask("查询回复详情", () -> {
            LinkedHashMap<String, String> fields = AiQuestionApiClient.getInstance().getAnswerInfo(envConfig, answerId);
            return () -> {
                dialog.setStatus("回复详情查询成功（回复ID " + answerId + "）", true);
                showAnswerInfoDetail(answerId, questionText, fixedAnswer, fields);
            };
        });
    }
    
    private void showAnswerInfoDetail(Long answerId, String questionText, String fixedAnswer, LinkedHashMap<String, String> fields) {
        String query = AiQuestionMangerDialog.firstNonBlank(fields.get("query"), fields.get("question"), questionText);
        JScrollPane answerScroll = AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(fields.get("answer")), 15);
        answerScroll.setBorder(BorderFactory.createTitledBorder("回复内容（回复审计）"));
        JScrollPane fixedScroll = AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(fixedAnswer), 15);
        fixedScroll.setBorder(BorderFactory.createTitledBorder("固定回复（问题训练）"));
        JSplitPane comparePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, answerScroll, fixedScroll);
        comparePane.setResizeWeight(0.5);
        comparePane.setDividerLocation(0.5);
        comparePane.setPreferredSize(new Dimension(700, 250));
        
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        dialog.addFormRow(form, gbc, 0, new JLabel("回复ID："), new JLabel(String.valueOf(answerId)));
        dialog.addFormRow(form, gbc, 1, new JLabel("回复问题："), new JLabel(AiQuestionMangerDialog.nullToEmpty(query)));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(comparePane, gbc);
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        dialog.addFormRow(form, gbc, 3, new JLabel("是否允许修改："), new JLabel(AiQuestionMangerDialog.answerModifyText(fields.get("allowModify"))));
        JOptionPane.showMessageDialog(dialog, form, "回复详情", JOptionPane.PLAIN_MESSAGE);
    }
    
    private void editQuestion(AiQuestion question) {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextField questionArea = new JTextField(AiQuestionMangerDialog.nullToEmpty(question.getQuestion()));
        // 所属用户：首项「全局」对应用户ID 为空 / 0 / -1 的记录（与列表、详情展示规则一致），其余为「用户ID - 名称」
        FilterComboBox<String> userIdCombo = new FilterComboBox<>();
        userIdCombo.addItem(AiQuestion.GLOBAL_DISPLAY_TEXT);
        for (String userOption : dialog.userOptions) {
            userIdCombo.addItem(userOption);
        }
        userIdCombo.setToolTipText("单选所属用户；选择「全局」表示不绑定具体用户（提交空值，与 0 等价）");
        String currentUserId = AiQuestionMangerDialog.nullToEmpty(question.getUserId());
        if (AiQuestion.isGlobalCode(currentUserId)) {
            // 用户ID 为空 / 0 / -1 统一回显「全局」，避免下拉留空或显示裸 ID
            userIdCombo.setSelectedIndex(0);
        } else {
            boolean matched = false;
            for (int i = 1; i < userIdCombo.getItemCount(); i++) {
                String item = userIdCombo.getItemAt(i);
                if (item != null && AiQuestionMangerDialog.extractUserIdFromCombo(item).equals(currentUserId)) {
                    userIdCombo.setSelectedIndex(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                userIdCombo.getEditor().setItem(currentUserId);
            }
        }
        // 所属项目（单选）：首项「全局」提交空字符串，其余为项目「编码 - 名称」；选项索引与编码一一对应，避免字符串解析
        List<String> editProjectCodeOptions = new ArrayList<>();
        editProjectCodeOptions.add("");
        FilterComboBox<String> projectCombo = new FilterComboBox<>();
        projectCombo.addItem(AiQuestion.GLOBAL_DISPLAY_TEXT);
        for (String[] project : dialog.projectOptions) {
            String code = project[0];
            String name = project[1];
            projectCombo.addItem(code + (name.isEmpty() ? "" : " - " + name));
            editProjectCodeOptions.add(code);
        }
        projectCombo.setToolTipText("单选所属项目；选择「全局」表示不绑定具体项目（提交空字符串）");
        // 回显：projectCode 为 -1 或空均视为「全局」（保持下拉首项）；仅当 projectCode 完全缺失时才回退到
        // projectCodeList 首项与 projectName 反查，避免把显式标记为全局的问题误判成具体项目
        String rawProjectCode = question.getProjectCode();
        boolean projectCodePresent = rawProjectCode != null && !rawProjectCode.isBlank();
        String currentProjectCode = "";
        if (projectCodePresent && !AiQuestion.isGlobalCode(rawProjectCode)) {
            currentProjectCode = rawProjectCode.trim();
        }
        if (currentProjectCode.isEmpty() && !projectCodePresent) {
            List<String> existingProjectCodes = question.getProjectCodeList();
            if (existingProjectCodes != null && !existingProjectCodes.isEmpty() && existingProjectCodes.get(0) != null && !AiQuestion.isGlobalCode(
                    existingProjectCodes.get(0))) {
                currentProjectCode = existingProjectCodes.get(0).trim();
            }
        }
        if (currentProjectCode.isEmpty() && !projectCodePresent) {
            String currentProjectName = AiQuestionMangerDialog.nullToEmpty(question.getProjectName()).trim();
            if (!currentProjectName.isEmpty()) {
                for (String[] project : dialog.projectOptions) {
                    String code = project[0];
                    String name = project[1];
                    if (currentProjectName.equals(code) || (!name.isEmpty() && currentProjectName.equals(name))) {
                        currentProjectCode = code;
                        break;
                    }
                }
            }
        }
        if (!currentProjectCode.isEmpty()) {
            boolean projectMatched = false;
            for (int i = 1; i < editProjectCodeOptions.size(); i++) {
                if (currentProjectCode.equals(editProjectCodeOptions.get(i))) {
                    projectCombo.setSelectedIndex(i);
                    projectMatched = true;
                    break;
                }
            }
            if (!projectMatched) {
                projectCombo.getEditor().setItem(currentProjectCode);
            }
        }
        FilterComboBox<String> classifyCombo = new FilterComboBox<>();
        for (String classify : dialog.classifySuggestions) {
            classifyCombo.addItem(classify);
        }
        String currentClassify = AiQuestionMangerDialog.nullToEmpty(question.getQuestionClassify());
        if (!currentClassify.isEmpty()) {
            boolean classifyMatched = false;
            for (int i = 0; i < classifyCombo.getItemCount(); i++) {
                if (currentClassify.equals(classifyCombo.getItemAt(i))) {
                    classifyCombo.setSelectedIndex(i);
                    classifyMatched = true;
                    break;
                }
            }
            if (!classifyMatched) {
                classifyCombo.getEditor().setItem(currentClassify);
            }
        }
        JTextArea trainingParamArea = new JTextArea(AiQuestionMangerDialog.nullToEmpty(question.getTrainingParam()), 5, 30);
        trainingParamArea.setLineWrap(true);
        trainingParamArea.setWrapStyleWord(true);
        JTextArea answerArea = new JTextArea(AiQuestionMangerDialog.nullToEmpty(question.getAnswer()), 10, 30);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        JComboBox<String> enableCombo = new JComboBox<>(new String[] {"开启", "不开启"});
        enableCombo.setSelectedIndex(question.isTrainingEnabled() ? 0 : 1);
        JComboBox<String> autoTrainingEditCombo = new JComboBox<>(new String[] {"允许", "不允许"});
        autoTrainingEditCombo.setSelectedIndex(question.isAutoTrainingAllowed() ? 0 : 1);
        JTextField priorityField = new JTextField(String.valueOf(question.getPriority() != null ? question.getPriority() : 0), 8);
        
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        dialog.addFormRow(form, gbc, 0, new JLabel("问题："), questionArea);
        dialog.addFormRow(form, gbc, 1, new JLabel("所属用户："), userIdCombo);
        dialog.addFormRow(form, gbc, 2, new JLabel("所属项目："), projectCombo);
        dialog.addFormRow(form, gbc, 3, new JLabel("分类："), classifyCombo);
        dialog.addFormRow(form, gbc, 4, new JLabel("训练参数："), AiQuestionMangerDialog.dialogScroll(trainingParamArea, 5));
        dialog.addFormRow(form, gbc, 5, new JLabel("固定回复："), AiQuestionMangerDialog.dialogScroll(answerArea, 10));
        dialog.addFormRow(form, gbc, 6, new JLabel("开启训练："), enableCombo);
        dialog.addFormRow(form, gbc, 7, new JLabel("自动训练："), autoTrainingEditCombo);
        
        int option = JOptionPane.showConfirmDialog(dialog, form, "编辑问题-环境[" + question.getEnvName() + "]", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        String newQuestion = questionArea.getText().trim();
        if (newQuestion.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "问题内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int newPriority;
        try {
            newPriority = Integer.parseInt(priorityField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(dialog, "优先级别必须为整数", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newClassify = dialog.editableComboText(classifyCombo).trim();
        // 所属用户：以编辑器文本为准；「全局」或手动输入 0 / -1 / 空统一归一化为空值提交（与「全局」展示规则一致）
        String userEditorText = dialog.editableComboText(userIdCombo).trim();
        String newUserId = (AiQuestion.GLOBAL_DISPLAY_TEXT.equals(userEditorText) || AiQuestion.isGlobalCode(userEditorText)) ? ""
                : AiQuestionMangerDialog.extractUserIdFromCombo(userEditorText).trim();
        question.setQuestion(newQuestion);
        question.setUserId(newUserId.isEmpty() ? null : newUserId);
        question.setQuestionClassify(newClassify.isEmpty() ? null : newClassify);
        question.setTrainingParam(trainingParamArea.getText().trim());
        question.setAnswer(answerArea.getText().trim());
        question.setEnableTraining(enableCombo.getSelectedIndex() == 0 ? AiQuestion.TRAINING_ENABLED : AiQuestion.TRAINING_DISABLED);
        question.setAllowAutoTraining(
                autoTrainingEditCombo.getSelectedIndex() == 0 ? AiQuestion.AUTO_TRAINING_ALLOWED : AiQuestion.AUTO_TRAINING_NOT_ALLOWED);
        question.setPriority(newPriority);
        // 所属项目单选：以编辑器文本为准判定（回显的非候选编码时选中索引会停留在「全局」，不能只按索引取）
        String projectEditorText = dialog.editableComboText(projectCombo).trim();
        int projectIndex = projectCombo.getSelectedIndex();
        String newProjectCode;
        if (AiQuestion.GLOBAL_DISPLAY_TEXT.equals(projectEditorText) || AiQuestion.isGlobalCode(projectEditorText)) {
            // 「全局」或手动输入 -1 / 空：统一归一化为空字符串提交（与 -1 等价）
            newProjectCode = "";
        } else if (projectIndex > 0 && projectIndex < editProjectCodeOptions.size() && projectEditorText.equals(
                projectCombo.getItemAt(projectIndex))) {
            newProjectCode = editProjectCodeOptions.get(projectIndex);
        } else {
            newProjectCode = projectEditorText;
        }
        question.setProjectCode(newProjectCode);
        AiEnvConfig envConfig = dialog.findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(question.getEnvName()) + "]",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (question.getId() == null) {
            JOptionPane.showMessageDialog(dialog, "该问题缺少 ID，无法调用更新接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dialog.runApiTask("更新问题", () -> {
            String message = AiQuestionApiClient.getInstance().updateQuestion(envConfig, question);
            return () -> {
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("更新问题成功：" + AiQuestionMangerDialog.abbreviate(newQuestion, 30) + "（" + message + "）", true);
            };
        });
    }
    
    private void deleteQuestion(AiQuestion question) {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = dialog.findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(question.getEnvName()) + "]",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (question.getId() == null) {
            JOptionPane.showMessageDialog(dialog, "该问题缺少 ID，无法调用删除接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(dialog,
                "确定删除问题 [" + AiQuestionMangerDialog.abbreviate(question.getQuestion(), 30) + "] 吗？",
                "确认删除-环境 [" + envConfig.getEnvName() + "]", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        dialog.runApiTask("删除问题", () -> {
            String message = AiQuestionApiClient.getInstance().deleteQuestion(envConfig, question.getId());
            return () -> {
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("已删除问题：" + AiQuestionMangerDialog.abbreviate(question.getQuestion(), 30) + "（" + message + "）", true);
            };
        });
    }
    
    // ────────── 训练操作 ──────────
    
    private void trainSingleQuestion(AiQuestion question) {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (question.getId() == null) {
            JOptionPane.showMessageDialog(dialog, "该问题缺少 ID，无法调用训练接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (question.getUserId() == null || question.getUserId().isBlank()) {
            JOptionPane.showMessageDialog(dialog, "该问题缺少所属用户，不允许触发训练，请先通过「编辑」或「批量更新」补充所属用户。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 训练中 / 训练已提交状态的问题不能再次触发训练
        Integer trainingStatus = question.getTrainingStatus();
        if (trainingStatus != null && (trainingStatus == AiQuestion.TRAINING_STATUS_RUNNING
                || trainingStatus == AiQuestion.TRAINING_STATUS_SUBMITTED)) {
            JOptionPane.showMessageDialog(dialog,
                    "该问题处于「" + AiQuestionMangerDialog.trainingStatusText(trainingStatus) + "」状态，不允许再次触发训练（如需中止请使用「停止训练」）。",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = dialog.findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(question.getEnvName()) + "]",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // AI 元数据下拉（智能体类型 / 训练模型 / 意图分析模型）：支持下拉选中与手动输入，训练模型与意图分析模型允许留空
        dialog.refreshAiMetaConfigIfNeeded();
        AiMetaConfig metaConfig = dialog.aiMetaConfig;
        JComboBox<AgentOption> agentTypeCombo = createAgentTypeCombo(metaConfig.agentOptions());
        JComboBox<String> coreLlmCombo = createMetaCombo(metaConfig.coreLlmNameOptions(), CORE_LLM_TIP);
        JComboBox<String> intentionLlmCombo = createMetaCombo(metaConfig.intentionLlmNameOptions(), INTENTION_LLM_TIP);
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定对该问题触发训练吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        JLabel envLabel = new JLabel("问题ID " + question.getId());
        envLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(envLabel);
        JLabel questionCaptionLabel = new JLabel("问题：" + AiQuestionMangerDialog.nullToEmpty(question.getQuestion()));
        questionCaptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(questionCaptionLabel);
        // 单问题训练并发数固定为 1，不开放编辑
        JPanel singleParamPanel = new JPanel(new GridBagLayout());
        singleParamPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(3, 2, 3, 6);
        pgbc.anchor = GridBagConstraints.WEST;
        addParamRow(singleParamPanel, pgbc, 0, "智能体类型：", agentTypeCombo);
        addParamRow(singleParamPanel, pgbc, 1, "训练模型：", coreLlmCombo);
        addParamRow(singleParamPanel, pgbc, 2, "意图分析模型：", intentionLlmCombo);
        confirmPanel.add(Box.createVerticalStrut(8));
        confirmPanel.add(singleParamPanel);
        // 弹窗展示期间元数据若尚未返回，异步加载完成后回填下拉选项
        openMetaComboGroup = new MetaComboGroup(agentTypeCombo, coreLlmCombo, intentionLlmCombo);
        int confirm;
        try {
            confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认触发训练-环境 [" + envConfig.getEnvName() + "]",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        } finally {
            openMetaComboGroup = null;
        }
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        // 单问题训练：并发数固定为 1，直接提交
        final Integer concurrency = 1;
        String agentType = requireAuto(agentTypeText(agentTypeCombo));
        String coreLlmName = requireAuto(comboText(coreLlmCombo));
        String intentionLlmName = requireAuto(comboText(intentionLlmCombo));
        Long questionId = question.getId();
        String questionText = question.getQuestion();
        dialog.runApiTask("触发训练", () -> {
            String message = AiQuestionApiClient.getInstance()
                    .triggerTraining(envConfig, List.of(questionId), agentType, concurrency, coreLlmName, intentionLlmName);
            return () -> {
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("触发训练成功：" + AiQuestionMangerDialog.abbreviate(questionText, 30) + "（" + message + "）", true);
            };
        });
    }
    
    /**
     * 批量触发勾选问题的训练：「训练中 / 训练已提交」状态的问题不允许再次触发，缺少所属用户的问题不允许触发
     */
    private void triggerTrainingQuestions() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先在列表中勾选要触发训练的问题（点击表头复选框可全选当前页）", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 训练中 / 训练已提交状态的问题不能再次触发训练
        List<AiQuestion> trainingBusyQuestions = new ArrayList<>();
        for (AiQuestion question : checkedQuestions) {
            Integer status = question.getTrainingStatus();
            if (status != null && (status == AiQuestion.TRAINING_STATUS_RUNNING || status == AiQuestion.TRAINING_STATUS_SUBMITTED)) {
                trainingBusyQuestions.add(question);
            }
        }
        if (!trainingBusyQuestions.isEmpty()) {
            int maxShown = 10;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < trainingBusyQuestions.size() && i < maxShown; i++) {
                AiQuestion question = trainingBusyQuestions.get(i);
                lines.add(" ID " + question.getId() + "（" + AiQuestionMangerDialog.trainingStatusText(question.getTrainingStatus()) + "）："
                        + AiQuestionMangerDialog.abbreviate(question.getQuestion(), 30));
            }
            if (trainingBusyQuestions.size() > maxShown) {
                lines.add(" …等共 " + trainingBusyQuestions.size() + " 条");
            }
            JOptionPane.showMessageDialog(dialog,
                    "选中的 " + checkedQuestions.size() + " 条问题中有 " + trainingBusyQuestions.size() + " 条处于「训练中 / 训练已提交」状态，不允许再次触发训练：\n"
                            + String.join("\n", lines) + "\n\n请取消勾选这些问题（如需中止请先使用「停止训练」）后再触发训练。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
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
                lines.add(" ID " + question.getId() + "：" + AiQuestionMangerDialog.abbreviate(question.getQuestion(), 30));
            }
            if (missingUserIdQuestions.size() > maxShown) {
                lines.add(" …等共 " + missingUserIdQuestions.size() + " 条");
            }
            JOptionPane.showMessageDialog(dialog,
                    "选中的 " + checkedQuestions.size() + " 条问题中有 " + missingUserIdQuestions.size() + " 条缺少所属用户，不允许触发训练：\n"
                            + String.join("\n", lines) + "\n\n请先通过「批量更新」为这些问题补充所属用户后再触发训练。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : checkedQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        // 批量触发训练只支持单一环境：跨环境时拒绝并提示用户重新勾选
        if (envIdGroups.size() > 1) {
            JOptionPane.showMessageDialog(dialog, "选中的 " + checkedQuestions.size() + " 条问题跨 " + envIdGroups.size() + " 个环境，暂不支持同时触发。\n\n"
                            + "请重新勾选，只保留同一环境的问题再触发训练。\n\n当前选中环境：\n" + String.join("、", envIdGroups.keySet()), "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map.Entry<String, List<Long>> onlyEntry = envIdGroups.entrySet().iterator().next();
        String envName = onlyEntry.getKey();
        List<Long> ids = onlyEntry.getValue();
        AiEnvConfig envConfig = dialog.findEnvByName(envName);
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(envName) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // AI 元数据下拉（智能体类型 / 训练模型 / 意图分析模型）：支持下拉选中与手动输入，训练模型与意图分析模型允许留空
        dialog.refreshAiMetaConfigIfNeeded();
        AiMetaConfig metaConfig = dialog.aiMetaConfig;
        JComboBox<AgentOption> agentTypeCombo = createAgentTypeCombo(metaConfig.agentOptions());
        JComboBox<String> coreLlmCombo = createMetaCombo(metaConfig.coreLlmNameOptions(), CORE_LLM_TIP);
        JComboBox<String> intentionLlmCombo = createMetaCombo(metaConfig.intentionLlmNameOptions(), INTENTION_LLM_TIP);
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定触发" + ids.size() + " 条问题吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        CustomTextField concurrencyField = new CustomTextField("留空走服务端默认");
        concurrencyField.setToolTipText("请输入 ≥ 1 的整数；留空则不提交该字段。单条问题默认设为 1，其他情况由服务端决定");
        // 数字框紧凑：默认 4 位整数够用，避免与下拉框宽度悬殊
        concurrencyField.setMinWidth(80);
        // 限制只允许输入数字（粘贴也会被过滤），提交时再校验 >= 1
        ((javax.swing.text.AbstractDocument) concurrencyField.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                    throws javax.swing.text.BadLocationException {
                if (string != null && string.matches("\\d*")) {
                    super.insertString(fb, offset, string, attr);
                }
            }
            
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                    throws javax.swing.text.BadLocationException {
                if (text == null || text.matches("\\d*")) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        JPanel paramsPanel = new JPanel(new GridBagLayout());
        paramsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(3, 2, 3, 6);
        pgbc.anchor = GridBagConstraints.WEST;
        // 第 1 行：智能体类型；第 2-4 行：智能体对象 / 训练模型 / 意图分析模型（取自 AI 元数据接口，允许留空）；第 5 行：训练并发数
        addParamRow(paramsPanel, pgbc, 0, "智能体类型：", agentTypeCombo);
        addParamRow(paramsPanel, pgbc, 1, "训练模型：", coreLlmCombo);
        addParamRow(paramsPanel, pgbc, 2, "意图分析模型：", intentionLlmCombo);
        addParamRow(paramsPanel, pgbc, 3, "训练并发数：", concurrencyField);
        confirmPanel.add(Box.createVerticalStrut(8));
        confirmPanel.add(paramsPanel);
        // 弹窗展示期间元数据若尚未返回，异步加载完成后回填下拉选项
        openMetaComboGroup = new MetaComboGroup(agentTypeCombo, coreLlmCombo, intentionLlmCombo);
        int confirm;
        try {
            confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认触发训练 - 环境 [" + envName + "]", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
        } finally {
            openMetaComboGroup = null;
        }
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        // 解析训练并发数：空值走服务端默认；非空必须是 >= 1 的整数
        // 必须用 if/else 各赋一次值，让 concurrency 是 effectively final，可被下方 lambda 捕获
        final Integer concurrency;
        String concurrencyText = concurrencyField.getText() == null ? "" : concurrencyField.getText().trim();
        if (concurrencyText.isEmpty()) {
            concurrency = null;
        } else {
            int parsed = Integer.parseInt(concurrencyText);
            if (parsed < 1) {
                JOptionPane.showMessageDialog(dialog, "训练并发数必须 >= 1", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            concurrency = parsed;
        }
        String agentType = requireAuto(agentTypeText(agentTypeCombo));
        String coreLlmName = requireAuto(comboText(coreLlmCombo));
        String intentionLlmName = requireAuto(comboText(intentionLlmCombo));
        dialog.runApiTask("触发训练", () -> {
            String message = AiQuestionApiClient.getInstance().triggerTraining(envConfig, ids, agentType, concurrency, coreLlmName, intentionLlmName);
            int successCount = ids.size();
            return () -> {
                tableModel.clearChecked();
                updateCheckControls();
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("触发训练成功：环境 [" + envName + "] 共 " + successCount + " 条问题（" + message + "）", true);
            };
        });
    }
    
    /**
     * 批量停止勾选问题的训练：仅「待训练 / 训练中 / 训练已提交」状态的问题可停止，其余状态跳过并在确认框中提示
     */
    private void batchStopTrainingQuestions() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先在列表中勾选要停止训练的问题（点击表头复选框可全选当前页）", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> stoppableQuestions = new ArrayList<>();
        List<AiQuestion> skippedQuestions = new ArrayList<>();
        for (AiQuestion question : checkedQuestions) {
            Integer status = question.getTrainingStatus();
            if (status != null && (status == AiQuestion.TRAINING_STATUS_PENDING || status == AiQuestion.TRAINING_STATUS_RUNNING
                    || status == AiQuestion.TRAINING_STATUS_SUBMITTED)) {
                stoppableQuestions.add(question);
            } else {
                skippedQuestions.add(question);
            }
        }
        if (stoppableQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog,
                    "选中的 " + checkedQuestions.size() + " 条问题均不在「待训练 / 训练中 / 训练已提交」状态，无需停止训练。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : stoppableQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        // 停止训练只支持单一环境：跨环境时拒绝并提示用户重新勾选
        if (envIdGroups.size() > 1) {
            JOptionPane.showMessageDialog(dialog, "选中的 " + stoppableQuestions.size() + " 条问题跨 " + envIdGroups.size() + " 个环境，暂不支持同时停止。\n\n"
                            + "请重新勾选，只保留同一环境的问题再停止训练。\n\n当前选中环境：\n" + String.join("、", envIdGroups.keySet()), "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map.Entry<String, List<Long>> onlyEntry = envIdGroups.entrySet().iterator().next();
        String envName = onlyEntry.getKey();
        List<Long> ids = onlyEntry.getValue();
        AiEnvConfig envConfig = dialog.findEnvByName(envName);
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(envName) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定停止" + stoppableQuestions.size() + " 条问题的训练吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        if (!skippedQuestions.isEmpty()) {
            int maxShown = 10;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < skippedQuestions.size() && i < maxShown; i++) {
                AiQuestion question = skippedQuestions.get(i);
                lines.add(" ID " + question.getId() + "（" + AiQuestionMangerDialog.trainingStatusText(question.getTrainingStatus()) + "）："
                        + AiQuestionMangerDialog.abbreviate(question.getQuestion(), 30));
            }
            if (skippedQuestions.size() > maxShown) {
                lines.add(" …等共 " + skippedQuestions.size() + " 条");
            }
            JLabel skippedLabel = new JLabel("另有 " + skippedQuestions.size() + " 条不在「待训练 / 训练中 / 训练已提交」状态，将被跳过：");
            skippedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            confirmPanel.add(Box.createVerticalStrut(8));
            confirmPanel.add(skippedLabel);
            for (String line : lines) {
                JLabel skipLineLabel = new JLabel(line);
                skipLineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                confirmPanel.add(skipLineLabel);
            }
        }
        int confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认停止训练 - 环境 [" + envName + "]", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        dialog.runApiTask("停止训练", () -> {
            String message = AiQuestionApiClient.getInstance().stopTraining(envConfig, ids);
            int successCount = ids.size();
            return () -> {
                tableModel.clearChecked();
                updateCheckControls();
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("停止训练成功：环境 [" + envName + "] 共 " + successCount + " 条问题（" + message + "）", true);
            };
        });
    }
    
    // ────────── 批量操作 ──────────
    
    private void batchUpdateUserQuestions() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先在列表中勾选要批量更新的问题（点击表头复选框可全选当前页）", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : checkedQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        // 批量更新只支持单一环境：跨环境时拒绝并提示用户重新勾选
        if (envIdGroups.size() > 1) {
            JOptionPane.showMessageDialog(dialog, "选中的 " + checkedQuestions.size() + " 条问题跨 " + envIdGroups.size() + " 个环境，暂不支持同时批量更新。\n\n"
                            + "请重新勾选，只保留同一环境的问题再批量更新。\n\n当前选中环境：\n" + String.join("、", envIdGroups.keySet()), "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map.Entry<String, List<Long>> onlyEntry = envIdGroups.entrySet().iterator().next();
        String envName = onlyEntry.getKey();
        List<Long> ids = onlyEntry.getValue();
        AiEnvConfig envConfig = dialog.findEnvByName(envName);
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(envName) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 所属用户：首项「不更新」保留原值，其余为「用户ID - 名称」；仍可编辑以直接输入用户ID
        FilterComboBox<String> userIdCombo = new FilterComboBox<>();
        userIdCombo.addItem(BATCH_NO_UPDATE_OPTION);
        for (String userOption : dialog.userOptions) {
            userIdCombo.addItem(userOption);
        }
        userIdCombo.setSelectedIndex(0);
        userIdCombo.setToolTipText("选择「不更新」保留原有用户，或下拉选择 / 直接输入用户ID");
        // 所属项目（单选）：首项「不更新」保留原值，「全局」提交空字符串，其余提交项目编码。
        // 批量操作不接受手工输入编码，故用不可编辑下拉，避免误把无效值批量写入
        List<String> batchProjectCodes = new ArrayList<>();
        batchProjectCodes.add(null);
        JComboBox<String> batchProjectCombo = new JComboBox<>();
        batchProjectCombo.addItem(BATCH_NO_UPDATE_OPTION);
        batchProjectCombo.addItem(AiQuestion.GLOBAL_DISPLAY_TEXT);
        batchProjectCodes.add("");
        for (String[] project : dialog.projectOptions) {
            String code = project[0];
            String name = project[1];
            batchProjectCombo.addItem(code + (name.isEmpty() ? "" : " - " + name));
            batchProjectCodes.add(code);
        }
        batchProjectCombo.setSelectedIndex(0);
        batchProjectCombo.setToolTipText("选择「不更新」保留原有项目；「全局」表示不绑定具体项目（提交空字符串）");
        JTextArea trainingParamArea = new JTextArea(3, 24);
        trainingParamArea.setLineWrap(true);
        trainingParamArea.setWrapStyleWord(true);
        JPanel inputForm = new JPanel(new GridBagLayout());
        GridBagConstraints inputGbc = new GridBagConstraints();
        inputGbc.insets = new Insets(4, 4, 4, 4);
        inputGbc.anchor = GridBagConstraints.WEST;
        dialog.addFormRow(inputForm, inputGbc, 0, new JLabel("所属用户："), userIdCombo);
        dialog.addFormRow(inputForm, inputGbc, 1, new JLabel("所属项目："), batchProjectCombo);
        dialog.addFormRow(inputForm, inputGbc, 2, new JLabel("训练参数："), new JScrollPane(trainingParamArea));
        JComboBox<String> autoTrainingCombo = new JComboBox<>(new String[] {BATCH_NO_UPDATE_OPTION, "允许", "不允许"});
        autoTrainingCombo.setSelectedIndex(0);
        dialog.addFormRow(inputForm, inputGbc, 3, new JLabel("自动训练："), autoTrainingCombo);
        inputForm.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel emptyHintLabel = new JLabel("「不更新」或留空表示该字段保持原值，四项至少选择一项");
        emptyHintLabel.setForeground(Color.GRAY);
        emptyHintLabel.setFont(UiConstants.FONT_SANS_11);
        emptyHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定批量更新" + checkedQuestions.size() + " 条问题吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        confirmPanel.add(Box.createVerticalStrut(8));
        confirmPanel.add(inputForm);
        confirmPanel.add(Box.createVerticalStrut(4));
        confirmPanel.add(emptyHintLabel);
        String userId;
        String trainingParam;
        int autoTrainingIndex;
        int batchProjectIndex;
        while (true) {
            int confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认批量更新 - 环境 [" + envName + "]", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            // 以编辑器文本为准：「不更新」或留空表示保留原值；否则按「用户ID - 名称」解析。
            // 必须先拦截「不更新」——extractUserIdFromCombo 会把它原样当作用户ID返回
            String userEditorText = dialog.editableComboText(userIdCombo).trim();
            userId = BATCH_NO_UPDATE_OPTION.equals(userEditorText) || userEditorText.isEmpty() ? ""
                    : AiQuestionMangerDialog.extractUserIdFromCombo(userEditorText).trim();
            trainingParam = trainingParamArea.getText().trim();
            autoTrainingIndex = autoTrainingCombo.getSelectedIndex();
            batchProjectIndex = batchProjectCombo.getSelectedIndex();
            if (!userId.isEmpty() || !trainingParam.isEmpty() || autoTrainingIndex > 0 || batchProjectIndex > 0) {
                break;
            }
            JOptionPane.showMessageDialog(dialog, "所属用户、所属项目、训练参数与自动训练不能同时为空，请至少选择一项", "提示",
                    JOptionPane.WARNING_MESSAGE);
        }
        String targetUserId = userId.isEmpty() ? null : userId;
        String targetTrainingParam = trainingParam.isEmpty() ? null : trainingParam;
        Integer targetAllowAutoTraining = autoTrainingCombo.getSelectedIndex() == 0 ? null
                : (autoTrainingCombo.getSelectedIndex() == 1 ? AiQuestion.AUTO_TRAINING_ALLOWED : AiQuestion.AUTO_TRAINING_NOT_ALLOWED);
        // 索引 0「不更新」→ null（不提交该字段）；索引 1「全局」→ 空字符串；其余为项目编码
        String targetProjectCode =
                batchProjectIndex > 0 && batchProjectIndex < batchProjectCodes.size() ? batchProjectCodes.get(batchProjectIndex) : null;
        dialog.runApiTask("批量更新", () -> {
            String message = AiQuestionApiClient.getInstance()
                    .batchUpdateUser(envConfig, ids, targetUserId, targetTrainingParam, targetAllowAutoTraining, targetProjectCode);
            int successCount = ids.size();
            return () -> {
                tableModel.clearChecked();
                updateCheckControls();
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("批量更新成功：环境 [" + envName + "] 共 " + successCount + " 条问题（" + message + "）", true);
            };
        });
    }
    
    private void batchDeleteQuestions() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiQuestion> checkedQuestions = tableModel.collectChecked(allQuestions);
        if (checkedQuestions.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先在列表中勾选要批量删除的问题（点击表头复选框可全选当前页）", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiQuestion question : checkedQuestions) {
            envIdGroups.computeIfAbsent(question.getEnvName(), key -> new ArrayList<>()).add(question.getId());
        }
        // 批量删除只支持单一环境：跨环境时拒绝并提示用户重新勾选
        if (envIdGroups.size() > 1) {
            JOptionPane.showMessageDialog(dialog, "选中的 " + checkedQuestions.size() + " 条问题跨 " + envIdGroups.size() + " 个环境，暂不支持同时批量删除。\n\n"
                            + "请重新勾选，只保留同一环境的问题再批量删除。\n\n当前选中环境：\n" + String.join("、", envIdGroups.keySet()), "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Map.Entry<String, List<Long>> onlyEntry = envIdGroups.entrySet().iterator().next();
        String envName = onlyEntry.getKey();
        List<Long> ids = onlyEntry.getValue();
        AiEnvConfig envConfig = dialog.findEnvByName(envName);
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到问题所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(envName) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定批量删除" + checkedQuestions.size() + " 条问题吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        JLabel warningLabel = new JLabel("删除后不可恢复，请谨慎操作");
        warningLabel.setForeground(UiConstants.COLOR_DANGER_LIGHT);
        warningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(Box.createVerticalStrut(6));
        confirmPanel.add(warningLabel);
        int confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认批量删除 - 环境 [" + envName + "]", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        dialog.runApiTask("批量删除", () -> {
            String message = AiQuestionApiClient.getInstance().deleteQuestions(envConfig, ids);
            int successCount = ids.size();
            return () -> {
                tableModel.clearChecked();
                updateCheckControls();
                dialog.invalidateLoadedData();
                refreshQuestionTable();
                dialog.setStatus("批量删除成功：环境 [" + envName + "] 共 " + successCount + " 条问题（" + message + "）", true);
            };
        });
    }
    
    // ────────── 内部类：表格模型 ──────────
    
    private class QuestionTableModel extends AbstractTableModel {
        
        private final String[] columns = {"选择", "ID", "问题", "分类", "所属用户", "所属项目", "训练参数", "批次号", "固定回复", "回复ID",
                "自动训练", "训练模式", "训练状态", "训练提交时间", "训练开始时间", "训练完成时间", "训练耗时(秒)", "备注", "操作"};
        
        private final List<AiQuestion> questions = new ArrayList<>();
        
        private final Set<String> checkedKeys = new LinkedHashSet<>();
        
        void setData(List<AiQuestion> list) {
            questions.clear();
            questions.addAll(list);
            fireTableDataChanged();
        }
        
        AiQuestion getQuestionAt(int row) {
            return questions.get(row);
        }
        
        private static String checkKey(AiQuestion question) {
            return question.getEnvName() + "#" + question.getId();
        }
        
        boolean isChecked(AiQuestion question) {
            return question.getId() != null && checkedKeys.contains(checkKey(question));
        }
        
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
        
        int getCheckedCount() {
            return checkedKeys.size();
        }
        
        List<AiQuestion> collectChecked(List<AiQuestion> source) {
            List<AiQuestion> result = new ArrayList<>();
            for (AiQuestion question : source) {
                if (question.getId() != null && checkedKeys.contains(checkKey(question))) {
                    result.add(question);
                }
            }
            return result;
        }
        
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
        public boolean isCellEditable(int row, int column) {
            return column == COL_QUESTION || column == COL_ANSWER || column == COL_TRAINING_NO;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            AiQuestion question = questions.get(row);
            return switch (column) {
                case COL_CHECK -> question.getId() != null ? isChecked(question) : null;
                case COL_ID -> question.getId();
                case COL_PROJECT -> projectDisplayText(question);
                case COL_QUESTION -> AiQuestionMangerDialog.nullToEmpty(question.getQuestion());
                case COL_CLASSIFY -> AiQuestionMangerDialog.nullToEmpty(question.getQuestionClassify());
                case COL_USER -> userDisplayText(question);
                case COL_TRAINING_PARAM -> AiQuestionMangerDialog.nullToEmpty(question.getTrainingParam());
                case COL_TRAINING_NO -> AiQuestionMangerDialog.nullToEmpty(question.getTrainingNo());
                case COL_ANSWER -> AiQuestionMangerDialog.nullToEmpty(question.getAnswer());
                case COL_ANSWER_ID -> question.getAnswerId();
                case COL_AUTO_TRAINING -> question.isAutoTrainingAllowed() ? "允许" : "不允许";
                case COL_TRAINING_MODEL -> question.getTrainingModel();
                case COL_TRAINING_STATUS -> question.getTrainingStatus();
                case COL_SUBMIT_TRAINING_TIME -> AiQuestionMangerDialog.formatEpochMillisTime(question.getSubmitTrainingTime());
                case COL_START_TRAINING_TIME -> AiQuestionMangerDialog.formatEpochMillisTime(question.getStartTrainingTime());
                case COL_LAST_TRAINING_TIME -> AiQuestionMangerDialog.formatEpochMillisTime(question.getLastTrainingTime());
                case COL_TRAINING_COST -> AiQuestionMangerDialog.trainingCostText(question);
                case COL_REMARK -> AiQuestionMangerDialog.nullToEmpty(question.getRemark());
                default -> "";
            };
        }
    }
    
    // ────────── 内部类：手动录入行 ──────────
    
    private class QuestionEntryRow {
        
        private final CustomTextField questionField;
        
        private final FilterComboBox<String> userIdField;
        
        private final List<String> selectedProjectCodes = new ArrayList<>();
        
        private final JButton projectBtn;
        
        private final JCheckBox projectSameAsAbove;
        
        private final FilterComboBox<String> classifyCombo;
        
        private final JComboBox<String> autoTrainingCombo;
        
        private final JTextArea trainingParamArea;
        
        private final JButton deleteRowBtn;
        
        private QuestionEntryRow(int rowIndex) {
            questionField = new CustomTextField("输入问题");
            questionField.setMinWidth(QUESTION_FIELD_MIN_WIDTH);
            
            userIdField = new FilterComboBox<>();
            if (rowIndex > 0) {
                userIdField.addItem(SAME_AS_ABOVE);
                userIdField.setSelectedItem(SAME_AS_ABOVE);
            }
            for (String userOption : dialog.userOptions) {
                userIdField.addItem(userOption);
            }
            
            projectSameAsAbove = rowIndex > 0 ? new JCheckBox("同上") : null;
            if (projectSameAsAbove != null) {
                projectSameAsAbove.setSelected(true);
                projectSameAsAbove.setFont(UiConstants.FONT_SANS_11);
            }
            projectBtn = ButtonFactory.createSecondary(
                    rowIndex > 0 ? SAME_AS_ABOVE : AiQuestionMangerDialog.projectButtonLabel(selectedProjectCodes));
            projectBtn.setToolTipText("点击选择所属项目（可多选）");
            if (projectSameAsAbove != null) {
                projectBtn.setEnabled(false);
            }
            projectBtn.addActionListener(e -> {
                List<String> result = dialog.showProjectMultiSelectDialog("选择所属项目", selectedProjectCodes);
                if (result != null) {
                    selectedProjectCodes.clear();
                    selectedProjectCodes.addAll(result);
                    projectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(selectedProjectCodes));
                    if (projectSameAsAbove != null && projectSameAsAbove.isSelected()) {
                        projectSameAsAbove.setSelected(false);
                    }
                }
            });
            if (projectSameAsAbove != null) {
                projectSameAsAbove.addActionListener(e -> {
                    boolean same = projectSameAsAbove.isSelected();
                    projectBtn.setEnabled(!same);
                    if (same) {
                        projectBtn.setText(SAME_AS_ABOVE);
                    } else {
                        selectedProjectCodes.clear();
                        projectBtn.setText(AiQuestionMangerDialog.projectButtonLabel(selectedProjectCodes));
                    }
                });
            }
            
            classifyCombo = new FilterComboBox<>();
            if (rowIndex > 0) {
                classifyCombo.addItem(SAME_AS_ABOVE);
            }
            for (String classify : dialog.classifySuggestions) {
                classifyCombo.addItem(classify);
            }
            if (rowIndex > 0) {
                classifyCombo.setSelectedItem(SAME_AS_ABOVE);
            }
            
            autoTrainingCombo = new JComboBox<>();
            if (rowIndex > 0) {
                autoTrainingCombo.addItem(SAME_AS_ABOVE);
            }
            autoTrainingCombo.addItem("允许");
            autoTrainingCombo.addItem("不允许");
            if (rowIndex > 0) {
                autoTrainingCombo.setSelectedItem(SAME_AS_ABOVE);
            } else {
                autoTrainingCombo.setSelectedItem("允许");
            }
            
            trainingParamArea = new JTextArea(2, 10);
            trainingParamArea.setLineWrap(true);
            trainingParamArea.setWrapStyleWord(true);
            trainingParamArea.setFont(UiConstants.FONT_SANS_11);
            
            deleteRowBtn = ButtonFactory.createLink("删除");
            deleteRowBtn.setToolTipText("删除本行");
            deleteRowBtn.addActionListener(e -> removeEntryRow(this));
        }
    }
}
