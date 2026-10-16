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
import com.datasync.model.AiEnvConfig;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 训练耗时查询弹窗（问题列表工具栏「训练耗时」触发）
 * <p>
 * 按批次号调用 GET 接口 training-cast-time 实时查询训练开始 / 最新时间与耗时，支持手动查询与自动刷新（1 / 2 / 5 / 10 秒或手动输入间隔），
 * 自动刷新可随时手动停止；查询在后台线程执行，结果实时刷新展示。
 *
 * @author liuweiping
 * @date 2026-10-12
 **/
public class TrainingCastTimeDialog extends JDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(TrainingCastTimeDialog.class);
    
    /**
     * 训练时间展示格式
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 最后刷新时间展示格式（仅时分秒）
     */
    private static final DateTimeFormatter TIME_ONLY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * 自动刷新默认间隔（秒）
     */
    private static final int DEFAULT_REFRESH_SECONDS = 5;
    
    /**
     * 自动刷新间隔可选值（秒，支持下拉选择或手动输入）
     */
    private static final String[] REFRESH_INTERVAL_OPTIONS = {"1", "2", "5", "10"};
    
    /**
     * 连续查询失败自动停止阈值（连续失败超过该次数时停止自动刷新）
     */
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    
    // ── 字段 ──
    private AiEnvConfig envConfig;
    
    /**
     * 批次号输入框
     */
    private CustomTextField trainingNoField;
    
    /**
     * 自动刷新间隔下拉（可编辑）
     */
    private JComboBox<String> intervalCombo;
    
    /**
     * 自动刷新开关按钮（开始 / 停止自动刷新）
     */
    private JButton autoRefreshBtn;
    
    /**
     * 手动查询按钮
     */
    private JButton queryBtn;
    
    /**
     * 状态提示（查询中 / 成功 / 失败 + 最后刷新时间）
     */
    private JLabel statusLabel;
    
    /**
     * 最后刷新时间展示（查询结果区第一行，仅时分秒）
     */
    private JLabel lastRefreshLabel;
    
    /**
     * 开始时间（minInitTime）展示
     */
    private JLabel startValueLabel;
    
    /**
     * 最新时间（maxLastTime）展示
     */
    private JLabel lastValueLabel;
    
    /**
     * 耗时（maxLastTime - minInitTime，秒）展示
     */
    private JLabel costValueLabel;

    /**
     * 耗时多单位换算展示（分 / 小时）
     */
    private JLabel costDetailLabel;

    /**
     * 训练数量（totalCount）展示
     */
    private JLabel totalCountValueLabel;
    
    /**
     * 自动刷新定时器（Swing Timer，EDT 触发，查询本身在后台线程执行）
     */
    private Timer refreshTimer;
    
    /**
     * 查询单飞标记：上一次查询未返回时跳过新查询，避免自动刷新堆叠请求
     */
    private boolean querying = false;
    
    /**
     * 连续查询失败次数（查询成功后清零，超过阈值停止自动刷新）
     */
    private int consecutiveErrorCount = 0;
    
    /**
     * 自动刷新监听器（回调在 EDT 执行，供列表页在分页栏实时展示训练耗时）
     */
    @Setter
    private AutoRefreshListener autoRefreshListener;
    
    /**
     * 批次训练状态校验器：批次存在待训练 / 训练中 / 训练已提交状态问题时返回 true（自动刷新仅在该条件满足时才允许开启）
     */
    @Setter
    private java.util.function.Predicate<String> batchInProgressChecker;
    
    /**
     * 当前自动刷新的批次号（停止回调时回传）
     */
    private String autoRefreshTrainingNo;
    
    /**
     * 自动刷新监听：每次自动刷新成功回调最新耗时，停止自动刷新时回调通知
     */
    public interface AutoRefreshListener {
        
        /**
         * 自动刷新查询成功
         *
         * @param trainingNo 本轮查询的批次号
         * @param castTime   最新耗时数据
         */
        void onAutoRefreshed(String trainingNo, AiQuestionApiClient.TrainingCastTime castTime);
        
        /**
         * 自动刷新停止（手动停止 / 弹窗关闭）
         *
         * @param trainingNo 停止前刷新的批次号
         */
        void onAutoRefreshStopped(String trainingNo);
    }
    
    public TrainingCastTimeDialog(Dialog owner, AiEnvConfig envConfig, String initialTrainingNo) {
        super(owner, "训练耗时查询", false);
        this.envConfig = envConfig;
        setSize(560, 445);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        initUI(initialTrainingNo);
        // 关闭弹窗：自动刷新进行中时仅隐藏弹窗（后台继续刷新，分页栏继续实时展示）；未开启自动刷新时正常销毁
        addWindowListener(new java.awt.event.WindowAdapter() {
            
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (refreshTimer != null && refreshTimer.isRunning()) {
                    setVisible(false);
                    return;
                }
                stopAutoRefresh();
                dispose();
            }
        });
    }
    
    /**
     * 更新环境配置（弹窗复用时同步当前选中环境）
     */
    public void updateEnvConfig(AiEnvConfig envConfig) {
        this.envConfig = envConfig;
    }
    
    /**
     * 填入批次号并立即查询一次（弹窗打开时的默认行为）
     * <p>
     * 是否开启自动刷新由响应的训练完成状态决定：finishTraining=false（未完成）自动开启，true（完成）不开启。
     *
     * @param trainingNo 最新批次号（null / 空白时清空输入框）
     */
    public void beginAutoRefresh(String trainingNo) {
        // 先停止旧的自动刷新，由查询结果决定是否重新开启
        stopAutoRefresh();
        if (trainingNo == null || trainingNo.isBlank()) {
            trainingNoField.setText("");
            setStatus("暂无批次号，请输入批次号后查询", false);
            return;
        }
        trainingNoField.setText(trainingNo.trim());
        triggerQuery();
    }
    
    // ────────── 界面构建 ──────────
    
    private void initUI(String initialTrainingNo) {
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 12, 10, 12));
        
        add(buildQueryPanel(initialTrainingNo), BorderLayout.NORTH);
        add(buildResultPanel(), BorderLayout.CENTER);
        add(buildStatusPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 查询条件区：批次号输入 + 手动查询 + 自动刷新配置与开关
     */
    private JPanel buildQueryPanel(String initialTrainingNo) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(createTitledBorder("查询条件"),
                new EmptyBorder(4, 8, 4, 8)));
        
        // ── 批次号 + 手动查询 ──
        JPanel noPanel = new JPanel(new BorderLayout(8, 0));
        noPanel.add(createLabel("批次号："), BorderLayout.WEST);
        trainingNoField = new CustomTextField("请输入训练批次号");
        trainingNoField.setMinWidth(240);
        trainingNoField.setFont(UiConstants.FONT_SANS_12);
        // 回车触发查询
        trainingNoField.addActionListener(e -> triggerQuery());
        if (initialTrainingNo != null && !initialTrainingNo.isBlank()) {
            trainingNoField.setText(initialTrainingNo.trim());
        }
        noPanel.add(trainingNoField, BorderLayout.CENTER);
        queryBtn = ButtonFactory.createPrimary("查询");
        queryBtn.setToolTipText("按当前批次号立即查询一次训练耗时");
        queryBtn.addActionListener(e -> triggerQuery());
        noPanel.add(queryBtn, BorderLayout.EAST);
        panel.add(noPanel, BorderLayout.NORTH);
        
        // ── 自动刷新配置 ──
        JPanel autoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        autoPanel.add(createLabel("自动刷新间隔："));
        intervalCombo = new JComboBox<>(REFRESH_INTERVAL_OPTIONS);
        intervalCombo.setEditable(true);
        // 可编辑下拉的编辑器是独立的 JTextField，需单独设置字体避免中文回退发虚
        intervalCombo.getEditor().getEditorComponent().setFont(UiConstants.FONT_SANS_12);
        intervalCombo.setFont(UiConstants.FONT_SANS_12);
        intervalCombo.setPreferredSize(new Dimension(70, 26));
        intervalCombo.setSelectedItem(String.valueOf(DEFAULT_REFRESH_SECONDS));
        intervalCombo.setToolTipText("选择或输入自动刷新间隔（秒，正整数）");
        autoPanel.add(intervalCombo);
        autoPanel.add(createLabel("秒"));
        autoRefreshBtn = ButtonFactory.createPill("开始自动刷新", UiConstants.COLOR_SUCCESS, UiConstants.COLOR_SUCCESS_LIGHT);
        autoRefreshBtn.setToolTipText("按配置间隔自动查询训练耗时（点击可随时停止）");
        autoRefreshBtn.addActionListener(e -> toggleAutoRefresh());
        autoPanel.add(autoRefreshBtn);
        panel.add(autoPanel, BorderLayout.SOUTH);
        return panel;
    }
    
    /**
     * 查询结果区：开始时间 / 最新时间 / 耗时
     */
    private JPanel buildResultPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(createTitledBorder("查询结果"),
                new EmptyBorder(4, 8, 4, 8)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(createLabel("训练数量："), gbc);
        totalCountValueLabel = createValueLabel();
        gbc.gridx = 1;
        gbc.weightx = 0;
        panel.add(totalCountValueLabel, gbc);
        
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(createLabel("最后刷新："), gbc);
        lastRefreshLabel = createValueLabel();
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(lastRefreshLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(createLabel("开始时间："), gbc);
        startValueLabel = createValueLabel();
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(startValueLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(createLabel("最新时间："), gbc);
        lastValueLabel = createValueLabel();
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(lastValueLabel, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        panel.add(createLabel("       耗时："), gbc);
        costValueLabel = new JLabel("—");
        costValueLabel.setFont(UiConstants.FONT_SANS_BOLD_22);
        costValueLabel.setForeground(UiConstants.COLOR_PRIMARY_LIGHT);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(costValueLabel, gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 5;
        costDetailLabel = new JLabel(" ");
        costDetailLabel.setFont(UiConstants.FONT_SANS_11);
        costDetailLabel.setForeground(Color.GRAY);
        panel.add(costDetailLabel, gbc);

      
        return panel;
    }
    
    private JLabel createValueLabel() {
        JLabel label = new JLabel("—");
        label.setFont(UiConstants.FONT_SANS_12_BOLD);
        return label;
    }
    
    /**
     * 创建统一界面字体的标签（未显式设置字体的中文标签会回退默认字体，小字号发虚模糊）
     */
    private static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiConstants.FONT_SANS_12);
        return label;
    }
    
    /**
     * 创建统一界面字体的分组标题边框（标题文字默认字体同样存在中文回退发虚问题）
     */
    private static javax.swing.border.TitledBorder createTitledBorder(String title) {
        javax.swing.border.TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(UiConstants.FONT_SANS_12_BOLD);
        return border;
    }
    
    /**
     * 底部状态区：查询状态 + 最后刷新时间
     */
    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        statusLabel = new JLabel("请输入批次号后查询");
        statusLabel.setFont(UiConstants.FONT_SANS_11);
        statusLabel.setForeground(Color.GRAY);
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }
    
    // ────────── 查询逻辑 ──────────
    
    /**
     * 触发一次查询（手动查询与自动刷新共用；查询未返回时跳过）
     */
    private void triggerQuery() {
        String trainingNo = trainingNoField.getText().trim();
        if (trainingNo.isEmpty()) {
            setStatus("请输入批次号", false);
            return;
        }
        if (querying) {
            return;
        }
        AiEnvConfig currentEnv = envConfig;
        querying = true;
        queryBtn.setEnabled(false);
        setStatus("批次号 [" + trainingNo + "] 查询中…", true);
        new Thread(() -> {
            AiQuestionApiClient.TrainingCastTime result = null;
            String error = null;
            try {
                result = AiQuestionApiClient.getInstance().getTrainingCastTime(currentEnv, trainingNo);
            } catch (AiQuestionApiClient.AiApiException ex) {
                logger.error("查询批次 [{}] 训练耗时失败", trainingNo, ex);
                error = ex.getMessage();
            }
            AiQuestionApiClient.TrainingCastTime castTime = result;
            String queryError = error;
            SwingUtilities.invokeLater(() -> {
                querying = false;
                queryBtn.setEnabled(true);
                if (queryError != null) {
                    consecutiveErrorCount++;
                    if (refreshTimer != null && refreshTimer.isRunning() && consecutiveErrorCount > MAX_CONSECUTIVE_ERRORS) {
                        // 查询异常连续超过阈值：停止自动刷新，避免持续失败空转
                        setStatus("查询失败：" + queryError + "（连续失败 " + consecutiveErrorCount + " 次，已停止自动刷新）", false);
                        stopAutoRefresh();
                    } else {
                        setStatus("查询失败：" + queryError + "（连续失败 " + consecutiveErrorCount + " 次）", false);
                    }
                } else {
                    consecutiveErrorCount = 0;
                    // 有 minInitTime 即展示；自动刷新由 finishTraining 驱动（false-未完成开启，true-完成停止）
                    renderResult(castTime);
                    boolean hasData = castTime != null && castTime.minInitTime() > 0;
                    if (castTime != null && castTime.finishTraining()) {
                        // 训练已完成：停止自动刷新
                        if (refreshTimer != null && refreshTimer.isRunning()) {
                            setStatus("训练已完成，已停止自动刷新", true);
                            stopAutoRefresh();
                        } else {
                            setStatus("训练已完成", true);
                        }
                    } else {
                        // 训练未完成：未开启自动刷新时自动开启
                        if (refreshTimer == null || !refreshTimer.isRunning()) {
                            startAutoRefresh();
                        }
                        setStatus(hasData ? "查询成功（训练未完成，自动刷新中）" : "暂无耗时数据（训练未完成，自动刷新中）", true);
                        // 自动刷新场景下回调监听器，供列表页在分页栏实时展示
                        if (autoRefreshListener != null && refreshTimer != null && refreshTimer.isRunning()) {
                            autoRefreshListener.onAutoRefreshed(trainingNo, castTime);
                        }
                    }
                }
            });
        }, "training-cast-time-query").start();
    }
    
    /**
     * 渲染查询结果：有 minInitTime 即展示（时间戳 + 格式化时间），耗时取差值秒；maxLastTime 缺失时最新时间与耗时展示占位
     */
    private void renderResult(AiQuestionApiClient.TrainingCastTime castTime) {
        if (castTime == null || castTime.minInitTime() <= 0) {
            startValueLabel.setText("—");
            lastValueLabel.setText("—");
            costValueLabel.setText("—");
            costDetailLabel.setText(" ");
            totalCountValueLabel.setText("—");
            return;
        }
        startValueLabel.setText(castTime.minInitTime() + "（" + formatEpoch(castTime.minInitTime()) + "）");
        if (castTime.maxLastTime() > 0) {
            lastValueLabel.setText(castTime.maxLastTime() + "（" + formatEpoch(castTime.maxLastTime()) + "）");
            // 接口时间戳为毫秒，耗时差值换算为秒展示
            long costMillis = Math.max(0, castTime.maxLastTime() - castTime.minInitTime());
            costValueLabel.setText(formatSeconds(costMillis));
            costDetailLabel.setText("≈ " + formatDuration(costMillis / 1000));
        } else {
            lastValueLabel.setText("—");
            costValueLabel.setText("—");
            costDetailLabel.setText(" ");
        }
        totalCountValueLabel.setText(castTime.totalCount() > 0 ? String.valueOf(castTime.totalCount()) : "—");
    }
    
    /**
     * 毫秒耗时格式化为秒（整秒直接展示，非整秒保留最多 3 位小数并去除尾部多余的 0）
     */
    private static String formatSeconds(long millis) {
        if (millis % 1000 == 0) {
            return (millis / 1000) + " 秒";
        }
        String seconds = String.format(java.util.Locale.ROOT, "%.3f", millis / 1000.0);
        seconds = seconds.replaceAll("0+$", "").replaceAll("\\.$", "");
        return seconds + " 秒";
    }
    
    /**
     * 耗时多单位换算：秒 → 分 / 小时（不足 1 分仅展示秒；小时 / 分按需省略前导零单位）
     */
    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours <= 0 && minutes <= 0) {
            return seconds + " 秒";
        }
        StringBuilder text = new StringBuilder();
        if (hours > 0) {
            text.append(hours).append(" 小时 ");
        }
        text.append(minutes).append(" 分 ");
        text.append(secs).append(" 秒");
        return text.toString();
    }
    
    /**
     * 时间戳格式化（自动识别秒 / 毫秒精度，按秒展示）
     */
    private static String formatEpoch(long epoch) {
        long millis = epoch >= 1_000_000_000_000L ? epoch : epoch * 1000L;
        return TIME_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
    
    private void setStatus(String text, boolean success) {
        statusLabel.setText(text);
        statusLabel.setForeground(success ? UiConstants.COLOR_SUCCESS_LIGHT : UiConstants.COLOR_DANGER_LIGHT);
        // 最后刷新时间展示在查询结果区第一行
        lastRefreshLabel.setText(TIME_ONLY_FORMATTER.format(java.time.LocalTime.now()));
    }
    
    // ────────── 自动刷新 ──────────
    
    /**
     * 切换自动刷新状态（开始 / 停止）：手动点击「开始 / 停止自动刷新」按钮触发
     */
    private void toggleAutoRefresh() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            stopAutoRefresh();
            return;
        }
        String trainingNo = trainingNoField.getText().trim();
        if (trainingNo.isEmpty()) {
            setStatus("请输入批次号", false);
            return;
        }
        // 手动开启自动刷新仅在批次存在待训练 / 训练中 / 训练已提交状态问题时生效
        if (batchInProgressChecker != null && !batchInProgressChecker.test(trainingNo)) {
            setStatus("批次 [" + trainingNo + "] 不存在训练任务，无法开启自动刷新", false);
            return;
        }
        startAutoRefresh();
        // 开启自动刷新时立即查询一次，避免等待首个间隔
        triggerQuery();
    }
    
    /**
     * 开启自动刷新（按当前配置间隔启动定时器，不触发立即查询；供手动开启与 finishTraining 未完成自动开启复用）
     */
    private void startAutoRefresh() {
        Integer seconds = parseIntervalSeconds();
        if (seconds == null) {
            setStatus("自动刷新间隔必须为正整数（秒）", false);
            return;
        }
        if (refreshTimer == null) {
            refreshTimer = new Timer(seconds * 1000, e -> triggerQuery());
        } else {
            refreshTimer.setDelay(seconds * 1000);
        }
        refreshTimer.start();
        autoRefreshTrainingNo = trainingNoField.getText().trim();
        autoRefreshBtn.setText("停止自动刷新");
        autoRefreshBtn.setToolTipText("停止按间隔自动查询训练耗时");
    }
    
    /**
     * 停止自动刷新
     */
    private void stopAutoRefresh() {
        boolean wasRunning = refreshTimer != null && refreshTimer.isRunning();
        if (wasRunning) {
            refreshTimer.stop();
        }
        autoRefreshBtn.setText("开始自动刷新");
        autoRefreshBtn.setToolTipText("按配置间隔自动查询训练耗时（点击可随时停止）");
        if (wasRunning && autoRefreshListener != null) {
            autoRefreshListener.onAutoRefreshStopped(autoRefreshTrainingNo);
        }
    }
    
    /**
     * 解析自动刷新间隔输入（下拉选择或手动输入，正整数秒；非法返回 null）
     */
    private Integer parseIntervalSeconds() {
        Object selected = intervalCombo.getSelectedItem();
        if (selected == null) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(selected.toString().trim());
            return seconds > 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
