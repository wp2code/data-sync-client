package com.datasync.util;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

/**
 * 应用图标绘制工具。
 */
public final class IconUtil {
    private IconUtil() {}

    /** 创建 64x64 的应用图标（蓝紫渐变背景 + 白色同步箭头） */
    public static ImageIcon createAppIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 蓝紫渐变圆角矩形背景
        GradientPaint gradient = new GradientPaint(0, 0, new Color(79, 70, 229), size, size, new Color(124, 58, 237));
        g2d.setPaint(gradient);
        g2d.fillRoundRect(2, 2, size - 4, size - 4, 16, 16);

        // 白色同步箭头
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int cx = size / 2;
        int cy = size / 2;

        // 上箭头
        GeneralPath upArrow = new GeneralPath();
        upArrow.moveTo(cx - 8, cy - 4);
        upArrow.lineTo(cx, cy - 16);
        upArrow.lineTo(cx + 8, cy - 4);
        g2d.draw(upArrow);

        // 下箭头
        GeneralPath downArrow = new GeneralPath();
        downArrow.moveTo(cx - 8, cy + 4);
        downArrow.lineTo(cx, cy + 16);
        downArrow.lineTo(cx + 8, cy + 4);
        g2d.draw(downArrow);

        // 垂直连接线
        g2d.drawLine(cx, cy - 16, cx, cy - 8);
        g2d.drawLine(cx, cy + 8, cx, cy + 16);

        g2d.dispose();
        return new ImageIcon(img);
    }
}
