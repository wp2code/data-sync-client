package com.omnikit.ui.programs;

import com.omnikit.components.ButtonFactory;
import com.omnikit.components.FullscreenJDialog;
import com.omnikit.components.combobox.IconItem;
import com.omnikit.components.combobox.IconJComboBox;
import com.omnikit.model.ProgramConfig;
import com.omnikit.ui.UiConstants;
import com.omnikit.util.IconUtil;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * 程序类型帮助窗口
 * <p>
 * 「帮助」按钮的独立弹窗：帮助文本展示在窗口内自带的只读日志区中，不写入主控制台、也不进入全屏日志窗口， 因此查看帮助不会冲掉程序正在看的运行日志。顶部提供「程序类型」下拉框，可直接切换查看其它类型的说明与示例； 支持双击日志区或 F11 切换全屏、ESC 退出全屏，右键可复制选中/全部内容。
 *
 * @author liuweiping
 * @date 2026-11-10
 **/
public class ProgramHelpDialog extends FullscreenJDialog {
    
    private static final int DEFAULT_WIDTH = 900;
    
    private static final int DEFAULT_HEIGHT = 720;
    
    /**
     * 可切换的程序类型持久化值（与下拉项顺序一一对应）
     */
    private static final String[] TYPE_VALUES = {ProgramConfig.TYPE_JAVA, ProgramConfig.TYPE_PYTHON, ProgramConfig.TYPE_HTTP_SERVER,
            ProgramConfig.TYPE_OTHER};
    
    /**
     * 帮助文本展示区：只读，样式与控制台日志一致，便于对照填写配置
     */
    private final JTextArea helpArea = new JTextArea();
    
    /**
     * 程序类型切换下拉：带类型图标，选择后直接刷新本窗口的帮助内容与标题
     */
    private final IconJComboBox typeComboBox = new IconJComboBox();
    
    /**
     * 程序性设置下拉选中项时，抑制触发刷新的回调
     */
    private boolean suppressTypeChange;
    
    /**
     * 全屏切换按钮：文字随全屏状态在「全屏 / 还原」间切换
     */
    private JButton fullscreenBtn;
    
