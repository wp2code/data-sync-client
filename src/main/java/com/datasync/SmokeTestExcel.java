package com.datasync;

import com.datasync.model.AiQuestion;
import com.datasync.util.ExcelQuestionUtil;
import java.io.File;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 临时冒烟测试（验证后删除）：模板生成 -> 解析回环 / 非法数据校验
 */
public class SmokeTestExcel {

    public static void main(String[] args) throws Exception {
        File template = File.createTempFile("ai-template", ".xlsx");
        ExcelQuestionUtil.generateTemplate(template);
        System.out.println("== 1. 模板生成成功: " + template.length() + " bytes");

        ExcelQuestionUtil.ParseResult result = ExcelQuestionUtil.parseQuestions(template);
        System.out.println("== 2. 模板回环解析: questions=" + result.getQuestions().size() + ", errors=" + result.getErrors().size());
        for (AiQuestion q : result.getQuestions()) {
            System.out.println("   " + q.getQuestion() + " | " + q.getQuestionClassify() + " | " + q.getEnableTraining() + " | " + q.getPriority() + " | " + q.getTrainingParam() + " | " + q.getAnswer());
        }
        if (result.getQuestions().size() != 3 || !result.getErrors().isEmpty()) {
            throw new AssertionError("模板回环解析结果不符合预期");
        }
        if (!"在数据源管理页面点击『新增』，填写连接信息后保存。".equals(result.getQuestions().get(0).getAnswer())) {
            throw new AssertionError("固定答案列回环解析失败");
        }

        // 非法数据：开启训练非法、优先级别非法、空行、表头
        File bad = File.createTempFile("ai-bad", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("问题列表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("问题");
            header.createCell(1).setCellValue("分类");
            header.createCell(2).setCellValue("开启训练");
            header.createCell(3).setCellValue("优先级别");
            header.createCell(4).setCellValue("训练参数");
            Row ok = sheet.createRow(1);
            ok.createCell(0).setCellValue("正常问题？");
            ok.createCell(2).setCellValue("开启");
            ok.createCell(3).setCellValue(3);
            Row badEnable = sheet.createRow(2);
            badEnable.createCell(0).setCellValue("开启训练非法");
            badEnable.createCell(2).setCellValue("也许");
            Row badPriority = sheet.createRow(3);
            badPriority.createCell(0).setCellValue("优先级别非法");
            badPriority.createCell(2).setCellValue("不开启");
            badPriority.createCell(3).setCellValue("abc");
            Row empty = sheet.createRow(5);
            empty.createCell(1).setCellValue("只有分类没有问题");
            try (var out = new java.io.FileOutputStream(bad)) {
                wb.write(out);
            }
        }
        ExcelQuestionUtil.ParseResult badResult = ExcelQuestionUtil.parseQuestions(bad);
        System.out.println("== 3. 非法数据解析: questions=" + badResult.getQuestions().size() + ", errors=" + badResult.getErrors().size());
        badResult.getErrors().forEach(e -> System.out.println("   " + e));
        if (badResult.getQuestions().size() != 1 || badResult.getErrors().size() != 2) {
            throw new AssertionError("非法数据校验结果不符合预期");
        }
        AiQuestion okQuestion = badResult.getQuestions().get(0);
        if (!"正常问题？".equals(okQuestion.getQuestion()) || okQuestion.getEnableTraining() != AiQuestion.TRAINING_ENABLED || okQuestion.getPriority() != 3) {
            throw new AssertionError("正常行解析值不符合预期");
        }
        if (!okQuestion.getAnswer().isEmpty()) {
            throw new AssertionError("旧模板（无固定答案列）解析结果应为空");
        }
        System.out.println("== ALL PASS");
        template.delete();
        bad.delete();
    }
}
