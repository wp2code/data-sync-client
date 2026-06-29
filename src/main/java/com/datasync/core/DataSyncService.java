package com.datasync.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * 数据同步核心业务类 封装单表全量数据同步逻辑：字段解析、SQL 动态构建、批量插入、事务管控、异常回滚
 */
public class DataSyncService {
    
    private static final int BATCH_SIZE = 500; // 每批次插入数据量
    
    
    /**
     * 执行单表全量数据同步（复用已有连接）
     *
     * @param source      源数据库配置
     * @param target      目标数据库配置
     * @param tableName   待同步表名
     * @param srcWrapper  源库已有连接（可为 null，null 时自动创建）
     * @param tgtWrapper  目标库已有连接（可为 null，null 时自动创建）
     * @param logConsumer 日志回调（用于 UI 实时输出）
     * @return 同步成功的数据条数；-1 表示失败
     */
    public int syncTableWithConn(DataSource source, DataSource target, String tableName, String tgtTable, boolean truncateBeforeSync,
            ConnectionWrapper srcWrapper, ConnectionWrapper tgtWrapper, Consumer<String> logConsumer) {
        // ── 日志工具 ──
        Consumer<String> log = msg -> {
            if (logConsumer != null) {
                logConsumer.accept(msg);
            }
        };
        
        Connection srcConn = null;
        Connection tgtConn = null;
        boolean srcSelfCreated = false;
        boolean tgtSelfCreated = false;
        Statement srcStmt = null;
        ResultSet rs = null;
        PreparedStatement tgtPstmt = null;
        
        try {
            // ── 1. 建立双端连接 ──
            if (srcWrapper != null && srcWrapper.getConnection() != null) {
                srcConn = srcWrapper.getConnection();
                log.accept("[INFO] 复用源数据库已有连接");
            } else {
                log.accept("[INFO] 正在连接源数据库...");
                srcConn = DbConnector.getConnection(source);
                srcSelfCreated = true;
                log.accept("[INFO] 源数据库连接成功");
            }
            
            if (tgtWrapper != null && tgtWrapper.getConnection() != null) {
                tgtConn = tgtWrapper.getConnection();
                log.accept("[INFO] 复用目标数据库已有连接");
            } else {
                log.accept("[INFO] 正在连接目标数据库...");
                tgtConn = DbConnector.getConnection(target);
                tgtSelfCreated = true;
                log.accept("[INFO] 目标数据库连接成功");
            }
            
            // ── 2. 清空目标表（可选）──
            if (truncateBeforeSync) {
                String tgtQualifiedTable = qualifiedTableName(target, tableName);
                log.accept("[INFO] 正在清空目标表: TRUNCATE TABLE " + tgtQualifiedTable);
                try (Statement truncateStmt = tgtConn.createStatement()) {
                    truncateStmt.executeUpdate("TRUNCATE TABLE " + tgtQualifiedTable);
                }
                log.accept("[INFO] 目标表已清空");
            }
            
            // ── 3. 查询源库全量数据 ──
            String qualifiedTable = qualifiedTableName(source, tableName);
            String selectSql = "SELECT * FROM " + qualifiedTable;
            log.accept("[INFO] 执行查询: " + selectSql);
            
            srcStmt = srcConn.createStatement();
            rs = srcStmt.executeQuery(selectSql);
            
            // ── 4. 解析字段结构 ──
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            String[] columnNames = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                columnNames[i] = meta.getColumnName(i + 1);
            }
            log.accept("[INFO] 表 [" + tableName + "] 共 " + columnCount + " 个字段: " + String.join(", ", columnNames));
            
            // ── 5. 动态构建 INSERT SQL ──
            String insertSql = buildInsertSql(target, tableName, columnNames);
            log.accept("[INFO] 目标库 INSERT SQL: " + insertSql);
            
            // ── 6. 开启事务 + 批量插入 ──
            tgtConn.setAutoCommit(false);
            tgtPstmt = tgtConn.prepareStatement(insertSql);
            
            int totalRows = 0;
            int skippedRows = 0;
            int batchCount = 0;
            
            while (rs.next()) {
                for (int i = 0; i < columnCount; i++) {
                    tgtPstmt.setObject(i + 1, rs.getObject(i + 1));
                }
                tgtPstmt.addBatch();
                batchCount++;
                
                if (batchCount >= BATCH_SIZE) {
                    int[] results = tgtPstmt.executeBatch();
                    int inserted = countInserted(results);
                    totalRows += inserted;
                    skippedRows += (batchCount - inserted);
                    log.accept("[INFO] 已同步 " + totalRows + " 条（跳过 " + skippedRows + " 条主键冲突）...");
                    batchCount = 0;
                }
            }
            
            // 处理最后一批
            if (batchCount > 0) {
                int[] results = tgtPstmt.executeBatch();
                int inserted = countInserted(results);
                totalRows += inserted;
                skippedRows += (batchCount - inserted);
            }
            
            // ── 7. 提交事务 ──
            tgtConn.commit();
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append("<html><body><span style='font-weight:bold;color:green;'>[SUCCESS] 同步完成！共同步 ")
                    .append(totalRows).append(" 条数据");
            if (skippedRows > 0) {
                summaryBuilder.append("，跳过 ").append(skippedRows).append(" 条（主键冲突）");
            }
            summaryBuilder.append(" 到表 [").append(tableName).append("]</span></body></html>");
            String summary = summaryBuilder.toString();
            log.accept(summary);
            return totalRows;
            
        } catch (Exception e) {
            log.accept("[ERROR] 同步异常 → " + e.getMessage());
            
            // ── 7. 异常回滚 ──
            if (tgtConn != null) {
                try {
                    tgtConn.rollback();
                    log.accept("[INFO] 事务已回滚，目标库数据未受影响");
                } catch (SQLException rollbackEx) {
                    log.accept("[ERROR] 事务回滚失败 → " + rollbackEx.getMessage());
                }
            }
            return -1;
            
        } finally {
            // ── 8. 统一资源释放（只关闭自己创建的连接，复用连接不关闭）──
            closeStatement(srcStmt);
            if (rs != null) {
                try {
                    closeStatement(rs.getStatement());
                } catch (SQLException ignored) {
                }
                try {
                    rs.close();
                } catch (SQLException ignored) {
                }
            }
            closeStatement(tgtPstmt);
            if (srcSelfCreated) {
                DbConnector.closeQuietly(srcConn);
            }
            if (tgtSelfCreated) {
                DbConnector.closeQuietly(tgtConn);
            }
            log.accept("[INFO] 同步资源已释放");
        }
    }
    
    // ────────── 私有辅助方法 ──────────
    
    /**
     * 动态构建 INSERT 语句（遇主键冲突自动跳过） MySQL: INSERT IGNORE INTO ... | PostgreSQL: INSERT INTO ... ON CONFLICT DO NOTHING
     */
    private String buildInsertSql(DataSource ds, String tableName, String[] columns) {
        String dbType = ds.getDbType();
        String qualifiedTable = qualifiedTableName(ds, tableName);
        StringBuilder cols = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                cols.append(", ");
                placeholders.append(", ");
            }
            cols.append(quoteIdentifier(columns[i], dbType));
            placeholders.append("?");
        }
        String baseSql = "INSERT INTO " + qualifiedTable + " (" + cols + ") VALUES (" + placeholders + ")";
        
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return baseSql + " ON CONFLICT DO NOTHING";
        }
        // MySQL: INSERT IGNORE
        return "INSERT IGNORE INTO " + qualifiedTable + " (" + cols + ") VALUES (" + placeholders + ")";
    }
    
    /**
     * 生成 schema 限定的表名（PostgreSQL 专属；MySQL 返回纯表名）
     */
    private String qualifiedTableName(DataSource ds, String tableName) {
        if ("postgresql".equalsIgnoreCase(ds.getDbType()) && ds.getSchema() != null && !ds.getSchema().isBlank()) {
            return quoteIdentifier(ds.getSchema(), "postgresql") + "." + quoteIdentifier(tableName, "postgresql");
        }
        return quoteIdentifier(tableName, ds.getDbType());
    }
    
    /**
     * 根据数据库类型对标识符加引号
     */
    private String quoteIdentifier(String name, String dbType) {
        if ("postgresql".equalsIgnoreCase(dbType)) {
            return "\"" + name + "\"";
        }
        // MySQL 使用反引号
        return "`" + name + "`";
    }
    
    private void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        }
    }
    
    /**
     * 统计批量插入结果中实际成功的条数 Statement.SUCCESS_NO_INFO（-2）视为成功，Statement.EXECUTE_FAILED（-3）视为失败
     */
    private int countInserted(int[] results) {
        int count = 0;
        for (int r : results) {
            if (r == Statement.EXECUTE_FAILED) {
                continue;
            }
            // SUCCESS_NO_INFO(-2) 和 >=0 都视为成功
            count++;
        }
        return count;
    }

    /**
     * 导出指定表的 INSERT SQL 脚本（导出所有列）
     *
     * @param ds        数据源配置
     * @param tableName 表名
     * @param wrapper   已有连接（可为 null）
     * @return INSERT SQL 脚本字符串，失败时返回空串
     */
    public String exportInsertScript(DataSource ds, String tableName, ConnectionWrapper wrapper) {
        return exportInsertScript(ds, tableName, wrapper, null);
    }

    /**
     * 导出指定表的 INSERT SQL 脚本（指定列）
     *
     * @param ds          数据源配置
     * @param tableName   表名
     * @param wrapper     已有连接（可为 null）
     * @param columnNames 要导出的列名列表，为 null 或空则导出所有列
     * @return INSERT SQL 脚本字符串，失败时返回空串
     */
    public String exportInsertScript(DataSource ds, String tableName, ConnectionWrapper wrapper, List<String> columnNames) {
        StringBuilder script = new StringBuilder();
        Connection conn = null;
        boolean selfCreated = false;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            if (wrapper != null && wrapper.getConnection() != null) {
                conn = wrapper.getConnection();
            } else {
                conn = DbConnector.getConnection(ds);
                selfCreated = true;
            }

            String qualifiedTable = qualifiedTableName(ds, tableName);
            String dbType = ds.getDbType();

            boolean selectAllColumns = (columnNames == null || columnNames.isEmpty());

            // 表头注释
            script.append("-- ============================================\n");
            script.append("-- 表: ").append(qualifiedTable).append("\n");
            script.append("-- 数据库: ").append(ds.getDbType().toUpperCase()).append(" | ")
                 .append(ds.getHost()).append(":").append(ds.getPort()).append("/").append(ds.getDbName()).append("\n");
            if (!selectAllColumns) {
                script.append("-- 导出列: ").append(String.join(", ", columnNames)).append("\n");
            }
            script.append("-- 导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            script.append("-- ============================================\n\n");

            // 构建 SELECT 语句
            String selectSql;
            if (selectAllColumns) {
                selectSql = "SELECT * FROM " + qualifiedTable;
            } else {
                String quotedCols = String.join(", ",
                        columnNames.stream().map(c -> quoteIdentifier(c, dbType)).toArray(String[]::new));
                selectSql = "SELECT " + quotedCols + " FROM " + qualifiedTable;
            }

            stmt = conn.createStatement();
            rs = stmt.executeQuery(selectSql);

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            String[] actualColumnNames = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                actualColumnNames[i] = meta.getColumnName(i + 1);
            }

            String cols = String.join(", ", java.util.Arrays.stream(actualColumnNames)
                    .map(c -> quoteIdentifier(c, dbType)).toArray(String[]::new));

            int rowCount = 0;
            while (rs.next()) {
                StringBuilder values = new StringBuilder();
                for (int i = 0; i < columnCount; i++) {
                    if (i > 0) values.append(", ");
                    values.append(formatSqlValue(rs, i + 1, meta.getColumnType(i + 1)));
                }
                script.append("INSERT INTO ").append(qualifiedTable)
                      .append(" (").append(cols).append(") VALUES (").append(values).append(");\n");
                rowCount++;
            }

            script.append("\n-- 共导出 ").append(rowCount).append(" 条数据\n");
            return script.toString();

        } catch (Exception e) {
            script.append("-- [ERROR] 导出失败: ").append(e.getMessage()).append("\n");
            return script.toString();
        } finally {
            if (rs != null) { try { rs.close(); } catch (SQLException ignored) {} }
            if (stmt != null) { try { stmt.close(); } catch (SQLException ignored) {} }
            if (selfCreated) { DbConnector.closeQuietly(conn); }
        }
    }

    /**
     * 将 ResultSet 中的值格式化为 SQL 字面量
     */
    private String formatSqlValue(ResultSet rs, int columnIndex, int sqlType) throws SQLException {
        Object value = rs.getObject(columnIndex);
        if (value == null || rs.wasNull()) {
            return "NULL";
        }
        switch (sqlType) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.REAL:
                return value.toString();
            case Types.BIT:
            case Types.BOOLEAN:
                return ((Boolean) value) ? "1" : "0";
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "'" + escapeSqlString(value.toString()) + "'";
            default:
                return "'" + escapeSqlString(value.toString()) + "'";
        }
    }

    /**
     * 转义 SQL 字符串中的特殊字符
     */
    private String escapeSqlString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
