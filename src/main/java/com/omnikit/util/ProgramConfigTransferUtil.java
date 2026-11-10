package com.omnikit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnikit.model.ProgramConfig;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 程序配置导入导出工具：把程序容器配置读写为 JSON 文件，便于在多台机器、多个环境之间迁移。
 *
 * <p>文件结构为可读的扁平 JSON，可选字段留空即可省略，支持手工编辑：</p>
 * <pre>
 * {
 *   "format": "omnikit-program-configs",
 *   "version": 1,
 *   "exportTime": "2026-11-10 10:30:00",
 *   "programs": [
 *     {
 *       "name": "订单服务",
 *       "groupName": "后端",
 *       "programType": "JAVA",
 *       "programPath": "java -jar D:\\app\\order.jar",
 *       "arguments": "--server.port=8080",
 *       "jvmArgs": "-Xms512m -Xmx1024m",
 *       "envVars": "SERVER_PORT=8080;LOGGING_LEVEL_ROOT=info",
 *       "workDir": "D:\\app",
 *       "jrePath": "D:\\jdk17",
 *       "remark": "备注"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>导出不包含 id、最近进程号与时间戳：这些是本地运行时状态，导入时由数据库重新生成。
 * 导入按程序名称判重，同名程序由调用方决定覆盖或跳过。</p>
 */
public final class ProgramConfigTransferUtil {
    
    /**
     * 导出文件扩展名（文件选择器过滤与后缀补全共用）
     */
    public static final String FILE_EXTENSION = "json";
    
    private static final String FORMAT_NAME = "omnikit-program-configs";
    
    private static final int FORMAT_VERSION = 1;
    
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 名称不可用时的兜底导出文件名前缀：program-configs-yyyyMMdd.json
     */
    private static final String FILE_NAME_PREFIX = "program-configs-";
    
    private static final DateTimeFormatter FILE_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    /**
     * JSON 读写器（ObjectMapper 线程安全，静态复用）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final String FIELD_FORMAT = "format";
    
    private static final String FIELD_VERSION = "version";
    
    private static final String FIELD_EXPORT_TIME = "exportTime";
    
    private static final String FIELD_PROGRAMS = "programs";
    
    private static final String FIELD_NAME = "name";
    
    private static final String FIELD_GROUP_NAME = "groupName";
    
    private static final String FIELD_PROGRAM_TYPE = "programType";
    
    private static final String FIELD_PROGRAM_PATH = "programPath";
    
    private static final String FIELD_ARGUMENTS = "arguments";
    
    private static final String FIELD_JVM_ARGS = "jvmArgs";
    
    private static final String FIELD_ENV_VARS = "envVars";
    
    private static final String FIELD_WORK_DIR = "workDir";
    
    private static final String FIELD_JRE_PATH = "jrePath";
    
    private static final String FIELD_PYTHON_PATH = "pythonPath";
    
    private static final String FIELD_REMARK = "remark";
    
    private ProgramConfigTransferUtil() {
    }
    
    /**
     * 导出程序配置到 JSON 文件（UTF-8，覆盖写）；id 与运行时状态不写入
     *
     * @param file    目标文件
     * @param configs 待导出的程序配置
     */
    public static void exportToFile(File file, List<ProgramConfig> configs) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put(FIELD_FORMAT, FORMAT_NAME);
        root.put(FIELD_VERSION, FORMAT_VERSION);
        root.put(FIELD_EXPORT_TIME, LocalDateTime.now().format(EXPORT_TIME_FORMATTER));
        ArrayNode programs = root.putArray(FIELD_PROGRAMS);
        for (ProgramConfig config : configs) {
            ObjectNode node = programs.addObject();
            node.put(FIELD_NAME, trimToEmpty(config.getName()));
            putIfNotBlank(node, FIELD_GROUP_NAME, config.resolveGroupName());
            putIfNotBlank(node, FIELD_PROGRAM_TYPE, config.resolveProgramType());
            node.put(FIELD_PROGRAM_PATH, trimToEmpty(config.getProgramPath()));
            putIfNotBlank(node, FIELD_ARGUMENTS, config.getArguments());
            putIfNotBlank(node, FIELD_JVM_ARGS, config.getJvmArgs());
            putIfNotBlank(node, FIELD_ENV_VARS, config.getEnvVars());
            putIfNotBlank(node, FIELD_WORK_DIR, config.getWorkDir());
            putIfNotBlank(node, FIELD_JRE_PATH, config.getJrePath());
            putIfNotBlank(node, FIELD_PYTHON_PATH, config.getPythonPath());
            putIfNotBlank(node, FIELD_REMARK, config.getRemark());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, root);
    }
    
    /**
     * 解析导入文件：返回可入库的程序配置与逐项错误说明。 名称为空、程序/命令为空、文件内重名的条目会被跳过并记录原因，其余条目正常返回
     *
     * @param file 待解析的 JSON 文件
     */
    public static ParseResult parse(File file) throws IOException {
        JsonNode root = MAPPER.readTree(file);
        ParseResult result = new ParseResult();
        JsonNode programs = programsNode(root);
        if (programs == null) {
            result.addError("文件格式不正确：未找到程序配置数组（programs）");
            return result;
        }
        Set<String> names = new LinkedHashSet<>();
        int index = 0;
        for (JsonNode node : programs) {
            index++;
            if (!node.isObject()) {
                result.addError("第 " + index + " 项不是有效的程序配置对象，已忽略");
                continue;
            }
            String name = text(node, FIELD_NAME);
            if (name.isEmpty()) {
                result.addError("第 " + index + " 项缺少程序名称，已忽略");
                continue;
            }
            if (!names.add(name)) {
                result.addError("「" + name + "」在文件中重复出现，仅导入第一条");
                continue;
            }
            String programPath = text(node, FIELD_PROGRAM_PATH);
            if (programPath.isEmpty()) {
                result.addError("「" + name + "」缺少程序/命令，已忽略");
                continue;
            }
            result.getConfigs().add(toProgramConfig(node, name, programPath));
        }
        return result;
    }
    
    /**
     * 补全导出文件后缀：无 .json 后缀时追加，已有时原样返回
     */
    public static File withJsonExtension(File file) {
        String suffix = "." + FILE_EXTENSION;
        return file.getName().toLowerCase(Locale.ROOT).endsWith(suffix) ? file : new File(file.getParentFile(), file.getName() + suffix);
    }
    
    /**
     * 导出文件名：取程序名称（去除文件名非法字符）加 .json，名称为空时退回 program-configs-yyyyMMdd.json
     */
    public static String defaultFileName(String programName) {
        String safeName = sanitizeFileName(programName);
        if (!safeName.isEmpty()) {
            return safeName + "." + FILE_EXTENSION;
        }
        return FILE_NAME_PREFIX + LocalDateTime.now().format(FILE_NAME_TIME_FORMATTER) + "." + FILE_EXTENSION;
    }
    
    /**
     * 把程序名称转为合法文件名：路径分隔符等 Windows 非法字符与空白替换为 '-'，首尾 '-' 去除
     */
    private static String sanitizeFileName(String name) {
        String text = trimToEmpty(name);
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder safe = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            safe.append("\\/:*?\"<>|".indexOf(c) >= 0 || Character.isWhitespace(c) ? '-' : c);
        }
        int start = 0;
        int end = safe.length();
        while (start < end && safe.charAt(start) == '-') {
            start++;
        }
        while (end > start && safe.charAt(end - 1) == '-') {
            end--;
        }
        return safe.substring(start, end);
    }
    
    /**
     * 取程序配置数组：标准格式为 {programs: [...]}，同时兼容仅包含数组的极简文件
     */
    private static JsonNode programsNode(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        JsonNode programs = root.path(FIELD_PROGRAMS);
        return programs.isArray() ? programs : null;
    }
    
    private static ProgramConfig toProgramConfig(JsonNode node, String name, String programPath) {
        ProgramConfig config = new ProgramConfig(name, programPath, text(node, FIELD_ARGUMENTS));
        config.setJvmArgs(text(node, FIELD_JVM_ARGS));
        config.setEnvVars(text(node, FIELD_ENV_VARS));
        config.setGroupName(text(node, FIELD_GROUP_NAME));
        // 未知或留空的类型保持 null，由 ProgramConfig#resolveProgramType 按程序字段自动推断
        config.setProgramType(normalizeProgramType(text(node, FIELD_PROGRAM_TYPE)));
        config.setWorkDir(text(node, FIELD_WORK_DIR));
        config.setJrePath(text(node, FIELD_JRE_PATH));
        config.setPythonPath(text(node, FIELD_PYTHON_PATH));
        config.setRemark(text(node, FIELD_REMARK));
        return config;
    }
    
    /**
     * 规范化程序类型：兼容大小写与常见别名，无法识别时返回 null（按程序字段自动推断）
     */
    private static String normalizeProgramType(String raw) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "JAVA", "JAVAW" -> ProgramConfig.TYPE_JAVA;
            case "PYTHON", "PYTHON3" -> ProgramConfig.TYPE_PYTHON;
            case "HTTP", "HTTP_SERVER", "HTTP-SERVER" -> ProgramConfig.TYPE_HTTP_SERVER;
            case "OTHER", "COMMAND" -> ProgramConfig.TYPE_OTHER;
            default -> null;
        };
    }
    
    /**
     * 读取字符串字段：缺失或 null 返回空串，去除首尾空白
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }
    
    private static void putIfNotBlank(ObjectNode node, String field, String value) {
        String text = trimToEmpty(value);
        if (!text.isEmpty()) {
            node.put(field, text);
        }
    }
    
    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
    
    /**
     * 导入解析结果：成功解析出的程序配置与逐项错误说明
     */
    public static final class ParseResult {
        
        private final List<ProgramConfig> configs = new ArrayList<>();
        
        private final List<String> errors = new ArrayList<>();
        
        public List<ProgramConfig> getConfigs() {
            return configs;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        private void addError(String error) {
            errors.add(error);
        }
    }
}
