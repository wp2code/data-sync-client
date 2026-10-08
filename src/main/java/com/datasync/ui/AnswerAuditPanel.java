/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.ui;

import com.datasync.components.CustomTextField;
import com.datasync.core.AiQuestionApiClient;
import com.datasync.model.AiAnswer;
import com.datasync.model.AiEnvConfig;
import com.datasync.util.ExcelExportUtil;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 回复审计页签面板：问题回复列表（筛选、分页、CRUD、批量操作、导出）
 *
 * @author liuweiping
 * @date 2026-09-16
 */
public class AnswerAuditPanel {
    
    private static final Logger logger = LoggerFactory.getLogger(AnswerAuditPanel.class);
    
    // ── 常量 ──
    private static final String ALL_PROJECT = "全部项目";
    
    private static final String ALL_USER = "全部用户";
    
    private static final String ALLOW_MODIFY_ALL = "全部";
    
    private static final String ALLOW_MODIFY_YES = "允许修改";
    
    private static final String ALLOW_MODIFY_NO = "不允许修改";
    
    private static final String SOURCE_TRAINING_ALL = "全部";
    
    private static final String SOURCE_TRAINING_YES = "是";
    
    private static final String SOURCE_TRAINING_NO = "不是";
    
    private static final Comparator<AiAnswer> ANSWER_COMPARATOR = (a, b) -> {
        long idA = a.getId() != null ? a.getId() : 0L;
        long idB = b.getId() != null ? b.getId() : 0L;
        return Long.compare(idB, idA);
    };
    
    // ── 回复审计表格列索引 ──
    private static final int ACOL_CHECK = 0;
    
    private static final int ACOL_ID = 1;
    
    private static final int ACOL_QUERY = 2;
    
    private static final int ACOL_ANSWER = 3;
    
    private static final int ACOL_USER = 4;
    
    private static final int ACOL_PROJECT = 5;
    
    private static final int ACOL_MODIFY = 6;
    
    private static final int ACOL_SOURCE_TRAINING = 7;
    
    private static final int ACOL_SOURCE_ID = 8;
    
    private static final int ACOL_CREATE_TIME = 9;
    
    private static final int ACOL_UPDATE_TIME = 10;
    
    private static final int ACOL_ACTION = 11;
    
    private static final int ANSWER_ACTION_COLUMN_WIDTH = 190;
    
    // ── 字段 ──
    private final AiQuestionMangerDialog dialog;
    
    private CustomTextField answerQueryField;
    
    private CustomTextField answerTextField;
    
    private JComboBox<String> allowModifyFilterCombo;
    
    private JComboBox<String> sourceTrainingFilterCombo;
    
    private JComboBox<String> answerProjectFilterCombo;
    
    private JComboBox<String> answerUserFilterCombo;
    
    private boolean suppressAnswerFilterEvents = false;
    
    private JTable answerTable;
    
    private AnswerTableModel answerTableModel;
    
    private int answerHoverActionRow = -1;
    
    private int answerHoverActionZone = -1;
    
    private JComboBox<String> answerPageSizeCombo;
    
    private JButton answerFirstPageBtn;
    
    private JButton answerPrevPageBtn;
    
    private JButton answerNextPageBtn;
    
    private JButton answerLastPageBtn;
    
    private JLabel answerPageInfoLabel;
    
    private JLabel answerTotalLabel;
    
    private JLabel answerCheckedCountLabel;
    
    private int answerCurrentPage = 1;
    
    private int answerTotalPages = 1;
    
    private long answerTotalCount = 0;
    
    private int answerPageSize = AiQuestionMangerDialog.DEFAULT_PAGE_SIZE;
    
    private final List<AiAnswer> loadedAnswers = new ArrayList<>();
    
    private boolean answersLoaded = false;
    
    // ────────── 构造 ──────────
    
    AnswerAuditPanel(AiQuestionMangerDialog dialog) {
        this.dialog = dialog;
    }
    
    // ────────── 面板构建 ──────────
    
