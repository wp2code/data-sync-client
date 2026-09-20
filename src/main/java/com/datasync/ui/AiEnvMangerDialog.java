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
import com.datasync.components.LinkJLabel;
import com.datasync.components.OptionJPanel;
import com.datasync.model.AiEnvConfig;
import com.datasync.util.ConfigUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

/**
 * AI 问题保存环境管理对话框（左右分栏布局）
 * <p>
 * 左侧：环境列表（点击切换全局选中的环境并持久化，◉ 标记当前选中环境；支持新增、删除）；<br> 右侧：环境接口配置（环境名称、Host、通用请求头参数，以及按问题训练 / 回复审计分组的接口路径，每个接口行提供“示例”链接，点击弹窗查看该接口的请求 / 响应参数 JSON 示例）。
 *
 * @author liuweiping
 * @date 2026-09-16
 **/
public class AiEnvMangerDialog extends FullscreenJDialog {
    
    /**
     * 请求头 JSON 序列化 / 反序列化（ObjectMapper 线程安全，静态复用）
     */
    private static final ObjectMapper HEADER_JSON_MAPPER = new ObjectMapper();
    
    // ── 左侧：环境列表（参照脚本管理脚本列表实现）──
    private JPanel envListPanel;
    
    private final List<OptionJPanel> envPanelList = new ArrayList<>();
    
    private OptionJPanel selectedEnvPanel;
    
    // ── 右侧：环境配置表单 ──
    private JTextField nameField;
    
    private JTextField hostField;
    
    private JTextField saveApiField;
    
    private JTextField updateApiField;
    
    private JTextField deleteApiField;
    
    private JTextField listApiField;
    
    private JTextField trainApiField;
    
    private JTextField updateUserApiField;
    
    private JTextField answerInfoApiField;
    
    private JTextField answerListApiField;
    
    private JTextField answerUpdateApiField;
    
    private JTextField answerDeleteApiField;
    
    private JTextField remarkField;
    
    private JTable headerTable;
    
    private HeaderTableModel headerModel;
    
    /**
     * 请求头表格滚动容器（高度随行数自适应，不被容器压缩）
     */
    private JScrollPane headerScrollPane;

    /**
     * 请求头配置内容区（手风琴容器：提示 + 表格 + 按钮，默认收起）
     */
    private JPanel headerContentPanel;

    /**
     * 请求头配置展开 / 收起开关（手风琴"配置"链接）
     */
    private LinkJLabel headerToggleLabel;
    
    /**
     * 表单外框（动态标题区分新增 / 编辑模式）
     */
    private TitledBorder formBorder;
    
    /**
     * 接口配置表单面板（刷新动态标题用）
     */
    private JPanel formPanel;
    
    /**
     * 当前编辑的环境（null 表示新增模式）
     */
    private AiEnvConfig editingEnv;
    
    public AiEnvMangerDialog(Frame owner) {
        super("AIENV", owner, "问题保存环境管理", true, 980, 620);
        initUI();
        refreshEnvList();
        selectDefaultEnv();
    }
    
