package com.omnikit.ui.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnikit.components.AbsJPanl;
import com.omnikit.components.ButtonFactory;
import com.omnikit.components.ChildLayoutPanel;
import com.omnikit.components.FilterComboBox;
import com.omnikit.core.KafkaPushService;
import com.omnikit.model.KafkaConnection;
import com.omnikit.model.KafkaTopic;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.ConfigUtil;
import com.omnikit.util.LogUtil;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Kafka 消息队列数据推送内容面板 — 参照 AI 训练的环境选择： 顶栏选择 Kafka 连接（可在连接管理中维护），顶栏 Topic 选择由消息推送与 Topic 数据查询两个页签共用， 输入 JSON 消息推送，并在底部日志区输出推送过程
 */
public class KafkaPushPanel extends AbsJPanl {
    
    public static final String NAME = "KAFKA";
    
    /**
     * app_config 中记住选中 Kafka 连接的键（值为连接 ID）
     */
    private static final String KAFKA_SELECTED_CONN_KEY = "kafka.connection.selected";
    
    /**
     * app_config 中消息模板的键（JSON 数组 [{name, content}]）
     */
    private static final String PAYLOAD_TEMPLATES_KEY = "kafka.payload.templates";
    
    // ── UI 组件 ──
    private JComboBox<KafkaConnection> connectionCombo;
    
    private FilterComboBox<String> topicCombo;
    
    private JTextField keyField;
    
    private JTextField headersField;
    
    /**
     * 分区选择下拉：首项「自动」表示按 Key 哈希 / 轮询分配，其余为具体分区号
     */
    private JComboBox<String> partitionCombo;
    
    private JTextArea payloadArea;
    
    private JEditorPane logArea;
    
    private JButton pushBtn;
    
    /**
     * Topic 数据查询页签（独立组件）
     */
    private KafkaTopicQueryPanel topicQueryPanel;
    
    private JLabel statusLabel;
    
    /**
     * 重建连接下拉项期间抑制 onConnectionChanged 联动，避免重复触发自动获取
     */
    private boolean suppressConnectionEvents = false;
    
    /**
     * 重建 Topic 下拉项期间抑制记忆写入
     */
    private boolean suppressTopicEvents = false;
    
    /**
     * 当前已打开的 Kafka 连接（切换连接时先关闭上一个）
     */
    private KafkaConnection activeConnection;
    
    /**
     * 当前连接的后台 Topic 拉取任务（切换连接时取消，避免旧连接继续占用网络资源）
     */
    private SwingWorker<List<String>, Void> fetchTopicsWorker;
    
    /**
     * 后台查询 Topic 分区数的任务（连接/Topic 切换时结果按上下文校验，过期结果丢弃）
     */
    private SwingWorker<Integer, Void> partitionWorker;
    
    // ── 消息模板 ──
    
    /**
     * 内存中的模板数据：名称 → JSON 内容（持久化到 app_config）
     */
    private final Map<String, String> payloadTemplates = new LinkedHashMap<>();
    
    /**
     * 当前 JSON 内容来源的模板名称（通过「选择模板」套用后记录，「清空输入」后置空）。 保存模板时若该值存在则直接更新该模板，否则按新增处理
     */
    private String currentTemplateSource;
    
    /**
     * JSON 格式化解析器（ObjectMapper 线程安全，静态复用）
     */
    private static final ObjectMapper PAYLOAD_JSON_MAPPER = new ObjectMapper();
    
    private final java.awt.Frame ownerFrame;
    
