package com.datasync;

import com.datasync.ui.AiQuestionMangerDialog;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * 临时冒烟测试（验证后删除）：用 Robot 模拟真实鼠标双击，验证 AI 问题管理对话框的双击全屏
 * <p>
 * 目标区域与真实用户操作一致：顶部提示文字（tipLabel）
 */
public class SmokeTestFullscreen {

    public static void main(String[] args) throws Exception {
        double scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getDefaultTransform().getScaleX();
        System.out.println("[env] DPI scale = " + scale);

        JFrame owner = new JFrame("smoke-owner");
        SwingUtilities.invokeAndWait(() -> {
            owner.setSize(900, 600);
            owner.setLocationRelativeTo(null);
            owner.setVisible(true);
        });

        final AiQuestionMangerDialog[] ref = new AiQuestionMangerDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            ref[0] = new AiQuestionMangerDialog(owner);
            new Thread(() -> ref[0].setVisible(true), "dialog-show").start();
        });
        AiQuestionMangerDialog dialog = ref[0];
        while (!dialog.isShowing()) {
            Thread.sleep(100);
        }
        Thread.sleep(500);

        Field fullscreenField = dialog.getClass().getSuperclass().getDeclaredField("fullscreen");
        fullscreenField.setAccessible(true);

        JLabel tipLabel = findTipLabel(dialog);
        if (tipLabel == null) {
            System.out.println("[RESULT] FAIL：未找到顶部提示文字组件");
            System.exit(1);
        }
        Rectangle r = tipLabel.getBounds();
        System.out.println("[check] tipLabel bounds=" + r + ", mouseListeners=" + tipLabel.getMouseListeners().length);

        Point onScreen = tipLabel.getLocationOnScreen();
        int clickX = onScreen.x + Math.min(80, Math.max(10, r.width / 3));
        int clickY = onScreen.y + r.height / 2;
        // 打印真实鼠标事件会落在哪个组件上
        Point inContent = new Point(clickX, clickY);
        SwingUtilities.convertPointFromScreen(inContent, dialog.getContentPane());
        Component deepest = SwingUtilities.getDeepestComponentAt(dialog.getContentPane(), inContent.x, inContent.y);
        System.out.println("[check] 双击落点的最深层组件 = " + (deepest == null ? "null" : deepest.getClass().getName()
                + ", mouseListeners=" + deepest.getMouseListeners().length));

        boolean before = fullscreenField.getBoolean(dialog);
        System.out.println("[step1] 双击前 fullscreen = " + before);

        Robot robot = new Robot();
        robot.setAutoDelay(60);
        Point saved = MouseInfo.getPointerInfo().getLocation();
        robot.mouseMove(clickX, clickY);
        Thread.sleep(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(600);

        boolean after = fullscreenField.getBoolean(dialog);
        System.out.println("[step2] Robot 双击后 fullscreen = " + after + ", bounds=" + dialog.getBounds());

        if (after) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(600);
            System.out.println("[step3] 再次双击 fullscreen = " + fullscreenField.getBoolean(dialog));
        }
        robot.mouseMove(saved.x, saved.y);

        boolean pass = !before && after;
        System.out.println(pass ? "[RESULT] PASS" : "[RESULT] FAIL");
        SwingUtilities.invokeLater(() -> {
            dialog.dispose();
            owner.dispose();
        });
        System.exit(pass ? 0 : 1);
    }

    /**
     * 按文本前缀在组件树中查找顶部提示文字标签
     */
    private static JLabel findTipLabel(Component component) {
        if (component instanceof JLabel label && label.getText() != null && label.getText().startsWith("选择环境后录入问题")) {
            return label;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findTipLabel(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
