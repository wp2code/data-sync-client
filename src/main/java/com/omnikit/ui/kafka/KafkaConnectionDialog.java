package com.omnikit.ui.kafka;

import com.omnikit.components.ButtonFactory;
import com.omnikit.components.ChildLayoutPanel;
import com.omnikit.components.CustomTextField;
import com.omnikit.components.FullscreenJDialog;
import com.omnikit.components.OptionJPanel;
import com.omnikit.core.KafkaPushService;
import com.omnikit.model.KafkaConnection;
import com.omnikit.model.KafkaTopic;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.ConfigUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Kafka 连接管理对话框 — 左右布局：左侧为连接列表（卡片式），右侧为连接编辑表单、 附加参数、当前连接的 Topic 管理（增删）与连接测试
 */
public class KafkaConnectionDialog extends FullscreenJDialog {
    
    private final List<OptionJPanel> itemPanelList = new ArrayList<>();
    
    private final JPanel connectionListPanel;
    
    private KafkaConnection selectedConnection;
    
    private OptionJPanel selectedPanel;
    
    // ── 连接编辑表单 ──
    private final JTextField nameField;
    
    private final JTextField serversField;
    
    private final JTextArea extraConfigArea;
    
    private final JTextField remarkField;
    
    // ── Topic 管理 ──
    private final DefaultListModel<String> topicListModel = new DefaultListModel<>();
    
    private final JList<String> topicList;
    
    private final JTextField topicInputField;
    
    private final JButton addTopicBtn;
    
    private final JButton deleteTopicBtn;
    
    /**
     * 删除连接按钮：仅在左侧列表选中连接时可见
     */
    private final JButton deleteBtn;
    
    private final JLabel statusLabel;
    
    public KafkaConnectionDialog(java.awt.Frame owner) {
        super("KAFKA_CONN", owner, "Kafka 连接管理", true, 960, 640);
        
        getRootPane().setBorder(new EmptyBorder(16, 16, 16, 16));
        
        // ── 左侧：连接列表 ──
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("连接列表"));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        
        connectionListPanel = new JPanel();
        connectionListPanel.setLayout(new BoxLayout(connectionListPanel, BoxLayout.Y_AXIS));
        connectionListPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        connectionListPanel.setBackground(UIManager.getColor("Panel.background"));
        connectionListPanel.setFocusable(true);
        JScrollPane listScroll = new JScrollPane(connectionListPanel);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        
        JButton newBtn = ButtonFactory.createPrimary("新增连接");
        newBtn.addActionListener(e -> startNew());
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leftBtnPanel.add(newBtn);
        leftPanel.add(leftBtnPanel, BorderLayout.SOUTH);
        
        // ── 右侧：连接表单 ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("连接详情"), new EmptyBorder(8, 8, 8, 8)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 12, 8, 12);
        
        nameField = new JTextField(20);
        serversField = new JTextField("localhost:9092", 20);
        extraConfigArea = new JTextArea(4, 20);
        extraConfigArea.setFont(UiConstants.FONT_MONO_12);
        extraConfigArea.setLineWrap(true);
        extraConfigArea.setWrapStyleWord(true);
        remarkField = new JTextField(20);
        
