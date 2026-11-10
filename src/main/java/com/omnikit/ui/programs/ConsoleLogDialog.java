package com.omnikit.ui.programs;

import com.omnikit.components.ButtonFactory;
import com.omnikit.components.FullscreenJDialog;
import com.omnikit.ui.UiConstants;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

/**
 * 全屏日志窗口
 * <p>
 * 按程序（容器）名称分页签：每个程序一个页签，切换选中程序时新增或切换到对应页签， 已打开的页签内容保留不替换。页签内容与控制台同步——选中程序时载入该程序完整日志，运行中追加新输出。 工具栏支持启动/停止/重启当前页签对应的程序（与主控制台同一套逻辑），并支持 F11 切换全屏、ESC
 * 退出全屏。
 *
 * @author liuweiping
 * @date 2026-11-10
 **/
public class ConsoleLogDialog extends FullscreenJDialog {
    
    /**
     * 未选中程序时的页签键
     */
    private static final long NO_PROGRAM_ID = -1L;
    
    private final JTabbedPane logTabs = new JTabbedPane();
    
    /**
     * programId → 日志页签（按打开顺序，页签标签为程序名称）
     */
    private final Map<Long, LogTab> tabs = new LinkedHashMap<>();
    
    /**
     * 自动滚动：只作用于当前选中页签，查看历史页签时不会被新输出打断
     */
    private final JCheckBox autoScrollBox = new JCheckBox("自动滚动", true);
    
    /**
     * 清空回调：复用面板的清空逻辑（待刷新缓冲 + 控制台 + 服务端日志）
     */
    private final Runnable onClear;
    
    /**
     * 程序操作回调：启动/停止/重启与页签切换同步选中，由程序容器面板实现，保证与主界面同一套逻辑
     */
    private final ProgramActions programActions;
    
    /**
     * 页签切换时的回调抑制标志：面板主动切换页签时不应反向触发选中
     */
    private boolean suppressTabChange;
    
    /**
     * 启动/停止/重启按钮：启用态由面板按当前程序运行状态推送
     */
    private JButton startBtn;
    
    private JButton stopBtn;
    
    private JButton restartBtn;
    
    /**
     * 面板最近一次推送的运行状态：切到帮助等非程序页签时据此重算按钮启用态
     */
    private boolean stateHasProgram;
    
    private boolean stateRunning;
    
    private boolean stateExternalRunning;
    
    /**
     * 全屏切换按钮：文字随全屏状态在「全屏 / 还原」间切换
     */
    private JButton fullscreenBtn;
    
    public ConsoleLogDialog(Frame owner, String title, ProgramActions programActions, Runnable onClear) {
        super("CONSOLE_LOG", owner, title, false, 1100, 760);
        this.programActions = programActions;
        this.onClear = onClear;
        // 关闭即销毁，页签内容随窗口一起释放
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        // 页签切换即同步面板选中程序，保证启动/停止指向的就是当前页签的程序
        logTabs.addChangeListener(e -> {
            if (suppressTabChange) {
                return;
            }
            Long programId = currentProgramId();
            if (programId != null && programActions != null) {
                programActions.selectProgram(programId);
            }
            // 帮助/未选中占位页签不代表具体程序，切到此类页签时禁用启动/停止/重启
            applyProgramActions();
        });
    }
    
    /**
     * 日志窗口的程序操作回调：由程序容器面板实现
     */
    public interface ProgramActions {
        
        void start();
        
        void stop();
        
        void restart();
        
        /**
         * 选中指定程序（页签切换时同步主界面选中项）
         */
        void selectProgram(Long programId);
    }
    
    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        JLabel hintLabel = new JLabel();
        hintLabel.setFont(UiConstants.FONT_SANS_12);
        hintLabel.setForeground(Color.GRAY);
        