    JComponent buildAnswerAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("问题回复列表"));
        
        JPanel toolbar = new JPanel(new BorderLayout(0, 2));
        JPanel answerFilterRow1 = new JPanel(new BorderLayout());
        JPanel answerFilterLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JPanel answerFilterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        JPanel answerFilterRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        answerFilterRow2.setVisible(false);
        answerQueryField = new CustomTextField("问题关键字模糊查询");
        answerQueryField.setMinWidth(180);
        answerQueryField.setToolTipText("按问题内容模糊查询（输入后按回车或点击『查 询』）");
        answerQueryField.addActionListener(e -> triggerAnswerSearch());
        answerTextField = new CustomTextField("回复内容模糊查询");
        answerTextField.setMinWidth(180);
        answerTextField.setToolTipText("按回复内容模糊查询（输入后按回车或点击『查 询』）");
        answerTextField.addActionListener(e -> triggerAnswerSearch());
        answerFilterLeft.add(answerQueryField);
        answerFilterLeft.add(answerTextField);
        answerFilterLeft.add(new JLabel("允许修改："));
        allowModifyFilterCombo = new JComboBox<>(new String[] {ALLOW_MODIFY_ALL, ALLOW_MODIFY_YES, ALLOW_MODIFY_NO});
        allowModifyFilterCombo.setPreferredSize(new Dimension(112, 26));
        allowModifyFilterCombo.setToolTipText("按是否允许修改过滤回复列表");
        allowModifyFilterCombo.addActionListener(e -> {
            if (!suppressAnswerFilterEvents) {
                triggerAnswerSearch();
            }
        });
        answerFilterLeft.add(allowModifyFilterCombo);
        answerFilterLeft.add(new JLabel("来源训练："));
        sourceTrainingFilterCombo = new JComboBox<>(new String[] {SOURCE_TRAINING_ALL, SOURCE_TRAINING_YES, SOURCE_TRAINING_NO});
        sourceTrainingFilterCombo.setPreferredSize(new Dimension(90, 26));
        sourceTrainingFilterCombo.setToolTipText("按是否来源训练过滤回复列表");
        sourceTrainingFilterCombo.addActionListener(e -> {
            if (!suppressAnswerFilterEvents) {
                triggerAnswerSearch();
            }
        });
        answerFilterLeft.add(sourceTrainingFilterCombo);
        JButton answerSearchBtn = ButtonFactory.createPill("查询", UiConstants.COLOR_SUCCESS, UiConstants.COLOR_SUCCESS_LIGHT);
        answerSearchBtn.addActionListener(e -> triggerAnswerSearch());
        answerFilterLeft.add(answerSearchBtn);
        JButton answerResetBtn = ButtonFactory.createPill("重置", UiConstants.COLOR_NEUTRAL, UiConstants.COLOR_NEUTRAL_LIGHT);
        answerResetBtn.setToolTipText("清空问题 / 回复关键字并恢复允许修改 / 来源训练 / 项目 / 用户为全部，回到第一页重新查询");
        answerResetBtn.addActionListener(e -> resetAnswerFilters());
        answerFilterLeft.add(answerResetBtn);
        JLabel answerMoreConditionLink = new JLabel("<html><a href=\"#\">更多条件 &#9660;</a></html>");
        answerMoreConditionLink.setToolTipText("展开 / 收起项目、用户筛选条件");
        answerMoreConditionLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        final String answerExpandedText = "<html><a href=\"#\">收起条件 &#9650;</a></html>";
        final String answerCollapsedText = "<html><a href=\"#\">更多条件 &#9660;</a></html>";
        answerMoreConditionLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean show = !answerFilterRow2.isVisible();
                answerFilterRow2.setVisible(show);
                answerMoreConditionLink.setText(show ? answerExpandedText : answerCollapsedText);
                toolbar.revalidate();
                toolbar.repaint();
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                answerMoreConditionLink.setForeground(new Color(59, 72, 221));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                answerMoreConditionLink.setForeground(new Color(0x1A, 0x73, 0xE8));
            }
        });
        answerFilterLeft.add(answerMoreConditionLink);
        
        answerCheckedCountLabel = new JLabel("已选 0 条");
        answerCheckedCountLabel.setForeground(Color.GRAY);
        answerCheckedCountLabel.setFont(UiConstants.FONT_SANS_11);
        answerFilterRight.add(answerCheckedCountLabel);
        JButton answerBatchUpdateBtn = ButtonFactory.createPill("批量更新", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
        answerBatchUpdateBtn.setToolTipText("批量更新勾选回复的是否允许修改状态（点击表头复选框可全选当前页）");
        answerBatchUpdateBtn.addActionListener(e -> batchUpdateAnswers());
        answerFilterRight.add(answerBatchUpdateBtn);
        JButton answerBatchDeleteBtn = ButtonFactory.createPill("批量删除", UiConstants.COLOR_DANGER, UiConstants.COLOR_DANGER_LIGHT);
        answerBatchDeleteBtn.setToolTipText("批量删除勾选的问题回复，删除后不可恢复（点击表头复选框可全选当前页）");
        answerBatchDeleteBtn.addActionListener(e -> batchDeleteAnswers());
        answerFilterRight.add(answerBatchDeleteBtn);
        
        answerFilterRow1.add(answerFilterLeft, BorderLayout.WEST);
        answerFilterRow1.add(answerFilterRight, BorderLayout.EAST);
        toolbar.add(answerFilterRow1, BorderLayout.NORTH);
        answerFilterRow2.add(new JLabel("项目："));
        answerProjectFilterCombo = new JComboBox<>();
        answerProjectFilterCombo.addItem(ALL_PROJECT);
        answerProjectFilterCombo.setPreferredSize(new Dimension(160, 26));
        answerProjectFilterCombo.setToolTipText("按项目过滤回复列表，选项来自当前环境回复列表的项目名称");
        answerProjectFilterCombo.addActionListener(e -> {
            if (!suppressAnswerFilterEvents) {
                answerCurrentPage = 1;
                applyAnswerPaging();
            }
        });
        answerFilterRow2.add(answerProjectFilterCombo);
        answerFilterRow2.add(new JLabel("用户："));
        answerUserFilterCombo = new JComboBox<>();
        answerUserFilterCombo.addItem(ALL_USER);
        for (String userOption : dialog.userOptions) {
            answerUserFilterCombo.addItem(AiQuestionMangerDialog.formatUserOption(userOption));
        }
        answerUserFilterCombo.setPreferredSize(new Dimension(160, 26));
        answerUserFilterCombo.setToolTipText("按用户过滤回复列表，选项来自当前环境用户列表");
        answerUserFilterCombo.addActionListener(e -> {
            if (!suppressAnswerFilterEvents) {
                answerCurrentPage = 1;
                applyAnswerPaging();
            }
        });
        answerFilterRow2.add(answerUserFilterCombo);
        toolbar.add(answerFilterRow2, BorderLayout.SOUTH);
        panel.add(toolbar, BorderLayout.NORTH);
        
        // 回复表格
        answerTableModel = new AnswerTableModel();
        answerTable = new JTable(answerTableModel);
        answerTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        answerTable.setRowHeight(28);
        answerTable.getTableHeader().setReorderingAllowed(false);
        int[] columnWidths = {AiQuestionMangerDialog.CHECK_COLUMN_WIDTH, 50, 240, 240, 90, 100, 90, 90, 70, 130, 130, ANSWER_ACTION_COLUMN_WIDTH};
        for (int i = 0; i < columnWidths.length; i++) {
            answerTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        TableColumn answerCheckColumn = answerTable.getColumnModel().getColumn(ACOL_CHECK);
        answerCheckColumn.setMinWidth(AiQuestionMangerDialog.CHECK_COLUMN_WIDTH);
        answerCheckColumn.setMaxWidth(AiQuestionMangerDialog.CHECK_COLUMN_WIDTH);
        answerCheckColumn.setCellRenderer(new AiQuestionMangerDialog.CheckBoxCellRenderer());
        answerCheckColumn.setHeaderRenderer(new AiQuestionMangerDialog.HeaderCheckBoxRenderer(() -> answerTableModel.isCurrentPageAllChecked(),
                () -> answerTableModel.getRowCount()));
        TableColumn answerActionColumn = answerTable.getColumnModel().getColumn(ACOL_ACTION);
        answerActionColumn.setMinWidth(ANSWER_ACTION_COLUMN_WIDTH);
        answerActionColumn.setMaxWidth(ANSWER_ACTION_COLUMN_WIDTH);
        answerTable.getColumnModel().getColumn(ACOL_ID).setCellRenderer(dialog.centeredRenderer());
        answerTable.getColumnModel().getColumn(ACOL_PROJECT).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(16));
        answerTable.getColumnModel().getColumn(ACOL_QUERY).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(48));
        answerTable.getColumnModel().getColumn(ACOL_ANSWER).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(48));
        AiQuestionMangerDialog.SelectableTextEditor answerTextEditor = new AiQuestionMangerDialog.SelectableTextEditor();
        answerTable.getColumnModel().getColumn(ACOL_QUERY).setCellEditor(answerTextEditor);
        answerTable.getColumnModel().getColumn(ACOL_ANSWER).setCellEditor(answerTextEditor);
        answerTable.getColumnModel().getColumn(ACOL_USER).setCellRenderer(new AiQuestionMangerDialog.UserIdCellRenderer(dialog.userOptions));
        answerTable.getColumnModel().getColumn(ACOL_MODIFY).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(ALLOW_MODIFY_YES.equals(value.toString()) ? UiConstants.COLOR_SUCCESS : Color.GRAY);
            return label;
        });
        answerTable.getColumnModel().getColumn(ACOL_SOURCE_TRAINING).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(SOURCE_TRAINING_YES.equals(value.toString()) ? UiConstants.COLOR_SUCCESS : Color.GRAY);
            return label;
        });
        answerTable.getColumnModel().getColumn(ACOL_SOURCE_ID).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            String text = value != null ? value.toString() : "";
            JLabel label = new JLabel(text);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            if (!text.isEmpty()) {
                label.setForeground(isSelected ? table.getSelectionForeground() : UiConstants.COLOR_LINK);
                label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                label.setToolTipText("点击查看问题来源信息");
            } else {
                label.setForeground(isSelected ? table.getSelectionForeground() : Color.GRAY);
            }
            return label;
        });
        answerTable.getColumnModel().getColumn(ACOL_CREATE_TIME).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(20));
        answerTable.getColumnModel().getColumn(ACOL_UPDATE_TIME).setCellRenderer(new AiQuestionMangerDialog.TextCellRenderer(20));
        answerTable.getColumnModel().getColumn(ACOL_ACTION).setCellRenderer(
                new AiQuestionMangerDialog.ActionCellRenderer(() -> answerHoverActionRow, () -> answerHoverActionZone, "查看回复详情（全部字段）",
                        false, AiQuestionMangerDialog.ANSWER_ACTION_DELETE));
        answerTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleAnswerTableClick(e);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                updateAnswerActionHover(null);
            }
        });
        answerTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateAnswerActionHover(e.getPoint());
            }
        });
        answerTable.addMouseWheelListener(e -> updateAnswerActionHover(e.getPoint()));
        answerTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && answerTable.getTableHeader().columnAtPoint(e.getPoint()) == ACOL_CHECK) {
                    answerTableModel.toggleCheckCurrentPage();
                    updateAnswerCheckControls();
                }
            }
        });
        panel.add(new JScrollPane(answerTable), BorderLayout.CENTER);
        
        // 分页栏
        JPanel pagePanel = new JPanel(new BorderLayout(8, 2));
        JPanel pageButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        answerFirstPageBtn = ButtonFactory.createToolbar("首页");
        answerPrevPageBtn = ButtonFactory.createToolbar("上一页");
        answerNextPageBtn = ButtonFactory.createToolbar("下一页");
        answerLastPageBtn = ButtonFactory.createToolbar("末页");
        answerPageInfoLabel = new JLabel();
        answerTotalLabel = new JLabel();
        answerFirstPageBtn.addActionListener(e -> gotoAnswerPage(1));
        answerPrevPageBtn.addActionListener(e -> gotoAnswerPage(answerCurrentPage - 1));
        answerNextPageBtn.addActionListener(e -> gotoAnswerPage(answerCurrentPage + 1));
        answerLastPageBtn.addActionListener(e -> gotoAnswerPage(answerTotalPages));
        pageButtonsPanel.add(answerFirstPageBtn);
        pageButtonsPanel.add(answerPrevPageBtn);
        pageButtonsPanel.add(answerPageInfoLabel);
        pageButtonsPanel.add(answerNextPageBtn);
        pageButtonsPanel.add(answerLastPageBtn);
        pageButtonsPanel.add(Box.createHorizontalStrut(12));
        pageButtonsPanel.add(answerTotalLabel);
        pagePanel.add(pageButtonsPanel, BorderLayout.CENTER);
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        answerPageSizeCombo = new JComboBox<>(AiQuestionMangerDialog.DEFAULT_PAGE_SCOPE);
        answerPageSizeCombo.setEditable(true);
        answerPageSizeCombo.setPreferredSize(new Dimension(76, 26));
        answerPageSizeCombo.setToolTipText(
                "选择或输入每页条数（" + AiQuestionMangerDialog.MIN_PAGE_SIZE + " ~ " + AiQuestionMangerDialog.MAX_PAGE_SIZE + "），回车或失去焦点生效");
        answerPageSizeCombo.setSelectedItem(String.valueOf(AiQuestionMangerDialog.DEFAULT_PAGE_SIZE));
        answerPageSizeCombo.addActionListener(e -> applyAnswerPageSizeFromCombo());
        if (answerPageSizeCombo.getEditor().getEditorComponent() instanceof JTextField editorField) {
            editorField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    applyAnswerPageSizeFromCombo();
                }
            });
        }
        JButton exportAnswerBtn = ButtonFactory.createToolbar("导出");
        exportAnswerBtn.setToolTipText("将当前筛选后的全部回复导出为 Excel 文件");
        exportAnswerBtn.addActionListener(e -> exportAnswersToExcel());
        sizePanel.add(exportAnswerBtn);
        sizePanel.add(Box.createHorizontalStrut(8));
        sizePanel.add(new JLabel("每页："));
        sizePanel.add(answerPageSizeCombo);
        sizePanel.add(new JLabel("条"));
        pagePanel.add(sizePanel, BorderLayout.EAST);
        panel.add(pagePanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // ────────── 筛选与搜索 ──────────
    
    private void triggerAnswerSearch() {
        answerCurrentPage = 1;
        dialog.invalidateAnswers();
        dialog.refreshAnswerTable();
    }
    
    private void resetAnswerFilters() {
        suppressAnswerFilterEvents = true;
        try {
            answerQueryField.setText("");
            answerTextField.setText("");
            allowModifyFilterCombo.setSelectedIndex(0);
            sourceTrainingFilterCombo.setSelectedIndex(0);
            answerProjectFilterCombo.setSelectedItem(ALL_PROJECT);
            answerUserFilterCombo.setSelectedItem(ALL_USER);
        } finally {
            suppressAnswerFilterEvents = false;
        }
        triggerAnswerSearch();
    }
    
    void refreshUserOptionsInPanel() {
        if (answerUserFilterCombo != null) {
            Object previousAnswerUserFilter = answerUserFilterCombo.getSelectedItem();
            suppressAnswerFilterEvents = true;
            try {
                answerUserFilterCombo.removeAllItems();
                answerUserFilterCombo.addItem(ALL_USER);
                for (String userOption : dialog.userOptions) {
                    answerUserFilterCombo.addItem(AiQuestionMangerDialog.formatUserOption(userOption));
                }
                if (previousAnswerUserFilter != null) {
                    answerUserFilterCombo.setSelectedItem(previousAnswerUserFilter);
                }
            } finally {
                suppressAnswerFilterEvents = false;
            }
        }
    }
    
    // ────────── 列表加载与分页 ──────────
    
    void invalidateCache() {
        answersLoaded = false;
    }
    
    void refreshAnswerTable() {
        if (!answersLoaded) {
            loadAnswersFromApi();
            return;
        }
        applyAnswerPaging();
    }
    
    private void loadAnswersFromApi() {
        if (dialog.apiBusy) {
            dialog.pendingAnswerReload = true;
            return;
        }
        dialog.pendingAnswerReload = false;
        AiEnvConfig envConfig = dialog.envCombo.getSelectedItem() instanceof AiEnvConfig config ? config : null;
        if (envConfig == null) {
            answersLoaded = true;
            loadedAnswers.clear();
            refreshAnswerProjectFilterOptions();
            answerTableModel.setData(new ArrayList<>());
            answerTableModel.clearChecked();
            updateAnswerCheckControls();
            dialog.setStatus("请先在右上角选择环境", false);
            return;
        }
        String queryKeyword = answerQueryField.getText().trim();
        String answerKeyword = answerTextField.getText().trim();
        Integer allowModifyFilter = selectedAllowModifyFilter();
        Boolean sourceTrainingFilter = selectedSourceTrainingFilter();
        dialog.apiBusy = true;
        dialog.setSaveButtonsEnabled(false);
        dialog.setStatus("正在加载环境 [" + envConfig.getEnvName() + "] 的问题回复列表…", true);
        new Thread(() -> {
            List<AiAnswer> loaded = new ArrayList<>();
            String error = null;
            try {
                for (AiAnswer item : AiQuestionApiClient.getInstance()
                        .listAnswers(envConfig, queryKeyword, answerKeyword, allowModifyFilter, sourceTrainingFilter)) {
                    item.setEnvName(envConfig.getEnvName());
                    loaded.add(item);
                }
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("加载环境 [{}] 问题回复列表失败", envConfig.getEnvName(), ex);
                error = "环境 [" + envConfig.getEnvName() + "] 问题回复列表加载失败：" + ex.getMessage();
            }
            String loadError = error;
            SwingUtilities.invokeLater(() -> {
                dialog.apiBusy = false;
                dialog.setSaveButtonsEnabled(true);
                answersLoaded = true;
                loadedAnswers.clear();
                loadedAnswers.addAll(loaded);
                answerTableModel.retainChecked(loadedAnswers);
                refreshAnswerProjectFilterOptions();
                if (loadError != null) {
                    dialog.setStatus(loadError, false);
                } else {
                    dialog.setStatus("环境 [" + envConfig.getEnvName() + "] 问题回复列表加载完成，共 " + loaded.size() + " 条", true);
                }
                applyAnswerPaging();
                if (dialog.envCombo.getSelectedItem() instanceof AiEnvConfig current && !current.getEnvName().equals(envConfig.getEnvName())) {
                    answersLoaded = false;
                    refreshAnswerTable();
                }
                dialog.resumePendingReloads();
            });
        }, "ai-answer-load").start();
    }
    
    private void applyAnswerPaging() {
        String answerProjectFilter = selectedAnswerProjectFilter();
        String answerUserFilter = selectedAnswerUserFilter();
        List<AiAnswer> sorted = new ArrayList<>();
        for (AiAnswer item : loadedAnswers) {
            if (matchesAnswerProject(item, answerProjectFilter) && matchesAnswerUserFilter(item, answerUserFilter)) {
                sorted.add(item);
            }
        }
        sorted.sort(ANSWER_COMPARATOR);
        answerTotalCount = sorted.size();
        answerTotalPages = (int) Math.max(1, (answerTotalCount + answerPageSize - 1) / answerPageSize);
        if (answerCurrentPage > answerTotalPages) {
            answerCurrentPage = answerTotalPages;
        }
        if (answerCurrentPage < 1) {
            answerCurrentPage = 1;
        }
        int fromIndex = (answerCurrentPage - 1) * answerPageSize;
        int toIndex = Math.min(fromIndex + answerPageSize, sorted.size());
        answerTableModel.setData(fromIndex < toIndex ? new ArrayList<>(sorted.subList(fromIndex, toIndex)) : new ArrayList<>());
        updateAnswerPaginationControls();
        updateAnswerCheckControls();
    }
    
    private void gotoAnswerPage(int page) {
        if (page < 1) {
            page = 1;
        }
        if (page > answerTotalPages) {
            page = answerTotalPages;
        }
        if (page == answerCurrentPage) {
            return;
        }
        answerCurrentPage = page;
        refreshAnswerTable();
    }
    
    private void applyAnswerPageSizeFromCombo() {
        Object editorValue = answerPageSizeCombo.getEditor().getItem();
        String text = editorValue != null ? editorValue.toString().trim() : "";
        int size = -1;
        try {
            size = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
        }
        if (size < AiQuestionMangerDialog.MIN_PAGE_SIZE || size > AiQuestionMangerDialog.MAX_PAGE_SIZE) {
            answerPageSizeCombo.getEditor().setItem(String.valueOf(answerPageSize));
            dialog.setStatus("每页条数请输入 " + AiQuestionMangerDialog.MIN_PAGE_SIZE + " ~ " + AiQuestionMangerDialog.MAX_PAGE_SIZE + " 之间的整数",
                    false);
            return;
        }
        if (size == answerPageSize) {
            return;
        }
        answerPageSize = size;
        answerCurrentPage = 1;
        refreshAnswerTable();
    }
    
    private void updateAnswerPaginationControls() {
        answerPageInfoLabel.setText("第 " + answerCurrentPage + " / " + answerTotalPages + " 页");
        answerTotalLabel.setText("共 " + answerTotalCount + " 条");
        answerFirstPageBtn.setEnabled(answerCurrentPage > 1);
        answerPrevPageBtn.setEnabled(answerCurrentPage > 1);
        answerNextPageBtn.setEnabled(answerCurrentPage < answerTotalPages);
        answerLastPageBtn.setEnabled(answerCurrentPage < answerTotalPages);
    }
    
    private void updateAnswerCheckControls() {
        answerTable.getTableHeader().repaint();
        if (answerCheckedCountLabel != null) {
            answerCheckedCountLabel.setText("已选 " + answerTableModel.getCheckedCount() + " 条");
        }
    }
    
    // ────────── 筛选项 ──────────
    
    private Integer selectedAllowModifyFilter() {
        Object selected = allowModifyFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        if (ALLOW_MODIFY_YES.equals(text)) {
            return AiAnswer.MODIFY_ALLOWED;
        }
        if (ALLOW_MODIFY_NO.equals(text)) {
            return AiAnswer.MODIFY_FORBIDDEN;
        }
        return null;
    }
    
    private Boolean selectedSourceTrainingFilter() {
        Object selected = sourceTrainingFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        if (SOURCE_TRAINING_YES.equals(text)) {
            return Boolean.TRUE;
        }
        if (SOURCE_TRAINING_NO.equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }
    
    private List<String> extractAnswerProjects(List<AiAnswer> answers) {
        LinkedHashSet<String> projects = new LinkedHashSet<>();
        for (AiAnswer item : answers) {
            String projectName = item.getProjectName();
            if (projectName != null && !projectName.isBlank()) {
                projects.add(projectName.trim());
            }
        }
        return new ArrayList<>(projects);
    }
    
    private void refreshAnswerProjectFilterOptions() {
        List<String> projects = extractAnswerProjects(loadedAnswers);
        Object previous = answerProjectFilterCombo.getSelectedItem();
        suppressAnswerFilterEvents = true;
        try {
            answerProjectFilterCombo.removeAllItems();
            answerProjectFilterCombo.addItem(ALL_PROJECT);
            for (String project : projects) {
                answerProjectFilterCombo.addItem(project);
            }
            if (previous != null && projects.contains(previous.toString())) {
                answerProjectFilterCombo.setSelectedItem(previous);
            } else {
                answerProjectFilterCombo.setSelectedItem(ALL_PROJECT);
            }
        } finally {
            suppressAnswerFilterEvents = false;
        }
    }
    
    private String selectedAnswerProjectFilter() {
        if (answerProjectFilterCombo == null) {
            return null;
        }
        Object selected = answerProjectFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        return text == null || ALL_PROJECT.equals(text) ? null : text;
    }
    
    private boolean matchesAnswerProject(AiAnswer answer, String projectFilter) {
        if (projectFilter == null) {
            return true;
        }
        return projectFilter.equals(AiQuestionMangerDialog.nullToEmpty(answer.getProjectName()).trim());
    }
    
    private String selectedAnswerUserFilter() {
        if (answerUserFilterCombo == null) {
            return null;
        }
        Object selected = answerUserFilterCombo.getSelectedItem();
        String text = selected != null ? selected.toString() : null;
        if (text == null || ALL_USER.equals(text)) {
            return null;
        }
        return AiQuestionMangerDialog.extractUserIdFromDisplayText(text);
    }
    
    private boolean matchesAnswerUserFilter(AiAnswer answer, String userIdFilter) {
        if (userIdFilter == null) {
            return true;
        }
        return userIdFilter.equals(AiQuestionMangerDialog.nullToEmpty(answer.getUserId()).trim());
    }
    
    // ────────── 表格交互 ──────────
    
    private void handleAnswerTableClick(MouseEvent e) {
        int row = answerTable.rowAtPoint(e.getPoint());
        int column = answerTable.columnAtPoint(e.getPoint());
        if (row < 0 || column < 0 || row >= answerTableModel.getRowCount()) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                dialog.toggleFullscreenDialog();
            }
            return;
        }
        AiAnswer answer = answerTableModel.getAnswerAt(row);
        if (column == ACOL_CHECK) {
            if (e.getClickCount() == 1) {
                answerTableModel.toggleChecked(row);
                updateAnswerCheckControls();
            }
        } else if (column == ACOL_SOURCE_ID) {
            if (e.getClickCount() >= 1) {
                AiAnswer clicked = answerTableModel.getAnswerAt(row);
                if (clicked.getSourceId() != null || answer.getSourceId().isEmpty()) {
                    showSourceQuestionInfo(clicked);
                }
            }
        } else if (column == ACOL_ACTION) {
            Rectangle cellRect = answerTable.getCellRect(row, column, false);
            int zone = AiQuestionMangerDialog.actionZoneAt(e.getX(), cellRect, 3);
            if (zone == AiQuestionMangerDialog.ACTION_VIEW) {
                showAnswerDetail(answer);
            } else if (zone == AiQuestionMangerDialog.ACTION_EDIT) {
                editAnswer(answer);
            } else {
                deleteAnswer(answer);
            }
        } else if (e.getClickCount() == 2) {
            if (column != ACOL_QUERY && column != ACOL_ANSWER) {
                editAnswer(answer);
            }
        }
    }
    
    private void updateAnswerActionHover(Point point) {
        int row = -1;
        int zone = -1;
        boolean clickable = false;
        if (point != null) {
            int hitRow = answerTable.rowAtPoint(point);
            int hitColumn = answerTable.columnAtPoint(point);
            if (hitRow >= 0 && hitColumn == ACOL_ACTION) {
                row = hitRow;
                zone = AiQuestionMangerDialog.actionZoneAt(point.x, answerTable.getCellRect(hitRow, ACOL_ACTION, false), 3);
                clickable = true;
            } else if (hitRow >= 0 && hitColumn == ACOL_CHECK) {
                clickable = true;
            }
        }
        answerTable.setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (row == answerHoverActionRow && zone == answerHoverActionZone) {
            return;
        }
        int previousRow = answerHoverActionRow;
        answerHoverActionRow = row;
        answerHoverActionZone = zone;
        repaintAnswerActionCell(previousRow);
        repaintAnswerActionCell(row);
    }
    
    private void repaintAnswerActionCell(int row) {
        if (row >= 0 && row < answerTable.getRowCount()) {
            answerTable.repaint(answerTable.getCellRect(row, ACOL_ACTION, false));
        }
    }
    
    // ────────── 回复 CRUD ──────────
    
    private void showAnswerDetail(AiAnswer answer) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        dialog.addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(AiQuestionMangerDialog.nullToEmpty(answer.getEnvName())));
        dialog.addFormRow(form, gbc, 1, new JLabel("ID："), new JLabel(answer.getId() != null ? String.valueOf(answer.getId()) : ""));
        dialog.addFormRow(form, gbc, 2, new JLabel("所属项目："), new JLabel(AiQuestionMangerDialog.nullToEmpty(answer.getProjectName())));
        dialog.addFormRow(form, gbc, 3, new JLabel("问题："),
                AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(answer.getQuery()), 2));
        dialog.addFormRow(form, gbc, 4, new JLabel("回复内容："),
                AiQuestionMangerDialog.readonlyArea(AiQuestionMangerDialog.nullToEmpty(answer.getAnswer()), 15));
        dialog.addFormRow(form, gbc, 5, new JLabel("所属用户："), AiQuestionMangerDialog.readonlyField(
                AiQuestionMangerDialog.formatUserIdDisplay(AiQuestionMangerDialog.nullToEmpty(answer.getUserId()), dialog.userOptions)));
        dialog.addFormRow(form, gbc, 6, new JLabel("是否允许修改："), new JLabel(answer.isModifyAllowed() ? ALLOW_MODIFY_YES : ALLOW_MODIFY_NO));
        dialog.addFormRow(form, gbc, 7, new JLabel("是否来源训练："), new JLabel(answer.isFromTraining() ? SOURCE_TRAINING_YES : SOURCE_TRAINING_NO));
        dialog.addFormRow(form, gbc, 8, new JLabel("创建时间："), new JLabel(AiQuestionMangerDialog.nullToEmpty(answer.getCreateTime())));
        dialog.addFormRow(form, gbc, 9, new JLabel("更新时间："), new JLabel(AiQuestionMangerDialog.nullToEmpty(answer.getUpdateTime())));
        JOptionPane.showMessageDialog(dialog, form, "回复详情", JOptionPane.PLAIN_MESSAGE);
    }
    
    private void showSourceQuestionInfo(AiAnswer answer) {
        AiEnvConfig envConfig = dialog.findEnvByName(answer.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到回复所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(answer.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (answer.getSourceId() == null || answer.getSourceId().isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "该回复未关联问题来源 ID", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dialog.runApiTask("查询问题来源信息", () -> {
            LinkedHashMap<String, String> fields = AiQuestionApiClient.getInstance().getSourceInfo(envConfig, answer.getSourceId());
            return () -> {
                JPanel content = new JPanel();
                content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
                content.setBorder(new EmptyBorder(8, 8, 8, 8));
                JPanel topPanel = new JPanel(new GridBagLayout());
                topPanel.setBorder(BorderFactory.createTitledBorder("问题来源信息"));
                topPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(4, 6, 4, 6);
                gbc.anchor = GridBagConstraints.WEST;
                dialog.addFormRow(topPanel, gbc, 0, new JLabel("问题："), dialog.readonlyArea(fields.getOrDefault("question", ""), 1));
                dialog.addFormRow(topPanel, gbc, 1, new JLabel("训练参数："), dialog.readonlyArea(fields.getOrDefault("trainingParam", ""), 3));
                content.add(topPanel);
                content.add(Box.createRigidArea(new Dimension(0, 8)));
                JPanel comparePanel = new JPanel(new GridLayout(1, 2, 8, 0));
                comparePanel.setBorder(BorderFactory.createEmptyBorder());
                JPanel leftPanel = new JPanel(new BorderLayout());
                leftPanel.setBorder(BorderFactory.createTitledBorder("固定回复（问题训练）"));
                JTextArea sourceAnswerArea = new JTextArea(fields.getOrDefault("answer", ""));
                sourceAnswerArea.setEditable(false);
                sourceAnswerArea.setLineWrap(true);
                sourceAnswerArea.setWrapStyleWord(true);
                sourceAnswerArea.setCaretPosition(0);
                leftPanel.add(new JScrollPane(sourceAnswerArea), BorderLayout.CENTER);
                JPanel rightPanel = new JPanel(new BorderLayout());
                rightPanel.setBorder(BorderFactory.createTitledBorder("回复内容（回复审计）"));
                JTextArea currentAnswerArea = new JTextArea(AiQuestionMangerDialog.nullToEmpty(answer.getAnswer()));
                currentAnswerArea.setEditable(false);
                currentAnswerArea.setLineWrap(true);
                currentAnswerArea.setWrapStyleWord(true);
                currentAnswerArea.setCaretPosition(0);
                rightPanel.add(new JScrollPane(currentAnswerArea), BorderLayout.CENTER);
                comparePanel.add(leftPanel);
                comparePanel.add(rightPanel);
                comparePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
                content.add(comparePanel);
                JScrollPane scroll = new JScrollPane(content);
                scroll.setBorder(BorderFactory.createEmptyBorder());
                scroll.setPreferredSize(new Dimension(1000, 600));
                JOptionPane.showMessageDialog(dialog, scroll, "问题来源信息 - 来源ID: " + answer.getSourceId(), JOptionPane.PLAIN_MESSAGE);
            };
        });
    }
    
    private void editAnswer(AiAnswer answer) {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextArea queryArea = new JTextArea(AiQuestionMangerDialog.nullToEmpty(answer.getQuery()), 2, 30);
        queryArea.setLineWrap(true);
        queryArea.setWrapStyleWord(true);
        JTextArea replyArea = new JTextArea(AiQuestionMangerDialog.nullToEmpty(answer.getAnswer()), 15, 30);
        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        JLabel projectNameLabel = new JLabel(AiQuestionMangerDialog.nullToEmpty(answer.getProjectName()));
        JComboBox<String> allowModifyCombo = new JComboBox<>(new String[] {ALLOW_MODIFY_YES, ALLOW_MODIFY_NO});
        allowModifyCombo.setSelectedIndex(answer.isModifyAllowed() ? 0 : 1);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        dialog.addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(AiQuestionMangerDialog.nullToEmpty(answer.getEnvName())));
        dialog.addFormRow(form, gbc, 1, new JLabel("问题："), dialog.dialogScroll(queryArea, 2));
        dialog.addFormRow(form, gbc, 2, new JLabel("回复内容："), dialog.dialogScroll(replyArea, 15));
        dialog.addFormRow(form, gbc, 3, new JLabel("所属项目："), projectNameLabel);
        dialog.addFormRow(form, gbc, 4, new JLabel("是否允许修改："), allowModifyCombo);
        int option = JOptionPane.showConfirmDialog(dialog, form, "编辑回复", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        String newQuery = queryArea.getText().trim();
        if (newQuery.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "问题内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newReply = replyArea.getText().trim();
        if (newReply.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "回复内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int newAllowModify = allowModifyCombo.getSelectedIndex() == 0 ? AiAnswer.MODIFY_ALLOWED : AiAnswer.MODIFY_FORBIDDEN;
        AiEnvConfig envConfig = dialog.findEnvByName(answer.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到回复所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(answer.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (answer.getId() == null) {
            JOptionPane.showMessageDialog(dialog, "该回复缺少 ID，无法调用更新接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dialog.runApiTask("更新回复", () -> {
            String message = AiQuestionApiClient.getInstance().updateAnswer(envConfig, answer.getId(), newQuery, newReply, newAllowModify, null);
            return () -> {
                dialog.invalidateAnswers();
                refreshAnswerTable();
                dialog.setStatus("更新回复成功：ID " + answer.getId() + "（" + message + "）", true);
            };
        });
    }
    
    private void deleteAnswer(AiAnswer answer) {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = dialog.findEnvByName(answer.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(dialog, "未找到回复所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(answer.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (answer.getId() == null) {
            JOptionPane.showMessageDialog(dialog, "该回复缺少 ID，无法调用删除接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(dialog,
                "确定删除环境 [" + envConfig.getEnvName() + "] 的问题回复 [ID " + answer.getId() + "] 吗？\n删除后不可恢复，请谨慎操作。", "确认删除",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        dialog.runApiTask("删除回复", () -> {
            String message = AiQuestionApiClient.getInstance().deleteAnswer(envConfig, answer.getId());
            return () -> {
                dialog.invalidateAnswers();
                refreshAnswerTable();
                dialog.setStatus("已删除回复：ID " + answer.getId() + "（" + message + "）", true);
            };
        });
    }
    
    // ────────── 批量操作 ──────────
    
    private void batchUpdateAnswers() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiAnswer> checkedAnswers = answerTableModel.collectChecked(loadedAnswers);
        if (checkedAnswers.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先在列表中勾选要批量更新的回复（点击表头复选框可全选当前页）", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiAnswer answer : checkedAnswers) {
            envIdGroups.computeIfAbsent(answer.getEnvName(), key -> new ArrayList<>()).add(answer.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = dialog.findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(dialog, "未找到回复所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(entry.getKey()) + "]", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            targets.add(envConfig);
            selectedTotal += entry.getValue().size();
            summaryLines.add(" 环境 [" + envConfig.getEnvName() + "] " + entry.getValue().size() + " 条");
        }
        JComboBox<String> allowModifyCombo = new JComboBox<>(new String[] {ALLOW_MODIFY_YES, ALLOW_MODIFY_NO});
        allowModifyCombo.setToolTipText("批量更新后统一设置的是否允许修改状态");
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定批量更新勾选的 " + selectedTotal + " 条回复吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        for (String line : summaryLines) {
            JLabel envLabel = new JLabel(line);
            envLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            confirmPanel.add(envLabel);
        }
        JPanel modifyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        modifyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modifyPanel.add(new JLabel("是否允许修改："));
        modifyPanel.add(allowModifyCombo);
        confirmPanel.add(Box.createVerticalStrut(8));
        confirmPanel.add(modifyPanel);
        int confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认批量更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        int targetAllowModify = allowModifyCombo.getSelectedIndex() == 0 ? AiAnswer.MODIFY_ALLOWED : AiAnswer.MODIFY_FORBIDDEN;
        dialog.runApiTask("批量更新", () -> {
            List<String> successDetails = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            for (AiEnvConfig envConfig : targets) {
                List<Long> ids = envIdGroups.get(envConfig.getEnvName());
                try {
                    String message = AiQuestionApiClient.getInstance().batchUpdateAnswerModify(envConfig, ids, targetAllowModify);
                    successCount += ids.size();
                    successDetails.add("环境 [" + envConfig.getEnvName() + "] " + ids.size() + " 条：" + message);
                } catch (AiQuestionApiClient.AiApiException ex) {
                    logger.error("环境 [{}] 批量更新回复失败", envConfig.getEnvName(), ex);
                    errors.add("环境 [" + envConfig.getEnvName() + "]：" + ex.getMessage());
                }
            }
            int successTotal = successCount;
            return () -> {
                if (errors.isEmpty()) {
                    answerTableModel.clearChecked();
                }
                updateAnswerCheckControls();
                dialog.invalidateAnswers();
                refreshAnswerTable();
                if (errors.isEmpty()) {
                    dialog.setStatus("批量更新回复成功：共 " + successTotal + " 条（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    dialog.setStatus("批量更新回复部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(dialog,
                            "已成功更新 " + successTotal + " 条回复。\n\n以下环境更新失败：\n" + String.join("\n", errors), "批量更新结果",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    dialog.setStatus("批量更新回复失败：" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(dialog, "批量更新失败：\n" + String.join("\n", errors), "错误", JOptionPane.ERROR_MESSAGE);
                }
            };
        });
    }
    
    private void batchDeleteAnswers() {
        if (dialog.apiBusy) {
            JOptionPane.showMessageDialog(dialog, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiAnswer> checkedAnswers = answerTableModel.collectChecked(loadedAnswers);
        if (checkedAnswers.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "请先在列表中勾选要批量删除的回复（点击表头复选框可全选当前页）", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiAnswer answer : checkedAnswers) {
            envIdGroups.computeIfAbsent(answer.getEnvName(), key -> new ArrayList<>()).add(answer.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = dialog.findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(dialog, "未找到回复所属环境配置 [" + AiQuestionMangerDialog.nullToEmpty(entry.getKey()) + "]", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            targets.add(envConfig);
            selectedTotal += entry.getValue().size();
            summaryLines.add(" 环境 [" + envConfig.getEnvName() + "] " + entry.getValue().size() + " 条");
        }
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定批量删除勾选的 " + selectedTotal + " 条回复吗？");
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
        int confirm = JOptionPane.showConfirmDialog(dialog, confirmPanel, "确认批量删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        dialog.runApiTask("批量删除", () -> {
            List<String> successDetails = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int successCount = 0;
            for (AiEnvConfig envConfig : targets) {
                List<Long> ids = envIdGroups.get(envConfig.getEnvName());
                try {
                    String message = AiQuestionApiClient.getInstance().deleteAnswers(envConfig, ids);
                    successCount += ids.size();
                    successDetails.add("环境 [" + envConfig.getEnvName() + "] " + ids.size() + " 条：" + message);
                } catch (AiQuestionApiClient.AiApiException ex) {
                    logger.error("环境 [{}] 批量删除回复失败", envConfig.getEnvName(), ex);
                    errors.add("环境 [" + envConfig.getEnvName() + "]：" + ex.getMessage());
                }
            }
            int successTotal = successCount;
            return () -> {
                if (errors.isEmpty()) {
                    answerTableModel.clearChecked();
                }
                updateAnswerCheckControls();
                dialog.invalidateAnswers();
                refreshAnswerTable();
                if (errors.isEmpty()) {
                    dialog.setStatus("批量删除回复成功：共 " + successTotal + " 条（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    dialog.setStatus("批量删除回复部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(dialog,
                            "已成功删除 " + successTotal + " 条回复。\n\n以下环境删除失败：\n" + String.join("\n", errors), "批量删除结果",
                            JOptionPane.WARNING_MESSAGE);
                } else {
                    dialog.setStatus("批量删除回复失败：" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(dialog, "批量删除失败：\n" + String.join("\n", errors), "错误", JOptionPane.ERROR_MESSAGE);
                }
            };
        });
    }
    
    // ────────── 导出 ──────────
    
    private void exportAnswersToExcel() {
        if (loadedAnswers.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "当前没有可导出的回复数据", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String answerProjectFilter = selectedAnswerProjectFilter();
        String answerUserFilter = selectedAnswerUserFilter();
        List<AiAnswer> filtered = new ArrayList<>();
        for (AiAnswer item : loadedAnswers) {
            if (matchesAnswerProject(item, answerProjectFilter) && matchesAnswerUserFilter(item, answerUserFilter)) {
                filtered.add(item);
            }
        }
        if (filtered.isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "当前筛选条件下没有可导出的回复", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiAnswer> exportData;
        int checkedCount = answerTableModel.getCheckedCount();
        if (checkedCount > 0) {
            String[] options = {"仅导出勾选（" + checkedCount + " 条）", "导出全部筛选（" + filtered.size() + " 条）", "取消"};
            int choice = JOptionPane.showOptionDialog(dialog, "已勾选 " + checkedCount + " 条数据，请选择导出范围：", "导出范围",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.CANCEL_OPTION || choice < 0) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                exportData = answerTableModel.collectChecked(filtered);
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
        exportData.sort(ANSWER_COMPARATOR);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出问题回复列表");
        chooser.setSelectedFile(new File("问题回复列表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx"));
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
            ExcelExportUtil.exportAnswers(outputFile, exportData, userIdDisplay);
        } catch (Exception ex) {
            logger.error("导出问题回复列表失败", ex);
            dialog.setStatus("导出失败：" + ex.getMessage(), false);
            JOptionPane.showMessageDialog(dialog, "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dialog.setStatus("问题回复列表已导出：" + outputFile.getAbsolutePath() + "（共 " + exportData.size() + " 条）", true);
        int open = JOptionPane.showConfirmDialog(dialog, "导出成功！\n" + outputFile.getAbsolutePath() + "\n\n是否立即打开查看？", "导出完成",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (open == JOptionPane.YES_OPTION) {
            dialog.openOutputFile(outputFile);
        }
    }
    
    // ────────── 内部类：表格模型 ──────────
    
    private static class AnswerTableModel extends AbstractTableModel {
        
        private final String[] columns = {"选择", "ID", "问题", "回复内容", "所属用户", "所属项目", "是否允许修改", "是否来源训练", "问题来源ID",
                "创建时间", "更新时间", "操作"};
        
        private final List<AiAnswer> answers = new ArrayList<>();
        
        private final Set<String> checkedKeys = new LinkedHashSet<>();
        
        void setData(List<AiAnswer> list) {
            answers.clear();
            answers.addAll(list);
            fireTableDataChanged();
        }
        
        AiAnswer getAnswerAt(int row) {
            return answers.get(row);
        }
        
        private static String checkKey(AiAnswer answer) {
            return answer.getEnvName() + "#" + answer.getId();
        }
        
        boolean isChecked(AiAnswer answer) {
            return answer.getId() != null && checkedKeys.contains(checkKey(answer));
        }
        
        void toggleChecked(int row) {
            AiAnswer answer = answers.get(row);
            if (answer.getId() == null) {
                return;
            }
            String key = checkKey(answer);
            if (!checkedKeys.remove(key)) {
                checkedKeys.add(key);
            }
            fireTableCellUpdated(row, ACOL_CHECK);
        }
        
        boolean isCurrentPageAllChecked() {
            boolean hasCheckable = false;
            for (AiAnswer answer : answers) {
                if (answer.getId() == null) {
                    continue;
                }
                hasCheckable = true;
                if (!checkedKeys.contains(checkKey(answer))) {
                    return false;
                }
            }
            return hasCheckable;
        }
        
        void toggleCheckCurrentPage() {
            if (isCurrentPageAllChecked()) {
                for (AiAnswer answer : answers) {
                    if (answer.getId() != null) {
                        checkedKeys.remove(checkKey(answer));
                    }
                }
            } else {
                for (AiAnswer answer : answers) {
                    if (answer.getId() != null) {
                        checkedKeys.add(checkKey(answer));
                    }
                }
            }
            if (!answers.isEmpty()) {
                fireTableRowsUpdated(0, answers.size() - 1);
            }
        }
        
        int getCheckedCount() {
            return checkedKeys.size();
        }
        
        List<AiAnswer> collectChecked(List<AiAnswer> source) {
            List<AiAnswer> result = new ArrayList<>();
            for (AiAnswer answer : source) {
                if (answer.getId() != null && checkedKeys.contains(checkKey(answer))) {
                    result.add(answer);
                }
            }
            return result;
        }
        
        void retainChecked(Collection<AiAnswer> validAnswers) {
            Set<String> validKeys = new HashSet<>();
            for (AiAnswer answer : validAnswers) {
                if (answer.getId() != null) {
                    validKeys.add(checkKey(answer));
                }
            }
            checkedKeys.retainAll(validKeys);
        }
        
        void clearChecked() {
            checkedKeys.clear();
            if (!answers.isEmpty()) {
                fireTableRowsUpdated(0, answers.size() - 1);
            }
        }
        
        @Override
        public int getRowCount() {
            return answers.size();
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
            return column == ACOL_QUERY || column == ACOL_ANSWER;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            AiAnswer answer = answers.get(row);
            return switch (column) {
                case ACOL_CHECK -> answer.getId() != null ? isChecked(answer) : null;
                case ACOL_ID -> answer.getId();
                case ACOL_PROJECT -> AiQuestionMangerDialog.nullToEmpty(answer.getProjectName());
                case ACOL_QUERY -> AiQuestionMangerDialog.nullToEmpty(answer.getQuery());
                case ACOL_ANSWER -> AiQuestionMangerDialog.nullToEmpty(answer.getAnswer());
                case ACOL_USER -> AiQuestionMangerDialog.nullToEmpty(answer.getUserId());
                case ACOL_MODIFY -> answer.isModifyAllowed() ? ALLOW_MODIFY_YES : ALLOW_MODIFY_NO;
                case ACOL_SOURCE_TRAINING -> answer.isFromTraining() ? SOURCE_TRAINING_YES : SOURCE_TRAINING_NO;
                case ACOL_SOURCE_ID -> answer.getSourceId() != null ? String.valueOf(answer.getSourceId()) : "";
                case ACOL_CREATE_TIME -> AiQuestionMangerDialog.nullToEmpty(answer.getCreateTime());
                case ACOL_UPDATE_TIME -> AiQuestionMangerDialog.nullToEmpty(answer.getUpdateTime());
                default -> "";
            };
        }
    }
}
