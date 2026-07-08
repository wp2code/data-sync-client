package com.datasync.ui;

import com.datasync.core.GitLabService;
import com.datasync.model.GitLabAuthConfig;
import com.datasync.util.IconUtil;
import com.datasync.util.LogUtil;
import com.datasync.util.SQLiteConfigUtil;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.gitlab4j.api.GitLabApi;

/**
 * GitLab 配置管理对话框：支持配置地址、账号、密码，持久化保存并测试登录
 *
 * @author liuweiping
 * @date 2026-07-07
 **/
public class GitLabMangerDialog extends AbsDialog {
    
    private JTextField nameField;
    
    private JTextField urlField;
    
    private JTextField usernameField;
    
    private JPasswordField passwordField;
    
    private JTextArea remarkArea;
    
    
    public GitLabMangerDialog(Frame owner) {
        super( owner, "GitLab配置", true, 520, 440);
        initUI();
        loadSavedConfig();
    }
    
    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        
        // 表单面板
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(20, 24, 10, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        JLabel nameLabel = new JLabel("配置名称:");
        nameLabel.setFont(UiConstants.FONT_SANS_12);
        nameField = new JTextField(30);
        nameField.setFont(UiConstants.FONT_SANS_12);
        nameField.setToolTipText("为此 GitLab 配置取个名称");
        
        JLabel urlLabel = new JLabel("GitLab 地址:");
        urlLabel.setFont(UiConstants.FONT_SANS_12);
        urlField = new JTextField(30);
        urlField.setFont(UiConstants.FONT_SANS_12);
        urlField.setToolTipText("例如: https://gitlab.example.com");
        
        JLabel usernameLabel = new JLabel("用户名:");
        usernameLabel.setFont(UiConstants.FONT_SANS_12);
        usernameField = new JTextField(30);
        usernameField.setFont(UiConstants.FONT_SANS_12);
        
        JLabel passwordLabel = new JLabel("密码:");
        passwordLabel.setFont(UiConstants.FONT_SANS_12);
        passwordField = new JPasswordField(30);
        passwordField.setFont(UiConstants.FONT_SANS_12);
        passwordField.setEchoChar('●');
        
        JLabel remarkLabel = new JLabel("备注:");
        remarkLabel.setFont(UiConstants.FONT_SANS_12);
        remarkArea = new JTextArea(3, 30);
        remarkArea.setFont(UiConstants.FONT_SANS_12);
        remarkArea.setLineWrap(true);
        remarkArea.setWrapStyleWord(true);
        JScrollPane remarkScroll = new JScrollPane(remarkArea);
        remarkScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        addFormRow(formPanel, gbc, 0, nameLabel, nameField);
        addFormRow(formPanel, gbc, 1, urlLabel, urlField);
        addFormRow(formPanel, gbc, 2, usernameLabel, usernameField);
        addFormRow(formPanel, gbc, 3, passwordLabel, passwordField);
        addFormRow(formPanel, gbc, 4, remarkLabel, remarkScroll);
        
        add(formPanel, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        JButton saveBtn = new JButton("登录&保存");
        saveBtn.setFont(UiConstants.FONT_SANS_12);
        saveBtn.addActionListener(e -> saveConfig());
        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(UiConstants.FONT_SANS_12);
        cancelBtn.addActionListener(e -> dispose());
        
        // 回车键触发保存
        getRootPane().setDefaultButton(saveBtn);
        // ESC 关闭
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);
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
    
    private void loadSavedConfig() {
        GitLabAuthConfig config = SQLiteConfigUtil.getInstance().loadGitLabAuthConfig();
        if (config != null) {
            nameField.setText(config.getName() != null ? config.getName() : "");
            urlField.setText(config.getUrl());
            usernameField.setText(config.getUsername());
            passwordField.setText(config.getPassword());
            remarkArea.setText(config.getRemark() != null ? config.getRemark() : "");
        }
    }
    
    private GitLabAuthConfig collectConfig() {
        GitLabAuthConfig config = new GitLabAuthConfig();
        config.setName(nameField.getText().trim());
        config.setUrl(urlField.getText().trim());
        config.setUsername(usernameField.getText().trim());
        config.setPassword(new String(passwordField.getPassword()));
        config.setRemark(remarkArea.getText().trim());
        return config;
    }
    
    private boolean validateConfig(GitLabAuthConfig config) {
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入 GitLab 地址", "校验失败", JOptionPane.WARNING_MESSAGE);
            urlField.requestFocus();
            return false;
        }
        if (config.getUsername() == null || config.getUsername().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入用户名", "校验失败", JOptionPane.WARNING_MESSAGE);
            usernameField.requestFocus();
            return false;
        }
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入密码", "校验失败", JOptionPane.WARNING_MESSAGE);
            passwordField.requestFocus();
            return false;
        }
        return true;
    }
    
    private void saveConfig() {
        GitLabAuthConfig config = collectConfig();
        if (!validateConfig(config)) {
            return;
        }
        boolean success = SQLiteConfigUtil.getInstance().saveGitLabAuthConfig(config);
        if (success) {
            gitLabLogin(config);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "GitLab 配置保存失败", "保存失败", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void gitLabLogin(GitLabAuthConfig config) {
        try {
            final GitLabApi gitLabApi = GitLabService.getInstance().login(config, false);
            LogUtil.appendLog(LogUtil.success("GitLab登录保存成功！Token: " + gitLabApi.getAuthToken()), LogUtil.DATA_SYNC_UI_LOG_AREA);
            SwingUtilities.invokeLater(
                    () -> JOptionPane.showMessageDialog(this, "登录保存成功！", "测试登录", JOptionPane.INFORMATION_MESSAGE, IconUtil.success()));
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "登录失败: " + ex.getMessage(), "登录", JOptionPane.ERROR_MESSAGE));
        }
    }
}
