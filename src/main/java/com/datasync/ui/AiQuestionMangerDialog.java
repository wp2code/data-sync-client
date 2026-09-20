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
import com.datasync.model.AiAnswer;
import com.datasync.model.AiEnvConfig;
import com.datasync.model.AiQuestion;
import com.datasync.util.ConfigUtil;
import com.datasync.util.ExcelQuestionUtil;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
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
 * AI 问题管理对话框（业务页签切换：问题训练 / 回复审计）
 * <p>
 * 问题数据通过外部接口存取（环境 host + 接口路径，见 {@link AiEnvConfig}）：<br> 批量保存、更新、删除、列表查询均调用远端服务，列表只加载当前所选环境，训练状态筛选随列表请求提交由服务端过滤，关键字 / 分类过滤与分页在本地完成。<br>
 * 页签一「问题训练」：上屏问题录入（Tab 切换「界面手动录入」与「批量粘贴录入」两种方式，手动录入支持 Excel 模板导入）+
 * 下屏问题列表（仅展示当前环境、模糊查询、分类与训练状态筛选、分页、复选框勾选与表头全选、回复ID超链接查询回复详情、行内查看详情/编辑/训练/删除、双击编辑、批量触发训练、批量更新用户信息 / 训练参数、批量删除）<br> 页签二「回复审计」：问题回复列表（问题 /
 * 回复内容模糊查询与允许修改、来源训练筛选由接口完成、本地分页、复选框勾选与表头全选、行内查看详情 / 编辑 / 删除、批量更新是否允许修改状态、批量删除）
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
     * 详情 / 编辑弹窗文本滚动区的统一首选宽度（避免 JOptionPane 弹窗过窄导致组件紧凑）
     */
    private static final int DIALOG_SCROLL_WIDTH = 700;
    
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
     * 问题列表训练状态筛选下拉的『全部状态』选项（选中时不过滤训练状态）
     */
    private static final String ALL_TRAINING_STATUS = "全部状态";
    
    /**
     * 训练状态筛选项文字（下标即训练状态值：-1-待训练；0-成功；1-失败；2-成功(同步回复)；3-训练中；4-超时）
     */
    private static final String[] TRAINING_STATUS_TEXTS = {"待训练", "成功", "失败", "成功(同步回复)", "训练中", "超时"};
    
    /**
     * 训练状态筛选下拉选项对应的训练状态值（顺序与 TRAINING_STATUS_TEXTS 一致：下标 0 为待训练 -1，其余为状态值本身）
     */
    private static final int[] TRAINING_STATUS_VALUES = {AiQuestion.TRAINING_STATUS_PENDING, 0, 1, 2, 3, 4};
    
    /**
     * 训练时间展示格式（接口返回毫秒时间戳，展示时按本地时区格式化）
     */
    private static final DateTimeFormatter TRAINING_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
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
    
    // ── 下屏表格列索引（表格不展示用户Session / 优先级，可在详情与编辑弹窗查看）──
    private static final int COL_CHECK = 0;
    
    private static final int COL_ID = 1;
    
    private static final int COL_QUESTION = 2;
    
    private static final int COL_CLASSIFY = 3;
    
    private static final int COL_USER = 4;
    
    private static final int COL_TRAINING_PARAM = 5;
    
    private static final int COL_ANSWER = 6;
    
    private static final int COL_ANSWER_ID = 7;
    
    private static final int COL_ENABLE = 8;
    
    private static final int COL_TRAINING_STATUS = 9;
    
    private static final int COL_START_TRAINING_TIME = 10;
    
    private static final int COL_LAST_TRAINING_TIME = 11;
    
    /**
     * 训练耗时列（最近完成训练时间 - 开始训练时间，单位秒）
     */
    private static final int COL_TRAINING_COST = 12;
    
    private static final int COL_REMARK = 13;
    
    private static final int COL_ACTION = 14;
    
    /**
     * 复选框列固定宽度
     */
    private static final int CHECK_COLUMN_WIDTH = 46;
    
    // ── 操作列动作区域（问题表单元格四等分：左查看详情、中编辑、中训练、右删除；回复表三等分：左查看详情、中编辑、右删除）──
    private static final int ACTION_VIEW = 0;
    
    private static final int ACTION_EDIT = 1;
    
    private static final int ACTION_TRAIN = 2;
    
    private static final int ACTION_DELETE = 3;
    
    /**
     * 回复审计操作列删除区域值（三等分无训练动作，删除为第 3 区）
     */
    private static final int ANSWER_ACTION_DELETE = 2;
    
    // ── 业务页签（问题训练 / 回复审计）──
    private static final int TAB_QUESTION_TRAINING = 0;
    
    private static final int TAB_ANSWER_AUDIT = 1;
    
    // ── 回复审计表格列索引 ──
    private static final int ACOL_CHECK = 0;
    
    private static final int ACOL_ID = 1;
    
    private static final int ACOL_QUERY = 2;
    
    private static final int ACOL_ANSWER = 3;
    
    private static final int ACOL_USER = 4;
    
    private static final int ACOL_MODIFY = 5;
    
    private static final int ACOL_SOURCE_TRAINING = 6;
    
    private static final int ACOL_CREATE_TIME = 7;
    
    private static final int ACOL_UPDATE_TIME = 8;
    
    private static final int ACOL_ACTION = 9;
    
    /**
     * 回复审计允许修改筛选下拉的『全部』选项（选中时不过滤该状态）
     */
    private static final String ALLOW_MODIFY_ALL = "全部";
    
    /**
     * 回复审计允许修改下拉选项：允许修改（筛选值 1）
     */
    private static final String ALLOW_MODIFY_YES = "允许修改";
    
    /**
     * 回复审计允许修改下拉选项：不允许修改（筛选值 0）
     */
    private static final String ALLOW_MODIFY_NO = "不允许修改";
    
    /**
     * 回复审计来源训练筛选下拉的『全部』选项（选中时不过滤该状态）
     */
    private static final String SOURCE_TRAINING_ALL = "全部";
    
    /**
     * 回复审计来源训练下拉选项：是（筛选值 true）
     */
    private static final String SOURCE_TRAINING_YES = "是";
    
    /**
     * 回复审计来源训练下拉选项：不是（筛选值 false）
     */
    private static final String SOURCE_TRAINING_NO = "不是";
    
    /**
     * 回复审计列表排序：ID 降序（新回复在前）
     */
    private static final Comparator<AiAnswer> ANSWER_COMPARATOR = (a, b) -> {
        long idA = a.getId() != null ? a.getId() : 0L;
        long idB = b.getId() != null ? b.getId() : 0L;
        return Long.compare(idB, idA);
    };
    
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
    
    /**
     * 训练状态筛选下拉（选项固定：全部状态 + 各训练状态，选中后本地过滤列表）
     */
    private JComboBox<String> statusFilterCombo;
    
    /**
     * 重置问题列表筛选期间抑制联动刷新（避免中间状态触发条件不完整的接口加载，由重置统一触发一次查询）
     */
    private boolean suppressQuestionFilterEvents = false;
    
    private JComboBox<String> pageSizeCombo;
    
    private JTable questionTable;
    
    private QuestionTableModel tableModel;
    
    /**
     * 操作列悬浮高亮所在行（-1 表示无）
     */
    private int hoverActionRow = -1;
    
    /**
     * 操作列悬浮高亮的动作区域（ACTION_VIEW / ACTION_EDIT / ACTION_TRAIN / ACTION_DELETE，-1 表示无）
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
     * 问题缓存对应的训练状态筛选值（null 表示『全部状态』；与当前选中值不一致时需重新拉取接口）
     */
    private Integer loadedStatusFilter;
    
    /**
     * 是否有接口操作进行中（防止并发重复提交）
     */
    private boolean apiBusy = false;
    
    /**
     * 问题列表加载被接口操作阻塞时的补偿标记（操作完成后自动重新加载）
     */
    private boolean pendingQuestionReload = false;
    
    /**
     * 回复审计列表加载被接口操作阻塞时的补偿标记（操作完成后自动重新加载）
     */
    private boolean pendingAnswerReload = false;
    
    private JButton manualSaveBtn;
    
    private JButton pasteSaveBtn;
    
    /**
     * 手动录入页 Excel 导入按钮（接口操作进行中禁用）
     */
    private JButton importExcelBtn;
    
    // ── 业务页签 ──
    private JTabbedPane businessTabs;
    
    // ── 回复审计（tab2）：筛选条件 ──
    private CustomTextField answerQueryField;
    
    private CustomTextField answerTextField;
    
    /**
     * 允许修改筛选下拉（全部 / 允许修改 / 不允许修改，选中后重新查询列表）
     */
    private JComboBox<String> allowModifyFilterCombo;
    
    /**
     * 来源训练筛选下拉（全部 / 是 / 不是，选中后重新查询列表）
     */
    private JComboBox<String> sourceTrainingFilterCombo;
    
    /**
     * 重置回复审计筛选期间抑制联动刷新（避免中间状态触发条件不完整的接口加载，由重置统一触发一次查询）
     */
    private boolean suppressAnswerFilterEvents = false;
    
    // ── 回复审计（tab2）：列表 ──
    private JTable answerTable;
    
    private AnswerTableModel answerTableModel;
    
    /**
     * 回复审计操作列悬浮高亮所在行（-1 表示无）
     */
    private int answerHoverActionRow = -1;
    
    /**
     * 回复审计操作列悬浮高亮的动作区域（ACTION_VIEW / ACTION_EDIT / ANSWER_ACTION_DELETE，-1 表示无）
     */
    private int answerHoverActionZone = -1;
    
    private JComboBox<String> answerPageSizeCombo;
    
    private JButton answerFirstPageBtn;
    
    private JButton answerPrevPageBtn;
    
    private JButton answerNextPageBtn;
    
    private JButton answerLastPageBtn;
    
    private JLabel answerPageInfoLabel;
    
    private JLabel answerTotalLabel;
    
    /**
     * 回复审计工具栏已勾选数量提示（跨页统计）
     */
    private JLabel answerCheckedCountLabel;
    
    private int answerCurrentPage = 1;
    
    private int answerTotalPages = 1;
    
    private long answerTotalCount = 0;
    
    private int answerPageSize = DEFAULT_PAGE_SIZE;
    
    /**
     * 回复审计列表缓存（按当前筛选条件从接口拉取，本地分页展示）
     */
    private final List<AiAnswer> loadedAnswers = new ArrayList<>();
    
    /**
     * 回复审计缓存是否已加载（false 表示尚未加载或已失效，刷新时需重新拉取）
     */
    private boolean answersLoaded = false;
    
    // ── 状态栏 ──
    private JLabel statusLabel;
    
    /**
     * 主窗口引用，用于弹出环境管理对话框
     */
    private final Frame ownerFrame;
    
    public AiQuestionMangerDialog(Frame owner) {
        super("AIQUESTION", owner, "AI 问题管理", true, 1280, 780);
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
        
        // ── 顶栏：环境选择（问题训练与回复审计共用同一环境，切换后当前页签数据重新加载）──
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
        // 切换当前环境后，问题列表重新加载该环境的数据
        envCombo.addActionListener(e -> onEnvSelectionChanged());
        JButton manageEnvBtn = ButtonFactory.createToolbar("管理环境");
        manageEnvBtn.addActionListener(e -> openEnvManageDialog());
        ChildLayoutPanel envPanel = new ChildLayoutPanel(new Insets(0, 4, 0, 4), ChildLayoutPanel.LayoutType.RIGHT);
        envPanel.add(new JLabel("环境："));
        envPanel.add(envCombo);
        envPanel.add(manageEnvBtn);
        headerPanel.add(envPanel, BorderLayout.EAST);
        
        // ── 业务页签：问题训练（原有录入 + 问题列表全部功能）/ 回复审计（问题回复查询、更新、删除）──
        businessTabs = new JTabbedPane();
        businessTabs.addTab("问题训练", buildQuestionTrainingPanel());
        businessTabs.addTab("回复审计", buildAnswerAuditPanel());
        businessTabs.addChangeListener(e -> onBusinessTabChanged());
        
        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(businessTabs, BorderLayout.CENTER);
        add(topPanel, BorderLayout.CENTER);
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        statusLabel.setToolTipText("双击空白区域可全屏 / 退出全屏（F11 / Esc）");
        add(statusLabel, BorderLayout.SOUTH);
        
        // 全部组件就绪后统一绑定：双击对话框内空白 / 展示区域切换全屏
        bindDoubleClickFullscreen(this);
    }
    
    /**
     * 问题训练页（tab1）：上屏问题录入（手动 / 批量粘贴）+ 下屏问题列表，保留原有全部功能
     */
    private JComponent buildQuestionTrainingPanel() {
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
    
    /**
     * 业务页签切换：刷新选中页签数据（回复审计数据懒加载，首次切换或数据失效时重新拉取）
     */
    private void onBusinessTabChanged() {
        refreshActiveBusinessTab();
    }
    
    /**
     * 重新加载当前选中业务页签的数据（问题训练 / 回复审计，页签切换与环境切换共用）
     */
    private void refreshActiveBusinessTab() {
        if (businessTabs != null && businessTabs.getSelectedIndex() == TAB_ANSWER_AUDIT) {
            refreshAnswerTable();
        } else {
            refreshQuestionTable();
        }
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
                "提示：用户ID选填，问题为空的行自动忽略；支持 Excel 导入（先『下载模板』）");
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
     * 手动录入网格表头：问题、用户ID、分类、开启训练、优先级别、训练参数、固定回复、操作
     */
    private void addManualGridHeader() {
        String[] headers = {"问题", "用户ID", "分类", "开启训练", "优先级别", "训练参数", "固定回复", "操作"};
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
                    resumePendingReloads();
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
                    resumePendingReloads();
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
        invalidateAnswers();
        refreshActiveBusinessTab();
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
     * 当前环境切换后的处理：持久化保存下拉选中的环境（下次打开界面默认选中），重置分页并重新加载当前页签数据
     */
    private void onEnvSelectionChanged() {
        if (suppressEnvEvents) {
            return;
        }
        persistSelectedEnv();
        currentPage = 1;
        answerCurrentPage = 1;
        invalidateLoadedData();
        invalidateAnswers();
        refreshActiveBusinessTab();
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
        
        // 工具栏：左侧筛选（模糊查询 + 分类 + 训练状态 + 查询 / 重置按钮），右侧批量操作（已选计数 + 触发训练 / 批量更新 / 批量删除）
        JPanel toolbar = new JPanel(new BorderLayout(6, 2));
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        searchField = new CustomTextField("输入关键字模糊查询");
        searchField.setMinWidth(200);
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
        filterPanel.add(searchField);
        filterPanel.add(new JLabel("分类："));
        // 分类筛选：选项由已加载问题列表的分类动态生成，选中后本地过滤列表
        classifyFilterCombo = new JComboBox<>();
        classifyFilterCombo.addItem(ALL_CLASSIFY);
        classifyFilterCombo.setPreferredSize(new Dimension(140, 26));
        classifyFilterCombo.setToolTipText("按分类过滤问题列表，选项来自当前环境问题列表的分类");
        classifyFilterCombo.addActionListener(e -> onClassifyFilterChanged());
        filterPanel.add(classifyFilterCombo);
        filterPanel.add(new JLabel("训练状态："));
        // 训练状态筛选：选项固定（全部状态 + 各训练状态），选中后触发接口重新拉取（服务端筛选）
        statusFilterCombo = new JComboBox<>();
        statusFilterCombo.addItem(ALL_TRAINING_STATUS);
        for (String statusText : TRAINING_STATUS_TEXTS) {
            statusFilterCombo.addItem(statusText);
        }
        statusFilterCombo.setPreferredSize(new Dimension(126, 26));
        statusFilterCombo.setToolTipText("按训练状态过滤问题列表（-1-待训练；0-成功；1-失败；2-成功(同步回复)；3-训练中；4-超时）");
        statusFilterCombo.addActionListener(e -> onTrainingStatusFilterChanged());
        filterPanel.add(statusFilterCombo);
        // 筛选动作按钮：胶囊描边样式（常态轻填充 + 彩色描边，悬浮 / 按下填充反白，与操作列按钮同一视觉语言）
        JButton refreshBtn = ButtonFactory.createPill("查询", UiConstants.COLOR_SUCCESS, UiConstants.COLOR_SUCCESS_LIGHT);
        refreshBtn.addActionListener(e -> {
            currentPage = 1;
            invalidateLoadedData();
            refreshQuestionTable();
        });
        filterPanel.add(refreshBtn);
        // 重置：清空关键字并恢复分类 / 训练状态筛选为全部，回到第一页重新查询
        JButton resetBtn = ButtonFactory.createPill("重置", UiConstants.COLOR_NEUTRAL, UiConstants.COLOR_NEUTRAL_LIGHT);
        resetBtn.setToolTipText("清空关键字并恢复分类 / 训练状态为全部，回到第一页重新查询");
        resetBtn.addActionListener(e -> resetQuestionFilters());
        filterPanel.add(resetBtn);
        
        // 右侧批量操作组：已选计数 + 批量训练 / 批量更新 / 批量删除（右对齐）
        checkedCountLabel = new JLabel("已选 0 条");
        checkedCountLabel.setForeground(Color.GRAY);
        checkedCountLabel.setFont(UiConstants.FONT_SANS_11);
        actionPanel.add(checkedCountLabel);
        JButton trainBtn = ButtonFactory.createPill("批量训练", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
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
        int[] columnWidths = {CHECK_COLUMN_WIDTH, 50, 280, 90, 120, 150, 150, 90, 80, 90, 140, 140, 100, 110, 260};
        for (int i = 0; i < columnWidths.length; i++) {
            questionTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        // 复选框列：固定宽度，表头渲染全选复选框
        TableColumn checkColumn = questionTable.getColumnModel().getColumn(COL_CHECK);
        checkColumn.setMinWidth(CHECK_COLUMN_WIDTH);
        checkColumn.setMaxWidth(CHECK_COLUMN_WIDTH);
        checkColumn.setCellRenderer(new CheckBoxCellRenderer());
        checkColumn.setHeaderRenderer(new HeaderCheckBoxRenderer(() -> tableModel.isCurrentPageAllChecked(), () -> tableModel.getRowCount()));
        questionTable.getColumnModel().getColumn(COL_ID).setCellRenderer(centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_QUESTION).setCellRenderer(new TextCellRenderer(48));
        questionTable.getColumnModel().getColumn(COL_CLASSIFY).setCellRenderer(new TextCellRenderer(12));
        questionTable.getColumnModel().getColumn(COL_USER).setCellRenderer(new TextCellRenderer(20));
        questionTable.getColumnModel().getColumn(COL_TRAINING_PARAM).setCellRenderer(new TextCellRenderer(24));
        questionTable.getColumnModel().getColumn(COL_ANSWER).setCellRenderer(new TextCellRenderer(24));
        // 问题 / 固定回复列：双击进入只读选择复制模式（文字自动全选，可拖选部分文字复制，不修改数据）
        SelectableTextEditor questionTextEditor = new SelectableTextEditor();
        questionTable.getColumnModel().getColumn(COL_QUESTION).setCellEditor(questionTextEditor);
        questionTable.getColumnModel().getColumn(COL_ANSWER).setCellEditor(questionTextEditor);
        questionTable.getColumnModel().getColumn(COL_ANSWER_ID).setCellRenderer(new AnswerIdCellRenderer());
        questionTable.getColumnModel().getColumn(COL_ENABLE).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground("开启".equals(value.toString()) ? UiConstants.COLOR_SUCCESS : Color.GRAY);
            return label;
        });
        questionTable.getColumnModel().getColumn(COL_TRAINING_STATUS).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            Integer status = (Integer) value;
            JLabel label = new JLabel(trainingStatusText(status));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(trainingStatusColor(status));
            return label;
        });
        questionTable.getColumnModel().getColumn(COL_START_TRAINING_TIME).setCellRenderer(centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_LAST_TRAINING_TIME).setCellRenderer(centeredRenderer());
        // 训练耗时列：数值居中展示（列头已注明单位秒）
        questionTable.getColumnModel().getColumn(COL_TRAINING_COST).setCellRenderer(centeredRenderer());
        questionTable.getColumnModel().getColumn(COL_REMARK).setCellRenderer(new TextCellRenderer(20));
        questionTable.getColumnModel().getColumn(COL_ACTION)
                .setCellRenderer(new ActionCellRenderer(() -> hoverActionRow, () -> hoverActionZone, "查看问题详情（全部字段）", true, ACTION_DELETE));
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
        
        // 分页栏：中间分页按钮，右侧每页条数（每页条数与分页控件同处一栏）
        JPanel pagePanel = new JPanel(new BorderLayout(8, 2));
        JPanel pageButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
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
        pageButtonsPanel.add(firstPageBtn);
        pageButtonsPanel.add(prevPageBtn);
        pageButtonsPanel.add(pageInfoLabel);
        pageButtonsPanel.add(nextPageBtn);
        pageButtonsPanel.add(lastPageBtn);
        pageButtonsPanel.add(Box.createHorizontalStrut(12));
        pageButtonsPanel.add(totalLabel);
        pagePanel.add(pageButtonsPanel, BorderLayout.CENTER);
        // 可编辑下拉：既可选快捷值，也可手动输入每页条数（回车或失去焦点生效）
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
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
        sizePanel.add(new JLabel("每页："));
        sizePanel.add(pageSizeCombo);
        sizePanel.add(new JLabel("条"));
        pagePanel.add(sizePanel, BorderLayout.EAST);
        panel.add(pagePanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 表格点击处理：复选框列单击切换勾选；回复ID列单击查询回复详情（有值时）；操作列四等分区域查看详情 / 编辑 / 训练 / 删除；问题 / 固定回复列双击进入选择复制模式；其它列双击编辑；空白区域双击切换全屏
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
        } else if (column == COL_ANSWER_ID) {
            // 回复ID列：单击有值行时查询回复详情（无值行无交互）
            if (e.getClickCount() == 1 && question.getAnswerId() != null) {
                viewAnswerInfo(question);
            }
        } else if (column == COL_ACTION) {
            Rectangle cellRect = questionTable.getCellRect(row, column, false);
            int zone = actionZoneAt(e.getX(), cellRect, 4);
            if (zone == ACTION_VIEW) {
                showQuestionDetail(question);
            } else if (zone == ACTION_EDIT) {
                editQuestion(question);
            } else if (zone == ACTION_TRAIN) {
                trainSingleQuestion(question);
            } else {
                deleteQuestion(question);
            }
        } else if (e.getClickCount() == 2) {
            // 问题 / 固定回复列双击由只读选择复制编辑器接管（isCellEditable + 列编辑器启动），不打开编辑弹窗
            if (column != COL_QUESTION && column != COL_ANSWER) {
                editQuestion(question);
            }
        }
    }
    
    /**
     * 判断横坐标落在操作单元格的哪个动作区域（问题表四等分：查看详情 / 编辑 / 训练 / 删除；回复表三等分：查看详情 / 编辑 / 删除）
     *
     * @param zoneCount 动作区域数（问题表 4，回复表 3）
     */
    private static int actionZoneAt(int x, Rectangle cellRect, int zoneCount) {
        double zoneWidth = cellRect.width / (double) zoneCount;
        int zone = (int) ((x - cellRect.x) / zoneWidth);
        return Math.max(0, Math.min(zone, zoneCount - 1));
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
                zone = actionZoneAt(point.x, questionTable.getCellRect(hitRow, COL_ACTION, false), 4);
                clickable = true;
            } else if (hitRow >= 0 && hitColumn == COL_CHECK) {
                clickable = true;
            } else if (hitRow >= 0 && hitRow < tableModel.getRowCount() && hitColumn == COL_ANSWER_ID
                    && tableModel.getQuestionAt(hitRow).getAnswerId() != null) {
                // 回复ID有值时显示手型光标提示可点击查询
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
     * 刷新问题列表：缓存未加载、已失效或训练状态筛选变化时从接口重新拉取，否则直接本地过滤分页
     */
    private void refreshQuestionTable() {
        if (!questionsLoaded || !Objects.equals(loadedStatusFilter, selectedTrainingStatusFilter())) {
            loadQuestionsFromApi();
            return;
        }
        applyLocalFilterAndPaging();
    }
    
    /**
     * 从外部接口拉取当前所选环境的问题列表（切换环境或训练状态筛选后重新加载；训练状态筛选随请求提交由服务端过滤）
     */
    private void loadQuestionsFromApi() {
        if (apiBusy) {
            // 有接口操作进行中：标记待加载，操作完成后自动补偿加载
            pendingQuestionReload = true;
            return;
        }
        pendingQuestionReload = false;
        AiEnvConfig envConfig = envCombo.getSelectedItem() instanceof AiEnvConfig config ? config : null;
        if (envConfig == null) {
            questionsLoaded = true;
            loadedStatusFilter = selectedTrainingStatusFilter();
            allQuestions.clear();
            refreshClassifyFilterOptions();
            tableModel.setData(new ArrayList<>());
            tableModel.clearChecked();
            updateCheckControls();
            setStatus("请先在右上角选择环境", false);
            return;
        }
        Integer statusFilter = selectedTrainingStatusFilter();
        apiBusy = true;
        setSaveButtonsEnabled(false);
        setStatus("正在加载环境 [" + envConfig.getEnvName() + "] 的问题列表…", true);
        new Thread(() -> {
            List<AiQuestion> loaded = new ArrayList<>();
            String error = null;
            try {
                for (AiQuestion item : AiQuestionApiClient.getInstance().listQuestions(envConfig, statusFilter)) {
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
                loadedStatusFilter = statusFilter;
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
                // 加载期间用户切换了环境或训练状态筛选：立即重新加载
                boolean envChanged =
                        envCombo.getSelectedItem() instanceof AiEnvConfig current && !current.getEnvName().equals(envConfig.getEnvName());
                if (envChanged || !Objects.equals(selectedTrainingStatusFilter(), statusFilter)) {
                    questionsLoaded = false;
                    refreshQuestionTable();
                }
                resumePendingReloads();
            });
        }, "ai-question-load").start();
    }
    
    /**
     * 在缓存数据上执行本地关键字过滤、排序与分页展示
     */
    private void applyLocalFilterAndPaging() {
        String keyword = searchField.getText().trim().toLowerCase();
        String classifyFilter = selectedClassifyFilter();
        Integer statusFilter = selectedTrainingStatusFilter();
        List<AiQuestion> filtered = new ArrayList<>();
        for (AiQuestion item : allQuestions) {
            if (matchesKeyword(item, keyword) && matchesClassify(item, classifyFilter) && matchesTrainingStatus(item, statusFilter)) {
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
     * 搜索关键字变化：回到第一页并刷新列表（缓存未失效时为本地过滤，无需重新拉取接口）
     */
    private void onSearchFieldChanged() {
        if (suppressQuestionFilterEvents) {
            return;
        }
        currentPage = 1;
        refreshQuestionTable();
    }
    
    /**
     * 训练状态筛选下拉选中项变化：回到第一页并触发列表重新加载（训练状态为服务端筛选条件，缓存与当前筛选不一致时重新拉取接口）
     */
    private void onTrainingStatusFilterChanged() {
        if (suppressQuestionFilterEvents) {
            return;
        }
        currentPage = 1;
        refreshQuestionTable();
    }
    
    /**
     * 重置问题列表筛选条件：清空关键字并恢复分类 / 训练状态为全部，回到第一页重新从接口查询
     */
    private void resetQuestionFilters() {
        // 抑制各筛选组件的联动刷新，避免中间状态触发条件不完整的接口加载，由下方统一触发一次查询
        suppressQuestionFilterEvents = true;
        suppressClassifyEvents = true;
        try {
            searchField.setText("");
            statusFilterCombo.setSelectedIndex(0);
            classifyFilterCombo.setSelectedItem(ALL_CLASSIFY);
        } finally {
            suppressClassifyEvents = false;
            suppressQuestionFilterEvents = false;
        }
        currentPage = 1;
        invalidateLoadedData();
        refreshQuestionTable();
    }
    
    /**
     * 当前选中的训练状态筛选值（null 表示『全部状态』，不参与过滤；下拉索引减 1 后经 TRAINING_STATUS_VALUES 映射，含待训练 -1）
     */
    private Integer selectedTrainingStatusFilter() {
        if (statusFilterCombo == null) {
            return null;
        }
        int index = statusFilterCombo.getSelectedIndex();
        return index <= 0 ? null : TRAINING_STATUS_VALUES[index - 1];
    }
    
    /**
     * 训练状态筛选匹配（服务端筛选为主，此处作为兼容兜底；状态值为空的问题仅在『全部状态』下可见）
     */
    private boolean matchesTrainingStatus(AiQuestion question, Integer statusFilter) {
        if (statusFilter == null) {
            return true;
        }
        return statusFilter.equals(question.getTrainingStatus());
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
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(nullToEmpty(question.getEnvName())));
        addFormRow(form, gbc, 1, new JLabel("ID："), new JLabel(question.getId() != null ? String.valueOf(question.getId()) : ""));
        addFormRow(form, gbc, 2, new JLabel("问题："), readonlyArea(nullToEmpty(question.getQuestion()), 2));
        addFormRow(form, gbc, 3, new JLabel("用户ID："), readonlyField(nullToEmpty(question.getUserId())));
        addFormRow(form, gbc, 4, new JLabel("用户Session："), readonlyArea(nullToEmpty(question.getUserSession()), 2));
        addFormRow(form, gbc, 5, new JLabel("分类："), readonlyField(nullToEmpty(question.getQuestionClassify())));
        addFormRow(form, gbc, 6, new JLabel("训练参数："), readonlyArea(nullToEmpty(question.getTrainingParam()), 4));
        addFormRow(form, gbc, 7, new JLabel("固定回复："), readonlyArea(nullToEmpty(question.getAnswer()), 10));
        addFormRow(form, gbc, 8, new JLabel("回复ID："), new JLabel(question.getAnswerId() != null ? String.valueOf(question.getAnswerId()) : ""));
        addFormRow(form, gbc, 9, new JLabel("开启训练："), new JLabel(question.isTrainingEnabled() ? "开启" : "不开启"));
        addFormRow(form, gbc, 10, new JLabel("训练状态："), new JLabel(trainingStatusText(question.getTrainingStatus())));
        addFormRow(form, gbc, 11, new JLabel("开始训练时间："), new JLabel(formatEpochMillis(question.getStartTrainingTime())));
        addFormRow(form, gbc, 12, new JLabel("最近完成训练时间："), new JLabel(formatEpochMillis(question.getLastTrainingTime())));
        String trainingCost = trainingCostText(question);
        addFormRow(form, gbc, 13, new JLabel("训练耗时："), new JLabel(trainingCost.isEmpty() ? "" : trainingCost + " 秒"));
        addFormRow(form, gbc, 14, new JLabel("优先级别："), new JLabel(String.valueOf(question.getPriority() != null ? question.getPriority() : 0)));
        addFormRow(form, gbc, 15, new JLabel("备注："), readonlyArea(nullToEmpty(question.getRemark()), 3));
        JOptionPane.showMessageDialog(this, form, "问题详情", JOptionPane.PLAIN_MESSAGE);
    }
    
    /**
     * 查询并展示问题回复详情（点击列表回复ID超链接触发，按回复详情查询接口返回字段只读展示）
     */
    private void viewAnswerInfo(AiQuestion question) {
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
        Long answerId = question.getAnswerId();
        String questionText = question.getQuestion();
        String fixedAnswer = question.getAnswer();
        runApiTask("查询回复详情", () -> {
            LinkedHashMap<String, String> fields = AiQuestionApiClient.getInstance().getAnswerInfo(envConfig, answerId);
            return () -> {
                setStatus("回复详情查询成功（回复ID " + answerId + "）", true);
                showAnswerInfoDetail(answerId, questionText, fixedAnswer, fields);
            };
        });
    }
    
    /**
     * 只读展示回复详情（回复ID、回复问题、回复内容与固定回复左右对比、是否允许修改）
     */
    private void showAnswerInfoDetail(Long answerId, String questionText, String fixedAnswer, LinkedHashMap<String, String> fields) {
        // 回复问题优先取接口返回的 query / question 字段，未返回时回退列表中的问题内容
        String query = firstNonBlank(fields.get("query"), fields.get("question"), questionText);
        // 左右分栏对比：左接口返回的回复内容，右问题配置的固定回复
        JScrollPane answerScroll = readonlyArea(nullToEmpty(fields.get("answer")), 15);
        answerScroll.setBorder(BorderFactory.createTitledBorder("回复内容（回复审计）"));
        JScrollPane fixedScroll = readonlyArea(nullToEmpty(fixedAnswer), 15);
        fixedScroll.setBorder(BorderFactory.createTitledBorder("固定回复（问题训练）"));
        JSplitPane comparePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, answerScroll, fixedScroll);
        comparePane.setResizeWeight(0.5);
        comparePane.setDividerLocation(0.5);
        comparePane.setPreferredSize(new Dimension(700, 250));
        
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("回复ID："), new JLabel(String.valueOf(answerId)));
        addFormRow(form, gbc, 1, new JLabel("回复问题："), new JLabel(nullToEmpty(query)));
        //        addFormRow(form, gbc, 1, new JLabel("回复问题："), readonlyArea(nullToEmpty(query), 3));
        // 对比区跨两列通栏展示（临时 gridwidth=2，放置后恢复，避免影响后续行）
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(comparePane, gbc);
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        addFormRow(form, gbc, 3, new JLabel("是否允许修改："), new JLabel(answerModifyText(fields.get("allowModify"))));
        JOptionPane.showMessageDialog(this, form, "回复详情", JOptionPane.PLAIN_MESSAGE);
    }
    
    /**
     * 是否允许修改取值转展示文本（1/true 允许修改、0/false 不允许修改，其它值原样展示，空值展示空）
     */
    private static String answerModifyText(String allowModify) {
        if (allowModify == null || allowModify.isBlank()) {
            return "";
        }
        return switch (allowModify.trim()) {
            case "1", "true" -> "允许修改";
            case "0", "false" -> "不允许修改";
            default -> allowModify;
        };
    }
    
    /**
     * 返回第一个非空白值（全部为空白时返回 null）
     */
    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
    
    /**
     * 编辑问题（保存时调用外部更新接口）
     */
    private void editQuestion(AiQuestion question) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextArea questionArea = new JTextArea(nullToEmpty(question.getQuestion()), 2, 30);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        JTextField userIdField = new JTextField(nullToEmpty(question.getUserId()), 20);
        JTextField userSessionField = new JTextField(nullToEmpty(question.getUserSession()), 20);
        JTextField classifyField = new JTextField(nullToEmpty(question.getQuestionClassify()), 20);
        JTextArea trainingParamArea = new JTextArea(nullToEmpty(question.getTrainingParam()), 5, 30);
        trainingParamArea.setLineWrap(true);
        trainingParamArea.setWrapStyleWord(true);
        JTextArea answerArea = new JTextArea(nullToEmpty(question.getAnswer()), 10, 30);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        JComboBox<String> enableCombo = new JComboBox<>(new String[] {"开启", "不开启"});
        enableCombo.setSelectedIndex(question.isTrainingEnabled() ? 0 : 1);
        JTextField priorityField = new JTextField(String.valueOf(question.getPriority() != null ? question.getPriority() : 0), 8);
        
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(nullToEmpty(question.getEnvName())));
        addFormRow(form, gbc, 1, new JLabel("问题："), dialogScroll(questionArea, 2));
        addFormRow(form, gbc, 2, new JLabel("用户ID："), userIdField);
        addFormRow(form, gbc, 3, new JLabel("用户Session："), userSessionField);
        addFormRow(form, gbc, 4, new JLabel("分类："), classifyField);
        addFormRow(form, gbc, 5, new JLabel("训练参数："), dialogScroll(trainingParamArea, 5));
        addFormRow(form, gbc, 6, new JLabel("固定回复："), dialogScroll(answerArea, 10));
        addFormRow(form, gbc, 7, new JLabel("开启训练："), enableCombo);
        
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
     * 触发单个问题的训练（操作列『训练』按钮）
     * <p>
     * 与批量触发训练一致：触发前校验问题必须有归属用户ID，缺少时提示补充后再触发；<br> 确认弹窗中完整展示问题全文（不截断）并选择智能体类型（safety / system / ops / auto，默认 auto）随请求 agentType 字段提交；<br> 调用训练接口提交单条 ID，成功后刷新列表。
     */
    private void trainSingleQuestion(AiQuestion question) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (question.getId() == null) {
            JOptionPane.showMessageDialog(this, "该问题缺少 ID，无法调用训练接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 训练要求问题必须有归属用户ID：缺少时提示先补充再触发
        if (question.getUserId() == null || question.getUserId().isBlank()) {
            JOptionPane.showMessageDialog(this, "该问题缺少用户ID，不允许触发训练，请先通过「编辑」或「批量更新」补充用户ID。", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = findEnvByName(question.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(this, "未找到问题所属环境配置 [" + nullToEmpty(question.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 确认弹窗：问题全文（自动换行可滚动，不截断）+ 环境信息 + 智能体类型下拉
        JComboBox<String> agentTypeCombo = new JComboBox<>(AGENT_TYPES);
        agentTypeCombo.setSelectedItem(DEFAULT_AGENT_TYPE);
        agentTypeCombo.setToolTipText("训练请求 agentType 字段值");
        JPanel confirmPanel = new JPanel();
        confirmPanel.setLayout(new BoxLayout(confirmPanel, BoxLayout.Y_AXIS));
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel titleLabel = new JLabel("确定对该问题触发训练吗？");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(titleLabel);
        // 问题内容完整展示（自动换行 + 滚动查看全文，不截断为省略号）
        JLabel envLabel = new JLabel("环境 [" + envConfig.getEnvName() + "]　问题ID " + question.getId());
        envLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(envLabel);
        JLabel questionCaptionLabel = new JLabel("问题："+nullToEmpty(question.getQuestion()));
        questionCaptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmPanel.add(questionCaptionLabel);
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
        Long questionId = question.getId();
        String questionText = question.getQuestion();
        runApiTask("触发训练", () -> {
            String message = AiQuestionApiClient.getInstance().triggerTraining(envConfig, List.of(questionId), agentType);
            return () -> {
                invalidateLoadedData();
                refreshQuestionTable();
                setStatus("触发训练成功：" + abbreviate(questionText, 30) + "（" + message + "）", true);
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
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
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
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
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
        confirmPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
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
    
    // ────────── 回复审计（tab2） ──────────
    
    /**
     * 回复审计面板：顶部筛选（问题 / 回复内容模糊查询、允许修改筛选）与批量操作（批量更新状态 / 批量删除），中部回复列表，底部分页
     */
    private JComponent buildAnswerAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("问题回复列表"));
        
        // 工具栏：左侧筛选（问题 / 回复内容模糊查询 + 允许修改筛选 + 查询 / 重置按钮），右侧批量操作（已选计数 + 批量更新 / 批量删除）
        JPanel toolbar = new JPanel(new BorderLayout(6, 2));
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        // 问题 / 回复内容关键字：输入后按回车或点击『查 询』触发接口查询（筛选参数随请求提交给接口）
        answerQueryField = new CustomTextField("问题关键字模糊查询");
        answerQueryField.setMinWidth(180);
        answerQueryField.setToolTipText("按问题内容模糊查询（输入后按回车或点击『查 询』）");
        answerQueryField.addActionListener(e -> triggerAnswerSearch());
        answerTextField = new CustomTextField("回复内容模糊查询");
        answerTextField.setMinWidth(180);
        answerTextField.setToolTipText("按回复内容模糊查询（输入后按回车或点击『查 询』）");
        answerTextField.addActionListener(e -> triggerAnswerSearch());
        filterPanel.add(answerQueryField);
        filterPanel.add(answerTextField);
        filterPanel.add(new JLabel("允许修改："));
        allowModifyFilterCombo = new JComboBox<>(new String[] {ALLOW_MODIFY_ALL, ALLOW_MODIFY_YES, ALLOW_MODIFY_NO});
        allowModifyFilterCombo.setPreferredSize(new Dimension(112, 26));
        allowModifyFilterCombo.setToolTipText("按是否允许修改过滤回复列表");
        allowModifyFilterCombo.addActionListener(e -> {
            if (!suppressAnswerFilterEvents) {
                triggerAnswerSearch();
            }
        });
        filterPanel.add(allowModifyFilterCombo);
        filterPanel.add(new JLabel("来源训练："));
        sourceTrainingFilterCombo = new JComboBox<>(new String[] {SOURCE_TRAINING_ALL, SOURCE_TRAINING_YES, SOURCE_TRAINING_NO});
        sourceTrainingFilterCombo.setPreferredSize(new Dimension(90, 26));
        sourceTrainingFilterCombo.setToolTipText("按是否来源训练过滤回复列表");
        sourceTrainingFilterCombo.addActionListener(e -> {
            if (!suppressAnswerFilterEvents) {
                triggerAnswerSearch();
            }
        });
        filterPanel.add(sourceTrainingFilterCombo);
        JButton answerSearchBtn = ButtonFactory.createPill("查询", UiConstants.COLOR_SUCCESS, UiConstants.COLOR_SUCCESS_LIGHT);
        answerSearchBtn.addActionListener(e -> triggerAnswerSearch());
        filterPanel.add(answerSearchBtn);
        // 重置：清空问题 / 回复关键字并恢复允许修改 / 来源训练筛选为全部，回到第一页重新查询
        JButton answerResetBtn = ButtonFactory.createPill("重置", UiConstants.COLOR_NEUTRAL, UiConstants.COLOR_NEUTRAL_LIGHT);
        answerResetBtn.setToolTipText("清空问题 / 回复关键字并恢复允许修改 / 来源训练为全部，回到第一页重新查询");
        answerResetBtn.addActionListener(e -> resetAnswerFilters());
        filterPanel.add(answerResetBtn);
        
        // 右侧批量操作组：已选计数 + 批量更新 / 批量删除（右对齐）
        answerCheckedCountLabel = new JLabel("已选 0 条");
        answerCheckedCountLabel.setForeground(Color.GRAY);
        answerCheckedCountLabel.setFont(UiConstants.FONT_SANS_11);
        actionPanel.add(answerCheckedCountLabel);
        JButton answerBatchUpdateBtn = ButtonFactory.createPill("批量更新", UiConstants.COLOR_PRIMARY, UiConstants.COLOR_PRIMARY_LIGHT);
        answerBatchUpdateBtn.setToolTipText("批量更新勾选回复的是否允许修改状态（点击表头复选框可全选当前页）");
        answerBatchUpdateBtn.addActionListener(e -> batchUpdateAnswers());
        actionPanel.add(answerBatchUpdateBtn);
        JButton answerBatchDeleteBtn = ButtonFactory.createPill("批量删除", UiConstants.COLOR_DANGER, UiConstants.COLOR_DANGER_LIGHT);
        answerBatchDeleteBtn.setToolTipText("批量删除勾选的问题回复，删除后不可恢复（点击表头复选框可全选当前页）");
        answerBatchDeleteBtn.addActionListener(e -> batchDeleteAnswers());
        actionPanel.add(answerBatchDeleteBtn);
        toolbar.add(filterPanel, BorderLayout.CENTER);
        toolbar.add(actionPanel, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);
        
        // 回复表格
        answerTableModel = new AnswerTableModel();
        answerTable = new JTable(answerTableModel);
        answerTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        answerTable.setRowHeight(28);
        answerTable.getTableHeader().setReorderingAllowed(false);
        int[] columnWidths = {CHECK_COLUMN_WIDTH, 50, 240, 240, 90, 90, 90, 130, 130, 190};
        for (int i = 0; i < columnWidths.length; i++) {
            answerTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        // 复选框列：固定宽度，表头渲染全选复选框
        TableColumn answerCheckColumn = answerTable.getColumnModel().getColumn(ACOL_CHECK);
        answerCheckColumn.setMinWidth(CHECK_COLUMN_WIDTH);
        answerCheckColumn.setMaxWidth(CHECK_COLUMN_WIDTH);
        answerCheckColumn.setCellRenderer(new CheckBoxCellRenderer());
        answerCheckColumn.setHeaderRenderer(
                new HeaderCheckBoxRenderer(() -> answerTableModel.isCurrentPageAllChecked(), () -> answerTableModel.getRowCount()));
        answerTable.getColumnModel().getColumn(ACOL_ID).setCellRenderer(centeredRenderer());
        answerTable.getColumnModel().getColumn(ACOL_QUERY).setCellRenderer(new TextCellRenderer(48));
        answerTable.getColumnModel().getColumn(ACOL_ANSWER).setCellRenderer(new TextCellRenderer(48));
        // 问题 / 回复内容列：双击进入只读选择复制模式（文字自动全选，可拖选部分文字复制，不修改数据）
        SelectableTextEditor answerTextEditor = new SelectableTextEditor();
        answerTable.getColumnModel().getColumn(ACOL_QUERY).setCellEditor(answerTextEditor);
        answerTable.getColumnModel().getColumn(ACOL_ANSWER).setCellEditor(answerTextEditor);
        answerTable.getColumnModel().getColumn(ACOL_USER).setCellRenderer(new TextCellRenderer(20));
        answerTable.getColumnModel().getColumn(ACOL_MODIFY).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(ALLOW_MODIFY_YES.equals(value.toString()) ? UiConstants.COLOR_SUCCESS : Color.GRAY);
            return label;
        });
        // 来源训练列：是（训练生成）绿色、不是灰色，居中展示
        answerTable.getColumnModel().getColumn(ACOL_SOURCE_TRAINING).setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JLabel label = new JLabel(value.toString());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(SOURCE_TRAINING_YES.equals(value.toString()) ? UiConstants.COLOR_SUCCESS : Color.GRAY);
            return label;
        });
        answerTable.getColumnModel().getColumn(ACOL_CREATE_TIME).setCellRenderer(new TextCellRenderer(20));
        answerTable.getColumnModel().getColumn(ACOL_UPDATE_TIME).setCellRenderer(new TextCellRenderer(20));
        answerTable.getColumnModel().getColumn(ACOL_ACTION)
                .setCellRenderer(new ActionCellRenderer(() -> answerHoverActionRow, () -> answerHoverActionZone, "查看回复详情（全部字段）", false, ANSWER_ACTION_DELETE));
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
        // 操作列悬浮高亮与手型光标反馈
        answerTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateAnswerActionHover(e.getPoint());
            }
        });
        answerTable.addMouseWheelListener(e -> updateAnswerActionHover(e.getPoint()));
        // 表头复选框：单击切换当前页全选 / 取消全选
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
        
        // 分页栏：中间分页按钮，右侧每页条数（每页条数与分页控件同处一栏）
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
        // 可编辑下拉：既可选快捷值，也可手动输入每页条数（回车或失去焦点生效）
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        answerPageSizeCombo = new JComboBox<>(new String[] {"100", "10", "20", "50"});
        answerPageSizeCombo.setEditable(true);
        answerPageSizeCombo.setPreferredSize(new Dimension(76, 26));
        answerPageSizeCombo.setToolTipText("选择或输入每页条数（" + MIN_PAGE_SIZE + " ~ " + MAX_PAGE_SIZE + "），回车或失去焦点生效");
        answerPageSizeCombo.setSelectedItem(String.valueOf(DEFAULT_PAGE_SIZE));
        answerPageSizeCombo.addActionListener(e -> applyAnswerPageSizeFromCombo());
        if (answerPageSizeCombo.getEditor().getEditorComponent() instanceof JTextField editorField) {
            editorField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    applyAnswerPageSizeFromCombo();
                }
            });
        }
        sizePanel.add(new JLabel("每页："));
        sizePanel.add(answerPageSizeCombo);
        sizePanel.add(new JLabel("条"));
        pagePanel.add(sizePanel, BorderLayout.EAST);
        panel.add(pagePanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 回复审计筛选条件变化 / 点击『查 询』：回到第一页并按最新条件重新拉取接口数据
     */
    private void triggerAnswerSearch() {
        answerCurrentPage = 1;
        invalidateAnswers();
        refreshAnswerTable();
    }
    
    /**
     * 重置回复审计筛选条件：清空问题 / 回复关键字并恢复允许修改 / 来源训练为全部，回到第一页重新查询
     */
    private void resetAnswerFilters() {
        // 抑制允许修改 / 来源训练筛选的联动刷新，避免中间状态触发条件不完整的接口加载，由下方统一触发一次查询
        suppressAnswerFilterEvents = true;
        try {
            answerQueryField.setText("");
            answerTextField.setText("");
            allowModifyFilterCombo.setSelectedIndex(0);
            sourceTrainingFilterCombo.setSelectedIndex(0);
        } finally {
            suppressAnswerFilterEvents = false;
        }
        triggerAnswerSearch();
    }
    
    /**
     * 刷新回复审计列表：缓存未加载或已失效时按当前筛选条件从接口重新拉取，否则直接本地分页
     */
    private void refreshAnswerTable() {
        if (!answersLoaded) {
            loadAnswersFromApi();
            return;
        }
        applyAnswerPaging();
    }
    
    /**
     * 从外部接口拉取当前环境的问题回复列表（问题 / 回复内容模糊查询与允许修改筛选随请求提交，结果本地分页）
     */
    private void loadAnswersFromApi() {
        if (apiBusy) {
            // 有接口操作进行中：标记待加载，操作完成后自动补偿加载
            pendingAnswerReload = true;
            return;
        }
        pendingAnswerReload = false;
        AiEnvConfig envConfig = envCombo.getSelectedItem() instanceof AiEnvConfig config ? config : null;
        if (envConfig == null) {
            answersLoaded = true;
            loadedAnswers.clear();
            answerTableModel.setData(new ArrayList<>());
            answerTableModel.clearChecked();
            updateAnswerCheckControls();
            setStatus("请先在右上角选择环境", false);
            return;
        }
        String queryKeyword = answerQueryField.getText().trim();
        String answerKeyword = answerTextField.getText().trim();
        Integer allowModifyFilter = selectedAllowModifyFilter();
        Boolean sourceTrainingFilter = selectedSourceTrainingFilter();
        apiBusy = true;
        setSaveButtonsEnabled(false);
        setStatus("正在加载环境 [" + envConfig.getEnvName() + "] 的问题回复列表…", true);
        new Thread(() -> {
            List<AiAnswer> loaded = new ArrayList<>();
            String error = null;
            try {
                for (AiAnswer item : AiQuestionApiClient.getInstance().listAnswers(                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          envConfig, queryKeyword, answerKeyword, allowModifyFilter, sourceTrainingFilter)) {
                    item.setEnvName(envConfig.getEnvName());
                    loaded.add(item);
                }
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("加载环境 [{}] 问题回复列表失败", envConfig.getEnvName(), ex);
                error = "环境 [" + envConfig.getEnvName() + "] 问题回复列表加载失败：" + ex.getMessage();
            }
            String loadError = error;
            SwingUtilities.invokeLater(() -> {
                apiBusy = false;
                setSaveButtonsEnabled(true);
                answersLoaded = true;
                loadedAnswers.clear();
                loadedAnswers.addAll(loaded);
                // 清理已勾选但已不存在的回复（保留跨页勾选）
                answerTableModel.retainChecked(loadedAnswers);
                if (loadError != null) {
                    setStatus(loadError, false);
                } else {
                    setStatus("环境 [" + envConfig.getEnvName() + "] 问题回复列表加载完成，共 " + loaded.size() + " 条", true);
                }
                applyAnswerPaging();
                // 加载期间用户切换了环境：立即重新加载切换后的环境
                if (envCombo.getSelectedItem() instanceof AiEnvConfig current && !current.getEnvName().equals(envConfig.getEnvName())) {
                    answersLoaded = false;
                    refreshAnswerTable();
                }
                resumePendingReloads();
            });
        }, "ai-answer-load").start();
    }
    
    /**
     * 在缓存的回复数据上执行排序与分页展示（筛选已由接口完成）
     */
    private void applyAnswerPaging() {
        List<AiAnswer> sorted = new ArrayList<>(loadedAnswers);
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
    
    /**
     * 数据变更后失效回复审计缓存，下次刷新时重新从接口拉取
     */
    private void invalidateAnswers() {
        answersLoaded = false;
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
    
    /**
     * 提交回复审计每页条数输入：合法则回到第一页并刷新列表，非法时恢复上一个有效值并提示
     */
    private void applyAnswerPageSizeFromCombo() {
        Object editorValue = answerPageSizeCombo.getEditor().getItem();
        String text = editorValue != null ? editorValue.toString().trim() : "";
        int size = -1;
        try {
            size = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            // 非数字输入按非法值处理，走下方回退分支
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            // 非法输入：恢复上一个有效值并提示
            answerPageSizeCombo.getEditor().setItem(String.valueOf(answerPageSize));
            setStatus("每页条数请输入 " + MIN_PAGE_SIZE + " ~ " + MAX_PAGE_SIZE + " 之间的整数", false);
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
    
    /**
     * 刷新回复审计勾选相关界面：表头全选复选框状态与已勾选数量提示
     */
    private void updateAnswerCheckControls() {
        answerTable.getTableHeader().repaint();
        if (answerCheckedCountLabel != null) {
            answerCheckedCountLabel.setText("已选 " + answerTableModel.getCheckedCount() + " 条");
        }
    }
    
    /**
     * 回复审计当前选中的允许修改筛选值（null 表示『全部』，不参与筛选）
     */
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
    
    /**
     * 回复审计当前选中的来源训练筛选值（null 表示『全部』，不参与筛选）
     */
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
    
    /**
     * 回复审计表格点击处理：复选框列单击切换勾选；操作列三等分区域查看详情 / 编辑 / 删除；问题 / 回复内容列双击进入选择复制模式；其它列双击编辑；空白区域双击切换全屏
     */
    private void handleAnswerTableClick(MouseEvent e) {
        int row = answerTable.rowAtPoint(e.getPoint());
        int column = answerTable.columnAtPoint(e.getPoint());
        if (row < 0 || column < 0 || row >= answerTableModel.getRowCount()) {
            // 双击表格空白区域（数据行以外的区域）切换全屏
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                toggleFullscreen();
            }
            return;
        }
        AiAnswer answer = answerTableModel.getAnswerAt(row);
        if (column == ACOL_CHECK) {
            if (e.getClickCount() == 1) {
                answerTableModel.toggleChecked(row);
                updateAnswerCheckControls();
            }
        } else if (column == ACOL_ACTION) {
            Rectangle cellRect = answerTable.getCellRect(row, column, false);
            int zone = actionZoneAt(e.getX(), cellRect, 3);
            if (zone == ACTION_VIEW) {
                showAnswerDetail(answer);
            } else if (zone == ACTION_EDIT) {
                editAnswer(answer);
            } else {
                deleteAnswer(answer);
            }
        } else if (e.getClickCount() == 2) {
            // 问题 / 回复内容列双击由只读选择复制编辑器接管（isCellEditable + 列编辑器启动），不打开编辑弹窗
            if (column != ACOL_QUERY && column != ACOL_ANSWER) {
                editAnswer(answer);
            }
        }
    }
    
    /**
     * 更新回复审计操作列悬浮高亮：鼠标位于操作列时按动作区域高亮对应按钮，并切换手型光标
     *
     * @param point 表格坐标下的鼠标位置，null 表示清除高亮
     */
    private void updateAnswerActionHover(Point point) {
        int row = -1;
        int zone = -1;
        boolean clickable = false;
        if (point != null) {
            int hitRow = answerTable.rowAtPoint(point);
            int hitColumn = answerTable.columnAtPoint(point);
            if (hitRow >= 0 && hitColumn == ACOL_ACTION) {
                row = hitRow;
                zone = actionZoneAt(point.x, answerTable.getCellRect(hitRow, ACOL_ACTION, false), 3);
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
    
    /**
     * 重绘回复审计指定行的操作单元格（行号无效时忽略）
     */
    private void repaintAnswerActionCell(int row) {
        if (row >= 0 && row < answerTable.getRowCount()) {
            answerTable.repaint(answerTable.getCellRect(row, ACOL_ACTION, false));
        }
    }
    
    /**
     * 查看回复详情（只读展示全部字段）
     */
    private void showAnswerDetail(AiAnswer answer) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(nullToEmpty(answer.getEnvName())));
        addFormRow(form, gbc, 1, new JLabel("ID："), new JLabel(answer.getId() != null ? String.valueOf(answer.getId()) : ""));
        addFormRow(form, gbc, 2, new JLabel("问题："), readonlyArea(nullToEmpty(answer.getQuery()), 2));
        addFormRow(form, gbc, 3, new JLabel("回复内容："), readonlyArea(nullToEmpty(answer.getAnswer()), 15));
        addFormRow(form, gbc, 4, new JLabel("用户ID："), readonlyField(nullToEmpty(answer.getUserId())));
        addFormRow(form, gbc, 5, new JLabel("是否允许修改："), new JLabel(answer.isModifyAllowed() ? ALLOW_MODIFY_YES : ALLOW_MODIFY_NO));
        addFormRow(form, gbc, 6, new JLabel("是否来源训练："), new JLabel(answer.isFromTraining() ? SOURCE_TRAINING_YES : SOURCE_TRAINING_NO));
        addFormRow(form, gbc, 7, new JLabel("创建时间："), new JLabel(nullToEmpty(answer.getCreateTime())));
        addFormRow(form, gbc, 8, new JLabel("更新时间："), new JLabel(nullToEmpty(answer.getUpdateTime())));
        JOptionPane.showMessageDialog(this, form, "回复详情", JOptionPane.PLAIN_MESSAGE);
    }
    
    /**
     * 编辑问题回复（保存时调用外部更新接口，按单行参数结构提交）
     */
    private void editAnswer(AiAnswer answer) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextArea queryArea = new JTextArea(nullToEmpty(answer.getQuery()), 2, 30);
        queryArea.setLineWrap(true);
        queryArea.setWrapStyleWord(true);
        JTextArea replyArea = new JTextArea(nullToEmpty(answer.getAnswer()), 15, 30);
        replyArea.setLineWrap(true);
        replyArea.setWrapStyleWord(true);
        JComboBox<String> allowModifyCombo = new JComboBox<>(new String[] {ALLOW_MODIFY_YES, ALLOW_MODIFY_NO});
        allowModifyCombo.setSelectedIndex(answer.isModifyAllowed() ? 0 : 1);
        
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        addFormRow(form, gbc, 0, new JLabel("环境："), new JLabel(nullToEmpty(answer.getEnvName())));
        addFormRow(form, gbc, 1, new JLabel("问题："), dialogScroll(queryArea, 2));
        addFormRow(form, gbc, 2, new JLabel("回复内容："), dialogScroll(replyArea, 15));
        addFormRow(form, gbc, 3, new JLabel("是否允许修改："), allowModifyCombo);
        
        int option = JOptionPane.showConfirmDialog(this, form, "编辑回复", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        String newQuery = queryArea.getText().trim();
        if (newQuery.isEmpty()) {
            JOptionPane.showMessageDialog(this, "问题内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String newReply = replyArea.getText().trim();
        if (newReply.isEmpty()) {
            JOptionPane.showMessageDialog(this, "回复内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int newAllowModify = allowModifyCombo.getSelectedIndex() == 0 ? AiAnswer.MODIFY_ALLOWED : AiAnswer.MODIFY_FORBIDDEN;
        AiEnvConfig envConfig = findEnvByName(answer.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(this, "未找到回复所属环境配置 [" + nullToEmpty(answer.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (answer.getId() == null) {
            JOptionPane.showMessageDialog(this, "该回复缺少 ID，无法调用更新接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        runApiTask("更新回复", () -> {
            String message = AiQuestionApiClient.getInstance().updateAnswer(envConfig, answer.getId(), newQuery, newReply, newAllowModify);
            return () -> {
                invalidateAnswers();
                refreshAnswerTable();
                setStatus("更新回复成功：ID " + answer.getId() + "（" + message + "）", true);
            };
        });
    }
    
    /**
     * 删除单条问题回复（调用外部删除接口）
     */
    private void deleteAnswer(AiAnswer answer) {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AiEnvConfig envConfig = findEnvByName(answer.getEnvName());
        if (envConfig == null) {
            JOptionPane.showMessageDialog(this, "未找到回复所属环境配置 [" + nullToEmpty(answer.getEnvName()) + "]", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (answer.getId() == null) {
            JOptionPane.showMessageDialog(this, "该回复缺少 ID，无法调用删除接口", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定删除环境 [" + envConfig.getEnvName() + "] 的问题回复 [ID " + answer.getId() + "] 吗？\n删除后不可恢复，请谨慎操作。", "确认删除",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        runApiTask("删除回复", () -> {
            String message = AiQuestionApiClient.getInstance().deleteAnswer(envConfig, answer.getId());
            return () -> {
                invalidateAnswers();
                refreshAnswerTable();
                setStatus("已删除回复：ID " + answer.getId() + "（" + message + "）", true);
            };
        });
    }
    
    /**
     * 批量更新已勾选回复的是否允许修改状态（勾选跨页 / 跨筛选保留；点击表头复选框可全选当前页）
     * <p>
     * 批量更新接口仅支持 allowModify 字段：确认弹窗中选择目标状态后，按回复所属环境分组调用更新接口；<br> 单个环境失败不影响其它环境，全部环境完成后汇总提示结果。
     */
    private void batchUpdateAnswers() {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiAnswer> checkedAnswers = answerTableModel.collectChecked(loadedAnswers);
        if (checkedAnswers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在列表中勾选要批量更新的回复（点击表头复选框可全选当前页）", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 按回复所属环境分组收集 ID（同一环境合并为一次请求）
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiAnswer answer : checkedAnswers) {
            envIdGroups.computeIfAbsent(answer.getEnvName(), key -> new ArrayList<>()).add(answer.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(this, "未找到回复所属环境配置 [" + nullToEmpty(entry.getKey()) + "]", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            targets.add(envConfig);
            selectedTotal += entry.getValue().size();
            summaryLines.add("　环境 [" + envConfig.getEnvName() + "] " + entry.getValue().size() + " 条");
        }
        // 确认弹窗：勾选摘要 + 允许修改状态下拉（批量更新接口仅支持该字段）
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
        int confirm = JOptionPane.showConfirmDialog(this, confirmPanel, "确认批量更新", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        int targetAllowModify = allowModifyCombo.getSelectedIndex() == 0 ? AiAnswer.MODIFY_ALLOWED : AiAnswer.MODIFY_FORBIDDEN;
        runApiTask("批量更新", () -> {
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
                // 全部成功时清空勾选；部分 / 全部失败时保留勾选便于修正后重试
                if (errors.isEmpty()) {
                    answerTableModel.clearChecked();
                }
                updateAnswerCheckControls();
                invalidateAnswers();
                refreshAnswerTable();
                if (errors.isEmpty()) {
                    setStatus("批量更新回复成功：共 " + successTotal + " 条（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    setStatus("批量更新回复部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "已成功更新 " + successTotal + " 条回复。\n\n以下环境更新失败：\n" + String.join("\n", errors),
                            "批量更新结果", JOptionPane.WARNING_MESSAGE);
                } else {
                    setStatus("批量更新回复失败：" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "批量更新失败：\n" + String.join("\n", errors), "错误", JOptionPane.ERROR_MESSAGE);
                }
            };
        });
    }
    
    /**
     * 批量删除已勾选的回复（勾选跨页 / 跨筛选保留，删除后不可恢复）
     * <p>
     * 确认弹窗中展示各环境删除条数并附不可恢复警告；<br> 按回复所属环境分组调用删除接口，同一环境合并一次请求，单环境失败不影响其它环境。
     */
    private void batchDeleteAnswers() {
        if (apiBusy) {
            JOptionPane.showMessageDialog(this, "请等待当前操作完成", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<AiAnswer> checkedAnswers = answerTableModel.collectChecked(loadedAnswers);
        if (checkedAnswers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先在列表中勾选要批量删除的回复（点击表头复选框可全选当前页）", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 按回复所属环境分组收集 ID（同一环境合并为一次请求）
        LinkedHashMap<String, List<Long>> envIdGroups = new LinkedHashMap<>();
        for (AiAnswer answer : checkedAnswers) {
            envIdGroups.computeIfAbsent(answer.getEnvName(), key -> new ArrayList<>()).add(answer.getId());
        }
        List<AiEnvConfig> targets = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        int selectedTotal = 0;
        for (Map.Entry<String, List<Long>> entry : envIdGroups.entrySet()) {
            AiEnvConfig envConfig = findEnvByName(entry.getKey());
            if (envConfig == null) {
                JOptionPane.showMessageDialog(this, "未找到回复所属环境配置 [" + nullToEmpty(entry.getKey()) + "]", "错误",
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
                // 全部成功时清空勾选；部分 / 全部失败时保留勾选便于修正后重试
                if (errors.isEmpty()) {
                    answerTableModel.clearChecked();
                }
                updateAnswerCheckControls();
                invalidateAnswers();
                refreshAnswerTable();
                if (errors.isEmpty()) {
                    setStatus("批量删除回复成功：共 " + successTotal + " 条（" + String.join("；", successDetails) + "）", true);
                } else if (successTotal > 0) {
                    setStatus("批量删除回复部分成功：" + successTotal + " 条成功；" + String.join("；", errors), false);
                    JOptionPane.showMessageDialog(this, "已成功删除 " + successTotal + " 条回复。\n\n以下环境删除失败：\n" + String.join("\n", errors),
                            "批量删除结果", JOptionPane.WARNING_MESSAGE);
                } else {
                    setStatus("批量删除回复失败：" + String.join("；", errors), false);
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
     * 创建只读多行文本组件（自动换行，配合滚动条查看长文本全文；统一弹窗宽度并按行数计算高度）
     * <p>
     * 文字可拖选选中，支持 Ctrl+C 快捷键与右键菜单「复制 / 全选」（回复详情对比区、问题详情等弹窗只读展示区）
     */
    private static JScrollPane readonlyArea(String text, int rows) {
        JTextArea area = new JTextArea(text, rows, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setToolTipText("可拖选文字复制，或右键全选 / 复制");
        attachCopyMenu(area);
        return dialogScroll(area, rows);
    }
    
    /**
     * 为只读文本组件附加右键「复制 / 全选」菜单（复制项仅在有选中文字时可用）
     */
    private static void attachCopyMenu(JTextComponent textComponent) {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制");
        JMenuItem selectAllItem = new JMenuItem("全选");
        copyItem.addActionListener(e -> textComponent.copy());
        selectAllItem.addActionListener(e -> textComponent.selectAll());
        // 菜单显示前按当前选中状态刷新复制项可用性（无选中时置灰）
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
    
    /**
     * 包装文本区域为详情 / 编辑弹窗滚动面板：统一首选宽度并按行数计算高度（避免 JOptionPane 弹窗过窄导致组件紧凑挤压）
     */
    private static JScrollPane dialogScroll(JTextArea area, int rows) {
        Font font = area.getFont() != null ? area.getFont() : UiConstants.FONT_SANS_12;
        int lineHeight = area.getFontMetrics(font).getHeight();
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(DIALOG_SCROLL_WIDTH, Math.max(rows, 1) * lineHeight + 6));
        return scroll;
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
     * 训练状态值 → 展示文字（-1-待训练；0-成功；1-失败；2-成功(同步回复)；3-训练中；4-超时，空 / 越界值显示为空）
     */
    private static String trainingStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        if (status == AiQuestion.TRAINING_STATUS_PENDING) {
            return TRAINING_STATUS_TEXTS[0];
        }
        int nonNegativeIndex = status + 1;
        if (nonNegativeIndex <= 0 || nonNegativeIndex >= TRAINING_STATUS_TEXTS.length) {
            return "";
        }
        return TRAINING_STATUS_TEXTS[nonNegativeIndex];
    }
    
    /**
     * 训练状态值 → 展示颜色（待训练琥珀色、成功类绿色、失败 / 超时红色、训练中主色、无状态时灰色）
     */
    private static Color trainingStatusColor(Integer status) {
        if (status == null) {
            return Color.GRAY;
        }
        return switch (status) {
            case AiQuestion.TRAINING_STATUS_PENDING -> UiConstants.COLOR_PENDING;
            case AiQuestion.TRAINING_STATUS_SUCCESS, AiQuestion.TRAINING_STATUS_SUCCESS_ANSWER -> UiConstants.COLOR_SUCCESS;
            case AiQuestion.TRAINING_STATUS_FAILED, AiQuestion.TRAINING_STATUS_TIMEOUT -> UiConstants.COLOR_DANGER;
            case AiQuestion.TRAINING_STATUS_RUNNING -> UiConstants.COLOR_PRIMARY;
            default -> Color.GRAY;
        };
    }
    
    /**
     * 毫秒时间戳格式化为 yyyy-MM-dd HH:mm:ss（空 / 非正值返回空串）
     */
    private static String formatEpochMillis(Long millis) {
        if (millis == null || millis <= 0) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(TRAINING_TIME_FORMATTER);
    }
    
    /**
     * 训练耗时（秒）：最近完成训练时间 - 开始训练时间（毫秒差换算为秒并保留 1 位小数；任一时间无效或差值非正时返回空串）
     */
    private static String trainingCostText(AiQuestion question) {
        Long start = question.getStartTrainingTime();
        Long last = question.getLastTrainingTime();
        if (start == null || last == null || start <= 0 || last <= start) {
            return "";
        }
        return String.format("%.1f", (last - start) / 1000.0);
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
     * 接口空闲后补偿被阻塞的页签数据加载（数据加载与接口操作共用并发标志，被跳过时在操作完成后自动重试）
     */
    private void resumePendingReloads() {
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
    
    /**
     * 接口任务：后台执行并返回成功后需要在 EDT 执行的回调
     */
    @FunctionalInterface
    private interface ApiCall {
        
        Runnable call() throws AiQuestionApiClient.AiApiException;
    }
    
    // ────────── 手动录入单行组件 ──────────
    
    /**
     * 手动录入一行录入组件集合：问题、用户ID、分类、开启训练、优先级别、训练参数、固定回复、删除按钮
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
            
            // 固定回复：多行文本（问题命中时优先返回的固定回复）
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
        
        private final String[] columns = {"选择", "ID", "问题", "分类", "用户ID", "训练参数", "固定回复", "回复ID", "开启训练", "训练状态", "开始训练时间",
                "最近完成训练时间", "训练耗时(秒)", "备注", "操作"};
        
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
        public boolean isCellEditable(int row, int column) {
            // 问题 / 固定回复列可进入只读选择复制编辑器（双击启动，编辑器原值返回不修改数据）
            return column == COL_QUESTION || column == COL_ANSWER;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            AiQuestion question = questions.get(row);
            return switch (column) {
                case COL_CHECK -> question.getId() != null ? isChecked(question) : null;
                case COL_ID -> question.getId();
                case COL_QUESTION -> nullToEmpty(question.getQuestion());
                case COL_CLASSIFY -> nullToEmpty(question.getQuestionClassify());
                case COL_USER -> nullToEmpty(question.getUserId());
                case COL_TRAINING_PARAM -> nullToEmpty(question.getTrainingParam());
                case COL_ANSWER -> nullToEmpty(question.getAnswer());
                case COL_ANSWER_ID -> question.getAnswerId();
                case COL_ENABLE -> question.isTrainingEnabled() ? "开启" : "不开启";
                case COL_TRAINING_STATUS -> question.getTrainingStatus();
                case COL_START_TRAINING_TIME -> formatEpochMillis(question.getStartTrainingTime());
                case COL_LAST_TRAINING_TIME -> formatEpochMillis(question.getLastTrainingTime());
                case COL_TRAINING_COST -> trainingCostText(question);
                case COL_REMARK -> nullToEmpty(question.getRemark());
                default -> "";
            };
        }
    }
    
    /**
     * 回复审计表格模型
     */
    private static class AnswerTableModel extends AbstractTableModel {
        
        private final String[] columns = {"选择", "ID", "问题", "回复内容", "用户ID", "是否允许修改", "是否来源训练", "创建时间", "更新时间", "操作"};
        
        private final List<AiAnswer> answers = new ArrayList<>();
        
        /**
         * 已勾选回复的键集合（环境名#ID 复合键，翻页 / 筛选后保留勾选）
         */
        private final Set<String> checkedKeys = new LinkedHashSet<>();
        
        void setData(List<AiAnswer> list) {
            answers.clear();
            answers.addAll(list);
            fireTableDataChanged();
        }
        
        AiAnswer getAnswerAt(int row) {
            return answers.get(row);
        }
        
        /**
         * 勾选键：环境名 + 回复 ID 组合（勾选状态按环境隔离）
         */
        private static String checkKey(AiAnswer answer) {
            return answer.getEnvName() + "#" + answer.getId();
        }
        
        boolean isChecked(AiAnswer answer) {
            return answer.getId() != null && checkedKeys.contains(checkKey(answer));
        }
        
        /**
         * 切换指定行的勾选状态（无 ID 的回复不可勾选）
         */
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
        
        /**
         * 当前页是否已全部勾选（无可勾选行时视为未全选）
         */
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
        
        /**
         * 切换当前页全选：已全选则取消勾选，否则全部勾选
         */
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
        
        /**
         * 已勾选回复总数（跨页统计）
         */
        int getCheckedCount() {
            return checkedKeys.size();
        }
        
        /**
         * 从全量数据中收集已勾选的回复（跨页）
         */
        List<AiAnswer> collectChecked(List<AiAnswer> source) {
            List<AiAnswer> result = new ArrayList<>();
            for (AiAnswer answer : source) {
                if (answer.getId() != null && checkedKeys.contains(checkKey(answer))) {
                    result.add(answer);
                }
            }
            return result;
        }
        
        /**
         * 清理已勾选但已不存在的回复（数据刷新后调用）
         */
        void retainChecked(Collection<AiAnswer> validAnswers) {
            Set<String> validKeys = new HashSet<>();
            for (AiAnswer answer : validAnswers) {
                if (answer.getId() != null) {
                    validKeys.add(checkKey(answer));
                }
            }
            checkedKeys.retainAll(validKeys);
        }
        
        /**
         * 清空全部勾选
         */
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
            // 问题 / 回复内容列可进入只读选择复制编辑器（双击启动，编辑器原值返回不修改数据）
            return column == ACOL_QUERY || column == ACOL_ANSWER;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            AiAnswer answer = answers.get(row);
            return switch (column) {
                case ACOL_CHECK -> answer.getId() != null ? isChecked(answer) : null;
                case ACOL_ID -> answer.getId();
                case ACOL_QUERY -> nullToEmpty(answer.getQuery());
                case ACOL_ANSWER -> nullToEmpty(answer.getAnswer());
                case ACOL_USER -> nullToEmpty(answer.getUserId());
                case ACOL_MODIFY -> answer.isModifyAllowed() ? ALLOW_MODIFY_YES : ALLOW_MODIFY_NO;
                case ACOL_SOURCE_TRAINING -> answer.isFromTraining() ? SOURCE_TRAINING_YES : SOURCE_TRAINING_NO;
                case ACOL_CREATE_TIME -> nullToEmpty(answer.getCreateTime());
                case ACOL_UPDATE_TIME -> nullToEmpty(answer.getUpdateTime());
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
     * 只读文本单元格编辑器：双击进入后文字自动全选，支持鼠标拖动选择与 Ctrl+C 复制（单元格内容不可修改，结束时原值返回）
     */
    private static class SelectableTextEditor extends DefaultCellEditor {
        
        /**
         * 进入编辑时的原值（编辑器只读，结束时原样返回，避免表格数据被改写）
         */
        private Object originalValue;
        
        private SelectableTextEditor() {
            super(new JTextField());
            JTextField field = (JTextField) getComponent();
            field.setEditable(false);
            field.setToolTipText("文字已全选：Ctrl+C 复制，可拖动选择部分文字，Esc 或点击其他区域退出");
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            originalValue = value;
            Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            // 编辑器组件显示并获得焦点后再全选（同步调用会因组件尚未显示而失效）
            SwingUtilities.invokeLater(((JTextField) component)::selectAll);
            return component;
        }
        
        @Override
        public Object getCellEditorValue() {
            return originalValue;
        }
    }
    
    /**
     * 回复ID单元格渲染器：有值时以超链接样式展示（链接色文字，点击由 {@link #handleTableClick} 触发查询回复详情），无值时显示为空
     */
    private static class AnswerIdCellRenderer extends DefaultTableCellRenderer {
        
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
    private static class HeaderCheckBoxRenderer implements TableCellRenderer {
        
        private final JCheckBox checkBox = new JCheckBox();
        
        /**
         * 当前表模型『当前页是否已全选』状态提供者
         */
        private final BooleanSupplier allCheckedSupplier;
        
        /**
         * 当前表模型行数提供者（决定复选框是否可点击）
         */
        private final IntSupplier rowCountSupplier;
        
        private HeaderCheckBoxRenderer(BooleanSupplier allCheckedSupplier, IntSupplier rowCountSupplier) {
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
     * 操作列渲染器：胶囊样式的详情 / 编辑 /（问题表含）训练 / 删除按钮，悬浮时按钮以饱和色填充并反白文字 （问题表点击区域四等分：查看详情 / 编辑 / 训练 / 删除；回复表三等分：查看详情 / 编辑 / 删除，与 {@link #actionZoneAt} 判定保持一致）
     */
    private static class ActionCellRenderer implements TableCellRenderer {
        
        private final JPanel panel;
        
        private final ActionLabel viewLabel = new ActionLabel("详情", UiConstants.COLOR_SUCCESS_LIGHT, UiConstants.COLOR_SUCCESS);
        
        private final ActionLabel editLabel = new ActionLabel("编辑", UiConstants.COLOR_PRIMARY_LIGHT, UiConstants.COLOR_PRIMARY);
        
        private final ActionLabel trainLabel = new ActionLabel("训练", UiConstants.COLOR_PENDING, UiConstants.COLOR_TRAINING);
        
        private final ActionLabel deleteLabel = new ActionLabel("删除", UiConstants.COLOR_DANGER_LIGHT, UiConstants.COLOR_DANGER);
        
        /**
         * 悬浮行号提供者（-1 表示无悬浮）
         */
        private final IntSupplier hoverRowSupplier;
        
        /**
         * 悬浮动作区域提供者（-1 表示无悬浮）
         */
        private final IntSupplier hoverZoneSupplier;
        
        /**
         * 删除动作的区域值（问题表四等分为 ACTION_DELETE，回复表三等分为 ANSWER_ACTION_DELETE）
         */
        private final int deleteZone;
        
        /**
         * 是否含训练动作（问题表 true，回复表 false）
         */
        private final boolean withTrainAction;
        
        private ActionCellRenderer(IntSupplier hoverRowSupplier, IntSupplier hoverZoneSupplier, String viewTooltip, boolean withTrainAction, int deleteZone) {
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
            // 文字与胶囊边缘留白（胶囊矩形绘制整个标签区域，border 仅内缩文字）；宽度不足时 FlatLaf 会截断为省略号，需保证操作列宽度足够
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
            // 文字自行居中绘制（避开 JLabel UI 的省略号截断逻辑，宽度不足时边溢出边完整展示）
            Graphics2D textGraphics = (Graphics2D) g.create();
            try {
                textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                String text = getText();
                if (text != null) {
                    textGraphics.setColor(getForeground());
                    textGraphics.setFont(getFont());
                    FontMetrics metrics = textGraphics.getFontMetrics(getFont());
                    // 在 border 内缩后的区域内居中（保留文字与胶囊边缘的留白）
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
