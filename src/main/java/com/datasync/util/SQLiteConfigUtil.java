package com.datasync.util;

import com.datasync.model.DataSource;
import com.datasync.model.DbType;
import com.datasync.model.Script;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 本地持久化工具类（单例模式）
 * 负责数据库与数据表的自动初始化，以及数据源配置的增删改查
 */
public class SQLiteConfigUtil {

    private static final String DB_FILE = "datasource_config.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    // ────────── 建表 DDL ──────────
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS data_source_config (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            source_name VARCHAR(64)  NOT NULL UNIQUE,
            db_type     VARCHAR(16)  NOT NULL,
            host        VARCHAR(128) NOT NULL,
            port        VARCHAR(16)  NOT NULL,
            db_name     VARCHAR(64)  NOT NULL,
            schema_name VARCHAR(64)  DEFAULT 'public',
            username    VARCHAR(64)  NOT NULL,
            password    VARCHAR(128) NOT NULL,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """;

    private static final String CREATE_SCRIPT_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS script_config (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            script_name VARCHAR(128) NOT NULL UNIQUE,
            db_type     VARCHAR(16)  NOT NULL DEFAULT 'mysql',
            content     TEXT         NOT NULL,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        """;

    // ────────── 单例 ──────────
    private static final SQLiteConfigUtil INSTANCE = new SQLiteConfigUtil();

    private SQLiteConfigUtil() {}

    public static SQLiteConfigUtil getInstance() {
        return INSTANCE;
    }

    // ────────── 初始化 ──────────

    /**
     * 检测并创建本地 SQLite 数据库与数据表（工具启动时调用一次）
     */
    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_TABLE_SQL);
                stmt.execute(CREATE_SCRIPT_TABLE_SQL);
                // 兼容旧库：为已存在的表补充 schema_name 列（SQLite 用 try/catch 忽略列已存在错误）
                try {
                    stmt.execute("ALTER TABLE data_source_config ADD COLUMN schema_name VARCHAR(64) DEFAULT 'public'");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                // 兼容旧库：为已存在的 script_config 表补充 db_type 列
                try {
                    stmt.execute("ALTER TABLE script_config ADD COLUMN db_type VARCHAR(16) DEFAULT 'mysql'");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
            }
        } catch (Exception e) {
            System.err.println("[SQLite] 初始化失败: " + e.getMessage());
        }
    }

    // ────────── CRUD ──────────

    /**
     * 保存数据源配置
     */
    public boolean saveDataSource(DataSource ds) {
        String sql = "INSERT INTO data_source_config (source_name, db_type, host, port, db_name, schema_name, username, password) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ds.getSourceName());
            ps.setString(2, ds.getDbType());
            ps.setString(3, ds.getHost());
            ps.setString(4, ds.getPort());
            ps.setString(5, ds.getDbName());
            ps.setString(6, ds.getSchema());
            ps.setString(7, ds.getUsername());
            ps.setString(8, ds.getPassword());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SQLite] 保存失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 加载所有已保存的数据源名称
     */
    public List<String> loadAllSourceNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT source_name FROM data_source_config ORDER BY update_time DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("source_name");
                if (name != null) {
                    names.add(name);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SQLite] 查询名称列表失败: " + e.getMessage());
        }
        return names;
    }

    /**
     * 根据名称加载完整数据源配置
     */
    public DataSource loadDataSourceByName(String sourceName) {
        String sql = "SELECT * FROM data_source_config WHERE source_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToDataSource(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SQLite] 加载配置失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 更新数据源配置（以 source_name 为条件）
     */
    public boolean updateDataSource(DataSource ds) {
        String sql = "UPDATE data_source_config SET db_type = ?, host = ?, port = ?, db_name = ?, schema_name = ?, "
                   + "username = ?, password = ?, source_name = ?, update_time = CURRENT_TIMESTAMP "
                   + "WHERE source_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ds.getDbType());
            ps.setString(2, ds.getHost());
            ps.setString(3, ds.getPort());
            ps.setString(4, ds.getDbName());
            ps.setString(5, ds.getSchema());
            ps.setString(6, ds.getUsername());
            ps.setString(7, ds.getPassword());
            ps.setString(8, ds.getSourceName());
            ps.setString(9, ds.getSourceName());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SQLite] 更新失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除指定名称的数据源配置
     */
    public boolean deleteDataSource(String sourceName) {
        String sql = "DELETE FROM data_source_config WHERE source_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SQLite] 删除失败: " + e.getMessage());
            return false;
        }
    }

    // ────────── 脚本配置 CRUD ──────────

    /**
     * 保存脚本配置
     */
    public boolean saveScript(Script script) {
        String sql = "INSERT INTO script_config (script_name, db_type, content) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, script.getScriptName());
            ps.setString(2, script.getDbType());
            ps.setString(3, script.getContent());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SQLite] 保存脚本失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 加载所有脚本配置
     */
    public List<Script> loadAllScripts() {
        List<Script> scripts = new ArrayList<>();
        String sql = "SELECT * FROM script_config ORDER BY update_time DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                scripts.add(mapRowToScript(rs));
            }
        } catch (SQLException e) {
            System.err.println("[SQLite] 加载脚本列表失败: " + e.getMessage());
        }
        return scripts;
    }

    /**
     * 根据名称加载脚本配置
     */
    public Script loadScriptByName(String scriptName) {
        String sql = "SELECT * FROM script_config WHERE script_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scriptName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToScript(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SQLite] 加载脚本失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 更新脚本配置（以 id 为条件）
     */
    public boolean updateScript(Script script) {
        String sql = "UPDATE script_config SET script_name = ?, db_type = ?, content = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, script.getScriptName());
            ps.setString(2, script.getDbType());
            ps.setString(3, script.getContent());
            ps.setLong(4, script.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SQLite] 更新脚本失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除指定 id 的脚本配置
     */
    public boolean deleteScript(Long id) {
        String sql = "DELETE FROM script_config WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SQLite] 删除脚本失败: " + e.getMessage());
            return false;
        }
    }

    // ────────── 私有方法 ──────────

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * 将 ResultSet 当前行映射为 DataSource 实体
     */
    private DataSource mapRowToDataSource(ResultSet rs) throws SQLException {
        DataSource ds = new DataSource();
        ds.setId(rs.getLong("id"));
        ds.setSourceName(rs.getString("source_name"));
        ds.setDbTypeEnum(DbType.fromString(rs.getString("db_type")));
        ds.setHost(rs.getString("host"));
        ds.setPort(rs.getString("port"));
        ds.setDbName(rs.getString("db_name"));
        ds.setSchema(rs.getString("schema_name"));
        ds.setUsername(rs.getString("username"));
        ds.setPassword(rs.getString("password"));

        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) ds.setCreateTime(ct.toLocalDateTime());

        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) ds.setUpdateTime(ut.toLocalDateTime());

        return ds;
    }

    /**
     * 将 ResultSet 当前行映射为 Script 实体
     */
    private Script mapRowToScript(ResultSet rs) throws SQLException {
        Script script = new Script();
        script.setId(rs.getLong("id"));
        script.setScriptName(rs.getString("script_name"));
        script.setDbType(rs.getString("db_type"));
        script.setContent(rs.getString("content"));

        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) script.setCreateTime(ct.toLocalDateTime());

        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) script.setUpdateTime(ut.toLocalDateTime());

        return script;
    }
}
