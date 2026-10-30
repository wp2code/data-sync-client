package com.omnikit.ui;

import com.omnikit.components.AbsJPanl;
import com.omnikit.components.ButtonFactory;
import com.omnikit.core.GitLabService;
import com.omnikit.core.ProcessContainerService;
import com.omnikit.core.UpdateService;
import com.omnikit.ui.aitraining.AiQuestionPanel;
import com.omnikit.ui.datasync.DataSourceManagerDialog;
import com.omnikit.ui.datasync.DataSyncPanel;
import com.omnikit.ui.programs.ProgramContainerPanel;
import com.omnikit.ui.scripts.GitLabMangerDialog;
import com.omnikit.ui.scripts.ScriptPanel;
import com.omnikit.util.GlobalUtil;
import com.omnikit.util.IconUtil;
import com.omnikit.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;

/**
 * Swing 主窗口 — 负责菜单卡片切换、在线更新与退出流程；数据同步由 {@link DataSyncPanel} 提供
 */
@Slf4j
public class OmniKitUI extends JFrame {
    
    // ────────── 在线更新组件 ──────────
    
    /**
     * 下载进度条：发现新版本下载时显示在顶栏「设置」按钮旁
     */
    private JProgressBar updateProgressBar;
    
    /**
     * 检查更新进行中标记：防止菜单重复触发
     */
    private boolean updateCheckInProgress = false;
    
    // ────────── 上下栏布局：上栏菜单 + 下栏内容卡片 ──────────
    private static final String CARD_SYNC = DataSyncPanel.NAME;
    
    private static final String CARD_AI = AiQuestionPanel.NAME;
    
    private static final String CARD_SCRIPT = ScriptPanel.NAME;
    
    private static final String CARD_CONTAINER = ProgramContainerPanel.NAME;
    
    private CardLayout contentCards;
    
    private JPanel contentCardPanel;
    
    /**
     * 数据同步内容面板（默认卡片）
     */
    private DataSyncPanel dataSyncPanel;
    
    /**
     * 菜单按钮（按插入顺序排列），key 为卡片标识
     */
    private final Map<String, JButton> menuButtons = new LinkedHashMap<>();
    
    /**
     * 已创建的内容卡片标识集合
     */
    private final java.util.Set<String> createdCards = new java.util.HashSet<>();
    
    private String activeCard = CARD_SYNC;
    
    // ────────── 构造方法 ──────────
    
    public OmniKitUI() {
        initUI();
        // 清理上次在线升级遗留的旧文件
        UpdateService.cleanupObsoleteFiles();
        SQLiteConfigUtil.getInstance().initialize();
        dataSyncPanel.refreshConfigCombos();
        // 启动 3 秒后静默检查更新（距上次检查超过 N 小时才检查，默认 24 小时，可配置）
        Timer updateTimer = new Timer(3000, e -> {
            if (UpdateService.shouldAutoCheck()) {
                performUpdateCheck(true);
            } else {
                log.debug("距上次检查更新未超过 {} 小时，跳过自动检查", UpdateService.getCheckIntervalHours());
            }
        });
        updateTimer.setRepeats(false);
        updateTimer.start();
    }
    
    // ────────── UI 初始化 ──────────
    