        int row = 0;
        addFormRow(formPanel, gbc, row++, "  连接名称：", nameField);
        addFormRow(formPanel, gbc, row++, "集群地址：", serversField);
        addFormRow(formPanel, gbc, row++, "      备注：", remarkField);
        // 附加参数（JSON）单独占一行，可拉伸
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.weightx = 0.12;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("附加参数："), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.88;
        gbc.anchor = GridBagConstraints.WEST;
        JScrollPane extraScroll = new JScrollPane(extraConfigArea);
        extraScroll.setPreferredSize(new Dimension(200, 80));
        formPanel.add(extraScroll, gbc);
        JLabel extraHint = new JLabel("生产者/管理客户端附加参数 JSON，如 {\"security.protocol\":\"SASL_PLAINTEXT\",\"sasl.jaas.config\":\"...\"}");
        extraHint.setForeground(Color.GRAY);
        extraHint.setFont(UiConstants.FONT_SANS_10);
        gbc.gridy = row++;
        formPanel.add(extraHint, gbc);
        
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createVerticalGlue(), gbc);
        
        // ── Topic 管理（当前选中连接） ──
        JPanel topicPanel = new JPanel(new BorderLayout(5, 5));
        topicPanel.setBorder(BorderFactory.createCompoundBorder(new TitledBorder("Topic 管理（当前连接）"), new EmptyBorder(6, 8, 8, 8)));
        topicList = new JList<>(topicListModel);
        topicList.setFont(UiConstants.FONT_MONO_12);
        JScrollPane topicScroll = new JScrollPane(topicList);
        topicPanel.add(topicScroll, BorderLayout.CENTER);
        
        JPanel topicEditPanel = new JPanel(new BorderLayout(5, 0));
        topicInputField = new CustomTextField("输入 Topic 名称，如 order-data-sync");
        addTopicBtn = ButtonFactory.createSuccess("添加");
        deleteTopicBtn = ButtonFactory.createDestructive("删除选中");
        topicEditPanel.add(topicInputField, BorderLayout.CENTER);
        JPanel topicBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        topicBtnPanel.add(deleteTopicBtn);
        topicBtnPanel.add(addTopicBtn);
        topicEditPanel.add(topicBtnPanel, BorderLayout.EAST);
        topicPanel.add(topicEditPanel, BorderLayout.SOUTH);
        
        // ── 右侧底部：操作按钮 ──
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 4));
        ChildLayoutPanel btnPanel = new ChildLayoutPanel();
        JButton testBtn = ButtonFactory.createSecondary("测试连接");
        deleteBtn = ButtonFactory.createDestructive("删除");
        // 仅选中连接时才显示删除按钮，避免误删
        deleteBtn.setVisible(false);
        JButton saveBtn = ButtonFactory.createPrimary("保存");
        btnPanel.add(deleteBtn);
        btnPanel.add(testBtn);
        btnPanel.add(saveBtn);
        bottomPanel.add(btnPanel, BorderLayout.SOUTH);
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        
        // ── 右侧整体：表单（上）+ Topic 管理（下）──
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formPanel, topicPanel);
        rightSplit.setResizeWeight(0.6);
        rightSplit.setDividerSize(6);
        rightSplit.setBorder(null);
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.add(rightSplit, BorderLayout.CENTER);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        // ── 主布局：左右分割 ──
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplitPane.setDividerLocation(270);
        mainSplitPane.setResizeWeight(0.25);
        mainSplitPane.setDividerSize(6);
        add(mainSplitPane, BorderLayout.CENTER);
        
        // ── 事件绑定 ──
        saveBtn.addActionListener(e -> saveCurrent());
        deleteBtn.addActionListener(e -> deleteCurrent());
        testBtn.addActionListener(e -> testConnection());
        addTopicBtn.addActionListener(e -> addTopic());
        deleteTopicBtn.addActionListener(e -> deleteTopic());
        
        refreshList();
    }
    
    private void addFormRow(JPanel formPanel, GridBagConstraints gbc, int row, String labelText, java.awt.Component field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.12;
        gbc.ipady = 4;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.88;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(field, gbc);
        gbc.ipady = 0;
    }
    
    // ────────── 连接列表 ──────────
    
    private void refreshList() {
        Long selectedId = selectedConnection != null ? selectedConnection.getId() : null;
        connectionListPanel.removeAll();
        itemPanelList.clear();
        
        List<KafkaConnection> all = ConfigUtil.loadKafkaConnections();
        if (all.isEmpty()) {
            JLabel emptyLabel = new JLabel("（暂无 Kafka 连接）", javax.swing.SwingConstants.CENTER);
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            connectionListPanel.add(emptyLabel);
        } else {
            for (KafkaConnection conn : all) {
                OptionJPanel panel = createItemPanel(conn);
                itemPanelList.add(panel);
                connectionListPanel.add(panel);
                connectionListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }
        connectionListPanel.revalidate();
        connectionListPanel.repaint();
        
        // 恢复选中项
        if (selectedId != null) {
            all.stream().filter(c -> selectedId.equals(c.getId())).findFirst().ifPresent(this::selectConnection);
        }
    }
    
    private OptionJPanel createItemPanel(KafkaConnection conn) {
        String remark = conn.getRemark() != null && !conn.getRemark().isBlank() ? conn.getRemark() : conn.getBootstrapServers();
        OptionJPanel panel = new OptionJPanel(conn.getName(), remark, null);
        panel.setData(conn);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.fitPreferredHeight();
        panel.setOnClick(() -> selectConnection(conn));
        return panel;
    }
    
    private void selectConnection(KafkaConnection conn) {
        selectedConnection = conn;
        deleteBtn.setVisible(true);
        for (OptionJPanel panel : itemPanelList) {
            panel.setSelected(panel.getData() == conn);
            if (panel.getData() == conn) {
                selectedPanel = panel;
            }
        }
        nameField.setText(conn.getName());
        serversField.setText(conn.getBootstrapServers());
        extraConfigArea.setText(conn.getExtraConfig() == null ? "" : conn.getExtraConfig());
        remarkField.setText(conn.getRemark() == null ? "" : conn.getRemark());
        refreshTopics();
    }
    
    private void startNew() {
        selectedConnection = null;
        selectedPanel = null;
        deleteBtn.setVisible(false);
        for (OptionJPanel panel : itemPanelList) {
            panel.setSelected(false);
        }
        nameField.setText("");
        serversField.setText("localhost:9092");
        extraConfigArea.setText("");
        remarkField.setText("");
        topicListModel.clear();
        setStatus("请填写新连接信息并保存（Topic 需先保存连接后再管理）");
        nameField.requestFocusInWindow();
    }
    
    // ────────── Topic 管理 ──────────
    
    private void refreshTopics() {
        topicListModel.clear();
        if (selectedConnection == null || selectedConnection.getId() == null) {
            return;
        }
        for (KafkaTopic topic : ConfigUtil.loadKafkaTopics(selectedConnection.getId())) {
            topicListModel.addElement(topic.getTopicName());
        }
    }
    
    private void addTopic() {
        if (selectedConnection == null || selectedConnection.getId() == null) {
            setStatus("请先保存连接，再管理 Topic");
            return;
        }
        String topicName = topicInputField.getText().trim();
        if (topicName.isEmpty()) {
            setStatus("请输入 Topic 名称");
            return;
        }
        if (ConfigUtil.saveKafkaTopic(selectedConnection.getId(), topicName, null)) {
            setStatus("Topic 添加成功：" + topicName);
        } else {
            setStatus("Topic 添加失败（可能已存在）：" + topicName);
        }
        topicInputField.setText("");
        refreshTopics();
    }
    
    private void deleteTopic() {
        String topicName = topicList.getSelectedValue();
        if (topicName == null) {
            setStatus("请先在 Topic 列表中选中要删除的 Topic");
            return;
        }
        if (selectedConnection == null || selectedConnection.getId() == null) {
            return;
        }
        Long topicId = ConfigUtil.findKafkaTopicId(selectedConnection.getId(), topicName);
        if (topicId != null && ConfigUtil.deleteKafkaTopic(topicId)) {
            setStatus("Topic 删除成功：" + topicName);
        } else {
            setStatus("Topic 删除失败：" + topicName);
        }
        refreshTopics();
    }
    
    // ────────── 保存 / 删除 / 测试 ──────────
    
    private void saveCurrent() {
        String name = nameField.getText().trim();
        String servers = serversField.getText().trim();
        if (name.isEmpty()) {
            setStatus("连接名称不能为空");
            return;
        }
        if (servers.isEmpty()) {
            setStatus("集群地址不能为空");
            return;
        }
        KafkaConnection conn = selectedConnection != null ? selectedConnection : new KafkaConnection();
        conn.setName(name);
        conn.setBootstrapServers(servers);
        String extra = extraConfigArea.getText().trim();
        conn.setExtraConfig(extra.isEmpty() ? null : extra);
        String remark = remarkField.getText().trim();
        conn.setRemark(remark.isEmpty() ? null : remark);
        
        if (conn.getId() == null) {
            long id = ConfigUtil.saveKafkaConnection(conn);
            if (id > 0) {
                conn.setId(id);
                setStatus("连接保存成功：" + name);
            } else {
                setStatus("连接保存失败（名称可能已存在）：" + name);
                return;
            }
        } else {
            if (!ConfigUtil.updateKafkaConnection(conn)) {
                setStatus("连接更新失败：" + name);
                return;
            }
            setStatus("连接更新成功：" + name);
        }
        refreshList();
    }
    
    private void deleteCurrent() {
        if (selectedConnection == null || selectedConnection.getId() == null) {
            setStatus("请先在左侧选中要删除的连接");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定删除连接「" + selectedConnection.getName() + "」？\n该连接下的 Topic 配置将一并删除。", "删除确认",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        if (ConfigUtil.deleteKafkaConnection(selectedConnection.getId())) {
            setStatus("连接删除成功：" + selectedConnection.getName());
            selectedConnection = null;
            startNew();
            refreshList();
        } else {
            setStatus("连接删除失败：" + selectedConnection.getName());
        }
    }
    
    private void testConnection() {
        String servers = serversField.getText().trim();
        if (servers.isEmpty()) {
            setStatus("集群地址不能为空");
            return;
        }
        // 测试连接始终用表单当前值构建临时配置，不影响已保存的连接
        final KafkaConnection target = new KafkaConnection();
        target.setBootstrapServers(servers);
        String extra = extraConfigArea.getText().trim();
        target.setExtraConfig(extra.isEmpty() ? null : extra);
        
        setStatus("正在连接 " + servers + " ...");
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
                    // 连接成功后自动获取集群已有 Topic，合并进当前连接的 Topic 配置
                    if (selectedConnection != null && selectedConnection.getId() != null) {
                        for (String topicName : topics) {
                            if (ConfigUtil.saveKafkaTopic(selectedConnection.getId(), topicName, null)) {
                                added++;
                            }
                        }
                        if (added > 0) {
                            refreshTopics();
                        }
                    }
                    setStatus("连接成功，集群共有 " + topics.size() + " 个 Topic，新增保存 " + added + " 个");
                } catch (Exception ex) {
                    setStatus("连接失败：" + KafkaPushService.friendlyError(ex));
                }
            }
        }.execute();
    }
    
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}
