package com.omnikit.model;

import com.omnikit.components.combobox.IconItem;
import com.omnikit.util.IconUtil;
import lombok.Getter;

/**
 * 数据库类型枚举，替代魔法字符串 "mysql"/"postgresql"
 */
@Getter
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
    
    public static final IconItem POSTGRESQL_ITEM = new IconItem(IconUtil.getDbTypeIcon(DbType.POSTGRESQL), DbType.POSTGRESQL.getDisplayName());
    
    public static final IconItem MYSQL_ITEM = new IconItem(IconUtil.getDbTypeIcon(DbType.MYSQL), DbType.MYSQL.getDisplayName());
    
    /**
     * 从字符串解析数据库类型，不区分大小写。默认返回 MYSQL。
     */
    public static DbType fromString(String s) {
        if (s == null) {
            return MYSQL;
        }
        return "postgresql".equalsIgnoreCase(s) ? POSTGRESQL : MYSQL;
    }
    
    public static IconItem getIconItem(String s) {
        final DbType dbType = fromString(s);
        return dbType.equals(POSTGRESQL) ? POSTGRESQL_ITEM : MYSQL_ITEM;
    }
}
