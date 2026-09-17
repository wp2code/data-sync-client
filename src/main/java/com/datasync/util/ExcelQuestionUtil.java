/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.util;

import com.datasync.model.AiQuestion;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 问题 Excel 导入工具
 * <p>
 * 提供「界面手动录入」的 Excel 支持能力：<br>
 * 1. {@link #generateTemplate(File)}：运行时生成示例模板（表头 + 示例数据 + 填写说明页），无需预置二进制资源；<br>
 * 2. {@link #parseQuestions(File)}：解析 .xlsx / .xls 文件（WorkbookFactory 自动识别格式），<br>
 * 表头行自动跳过，问题为空的行忽略，开启训练 / 优先级别做格式校验，错误信息带 Excel 行号。
 *
 * @author liuweiping
 * @date 2026-09-17
 **/
public final class ExcelQuestionUtil {

    private static final Logger logger = LoggerFactory.getLogger(ExcelQuestionUtil.class);

    /**
     * 模板 / 导入文件的表头（列顺序固定，与手动录入网格一致；用户ID列新增于分类之前，固定答案列位于末尾，兼容旧模板文件）
     */
    static final String[] TEMPLATE_HEADERS = {"问题", "用户ID", "分类", "开启训练", "优先级别", "训练参数", "固定答案"};

    /**
     * 模板问题数据 Sheet 名称
     */
    private static final String TEMPLATE_SHEET_NAME = "问题列表";

    /**
     * 模板填写说明 Sheet 名称
     */
    private static final String GUIDE_SHEET_NAME = "填写说明";

    /**
     * 表头首列名称（用于识别并跳过表头行）
     */
    private static final String HEADER_QUESTION = "问题";

    /**
     * 开启训练列的可选值
     */
    private static final String ENABLE_TEXT_ON = "开启";

    private static final String ENABLE_TEXT_OFF = "不开启";

    private ExcelQuestionUtil() {
    }

    /**
     * 生成 Excel 导入模板（.xlsx：问题列表 + 填写说明两个 Sheet）
     *
     * @param outputFile 模板输出文件（含 .xlsx 扩展名）
     */
    public static void generateTemplate(File outputFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(TEMPLATE_SHEET_NAME);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }

            String[][] samples = {
                    {"如何新增数据源？", "u10001", "数据源管理", "开启", "0", "datasource", "在数据源管理页面点击『新增』，填写连接信息后保存。"},
                    {"数据同步失败如何排查？", "u10002", "数据同步", "开启", "1", "sync", "查看日志中的错误提示，检查网络与账号权限。"},
                    {"支持哪些数据库类型？", "u10003", "常见问题", "不开启", "", "", "支持 MySQL、PostgreSQL 等常见数据库。"}
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    row.createCell(c).setCellValue(samples[r][c]);
                }
            }

            Sheet guide = workbook.createSheet(GUIDE_SHEET_NAME);
            String[][] guideLines = {
                    {"AI 问题导入模板 - 填写说明"},
                    {"1. 请在「" + TEMPLATE_SHEET_NAME + "」页填写，第一行为表头（请勿修改列名与顺序），示例数据可删除后填写实际内容"},
                    {"2. 问题：必填，内容为空的行导入时自动忽略"},
                    {"3. 用户ID：必填，标识问题归属用户，会随请求提交到远端接口；为空时导入到手动录入页面保存会校验失败"},
                    {"4. 分类：选填，可留空"},
                    {"5. 开启训练：仅支持『" + ENABLE_TEXT_ON + "』或『" + ENABLE_TEXT_OFF + "』，留空默认" + ENABLE_TEXT_ON},
                    {"6. 优先级别：整数（越大越优先），留空默认 0"},
                    {"7. 训练参数：选填，可留空"},
                    {"8. 固定答案：选填，可留空；问题命中时优先返回该固定答案"},
                    {"9. 填写完成后保存文件，在客户端「界面手动录入」页点击『导入 Excel』选择该文件"}
            };
            for (int i = 0; i < guideLines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(guideLines[i][0]);
            }
            guide.setColumnWidth(0, 90 * 256);

            try (OutputStream out = new FileOutputStream(outputFile)) {
                workbook.write(out);
            }
        }
        logger.info("[Excel] 导入模板已生成: {}", outputFile.getAbsolutePath());
    }

    /**
     * 解析结果：问题列表与错误信息（errors 非空时不应导入任何数据）
     */
    public static class ParseResult {

        private final List<AiQuestion> questions = new ArrayList<>();

        private final List<String> errors = new ArrayList<>();

        public List<AiQuestion> getQuestions() {
            return questions;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * 解析 Excel 文件中的问题（自动识别 .xlsx / .xls，读取第一个 Sheet）
     *
     * @param inputFile Excel 文件
     */
    public static ParseResult parseQuestions(File inputFile) throws IOException {
        ParseResult result = new ParseResult();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(inputFile)) {
            Sheet sheet = workbook.getSheetAt(0);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();
            for (int i = firstRowNum; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String question = cellText(row, 0, formatter);
                // 首行首列为「问题」时视为表头行，跳过
                if (i == firstRowNum && HEADER_QUESTION.equals(question)) {
                    continue;
                }
                // 问题为空的行忽略（与手动录入行为一致）
                if (question.isEmpty()) {
                    continue;
                }
                String userId = cellText(row, 1, formatter);
                String classify = cellText(row, 2, formatter);
                String enableText = cellText(row, 3, formatter);
                String priorityText = cellText(row, 4, formatter);
                String trainingParam = cellText(row, 5, formatter);
                String answer = cellText(row, 6, formatter);

                AiQuestion item = new AiQuestion();
                item.setQuestion(question);
                item.setUserId(userId.isEmpty() ? null : userId);
                item.setQuestionClassify(classify.isEmpty() ? null : classify);
                item.setTrainingParam(trainingParam);
                item.setAnswer(answer);

                Integer enable = parseEnableTraining(enableText);
                if (enable == null) {
                    result.errors.add("第 " + (i + 1) + " 行：开启训练仅支持『" + ENABLE_TEXT_ON + " / " + ENABLE_TEXT_OFF + "』，当前值："
                            + (enableText.isEmpty() ? "（空）" : enableText));
                    continue;
                }
                item.setEnableTraining(enable);

                if (!priorityText.isEmpty()) {
                    try {
                        item.setPriority(Integer.parseInt(priorityText));
                    } catch (NumberFormatException ex) {
                        result.errors.add("第 " + (i + 1) + " 行：优先级别必须为整数，当前值：" + priorityText);
                        continue;
                    }
                } else {
                    item.setPriority(0);
                }
                result.questions.add(item);
            }
        }
        logger.info("[Excel] 解析文件 {} 完成：{} 条问题，{} 处错误", inputFile.getName(), result.questions.size(), result.errors.size());
        return result;
    }

    /**
     * 解析开启训练列：开启→1，不开启→2，留空默认开启，其它值非法返回 null
     */
    private static Integer parseEnableTraining(String text) {
        if (text == null || text.isEmpty()) {
            return AiQuestion.TRAINING_ENABLED;
        }
        if (ENABLE_TEXT_ON.equals(text)) {
            return AiQuestion.TRAINING_ENABLED;
        }
        if (ENABLE_TEXT_OFF.equals(text)) {
            return AiQuestion.TRAINING_DISABLED;
        }
        return null;
    }

    /**
     * 读取单元格显示文本（数字按显示值取，避免科学计数法），去除首尾空白（含全角空格）
     */
    private static String cellText(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }
        String text = formatter.formatCellValue(cell);
        return text == null ? "" : text.strip();
    }
}
