package com.datasync.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 程序容器配置实体类
 * <p>
 * 描述一个可在程序容器中启动的外部程序：既可以是可执行文件/脚本的绝对路径，
 * 也可以是 PATH 中的命令名（如 java、node、python）。
 */
@Data
public class ProgramConfig {
    
    private Long id;
    
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
     * 最近一次启动的进程号（可选）：启动成功后由服务层写入，应用重启后用于检测程序是否仍在后台运行
     */
    private Long lastPid;
    
    /**
     * 输出编码（可选）：控制台日志的解码编码，如 UTF-8、GBK；留空时自动探测
     * （开头字节能通过 UTF-8 严格校验则按 UTF-8，否则按系统编码）
     */
    private String outputCharset;
    
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
    
}
