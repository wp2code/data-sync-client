/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.util;

import com.datasync.model.DataSource;
import java.awt.*;
import java.awt.datatransfer.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/**
 * @author liuweiping
 * @date 2026-06-26
 **/
public class GlobalUtil {
    
    private static final Map<String, DataSource> currDataSourceMap = new HashMap<>(2);
    
    public static void addSrcDataSource(DataSource dataSource) {
        currDataSourceMap.put("SOURCE", dataSource);
    }
    
    public static void addTargetDataSource(DataSource dataSource) {
        currDataSourceMap.put("TARGET", dataSource);
    }
    
    public static String getSrcDataSource() {
        final DataSource source = currDataSourceMap.get("SOURCE");
        if (source == null) {
            return null;
        }
        return source.getSourceName();
    }
    
    public static void removeSrcDataSource() {
        currDataSourceMap.remove("SOURCE");
    }
    
    public static void removeTargetDataSource() {
        currDataSourceMap.remove("TARGET");
    }
    
    public static String getTargetDataSource() {
        final DataSource target = currDataSourceMap.get("TARGET");
        if (target == null) {
            return null;
        }
        return target.getSourceName();
    }
    
    /**
     * 获取面板中所有勾选的复选框文本。
     */
    public static List<String> getCheckedTables(JPanel panel) {
        List<String> checked = new ArrayList<>();
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JCheckBox) {
                JCheckBox cb = (JCheckBox) comp;
                if (cb.isSelected()) {
                    checked.add(cb.getText());
                }
            }
        }
        return checked;
    }
    
    /**
     * 从 ALTER TABLE 语句中提取完整表名（含 schema 前缀） 如 ALTER TABLE "public"."users" → "public"."users"
     */
    public static String extractTableNameFromAlter(String alterSql) {
        String upper = alterSql.toUpperCase();
        int idx = upper.indexOf("ALTER TABLE ");
        if (idx < 0) {
            return null;
        }
        String rest = alterSql.substring(idx + 12).trim();
        
        // 提取到下一个空格或行尾（即完整表名部分）
        int spaceIdx = rest.indexOf(' ');
        if (spaceIdx > 0) {
            rest = rest.substring(0, spaceIdx);
        }
        // 去掉末尾可能的分号
        if (rest.endsWith(";")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest;
    }
    
    /**
     * 清洗按分号拆分后的 SQL 片段：去掉注释行，提取真正的 SQL 语句。 返回 null 表示该片段是纯注释或空白，应跳过。
     */
    public static String cleanSql(String rawFragment) {
        if (rawFragment == null) {
            return null;
        }
        // 按行拆分，过滤掉注释行和空行
        String[] lines = rawFragment.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append("\n");
            }
            result.append(line); // 保留原始缩进
        }
        String sql = result.toString().trim();
        return sql.isEmpty() ? null : sql;
    }
    
    /**
     * @param filePath
     * @param fileName
     * @return
     */
    public static String getFullFilePath(String filePath, String fileName) {
        if (filePath.endsWith(".sql")) {
            return filePath;
        }
        return filePath.replaceAll("\\\\", "/") + "/" + fileName;
    }
    
    /**
     * 将文本复制到剪贴板
     *
     * @param text   文本
     * @param dialog 对话框，用于显示提示信息
     */
    public static void copy(String text, Component dialog) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        JOptionPane.showMessageDialog(dialog, "已复制到剪贴板！", "提示", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static Object[] toggleFullscreen(boolean fullscreen, Component component, Rectangle normalBounds) {
        Object[] objects = new Object[2];
        if (!fullscreen) {
            normalBounds = component.getBounds();
            Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            component.setBounds(screenBounds);
            fullscreen = true;
        } else {
            if (normalBounds != null) {
                component.setBounds(normalBounds);
            }
            fullscreen = false;
        }
        objects[0] = normalBounds;
        objects[1] = fullscreen;
        return objects;
    }
    
    /**
     * 截断 SQL 用于日志显示
     */
    public static String truncateSql(String sql) {
        if (sql == null) {
            return "";
        }
        String s = sql.replaceAll("\\s+", " ").trim();
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
    
    /**
     * 拆分 SQL 脚本为独立语句。 支持跳过 -- 行注释、
     */
    public static List<String> splitSqlStatements(String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';
            
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            
            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
                continue;
            }
            
            if (c == ';' && !inSingleQuote && !inDoubleQuote) {
                String sql = current.toString().trim();
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
                current = new StringBuilder();
                continue;
            }
            
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            statements.add(last);
        }
        return statements;
    }
}
