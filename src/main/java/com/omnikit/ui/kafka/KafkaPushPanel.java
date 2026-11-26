package com.omnikit.ui.kafka;

import com.omnikit.components.AbsJPanl;
import com.omnikit.components.ButtonFactory;
import com.omnikit.components.ChildLayoutPanel;
import com.omnikit.components.FilterComboBox;
import com.omnikit.core.KafkaPushService;
import com.omnikit.model.KafkaConnection;
import com.omnikit.model.KafkaTopic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.ConfigUtil;
import com.omnikit.util.LogUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Kafka 消息队列数据推送内容面板 — 参照 AI 训练的环境选择： 顶栏选择 Kafka 连接（可在连接管理中维护），顶栏 Topic 选择由消息推送与 Topic 数据查询两个页签共用， 输入 JSON 消息推送，并在底部日志区输出推送过程
 */
public class KafkaPushPanel extends AbsJPanl {
    
    public static final String NAME = "KAFKA";
    
    // ── UI 组件 ──
    private JComboBox<KafkaConnection> connectionCombo;
    
    private FilterComboBox<String> topicCombo;
    
    private JTextField keyField;
    
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
        // 选中 Topic 后按连接维度记忆，下次打开默认选中该 Topic
        topicCombo.addActionListener(e -> saveSelectedTopic());
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
        gbc.anchor = GridBagConstraints.NORTH;
        formPanel.add(new JLabel("JSON内容："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.92;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JScrollPane payloadScroll = new JScrollPane(payloadArea);
        payloadScroll.setPreferredSize(new Dimension(200, 160));
        formPanel.add(payloadScroll, gbc);
        
        JLabel hintLabel = new JLabel("支持单个 JSON 对象（推送一条）或 JSON 数组（逐条推送），例如 [{...},{...}]");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(UiConstants.FONT_SANS_10);
        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(hintLabel, gbc);
        
        ChildLayoutPanel actionPanel = new ChildLayoutPanel();
        JButton formatBtn = ButtonFactory.createSecondary("格式化 JSON");
        formatBtn.addActionListener(e -> formatJson());
        JButton clearInputBtn = ButtonFactory.createDestructive("清空输入");
        clearInputBtn.addActionListener(e -> payloadArea.setText(""));
        pushBtn = ButtonFactory.createPrimary("推送到队列");
        pushBtn.addActionListener(e -> pushMessage());
        actionPanel.add(formatBtn);
        actionPanel.add(clearInputBtn);
        actionPanel.add(pushBtn);
        gbc.gridy = 3;
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(actionPanel, gbc);
        
        // ── 底部：推送日志 ──
        logArea = LogUtil.createLogArea();
        LogUtil.attachClearPopupMenu(logArea);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("推送日志（右键清空）"), new EmptyBorder(0, 1, 0, 1)));
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
            // 恢复上次选中
            for (KafkaConnection config : configs) {
                if (config.getName().equals(previousName)) {
                    connectionCombo.setSelectedItem(config);
                    break;
                }
            }
            if (connectionCombo.getSelectedIndex() < 0 && configs.size() == 1) {
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
            topicCombo.removeAllItems();
            return;
        }
        List<String> topicNames = loadTopicsIntoCombo(conn);
        // 连接选中/切换成功后自动获取集群已有 Topic（静默合并，失败不打断）
        autoFetchTopics(conn);
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
        return topicNames;
    }
    
    /**
     * 保存推送页签当前选中的 Topic（按连接维度记忆，重建下拉期间跳过）
     */
    private void saveSelectedTopic() {
        if (suppressTopicEvents) {
            return;
        }
        KafkaConnection conn = getSelectedConnection();
        if (conn != null && conn.getId() != null
                && topicCombo.getSelectedItem() instanceof String topic && !topic.isBlank()) {
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
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return KafkaPushService.listTopics(conn);
            }
            
            @Override
            protected void done() {
                // 结果返回时用户已切换到其他连接，本次结果不适用
                if (getSelectedConnection() != conn) {
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
        }.execute();
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
    
    // ────────── 消息推送 ──────────
    
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
            return;
        }
        String payload = payloadArea.getText().trim();
        if (payload.isEmpty()) {
            appendLog(LogUtil.warn("[KAFKA] 消息内容不能为空"));
            return;
        }
        // 必须是合法 JSON，且不允许空对象 {} / 空数组 []
        String invalidReason = validateJsonPayload(payload);
        if (invalidReason != null) {
            appendLog(LogUtil.warn("[KAFKA] 消息校验失败：" + invalidReason));
            return;
        }
        String keyText = keyField.getText().trim();
        final String key = keyText.isEmpty() ? null : keyText;
        appendLog(LogUtil.warn("[KAFKA] 推送开始 → " + conn.getName() + " / <b>" + topic + "</b>" + (key != null ? "（key=" + key + "）" : "")));
        pushBtn.setEnabled(false);
        setStatus("正在推送 ...");
        
        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return KafkaPushService.sendJson(conn, topic, key, payload, this::publish);
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
