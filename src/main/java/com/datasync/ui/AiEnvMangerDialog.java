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
 * 左侧：环境列表（点击切换全局选中的环境并持久化，◉ 标记当前选中环境；支持新增、删除）；<br>
 * 右侧：环境接口配置（Host 与六个功能接口路径）+ 环境通用请求头参数（调用该环境全部接口时附加）。
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

    private JTextField remarkField;

    private JTable headerTable;

    private HeaderTableModel headerModel;

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
        remarkField = new JTextField(24);
        nameField.setFont(UiConstants.FONT_SANS_12);
        hostField.setFont(UiConstants.FONT_SANS_12);
        saveApiField.setFont(UiConstants.FONT_SANS_12);
        updateApiField.setFont(UiConstants.FONT_SANS_12);
        deleteApiField.setFont(UiConstants.FONT_SANS_12);
        listApiField.setFont(UiConstants.FONT_SANS_12);
        trainApiField.setFont(UiConstants.FONT_SANS_12);
        updateUserApiField.setFont(UiConstants.FONT_SANS_12);
        remarkField.setFont(UiConstants.FONT_SANS_12);

        addFormRow(formPanel, gbc, 0, new JLabel("环境名称："), nameField);
        addFormRow(formPanel, gbc, 1, new JLabel("Host："), hostField);
        addFormRow(formPanel, gbc, 2, new JLabel("保存接口："), saveApiField);
        addFormRow(formPanel, gbc, 3, new JLabel("更新接口："), updateApiField);
        addFormRow(formPanel, gbc, 4, new JLabel("删除接口："), deleteApiField);
        addFormRow(formPanel, gbc, 5, new JLabel("列表接口："), listApiField);
        addFormRow(formPanel, gbc, 6, new JLabel("训练接口："), trainApiField);
        addFormRow(formPanel, gbc, 7, new JLabel("批量更新接口："), updateUserApiField);
        addFormRow(formPanel, gbc, 8, new JLabel("备注："), remarkField);
        rightPanel.add(formPanel, BorderLayout.NORTH);

        // ── 通用请求头参数 ──
        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("通用请求头参数"), new EmptyBorder(0, 10, 6, 10)));

        JLabel headerHint = new JLabel("请求头以 JSON 保存并附加到该环境全部接口调用（Content-Type 由系统维护，无需配置）");
        headerHint.setForeground(Color.GRAY);
        headerHint.setFont(UiConstants.FONT_SANS_10);
        headerPanel.add(headerHint, BorderLayout.NORTH);

        headerModel = new HeaderTableModel();
        headerTable = new JTable(headerModel);
        headerTable.setRowHeight(26);
        headerTable.getTableHeader().setReorderingAllowed(false);
        headerTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        headerTable.getColumnModel().getColumn(1).setPreferredWidth(340);
        // 焦点离开表格时提交单元格编辑，避免最后一处编辑丢失
        headerTable.putClientProperty("terminateEditOnFocusLost", true);
        headerPanel.add(new JScrollPane(headerTable), BorderLayout.CENTER);

        ChildLayoutPanel headerBtnPanel = new ChildLayoutPanel(new Insets(2, 4, 2, 4), ChildLayoutPanel.LayoutType.RIGHT);
        JButton addHeaderBtn = ButtonFactory.createSecondary("添加请求头");
        addHeaderBtn.addActionListener(e -> addHeaderRow());
        JButton removeHeaderBtn = ButtonFactory.createDestructive("删除请求头");
        removeHeaderBtn.addActionListener(e -> removeSelectedHeaderRow());
        headerBtnPanel.add(addHeaderBtn);
        headerBtnPanel.add(removeHeaderBtn);
        headerPanel.add(headerBtnPanel, BorderLayout.SOUTH);
        rightPanel.add(headerPanel, BorderLayout.CENTER);

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
        remarkField.setText(nullToEmpty(env.getRemark()));
        headerModel.setRows(parseHeaderRows(env.getHeaders()));
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
        remarkField.setText("");
        headerModel.setRows(new ArrayList<>());
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
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定删除环境 [" + env.getEnvName() + "] 吗？\n该环境下已保存的问题不会被删除。",
                "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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
                node.fields().forEachRemaining(entry -> rows.add(new String[]{entry.getKey(), entry.getValue().asText("")}));
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

    private static String nullToEmpty(String text) {
        return text != null ? text : "";
    }

    /**
     * 接口路径空值兜底（未配置时使用默认路径）
     */
    private static String valueOrDefault(String apiPath) {
        return apiPath == null || apiPath.isBlank() ? "" : apiPath;
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
            rows.add(new String[]{"", ""});
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
