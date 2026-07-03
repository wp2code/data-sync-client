package com.datasync.core;

import com.datasync.model.DataSource;
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

    /**
     * 列详细信息
     */
    public static class ColumnDetail {
        public String columnName;
        public String dataType;
        public int columnSize;
        public boolean nullable;
        public String defaultValue;
        public boolean isPrimaryKey;
        public boolean isAutoIncrement;
        public int ordinalPosition;
        public String comment;

        @Override
        public String toString() {
            return columnName + " " + dataType + (columnSize > 0 ? "(" + columnSize + ")" : "")
                    + (isPrimaryKey ? " PK" : "") + (nullable ? " NULL" : " NOT NULL")
                    + (isAutoIncrement ? " AUTO_INCREMENT" : "")
                    + (defaultValue != null ? " DEFAULT " + defaultValue : "")
                    + (comment != null && !comment.isEmpty() ? " COMMENT '" + comment + "'" : "");
        }
    }

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
     * 查询数据库服务器中的所有数据库（MySQL: SHOW DATABASES，PostgreSQL: pg_database）
     *
     * @param ds 数据源配置
     * @return 数据库名称列表，失败时返回包含当前 dbName 的列表
     */
    public static List<String> fetchDatabases(DataSource ds) {
        List<String> databases = new ArrayList<>();
        if (!ds.isValid()) {
            return databases;
        }
        String defaultDb = ds.getDbName();
        try (Connection conn = getConnection(ds);
             Statement stmt = conn.createStatement();
             ResultSet rs = ds.isPostgresql()
                 ? stmt.executeQuery("SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")
                 : stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String db = rs.getString(1);
                if (db == null) {
                    continue;
                }
                if (ds.isPostgresql()) {
                    if ("template0".equals(db) || "template1".equals(db)) {
                        continue;
                    }
                } else {
                    if ("information_schema".equals(db) || "mysql".equals(db)
                            || "performance_schema".equals(db) || "sys".equals(db)) {
                        continue;
                    }
                }
                if (!databases.contains(db)) {
                    databases.add(db);
                }
            }
        } catch (Exception e) {
            // 不抛异常，使用默认数据库兜底
        }
        if (databases.isEmpty() && defaultDb != null && !defaultDb.isBlank()) {
            databases.add(defaultDb);
        }
        return databases;
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
        if (!ds.isPostgresql() || !ds.isValid()) {
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
            if (ds.isPostgresql()) {
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
            if (!ds.isPostgresql()) {
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
            if (!ds.isPostgresql()) {
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
     * 查询指定表的列详细信息（列名、类型、是否可空、默认值、是否主键）
     *
     * @param ds        数据源配置
     * @param tableName 表名
     * @param schema    PostgreSQL 时为 schema 名，MySQL 时为 null
     * @return 列详情列表
     */
    public static List<ColumnDetail> fetchColumnDetails(DataSource ds, String tableName, String schema) {
        List<ColumnDetail> columns = new ArrayList<>();
        if (!ds.isValid() || tableName == null || tableName.isBlank()) return columns;
        try (Connection conn = getConnection(ds)) {
            String catalog = null;
            String schemaPattern = schema;
            if (!ds.isPostgresql()) {
                catalog = ds.getDbName();
                schemaPattern = null;
            }

            // 获取主键列集合
            java.util.Set<String> pkColumns = new java.util.HashSet<>();
            try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schemaPattern, tableName)) {
                while (pkRs.next()) {
                    pkColumns.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // 获取列详细信息
            try (ResultSet rs = conn.getMetaData().getColumns(catalog, schemaPattern, tableName, "%")) {
                while (rs.next()) {
                    ColumnDetail col = new ColumnDetail();
                    col.columnName = rs.getString("COLUMN_NAME");
                    col.dataType = rs.getString("TYPE_NAME");
                    col.columnSize = rs.getInt("COLUMN_SIZE");
                    col.nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"))
                            || "1".equals(rs.getString("NULLABLE"));
                    col.defaultValue = rs.getString("COLUMN_DEF");
                    col.isPrimaryKey = pkColumns.contains(col.columnName);
                    col.isAutoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                    col.ordinalPosition = rs.getInt("ORDINAL_POSITION");
                    col.comment = rs.getString("REMARKS");
                    columns.add(col);
                }
            }
        } catch (Exception e) {
            // 不抛异常，返回空列表
        }
        return columns;
    }

    /**
     * 索引详细信息
     */
    public static class IndexDetail {
        public String indexName;
        public String columnName;
        public boolean nonUnique;      // true=非唯一索引, false=唯一索引
        public short ordinalPosition;  // 列在索引中的位置（1-based）
        public String ascOrDesc;       // "A"=升序, "D"=降序

        @Override
        public String toString() {
            return indexName + (nonUnique ? " (INDEX)" : " (UNIQUE)") + " ON " + columnName
                    + ("D".equals(ascOrDesc) ? " DESC" : "");
        }
    }

    /**
     * 查询指定表的所有索引信息
     *
     * @param ds        数据源配置
     * @param tableName 表名
     * @param schema    PostgreSQL 时为 schema 名，MySQL 时为 null
     * @return 索引详情列表
     */
    public static List<IndexDetail> fetchIndexes(DataSource ds, String tableName, String schema) {
        List<IndexDetail> indexes = new ArrayList<>();
        if (!ds.isValid() || tableName == null || tableName.isBlank()) return indexes;
        try (Connection conn = getConnection(ds)) {
            String catalog = null;
            String schemaPattern = schema;
            if (!ds.isPostgresql()) {
                catalog = ds.getDbName();
                schemaPattern = null;
            }
            try (ResultSet rs = conn.getMetaData().getIndexInfo(catalog, schemaPattern, tableName, false, false)) {
                while (rs.next()) {
                    // 排除统计类索引和表统计行
                    short type = rs.getShort("TYPE");
                    if (type == java.sql.DatabaseMetaData.tableIndexStatistic) continue;

                    String indexName = rs.getString("INDEX_NAME");
                    // 排除主键索引（主键索引由列比较处理）
                    if (indexName == null || "PRIMARY".equalsIgnoreCase(indexName)) continue;

                    IndexDetail idx = new IndexDetail();
                    idx.indexName = indexName;
                    idx.columnName = rs.getString("COLUMN_NAME");
                    idx.nonUnique = rs.getBoolean("NON_UNIQUE");
                    idx.ordinalPosition = rs.getShort("ORDINAL_POSITION");
                    idx.ascOrDesc = rs.getString("ASC_OR_DESC");
                    indexes.add(idx);
                }
            }
        } catch (Exception e) {
            // 不抛异常，返回空列表
        }
        return indexes;
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
