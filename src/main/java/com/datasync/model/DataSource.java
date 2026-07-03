package com.datasync.model;

import java.time.LocalDateTime;

/**
 * 数据源配置实体类 统一封装源库/目标库的连接参数，提供 MySQL / PostgreSQL 的 JDBC URL 自动生成能力
 */
public class DataSource {
    
    // ────────── 基础字段 ──────────
    private Long id;                      // SQLite 主键（持久化场景）
    
    private String sourceName;            // 自定义数据源名称（唯一，用于持久化识别）
    
    private DbType dbType = DbType.MYSQL; // 数据库类型枚举
    
    private String host;                  // 主机地址
    
    private String port;                  // 端口
    
    private String dbName;                // 数据库名称
    
    private String schema = "public";     // PostgreSQL schema（默认 public），MySQL 忽略
    
    private String username;              // 登录账号
    
    private String password;              // 登录密码
    
    private LocalDateTime createTime;     // 创建时间
    
    private LocalDateTime updateTime;     // 更新时间
    
    // ────────── 构造方法 ──────────
    
    public DataSource() {
    }
    
    /**
     * 快捷构造（用于临时录入，无 SQLite 持久化字段）
     */
    public DataSource(DbType dbType, String host, String port, String dbName, String username, String password) {
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.password = password;
    }
    
    /**
     * 快捷构造（兼容字符串类型，内部转为枚举）
     */
    public DataSource(String dbType, String host, String port, String dbName, String username, String password) {
        this(DbType.fromString(dbType), host, port, dbName, username, password);
    }
    
    /**
     * 完整构造（含持久化字段，兼容字符串类型）
     */
    public DataSource(Long id, String sourceName, String dbType, String host, String port, String dbName, String username, String password,
            LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.sourceName = sourceName;
        this.dbType = DbType.fromString(dbType);
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.password = password;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
    
    // ────────── 核心方法 ──────────
    
    /**
     * 判断是否为 PostgreSQL
     */
    public boolean isPostgresql() {
        return dbType == DbType.POSTGRESQL;
    }
    
    /**
     * 根据数据库类型自动生成对应格式的 JDBC 连接 URL
     */
    public String buildJdbcUrl() {
        if (isPostgresql()) {
            String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
            if (schema != null && !schema.isBlank()) {
                url += "?currentSchema=" + schema;
            }
            return url;
        }
        // 默认 MySQL
        return String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai", host, port, dbName);
    }
    
    /**
     * 获取 JDBC 驱动类名
     */
    public String getDriverClassName() {
        return dbType.getDriverClass();
    }
    
    /**
     * 获取默认端口（静态方法，兼容字符串类型）
     */
    public static String getDefaultPort(String dbType) {
        return String.valueOf(DbType.fromString(dbType).getDefaultPort());
    }
    
    /**
     * 参数校验 —— 所有必填字段不得为空
     */
    public boolean isValid() {
        return dbType != null && host != null && !host.isBlank() && port != null && !port.isBlank() && dbName != null && !dbName.isBlank()
                && username != null && !username.isBlank();
    }
    
    // ────────── Getter / Setter ──────────
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
    
    /**
     * @return 数据库类型字符串（兼容旧代码），使用枚举的 key
     */
    public String getDbType() {
        return dbType != null ? dbType.getKey() : "mysql";
    }
    
    /**
     * 设置数据库类型（推荐使用枚举）
     */
    public void setDbTypeEnum(DbType dbType) {
        this.dbType = dbType;
    }
    
    /**
     * 获取数据库类型枚举
     */
    public DbType getDbTypeEnum() {
        return dbType;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public String getPort() {
        return port;
    }
    
    public void setPort(String port) {
        this.port = port;
    }
    
    public String getDbName() {
        return dbName;
    }
    
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }
    
    public String getSchema() {
        return schema;
    }
    
    public void setSchema(String schema) {
        this.schema = (schema != null && !schema.isBlank()) ? schema : "public";
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
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
    
    @Override
    public String toString() {
        return "DataSource{" + "sourceName='" + sourceName + '\'' + ", dbType='" + dbType + '\'' + ", url='" + buildJdbcUrl() + '\'' + '}';
    }
}
