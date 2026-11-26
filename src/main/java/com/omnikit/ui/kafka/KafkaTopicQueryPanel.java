package com.omnikit.ui.kafka;

import com.omnikit.components.ButtonFactory;
import com.omnikit.components.ChildLayoutPanel;
import com.omnikit.core.KafkaPushService;
import com.omnikit.model.KafkaConnection;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.LogUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.event.DocumentListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Topic 数据查询组件（独立页签）— Topic 由外层面板顶部的共用选择框决定， 按最新/最早查询指定条数，结果以表格展示，表头：Partition | Offset | Key | Value | Timestamp
 */
public class KafkaTopicQueryPanel extends JPanel {
    
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 获取当前选中的 Kafka 连接（由外层面板提供）
     */
    private final Supplier<KafkaConnection> connectionSupplier;
    
    /**
     * 获取当前选中的 Topic（与消息推送页签共用外层面板顶部的选择框）
     */
    private final Supplier<String> topicSupplier;
    
    /**
     * 日志回调（写入外层面板的日志区）
     */
    private final Consumer<String> logConsumer;
    
    // ── UI 组件 ──
    private JSpinner limitSpinner;
    
    private JComboBox<String> positionCombo;
    
    private JButton queryBtn;
    
    // ── Key / Value 筛选 ──
    private JTextField keyFilterField;
    
    private JTextField valueFilterField;
    
    /**
     * 最近一次查询到的全部消息（筛选前的完整数据）
     */
    private List<KafkaPushService.KafkaMessage> allMessages = List.of();
    
    private DefaultTableModel tableModel;
    
    private JTable messageTable;
    
    /**
     * 表格标题栏：查询后动态携带当前 Topic，突出显示结果来源
     */
    private TitledBorder listBorder;
    
    private JScrollPane listScroll;
    
    private JLabel statusLabel;
    
    public KafkaTopicQueryPanel(Supplier<KafkaConnection> connectionSupplier, Supplier<String> topicSupplier, Consumer<String> logConsumer) {
        this.connectionSupplier = connectionSupplier;
        this.topicSupplier = topicSupplier;
        this.logConsumer = logConsumer;
        initUI();
    }
    
