package com.datasync.model;

import java.time.LocalDateTime;
import java.util.Locale;
import lombok.Data;

/**
 * 程序容器配置实体类
 * <p>
 * 描述一个可在程序容器中启动的外部程序：既可以是可执行文件/脚本的绝对路径，
 * 也可以是 PATH 中的命令名（如 java、node、python）。
 */
@Data
public class ProgramConfig {

    /**
     * 程序类型：Java 程序（可指定 JRE/JDK 运行环境）
     */
    public static final String TYPE_JAVA = "JAVA";

    /**
     * 程序类型：Python 程序（可指定解释器/虚拟环境，入口脚本自动前置解释器）
     */
    public static final String TYPE_PYTHON = "PYTHON";

    /**
     * 程序类型：其他通用命令（bat 脚本、可执行文件等）
     */
    public static final String TYPE_OTHER = "OTHER";

    private Long id;

    /**
     * 程序类型：{@link #TYPE_JAVA} / {@link #TYPE_PYTHON} / {@link #TYPE_OTHER}。
     * 决定表单展示的运行环境字段与命令组装行为；旧数据为 null 时按程序字段自动推断
     */
    private String programType;
    
    /**
     * 程序名称（列表展示，唯一）
     */
    private String name;
    
    /**
     * 程序/命令：两种写法——单个可执行文件/脚本的路径（可含空格，或 PATH 中命令名如 java），
     * 或完整命令行（如 java -jar app.jar，首词为程序其余为参数）
     */
    private String programPath;
    
    /**
     * 附加参数（如 -jar D:\app.jar --port=8080），按空白分词
     */
    private String arguments;
    
    /**
     * 工作目录（可选，空则继承应用目录）
     */
    private String workDir;
    
    /**
     * JRE/JDK 路径（可选）：JDK/JRE 根目录（自动定位 bin/java）或直接指向 java 可执行文件；
     * 留空时使用系统 PATH 中的 java。仅对程序为 java/javaw 命令的配置生效
     */
    private String jrePath;
    
    /**
     * Python 解释器路径（可选）：Python 安装根目录（自动定位 python 可执行文件）、
     * 虚拟环境目录（自动定位 Scripts/bin 下的 python）或直接指向 python 可执行文件；
     * 留空时使用系统 PATH 中的 python。仅对 python/python3 命令或 .py 入口脚本生效
     */
    private String pythonPath;

    /**
     * 最近一次启动的进程号（可选）：启动成功后由服务层写入，应用重启后用于检测程序是否仍在后台运行
     */
    private Long lastPid;
    
    /**
     * 备注
     */
    private String remark;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    public ProgramConfig() {
    }
    
    public ProgramConfig(String name, String programPath, String arguments) {
        this.name = name;
        this.programPath = programPath;
        this.arguments = arguments;
    }

    /**
     * 解析生效的程序类型：显式配置优先；未配置（旧数据）时按程序字段与运行环境自动推断，
     * 保证旧行为不变。程序为 java/javaw 命令或配置了 JRE 路径视为 Java；
     * python/python3 命令或配置了解释器视为 Python；其余为通用命令
     */
    public String resolveProgramType() {
        if (programType != null && !programType.isBlank()) {
            return programType;
        }
        String path = programPath == null ? "" : programPath.trim().toLowerCase(Locale.ROOT).replace("\"", "");
        if (path.matches("^(java|javaw)(\\.exe)?(\\s.*|$)") || (jrePath != null && !jrePath.isBlank())) {
            return TYPE_JAVA;
        }
        if (path.matches("^(python3?)(\\.exe)?(\\s.*|$)") || (pythonPath != null && !pythonPath.isBlank())) {
            return TYPE_PYTHON;
        }
        return TYPE_OTHER;
    }

}