    /**
     * @param owner       父窗口
     * @param programType 首次展示的程序类型持久化值
     */
    public ProgramHelpDialog(Frame owner, String programType) {
        super("PROGRAM_HELP", owner, titleOf(programType), false, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        // 关闭即销毁：下次打开重新按当前类型生成帮助内容
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        showHelpFor(programType);
    }
    
    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        
        // 程序类型切换：切换后直接展示该类型的帮助，无需回到主界面改下拉框
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel typeLabel = new JLabel("程序类型:");
        typeLabel.setFont(UiConstants.FONT_SANS_12);
        typeComboBox.setFont(UiConstants.FONT_SANS_12);
        typeComboBox.setPreferredSize(new Dimension(150, 26));
        typeComboBox.setToolTipText("切换程序类型，查看对应类型的字段说明、参数说明与示例");
        // 选项与控制台「程序类型」下拉一致：图标 + 类型名，业务数据存持久化值
        for (String typeValue : TYPE_VALUES) {
            typeComboBox.addItem(new IconItem(IconUtil.getProgramTypeIcon(typeValue), ProgramHelpDoc.typeName(typeValue), typeValue));
        }
        typeComboBox.addActionListener(e -> {
            if (suppressTypeChange) {
                return;
            }
            IconItem selected = typeComboBox.getSelectedItem();
            if (selected != null && selected.userData() instanceof String typeValue) {
                showHelpFor(typeValue);
            }
        });
        typePanel.add(typeLabel);
        typePanel.add(typeComboBox);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        fullscreenBtn = ButtonFactory.createToolbar("全屏");
        fullscreenBtn.setToolTipText("切换全屏（F11），ESC 退出全屏");
        fullscreenBtn.addActionListener(e -> toggleFullscreen());
        JButton copyBtn = ButtonFactory.createToolbar("一键复制");
        copyBtn.setToolTipText("复制全部帮助内容到剪贴板");
        copyBtn.addActionListener(e -> copyAll());
        JButton closeBtn = ButtonFactory.createToolbar("关闭");
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(copyBtn);
        btnPanel.add(fullscreenBtn);
        btnPanel.add(closeBtn);
        
        toolbar.add(typePanel, BorderLayout.WEST);
        toolbar.add(btnPanel, BorderLayout.EAST);
        
        helpArea.setFont(UiConstants.FONT_MONO_12);
        helpArea.setBackground(UiConstants.COLOR_LOG_BG);
        helpArea.setForeground(UiConstants.COLOR_LOG_FG);
        helpArea.setCaretColor(UiConstants.COLOR_LOG_FG);
        helpArea.setEditable(false);
        helpArea.setLineWrap(true);
        helpArea.setWrapStyleWord(true);
        helpArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        helpArea.setToolTipText("双击帮助区域切换全屏，再次双击还原；右键可复制内容");
        JScrollPane scroll = new JScrollPane(helpArea);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // 与控制台一致：双击文本区与下方空白区域都能切换全屏（JViewport 不向上冒泡鼠标事件，需分别绑定）
        attachFullscreenToggle(helpArea);
        attachFullscreenToggle(scroll.getViewport());
        attachFullscreenToggle(scroll);
        attachPopupMenu(helpArea);
        attachPopupMenu(scroll.getViewport());
        
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * 展示指定程序类型的帮助：同步下拉选中项、窗口标题与内容。 主界面重复点击「帮助」时复用本窗口，此方法用于刷新为最新类型
     */
    public void showHelpFor(String programType) {
        String type = normalizeType(programType);
        selectTypeItem(type);
        setTitle(titleOf(type));
        helpArea.setText(ProgramHelpDoc.helpText(type));
        // 回到帮助开头，长文本也能从标题行开始阅读
        helpArea.setCaretPosition(0);
    }
    
    /**
     * 同步下拉选中项：已选中同一类型时不做处理，避免重复刷新内容
     */
    private void selectTypeItem(String programType) {
        int target = 0;
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i].equals(programType)) {
                target = i;
                break;
            }
        }
        if (typeComboBox.getSelectedIndex() == target) {
            return;
        }
        suppressTypeChange = true;
        try {
            typeComboBox.setSelectedIndex(target);
        } finally {
            suppressTypeChange = false;
        }
    }
    
    /**
     * 帮助窗口标题：如「程序帮助 - Java 程序」
     */
    private static String titleOf(String programType) {
        return "程序帮助 - " + ProgramHelpDoc.typeName(normalizeType(programType));
    }
    
    /**
     * 未配置类型时统一视为「其他程序」，保证标题、下拉选中项与帮助内容一致
     */
    private static String normalizeType(String programType) {
        return programType == null || programType.isBlank() ? ProgramConfig.TYPE_OTHER : programType;
    }
    
    /**
     * 双击切换全屏：文本区、视口与滚动容器都绑定，内容较少时双击下方空白也能触发
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
     * 右键菜单：复制选中 / 一键复制全部（帮助内容只读，不提供清空）
     */
    private void attachPopupMenu(Component comp) {
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
                JMenuItem copySelectionItem = new JMenuItem("复制选中");
                copySelectionItem.setEnabled(helpArea.getSelectionStart() != helpArea.getSelectionEnd());
                copySelectionItem.addActionListener(ev -> helpArea.copy());
                JMenuItem copyAllItem = new JMenuItem("一键复制全部");
                copyAllItem.addActionListener(ev -> copyAll());
                popup.add(copySelectionItem);
                popup.add(copyAllItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }
    
    /**
     * 复制全部帮助内容并提示结果
     */
    private void copyAll() {
        if (helpArea.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "暂无帮助内容", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        helpArea.selectAll();
        helpArea.copy();
        helpArea.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, "帮助内容已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
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
}
