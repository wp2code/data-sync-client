package com.datasync.core;

import java.time.LocalDateTime;

/**
 * 数据源配置实体类
 * 统一封装源库/目标库的连接参数，提供 MySQL / PostgreSQL 的 JDBC URL 自动生成能力
 */
public class DataSource {

    // ────────── 基础字段 ──────────
    private Long id;                      // SQLite 主键（持久化场景）
    private String sourceName;            // 自定义数据源名称（唯一，用于持久化识别）
    private String dbType;                // 数据库类型：mysql / postgresql
    private String host;                  // 主机地址
    private String port;                  // 端口
    private String dbName;                // 数据库名称
    private String schema = "public";     // PostgreSQL schema（默认 public），MySQL 忽略
    private String username;              // 登录账号
    private String password;              // 登录密码
    private LocalDateTime createTime;     // 创建时间
    private LocalDateTime updateTime;     // 更新时间

    // ────────── 构造方法 ──────────

    public DataSource() {}

    /**
     * 快捷构造（用于临时录入，无 SQLite 持久化字段）
     */
    public DataSource(String dbType, String host, String port, String dbName, String username, String password) {
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.password = password;
    }

    /**
     * 完整构造（含持久化字段）
     */
    public DataSource(Long id, String sourceName, String dbType, String host, String port,
                      String dbName, String username, String password,
                      LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.sourceName = sourceName;
        this.dbType = dbType;
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
     * 根据数据库类型自动生成对应格式的 JDBC 连接 URL
     */
    public String buildJdbcUrl() {
        if ("postgresql".equalsIgnoreCase(dbType)) {
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
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return "org.postgresql.Driver";
        }
        return "com.mysql.cj.jdbc.Driver";
    }

    /**
     * 获取默认端口
     */
    public static String getDefaultPort(String dbType) {
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return "5432";
        }
        return "3306"; // MySQL 默认
    }

    /**
     * 参数校验 —— 所有必填字段不得为空
     */
    public boolean isValid() {
        return dbType != null && !dbType.isBlank()
            && host != null && !host.isBlank()
            && port != null && !port.isBlank()
            && dbName != null && !dbName.isBlank()
            && username != null && !username.isBlank();
    }

    // ────────── Getter / Setter ──────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = (schema != null && !schema.isBlank()) ? schema : "public"; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "DataSource{" +
            "sourceName='" + sourceName + '\'' +
            ", dbType='" + dbType + '\'' +
            ", url='" + buildJdbcUrl() + '\'' +
            '}';
    }
}
