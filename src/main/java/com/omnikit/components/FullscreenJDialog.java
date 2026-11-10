package com.omnikit.components;

import java.awt.*;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-07-06
 **/
public class FullscreenJDialog extends AbsDialog {
    
    protected boolean fullscreen = false;
    
    protected Rectangle normalBounds;
    
    private static final String FULL_FLAG = "toggleFullscreen_";
    
    private static final String EXIT_FULL_FLAG = "exitFullscreen_";
    
    public FullscreenJDialog(String id, Frame owner, String title, boolean modal, int width, int height) {
        super(owner, title, modal, width, height);
        final JRootPane jRootPane = this.getRootPane();
        KeyStroke f11 = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0);
        jRootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(f11, FULL_FLAG + id);
        jRootPane.getActionMap().put(FULL_FLAG + id, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                toggleFullscreen();
            }
        });
        KeyStroke esc = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        jRootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, EXIT_FULL_FLAG + id);
        jRootPane.getActionMap().put(EXIT_FULL_FLAG + id, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (fullscreen) {
                    toggleFullscreen();
                }
            }
        });
    }
    
    /**
     * 以全屏状态首次显示：未全屏时先切到全屏（构造时的尺寸被记为退出全屏的还原尺寸），再显示窗口
     */
    public void showFullscreen() {
        if (!fullscreen) {
            toggleFullscreen();
        }
        setVisible(true);
    }
    
    protected void toggleFullscreen() {
        if (!fullscreen) {
            normalBounds = getBounds();
            Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            setBounds(screenBounds);
            fullscreen = true;
        } else {
            if (normalBounds != null) {
                setBounds(normalBounds);
            }
            fullscreen = false;
        }
    }
}
