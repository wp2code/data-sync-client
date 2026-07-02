package com.datasync.core;

import com.datasync.components.combobox.IconItem;
import com.datasync.ui.UiConstants;
import com.datasync.util.IconUtil;

/**
 * 数据库类型枚举，替代魔法字符串 "mysql"/"postgresql"
 */
public enum DbType {
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", 3306, "MySQL"),
    POSTGRESQL("postgresql", "org.postgresql.Driver", 5432, "PostgreSQL");
    
    private final String key;
    
    private final String driverClass;
    
    private final int defaultPort;
    
    private final String displayName;
    
    DbType(String key, String driverClass, int defaultPort, String displayName) {
        this.key = key;
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
        this.displayName = displayName;
    }
    
    public static IconItem POSTGRESQL_ITEM = new IconItem(IconUtil.getDbTypeIcon(DbType.POSTGRESQL), DbType.POSTGRESQL.getDisplayName());
    
    public static IconItem MYSQL_ITEM = new IconItem(IconUtil.getDbTypeIcon(DbType.MYSQL), DbType.MYSQL.getDisplayName());
    
    public static final IconItem FIRST_ITEM = new IconItem(null, UiConstants.PLACEHOLDER_SELECT_SOURCE);
    
    /**
     * 从字符串解析数据库类型，不区分大小写。默认返回 MYSQL。
     */
    public static DbType fromString(String s) {
        if (s == null) {
            return MYSQL;
        }
        return "postgresql".equalsIgnoreCase(s) ? POSTGRESQL : MYSQL;
    }
    
    public String getKey() {
        return key;
    }
    
    public String getDriverClass() {
        return driverClass;
    }
    
    public int getDefaultPort() {
        return defaultPort;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
