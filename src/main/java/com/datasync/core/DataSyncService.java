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
import java.util.ArrayList;
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
            String insertSql = buildInsertSql(target, tableName, columnNames, tgtConn);
            log.accept("[INFO] 目标库 INSERT SQL: " + insertSql);
            
            // ── 6. 开启事务 + 批量插入 ──
            tgtConn.setAutoCommit(false);
            tgtPstmt = tgtConn.prepareStatement(insertSql);
            
            int totalRows = 0;
            int batchCount = 0;
            
            while (rs.next()) {
                for (int i = 0; i < columnCount; i++) {
                    tgtPstmt.setObject(i + 1, rs.getObject(i + 1));
                }
                tgtPstmt.addBatch();
                batchCount++;
                
                if (batchCount >= BATCH_SIZE) {
                    int[] results = tgtPstmt.executeBatch();
                    int affected = countAffected(results);
                    totalRows += affected;
                    log.accept("[INFO] 已同步 " + totalRows + " 条（含主键冲突自动更新）...");
                    batchCount = 0;
                }
            }
            
            // 处理最后一批
            if (batchCount > 0) {
                int[] results = tgtPstmt.executeBatch();
                int affected = countAffected(results);
                totalRows += affected;
            }
            
            // ── 7. 提交事务 ──
            tgtConn.commit();
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append("<html><body><span style='font-weight:bold;color:green;'>[SUCCESS] 同步完成！共同步 ")
                    .append(totalRows).append(" 条数据");
            summaryBuilder.append(" 到表 [").append(tableName).append("]（遇主键冲突自动更新）</span></body></html>");
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
     * 动态构建 INSERT 语句（遇主键冲突自动更新字段）
     * MySQL: INSERT INTO ... ON DUPLICATE KEY UPDATE col1=VALUES(col1), ...
     * PostgreSQL: INSERT INTO ... ON CONFLICT (pk_cols) DO UPDATE SET col1=EXCLUDED.col1, ...
     */
    private String buildInsertSql(DataSource ds, String tableName, String[] columns, Connection tgtConn) throws SQLException {
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

        if (ds.isPostgresql()) {
            // PostgreSQL: 通过 JDBC 元数据获取主键列作为 ON CONFLICT 冲突目标
            String conflictTarget = getConflictTarget(ds, tableName, tgtConn);
            StringBuilder updateSet = new StringBuilder();
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) updateSet.append(", ");
                String col = quoteIdentifier(columns[i], dbType);
                updateSet.append(col).append(" = EXCLUDED.").append(col);
            }
            return baseSql + " ON CONFLICT (" + conflictTarget + ") DO UPDATE SET " + updateSet;
        }
        // MySQL: INSERT ... ON DUPLICATE KEY UPDATE
        StringBuilder updateSet = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) updateSet.append(", ");
            String col = quoteIdentifier(columns[i], dbType);
            updateSet.append(col).append(" = VALUES(").append(col).append(")");
        }
        return baseSql + " ON DUPLICATE KEY UPDATE " + updateSet;
    }

    /**
     * 通过 JDBC 元数据获取表的主键列名，拼接为逗号分隔的带引号标识符
     */
    private String getConflictTarget(DataSource ds, String tableName, Connection conn) throws SQLException {
        String schema = ds.getSchema();
        java.sql.DatabaseMetaData meta = conn.getMetaData();
        // PostgreSQL 中 schema 可能为 null（默认 public），传入 null 让 JDBC 使用当前 schema
        try (ResultSet pkRs = meta.getPrimaryKeys(null, schema, tableName)) {
            StringBuilder pkCols = new StringBuilder();
            while (pkRs.next()) {
                if (!pkCols.isEmpty()) pkCols.append(", ");
                pkCols.append(quoteIdentifier(pkRs.getString("COLUMN_NAME"), ds.getDbType()));
            }
            if (pkCols.isEmpty()) {
                throw new SQLException("表 [" + qualifiedTableName(ds, tableName) + "] 未找到主键，无法构建 ON CONFLICT 语句");
            }
            return pkCols.toString();
        }
    }
    
    /**
     * 生成 schema 限定的表名（PostgreSQL 专属；MySQL 返回纯表名）
     */
    private String qualifiedTableName(DataSource ds, String tableName) {
        if (ds.isPostgresql() && ds.getSchema() != null && !ds.getSchema().isBlank()) {
            return quoteIdentifier(ds.getSchema(), ds.getDbType()) + "." + quoteIdentifier(tableName, ds.getDbType());
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

    /** 使用 DataSource 对象判断是否用 PG 引号 */
    private String quoteIdentifier(String name, DataSource ds) {
        return quoteIdentifier(name, ds.getDbType());
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
     * 统计批量操作中实际影响的行数
     * Statement.SUCCESS_NO_INFO（-2）视为成功（计1），Statement.EXECUTE_FAILED（-3）视为失败（不计）
     */
    private int countAffected(int[] results) {
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

    // ────────── 表结构比较 ──────────

    /**
     * 比较源表和目标表的列结构差异，返回结构化差异列表
     *
     * @param srcColumns 源表列详情
     * @param tgtColumns 目标表列详情
     * @param srcDs      源数据源配置（用于 SQL 生成）
     * @param tgtDs      目标数据源配置（用于 SQL 生成）
     * @return 差异列表
     */
    public List<ColumnDiff> compareTableStructure(List<DbConnector.ColumnDetail> srcColumns,
                                                   List<DbConnector.ColumnDetail> tgtColumns,
                                                   DataSource srcDs, DataSource tgtDs) {
        List<ColumnDiff> diffs = new ArrayList<>();
        String tgtDbType = tgtDs.getDbType();

        // 源表列名 -> 列详情映射
        java.util.Map<String, DbConnector.ColumnDetail> srcMap = new java.util.LinkedHashMap<>();
        for (DbConnector.ColumnDetail col : srcColumns) {
            srcMap.put(col.columnName.toLowerCase(), col);
        }
        // 目标表列名 -> 列详情映射
        java.util.Map<String, DbConnector.ColumnDetail> tgtMap = new java.util.LinkedHashMap<>();
        for (DbConnector.ColumnDetail col : tgtColumns) {
            tgtMap.put(col.columnName.toLowerCase(), col);
        }

        // 1. 目标表缺少的列（需 ADD）
        for (DbConnector.ColumnDetail srcCol : srcColumns) {
            if (!tgtMap.containsKey(srcCol.columnName.toLowerCase())) {
                ColumnDiff diff = new ColumnDiff();
                diff.type = DiffType.ADD_COLUMN;
                diff.columnName = srcCol.columnName;
                diff.srcColumn = srcCol;
                diff.alterSql = buildAddColumnSql(srcCol, tgtDbType);
                diffs.add(diff);
            }
        }

        // 2. 目标表多余的列（需 DROP）
        for (DbConnector.ColumnDetail tgtCol : tgtColumns) {
            if (!srcMap.containsKey(tgtCol.columnName.toLowerCase())) {
                ColumnDiff diff = new ColumnDiff();
                diff.type = DiffType.DROP_COLUMN;
                diff.columnName = tgtCol.columnName;
                diff.tgtColumn = tgtCol;
                diff.alterSql = buildDropColumnSql(tgtCol, tgtDbType);
                diffs.add(diff);
            }
        }

        // 3. 类型/约束不同的列（需 MODIFY）
        for (DbConnector.ColumnDetail srcCol : srcColumns) {
            DbConnector.ColumnDetail tgtCol = tgtMap.get(srcCol.columnName.toLowerCase());
            if (tgtCol != null && !columnsEqual(srcCol, tgtCol)) {
                ColumnDiff diff = new ColumnDiff();
                // 如果结构相同仅注释不同，标记为 COMMENT_DIFF
                if (columnsStructEqual(srcCol, tgtCol) && !commentsEqual(srcCol, tgtCol)) {
                    diff.type = DiffType.COMMENT_DIFF;
                } else {
                    diff.type = DiffType.MODIFY_COLUMN;
                }
                diff.columnName = srcCol.columnName;
                diff.srcColumn = srcCol;
                diff.tgtColumn = tgtCol;
                diff.alterSql = buildModifyColumnSql(srcCol, tgtCol, tgtDbType);
                diffs.add(diff);
            }
        }

        return diffs;
    }

    /**
     * 比较源表和目标表的索引差异，返回索引差异列表
     *
     * @param srcIndexes 源表索引详情
     * @param tgtIndexes 目标表索引详情
     * @param tgtDbType  目标库类型（用于 SQL 生成）
     * @param tgtSchema  目标库 schema（PostgreSQL 时为 schema 名，MySQL 时为 null）
     * @return 索引差异列表
     */
    public List<IndexDiff> compareIndexes(List<DbConnector.IndexDetail> srcIndexes,
                                           List<DbConnector.IndexDetail> tgtIndexes,
                                           String tgtDbType, String tgtSchema) {
        List<IndexDiff> diffs = new ArrayList<>();

        // 按索引名分组: 索引名 → 列名列表（保持顺序）
        java.util.Map<String, List<DbConnector.IndexDetail>> srcIdxMap = groupIndexesByName(srcIndexes);
        java.util.Map<String, List<DbConnector.IndexDetail>> tgtIdxMap = groupIndexesByName(tgtIndexes);

        // 1. 目标表缺少的索引（需 ADD）
        for (String idxName : srcIdxMap.keySet()) {
            if (!tgtIdxMap.containsKey(idxName)) {
                List<DbConnector.IndexDetail> idxCols = srcIdxMap.get(idxName);
                IndexDiff diff = new IndexDiff();
                diff.type = DiffType.ADD_INDEX;
                diff.indexName = idxName;
                diff.srcIndexColumns = idxCols;
                diff.alterSql = buildAddIndexSql(idxName, idxCols, tgtDbType);
                diffs.add(diff);
            }
        }

        // 2. 目标表多余的索引（需 DROP）
        for (String idxName : tgtIdxMap.keySet()) {
            if (!srcIdxMap.containsKey(idxName)) {
                IndexDiff diff = new IndexDiff();
                diff.type = DiffType.DROP_INDEX;
                diff.indexName = idxName;
                diff.tgtIndexColumns = tgtIdxMap.get(idxName);
                diff.alterSql = buildDropIndexSql(idxName, tgtDbType, tgtSchema);
                diffs.add(diff);
            }
        }

        // 3. 同名索引但定义不同（列或唯一性不同）
        for (String idxName : srcIdxMap.keySet()) {
            List<DbConnector.IndexDetail> srcCols = srcIdxMap.get(idxName);
            List<DbConnector.IndexDetail> tgtCols = tgtIdxMap.get(idxName);
            if (tgtCols != null && !indexesEqual(srcCols, tgtCols)) {
                IndexDiff diff = new IndexDiff();
                diff.type = DiffType.MODIFY_INDEX;
                diff.indexName = idxName;
                diff.srcIndexColumns = srcCols;
                diff.tgtIndexColumns = tgtCols;
                // 修改索引 = 先 DROP 再 ADD
                diff.alterSql = buildDropIndexSql(idxName, tgtDbType, tgtSchema) + ";\n"
                        + buildAddIndexSql(idxName, srcCols, tgtDbType);
                diffs.add(diff);
            }
        }

        return diffs;
    }

    /**
     * 按索引名分组，同一索引的多列按 ordinalPosition 排序
     */
    private java.util.Map<String, List<DbConnector.IndexDetail>> groupIndexesByName(List<DbConnector.IndexDetail> indexes) {
        java.util.Map<String, List<DbConnector.IndexDetail>> map = new java.util.LinkedHashMap<>();
        for (DbConnector.IndexDetail idx : indexes) {
            map.computeIfAbsent(idx.indexName, k -> new ArrayList<>()).add(idx);
        }
        // 每组内按 ordinalPosition 排序
        for (List<DbConnector.IndexDetail> cols : map.values()) {
            cols.sort((a, b) -> Short.compare(a.ordinalPosition, b.ordinalPosition));
        }
        return map;
    }

    /**
     * 判断两个索引是否相同（比较列列表和唯一性）
     */
    private boolean indexesEqual(List<DbConnector.IndexDetail> a, List<DbConnector.IndexDetail> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            DbConnector.IndexDetail ia = a.get(i);
            DbConnector.IndexDetail ib = b.get(i);
            if (!ia.columnName.equalsIgnoreCase(ib.columnName)) return false;
            if (ia.nonUnique != ib.nonUnique) return false;
            // 比较排序方向（null 视为 "A"）
            String dirA = ia.ascOrDesc != null ? ia.ascOrDesc : "A";
            String dirB = ib.ascOrDesc != null ? ib.ascOrDesc : "A";
            if (!dirA.equals(dirB)) return false;
        }
        return true;
    }

    private static boolean isPostgresqlType(String dbType) {
        return "postgresql".equalsIgnoreCase(dbType);
    }

    /**
     * 构建 CREATE INDEX 语句
     */
    private String buildAddIndexSql(String indexName, List<DbConnector.IndexDetail> cols, String dbType) {
        StringBuilder sql = new StringBuilder();
        boolean isUnique = !cols.isEmpty() && !cols.get(0).nonUnique;

        sql.append("CREATE ");
        if (isUnique) {
            sql.append("UNIQUE ");
        }
        sql.append("INDEX ");
        if (isPostgresqlType(dbType)) {
            sql.append("\"").append(indexName).append("\"");
        } else {
            sql.append("`").append(indexName).append("`");
        }
        sql.append(" ON "); // 表名由 generateAlterScript 拼接

        // 列列表
        StringBuilder colList = new StringBuilder();
        for (DbConnector.IndexDetail col : cols) {
            if (!colList.isEmpty()) colList.append(", ");
            String quotedCol = isPostgresqlType(dbType)
                    ? "\"" + col.columnName + "\"" : "`" + col.columnName + "`";
            colList.append(quotedCol);
            if ("D".equals(col.ascOrDesc)) {
                colList.append(" DESC");
            }
        }
        sql.append("(").append(colList).append(")");

        return sql.toString();
    }

    /**
     * 构建 DROP INDEX 语句
     */
    private String buildDropIndexSql(String indexName, String dbType, String schema) {
        if (isPostgresqlType(dbType)) {
            // PostgreSQL: DROP INDEX 需要带上 schema 前缀
            if (schema != null && !schema.isBlank()) {
                return "DROP INDEX IF EXISTS \"" + schema + "\".\"" + indexName + "\"";
            }
            return "DROP INDEX IF EXISTS \"" + indexName + "\"";
        }
        // MySQL: DROP INDEX ON table
        return "DROP INDEX `" + indexName + "`";
    }

    /**
     * 根据差异列表生成完整的 ALTER TABLE 脚本（含索引差异）
     *
     * @param tableName  表名（裸表名）
     * @param diffs      列差异列表
     * @param tgtDbType  目标库类型
     * @param schema     目标库 schema（MySQL 为 null，PostgreSQL 为 schema 名）
     * @param indexDiffs 索引差异列表（可为 null 或空）
     */
    public String generateAlterScript(String tableName, List<ColumnDiff> diffs, String tgtDbType, String schema,
                                       List<IndexDiff> indexDiffs) {
        StringBuilder script = new StringBuilder();
        String fullName = buildFullTableName(tableName, tgtDbType, schema);

        int totalDiffs = diffs.size() + (indexDiffs != null ? indexDiffs.size() : 0);
        if (totalDiffs == 0) {
            return "-- 表结构一致，无需修改\n";
        }

        script.append("-- ============================================\n");
        script.append("-- 表结构同步脚本: ").append(fullName).append("\n");
        script.append("-- 目标库类型: ").append(tgtDbType.toUpperCase()).append("\n");
        script.append("-- 差异数: ").append(totalDiffs).append("\n");
        script.append("-- ============================================\n\n");

        // 按类型排序：ADD 在前，MODIFY/COMMENT 在中，DROP 在后，索引在列之后
        java.util.List<ColumnDiff> sorted = new java.util.ArrayList<>(diffs);
        sorted.sort((a, b) -> {
            int orderA = a.type == DiffType.ADD_COLUMN ? 0 :
                         a.type == DiffType.DROP_COLUMN ? 2 : 1;
            int orderB = b.type == DiffType.ADD_COLUMN ? 0 :
                         b.type == DiffType.DROP_COLUMN ? 2 : 1;
            return Integer.compare(orderA, orderB);
        });

        for (ColumnDiff diff : sorted) {
            script.append("-- ").append(diff.type.getLabel()).append(": ").append(diff.columnName).append("\n");

            if (diff.type == DiffType.COMMENT_DIFF) {
                if (isPostgresqlType(tgtDbType)) {
                    String commentSql = buildPgCommentSql(diff.srcColumn, diff.tgtColumn, fullName);
                    if (commentSql != null) {
                        script.append(commentSql).append(";\n\n");
                    }
                } else {
                    script.append("ALTER TABLE ").append(fullName).append(" ").append(diff.alterSql).append(";\n\n");
                }
            } else if (diff.type == DiffType.MODIFY_COLUMN) {
                script.append("ALTER TABLE ").append(fullName).append(" ").append(diff.alterSql).append(";\n\n");
                if (isPostgresqlType(tgtDbType) && diff.srcColumn != null && diff.tgtColumn != null) {
                    String commentSql = buildPgCommentSql(diff.srcColumn, diff.tgtColumn, fullName);
                    if (commentSql != null) {
                        script.append(commentSql).append(";\n\n");
                    }
                }
            } else {
                script.append("ALTER TABLE ").append(fullName).append(" ").append(diff.alterSql).append(";\n\n");
                if (diff.type == DiffType.ADD_COLUMN && isPostgresqlType(tgtDbType)
                        && diff.srcColumn != null && diff.srcColumn.comment != null && !diff.srcColumn.comment.isBlank()) {
                    String quotedCol = "\"" + diff.srcColumn.columnName + "\"";
                    script.append("COMMENT ON COLUMN ").append(fullName).append(".").append(quotedCol)
                            .append(" IS '").append(escapeSqlString(diff.srcColumn.comment.trim())).append("';\n\n");
                }
            }
        }

        // 索引差异 DDL
        if (indexDiffs != null && !indexDiffs.isEmpty()) {
            script.append("-- ============================================\n");
            script.append("-- 索引差异\n");
            script.append("-- ============================================\n\n");

            // 先 DROP 后 ADD/新建
            List<IndexDiff> sortedIdx = new java.util.ArrayList<>(indexDiffs);
            sortedIdx.sort((a, b) -> {
                int orderA = a.type == DiffType.DROP_INDEX ? 0 :
                             a.type == DiffType.ADD_INDEX ? 2 : 1;
                int orderB = b.type == DiffType.DROP_INDEX ? 0 :
                             b.type == DiffType.ADD_INDEX ? 2 : 1;
                return Integer.compare(orderA, orderB);
            });

            for (IndexDiff idxDiff : sortedIdx) {
                script.append("-- ").append(idxDiff.type.getLabel()).append(": ").append(idxDiff.indexName)
                        .append(" (").append(idxDiff.getColumnNames()).append(")");
                if (idxDiff.isUnique()) script.append(" UNIQUE");
                script.append("\n");

                if (idxDiff.type == DiffType.MODIFY_INDEX) {
                    // 先 DROP 再 CREATE
                    String dropSql = buildDropIndexSql(idxDiff.indexName, tgtDbType, schema);
                    String addSql = buildAddIndexSql(idxDiff.indexName, idxDiff.srcIndexColumns, tgtDbType);
                    // DROP INDEX 对于 MySQL 需要表名
                    if (isPostgresqlType(tgtDbType)) {
                        script.append(dropSql).append(";\n");
                    } else {
                        script.append("ALTER TABLE ").append(fullName).append(" ").append(dropSql).append(";\n");
                    }
                    script.append(addSql.replace(" ON ", " ON " + fullName + " ")).append(";\n\n");
                } else if (idxDiff.type == DiffType.ADD_INDEX) {
                    String addSql = buildAddIndexSql(idxDiff.indexName, idxDiff.srcIndexColumns, tgtDbType);
                    script.append(addSql.replace(" ON ", " ON " + fullName + " ")).append(";\n\n");
                } else if (idxDiff.type == DiffType.DROP_INDEX) {
                    String dropSql = buildDropIndexSql(idxDiff.indexName, tgtDbType, schema);
                    if (isPostgresqlType(tgtDbType)) {
                        script.append(dropSql).append(";\n\n");
                    } else {
                        script.append("ALTER TABLE ").append(fullName).append(" ").append(dropSql).append(";\n\n");
                    }
                }
            }
        }

        script.append("-- 脚本生成完毕\n");
        return script.toString();
    }

    /**
     * 根据差异列表生成完整的 ALTER TABLE 脚本（仅列差异，向后兼容）
     */
    public String generateAlterScript(String tableName, List<ColumnDiff> diffs, String tgtDbType, String schema) {
        return generateAlterScript(tableName, diffs, tgtDbType, schema, null);
    }

    /**
     * 构建带 schema 前缀的完整表名
     */
    private String buildFullTableName(String tableName, String dbType, String schema) {
        String quotedTable = isPostgresqlType(dbType)
                ? "\"" + tableName + "\"" : "`" + tableName + "`";
        if (schema != null && !schema.isBlank()) {
            String quotedSchema = isPostgresqlType(dbType)
                    ? "\"" + schema + "\"" : "`" + schema + "`";
            return quotedSchema + "." + quotedTable;
        }
        return quotedTable;
    }

    /**
     * 构建 ADD COLUMN 子句
     */
    private String buildAddColumnSql(DbConnector.ColumnDetail col, String dbType) {
        return "ADD COLUMN " + buildColumnDefinition(col, dbType);
    }

    /**
     * 构建 DROP COLUMN 子句
     */
    private String buildDropColumnSql(DbConnector.ColumnDetail col, String dbType) {
        String quotedName = isPostgresqlType(dbType) ? "\"" + col.columnName + "\"" : "`" + col.columnName + "`";
        return "DROP COLUMN " + quotedName;
    }

    /**
     * 构建 MODIFY COLUMN 子句
     */
    private String buildModifyColumnSql(DbConnector.ColumnDetail srcCol, DbConnector.ColumnDetail tgtCol, String dbType) {
        if (isPostgresqlType(dbType)) {
            return buildPgModifyColumnSql(srcCol, tgtCol);
        }
        // MySQL: MODIFY COLUMN
        return "MODIFY COLUMN " + buildColumnDefinition(srcCol, dbType);
    }

    /**
     * PostgreSQL 修改列的 SQL（需要用 ALTER COLUMN ... TYPE / SET NOT NULL 等组合）
     * 注意：COMMENT ON COLUMN 是独立语句，不放在 ALTER TABLE 中，由 generateAlterScript 单独处理
     */
    private String buildPgModifyColumnSql(DbConnector.ColumnDetail srcCol, DbConnector.ColumnDetail tgtCol) {
        StringBuilder sql = new StringBuilder();
        String quotedName = "\"" + srcCol.columnName + "\"";

        // 类型变更
        if (!srcCol.dataType.equalsIgnoreCase(tgtCol.dataType) || srcCol.columnSize != tgtCol.columnSize) {
            String typeDef = srcCol.dataType;
            if (srcCol.columnSize > 0 && needsSize(typeDef)) {
                typeDef += "(" + srcCol.columnSize + ")";
            }
            sql.append("ALTER COLUMN ").append(quotedName).append(" TYPE ").append(typeDef).append(";\n");
        }

        // NOT NULL 变更
        if (srcCol.nullable != tgtCol.nullable) {
            if (!srcCol.nullable) {
                sql.append("ALTER COLUMN ").append(quotedName).append(" SET NOT NULL;\n");
            } else {
                sql.append("ALTER COLUMN ").append(quotedName).append(" DROP NOT NULL;\n");
            }
        }

        // 默认值变更
        String srcDef = srcCol.defaultValue;
        String tgtDef = tgtCol.defaultValue;
        boolean srcHasDefault = srcDef != null && !srcDef.isBlank();
        boolean tgtHasDefault = tgtDef != null && !tgtDef.isBlank();
        if (srcHasDefault != tgtHasDefault || (srcHasDefault && !srcDef.equals(tgtDef))) {
            if (srcHasDefault) {
                sql.append("ALTER COLUMN ").append(quotedName).append(" SET DEFAULT ").append(srcDef).append(";\n");
            } else {
                sql.append("ALTER COLUMN ").append(quotedName).append(" DROP DEFAULT;\n");
            }
        }

        // 去掉末尾多余的 ;\n
        String result = sql.toString().trim();
        if (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.replace("\n", "\n    "); // 缩进后续 ALTER
    }

    /**
     * 构建 PostgreSQL COMMENT ON COLUMN 独立语句（含完整表名）
     *
     * @param srcCol   源列详情
     * @param tgtCol   目标列详情
     * @param fullName 完整表名（含 schema 前缀）
     * @return COMMENT ON COLUMN 语句，如果注释无变化则返回 null
     */
    private String buildPgCommentSql(DbConnector.ColumnDetail srcCol, DbConnector.ColumnDetail tgtCol, String fullName) {
        String srcComment = srcCol.comment != null ? srcCol.comment.trim() : "";
        String tgtComment = tgtCol.comment != null ? tgtCol.comment.trim() : "";
        if (srcComment.equals(tgtComment)) {
            return null;
        }
        String quotedCol = "\"" + srcCol.columnName + "\"";
        if (!srcComment.isEmpty()) {
            return "COMMENT ON COLUMN " + fullName + "." + quotedCol + " IS '" + escapeSqlString(srcComment) + "'";
        } else {
            return "COMMENT ON COLUMN " + fullName + "." + quotedCol + " IS NULL";
        }
    }

    /**
     * 构建列定义字符串
     */
    private String buildColumnDefinition(DbConnector.ColumnDetail col, String dbType) {
        StringBuilder def = new StringBuilder();
        String quotedName = isPostgresqlType(dbType) ? "\"" + col.columnName + "\"" : "`" + col.columnName + "`";
        def.append(quotedName).append(" ");

        String type = col.dataType;
        if (col.columnSize > 0 && needsSize(type)) {
            type += "(" + col.columnSize + ")";
        }
        def.append(type);

        if (!col.nullable) {
            def.append(" NOT NULL");
        } else {
            def.append(" NULL");
        }

        if (col.defaultValue != null && !col.defaultValue.isBlank()) {
            def.append(" DEFAULT ").append(col.defaultValue);
        }

        if (col.isAutoIncrement) {
            if (isPostgresqlType(dbType)) {
                // PG 自增列已经通过 SERIAL 类型处理，不需要额外子句
            } else {
                def.append(" AUTO_INCREMENT");
            }
        }

        // MySQL: MODIFY/ADD COLUMN 支持 COMMENT 子句
        if (!isPostgresqlType(dbType)) {
            if (col.comment != null && !col.comment.isBlank()) {
                def.append(" COMMENT '").append(escapeSqlString(col.comment)).append("'");
            }
        }

        return def.toString();
    }

    /**
     * 判断两个列的属性是否完全相等（包含注释比较）
     */
    private boolean columnsEqual(DbConnector.ColumnDetail a, DbConnector.ColumnDetail b) {
        if (!a.dataType.equalsIgnoreCase(b.dataType)) return false;
        if (a.columnSize != b.columnSize) return false;
        if (a.nullable != b.nullable) return false;
        String defA = a.defaultValue != null ? a.defaultValue.trim() : null;
        String defB = b.defaultValue != null ? b.defaultValue.trim() : null;
        if (defA == null && defB == null) {
            // 继续检查注释
        } else if (defA == null || defB == null) {
            return false;
        } else if (!defA.equalsIgnoreCase(defB)) {
            return false;
        }
        // 比较注释
        String commentA = a.comment != null ? a.comment.trim() : "";
        String commentB = b.comment != null ? b.comment.trim() : "";
        return commentA.equals(commentB);
    }

    /**
     * 判断两个列的结构属性（不含注释）是否相等
     */
    private boolean columnsStructEqual(DbConnector.ColumnDetail a, DbConnector.ColumnDetail b) {
        if (!a.dataType.equalsIgnoreCase(b.dataType)) return false;
        if (a.columnSize != b.columnSize) return false;
        if (a.nullable != b.nullable) return false;
        String defA = a.defaultValue != null ? a.defaultValue.trim() : null;
        String defB = b.defaultValue != null ? b.defaultValue.trim() : null;
        if (defA == null && defB == null) return true;
        if (defA == null || defB == null) return false;
        return defA.equalsIgnoreCase(defB);
    }

    /**
     * 判断两个列的注释是否相等
     */
    private boolean commentsEqual(DbConnector.ColumnDetail a, DbConnector.ColumnDetail b) {
        String commentA = a.comment != null ? a.comment.trim() : "";
        String commentB = b.comment != null ? b.comment.trim() : "";
        return commentA.equals(commentB);
    }

    /**
     * 判断数据类型是否需要 SIZE 参数
     */
    private boolean needsSize(String dataType) {
        if (dataType == null) return false;
        String upper = dataType.toUpperCase();
        // 不需要 size 的类型
        return !upper.equals("TEXT") && !upper.equals("LONGTEXT") && !upper.equals("MEDIUMTEXT")
                && !upper.equals("TINYTEXT") && !upper.equals("DATE") && !upper.equals("DATETIME")
                && !upper.equals("TIMESTAMP") && !upper.equals("TIME") && !upper.equals("YEAR")
                && !upper.equals("BOOLEAN") && !upper.equals("BOOL") && !upper.equals("JSON")
                && !upper.equals("JSONB") && !upper.equals("BLOB") && !upper.equals("LONGBLOB")
                && !upper.equals("MEDIUMBLOB") && !upper.equals("TINYBLOB")
                && !upper.equals("SERIAL") && !upper.equals("BIGSERIAL")
                && !upper.equals("SMALLSERIAL") && !upper.equals("UUID")
                && !upper.equals("INT4") && !upper.equals("INT8") && !upper.equals("INT2")
                && !upper.equals("FLOAT4") && !upper.equals("FLOAT8") && !upper.equals("BOOL");
    }

    /**
     * 差异类型（列 + 索引）
     */
    public enum DiffType {
        ADD_COLUMN("新增列"),
        DROP_COLUMN("删除多余列"),
        MODIFY_COLUMN("修改列"),
        COMMENT_DIFF("注释差异"),
        ADD_INDEX("新增索引"),
        DROP_INDEX("删除多余索引"),
        MODIFY_INDEX("修改索引");

        private final String label;

        DiffType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 列差异详情
     */
    public static class ColumnDiff {
        public DiffType type;
        public String columnName;
        public DbConnector.ColumnDetail srcColumn;
        public DbConnector.ColumnDetail tgtColumn;
        public String alterSql;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(type.getLabel()).append("] ").append(columnName);
            if (type == DiffType.MODIFY_COLUMN && tgtColumn != null && srcColumn != null) {
                sb.append("\n  目标: ").append(tgtColumn.toString());
                sb.append("\n  源  : ").append(srcColumn.toString());
            }
            if (alterSql != null) {
                sb.append("\n  SQL: ").append(alterSql);
            }
            return sb.toString();
        }
    }

    // ────────── 导出 CREATE TABLE DDL ──────────

    /**
     * 生成单表的完整 CREATE TABLE DDL（含索引和注释）
     *
     * @param ds        数据源配置
     * @param tableName 表名
     * @param schema    schema 名（MySQL 为 null，PostgreSQL 为 schema 名）
     * @return 完整 CREATE TABLE 语句，失败时返回带错误注释的字符串
     */
    public String generateCreateTableDDL(DataSource ds, String tableName, String schema) {
        StringBuilder ddl = new StringBuilder();
        try {
            String dbType = ds.getDbType();
            boolean isPg = isPostgresqlType(dbType);
            String fullName = buildFullTableName(tableName, dbType, schema);

            List<DbConnector.ColumnDetail> columns = DbConnector.fetchColumnDetails(ds, tableName, schema);
            if (columns.isEmpty()) {
                return "-- 无法获取表 " + fullName + " 的列信息\n";
            }

            List<DbConnector.IndexDetail> indexes = DbConnector.fetchIndexes(ds, tableName, schema);

            // ── 表头注释 ──
            ddl.append("-- ============================================\n");
            ddl.append("-- 表结构: ").append(fullName).append("\n");
            ddl.append("-- 数据库: ").append(dbType.toUpperCase()).append(" | ")
                    .append(ds.getHost()).append(":").append(ds.getPort()).append("/").append(ds.getDbName()).append("\n");
            ddl.append("-- 导出时间: ").append(LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            ddl.append("-- ============================================\n\n");

            ddl.append("DROP TABLE IF EXISTS ").append(fullName).append(";\n\n");
            ddl.append("CREATE TABLE ").append(fullName).append(" (\n");

            // ── 列定义 ──
            List<String> pkColumns = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                DbConnector.ColumnDetail col = columns.get(i);
                String colDef = buildColumnDefForDDL(col, isPg);
                ddl.append("    ").append(colDef);
                if (i < columns.size() - 1 || !pkColumns.isEmpty()) {
                    ddl.append(",");
                }
                ddl.append("\n");
                if (col.isPrimaryKey) {
                    pkColumns.add(isPg ? "\"" + col.columnName + "\"" : "`" + col.columnName + "`");
                }
            }

            // ── PRIMARY KEY ──
            if (!pkColumns.isEmpty()) {
                ddl.append("    PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")\n");
            }

            // ── 表选项 ──
            if (isPg) {
                ddl.append(");\n");
            } else {
                ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n");
            }

            // ── PostgreSQL 列注释 ──
            if (isPg) {
                ddl.append("\n");
                for (DbConnector.ColumnDetail col : columns) {
                    if (col.comment != null && !col.comment.isBlank()) {
                        ddl.append("COMMENT ON COLUMN ").append(fullName).append(".\"")
                                .append(col.columnName).append("\" IS '")
                                .append(escapeSqlString(col.comment.trim())).append("';\n");
                    }
                }
            }

            // ── 索引（独立 CREATE INDEX 语句）──
            if (!indexes.isEmpty()) {
                ddl.append("\n");
                java.util.Map<String, List<DbConnector.IndexDetail>> grouped = groupIndexesByName(indexes);
                for (java.util.Map.Entry<String, List<DbConnector.IndexDetail>> entry : grouped.entrySet()) {
                    String idxName = entry.getKey();
                    List<DbConnector.IndexDetail> idxCols = entry.getValue();
                    boolean isUnique = !idxCols.get(0).nonUnique;

                    ddl.append("CREATE ");
                    if (isUnique) ddl.append("UNIQUE ");
                    ddl.append("INDEX ");
                    if (isPg) {
                        ddl.append("\"").append(idxName).append("\"");
                    } else {
                        ddl.append("`").append(idxName).append("`");
                    }
                    ddl.append(" ON ").append(fullName).append(" (");

                    StringBuilder colList = new StringBuilder();
                    for (DbConnector.IndexDetail ic : idxCols) {
                        if (!colList.isEmpty()) colList.append(", ");
                        if (isPg) {
                            colList.append("\"").append(ic.columnName).append("\"");
                        } else {
                            colList.append("`").append(ic.columnName).append("`");
                        }
                        if ("D".equals(ic.ascOrDesc)) {
                            colList.append(" DESC");
                        }
                    }
                    ddl.append(colList).append(");\n");
                }
            }

            ddl.append("\n");
            return ddl.toString();

        } catch (Exception e) {
            return "-- [ERROR] 生成 DDL 失败: " + e.getMessage() + "\n";
        }
    }

    /**
     * 为 CREATE TABLE DDL 构建列定义（不同于 ALTER 场景，需区分 PG SERIAL 类型）
     */
    private String buildColumnDefForDDL(DbConnector.ColumnDetail col, boolean isPg) {
        StringBuilder def = new StringBuilder();
        String quotedName = isPg ? "\"" + col.columnName + "\"" : "`" + col.columnName + "`";
        def.append(quotedName).append(" ");

        // PostgreSQL: 自增列映射为 SERIAL 类型
        String typeStr = col.dataType;
        if (isPg && col.isAutoIncrement) {
            String upper = typeStr.toUpperCase();
            if ("INTEGER".equals(upper) || "INT".equals(upper) || "INT4".equals(upper)) {
                typeStr = "SERIAL";
            } else if ("BIGINT".equals(upper) || "INT8".equals(upper)) {
                typeStr = "BIGSERIAL";
            } else if ("SMALLINT".equals(upper) || "INT2".equals(upper)) {
                typeStr = "SMALLSERIAL";
            }
        }
        def.append(typeStr);

        if (col.columnSize > 0 && needsSizeForDDL(typeStr, isPg, col.isAutoIncrement)) {
            def.append("(").append(col.columnSize).append(")");
        }

        if (!col.nullable) {
            def.append(" NOT NULL");
        }

        if (col.defaultValue != null && !col.defaultValue.isBlank()) {
            def.append(" DEFAULT ").append(col.defaultValue);
        }

        if (!isPg && col.isAutoIncrement) {
            def.append(" AUTO_INCREMENT");
        }

        if (!isPg && col.comment != null && !col.comment.isBlank()) {
            def.append(" COMMENT '").append(escapeSqlString(col.comment.trim())).append("'");
        }

        return def.toString();
    }

    /**
     * 判断 DDL 中的类型是否需要 SIZE（SERIAL/BIGSERIAL 不需要，与 needsSize 略有不同）
     */
    private boolean needsSizeForDDL(String dataType, boolean isPg, boolean isAutoIncrement) {
        if (isPg && isAutoIncrement) {
            String upper = dataType.toUpperCase();
            if ("SERIAL".equals(upper) || "BIGSERIAL".equals(upper) || "SMALLSERIAL".equals(upper)) {
                return false;
            }
        }
        return needsSize(dataType);
    }

    /**
     * 索引差异详情
     */
    public static class IndexDiff {
        public DiffType type;
        public String indexName;
        public List<DbConnector.IndexDetail> srcIndexColumns;
        public List<DbConnector.IndexDetail> tgtIndexColumns;
        public String alterSql;

        /**
         * 获取索引包含的列名（逗号分隔）
         */
        public String getColumnNames() {
            List<DbConnector.IndexDetail> cols = srcIndexColumns != null ? srcIndexColumns : tgtIndexColumns;
            if (cols == null || cols.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (DbConnector.IndexDetail col : cols) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(col.columnName);
            }
            return sb.toString();
        }

        /**
         * 是否为唯一索引
         */
        public boolean isUnique() {
            List<DbConnector.IndexDetail> cols = srcIndexColumns != null ? srcIndexColumns : tgtIndexColumns;
            return cols != null && !cols.isEmpty() && !cols.get(0).nonUnique;
        }

        @Override
        public String toString() {
            return "[" + type.getLabel() + "] " + indexName + " (" + getColumnNames() + ")"
                    + (isUnique() ? " UNIQUE" : "");
        }
    }
}
