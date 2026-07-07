package com.datasync.util;

import com.datasync.model.DataSource;
import com.datasync.model.DbType;
import com.datasync.model.GitLabAuthConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQLite 本地持久化工具类（单例模式） 负责数据库与数据表的自动初始化，以及数据源配置的增删改查
 */
public class SQLiteConfigUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(SQLiteConfigUtil.class);
    
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
                id                INTEGER PRIMARY KEY AUTOINCREMENT,
                script_name       VARCHAR(128) NOT NULL UNIQUE,
                db_type           VARCHAR(16)  NOT NULL DEFAULT 'mysql',
                content           TEXT         DEFAULT NULL,
                remark            TEXT         DEFAULT NULL,
                git_lab_config_id INTEGER      DEFAULT NULL,
                project_or_id     VARCHAR(128) DEFAULT NULL,
                branch            VARCHAR(64)  DEFAULT NULL,
                file_path         VARCHAR(256) DEFAULT NULL,
                create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
    
    private static final String CREATE_GITLAB_CONFIG_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS gitlab_config (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        VARCHAR(64)  DEFAULT NULL,
                url         VARCHAR(256) NOT NULL,
                username    VARCHAR(64)  NOT NULL,
                password    VARCHAR(128) NOT NULL,
                remark      TEXT         DEFAULT NULL,
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
    
    // ────────── 单例 ──────────
    private static final SQLiteConfigUtil INSTANCE = new SQLiteConfigUtil();
    
    private SQLiteConfigUtil() {
    }
    
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
                stmt.execute(CREATE_GITLAB_CONFIG_TABLE_SQL);
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
                // 兼容旧库：为已存在的 script_config 表补充 remark 列
                try {
                    stmt.execute("ALTER TABLE script_config ADD COLUMN remark TEXT DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                // 兼容旧库：为已存在的 gitlab_config 表补充 name 列
                try {
                    stmt.execute("ALTER TABLE gitlab_config ADD COLUMN name VARCHAR(64) DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                // 兼容旧库：为已存在的 gitlab_config 表补充 remark 列
                try {
                    stmt.execute("ALTER TABLE gitlab_config ADD COLUMN remark TEXT DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                // 兼容旧库：为已存在的 script_config 表补充 GitLab 相关列
                try {
                    stmt.execute("ALTER TABLE script_config ADD COLUMN git_lab_config_id INTEGER DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                try {
                    stmt.execute("ALTER TABLE script_config ADD COLUMN project_or_id VARCHAR(128) DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                try {
                    stmt.execute("ALTER TABLE script_config ADD COLUMN branch VARCHAR(64) DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
                try {
                    stmt.execute("ALTER TABLE script_config ADD COLUMN file_path VARCHAR(256) DEFAULT NULL");
                } catch (SQLException ignored) {
                    // 列已存在，忽略
                }
            }
        } catch (Exception e) {
            logger.error("[SQLite] 初始化失败", e);
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
            logger.error("[SQLite] 保存数据源失败", e);
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
            logger.error("[SQLite] 查询数据源名称列表失败", e);
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
            logger.error("[SQLite] 加载数据源配置失败", e);
        }
        return null;
    }
    
    /**
     * 更新数据源配置（以 source_name 为条件）
     */
    public boolean updateDataSource(DataSource ds) {
        String sql = "UPDATE data_source_config SET db_type = ?, host = ?, port = ?, db_name = ?, schema_name = ?, "
                + "username = ?, password = ?, source_name = ?, update_time = CURRENT_TIMESTAMP " + "WHERE source_name = ?";
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
            logger.error("[SQLite] 更新数据源失败", e);
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
            logger.error("[SQLite] 删除数据源失败", e);
            return false;
        }
    }
    
    // ────────── 脚本配置 CRUD ──────────
    
    /**
     * 保存脚本配置
     */
    public boolean saveScript(Script script) {
        String sql = "INSERT INTO script_config (script_name, db_type, content, remark, git_lab_config_id, project_or_id, branch, file_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, script.getScriptName());
            ps.setString(2, script.getDbType() != null ? script.getDbType().getKey() : null);
            ps.setString(3, script.getContent());
            ps.setString(4, script.getRemark());
            if (script.getGitLabConfigId() != null) {
                ps.setLong(5, script.getGitLabConfigId());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            ps.setString(6, script.getProjectOrId());
            ps.setString(7, script.getBranch());
            ps.setString(8, script.getFilePath());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("[SQLite] 保存脚本失败", e);
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
            logger.error("[SQLite] 加载脚本列表失败", e);
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
            logger.error("[SQLite] 加载脚本失败", e);
        }
        return null;
    }
    
    /**
     * 更新脚本配置（以 id 为条件）
     */
    public boolean updateScript(Script script) {
        String sql = "UPDATE script_config SET script_name = ?, db_type = ?, content = ?, remark = ?, git_lab_config_id = ?, project_or_id = ?, branch = ?, file_path = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, script.getScriptName());
            ps.setString(2, script.getDbType() != null ? script.getDbType().getKey() : null);
            ps.setString(3, script.getContent());
            ps.setString(4, script.getRemark());
            if (script.getGitLabConfigId() != null) {
                ps.setLong(5, script.getGitLabConfigId());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            ps.setString(6, script.getProjectOrId());
            ps.setString(7, script.getBranch());
            ps.setString(8, script.getFilePath());
            ps.setLong(9, script.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("[SQLite] 更新脚本失败", e);
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
            logger.error("[SQLite] 删除脚本失败", e);
            return false;
        }
    }
    
    // ────────── GitLab 配置 CRUD ──────────
    
    /**
     * 保存 GitLab 登录配置（全局仅保留一条，存在则更新）
     */
    public boolean saveGitLabAuthConfig(GitLabAuthConfig config) {
        Long existingId = findGitLabConfigId();
        if (existingId == null) {
            String sql = "INSERT INTO gitlab_config (name, url, username, password, remark) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, config.getName());
                ps.setString(2, config.getUrl());
                ps.setString(3, config.getUsername());
                ps.setString(4, config.getPassword());
                ps.setString(5, config.getRemark());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("[SQLite] 保存 GitLab 配置失败", e);
                return false;
            }
        } else {
            String sql = "UPDATE gitlab_config SET name = ?, url = ?, username = ?, password = ?, remark = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?";
            try (Connection conn = getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, config.getName());
                ps.setString(2, config.getUrl());
                ps.setString(3, config.getUsername());
                ps.setString(4, config.getPassword());
                ps.setString(5, config.getRemark());
                ps.setLong(6, existingId);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("[SQLite] 更新 GitLab 配置失败", e);
                return false;
            }
        }
    }
    
    /**
     * 加载已保存的 GitLab 登录配置
     */
    public GitLabAuthConfig loadGitLabAuthConfig() {
        String sql = "SELECT * FROM gitlab_config LIMIT 1";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapRowToGitLabAuthConfig(rs);
            }
        } catch (SQLException e) {
            logger.error("[SQLite] 加载 GitLab 配置失败", e);
        }
        return null;
    }
    
    /**
     * 加载所有 GitLab 登录配置
     */
    public List<GitLabAuthConfig> loadAllGitLabAuthConfigs() {
        List<GitLabAuthConfig> configs = new ArrayList<>();
        String sql = "SELECT * FROM gitlab_config ORDER BY update_time DESC";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                configs.add(mapRowToGitLabAuthConfig(rs));
            }
        } catch (SQLException e) {
            logger.error("[SQLite] 加载 GitLab 配置列表失败", e);
        }
        return configs;
    }
    
    /**
     * 根据 ID 加载 GitLab 登录配置
     */
    public GitLabAuthConfig loadGitLabAuthConfigById(Long id) {
        String sql = "SELECT * FROM gitlab_config WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToGitLabAuthConfig(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("[SQLite] 根据 ID 加载 GitLab 配置失败", e);
        }
        return null;
    }
    
    private GitLabAuthConfig mapRowToGitLabAuthConfig(ResultSet rs) throws SQLException {
        GitLabAuthConfig config = new GitLabAuthConfig();
        config.setId(rs.getLong("id"));
        config.setName(rs.getString("name"));
        config.setUrl(rs.getString("url"));
        config.setUsername(rs.getString("username"));
        config.setPassword(rs.getString("password"));
        config.setRemark(rs.getString("remark"));
        return config;
    }
    
    private Long findGitLabConfigId() {
        String sql = "SELECT id FROM gitlab_config LIMIT 1";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            logger.error("[SQLite] 查询 GitLab 配置 ID 失败", e);
        }
        return null;
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
        if (ct != null) {
            ds.setCreateTime(ct.toLocalDateTime());
        }
        
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) {
            ds.setUpdateTime(ut.toLocalDateTime());
        }
        
        return ds;
    }
    
    /**
     * 将 ResultSet 当前行映射为 Script 实体
     */
    private Script mapRowToScript(ResultSet rs) throws SQLException {
        Script script = new Script();
        script.setId(rs.getLong("id"));
        script.setScriptName(rs.getString("script_name"));
        script.setDbType(DbType.fromString(rs.getString("db_type")));
        script.setContent(rs.getString("content"));
        script.setRemark(rs.getString("remark"));
        long gitLabConfigId = rs.getLong("git_lab_config_id");
        if (!rs.wasNull()) {
            script.setGitLabConfigId(gitLabConfigId);
        }
        script.setProjectOrId(rs.getString("project_or_id"));
        script.setBranch(rs.getString("branch"));
        script.setFilePath(rs.getString("file_path"));
        
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) {
            script.setCreateTime(ct.toLocalDateTime());
        }
        
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) {
            script.setUpdateTime(ut.toLocalDateTime());
        }
        
        return script;
    }
}
