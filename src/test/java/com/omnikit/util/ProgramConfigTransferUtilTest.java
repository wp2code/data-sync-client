package com.omnikit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omnikit.model.ProgramConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProgramConfigTransferUtil 单元测试：导出/解析往返、字段容错、文件名补全。
 */
@DisplayName("ProgramConfigTransferUtil 程序配置导入导出")
class ProgramConfigTransferUtilTest {
    
    @TempDir
    Path tempDir;
    
    private ProgramConfig sampleConfig() {
        ProgramConfig config = new ProgramConfig("订单服务", "java -jar D:\\app\\order.jar", "--server.port=8080");
        config.setId(100L);
        config.setLastPid(12345L);
        config.setProgramType(ProgramConfig.TYPE_JAVA);
        config.setGroupName("后端");
        config.setJvmArgs("-Xms512m -Dserver.port=8080");
        config.setEnvVars("SERVER_PORT=8080;LOGGING_LEVEL_ROOT=info");
        config.setWorkDir("D:\\app");
        config.setJrePath("D:\\jdk17");
        config.setRemark("备注");
        return config;
    }
    
    @Test
    @DisplayName("exportToFile / parse：导出后可完整还原字段，且不携带本地运行时状态")
    void testExportThenParse() throws IOException {
        File file = tempDir.resolve("programs.json").toFile();
        ProgramConfigTransferUtil.exportToFile(file, List.of(sampleConfig()));
        
        String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"programs\""), "应包含程序数组");
        assertFalse(json.contains("lastPid"), "导出不应包含本地进程号");
        assertFalse(json.contains("\"id\""), "导出不应包含数据库主键");
        
        ProgramConfigTransferUtil.ParseResult result = ProgramConfigTransferUtil.parse(file);
        assertTrue(result.getErrors().isEmpty(), "导出文件应可无错误解析");
        assertEquals(1, result.getConfigs().size());
        ProgramConfig parsed = result.getConfigs().get(0);
        assertEquals("订单服务", parsed.getName(), "中文名称应正常往返（UTF-8）");
        assertEquals("java -jar D:\\app\\order.jar", parsed.getProgramPath());
        assertEquals("--server.port=8080", parsed.getArguments());
        assertEquals(ProgramConfig.TYPE_JAVA, parsed.getProgramType());
        assertEquals("后端", parsed.getGroupName());
        assertEquals("-Xms512m -Dserver.port=8080", parsed.getJvmArgs(), "JVM 参数应往返保留");
        assertEquals("SERVER_PORT=8080;LOGGING_LEVEL_ROOT=info", parsed.getEnvVars(), "环境变量应往返保留");
        assertEquals("D:\\app", parsed.getWorkDir());
        assertEquals("D:\\jdk17", parsed.getJrePath());
        assertEquals("备注", parsed.getRemark());
        assertNull(parsed.getId(), "导入的配置不应带主键");
        assertNull(parsed.getLastPid(), "导入的配置不应带进程号");
    }
    
    @Test
    @DisplayName("parse：兼容仅包含程序数组的极简文件，程序类型别名归一化")
    void testParseSimpleArray() throws IOException {
        Path path = tempDir.resolve("simple.json");
        Files.writeString(path, """
                [
                  {"name": "采集脚本", "programPath": "D:/proj/main.py", "programType": "python3"},
                  {"name": "未知类型", "programPath": "node app.js", "programType": "whatever"}
                ]
                """, StandardCharsets.UTF_8);
        
        ProgramConfigTransferUtil.ParseResult result = ProgramConfigTransferUtil.parse(path.toFile());
        assertTrue(result.getErrors().isEmpty(), "极简数组格式应可解析");
        assertEquals(2, result.getConfigs().size());
        assertEquals(ProgramConfig.TYPE_PYTHON, result.getConfigs().get(0).getProgramType(), "python3 应归一化为 Python 类型");
        ProgramConfig unknown = result.getConfigs().get(1);
        assertNull(unknown.getProgramType(), "无法识别的类型保留为空，由配置自行推断");
        assertEquals(ProgramConfig.TYPE_OTHER, unknown.resolveProgramType(), "空类型按程序字段推断为其他程序");
    }
    
    @Test
    @DisplayName("parse：缺名称、缺程序/命令、文件内重名、非对象条目逐项跳过并给出原因")
    void testParseInvalidEntries() throws IOException {
        Path path = tempDir.resolve("invalid.json");
        Files.writeString(path, """
                {"programs": [
                  {"programPath": "java -jar a.jar"},
                  {"name": "缺路径"},
                  {"name": "重复", "programPath": "cmd"},
                  {"name": "重复", "programPath": "cmd2"},
                  "not-an-object"
                ]}
                """, StandardCharsets.UTF_8);
        
        ProgramConfigTransferUtil.ParseResult result = ProgramConfigTransferUtil.parse(path.toFile());
        assertEquals(1, result.getConfigs().size(), "仅名称与程序/命令齐全且不重复的条目可导入");
        assertEquals("重复", result.getConfigs().get(0).getName());
        assertEquals(4, result.getErrors().size(), "四类非法条目均应记录原因");
    }
    
    @Test
    @DisplayName("parse：缺少 programs 数组时返回格式错误且不静默通过")
    void testParseMissingPrograms() throws IOException {
        Path path = tempDir.resolve("bad.json");
        Files.writeString(path, "{\"foo\": 1}", StandardCharsets.UTF_8);
        
        ProgramConfigTransferUtil.ParseResult result = ProgramConfigTransferUtil.parse(path.toFile());
        assertTrue(result.getConfigs().isEmpty());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("programs"), "应提示缺少程序数组");
    }
    
    @Test
    @DisplayName("withJsonExtension / defaultFileName：后缀补全与导出文件名规则")
    void testFileNameHelpers() {
        assertEquals("out.json", ProgramConfigTransferUtil.withJsonExtension(new File("out")).getName(), "无后缀时自动补全");
        assertEquals("out.JSON", ProgramConfigTransferUtil.withJsonExtension(new File("out.JSON")).getName(), "已有后缀不重复追加");
        assertEquals("订单服务.json", ProgramConfigTransferUtil.defaultFileName("订单服务"), "默认文件名取程序名称");
        assertEquals("a-b-c.json", ProgramConfigTransferUtil.defaultFileName(" a/b:c "), "非法字符与空白替换为 -");
        assertTrue(ProgramConfigTransferUtil.defaultFileName("").matches("program-configs-\\d{8}\\.json"), "名称为空时退回日期文件名");
        assertTrue(ProgramConfigTransferUtil.defaultFileName(null).matches("program-configs-\\d{8}\\.json"), "名称为 null 时退回日期文件名");
    }
}
