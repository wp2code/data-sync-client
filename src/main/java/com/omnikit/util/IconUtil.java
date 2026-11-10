package com.omnikit.util;

import com.omnikit.model.DbType;
import com.omnikit.model.ProgramConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 为 MySQL / PostgreSQL 生成小尺寸程序化图标，供 ComboBox、JTable、JLabel 等组件使用。
 */
public final class IconUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(IconUtil.class);
    
    private IconUtil() {
    }
    
    public static final int ICON_SIZE = 20;
    
    private static ImageIcon mysqlIcon;
    
    private static ImageIcon postgresqlIcon;
    
    private static ImageIcon javaIcon;
    
    private static ImageIcon pythonIcon;
    
    private static ImageIcon httpServerIcon;
    
    private static ImageIcon otherProgramIcon;
    
    private static ImageIcon groupIcon;
    
    private static ImageIcon scriptsGroupIcon;
    
    private static ImageIcon groupArrowExpandedIcon;
    
    private static ImageIcon groupArrowCollapsedIcon;
    
    /**
     * 分组折叠/展开箭头尺寸（比图标略小，不抢标题视觉重心）
     */
    private static final int ARROW_ICON_SIZE = 12;
    
    private static final Color ARROW_ICON_COLOR = new Color(0x8A93A6);
    
    /**
     * 应用图标
     */
    public static ImageIcon createAppIcon() {
        return loadSvgIcon("assets/icon.svg");
    }
    
    public static Icon success() {
        return loadSvgIcon("assets/success.svg");
    }
    // ── 通过 DbType 枚举获取图标 ──
    
    public static ImageIcon getDbTypeIcon(DbType dbType) {
        if (dbType == DbType.POSTGRESQL) {
            if (postgresqlIcon == null) {
                postgresqlIcon = loadSvgIcon("assets/postgresql.svg");
            }
            return postgresqlIcon;
        }
        if (mysqlIcon == null) {
            mysqlIcon = loadSvgIcon("assets/mysql.svg");
        }
        return mysqlIcon;
    }
    
    /**
     * 通过字符串获取图标，兼容 "mysql" / "postgresql" / "MySQL" / "PostgreSQL"
     */
    public static ImageIcon getDbTypeIcon(String dbTypeStr) {
        return getDbTypeIcon(DbType.fromString(dbTypeStr));
    }
    
    // ── 通过程序类型获取图标 ──
    
    /**
     * 按程序类型（{@link ProgramConfig#TYPE_JAVA} / {@link ProgramConfig#TYPE_PYTHON} /
     * {@link ProgramConfig#TYPE_OTHER}）获取图标，未知类型按「其他程序」处理
     */
    public static ImageIcon getProgramTypeIcon(String programType) {
        if (ProgramConfig.TYPE_JAVA.equals(programType)) {
            if (javaIcon == null) {
                javaIcon = loadSvgIcon("assets/java.svg");
            }
            return javaIcon;
        }
        if (ProgramConfig.TYPE_PYTHON.equals(programType)) {
            if (pythonIcon == null) {
                pythonIcon = loadSvgIcon("assets/python.svg");
            }
            return pythonIcon;
        }
        if (ProgramConfig.TYPE_HTTP_SERVER.equals(programType)) {
            if (httpServerIcon == null) {
                httpServerIcon = loadSvgIcon("assets/http.svg");
            }
            return httpServerIcon;
        }
        if (otherProgramIcon == null) {
            otherProgramIcon = loadSvgIcon("assets/generic.svg");
        }
        return otherProgramIcon;
    }
    
    /**
     * 分组标题图标（assets/group.svg），供程序列表按分组聚合展示时使用
     */
    public static ImageIcon getGroupIcon() {
        if (groupIcon == null) {
            groupIcon = loadSvgIcon("assets/group.svg");
        }
        return groupIcon;
    }
    
    /**
     * 脚本分组标题图标（assets/scripts_group.svg），供脚本列表按分组聚合展示时使用
     */
    public static ImageIcon getScriptsGroupIcon() {
        if (scriptsGroupIcon == null) {
            scriptsGroupIcon = loadSvgIcon("assets/scripts_group.svg");
        }
        return scriptsGroupIcon;
    }
    
    /**
     * 分组折叠/展开箭头：展开态（▾，点击收起）与折叠态（▸，点开展开）
     */
    public static ImageIcon getGroupArrowIcon(boolean expanded) {
        if (expanded) {
            if (groupArrowExpandedIcon == null) {
                groupArrowExpandedIcon = createChevronIcon(true);
            }
            return groupArrowExpandedIcon;
        }
        if (groupArrowCollapsedIcon == null) {
            groupArrowCollapsedIcon = createChevronIcon(false);
        }
        return groupArrowCollapsedIcon;
    }
    
    /**
     * 绘制箭头：down 为向下箭头（展开态），否则为向右箭头（折叠态）；圆头描边保证小尺寸下依然清晰
     */
    private static ImageIcon createChevronIcon(boolean down) {
        BufferedImage img = new BufferedImage(ARROW_ICON_SIZE, ARROW_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ARROW_ICON_COLOR);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        Path2D.Float arrow = new Path2D.Float();
        if (down) {
            arrow.moveTo(2.5f, 4.5f);
            arrow.lineTo(6f, 8f);
            arrow.lineTo(9.5f, 4.5f);
        } else {
            arrow.moveTo(4.5f, 2.5f);
            arrow.lineTo(8f, 6f);
            arrow.lineTo(4.5f, 9.5f);
        }
        g.draw(arrow);
        
        g.dispose();
        return new ImageIcon(img);
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
            logger.warn("Failed to load SVG icon: {}, falling back to generated icon", resourceName, e);
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
        if (resourceName.contains("java")) {
            return createLetterIcon('J', new Color(0xE76F00));
        }
        if (resourceName.contains("python")) {
            return createLetterIcon('P', new Color(0x3776AB));
        }
        if (resourceName.contains("http")) {
            return createLetterIcon('H', new Color(0x4A9EFF));
        }
        if (resourceName.contains("generic")) {
            return createLetterIcon('▶', new Color(0x7A8290));
        }
        if (resourceName.contains("group")) {
            return createGroupFolderIcon();
        }
        return createMySqlIcon();
    }
    
    /**
     * group.svg 加载失败时的后备图标：中性色文件夹（左上标签页 + 下方圆角主体）
     */
    private static ImageIcon createGroupFolderIcon() {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g.setColor(new Color(0x409EFF));
        g.fill(new RoundRectangle2D.Float(1, 4, 8, 5, 2, 2));
        g.fill(new RoundRectangle2D.Float(1, 7, ICON_SIZE - 2, ICON_SIZE - 9, 3, 3));
        
        g.dispose();
        return new ImageIcon(img);
    }
    
    /**
     * 通用后备图标：指定颜色圆角方形背景 + 白色字符
     */
    private static ImageIcon createLetterIcon(char letter, Color bgColor) {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        RoundRectangle2D.Float bg = new RoundRectangle2D.Float(1, 1, ICON_SIZE - 2, ICON_SIZE - 2, 6, 6);
        g.setColor(bgColor);
        g.fill(bg);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        String text = String.valueOf(letter);
        int x = (ICON_SIZE - fm.stringWidth(text)) / 2;
        int y = (ICON_SIZE - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        
        g.dispose();
        return new ImageIcon(img);
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
