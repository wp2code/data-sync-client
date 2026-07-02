/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.components;

import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import javax.swing.*;

/**
 * 链接标签 - 支持点击跳转浏览器、鼠标悬停变色、下划线效果
 *
 * @author liuweiping
 * @date 2026-06-30
 **/
public class LinkJLabel extends JLabel {
    
    private String url;
    
    private Color normalColor = new Color(0x1A, 0x73, 0xE8);  // Google Blue
    
    private Color hoverColor = new Color(59, 72, 221);   // 深蓝
    
    //    private final Color visitedColor = new Color(0x68, 0x1D, 0xA8);  // 紫色（已访问）
    private final Color visitedColor = normalColor;  // 紫色（已访问）
    
    private boolean underline;
    
    private boolean visited = false;
    
    public LinkJLabel() {
        this("", "");
    }
    
    public LinkJLabel(String text, String url) {
        super(text);
        this.url = (url != null) ? url : "";
        initStyle();
        initMouseListener();
    }
    
    private void initStyle() {
        setForeground(normalColor);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Font font = getFont();
        if (font != null && underline) {
            setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
        }
    }
    
    private void initMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (url != null && !url.isEmpty()) {
                    openUrl(url);
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    setForeground(hoverColor);
                    if (underline) {
                        setText(buildUnderlineHtml(getText()));
                    }
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                setForeground(visited ? visitedColor : normalColor);
                if (underline) {
                    setText(stripHtml(getText()));
                }
            }
        });
    }
    
    /**
     * 在系统默认浏览器中打开 URL
     */
    private void openUrl(String url) {
        try {
            String targetUrl = url.startsWith("http://") || url.startsWith("https://") ? url : "https://" + url;
            Desktop.getDesktop().browse(new URI(targetUrl));
            visited = true;
            setForeground(visitedColor);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "无法打开链接: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String buildUnderlineHtml(String plainText) {
        return "<html>" + plainText + "</html>";
    }
    
    private String stripHtml(String htmlText) {
        if (htmlText == null) {
            return "";
        }
        return htmlText.replaceAll("<html>", "").replaceAll("</html>", "");
    }
    
    // ── Getter/Setter ──
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public void setNormalColor(Color normalColor) {
        this.normalColor = normalColor;
        if (!visited) {
            setForeground(normalColor);
        }
    }
    
    public void setHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
    }
    
    public void setUnderline(boolean underline) {
        this.underline = underline;
    }
}
