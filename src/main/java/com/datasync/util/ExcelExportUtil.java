/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.util;

import com.datasync.model.AiAnswer;
import com.datasync.model.AiQuestion;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 问题 / 回复列表 Excel 导出工具
 * <p>
 * 提供问题列表与问题回复列表的 Excel 导出能力：<br> 1. {@link #exportQuestions(File, List, Map)}：将问题列表导出为 Excel（.xlsx）；<br> 2.
 * {@link #exportAnswers(File, List, Map)}：将问题回复列表导出为 Excel（.xlsx）。
 *
 * @author liuweiping
 * @date 2026-09-29
 **/
public final class ExcelExportUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(ExcelExportUtil.class);
    
    /**
     * 训练时间展示格式（毫秒时间戳按本地时区格式化）
     */
    private static final DateTimeFormatter TRAINING_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 问题列表导出表头
     */
    private static final String[] QUESTION_HEADERS = {"问题ID", "问题", "固定回复", "训练参数", "所属用户", "所属项目", "分类", "训练开始时间",
            "训练结束时间", "训练耗时（单位秒）", "备注", "回复ID"};
    
    /**
     * 问题回复列表导出表头
     */
    private static final String[] ANSWER_HEADERS = {"回复ID", "问题", "回复内容", "所属用户", "所属项目", "是否来源训练"};
    
    /**
     * 问题列表 Sheet 名称
     */
    private static final String QUESTION_SHEET_NAME = "问题列表";
    
    /**
     * 问题回复列表 Sheet 名称
     */
    private static final String ANSWER_SHEET_NAME = "问题回复列表";
    
    private ExcelExportUtil() {
    }
    
    /**
     * 导出问题列表到 Excel 文件
     *
     * @param outputFile    输出文件（.xlsx）
     * @param questions     问题列表
     * @param userIdDisplay userId 到展示名称的映射（格式「用户名称（用户ID）」，映射不存在时原样输出 userId）
     */
    public static void exportQuestions(File outputFile, List<AiQuestion> questions, Map<String, String> userIdDisplay) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(QUESTION_SHEET_NAME);
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            writeHeader(sheet, headerStyle, QUESTION_HEADERS);
            
            for (int i = 0; i < questions.size(); i++) {
                AiQuestion q = questions.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(q.getId() != null ? String.valueOf(q.getId()) : "");
                row.createCell(1).setCellValue(nullSafe(q.getQuestion()));
                row.createCell(2).setCellValue(nullSafe(q.getAnswer()));
                row.createCell(3).setCellValue(nullSafe(q.getTrainingParam()));
                row.createCell(4).setCellValue(formatUserId(q.getUserId(), userIdDisplay));
                row.createCell(5).setCellValue(nullSafe(q.getProjectName()));
                row.createCell(6).setCellValue(nullSafe(q.getQuestionClassify()));
                row.createCell(7).setCellValue(formatTimestamp(q.getStartTrainingTime()));
                row.createCell(8).setCellValue(formatTimestamp(q.getLastTrainingTime()));
                row.createCell(9).setCellValue(calcTrainingCostSeconds(q.getStartTrainingTime(), q.getLastTrainingTime()));
                row.createCell(10).setCellValue(nullSafe(q.getRemark()));
                row.createCell(11).setCellValue(q.getAnswerId() != null ? String.valueOf(q.getAnswerId()) : "");
            }
            
            autoSizeColumns(sheet, QUESTION_HEADERS.length);
            writeWorkbook(workbook, outputFile);
        }
        logger.info("[ExcelExport] 问题列表已导出: {}，共 {} 条", outputFile.getAbsolutePath(), questions.size());
    }
    
    /**
     * 导出问题回复列表到 Excel 文件
     *
     * @param outputFile    输出文件（.xlsx）
     * @param answers       回复列表
     * @param userIdDisplay userId 到展示名称的映射（格式「用户名称（用户ID）」，映射不存在时原样输出 userId）
     */
    public static void exportAnswers(File outputFile, List<AiAnswer> answers, Map<String, String> userIdDisplay) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(ANSWER_SHEET_NAME);
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            writeHeader(sheet, headerStyle, ANSWER_HEADERS);
            
            for (int i = 0; i < answers.size(); i++) {
                AiAnswer a = answers.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(a.getId() != null ? String.valueOf(a.getId()) : "");
                row.createCell(1).setCellValue(nullSafe(a.getQuery()));
                row.createCell(2).setCellValue(nullSafe(a.getAnswer()));
                row.createCell(3).setCellValue(formatUserId(a.getUserId(), userIdDisplay));
                row.createCell(4).setCellValue(nullSafe(a.getProjectName()));
                row.createCell(5).setCellValue(Boolean.TRUE.equals(a.getSourceTraining()) ? "是" : "否");
            }
            
            autoSizeColumns(sheet, ANSWER_HEADERS.length);
            writeWorkbook(workbook, outputFile);
        }
        logger.info("[ExcelExport] 问题回复列表已导出: {}，共 {} 条", outputFile.getAbsolutePath(), answers.size());
    }
    
    /**
     * 创建表头样式（加粗 + 灰色背景）
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    /**
     * 写入表头行
     */
    private static void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
    
    /**
     * 自动调整列宽（限制最大宽度避免过宽）
     */
    private static void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            // 限制最大列宽为 60 个字符宽度
            int width = sheet.getColumnWidth(i);
            int maxWidth = 60 * 256;
            // 前三列宽度至少为 30 个字符宽度
            if (columnCount >= 3 && (i == 1 || i == 2)) {
                int minWidth = 30 * 256;
                if (width < minWidth) {
                    sheet.setColumnWidth(i, minWidth);
                }
            }
            if (width > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            }
        }
    }
    
    /**
     * 写入工作簿到文件
     */
    private static void writeWorkbook(Workbook workbook, File outputFile) throws IOException {
        try (OutputStream out = new FileOutputStream(outputFile)) {
            workbook.write(out);
        }
    }
    
    /**
     * 毫秒时间戳格式化为 yyyy-MM-dd HH:mm:ss（null 返回空字符串）
     */
    private static String formatTimestamp(Long timestamp) {
        if (timestamp == null || timestamp <= 0L) {
            return "";
        }
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(TRAINING_TIME_FORMATTER);
    }
    
    /**
     * 计算训练耗时秒数（结束时间 - 开始时间，两者均有效时返回秒数，否则返回空字符串）
     */
    private static String calcTrainingCostSeconds(Long startTimestamp, Long endTimestamp) {
        if (startTimestamp == null || endTimestamp == null || startTimestamp <= 0L || endTimestamp <= 0L) {
            return "";
        }
        double costSeconds = (endTimestamp - startTimestamp) / 1000.d;
        return costSeconds >= 0 ? String.valueOf(costSeconds) : "";
    }
    
    /**
     * null 安全取字符串值（null 返回空字符串）
     */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
    
    /**
     * 格式化用户ID为展示格式「用户名称（用户ID）」（userId 为空返回空字符串，映射中不存在时原样输出 userId）
     */
    private static String formatUserId(String userId, Map<String, String> userIdDisplay) {
        if (userId == null || userId.isEmpty()) {
            return "";
        }
        if (userIdDisplay != null && userIdDisplay.containsKey(userId)) {
            return userIdDisplay.get(userId);
        }
        return userId;
    }
}