        // 程序控制：与控制台工具栏同构，作用于当前页签对应的程序
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        startBtn = ButtonFactory.createSuccess("启动");
        startBtn.setToolTipText("启动当前页签对应的程序");
        startBtn.addActionListener(e -> {
            if (programActions != null) {
                programActions.start();
            }
        });
        stopBtn = ButtonFactory.createDestructive("停止");
        stopBtn.setToolTipText("停止当前页签对应的程序（含接管运行的进程）");
        stopBtn.addActionListener(e -> {
            if (programActions != null) {
                programActions.stop();
            }
        });
        restartBtn = ButtonFactory.createWarning("重启");
        restartBtn.setToolTipText("重启当前页签对应的程序");
        restartBtn.addActionListener(e -> {
            if (programActions != null) {
                programActions.restart();
            }
        });
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        restartBtn.setEnabled(false);
        actionPanel.add(startBtn);
        actionPanel.add(stopBtn);
        actionPanel.add(restartBtn);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        autoScrollBox.setFont(UiConstants.FONT_SANS_12);
        autoScrollBox.setOpaque(false);
        // 重新勾选时立即跟到当前页签末尾，否则要等下一条输出才滚动，看起来像勾选没生效
        autoScrollBox.addActionListener(e -> {
            LogTab tab = tabs.get(keyOf(currentProgramId()));
            if (autoScrollBox.isSelected() && tab != null) {
                tab.scrollToEnd();
            }
        });
        fullscreenBtn = ButtonFactory.createToolbar("全屏");
        fullscreenBtn.setToolTipText("切换全屏（F11），ESC 退出全屏");
        fullscreenBtn.addActionListener(e -> toggleFullscreen());
        JButton copyBtn = ButtonFactory.createToolbar("一键复制");
        copyBtn.addActionListener(e -> copyAll(currentArea()));
        JButton clearBtn = ButtonFactory.createToolbar("清空");
        clearBtn.addActionListener(e -> {
            if (onClear != null) {
                onClear.run();
            }
        });
        JButton closeTabBtn = ButtonFactory.createToolbar("关闭页签");
        closeTabBtn.setToolTipText("关闭当前页签；关闭最后一个页签时关闭窗口");
        closeTabBtn.addActionListener(e -> closeSelectedTab());
        JButton closeBtn = ButtonFactory.createToolbar("关闭");
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(autoScrollBox);
        btnPanel.add(copyBtn);
        btnPanel.add(clearBtn);
        btnPanel.add(closeTabBtn);
        btnPanel.add(fullscreenBtn);
        btnPanel.add(closeBtn);
        
        toolbar.add(actionPanel, BorderLayout.WEST);
        toolbar.add(hintLabel, BorderLayout.CENTER);
        toolbar.add(btnPanel, BorderLayout.EAST);
        