    private void initUI() {
        setTitle("OmniKit");
        // 拦截关闭事件：有运行中的程序时弹窗选择停止或保持后台运行，无则直接退出
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 820);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));
        
        setAppIcon();
        bindF11Maximize();
        
        // 窗口关闭时释放所有连接
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                GitLabService.getInstance().autoLogin();
                super.windowActivated(e);
            }
            
            @Override
            public void windowClosing(WindowEvent e) {
                handleCloseApplication();
            }
        });
        
        add(createHeaderPanel(), BorderLayout.NORTH);
        
        // 下栏：与菜单对应的内容区（CardLayout 切换），默认显示数据同步
        contentCards = new CardLayout();
        contentCardPanel = new JPanel(contentCards);
        contentCardPanel.setBorder(new EmptyBorder(5, 12, 5, 12));
        dataSyncPanel = new DataSyncPanel(this);
        contentCardPanel.add(dataSyncPanel, CARD_SYNC);
        add(contentCardPanel, BorderLayout.CENTER);
        updateMenuSelection();
    }
    
    /**
     * F11 切换主窗口最大化 / 还原；Esc 在最大化状态下还原（未最大化时不响应，避免干扰弹窗 Esc 关闭）
     */
    private void bindF11Maximize() {
        JRootPane rootPane = getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggleMaximize");
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "restoreMaximize");
        rootPane.getActionMap().put("toggleMaximize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int state = getExtendedState();
                if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                    setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
                } else {
                    setExtendedState(state | Frame.MAXIMIZED_BOTH);
                }
            }
        });
        rootPane.getActionMap().put("restoreMaximize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int state = getExtendedState();
                if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                    setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
                }
            }
        });
    }
    
    // ────────── 图标 ──────────
    
    private void setAppIcon() {
        try {
            setIconImage(IconUtil.createAppIcon().getImage());
        } catch (Exception ignored) {
        }
    }
    
    // ────────── 头部面板 ──────────
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(12, 20, 8, 20));
        
        // 上栏菜单：数据同步（默认）/ AI 问题 / 脚本管理 / 程序容器 / 设置
        JPanel menuPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        addMenuButton(menuPanel, "数据同步", CARD_SYNC);
        addMenuButton(menuPanel, "AI 训练", CARD_AI);
        addMenuButton(menuPanel, "脚本管理", CARD_SCRIPT);
        addMenuButton(menuPanel, "程序容器", CARD_CONTAINER);
        
        // 设置下拉菜单：数据源管理 / GitLab 配置 / 检查更新
        JButton settingsBtn = ButtonFactory.createToolbar("设置");
        JPopupMenu settingsMenu = new JPopupMenu();
        GlobalUtil.addMenuItem(settingsMenu, "数据源管理", this::openDataSourceManager);
        GlobalUtil.addMenuItem(settingsMenu, "GitLab 配置", this::openGitLabManager);
        settingsMenu.addSeparator();
        // 检查更新菜单项：第二行灰色显示当前版本
        GlobalUtil.addMenuItem(settingsMenu, "<html>检查更新<br><span style='color:gray'>" + UiConstants.VERSION + "</span></html>",
                () -> performUpdateCheck(false));
        GlobalUtil.addMenuItem(settingsMenu, "帮助", this::openGitHubAddr);
        settingsBtn.addActionListener(e -> settingsMenu.show(settingsBtn, 0, settingsBtn.getHeight()));
        menuPanel.add(settingsBtn);
        
        // 在线更新下载进度条：下载期间显示在设置按钮旁
        updateProgressBar = new JProgressBar(0, 100);
        updateProgressBar.setStringPainted(true);
        updateProgressBar.setVisible(false);
        updateProgressBar.setPreferredSize(new Dimension(150, settingsBtn.getPreferredSize().height));
        menuPanel.add(updateProgressBar);
        
        panel.add(menuPanel, BorderLayout.WEST);
        return panel;
    }
    
    /**
     * 在菜单栏添加一个内容切换按钮
     */
    private void addMenuButton(JPanel menuPanel, String text, String card) {
        JButton btn = ButtonFactory.createToolbar(text);
        btn.addActionListener(e -> showCard(card));
        menuPanel.add(btn);
        menuButtons.put(card, btn);
    }
    
    
    /**
     * 切换下栏内容卡片，并高亮当前菜单按钮
     */
    private void showCard(String card) {
        ensureCardCreated(card);
        activeCard = card;
        contentCards.show(contentCardPanel, card);
        updateMenuSelection();
        final Component[] components = contentCardPanel.getComponents();
        for (final Component component : components) {
            if (component instanceof AbsJPanl cp) {
                if (!activeCard.equals(cp.name())) {
                    cp.destroy();
                } else {
                    cp.refresh();
                }
            }
        }
    }
    
    /**
     * 懒加载创建内容卡片：AI 问题 / 脚本管理 / 程序容器复用各自对话框构建的内容面板， 对话框本身不再以弹窗展示，而是嵌入主界面下栏
     */
    private void ensureCardCreated(String card) {
        if (createdCards.contains(card)) {
            return;
        }
        createdCards.add(card);
        switch (card) {
            case CARD_AI -> contentCardPanel.add(new AiQuestionPanel(this), CARD_AI);
            case CARD_SCRIPT -> contentCardPanel.add(new ScriptPanel(), CARD_SCRIPT);
            case CARD_CONTAINER -> contentCardPanel.add(new ProgramContainerPanel(), CARD_CONTAINER);
            default -> {
            }
        }
    }
    
    /**
     * 刷新菜单按钮的选中高亮（当前卡片对应按钮加粗 + 主色）
     */
    private void updateMenuSelection() {
        Color normalColor = UIManager.getColor("Button.foreground");
        menuButtons.forEach((card, btn) -> {
            boolean active = card.equals(activeCard);
            btn.setFont(active ? UiConstants.FONT_SANS_12_BOLD : UiConstants.FONT_SANS_12);
            btn.setForeground(active ? UiConstants.COLOR_ACTIVE : normalColor);
        });
    }
    
    // ────────── 在线更新 ──────────
    
    /**
     * 检查更新：后台查询 GitHub 最新 Release，发现新版本时询问用户是否立即升级。
     *
     * @param silent true 表示静默模式（启动时自动检查）：无新版本或检查失败时不弹窗
     */
    private void performUpdateCheck(boolean silent) {
        // 检查期间禁止重复触发
        if (updateCheckInProgress) {
            return;
        }
        updateCheckInProgress = true;
        new SwingWorker<UpdateService.UpdateInfo, Void>() {
            
            @Override
            protected UpdateService.UpdateInfo doInBackground() throws Exception {
                UpdateService.UpdateInfo info = UpdateService.create().checkForUpdate();
                // 无论结果如何，记录本次检查时间（供启动节流判断）
                UpdateService.markChecked();
                return info;
            }
            
            @Override
            protected void done() {
                updateCheckInProgress = false;
                try {
                    UpdateService.UpdateInfo info = get();
                    if (!UpdateService.isNewerVersion(info.version(), UiConstants.VERSION)) {
                        if (!silent) {
                            JOptionPane.showMessageDialog(OmniKitUI.this, "当前已是最新版本 " + UiConstants.VERSION, "检查更新",
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                        return;
                    }
                    String message = "发现新版本 " + info.version() + "\n\n" + (info.notes().isBlank() ? "" : info.notes() + "\n\n")
                            + "是否立即下载并更新？（更新完成后将自动重启）";
                    if (JOptionPane.showConfirmDialog(OmniKitUI.this, message, "在线更新", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        downloadAndApplyUpdate(info);
                    }
                } catch (Exception ex) {
                    log.warn("检查更新失败", ex);
                    if (!silent) {
                        JOptionPane.showMessageDialog(OmniKitUI.this, "检查更新失败：" + ex.getMessage(), "检查更新", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }.execute();
    }
    
    /**
     * 下载新版本并替换当前 exe，成功后自动重启。 下载期间在顶栏「设置」按钮旁显示下载进度条，下载结束或失败后隐藏。
     */
    private void downloadAndApplyUpdate(UpdateService.UpdateInfo info) {
        // 进入下载态：显示进度条
        updateProgressBar.setValue(0);
        updateProgressBar.setString(info.version() + " 下载 0%");
        updateProgressBar.setIndeterminate(true);
        updateProgressBar.setVisible(true);
        
        new SwingWorker<Void, Integer>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                // 成功时进程内部直接退出，本方法不会返回
                UpdateService.create().downloadAndApply(info, this::publish);
                return null;
            }
            
            @Override
            protected void process(List<Integer> chunks) {
                int percent = chunks.get(chunks.size() - 1);
                updateProgressBar.setIndeterminate(false);
                updateProgressBar.setValue(percent);
                updateProgressBar.setString(info.version() + " 下载 " + percent + "%");
            }
            
            @Override
            protected void done() {
                // 下载结束（失败或重启失败）隐藏进度条
                updateProgressBar.setVisible(false);
                try {
                    get();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof UpdateService.RestartException) {
                        // 更新已成功，仅自动重启失败：提示手动启动，当前窗口可继续使用
                        log.warn("更新完成但自动重启失败", cause);
                        JOptionPane.showMessageDialog(OmniKitUI.this, cause.getMessage(), "更新完成", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        log.warn("在线更新失败", cause);
                        JOptionPane.showMessageDialog(OmniKitUI.this, "更新失败：" + cause.getMessage(), "在线更新", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    log.warn("在线更新失败", ex);
                    JOptionPane.showMessageDialog(OmniKitUI.this, "更新失败：" + ex.getMessage(), "在线更新", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    
    // ────────── GitLab管理对话框 ──────────
    private void openGitLabManager() {
        GitLabMangerDialog dialog = new GitLabMangerDialog(this);
        dialog.setVisible(true);
        // 对话框关闭后刷新主页下拉框
        dataSyncPanel.refreshConfigCombos();
    }
    
    // ────────── GitHub 项目主页 ──────────
    private void openGitHubAddr() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(UiConstants.GITHUB_ADDR));
            } else {
                JOptionPane.showMessageDialog(this, "当前环境不支持打开浏览器：" + UiConstants.GITHUB_ADDR, "GitHub", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            log.warn("打开 GitHub 页面失败", ex);
            JOptionPane.showMessageDialog(this, "打开浏览器失败：" + ex.getMessage(), "GitHub", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    // ────────── 数据源管理对话框 ──────────
    private void openDataSourceManager() {
        DataSourceManagerDialog dialog = new DataSourceManagerDialog(this);
        dialog.setVisible(true);
        // 对话框关闭后刷新主页下拉框
        dataSyncPanel.refreshConfigCombos();
    }
    
    // ────────── 应用退出 ──────────
    
    /**
     * 主窗口关闭流程：无运行中程序直接退出；有运行中程序时弹窗选择 「停止程序并退出」或「保持程序后台运行」，取消则留在应用。 选择保持运行时子进程不随应用退出（Windows 下父进程退出后子进程继续存活）， 但退出后无法再通过本工具查看输出或停止这些程序。
     */
    private void handleCloseApplication() {
        ProcessContainerService container = ProcessContainerService.getInstance();
        long running = container.countRunningProcesses();
        if (running == 0) {
            exitApplication();
            return;
        }
        Object[] options = {"停止程序并退出", "保持程序运行", "取消"};
        int choice = JOptionPane.showOptionDialog(this, "检测到 " + running + " 个程序正在运行，关闭应用时如何处理？\n"
                        + "选择「保持程序运行」后，程序将在后台继续运行，但无法再通过本工具查看输出或停止", "关闭确认", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
            return; // 取消：留在应用
        }
        if (choice == 1) {
            // 保持程序后台运行：关闭退出时的进程清理钩子
            container.setStopProcessesOnExit(false);
            exitApplication();
            return;
        }
        // 停止程序并退出：stopAll 为阻塞方法（最多等待约 3 秒），隐藏窗口后交后台线程执行避免冻结
        setVisible(false);
        new Thread(() -> {
            container.stopAll();
            System.exit(0);
        }, "app-exit-stop-processes").start();
    }
    
    /**
     * 执行窗口关闭清理（释放数据库连接、GitLab 登出）后退出应用
     */
    private void exitApplication() {
        dataSyncPanel.closeConnectionsForExit();
        GitLabService.getInstance().logout();
        System.exit(0);
    }
}
