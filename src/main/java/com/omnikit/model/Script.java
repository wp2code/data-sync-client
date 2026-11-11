package com.omnikit.model;

import com.omnikit.util.GlobalUtil;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 脚本配置实体类
 */
@Data
public class Script {
    
    /**
     * 脚本类型：SQL 脚本，可绑定数据库类型并在目标库执行
     */
    public static final String TYPE_SQL = "sql";
    
    /**
     * 脚本类型：普通文件，任意文本文件（md / yml / java 等），仅支持编辑、保存与 GitLab 同步，不连接数据库运行
     */
    public static final String TYPE_FILE = "file";
    
    private Long id;
    
    private String scriptName;
    
    /**
     * 分组名称（可选）：脚本列表按分组聚合展示，留空则不分组（直接平铺展示）
     */
    private String groupName;
    
    /**
     * 脚本类型：{@link #TYPE_SQL} / {@link #TYPE_FILE}，旧数据为空时按 SQL 脚本处理
     */
    private String scriptType;
    
    private DbType dbType = DbType.MYSQL;
    
    /**
     * 脚本内容
     */
    private String content;
    
    /**
     * gitLab配置ID
     */
    private Long gitLabConfigId;
    
    /**
     * 项目ID或路径
     */
    private String projectOrId;
    
    /**
     * 绑定的分支
     */
    private String branch;
    
    /**
     * 文件地址
     */
    private String filePath;
    
    /**
     * 备注
     *
     */
    private String remark;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    public Script() {
    }
    
    public Script(String scriptName, String content) {
        this.scriptName = scriptName;
        this.content = content;
    }
    
    public Script(String scriptName, String dbType, String content) {
        this.scriptName = scriptName;
        this.dbType = DbType.fromString(dbType);
        this.content = content;
    }
    
    /**
     * 解析生效的分组名称：去除首尾空白，返回空串表示未分组
     */
    public String resolveGroupName() {
        return groupName == null ? "" : groupName.trim();
    }
    
    /**
     * 解析生效的脚本类型：未配置（旧数据）时按 SQL 脚本处理
     */
    public String resolveScriptType() {
        return scriptType == null || scriptType.isBlank() ? TYPE_SQL : scriptType;
    }
    
    /**
     * 是否 SQL 脚本：只有 SQL 脚本才需要选择数据源执行，普通文件仅编辑保存
     */
    public boolean isSqlScript() {
        return TYPE_SQL.equalsIgnoreCase(resolveScriptType());
    }
    
    /**
     * 上传 / 导出使用的文件名：SQL 脚本固定为「脚本名称.sql」；普通文件优先取 filePath 末段（已带扩展名时），
     * 否则取脚本名称
     */
    public String resolveFileName() {
        if (isSqlScript()) {
            return scriptName + ".sql";
        }
        String path = filePath == null ? "" : filePath.trim().replace('\\', '/');
        return GlobalUtil.isFullFilePath(path) ? path.substring(path.lastIndexOf('/') + 1) : scriptName;
    }
    
}
