package com.datasync.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库连接工具类
 * 统一管控 MySQL / PostgreSQL 的 JDBC 连接创建、测试、资源释放
 */
public class DbConnector {

    private DbConnector() {
        // 工具类，禁止实例化
    }

    /**
     * 根据数据源配置获取数据库连接
     *
     * @param ds 数据源配置实体
     * @return 数据库连接
     * @throws SQLException          连接失败时抛出
     * @throws ClassNotFoundException 驱动类未找到
     */
    public static Connection getConnection(DataSource ds) throws SQLException, ClassNotFoundException {
        Class.forName(ds.getDriverClassName());
        return DriverManager.getConnection(ds.buildJdbcUrl(), ds.getUsername(), ds.getPassword());
    }

    /**
     * 测试数据源连接是否有效
     *
     * @param ds 数据源配置实体
     * @return 测试结果信息
     */
    public static String testConnection(DataSource ds) {
        if (!ds.isValid()) {
            return "[FAILED] 数据源参数不完整，请检查所有必填项";
        }
        Connection conn = null;
        try {
            conn = getConnection(ds);
            return "[SUCCESS] 连接成功 → " + ds.getDbType().toUpperCase()
                + " " + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName();
        } catch (ClassNotFoundException e) {
            return "[FAILED] 数据库驱动未找到 → " + e.getMessage();
        } catch (SQLException e) {
            return "[FAILED] 数据库连接失败 → " + e.getMessage();
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * 查询 PostgreSQL 数据库中的所有 Schema（非系统 schema）
     *
     * @param ds 数据源配置（需为 PostgreSQL 且已填充连接信息）
     * @return Schema 名称列表，失败时至少返回 {"public"}
     */
    public static List<String> fetchSchemas(DataSource ds) {
        List<String> schemas = new ArrayList<>();
        schemas.add("public"); // 始终包含默认值
        if (!"postgresql".equalsIgnoreCase(ds.getDbType()) || !ds.isValid()) {
            return schemas;
        }
        try (Connection conn = getConnection(ds);
             ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (schema != null && !schema.startsWith("pg_") && !"information_schema".equals(schema)
                    && !schemas.contains(schema)) {
                    schemas.add(schema);
                }
            }
        } catch (Exception e) {
            // 不抛异常，至少返回 public
        }
        return schemas;
    }

    /**
     * 查询指定 Schema / Database 下的所有用户表
     *
     * @param ds     数据源配置
     * @param schema PostgreSQL 时为 schema 名，MySQL 时为 null（用 dbName 作 catalog）
     * @return 表名列表
     */
    public static List<String> fetchTables(DataSource ds, String schema) {
        List<String> tables = new ArrayList<>();
        if (!ds.isValid()) return tables;
        try (Connection conn = getConnection(ds)) {
            if ("postgresql".equalsIgnoreCase(ds.getDbType())) {
                // PostgreSQL: 使用 schema 模式查询
                try (ResultSet rs = conn.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if (tableName != null) {
                            tables.add(tableName);
                        }
                    }
                }
            } else {
                // MySQL: 直接用 SHOW TABLES 查询，避免 getTables catalog 参数兼容性问题
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'")) {
                    while (rs.next()) {
                        String tableName = rs.getString(1);
                        if (tableName != null) {
                            tables.add(tableName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 不抛异常，返回空列表
        }
        return tables;
    }

    /**
     * 查询指定表的所有列名
     *
     * @param ds        数据源配置
     * @param tableName 表名
     * @param schema    PostgreSQL 时为 schema 名，MySQL 时为 null
     * @return 列名列表，失败时返回空列表
     */
    public static List<String> fetchColumns(DataSource ds, String tableName, String schema) {
        List<String> columns = new ArrayList<>();
        if (!ds.isValid() || tableName == null || tableName.isBlank()) return columns;
        try (Connection conn = getConnection(ds)) {
            String catalog = null;
            String schemaPattern = schema;
            if (!"postgresql".equalsIgnoreCase(ds.getDbType())) {
                catalog = ds.getDbName();
                schemaPattern = null;
            }
            try (ResultSet rs = conn.getMetaData().getColumns(catalog, schemaPattern, tableName, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    if (colName != null) {
                        columns.add(colName);
                    }
                }
            }
        } catch (Exception e) {
            // 不抛异常，返回空列表
        }
        return columns;
    }

    /**
     * 查询指定表的自增列名
     *
     * @param ds        数据源配置
     * @param tableName 表名
     * @param schema    PostgreSQL 时为 schema 名，MySQL 时为 null
     * @return 自增列名，无自增列时返回 null
     */
    public static String fetchAutoIncrementColumn(DataSource ds, String tableName, String schema) {
        if (!ds.isValid() || tableName == null || tableName.isBlank()) return null;
        try (Connection conn = getConnection(ds)) {
            String catalog = null;
            String schemaPattern = schema;
            if (!"postgresql".equalsIgnoreCase(ds.getDbType())) {
                catalog = ds.getDbName();
                schemaPattern = null;
            }
            try (ResultSet rs = conn.getMetaData().getColumns(catalog, schemaPattern, tableName, "%")) {
                while (rs.next()) {
                    String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
                    if ("YES".equalsIgnoreCase(isAutoIncrement)) {
                        return rs.getString("COLUMN_NAME");
                    }
                }
            }
        } catch (Exception e) {
            // 不抛异常
        }
        return null;
    }

    /**
     * 安全关闭数据库连接，不抛出异常
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // 忽略关闭时的异常
            }
        }
    }

    /**
     * 批量关闭数据库资源（Statement、ResultSet、Connection）
     */
    public static void closeResources(Connection conn, Statement stmt, ResultSet rs) {
        if (rs != null) {
            try { rs.close(); } catch (SQLException ignored) {}
        }
        if (stmt != null) {
            try { stmt.close(); } catch (SQLException ignored) {}
        }
        closeQuietly(conn);
    }
}