    /**
     * 默认选中全局选中的环境（持久化保存的，无环境时进入新增模式）
     */
    private void selectDefaultEnv() {
        for (OptionJPanel panel : envPanelList) {
            AiEnvConfig config = (AiEnvConfig) panel.getData();
            if (Boolean.TRUE.equals(config.getSelected())) {
                selectEnv(config, panel);
                envListPanel.scrollRectToVisible(panel.getBounds());
                return;
            }
        }
        if (!envPanelList.isEmpty()) {
            OptionJPanel first = envPanelList.get(0);
            selectEnv((AiEnvConfig) first.getData(), first);
        } else {
            startCreateEnv();
        }
    }
    
    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        getRootPane().setBorder(new EmptyBorder(10, 12, 10, 12));
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel());
        splitPane.setResizeWeight(0.28);
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);
    }
    
    // ────────── 左侧：环境列表 ──────────
    
    private JPanel buildLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setBorder(BorderFactory.createTitledBorder("环境列表"));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        
        envListPanel = new JPanel();
        envListPanel.setLayout(new BoxLayout(envListPanel, BoxLayout.Y_AXIS));
        envListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        envListPanel.setBackground(UIManager.getColor("Panel.background"));
        
        JScrollPane listScrollPane = new JScrollPane(envListPanel);
        listScrollPane.setBorder(BorderFactory.createEmptyBorder());
        listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        
        ChildLayoutPanel btnPanel = new ChildLayoutPanel(new Insets(4, 4, 4, 4), ChildLayoutPanel.LayoutType.CENTER);
        JButton addBtn = ButtonFactory.createPrimary("新增环境");
        addBtn.addActionListener(e -> startCreateEnv());
        //        JButton deleteBtn = new JButton("删除");
        //        deleteBtn.addActionListener(e -> deleteSelectedEnv());
        btnPanel.add(addBtn);
        //        btnPanel.add(deleteBtn);
        leftPanel.add(btnPanel, BorderLayout.SOUTH);
        return leftPanel;
    }
    
    // ────────── 右侧：环境接口配置 + 通用请求头参数 ──────────
    
    private JPanel buildRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        
        // ── 接口配置表单 ──
        formPanel = new JPanel(new GridBagLayout());
        formBorder = BorderFactory.createTitledBorder("环境接口配置");
        formPanel.setBorder(BorderFactory.createCompoundBorder(formBorder, new EmptyBorder(4, 10, 4, 10)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        nameField = new JTextField(24);
        hostField = new JTextField(24);
        saveApiField = new JTextField(28);
        updateApiField = new JTextField(28);
        deleteApiField = new JTextField(28);
        listApiField = new JTextField(28);
        trainApiField = new JTextField(28);
        updateUserApiField = new JTextField(28);
        answerInfoApiField = new JTextField(28);
        answerListApiField = new JTextField(28);
        answerUpdateApiField = new JTextField(28);
        answerDeleteApiField = new JTextField(28);
        remarkField = new JTextField(24);
        nameField.setFont(UiConstants.FONT_SANS_12);
        hostField.setFont(UiConstants.FONT_SANS_12);
        saveApiField.setFont(UiConstants.FONT_SANS_12);
        updateApiField.setFont(UiConstants.FONT_SANS_12);
        deleteApiField.setFont(UiConstants.FONT_SANS_12);
        listApiField.setFont(UiConstants.FONT_SANS_12);
        trainApiField.setFont(UiConstants.FONT_SANS_12);
        updateUserApiField.setFont(UiConstants.FONT_SANS_12);
        answerInfoApiField.setFont(UiConstants.FONT_SANS_12);
        answerListApiField.setFont(UiConstants.FONT_SANS_12);
        answerUpdateApiField.setFont(UiConstants.FONT_SANS_12);
        answerDeleteApiField.setFont(UiConstants.FONT_SANS_12);
        remarkField.setFont(UiConstants.FONT_SANS_12);
        
        addFormRow(formPanel, gbc, 0, new JLabel("环境名称："), nameField);
        addFormRow(formPanel, gbc, 1, new JLabel("Host："), hostField);
        
        // ── 通用请求头参数（手风琴：默认收起，点击"配置"展开） ──
        JPanel headerTitleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerTitleRow.setOpaque(false);
        JLabel headerTitleLabel = new JLabel("通用请求头参数");
        headerTitleLabel.setFont(UiConstants.FONT_SANS_12_BOLD);
        headerTitleLabel.setForeground(UiConstants.COLOR_PRIMARY);
        headerTitleRow.add(headerTitleLabel);
        headerToggleLabel = new LinkJLabel("配置 ▾", "");
        headerToggleLabel.setFont(UiConstants.FONT_SANS_12);
        headerToggleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleHeaderContent();
            }
        });
        headerTitleRow.add(headerToggleLabel);
        addFormFullRow(formPanel, gbc, 2, headerTitleRow);

        // 手风琴内容区（提示 + 表格 + 按钮栏）：默认收起
        headerContentPanel = new JPanel(new BorderLayout(0, 4));
        JLabel headerHint = new JLabel("请求头以 JSON 保存并附加到该环境全部接口调用（Content-Type 由系统维护，无需配置）");
        headerHint.setForeground(Color.GRAY);
        headerHint.setFont(UiConstants.FONT_SANS_10);
        headerContentPanel.add(headerHint, BorderLayout.NORTH);
        
        headerModel = new HeaderTableModel();
        headerTable = new JTable(headerModel);
        headerTable.setRowHeight(26);
        headerTable.getTableHeader().setReorderingAllowed(false);
        headerTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        headerTable.getColumnModel().getColumn(1).setPreferredWidth(340);
        // 焦点离开表格时提交单元格编辑，避免最后一处编辑丢失
        headerTable.putClientProperty("terminateEditOnFocusLost", true);
        headerScrollPane = new JScrollPane(headerTable);
        headerContentPanel.add(headerScrollPane, BorderLayout.CENTER);
        
        ChildLayoutPanel headerBtnPanel = new ChildLayoutPanel(new Insets(2, 4, 2, 4), ChildLayoutPanel.LayoutType.RIGHT);
        JButton addHeaderBtn = ButtonFactory.createSecondary("添加请求头");
        addHeaderBtn.addActionListener(e -> addHeaderRow());
        JButton removeHeaderBtn = ButtonFactory.createDestructive("删除请求头");
        removeHeaderBtn.addActionListener(e -> removeSelectedHeaderRow());
        headerBtnPanel.add(addHeaderBtn);
        headerBtnPanel.add(removeHeaderBtn);
        headerContentPanel.add(headerBtnPanel, BorderLayout.SOUTH);
        headerContentPanel.setVisible(false);
        addFormFullRow(formPanel, gbc, 3, headerContentPanel);
        updateHeaderTableHeight();
        
        addFormGroupTitle(formPanel, gbc, 4, "问题训练接口");
        addFormRow(formPanel, gbc, 5, new JLabel("批量保存："), saveApiField, exampleLink(SAVE_EXAMPLE, saveApiField));
        addFormRow(formPanel, gbc, 6, new JLabel("更新："), updateApiField, exampleLink(UPDATE_EXAMPLE, updateApiField));
        addFormRow(formPanel, gbc, 7, new JLabel("删除："), deleteApiField, exampleLink(DELETE_EXAMPLE, deleteApiField));
        addFormRow(formPanel, gbc, 8, new JLabel("查询："), listApiField, exampleLink(LIST_EXAMPLE, listApiField));
        addFormRow(formPanel, gbc, 9, new JLabel("触发训练："), trainApiField, exampleLink(TRAIN_EXAMPLE, trainApiField));
        addFormRow(formPanel, gbc, 10, new JLabel("批量更新接口："), updateUserApiField, exampleLink(UPDATE_USER_EXAMPLE, updateUserApiField));
        addFormRow(formPanel, gbc, 11, new JLabel("回复详情查询："), answerInfoApiField, exampleLink(ANSWER_INFO_EXAMPLE, answerInfoApiField));
        addFormGroupTitle(formPanel, gbc, 12, "回复审计接口");
        addFormRow(formPanel, gbc, 13, new JLabel("回复列表："), answerListApiField, exampleLink(ANSWER_LIST_EXAMPLE, answerListApiField));
        addFormRow(formPanel, gbc, 14, new JLabel("回复更新："), answerUpdateApiField, exampleLink(ANSWER_UPDATE_EXAMPLE, answerUpdateApiField));
        addFormRow(formPanel, gbc, 15, new JLabel("回复删除："), answerDeleteApiField, exampleLink(ANSWER_DELETE_EXAMPLE, answerDeleteApiField));
        addFormRow(formPanel, gbc, 16, new JLabel("备注："), remarkField);
        
        // 表单整体放入滚动面板：窗口高度不足时出现滚动条，内容与表格高度不被压缩
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setBorder(BorderFactory.createEmptyBorder());
        formScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        rightPanel.add(formScrollPane, BorderLayout.CENTER);
        
        // ── 保存操作 ──
        ChildLayoutPanel saveBtnPanel = new ChildLayoutPanel(new Insets(2, 4, 2, 4), ChildLayoutPanel.LayoutType.RIGHT);
        JButton closeBtn = ButtonFactory.createSecondary("关闭");
        closeBtn.addActionListener(e -> dispose());
        JButton resetBtn = ButtonFactory.createDestructive("重置");
        resetBtn.addActionListener(e -> resetForm());
        JButton saveBtn = ButtonFactory.createPrimary("保存配置");
        saveBtn.addActionListener(e -> saveCurrentEnv());
        saveBtnPanel.add(closeBtn);
        saveBtnPanel.add(resetBtn);
        saveBtnPanel.add(saveBtn);
        rightPanel.add(saveBtnPanel, BorderLayout.SOUTH);
        
        getRootPane().setDefaultButton(saveBtn);
        return rightPanel;
    }
    
    // ────────── 表单加载 / 重置 ──────────
    
    /**
     * 左侧列表选中环境时回显到右侧表单
     */
    private void loadEnvToForm(AiEnvConfig env) {
        editingEnv = env;
        updateFormTitle("编辑：" + nullToEmpty(env.getEnvName()));
        nameField.setText(nullToEmpty(env.getEnvName()));
        hostField.setText(nullToEmpty(env.getHost()));
        saveApiField.setText(valueOrDefault(env.getSaveApi()));
        updateApiField.setText(valueOrDefault(env.getUpdateApi()));
        deleteApiField.setText(valueOrDefault(env.getDeleteApi()));
        listApiField.setText(valueOrDefault(env.getListApi()));
        trainApiField.setText(valueOrDefault(env.getTrainApi()));
        updateUserApiField.setText(valueOrDefault(env.getUpdateUserApi()));
        answerInfoApiField.setText(valueOrDefault(env.getAnswerInfoApi()));
        answerListApiField.setText(valueOrDefault(env.getAnswerListApi()));
        answerUpdateApiField.setText(valueOrDefault(env.getAnswerUpdateApi()));
        answerDeleteApiField.setText(valueOrDefault(env.getAnswerDeleteApi()));
        remarkField.setText(nullToEmpty(env.getRemark()));
        headerModel.setRows(parseHeaderRows(env.getHeaders()));
        updateHeaderTableHeight();
    }
    
    /**
     * 进入新增环境模式：清空右侧表单并聚焦环境名称
     */
    private void startCreateEnv() {
        if (selectedEnvPanel != null) {
            selectedEnvPanel.setSelected(false);
            selectedEnvPanel = null;
        }
        editingEnv = null;
        updateFormTitle("新增环境");
        nameField.setText("");
        hostField.setText("http://");
        saveApiField.setText(AiEnvConfig.DEFAULT_SAVE_API);
        updateApiField.setText(AiEnvConfig.DEFAULT_UPDATE_API);
        deleteApiField.setText(AiEnvConfig.DEFAULT_DELETE_API);
        listApiField.setText(AiEnvConfig.DEFAULT_LIST_API);
        trainApiField.setText(AiEnvConfig.DEFAULT_TRAIN_API);
        updateUserApiField.setText(AiEnvConfig.DEFAULT_UPDATE_USER_API);
        answerInfoApiField.setText(AiEnvConfig.DEFAULT_ANSWER_INFO_API);
        answerListApiField.setText(AiEnvConfig.DEFAULT_ANSWER_LIST_API);
        answerUpdateApiField.setText(AiEnvConfig.DEFAULT_ANSWER_UPDATE_API);
        answerDeleteApiField.setText(AiEnvConfig.DEFAULT_ANSWER_DELETE_API);
        remarkField.setText("");
        headerModel.setRows(new ArrayList<>());
        updateHeaderTableHeight();
        nameField.requestFocusInWindow();
    }
    
    /**
     * 重置表单：编辑模式回显当前环境，新增模式恢复默认值
     */
    private void resetForm() {
        if (editingEnv != null) {
            loadEnvToForm(editingEnv);
        } else {
            startCreateEnv();
        }
    }
    
    /**
     * 更新接口配置分组标题（区分新增 / 编辑模式）
     */
    private void updateFormTitle(String suffix) {
        formBorder.setTitle("环境接口配置（" + suffix + "）");
        formPanel.repaint();
    }
    
    // ────────── 保存 / 删除 ──────────
    
    /**
     * 保存右侧表单（新增环境或更新当前编辑的环境）
     */
    private void saveCurrentEnv() {
        commitHeaderEditing();
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        if (name.isEmpty() || host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "环境名称与 Host 不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        for (AiEnvConfig config : ConfigUtil.loadAiEnvConfigs()) {
            if (config.getEnvName().equals(name) && (editingEnv == null || !config.getId().equals(editingEnv.getId()))) {
                JOptionPane.showMessageDialog(this, "已存在同名环境：" + name, "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        String headersJson = collectHeadersJson();
        boolean ok;
        if (editingEnv == null) {
            AiEnvConfig config = new AiEnvConfig();
            fillConfig(config, name, host, headersJson);
            ok = ConfigUtil.saveAiEnvConfig(config);
        } else {
            fillConfig(editingEnv, name, host, headersJson);
            ok = ConfigUtil.updateAiEnvConfig(editingEnv);
        }
        if (!ok) {
            JOptionPane.showMessageDialog(this, "保存环境失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        refreshEnvList();
        selectEnvByName(name);
    }
    
    private void fillConfig(AiEnvConfig config, String name, String host, String headersJson) {
        config.setEnvName(name);
        config.setHost(host);
        config.setSaveApi(saveApiField.getText().trim());
        config.setUpdateApi(updateApiField.getText().trim());
        config.setDeleteApi(deleteApiField.getText().trim());
        config.setListApi(listApiField.getText().trim());
        config.setTrainApi(trainApiField.getText().trim());
        config.setUpdateUserApi(updateUserApiField.getText().trim());
        config.setAnswerInfoApi(answerInfoApiField.getText().trim());
        config.setAnswerListApi(answerListApiField.getText().trim());
        config.setAnswerUpdateApi(answerUpdateApiField.getText().trim());
        config.setAnswerDeleteApi(answerDeleteApiField.getText().trim());
        config.setHeaders(headersJson);
        config.setRemark(remarkField.getText().trim());
    }
    
    /**
     * 刷新左侧列表后按名称重新选中环境（回显保存结果）
     */
    private void selectEnvByName(String name) {
        for (OptionJPanel panel : envPanelList) {
            AiEnvConfig config = (AiEnvConfig) panel.getData();
            if (name.equals(config.getEnvName())) {
                selectEnv(config, panel);
                envListPanel.scrollRectToVisible(panel.getBounds());
                return;
            }
        }
    }
    
    /**
     * 删除左侧选中的环境配置
     */
    private void deleteSelectedEnv() {
        int selectedIndex = selectedEnvPanel != null ? envPanelList.indexOf(selectedEnvPanel) : -1;
        if (selectedIndex < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的环境", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        AiEnvConfig env = (AiEnvConfig) selectedEnvPanel.getData();
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除环境 [" + env.getEnvName() + "] 吗？\n该环境下已保存的问题不会被删除。", "确认删除",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        if (!ConfigUtil.deleteAiEnvConfig(env.getId())) {
            JOptionPane.showMessageDialog(this, "删除环境失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        refreshEnvList();
        if (envPanelList.isEmpty()) {
            startCreateEnv();
        } else {
            int targetIndex = Math.min(selectedIndex, envPanelList.size() - 1);
            OptionJPanel targetPanel = envPanelList.get(targetIndex);
            selectEnv((AiEnvConfig) targetPanel.getData(), targetPanel);
            envListPanel.scrollRectToVisible(targetPanel.getBounds());
        }
    }
    
    // ────────── 通用请求头维护 ──────────
    
    /**
     * 添加一行请求头并定位到参数名编辑
     */
    private void addHeaderRow() {
        commitHeaderEditing();
        headerModel.addRow();
        updateHeaderTableHeight();
        int lastRow = headerModel.getRowCount() - 1;
        headerTable.editCellAt(lastRow, 0);
        headerTable.changeSelection(lastRow, 0, false, false);
        headerTable.scrollRectToVisible(headerTable.getCellRect(lastRow, 0, true));
    }
    
    /**
     * 删除选中的请求头行
     */
    private void removeSelectedHeaderRow() {
        int row = headerTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的请求头", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        commitHeaderEditing();
        headerModel.removeRow(row);
        updateHeaderTableHeight();
    }
    
    /**
     * 提交请求头表格中未完成的单元格编辑
     */
    private void commitHeaderEditing() {
        if (headerTable.isEditing()) {
            headerTable.getCellEditor().stopCellEditing();
        }
    }
    
    /**
     * 收集请求头表格内容为 JSON 字符串（参数名为空的行忽略，全部为空返回 null）
     */
    private String collectHeadersJson() {
        ObjectNode node = HEADER_JSON_MAPPER.createObjectNode();
        for (String[] row : headerModel.getRows()) {
            String key = row[0] == null ? "" : row[0].trim();
            String value = row[1] == null ? "" : row[1].trim();
            if (!key.isEmpty()) {
                node.put(key, value);
            }
        }
        return node.size() == 0 ? null : node.toString();
    }
    
    /**
     * 解析请求头 JSON 为表格行（解析失败时返回空列表）
     */
    private static List<String[]> parseHeaderRows(String headersJson) {
        List<String[]> rows = new ArrayList<>();
        if (headersJson == null || headersJson.isBlank()) {
            return rows;
        }
        try {
            JsonNode node = HEADER_JSON_MAPPER.readTree(headersJson);
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> rows.add(new String[] {entry.getKey(), entry.getValue().asText("")}));
            }
        } catch (Exception ignored) {
            // 历史数据非法时按无请求头处理
        }
        return rows;
    }
    
    // ────────── 辅助方法 ──────────
    
    /**
     * 重建左侧环境列表：基于环境配置生成可选中面板（参照脚本管理脚本列表实现）
     */
    private void refreshEnvList() {
        envListPanel.removeAll();
        envPanelList.clear();
        selectedEnvPanel = null;
        
        List<AiEnvConfig> configs = ConfigUtil.loadAiEnvConfigs();
        if (configs.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无环境）", SwingConstants.CENTER);
            emptyLabel.setFont(UiConstants.FONT_SANS_12);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            envListPanel.add(emptyLabel);
        } else {
            for (AiEnvConfig config : configs) {
                OptionJPanel panel = createEnvPanel(config);
                envPanelList.add(panel);
                envListPanel.add(panel);
                envListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        envListPanel.revalidate();
        envListPanel.repaint();
    }
    
    /**
     * 创建单个环境面板：环境名称（含全局选中 ◉ 标记）为主标题、Host 为副标题，点击选中并回显右侧表单
     */
    private OptionJPanel createEnvPanel(AiEnvConfig config) {
        OptionJPanel panel = new OptionJPanel(envPanelText(config), config.getHost(), null);
        panel.setData(config);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setOnClick(() -> selectEnv(config, panel));
        attachEnvPopupMenu(panel, config, panel);
        return panel;
    }
    
    /**
     * 环境面板主文本：全局选中的环境前带 ◉ 标记，其余为 ○
     */
    private static String envPanelText(AiEnvConfig config) {
        return (Boolean.TRUE.equals(config.getSelected()) ? "◉ " : "○ ") + nullToEmpty(config.getEnvName());
    }
    
    /**
     * 刷新全部环境面板的全局选中标记
     */
    private void refreshEnvPanelTexts() {
        for (OptionJPanel panel : envPanelList) {
            panel.updateText(envPanelText((AiEnvConfig) panel.getData()));
        }
    }
    
    /**
     * 选中环境：切换面板高亮、回显右侧表单，并切换全局选中的环境（持久化，对 AiMock 界面生效）
     */
    private void selectEnv(AiEnvConfig env, OptionJPanel panel) {
        if (selectedEnvPanel != null && selectedEnvPanel != panel) {
            selectedEnvPanel.setSelected(false);
        }
        selectedEnvPanel = panel;
        selectedEnvPanel.setSelected(true);
        loadEnvToForm(env);
        switchGlobalSelectedEnv(env);
    }
    
    /**
     * 切换全局选中的环境（其余环境取消选中并持久化保存，进入界面后默认选中该环境）
     */
    private void switchGlobalSelectedEnv(AiEnvConfig env) {
        if (Boolean.TRUE.equals(env.getSelected())) {
            return;
        }
        if (!ConfigUtil.updateSelectedAiEnv(env.getId())) {
            JOptionPane.showMessageDialog(this, "切换全局选中环境失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (OptionJPanel panel : envPanelList) {
            ((AiEnvConfig) panel.getData()).setSelected(false);
        }
        env.setSelected(true);
        refreshEnvPanelTexts();
    }
    
    /**
     * 环境面板右键菜单（删除环境）
     */
    private void attachEnvPopupMenu(Component comp, AiEnvConfig config, OptionJPanel panel) {
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
                    selectEnv(config, panel);
                    JPopupMenu popup = new JPopupMenu();
                    JMenuItem deleteItem = new JMenuItem("删 除");
                    deleteItem.addActionListener(ev -> deleteSelectedEnv());
                    popup.add(deleteItem);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachEnvPopupMenu(child, config, panel);
            }
        }
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
     * 表单行重载：标签 + 输入框 + 尾部组件（接口行尾部的“示例”链接，链接列不参与拉伸）
     */
    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, JLabel label, JComponent field, JComponent trailing) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(label, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
        
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(trailing, gbc);
    }
    
    /**
     * 表单分组标题行：跨两列的加粗主色标签（接口按问题训练 / 回复审计分组标示）
     */
    private void addFormGroupTitle(JPanel panel, GridBagConstraints gbc, int row, String title) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UiConstants.FONT_SANS_12_BOLD);
        titleLabel.setForeground(UiConstants.COLOR_PRIMARY);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 5, 1, 5);
        panel.add(titleLabel, gbc);
        // 恢复共享约束，避免影响后续 addFormRow 的行布局
        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 5, 5, 5);
    }
    
    /**
     * 表单整行组件：跨两列的嵌入式区块（请求头提示 / 表格 / 按钮栏等）
     */
    private void addFormFullRow(JPanel panel, GridBagConstraints gbc, int row, JComponent component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(component, gbc);
        // 恢复共享约束，避免影响后续 addFormRow 的行布局
        gbc.gridwidth = 1;
        gbc.weightx = 0;
    }
    
    /**
     * 请求头表格区域高度自适应：按行数计算高度（最多展示 10 行，超出后表格内部滚动），保证内容完整可见且不被容器压缩
     */
    private void updateHeaderTableHeight() {
        if (headerScrollPane == null) {
            return;
        }
        int headerHeight = headerTable.getTableHeader().getHeight();
        if (headerHeight <= 0) {
            headerHeight = headerTable.getTableHeader().getPreferredSize().height;
        }
        if (headerHeight <= 0) {
            // 组件尚未显示时按行高加边距估算表头高度
            headerHeight = headerTable.getRowHeight() + 4;
        }
        int visibleRows = Math.min(Math.max(headerModel.getRowCount(), 1), 10);
        int height = headerHeight + visibleRows * headerTable.getRowHeight() + 2;
        int width = headerScrollPane.getPreferredSize().width;
        if (width <= 0) {
            width = 480;
        }
        headerScrollPane.setPreferredSize(new Dimension(width, height));
        headerScrollPane.setMinimumSize(new Dimension(width, height));
        formPanel.revalidate();
        formPanel.repaint();
    }
    
    /**
     * 切换请求头配置展开 / 收起（手风琴：默认收起，点击"配置"展开）
     */
    private void toggleHeaderContent() {
        boolean expanded = !headerContentPanel.isVisible();
        headerContentPanel.setVisible(expanded);
        headerToggleLabel.setText(expanded ? "收起 ▴" : "配置 ▾");
        if (expanded) {
            updateHeaderTableHeight();
        }
        formPanel.revalidate();
        formPanel.repaint();
    }
    
    private static String nullToEmpty(String text) {
        return text != null ? text : "";
    }
    
    /**
     * 接口路径空值兑底（未配置时使用默认路径）
     */
    private static String valueOrDefault(String apiPath) {
        return apiPath == null || apiPath.isBlank() ? "" : apiPath;
    }
        
    // ────────── 接口示例 ──────────
        
    /**
     * 接口示例定义（弹窗标题 / 路径空值兑底的默认路径 / 请求参数示例 / 响应参数示例 / URL 查询参数示例，无则为 null）
     */
    private record ApiExample(String title, String defaultPath, String requestExample, String responseExample, String urlQuery) {
        
        /**
         * 便利构造器：无 URL 查询参数的接口（urlQuery 为 null）
         */
        ApiExample(String title, String defaultPath, String requestExample, String responseExample) {
            this(title, defaultPath, requestExample, responseExample, null);
        }
    }
        
    /**
     * 批量保存问题接口示例（请求体为问题数组，数组内可选字段可省略）
     */
    private static final ApiExample SAVE_EXAMPLE = new ApiExample("批量保存问题", AiEnvConfig.DEFAULT_SAVE_API, """
            [
              {
                "question": "如何配置数据源",
                "questionClassify": "数据源",
                "userId": "u10001",
                "userSession": "sess-8f3e2a1c",
                "trainingParam": "{\\"topK\\":3}",
                "answer": "",
                "enableTraining": 1,
                "priority": 0
              },
              {
                "question": "如何测试数据源连通性",
                "questionClassify": "数据源",
                "enableTraining": 1,
                "priority": 0
              }
            ]
            """, """
            {
              "code": "0",
              "message": "保存成功",
              "data": null
            }
            """);
        
    /**
     * 更新问题接口示例（请求体为单个问题对象，id 为待更新的问题 ID）
     */
    private static final ApiExample UPDATE_EXAMPLE = new ApiExample("更新问题", AiEnvConfig.DEFAULT_UPDATE_API, """
            {
              "id": 1001,
              "question": "如何配置数据源",
              "questionClassify": "数据源",
              "userId": "u10001",
              "userSession": "sess-8f3e2a1c",
              "trainingParam": "{\\"topK\\":3}",
              "answer": "进入数据源管理页面新增配置",
              "enableTraining": 1,
              "priority": 10
            }
            """, """
            {
              "code": "0",
              "message": "更新成功",
              "data": null
            }
            """);
        
    /**
     * 删除问题接口示例（支持单个 / 批量，请求体为问题 ID 数组）
     */
    private static final ApiExample DELETE_EXAMPLE = new ApiExample("删除问题", AiEnvConfig.DEFAULT_DELETE_API, """
            {
              "ids": [1001, 1002]
            }
            """, """
            {
              "code": "0",
              "message": "删除成功",
              "data": null
            }
            """);
        
    /**
     * 查询问题列表接口示例（trainingStatus 可选，不传返回全部：-1-待训练；0-成功；1-失败；2-成功同步回复；3-训练中；4-超时）
     */
    private static final ApiExample LIST_EXAMPLE = new ApiExample("查询问题列表", AiEnvConfig.DEFAULT_LIST_API, """
            {
              "trainingStatus": 0
            }
            """, """
            {
              "code": "0",
              "message": "查询成功",
              "data": [
                {
                  "id": 1001,
                  "question": "如何配置数据源",
                  "questionClassify": "数据源",
                  "userId": "u10001",
                  "userSession": "sess-8f3e2a1c",
                  "remark": "",
                  "answerId": 2001,
                  "trainingParam": "{\\"topK\\":3}",
                  "trainingStatus": 2,
                  "startTrainingTime": 1758000000000,
                  "lastTrainingTime": 1758000060000,
                  "answer": "进入数据源管理页面新增配置",
                  "enableTraining": 1,
                  "priority": 10
                }
              ]
            }
            """);
        
    /**
     * 触发训练接口示例（questionIds 为勾选的问题 ID 列表，agentType：safety / system / ops / auto）
     */
    private static final ApiExample TRAIN_EXAMPLE = new ApiExample("触发训练", AiEnvConfig.DEFAULT_TRAIN_API, """
            {
              "questionIds": [1001, 1002],
              "agentType": "auto"
            }
            """, """
            {
              "code": "0",
              "message": "触发成功",
              "data": null
            }
            """);
        
    /**
     * 批量更新问题接口示例（ids 为勾选的问题 ID 列表，userId / userSession / trainingParam 均可选，留空字段不提交保持原值）
     */
    private static final ApiExample UPDATE_USER_EXAMPLE = new ApiExample("批量更新问题", AiEnvConfig.DEFAULT_UPDATE_USER_API, """
            {
              "ids": [1001, 1002],
              "userId": "u10001",
              "userSession": "sess-8f3e2a1c",
              "trainingParam": "{\\"topK\\":5}"
            }
            """, """
            {
              "code": "0",
              "message": "更新成功",
              "data": null
            }
            """);
        
    /**
     * 回复详情查询接口示例（answerId 以 URL 查询参数提交，请求体为空对象；data 字段名 → 文本值全量返回）
     */
    private static final ApiExample ANSWER_INFO_EXAMPLE = new ApiExample("回复详情查询", AiEnvConfig.DEFAULT_ANSWER_INFO_API, """
            {}
            """, """
            {
              "code": "0",
              "message": "查询成功",
              "data": {
                "answerId": 2001,
                "query": "如何配置数据源",
                "answer": "进入数据源管理页面新增配置",
                "allowModify": 1,
                "userId": "u10001"
              }
            }
            """, "?answerId=2001");
        
    /**
     * 回复列表查询接口示例（query / answer / allowModify / sourceTraining 均可选，不传字段不参与筛选）
     */
    private static final ApiExample ANSWER_LIST_EXAMPLE = new ApiExample("回复列表查询", AiEnvConfig.DEFAULT_ANSWER_LIST_API, """
            {
              "query": "数据源",
              "answer": "配置",
              "allowModify": 1,
              "sourceTraining": true
            }
            """, """
            {
              "code": "0",
              "message": "查询成功",
              "data": [
                {
                  "id": 2001,
                  "query": "如何配置数据源",
                  "answer": "进入数据源管理页面新增配置",
                  "userId": "u10001",
                  "allowModify": 1,
                  "sourceTraining": true,
                  "createTime": "2026-09-18 10:30:00",
                  "updateTime": "2026-09-18 10:35:00"
                }
              ]
            }
            """);
        
    /**
     * 回复更新接口示例（ids 为待更新回复 ID 列表，单条更新传单元素数组；批量更新仅提交 ids 与 allowModify）
     */
    private static final ApiExample ANSWER_UPDATE_EXAMPLE = new ApiExample("回复更新", AiEnvConfig.DEFAULT_ANSWER_UPDATE_API, """
            {
              "ids": [2001],
              "query": "如何配置数据源",
              "answer": "进入数据源管理页面新增配置",
              "allowModify": 1
            }
            """, """
            {
              "code": "0",
              "message": "更新成功",
              "data": null
            }
            """);
        
    /**
     * 回复删除接口示例（支持单个 / 批量，请求体为回复 ID 数组）
     */
    private static final ApiExample ANSWER_DELETE_EXAMPLE = new ApiExample("回复删除", AiEnvConfig.DEFAULT_ANSWER_DELETE_API, """
            {
              "ids": [2001, 2002]
            }
            """, """
            {
              "code": "0",
              "message": "删除成功",
              "data": null
            }
            """);
        
    /**
     * 创建接口“示例”链接（点击弹窗展示该接口的请求 / 响应参数 JSON 示例）
     */
    private LinkJLabel exampleLink(ApiExample example, JTextField pathField) {
        LinkJLabel link = new LinkJLabel("示例", "");
        link.setFont(UiConstants.FONT_SANS_12);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showApiExample(example, pathField.getText().trim());
            }
        });
        return link;
    }
        
    /**
     * 弹窗展示接口示例（请求方式与地址、请求参数与响应参数 JSON 示例，地址按当前表单 Host 与接口路径动态拼接）
     */
    private void showApiExample(ApiExample example, String apiPath) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
            
        JLabel urlLabel = new JLabel("POST    " + buildExampleUrl(apiPath, example));
        urlLabel.setFont(UiConstants.FONT_MONO_12);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(urlLabel, gbc);
            
        JLabel headerTip = new JLabel("请求头：Content-Type: application/json; charset=UTF-8（另自动附加该环境配置的通用请求头）");
        headerTip.setForeground(Color.GRAY);
        headerTip.setFont(UiConstants.FONT_SANS_10);
        gbc.gridy = 1;
        form.add(headerTip, gbc);
            
        String requestTitle = "请求参数示例" + (example.urlQuery() == null ? "" : "（参数已拼入上方地址）");
        gbc.gridy = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(exampleScroll(requestTitle, example.requestExample()), gbc);
        gbc.gridy = 3;
        form.add(exampleScroll("响应参数示例", example.responseExample()), gbc);
        gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
            
        JOptionPane.showMessageDialog(this, form, "接口示例 - " + example.title(), JOptionPane.PLAIN_MESSAGE);
    }
        
    /**
     * JSON 示例只读文本区（等宽字体、不换行保持 JSON 结构、高度按内容行数自适应，超出后内部滚动）
     */
    private static JScrollPane exampleScroll(String title, String json) {
        JTextArea area = new JTextArea(json);
        area.setFont(UiConstants.FONT_MONO_12);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        int lines = json.split("\n", -1).length;
        scroll.setPreferredSize(new Dimension(700, Math.min(20 + lines * 18, 320)));
        return scroll;
    }
        
    /**
     * 按当前表单 Host 与接口路径拼接完整示例地址（Host 未填写时用 {host} 占位，路径空值回退默认路径，协议补全与尾部斜杠处理与接口调用一致）
     */
    private String buildExampleUrl(String apiPath, ApiExample example) {
        String host = hostField.getText().trim();
        if (host.isEmpty() || "http://".equals(host)) {
            host = "http://{host}";
        } else {
            if (!host.startsWith("http://") && !host.startsWith("https://")) {
                host = "http://" + host;
            }
            while (host.endsWith("/")) {
                host = host.substring(0, host.length() - 1);
            }
        }
        String path = apiPath == null || apiPath.isBlank() ? example.defaultPath() : apiPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return host + path + (example.urlQuery() == null ? "" : example.urlQuery());
    }
        
    // ────────── 表格模型 ──────────
    
    /**
     * 通用请求头参数表格模型（两列可编辑：参数名 / 参数值）
     */
    private static class HeaderTableModel extends AbstractTableModel {
        
        private final String[] columns = {"参数名", "参数值"};
        
        private final List<String[]> rows = new ArrayList<>();
        
        void setRows(List<String[]> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }
        
        void addRow() {
            rows.add(new String[] {"", ""});
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }
        
        void removeRow(int index) {
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }
        
        List<String[]> getRows() {
            return rows;
        }
        
        @Override
        public int getRowCount() {
            return rows.size();
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
            return true;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            return rows.get(row)[column];
        }
        
        @Override
        public void setValueAt(Object value, int row, int column) {
            rows.get(row)[column] = value == null ? "" : value.toString();
            fireTableCellUpdated(row, column);
        }
    }
}