    public KafkaPushPanel(java.awt.Frame owner) {
        this.ownerFrame = owner;
        initUI();
        refreshConnections();
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
    @Override
    public void refresh() {
        refreshConnections();
    }
    
    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        
        // ── 顶栏：连接选择 + Topic 选择 ──
        connectionCombo = new JComboBox<>();
        connectionCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getName() + "（" + value.getBootstrapServers() + "）");
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
                label.setOpaque(true);
            }
            return label;
        });
        connectionCombo.setPreferredSize(new Dimension(280, 28));
        connectionCombo.addActionListener(e -> {
            if (!suppressConnectionEvents) {
                onConnectionChanged();
            }
        });
        
        JButton manageConnBtn = ButtonFactory.createToolbar("连接管理");
        manageConnBtn.addActionListener(e -> openConnectionDialog());
        
        // ── 顶栏共用：Topic 选择 + 刷新（消息推送与 Topic 查询页签共用同一选择） ──
        topicCombo = new FilterComboBox<>();
        topicCombo.setPreferredSize(new Dimension(280, 28));
        // 选中 Topic 后按连接维度记忆，并刷新分区下拉框
        topicCombo.addActionListener(e -> {
            saveSelectedTopic();
            refreshTopicPartitions();
        });
        JButton fetchTopicBtn = ButtonFactory.createToolbar("刷新");
        fetchTopicBtn.addActionListener(e -> fetchTopicsFromCluster());
        
        ChildLayoutPanel topPanel = new ChildLayoutPanel(new Insets(0, 4, 0, 4), ChildLayoutPanel.LayoutType.RIGHT);
        topPanel.add(new JLabel("连接："));
        topPanel.add(connectionCombo);
        topPanel.add(manageConnBtn);
        topPanel.add(new JLabel("Topic："));
        topPanel.add(topicCombo);
        topPanel.add(fetchTopicBtn);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(topPanel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);
        
        // ── 中部：消息编辑表单 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("消息内容"), new EmptyBorder(8, 8, 8, 8)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 10, 6, 10);
        
        keyField = new JTextField(20);
        keyField.setToolTipText("消息 Key（可选，相同 Key 进入同一分区；留空则轮询分配）");
        
        partitionCombo = new JComboBox<>();
        partitionCombo.setPreferredSize(new Dimension(120, 28));
        partitionCombo.addItem("自动");
        partitionCombo.setToolTipText("推送目标分区：自动（按 Key 哈希 / 轮询分配）或指定分区号");
        
        headersField = new JTextField(20);
        headersField.setToolTipText("消息头（可选，JSON 对象形式，如 {\"traceId\":\"abc\",\"source\":\"client\"}，留空则不携带）");
        headersField.setFont(UiConstants.FONT_MONO_12);
        
        payloadArea = new JTextArea();
        payloadArea.setFont(UiConstants.FONT_MONO_12);
        payloadArea.setLineWrap(true);
        payloadArea.setWrapStyleWord(true);
        payloadArea.setText("{\n    \n}");
        payloadArea.setCaretPosition(6);
        
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0.08;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("消息Key："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.92;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(keyField, gbc);
        
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.weightx = 0.08;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("分区："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.92;
        gbc.anchor = GridBagConstraints.WEST;
        // 下拉框占满剩余宽度，右侧手动刷新按钮（连接/Topic 变化会自动刷新，按钮用于手动重查）
        JButton refreshPartitionBtn = ButtonFactory.createToolbar("刷新");
        refreshPartitionBtn.setMargin(new Insets(1, 8, 1, 8));
        refreshPartitionBtn.addActionListener(e -> refreshTopicPartitions());
        JPanel partitionRow = new JPanel(new BorderLayout(6, 0));
        partitionRow.setOpaque(false);
        partitionRow.add(partitionCombo, BorderLayout.CENTER);
        partitionRow.add(refreshPartitionBtn, BorderLayout.EAST);
        formPanel.add(partitionRow, gbc);
        
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.weightx = 0.08;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("消息头："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.92;
        gbc.anchor = GridBagConstraints.WEST;
        // 输入框右侧内联示例，便于直接照抄格式
        JLabel headersHint = new JLabel("示例：{\"traceId\":\"abc\",\"source\":\"client\"}");
        headersHint.setForeground(Color.GRAY);
        headersHint.setFont(UiConstants.FONT_SANS_10);
        JPanel headersRow = new JPanel(new BorderLayout(6, 0));
        headersRow.setOpaque(false);
        headersRow.add(headersField, BorderLayout.CENTER);
        headersRow.add(headersHint, BorderLayout.EAST);
        formPanel.add(headersRow, gbc);
        
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.weightx = 0.08;
        // 顶部 + 右对齐：与「消息Key：」「分区：」「消息头：」标签右端对齐（NORTH 会水平居中导致错位）
        gbc.anchor = GridBagConstraints.NORTHEAST;
        formPanel.add(new JLabel("JSON内容："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.92;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JScrollPane payloadScroll = new JScrollPane(payloadArea);
        payloadScroll.setPreferredSize(new Dimension(200, 160));
        formPanel.add(payloadScroll, gbc);
        
        // ── JSON 内容下方操作行：保存模板 / 选择模板 / 格式化 / 全屏编辑紧贴输入框右下角 ──
        JButton saveTemplateBtn = ButtonFactory.createSecondary("保存为模板");
        saveTemplateBtn.addActionListener(e -> saveTemplate(null));
        JButton selectTemplateBtn = ButtonFactory.createToolbar("选择模板");
        selectTemplateBtn.addActionListener(e -> showTemplateSelectDialog());
        JButton formatBtn = ButtonFactory.createSecondary("格式化 JSON");
        formatBtn.addActionListener(e -> formatJson());
        JButton fullscreenEditBtn = ButtonFactory.createToolbar("全屏编辑");
        fullscreenEditBtn.addActionListener(e -> openFullscreenEditor());
        JButton clearInputBtn = ButtonFactory.createDestructive("清空输入");
        clearInputBtn.addActionListener(e -> {
            payloadArea.setText("");
            // 清空后内容不再来自任何模板，保存将按新增处理
            currentTemplateSource = null;
        });
        pushBtn = ButtonFactory.createSuccess("推送到队列");
        pushBtn.addActionListener(e -> pushMessage());
        ChildLayoutPanel payloadActionRow = new ChildLayoutPanel();
        payloadActionRow.add(clearInputBtn);
        payloadActionRow.add(fullscreenEditBtn);
        payloadActionRow.add(formatBtn);
        payloadActionRow.add(selectTemplateBtn);
        payloadActionRow.add(saveTemplateBtn);
        payloadActionRow.add(pushBtn);
        // ── 提示说明与操作按钮同一行：提示居左，按钮组布局到最右边 ──
        JLabel hintLabel = new JLabel("支持单个 JSON 对象（推送一条）或 JSON 数组（逐条推送），例如 [{...},{...}]");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(UiConstants.FONT_SANS_10);
        JPanel payloadBottomRow = new JPanel(new BorderLayout(8, 0));
        payloadBottomRow.setOpaque(false);
        payloadBottomRow.add(hintLabel, BorderLayout.WEST);
        payloadBottomRow.add(payloadActionRow, BorderLayout.EAST);
        gbc.gridy = 4;
        gbc.gridx = 1;
        gbc.weightx = 0.92;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(payloadBottomRow, gbc);

        // ── 底部：推送日志 ──
        logArea = LogUtil.createLogArea();
        LogUtil.attachClearPopupMenu(logArea);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("日志"), new EmptyBorder(0, 1, 0, 1)));
        logScroll.setPreferredSize(new Dimension(0, 200));
        
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formPanel, logScroll);
        mainSplit.setResizeWeight(0.65);
        mainSplit.setDividerSize(6);
        mainSplit.setBorder(null);
        
        // ── 业务页签：消息推送 / Topic 数据查询（独立组件） ──
        JPanel pushTabPanel = new JPanel(new BorderLayout(0, 6));
        // 页签内容与页签线之间留出间距，避免操作组件紧贴页签线
        pushTabPanel.setBorder(new EmptyBorder(10, 12, 4, 12));
        pushTabPanel.add(mainSplit, BorderLayout.CENTER);
        topicQueryPanel = new KafkaTopicQueryPanel(this::getSelectedConnection, this::getSelectedTopic, this::appendLog);
        JTabbedPane centerTabs = new JTabbedPane();
        centerTabs.addTab("Topic 查询", topicQueryPanel);
        centerTabs.addTab("消息推送", pushTabPanel);
        add(centerTabs, BorderLayout.CENTER);
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);
        
        loadTemplates();
    }
    
    // ────────── 消息模板 ──────────
    
    /**
     * 从 app_config 加载消息模板（JSON 数组 [{name, content}]）到内存； 解析失败时仅日志提示，按无模板处理
     */
    private void loadTemplates() {
        payloadTemplates.clear();
        String raw = ConfigUtil.getAppConfig(PAYLOAD_TEMPLATES_KEY, "");
        if (!raw.isBlank()) {
            try {
                JsonNode root = PAYLOAD_JSON_MAPPER.readTree(raw);
                if (root.isArray()) {
                    for (JsonNode item : root) {
                        if (item.isObject() && item.hasNonNull("name")) {
                            payloadTemplates.put(item.get("name").asText(), item.path("content").asText(""));
                        }
                    }
                }
            } catch (Exception ex) {
                appendLog(LogUtil.warn("[KAFKA] 消息模板加载失败，已忽略：" + ex.getMessage()));
            }
        }
    }
    
    /**
     * 将内存模板持久化到 app_config（JSON 数组形式）
     */
    private boolean persistTemplates() {
        ArrayNode array = PAYLOAD_JSON_MAPPER.createArrayNode();
        payloadTemplates.forEach((name, content) -> {
            ObjectNode item = PAYLOAD_JSON_MAPPER.createObjectNode();
            item.put("name", name);
            item.put("content", content);
            array.add(item);
        });
        return ConfigUtil.setAppConfig(PAYLOAD_TEMPLATES_KEY, array.toString());
    }
    
    /**
     * 模板命名对话框：名称为空或与已有模板重复时弹窗提示并在对话框内红字提示， 对话框保持打开（不自动关闭），已输入内容保留；返回 null 表示取消。
     *
     * <p>注意：不能使用 JOptionPane.createDialog 实现——BasicOptionPaneUI 会在 VALUE_PROPERTY 变化时 自动关闭对话框，导致校验失败也无法保留窗口。
     */
    private String showTemplateNameDialog(String title, String message) {
        JTextField nameField = new JTextField(20);
        JLabel hintLabel = new JLabel(" ");
        hintLabel.setForeground(UiConstants.COLOR_DANGER_LIGHT);
        hintLabel.setFont(UiConstants.FONT_SANS_10);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(new EmptyBorder(8, 12, 4, 12));
        body.add(new JLabel(message), BorderLayout.NORTH);
        body.add(nameField, BorderLayout.CENTER);
        body.add(hintLabel, BorderLayout.SOUTH);
        
        JButton okBtn = ButtonFactory.createPrimary("确定");
        JButton cancelBtn = ButtonFactory.createSecondary("取消");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setBorder(new EmptyBorder(0, 12, 8, 12));
        buttonPanel.add(cancelBtn);
        buttonPanel.add(okBtn);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(body, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);
        
        java.awt.Frame owner = (java.awt.Frame) javax.swing.SwingUtilities.getAncestorOfClass(java.awt.Frame.class, this);
        JDialog dialog = new JDialog(owner, title, true);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(okBtn);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        
        final boolean[] confirmed = {false};
        okBtn.addActionListener(e -> {
            String input = nameField.getText().trim();
            // 名称非法：弹窗提示 + 对话框内红字提示，对话框保持打开、已输入内容保留
            if (input.isEmpty() || payloadTemplates.containsKey(input)) {
                hintLabel.setText(input.isEmpty() ? "模板名称不能为空" : "模板名称已存在：「" + input + "」，请换一个名称");
                nameField.requestFocusInWindow();
                return;
            }
            confirmed[0] = true;
            dialog.setVisible(false);
        });
        cancelBtn.addActionListener(e -> dialog.setVisible(false));
        
        dialog.setVisible(true);
        dialog.dispose();
        if (confirmed[0]) {
            return nameField.getText().trim();
        }
        return null;
    }
    
    /**
     * 保存当前 JSON 内容为模板：内容来自已选模板时弹窗确认「更新当前模板」或「另存为新模板」， 否则弹窗输入名称按新增处理； 传入选择模板弹窗的列表时同步刷新列表（主界面按钮保存时传 null）
     */
    private void saveTemplate(javax.swing.DefaultListModel<String> listModel) {
        String content = payloadArea.getText();
        if (content.isBlank()) {
            JOptionPane.showMessageDialog(this, "JSON 内容为空，无法保存为模板", "保存为模板", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 模板内容必须是合法 JSON，避免保存后套用即推送失败
        try {
            PAYLOAD_JSON_MAPPER.readTree(content);
        } catch (Exception ex) {
            appendLog(LogUtil.warn("[KAFKA] 模板内容错误：不是合法 JSON，无法保存为模板（" + ex.getMessage() + "）"));
            JOptionPane.showMessageDialog(this, "模板内容错误：不是合法 JSON，无法保存为模板\n\n" + ex.getMessage(), "保存为模板",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name;
        if (currentTemplateSource != null && payloadTemplates.containsKey(currentTemplateSource)) {
            // 内容来自已选模板：让用户选择更新当前模板或另存为新模板
            int choice = JOptionPane.showConfirmDialog(this,
                    "当前内容来自模板「" + currentTemplateSource + "」。\n选择「是」更新该模板，选择「否」另存为新模板。", "保存为模板",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                name = currentTemplateSource;
            } else {
                name = showTemplateNameDialog("另存为新模板", "请输入新模板名称：");
                if (name == null) {
                    return;
                }
            }
        } else {
            name = showTemplateNameDialog("保存为模板", "请输入模板名称：");
            if (name == null) {
                return;
            }
        }
        String previousContent = payloadTemplates.get(name);
        boolean overwrite = previousContent != null;
        payloadTemplates.put(name, content);
        if (!persistTemplates()) {
            // 持久化失败回滚内存数据（更新时恢复原内容，新增时移除）
            if (overwrite) {
                payloadTemplates.put(name, previousContent);
            } else {
                payloadTemplates.remove(name);
            }
            JOptionPane.showMessageDialog(this, "模板保存失败：" + name, "保存为模板", JOptionPane.ERROR_MESSAGE);
            setStatus("模板保存失败：" + name);
            return;
        }
        if (!overwrite && listModel != null) {
            listModel.addElement(name);
        }
        currentTemplateSource = name;
        setStatus((overwrite ? "模板已更新：" : "模板保存成功：") + name);
    }
    
    /**
     * 删除弹窗列表中选中的模板（确认后执行，失败时回滚内存数据）
     */
    private void deleteTemplate(javax.swing.JList<String> list) {
        String name = list.getSelectedValue();
        if (name == null || !payloadTemplates.containsKey(name)) {
            setStatus("请先选中要删除的模板");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "确定删除模板「" + name + "」？", "删除模板", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        String removed = payloadTemplates.remove(name);
        if (!persistTemplates()) {
            payloadTemplates.put(name, removed);
            setStatus("模板删除失败：" + name);
            return;
        }
        ((javax.swing.DefaultListModel<String>) list.getModel()).removeElement(name);
        setStatus("模板删除成功：" + name);
    }
    
    // ────────── 连接选择 ──────────
    
    private void openConnectionDialog() {
        new KafkaConnectionDialog(ownerFrame).setVisible(true);
        refreshConnections();
    }
    
    private void refreshConnections() {
        List<KafkaConnection> configs = ConfigUtil.loadKafkaConnections();
        String previousName = connectionCombo.getSelectedItem() instanceof KafkaConnection selected ? selected.getName() : null;
        // 重建下拉项期间抑制联动事件，结束后统一触发一次 onConnectionChanged
        suppressConnectionEvents = true;
        try {
            connectionCombo.removeAllItems();
            for (KafkaConnection config : configs) {
                connectionCombo.addItem(config);
            }
            // 优先恢复上次记住的选中连接（按 ID），其次恢复本次重建前的选中；已删除的连接自动跳过
            KafkaConnection toSelect = null;
            String savedId = ConfigUtil.getAppConfig(KAFKA_SELECTED_CONN_KEY, "");
            if (!savedId.isBlank()) {
                toSelect = configs.stream().filter(config -> String.valueOf(config.getId()).equals(savedId)).findFirst().orElse(null);
            }
            if (toSelect == null && previousName != null) {
                toSelect = configs.stream().filter(config -> config.getName().equals(previousName)).findFirst().orElse(null);
            }
            if (toSelect != null) {
                connectionCombo.setSelectedItem(toSelect);
            } else if (connectionCombo.getSelectedIndex() < 0 && configs.size() == 1) {
                connectionCombo.setSelectedIndex(0);
            }
        } finally {
            suppressConnectionEvents = false;
        }
        onConnectionChanged();
    }
    
    private void onConnectionChanged() {
        Object selected = connectionCombo.getSelectedItem();
        if (!(selected instanceof KafkaConnection conn) || conn.getId() == null) {
            closeActiveConnection();
            activeConnection = null;
            topicCombo.removeAllItems();
            return;
        }
        // 切换连接：先关闭之前的连接，再打开新连接
        if (activeConnection != conn) {
            closeActiveConnection();
            activeConnection = conn;
            // 选中连接立即打印连接成功日志，无需等待后台 Topic 拉取结果
            appendLog(LogUtil.success("[KAFKA] 连接成功：" + conn.getName() + "（" + conn.getBootstrapServers() + "）"));
        }
        // 记住选中的连接，下次打开面板自动选中
        ConfigUtil.setAppConfig(KAFKA_SELECTED_CONN_KEY, String.valueOf(conn.getId()));
        List<String> topicNames = loadTopicsIntoCombo(conn);
        // 连接选中/切换成功后自动获取集群已有 Topic（静默合并，失败不打断）
        autoFetchTopics(conn);
    }
    
    /**
     * 关闭当前连接：Kafka 客户端（Admin/Producer/Consumer）均按操作即时创建并关闭， 这里负责终止该连接遗留的后台 Topic 拉取任务，避免旧连接继续占用网络资源
     */
    private void closeActiveConnection() {
        SwingWorker<List<String>, Void> worker = fetchTopicsWorker;
        fetchTopicsWorker = null;
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        if (activeConnection != null) {
            appendLog(LogUtil.warn("[KAFKA] 已关闭之前的连接：" + activeConnection.getName()));
        }
    }
    
    /**
     * 从本地配置加载该连接的 Topic 列表到下拉框，返回 Topic 名称列表； 恢复上次选中的 Topic，不存在（或已删除）时默认选中第一个
     */
    private List<String> loadTopicsIntoCombo(KafkaConnection conn) {
        List<KafkaTopic> topics = ConfigUtil.loadKafkaTopics(conn.getId());
        List<String> topicNames = topics.stream().map(KafkaTopic::getTopicName).toList();
        suppressTopicEvents = true;
        try {
            topicCombo.setAllItems(topicNames);
            String saved = ConfigUtil.getAppConfig(pushTopicKey(conn), "");
            if (topicNames.contains(saved)) {
                topicCombo.setSelectedItem(saved);
            } else if (!topicNames.isEmpty()) {
                topicCombo.setSelectedIndex(0);
            }
        } finally {
            suppressTopicEvents = false;
        }
        // Topic 列表变化后同步刷新分区下拉框
        refreshTopicPartitions();
        return topicNames;
    }
    
    /**
     * 后台查询当前 Topic 的分区数并填充分区下拉框（首项「自动」为按 Key 哈希 / 轮询分配）； 连接或 Topic 未选、查询失败时仅保留「自动」
     */
    private void refreshTopicPartitions() {
        KafkaConnection conn = getSelectedConnection();
        String topic = getSelectedTopic();
        // 重建下拉期间先回到「自动」，避免残留其他 Topic 的分区号
        partitionCombo.removeAllItems();
        partitionCombo.addItem("自动");
        partitionCombo.setSelectedIndex(0);
        if (conn == null || conn.getId() == null || topic == null || topic.isBlank()) {
            return;
        }
        final KafkaConnection targetConn = conn;
        final String targetTopic = topic;
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                return KafkaPushService.getTopicPartitionCount(targetConn, targetTopic);
            }
            
            @Override
            protected void done() {
                // 返回时连接或 Topic 已切换，过期结果丢弃
                if (isCancelled() || getSelectedConnection() != targetConn || !targetTopic.equals(getSelectedTopic())) {
                    return;
                }
                try {
                    int count = get();
                    if (count > 0) {
                        for (int i = 0; i < count; i++) {
                            partitionCombo.addItem(String.valueOf(i));
                        }
                        setStatus("已获取 Topic「" + targetTopic + "」分区数：" + count);
                    }
                } catch (Exception ignore) {
                    // 查询失败保持「自动」，不影响推送（自动分配仍可用）
                }
            }
        };
        partitionWorker = worker;
        worker.execute();
    }
    
    /**
     * 保存推送页签当前选中的 Topic（按连接维度记忆，重建下拉期间跳过）
     */
    private void saveSelectedTopic() {
        if (suppressTopicEvents) {
            return;
        }
        KafkaConnection conn = getSelectedConnection();
        if (conn != null && conn.getId() != null && topicCombo.getSelectedItem() instanceof String topic && !topic.isBlank()) {
            ConfigUtil.setAppConfig(pushTopicKey(conn), topic);
        }
    }
    
    private static String pushTopicKey(KafkaConnection conn) {
        return "kafka.topic." + conn.getId();
    }
    
    /**
     * 后台自动拉取集群 Topic：成功后合并进该连接的 Topic 配置（已存在的忽略）， 刷新下拉框；失败时仅在日志区提示，不打断操作
     */
    private void autoFetchTopics(KafkaConnection conn) {
        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return KafkaPushService.listTopics(conn);
            }
            
            @Override
            protected void done() {
                // 任务已被切换连接时取消，或结果返回时用户已切换到其他连接，本次结果不适用
                if (isCancelled() || getSelectedConnection() != conn) {
                    return;
                }
                try {
                    List<String> topics = get();
                    int added = 0;
                    for (String topicName : topics) {
                        if (ConfigUtil.saveKafkaTopic(conn.getId(), topicName, null)) {
                            added++;
                        }
                    }
                    if (added > 0) {
                        loadTopicsIntoCombo(conn);
                        appendLog(LogUtil.success("[KAFKA] 自动获取集群 Topic 完成：共 " + topics.size() + " 个，新增保存 " + added + " 个"));
                        setStatus("自动获取 Topic 完成：共 " + topics.size() + " 个，新增 " + added + " 个");
                    } else {
                        setStatus("集群 Topic 已同步（共 " + topics.size() + " 个）");
                    }
                } catch (Exception ex) {
                    appendLog(LogUtil.warn("[KAFKA] 自动获取 Topic 失败：" + KafkaPushService.friendlyError(ex)));
                    setStatus("自动获取 Topic 失败");
                }
            }
        };
        fetchTopicsWorker = worker;
        worker.execute();
    }
    
    // ────────── Topic 获取 ──────────
    
    /**
     * 从集群实时获取 Topic 列表：结果合并进该连接的 Topic 配置（已存在的忽略），并刷新下拉框
     */
    private void fetchTopicsFromCluster() {
        KafkaConnection conn = getSelectedConnection();
        if (conn == null) {
            setStatus(LogUtil.warn("请先选择 Kafka 连接"));
            return;
        }
        final KafkaConnection target = conn;
        setStatus("正在查询集群 Topic 列表 ...");
        appendLog("[KAFKA] 查询集群 Topic 列表：" + target.getBootstrapServers());
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return KafkaPushService.listTopics(target);
            }
            
            @Override
            protected void done() {
                try {
                    List<String> topics = get();
                    int added = 0;
                    for (String topicName : topics) {
                        if (ConfigUtil.saveKafkaTopic(target.getId(), topicName, null)) {
                            added++;
                        }
                    }
                    appendLog(LogUtil.success("[KAFKA] 共 " + topics.size() + " 个 Topic，新增保存 " + added + " 个"));
                    onConnectionChanged();
                    setStatus("获取 Topic 完成：共 " + topics.size() + " 个，新增 " + added + " 个");
                } catch (Exception ex) {
                    appendLog(LogUtil.failed("[KAFKA] 查询 Topic 失败：" + KafkaPushService.friendlyError(ex)));
                    setStatus("查询 Topic 失败：" + ex.getMessage());
                }
            }
        }.execute();
    }
    
    // ────────── JSON 格式化 ──────────
    
    /**
     * JSON 内容格式化（pretty print），对象与数组均支持；内容不是合法 JSON 时日志提示且不修改原文
     */
    private void formatJson() {
        String text = payloadArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            JsonNode root = PAYLOAD_JSON_MAPPER.readTree(text);
            String formatted = PAYLOAD_JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            payloadArea.setText(formatted);
            payloadArea.setCaretPosition(0);
            setStatus("JSON 格式化完成");
        } catch (Exception ex) {
            appendLog(LogUtil.warn("[KAFKA] JSON 格式化失败，内容不是合法 JSON：" + ex.getMessage()));
            setStatus("JSON 格式化失败：内容不是合法 JSON");
        }
    }
    
    // ────────── 全屏编辑 ──────────
    
    /**
     * 打开全屏编辑弹窗编辑 JSON 内容：当前输入框内容带入弹窗， 点击「确定回填」后写回输入框（取消或直接关闭则不回填）
     */
    private void openFullscreenEditor() {
        KafkaJsonEditDialog dialog = new KafkaJsonEditDialog(ownerFrame, payloadArea.getText());
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            payloadArea.setText(dialog.getEditedText());
            payloadArea.setCaretPosition(0);
            setStatus("JSON 内容已从全屏编辑回填");
        }
    }
    
    // ────────── 消息推送 ──────────
    
    /**
     * 弹窗选择模板：列表展示已保存模板，选中确定后套用到 JSON 内容， 并同步顶部模板下拉框选中项（重建期间跳过联动）
     */
    private void showTemplateSelectDialog() {
        javax.swing.DefaultListModel<String> listModel = new javax.swing.DefaultListModel<>();
        payloadTemplates.keySet().forEach(listModel::addElement);
        javax.swing.JList<String> list = new javax.swing.JList<>(listModel);
        list.setVisibleRowCount(8);
        list.setFont(UiConstants.FONT_SANS_12);
        if (!payloadTemplates.isEmpty()) {
            list.setSelectedIndex(0);
        }
        
        JPanel listPanel = new JPanel(new BorderLayout(0, 6));
        listPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        // 弹窗内直接管理模板：删除选中模板
        JPanel dialogBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dialogBtnRow.setOpaque(false);
        JButton deleteInDialogBtn = ButtonFactory.createDestructive("删除选中模板");
        deleteInDialogBtn.addActionListener(e -> deleteTemplate(list));
        dialogBtnRow.add(deleteInDialogBtn);
        listPanel.add(dialogBtnRow, BorderLayout.SOUTH);
        
        int choice = javax.swing.JOptionPane.showConfirmDialog(this, listPanel, "选择模板", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (choice != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        String name = list.getSelectedValue();
        if (name == null || !payloadTemplates.containsKey(name)) {
            return;
        }
        payloadArea.setText(payloadTemplates.get(name));
        payloadArea.setCaretPosition(0);
        // 记录内容来源模板：后续保存将直接更新该模板
        currentTemplateSource = name;
        setStatus("已套用模板：" + name);
    }
    
    /**
     * 校验推送内容：必须为合法 JSON（单个对象或数组），且不允许空对象 {} 与空数组 []； 返回 null 表示通过，否则返回失败原因
     */
    private String validateJsonPayload(String payload) {
        JsonNode root;
        try {
            root = PAYLOAD_JSON_MAPPER.readTree(payload);
        } catch (Exception ex) {
            return "内容不是合法 JSON：" + ex.getMessage();
        }
        if (root.isObject() && root.isEmpty()) {
            return "JSON 内容为空对象 {}，至少需要一个字段";
        }
        if (root.isArray()) {
            if (root.isEmpty()) {
                return "JSON 内容为空数组 []，至少需要一条消息";
            }
            for (JsonNode item : root) {
                if (!item.isObject() || item.isEmpty()) {
                    return "JSON 数组中存在空对象或非对象元素，数组内必须全部为非空对象";
                }
            }
        }
        return null;
    }
    
    private void pushMessage() {
        KafkaConnection conn = getSelectedConnection();
        if (conn == null) {
            appendLog(LogUtil.warn("[KAFKA] 请先选择 Kafka 连接"));
            return;
        }
        String topic = topicCombo.getSelectedItem() instanceof String selected ? selected.trim() : null;
        if (topic == null || topic.isEmpty()) {
            appendLog(LogUtil.warn("[KAFKA] 请选择或输入目标 Topic"));
            JOptionPane.showMessageDialog(this, "请选择或输入目标 Topic", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String payload = payloadArea.getText().trim();
        if (payload.isEmpty()) {
            appendLog(LogUtil.warn("[KAFKA] 消息内容不能为空"));
            JOptionPane.showMessageDialog(this, "消息内容不能为空", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 必须是合法 JSON，且不允许空对象 {} / 空数组 []
        String invalidReason = validateJsonPayload(payload);
        if (invalidReason != null) {
            appendLog(LogUtil.warn("[KAFKA] 消息校验失败：" + invalidReason));
            JOptionPane.showMessageDialog(this, "消息内容错误！" + invalidReason, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String keyText = keyField.getText().trim();
        final String key = keyText.isEmpty() ? null : keyText;
        // 解析分区选择：首项「自动」表示按 Key 哈希 / 轮询分配
        Integer partitionValue = null;
        if (partitionCombo.getSelectedIndex() > 0) {
            try {
                partitionValue = Integer.parseInt(String.valueOf(partitionCombo.getSelectedItem()).trim());
            } catch (NumberFormatException ignore) {
                // 分区号非法时按自动分配处理
                partitionValue = null;
            }
        }
        final Integer partition = partitionValue;
        // 解析消息头：JSON 对象形式，留空表示不携带
        final Map<String, String> headers = new LinkedHashMap<>();
        String headersText = headersField.getText().trim();
        if (!headersText.isEmpty()) {
            JsonNode headerRoot;
            try {
                headerRoot = PAYLOAD_JSON_MAPPER.readTree(headersText);
            } catch (Exception ex) {
                appendLog(LogUtil.warn("[KAFKA] 消息头不是合法 JSON：" + ex.getMessage()));
                setStatus("消息头不是合法 JSON");
                return;
            }
            if (!headerRoot.isObject()) {
                appendLog(LogUtil.warn("[KAFKA] 消息头必须是 JSON 对象，如 {\"traceId\":\"abc\",\"source\":\"client\"}"));
                setStatus("消息头必须是 JSON 对象");
                return;
            }
            headerRoot.fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText("")));
        }
        String headerSuffix = headers.isEmpty() ? "" : "，消息头 " + headers.size() + " 个";
        // 推送前二次确认：推送后无法撤回
        int messageCount = 1;
        try {
            JsonNode payloadRoot = PAYLOAD_JSON_MAPPER.readTree(payload);
            messageCount = payloadRoot.isArray() ? payloadRoot.size() : 1;
        } catch (Exception ignore) {
            // 校验已通过，此处解析异常按 1 条处理
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定推送到队列？推送后无法撤回。\n\n" + "连接：" + conn.getName() + "（" + conn.getBootstrapServers() + "）\n" + "Topic：" + topic + "\n"
                        + "分区：" + (partition == null ? "自动" : String.valueOf(partition)) + "\n" + "消息数量：" + messageCount + " 条\n" + "消息Key："
                        + (key == null ? "（空，按分区轮询分配）" : key) + "\n" + "消息头：" + (headers.isEmpty() ? "无" : headers.size() + " 个"),
                "推送到队列", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            appendLog(LogUtil.warn("[KAFKA] 推送已取消"));
            setStatus("推送已取消");
            return;
        }
        appendLog(LogUtil.warn(
                "[KAFKA] 推送开始 → " + conn.getName() + " / <b>" + topic + "</b> / 分区 " + (partition == null ? "自动" : partition) + (key != null ?
                        "（key=" + key + "）" : "") + headerSuffix));
        pushBtn.setEnabled(false);
        setStatus("正在推送 ...");
        
        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return KafkaPushService.sendJson(conn, topic, partition, key, headers, payload, this::publish);
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendLog(chunk);
                }
            }
            
            @Override
            protected void done() {
                pushBtn.setEnabled(true);
                try {
                    int count = get();
                    appendLog(LogUtil.success("[KAFKA] 推送完成，共 " + count + " 条消息 → Topic: <b>" + topic + "</b>"));
                    setStatus("推送完成：共 " + count + " 条消息");
                } catch (Exception ex) {
                    appendLog(LogUtil.failed("[KAFKA] 推送失败：" + KafkaPushService.friendlyError(ex)));
                    setStatus("推送失败：" + KafkaPushService.friendlyError(ex));
                }
            }
        }.execute();
    }
    
    private KafkaConnection getSelectedConnection() {
        return connectionCombo.getSelectedItem() instanceof KafkaConnection selected ? selected : null;
    }
    
    /**
     * 获取顶部栏当前选中的 Topic（消息推送与 Topic 查询页签共用）
     */
    private String getSelectedTopic() {
        return topicCombo.getSelectedItem() instanceof String selected ? selected.trim() : null;
    }
    
    public void appendLog(String message) {
        LogUtil.appendLog(message, logArea);
    }
    
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}