    private void initUI() {
        setLayout(new BorderLayout(6, 6));
        // 页签内容与页签线之间留出间距，避免操作组件紧贴页签线
        setBorder(new EmptyBorder(10, 12, 4, 12));
        
        // ── 顶栏单行：查询条件 + Key / Value 筛选（Topic 取自外层顶部共用选择框） ──
        limitSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 1000, 10));
        limitSpinner.setPreferredSize(new Dimension(64, 28));
        positionCombo = new JComboBox<>(new String[]{"最新", "最早"});
        positionCombo.setPreferredSize(new Dimension(64, 28));
        queryBtn = ButtonFactory.createPrimary("查询数据");
        queryBtn.addActionListener(e -> queryTopicData());
        
        keyFilterField = new JTextField(12);
        keyFilterField.setToolTipText("按 Key 包含关键字筛选（不区分大小写）");
        valueFilterField = new JTextField(16);
        valueFilterField.setToolTipText("按 Value 包含关键字筛选（不区分大小写）");
        DocumentListener filterListener = new DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        };
        keyFilterField.getDocument().addDocumentListener(filterListener);
        valueFilterField.getDocument().addDocumentListener(filterListener);
        JButton clearFilterBtn = ButtonFactory.createLink("清空筛选");
        clearFilterBtn.addActionListener(e -> {
            keyFilterField.setText("");
            valueFilterField.setText("");
        });
        
        ChildLayoutPanel headerRow = new ChildLayoutPanel(new Insets(0, 4, 4, 4), ChildLayoutPanel.LayoutType.RIGHT);
        headerRow.add(new JLabel("查询："));
        headerRow.add(limitSpinner);
        headerRow.add(new JLabel("条"));
        headerRow.add(positionCombo);
        headerRow.add(queryBtn);
        headerRow.add(new JLabel("筛选 Key："));
        headerRow.add(keyFilterField);
        headerRow.add(new JLabel("Value："));
        headerRow.add(valueFilterField);
        headerRow.add(clearFilterBtn);
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerRow, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);
        
        // ── 中部：消息表格 ──
        tableModel = new DefaultTableModel(new Object[]{"Partition", "Offset", "Key", "Value", "Timestamp"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        messageTable = new JTable(tableModel);
        messageTable.setFont(UiConstants.FONT_MONO_12);
        messageTable.setRowHeight(24);
        messageTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        // 点击表头排序（Timestamp 为定长格式化文本，字典序即时间序）
        messageTable.setAutoCreateRowSorter(true);
        // 允许按单元格选中，配合 Ctrl+C 复制选中内容（如 Key）
        messageTable.setRowSelectionAllowed(true);
        messageTable.setColumnSelectionAllowed(true);
        messageTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        messageTable.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("control C"),
                "copySelectedCell");
        messageTable.getActionMap().put("copySelectedCell", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int viewRow = messageTable.getSelectedRow();
                int viewCol = messageTable.getSelectedColumn();
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                Object cellValue = messageTable.getValueAt(viewRow, viewCol);
                if (cellValue != null) {
                    java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(cellValue.toString());
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                    setStatus("已复制选中单元格内容");
                }
            }
        });
        // 双击行弹出 Value 详情（JSON 格式化 / 文本两种显示）
        messageTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int row = messageTable.rowAtPoint(e.getPoint());
                int col = messageTable.columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return;
                }
                int modelCol = messageTable.convertColumnIndexToModel(col);
                if (modelCol == 2) {
                    // 双击 Key 列：直接复制该单元格内容
                    Object keyValue = tableModel.getValueAt(messageTable.convertRowIndexToModel(row), 2);
                    if (keyValue != null) {
                        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(keyValue.toString());
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                        setStatus("已复制 Key：" + keyValue);
                    }
                    return;
                }
                // 仅双击 Value 列弹出详情，其余列不弹窗
                if (modelCol != 3) {
                    return;
                }
                int modelRow = messageTable.convertRowIndexToModel(row);
                Object offsetValue = tableModel.getValueAt(modelRow, 1);
                Object value = tableModel.getValueAt(modelRow, 3);
                java.awt.Frame owner = (java.awt.Frame) javax.swing.SwingUtilities.getAncestorOfClass(java.awt.Frame.class,
                        KafkaTopicQueryPanel.this);
                new KafkaValueDialog(owner, "消息内容（offset=" + offsetValue + "）", value == null ? null : value.toString())
                        .setVisible(true);
            }
        });
        setColumnWidths();
        // Timestamp 列表头始终显示排序箭头：未按该列排序时显示升序箭头提示可点击
        TableCellRenderer defaultHeaderRenderer = messageTable.getTableHeader().getDefaultRenderer();
        Icon sortHintIcon = createSortHintIcon();
        messageTable.getColumnModel().getColumn(4).setHeaderRenderer((table, value, isSelected, hasFocus, row, column) -> {
            Component comp = defaultHeaderRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelColumn = table.convertColumnIndexToModel(column);
            boolean sortedHere = false;
            RowSorter<?> rowSorter = table.getRowSorter();
            if (rowSorter != null) {
                for (RowSorter.SortKey sortKey : rowSorter.getSortKeys()) {
                    if (sortKey.getColumn() == modelColumn && sortKey.getSortOrder() != SortOrder.UNSORTED) {
                        sortedHere = true;
                        break;
                    }
                }
            }
            if (!sortedHere && comp instanceof JLabel label) {
                label.setIcon(sortHintIcon);
                label.setHorizontalTextPosition(SwingConstants.LEFT);
            }
            return comp;
        });
        JScrollPane scroll = new JScrollPane(messageTable);
        // 表格标题动态携带当前查询的 Topic，突出显示本次结果来自哪个 Topic
        listBorder = BorderFactory.createTitledBorder("消息列表（双击 Value 列查看详情，双击 Key 列复制）");
        listBorder.setTitleFont(UiConstants.FONT_SANS_12.deriveFont(Font.BOLD));
        scroll.setBorder(BorderFactory.createCompoundBorder(listBorder, new EmptyBorder(0, 1, 0, 1)));
        listScroll = scroll;
        add(scroll, BorderLayout.CENTER);
        
        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);
    }
    
    /**
     * 排序提示箭头：优先使用主题自带升序图标，缺失时回退为自绘小三角
     */
    private static Icon createSortHintIcon() {
        Icon icon = UIManager.getIcon("Table.ascendingSortIcon");
        if (icon != null) {
            return icon;
        }
        return new Icon() {
            @Override
            public int getIconWidth() {
                return 8;
            }
            
            @Override
            public int getIconHeight() {
                return 8;
            }
            
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(java.awt.Color.GRAY);
                int cx = x + getIconWidth() / 2;
                g.drawLine(cx - 3, y + 5, cx, y + 2);
                g.drawLine(cx, y + 2, cx + 3, y + 5);
                g.drawLine(cx - 3, y + 6, cx + 3, y + 6);
            }
        };
    }
    
    private void setColumnWidths() {
        int[] widths = {80, 100, 160, 500, 180};
        for (int i = 0; i < widths.length && i < messageTable.getColumnCount(); i++) {
            messageTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
    
    private void queryTopicData() {
        KafkaConnection conn = connectionSupplier.get();
        if (conn == null || conn.getId() == null) {
            logConsumer.accept(LogUtil.warn("[KAFKA] 请先在顶部选择 Kafka 连接"));
            return;
        }
        String topic = topicSupplier.get();
        if (topic == null || topic.isEmpty()) {
            logConsumer.accept(LogUtil.warn("[KAFKA] 请在顶部选择或输入要查询的 Topic"));
            return;
        }
        int limit = (Integer) limitSpinner.getValue();
        boolean latest = positionCombo.getSelectedIndex() == 0;
        // 突出显示本次查询使用的 Topic：日志加粗 + 表格标题栏携带 Topic
        logConsumer.accept(LogUtil.warn("[KAFKA] 查询 Topic 数据 → <b>" + topic + "</b>（" + (latest ? "最新" : "最早") + " " + limit + " 条）"));
        listBorder.setTitle("消息列表 — Topic: " + topic);
        listScroll.repaint();
        queryBtn.setEnabled(false);
        setStatus("正在查询 Topic 数据 ...");
        
        new SwingWorker<List<KafkaPushService.KafkaMessage>, Void>() {
            @Override
            protected List<KafkaPushService.KafkaMessage> doInBackground() throws Exception {
                return KafkaPushService.queryTopicMessages(conn, topic, limit, latest);
            }
            
            @Override
            protected void done() {
                queryBtn.setEnabled(true);
                try {
                    List<KafkaPushService.KafkaMessage> messages = get();
                    fillTable(messages);
                    logConsumer.accept(LogUtil.success("[KAFKA] 查询完成，共 " + messages.size() + " 条消息 → Topic: <b>" + topic + "</b>"));
                    setStatus("查询完成 → Topic: " + topic + "，共 " + messages.size() + " 条消息");
                } catch (Exception ex) {
                    logConsumer.accept(LogUtil.failed("[KAFKA] 查询失败：" + KafkaPushService.friendlyError(ex)));
                    setStatus("查询失败：" + ex.getMessage());
                }
            }
        }.execute();
    }
    
    private void fillTable(List<KafkaPushService.KafkaMessage> messages) {
        allMessages = messages;
        applyFilter();
        // 点击查询后重置全部排序：默认按 Timestamp 降序（Timestamp 为定长格式化文本，字典序即时间序）
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        messageTable.setRowSorter(sorter);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(4, SortOrder.DESCENDING)));
        sorter.sort();
    }
    
    /**
     * 按筛选关键字重建表格数据：Key / Value 不区分大小写的包含匹配，任一为空则该维度不过滤
     */
    private void applyFilter() {
        String keyText = keyFilterField.getText().trim().toLowerCase();
        String valueText = valueFilterField.getText().trim().toLowerCase();
        tableModel.setRowCount(0);
        int shown = 0;
        for (KafkaPushService.KafkaMessage message : allMessages) {
            boolean keyOk = keyText.isEmpty() || (message.key() != null && message.key().toLowerCase().contains(keyText));
            boolean valueOk = valueText.isEmpty() || (message.value() != null && message.value().toLowerCase().contains(valueText));
            if (keyOk && valueOk) {
                tableModel.addRow(new Object[]{
                        message.partition(),
                        message.offset(),
                        message.key(),
                        message.value(),
                        TIME_FORMAT.format(new Date(message.timestamp()))
                });
                shown++;
            }
        }
        if (shown != allMessages.size()) {
            setStatus("显示 " + shown + " / " + allMessages.size() + " 条（筛选生效）");
        } else if (!allMessages.isEmpty()) {
            setStatus("共 " + allMessages.size() + " 条消息");
        }
    }
    
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}
