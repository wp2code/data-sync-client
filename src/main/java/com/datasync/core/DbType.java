package com.datasync.core;

/**
 * 数据库类型枚举，替代魔法字符串 "mysql"/"postgresql"
 */
public enum DbType {
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", 3306),
    POSTGRESQL("postgresql", "org.postgresql.Driver", 5432);

    private final String key;
    private final String driverClass;
    private final int defaultPort;

    DbType(String key, String driverClass, int defaultPort) {
        this.key = key;
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
    }

    /**
     * 从字符串解析数据库类型，不区分大小写。默认返回 MYSQL。
     */
    public static DbType fromString(String s) {
        if (s == null) return MYSQL;
        return "postgresql".equalsIgnoreCase(s) ? POSTGRESQL : MYSQL;
    }

    public String getKey() { return key; }
    public String getDriverClass() { return driverClass; }
    public int getDefaultPort() { return defaultPort; }

    /** 首字母大写的显示名称，如 "Mysql", "Postgresql" */
    public String getDisplayName() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