        logTabs.setFont(UiConstants.FONT_SANS_12);
        
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(logTabs, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * 显示指定程序的日志页签：页签不存在则新建；已存在则刷新内容并选中，其他页签保持不变
     *
     * @param programId   程序 id，null 表示当前未选中程序
     * @param programName 页签名称（程序名称）
     * @param content     该程序的完整日志内容（与控制台一致）
     */
    public void showLog(Long programId, String programName, String content) {
        showTab(keyOf(programId), programName, content, true);
    }
    
    /**
     * 后台补齐页签：页签不存在时新建并载入日志快照，已存在时只同步名称，不切换当前选中页签。 用于打开窗口时一次性补上运行中/接管运行程序的页签，不打断正在看的页签
     */
    public void showLogInBackground(Long programId, String programName, String content) {
        long key = keyOf(programId);
        LogTab existing = tabs.get(key);
        if (existing != null) {
            int index = logTabs.indexOfComponent(existing.scrollPane);
            if (index >= 0 && !programName.equals(logTabs.getTitleAt(index))) {
                logTabs.setTitleAt(index, programName);
            }
            return;
        }
        showTab(key, programName, content, false);
    }
    
    /**
     * 新建或刷新指定页签：页签已存在时只改标题与内容，其它页签保持不变
     *
     * @param select 为 true 时选中该页签并滚到末尾；为 false 时只在后台补齐，不影响当前页签
     */
    private void showTab(long key, String tabName, String content, boolean select) {
        // 抑制页签变化回调：面板主动切页签（含新建页签自动选中）不应反向选中程序，避免循环调用
        suppressTabChange = true;
        try {
            LogTab tab = tabs.get(key);
            if (tab == null) {
                // 已有真实程序页签时移除「未选择程序」占位页签
                removePlaceholderTab(key);
                tab = createTab();
                tabs.put(key, tab);
                logTabs.addTab(tabName, tab.scrollPane);
            } else {
                int index = logTabs.indexOfComponent(tab.scrollPane);
                if (index >= 0 && !tabName.equals(logTabs.getTitleAt(index))) {
                    logTabs.setTitleAt(index, tabName);
                }
            }
            tab.setContent(content);
            if (select) {
                logTabs.setSelectedComponent(tab.scrollPane);
                tab.scrollToEnd();
            }
        } finally {
            suppressTabChange = false;
        }
    }
    
    /**
     * 按程序运行状态刷新启动/停止/重启按钮：与主控制台按钮保持同一套启用规则
     */
    public void updateProgramActions(boolean hasProgram, boolean running, boolean externallyRunning) {
        stateHasProgram = hasProgram;
        stateRunning = running;
        stateExternalRunning = externallyRunning;
        applyProgramActions();
    }
    
    /**
     * 应用按钮启用态：未选中程序的占位页签不代表具体程序，此时三个按钮一律禁用
     */
    private void applyProgramActions() {
        boolean programTab = currentProgramId() != null;
        startBtn.setEnabled(programTab && stateHasProgram && !stateRunning && !stateExternalRunning);
        stopBtn.setEnabled(programTab && stateHasProgram && (stateRunning || stateExternalRunning));
        restartBtn.setEnabled(programTab && stateHasProgram && stateRunning);
    }
    
    /**
     * 追加当前程序的新输出：仅在对应页签已存在时追加，切换程序后旧行不会写错页签
     */
    public void appendLog(Long programId, String text) {
        LogTab tab = tabs.get(keyOf(programId));
        if (tab == null) {
            return;
        }
        // 取消自动滚动或非当前页签时只追加不跟随：JTextArea 追加文本会把 caret 带到末尾并带动视口滚到底，需还原滚动位置
        boolean follow = autoScrollBox.isSelected() && logTabs.getSelectedComponent() == tab.scrollPane;
        tab.append(text, follow);
    }
    
    private LogTab createTab() {
        JTextArea area = new JTextArea();
        // caret 会在追加文本时跟随光标把视口拉到底部，这里的实现按「自动滚动」开关决定是否跟随
        area.setCaret(new LogCaret());
        area.setFont(UiConstants.FONT_MONO_12);
        area.setBackground(UiConstants.COLOR_LOG_BG);
        area.setForeground(UiConstants.COLOR_LOG_FG);
        area.setCaretColor(UiConstants.COLOR_LOG_FG);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        area.setToolTipText("双击日志区域切换全屏，再次双击还原");
        JScrollPane scroll = new JScrollPane(area);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // 与控制台一致：双击日志内容（含下方空白区域）切换全屏
        attachFullscreenToggle(area);
        attachFullscreenToggle(scroll.getViewport());
        attachFullscreenToggle(scroll);
        // 右键菜单：复制选中 / 一键复制全部 / 清空日志
        attachLogPopupMenu(area, area);
        attachLogPopupMenu(scroll.getViewport(), area);
        return new LogTab(area, scroll);
    }
    
    /**
     * 双击切换全屏：文本区、视口与滚动容器都绑定，日志较少时双击下方空白也能触发
     */
    private void attachFullscreenToggle(Component comp) {
        comp.addMouseListener(new MouseAdapter() {
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    toggleFullscreen();
                }
            }
        });
    }
    
    /**
     * 日志右键菜单：复制选中、一键复制全部、清空日志；与主控制台右键菜单保持相同的操作语义。 绑定到文本区与视口，日志较少时在下方空白处右键也能弹出
     */
    private void attachLogPopupMenu(Component comp, JTextArea area) {
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
                if (!e.isPopupTrigger()) {
                    return;
                }
                JPopupMenu popup = new JPopupMenu();
                // 复制选中：无选中内容时置灰，避免误以为已复制全文
                JMenuItem copySelectionItem = new JMenuItem("复制选中");
                copySelectionItem.setEnabled(area.getSelectionStart() != area.getSelectionEnd());
                copySelectionItem.addActionListener(ev -> area.copy());
                JMenuItem copyAllItem = new JMenuItem("一键复制全部");
                copyAllItem.addActionListener(ev -> copyAll(area));
                JMenuItem clearItem = new JMenuItem("清空日志");
                clearItem.addActionListener(ev -> {
                    if (onClear != null) {
                        onClear.run();
                    }
                });
                popup.add(copySelectionItem);
                popup.add(copyAllItem);
                popup.addSeparator();
                popup.add(clearItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }
    
    /**
     * 移除「未选择程序」占位页签：仅新增真实程序页签时调用，避免占位页签堆积
     */
    private void removePlaceholderTab(long key) {
        if (key == NO_PROGRAM_ID) {
            return;
        }
        LogTab placeholder = tabs.remove(NO_PROGRAM_ID);
        if (placeholder != null) {
            logTabs.remove(placeholder.scrollPane);
        }
    }
    
    /**
     * 关闭当前页签：关闭最后一个页签时整个窗口一起关闭
     */
    private void closeSelectedTab() {
        int index = logTabs.getSelectedIndex();
        if (index < 0) {
            return;
        }
        Component selected = logTabs.getComponentAt(index);
        Long keyToRemove = null;
        for (Map.Entry<Long, LogTab> entry : tabs.entrySet()) {
            if (entry.getValue().scrollPane == selected) {
                keyToRemove = entry.getKey();
                break;
            }
        }
        if (keyToRemove != null) {
            tabs.remove(keyToRemove);
        }
        suppressTabChange = true;
        try {
            logTabs.remove(index);
        } finally {
            suppressTabChange = false;
        }
        if (logTabs.getTabCount() == 0) {
            dispose();
            return;
        }
        // 关闭页签后选中项跟着落到新页签，保持「当前页签 = 主界面选中程序」
        Long nextProgramId = currentProgramId();
        if (nextProgramId != null && programActions != null) {
            programActions.selectProgram(nextProgramId);
        }
    }
    
    /**
     * 一键复制全部日志：复制当前页签（或右键所在页签）的全部内容并提示结果
     */
    private void copyAll(JTextArea area) {
        if (area == null || area.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "暂无日志内容", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        area.selectAll();
        area.copy();
        area.setCaretPosition(area.getDocument().getLength());
        JOptionPane.showMessageDialog(this, "日志已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 当前选中页签的文本区（无页签返回 null）
     */
    private JTextArea currentArea() {
        LogTab tab = tabs.get(keyOf(currentProgramId()));
        return tab == null ? null : tab.area;
    }
    
    /**
     * 当前选中页签对应的程序 id（未选中程序占位页签为负数键，返回 null）
     */
    private Long currentProgramId() {
        Component selected = logTabs.getSelectedComponent();
        if (selected == null) {
            return null;
        }
        for (Map.Entry<Long, LogTab> entry : tabs.entrySet()) {
            if (entry.getValue().scrollPane == selected) {
                return entry.getKey() < 0 ? null : entry.getKey();
            }
        }
        return null;
    }
    
    private static long keyOf(Long programId) {
        return programId == null ? NO_PROGRAM_ID : programId;
    }
    
    /**
     * 全屏切换：F11 与按钮共用同一入口，切换后同步按钮文字
     */
    @Override
    protected void toggleFullscreen() {
        super.toggleFullscreen();
        if (fullscreenBtn != null) {
            fullscreenBtn.setText(fullscreen ? "还原" : "全屏");
        }
    }
    
    /**
     * 日志文本区的光标：取消「自动滚动」后忽略光标变化引起的视口调整， 否则 JTextArea 追加文本把光标带到末尾时仍会把视口拉到最底部，不受自动滚动开关控制
     */
    private final class LogCaret extends DefaultCaret {
        
        @Override
        protected void adjustVisibility(Rectangle nloc) {
            if (autoScrollBox.isSelected()) {
                super.adjustVisibility(nloc);
            }
        }
    }
    
    /**
     * 单个程序日志页签：独立的文本区与文档，页签之间互不干扰
     */
    private static final class LogTab {
        
        private final JTextArea area;
        
        private final JScrollPane scrollPane;
        
        private LogTab(JTextArea area, JScrollPane scrollPane) {
            this.area = area;
            this.scrollPane = scrollPane;
        }
        
        private void setContent(String content) {
            area.setText(content == null ? "" : content);
        }
        
        /**
         * 追加文本：follow 为 true 时滚到末尾；为 false 时保持用户当前的滚动位置与选区不变
         */
        private void append(String text, boolean follow) {
            if (follow) {
                area.append(text);
                scrollToEnd();
                return;
            }
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            int scrollValue = bar.getValue();
            int selStart = area.getSelectionStart();
            int selEnd = area.getSelectionEnd();
            area.append(text);
            // 追加时 caret 跟到文档末尾会带动视口跳到底部，这里按追加前的位置还原
            area.select(selStart, selEnd);
            bar.setValue(scrollValue);
        }
        
        private void scrollToEnd() {
            area.setCaretPosition(area.getDocument().getLength());
            // 兜底：文本刚追加时滚动条模型可能尚未刷新，直接推到当前最大值
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        }
    }
}
