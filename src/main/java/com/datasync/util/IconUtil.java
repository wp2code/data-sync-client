package com.datasync.util;

import com.datasync.model.DbType;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

/**
 * 为 MySQL / PostgreSQL 生成小尺寸程序化图标，供 ComboBox、JTable、JLabel 等组件使用。
 */
public final class IconUtil {
    
    private IconUtil() {
    }
    
    public static final int ICON_SIZE = 20;
    
    private static ImageIcon mysqlIcon;
    
    private static ImageIcon postgresqlIcon;
    
    /**
     * 应用图标
     */
    public static ImageIcon createAppIcon() {
        return loadSvgIcon("icon.svg");
    }
    
    public static Icon success() {
        return loadSvgIcon("success.svg");
    }
    // ── 通过 DbType 枚举获取图标 ──
    
    public static ImageIcon getDbTypeIcon(DbType dbType) {
        if (dbType == DbType.POSTGRESQL) {
            if (postgresqlIcon == null) {
                postgresqlIcon = loadSvgIcon("postgresql.svg");
            }
            return postgresqlIcon;
        }
        if (mysqlIcon == null) {
            mysqlIcon = loadSvgIcon("mysql.svg");
        }
        return mysqlIcon;
    }
    
    /**
     * 通过字符串获取图标，兼容 "mysql" / "postgresql" / "MySQL" / "PostgreSQL"
     */
    public static ImageIcon getDbTypeIcon(String dbTypeStr) {
        return getDbTypeIcon(DbType.fromString(dbTypeStr));
    }
    
    /**
     * 从 classpath 加载 SVG 图标文件，转换为指定尺寸的 ImageIcon
     */
    private static ImageIcon loadSvgIcon(String resourceName) {
        try {
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) ICON_SIZE);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) ICON_SIZE);
            
            try (InputStream svgStream = IconUtil.class.getResourceAsStream("/" + resourceName)) {
                if (svgStream != null) {
                    TranscoderInput input = new TranscoderInput(svgStream);
                    ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
                    TranscoderOutput output = new TranscoderOutput(pngStream);
                    transcoder.transcode(input, output);
                    
                    byte[] pngBytes = pngStream.toByteArray();
                    BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(pngBytes));
                    return new ImageIcon(img);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load SVG icon: " + resourceName + ", falling back to generated icon");
        }
        
        // SVG 加载失败时的后备图标
        return createFallbackIcon(resourceName);
    }
    
    /**
     * SVG 加载失败时的后备图标
     */
    private static ImageIcon createFallbackIcon(String resourceName) {
        if (resourceName.contains("postgresql")) {
            return createPostgreSqlIcon();
        }
        return createMySqlIcon();
    }
    
    // ── 后备图标绘制 ──
    
    private static ImageIcon createMySqlIcon() {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 圆角方形背景 — MySQL 海蓝色
        RoundRectangle2D.Float bg = new RoundRectangle2D.Float(1, 1, ICON_SIZE - 2, ICON_SIZE - 2, 6, 6);
        g.setColor(new Color(0x00758F));
        g.fill(bg);
        
        // 白色 "M" 字母
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        String letter = "M";
        int x = (ICON_SIZE - fm.stringWidth(letter)) / 2;
        int y = (ICON_SIZE - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(letter, x, y);
        
        g.dispose();
        return new ImageIcon(img);
    }
    
    private static ImageIcon createPostgreSqlIcon() {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 圆角方形背景 — PostgreSQL 深蓝色
        RoundRectangle2D.Float bg = new RoundRectangle2D.Float(1, 1, ICON_SIZE - 2, ICON_SIZE - 2, 6, 6);
        g.setColor(new Color(0x336791));
        g.fill(bg);
        
        // 简化的象头侧影
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // 用 Path2D 画简化的大象侧脸剪影
        Path2D.Float elephant = new Path2D.Float();
        // 额头 → 鼻子上端
        elephant.moveTo(5, 5);
        elephant.curveTo(5, 3, 8, 2, 10, 4);
        // 鼻子向下
        elephant.curveTo(12, 6, 14, 10, 13, 15);
        elephant.curveTo(12, 17, 10, 17, 9, 16);
        // 鼻子向上弯
        elephant.curveTo(8, 14, 10, 12, 11, 14);
        elephant.curveTo(13, 17, 11, 17, 10, 15);
        elephant.curveTo(8, 12, 6, 14, 5, 13);
        elephant.curveTo(4, 12, 4, 8, 5, 5);
        elephant.closePath();
        
        g.fill(elephant);
        
        g.dispose();
        return new ImageIcon(img);
    }
    
    /**
     * 创建一个显示图标 + 文本的 JLabel（用于列表/表格渲染器复用）。
     */
    public static JLabel createIconLabel(String text, DbType dbType) {
        JLabel label = new JLabel(text);
        label.setIcon(getDbTypeIcon(dbType));
        label.setIconTextGap(4);
        return label;
    }
}
