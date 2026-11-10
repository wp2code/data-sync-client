package com.omnikit.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 脚本配置实体类
 */
@Data
public class Script {
    
    private Long id;
    
    private String scriptName;
    
    /**
     * 分组名称（可选）：脚本列表按分组聚合展示，留空则不分组（直接平铺展示）
     */
    private String groupName;
    
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
    
}
