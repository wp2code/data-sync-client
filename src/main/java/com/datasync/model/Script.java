package com.datasync.model;

import java.time.LocalDateTime;

/**
 * 脚本配置实体类
 */
public class Script {
    
    private Long id;
    
    private String scriptName;
    
    private DbType dbType = DbType.MYSQL;
    
    private String content;
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
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getScriptName() {
        return scriptName;
    }
    
    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }
    
    /**
     * 获取数据库类型字符串（mysql / postgresql）
     */
    public String getDbType() {
        return dbType != null ? dbType.getKey() : "mysql";
    }
    
    public void setDbType(String dbType) {
        this.dbType = DbType.fromString(dbType);
    }
    
    public DbType getDbTypeEnum() {
        return dbType;
    }
    
    public void setDbTypeEnum(DbType dbType) {
        this.dbType = dbType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }
}
