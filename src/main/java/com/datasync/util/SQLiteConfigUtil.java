package com.datasync.util;

import com.datasync.model.AiEnvConfig;
import com.datasync.model.DataSource;
import com.datasync.model.DbType;
import com.datasync.model.GitLabAuthConfig;
import com.datasync.model.Script;
import java.io.File;
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
    
    private static final String DB_FILE;
    
    private static final String DB_URL;
    
    static {
        // 获取程序所在目录，确保 data 目录创建在启动程序同一目录下
        String appDir;
        try {
            File codeFile = new File(SQLiteConfigUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (codeFile.isFile()) {
                // 打包为 JAR 运行，使用 JAR 所在目录
                appDir = codeFile.getParent();
            } else {
                // IDE 开发环境，使用当前工作目录
                appDir = System.getProperty("user.dir");
            }
        } catch (Exception e) {
            appDir = System.getProperty("user.dir");
        }
        
        DB_FILE = appDir + File.separator + "data" + File.separator + "datasource_config.db";
        DB_URL = "jdbc:sqlite:" + DB_FILE;
        
        // 确保 data 目录存在，不存在则自动创建
        File dataDir = new File(appDir, "data");
        if (!dataDir.exists()) {
            if (dataDir.mkdirs()) {
                logger.info("[SQLite] 自动创建数据目录: {}", dataDir.getAbsolutePath());
            } else {
                logger.warn("[SQLite] 数据目录创建失败: {}", dataDir.getAbsolutePath());
            }
        }
    }
    
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
    
    private static final String CREATE_AI_ENV_CONFIG_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS ai_env_config (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                env_name    VARCHAR(64)  NOT NULL UNIQUE,
                host        VARCHAR(256) NOT NULL,
                save_api    VARCHAR(256) DEFAULT '/pilot/training/knowledge/save',
                update_api  VARCHAR(256) DEFAULT '/pilot/training/knowledge/update',
                delete_api  VARCHAR(256) DEFAULT '/pilot/training/knowledge/delete',
                list_api    VARCHAR(256) DEFAULT '/pilot/training/knowledge/list',
                train_api   VARCHAR(256) DEFAULT '/pilot/training/knowledge/train',
                update_user_api VARCHAR(256) DEFAULT '/pilot/training/knowledge/updateUser',
                headers     TEXT         DEFAULT NULL,
                selected    INTEGER      DEFAULT 0,
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
                stmt.execute(CREATE_AI_ENV_CONFIG_TABLE_SQL);
                ensureAiEnvConfigColumns(stmt);
                seedDefaultAiEnv(stmt);
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
    
    // ────────── AI 问题环境 CRUD ──────────
    
    /**
     * 加载全部 AI 问题保存环境配置（保证全局选中环境有效）
     */
    public List<AiEnvConfig> loadAiEnvConfigs() {
        List<AiEnvConfig> configs = new ArrayList<>();
        String sql = "SELECT * FROM ai_env_config ORDER BY id ASC";
        try (Connection conn = getConnection()) {
            ensureSelectedAiEnv(conn);
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    configs.add(mapRowToAiEnvConfig(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("[SQLite] 加载 AI 问题环境列表失败", e);
        }
        return configs;
    }

    /**
     * 切换全局选中的环境（其余环境取消选中）
     */
    public boolean updateSelectedAiEnv(Long id) {
        String sql = "UPDATE ai_env_config SET selected = CASE WHEN id = ? THEN 1 ELSE 0 END";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("[SQLite] 切换全局选中环境失败", e);
            return false;
        }
    }
    
    /**
     * 新增 AI 问题保存环境配置（env_name 唯一）
     */
    public boolean saveAiEnvConfig(AiEnvConfig config) {
        String sql = "INSERT INTO ai_env_config (env_name, host, save_api, update_api, delete_api, list_api, train_api, update_user_api, headers, remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, config.getEnvName());
            ps.setString(2, config.getHost());
            ps.setString(3, valueOrDefault(config.getSaveApi(), AiEnvConfig.DEFAULT_SAVE_API));
            ps.setString(4, valueOrDefault(config.getUpdateApi(), AiEnvConfig.DEFAULT_UPDATE_API));
            ps.setString(5, valueOrDefault(config.getDeleteApi(), AiEnvConfig.DEFAULT_DELETE_API));
            ps.setString(6, valueOrDefault(config.getListApi(), AiEnvConfig.DEFAULT_LIST_API));
            ps.setString(7, valueOrDefault(config.getTrainApi(), AiEnvConfig.DEFAULT_TRAIN_API));
            ps.setString(8, valueOrDefault(config.getUpdateUserApi(), AiEnvConfig.DEFAULT_UPDATE_USER_API));
            ps.setString(9, config.getHeaders());
            ps.setString(10, config.getRemark());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("[SQLite] 保存 AI 问题环境失败", e);
            return false;
        }
    }

    /**
     * 更新 AI 问题保存环境配置
     */
    public boolean updateAiEnvConfig(AiEnvConfig config) {
        String sql = "UPDATE ai_env_config SET env_name = ?, host = ?, save_api = ?, update_api = ?, delete_api = ?, list_api = ?, train_api = ?, update_user_api = ?, "
                + "headers = ?, remark = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, config.getEnvName());
            ps.setString(2, config.getHost());
            ps.setString(3, valueOrDefault(config.getSaveApi(), AiEnvConfig.DEFAULT_SAVE_API));
            ps.setString(4, valueOrDefault(config.getUpdateApi(), AiEnvConfig.DEFAULT_UPDATE_API));
            ps.setString(5, valueOrDefault(config.getDeleteApi(), AiEnvConfig.DEFAULT_DELETE_API));
            ps.setString(6, valueOrDefault(config.getListApi(), AiEnvConfig.DEFAULT_LIST_API));
            ps.setString(7, valueOrDefault(config.getTrainApi(), AiEnvConfig.DEFAULT_TRAIN_API));
            ps.setString(8, valueOrDefault(config.getUpdateUserApi(), AiEnvConfig.DEFAULT_UPDATE_USER_API));
            ps.setString(9, config.getHeaders());
            ps.setString(10, config.getRemark());
            ps.setLong(11, config.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("[SQLite] 更新 AI 问题环境失败", e);
            return false;
        }
    }
    
    /**
     * 删除 AI 问题保存环境配置
     */
    public boolean deleteAiEnvConfig(Long id) {
        String sql = "DELETE FROM ai_env_config WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("[SQLite] 删除 AI 问题环境失败", e);
            return false;
        }
    }
    
    // ────────── 私有方法 ──────────
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
    
    /**
     * 兼容旧库：ai_env_config 缺少接口路径 / 请求头列时自动补齐（SQLite ALTER TABLE ADD COLUMN）
     */
    private void ensureAiEnvConfigColumns(Statement stmt) throws SQLException {
        addColumnIfAbsent(stmt, "ai_env_config", "save_api", "VARCHAR(256) DEFAULT '" + AiEnvConfig.DEFAULT_SAVE_API + "'");
        addColumnIfAbsent(stmt, "ai_env_config", "update_api", "VARCHAR(256) DEFAULT '" + AiEnvConfig.DEFAULT_UPDATE_API + "'");
        addColumnIfAbsent(stmt, "ai_env_config", "delete_api", "VARCHAR(256) DEFAULT '" + AiEnvConfig.DEFAULT_DELETE_API + "'");
        addColumnIfAbsent(stmt, "ai_env_config", "list_api", "VARCHAR(256) DEFAULT '" + AiEnvConfig.DEFAULT_LIST_API + "'");
        addColumnIfAbsent(stmt, "ai_env_config", "train_api", "VARCHAR(256) DEFAULT '" + AiEnvConfig.DEFAULT_TRAIN_API + "'");
        addColumnIfAbsent(stmt, "ai_env_config", "update_user_api", "VARCHAR(256) DEFAULT '" + AiEnvConfig.DEFAULT_UPDATE_USER_API + "'");
        addColumnIfAbsent(stmt, "ai_env_config", "headers", "TEXT DEFAULT NULL");
        addColumnIfAbsent(stmt, "ai_env_config", "selected", "INTEGER DEFAULT 0");
    }
    
    /**
     * 列不存在时执行 ALTER TABLE ADD COLUMN
     */
    private void addColumnIfAbsent(Statement stmt, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        logger.info("[SQLite] 表 {} 自动补充列 {} {}", table, column, definition);
    }
    
    /**
     * 环境表为空时播种默认环境，保证问题录入始终有可选保存环境
     */
    private void seedDefaultAiEnv(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ai_env_config")) {
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO ai_env_config (env_name, host, remark, selected) "
                        + "VALUES ('默认环境', 'http://localhost', '系统自动创建', 1)");
            }
        }
    }

    /**
     * 保证全局选中环境有效：无选中环境时自动选中第一个（旧库迁移 / 选中环境被删除后兜底）
     */
    private void ensureSelectedAiEnv(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ai_env_config WHERE selected = 1")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("UPDATE ai_env_config SET selected = 1 "
                            + "WHERE id = (SELECT id FROM ai_env_config ORDER BY id ASC LIMIT 1)");
                }
            }
        }
    }
    
    /**
     * 空值兜底：为空时返回默认值
     */
    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
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
    
    /**
     * 将 ResultSet 当前行映射为 AiEnvConfig 实体
     */
    private AiEnvConfig mapRowToAiEnvConfig(ResultSet rs) throws SQLException {
        AiEnvConfig config = new AiEnvConfig();
        config.setId(rs.getLong("id"));
        config.setEnvName(rs.getString("env_name"));
        config.setHost(rs.getString("host"));
        config.setSaveApi(valueOrDefault(rs.getString("save_api"), AiEnvConfig.DEFAULT_SAVE_API));
        config.setUpdateApi(valueOrDefault(rs.getString("update_api"), AiEnvConfig.DEFAULT_UPDATE_API));
        config.setDeleteApi(valueOrDefault(rs.getString("delete_api"), AiEnvConfig.DEFAULT_DELETE_API));
        config.setListApi(valueOrDefault(rs.getString("list_api"), AiEnvConfig.DEFAULT_LIST_API));
        config.setTrainApi(valueOrDefault(rs.getString("train_api"), AiEnvConfig.DEFAULT_TRAIN_API));
        config.setUpdateUserApi(valueOrDefault(rs.getString("update_user_api"), AiEnvConfig.DEFAULT_UPDATE_USER_API));
        config.setHeaders(rs.getString("headers"));
        config.setSelected(rs.getInt("selected") == 1);
        config.setRemark(rs.getString("remark"));
        
        Timestamp ct = rs.getTimestamp("create_time");
        if (ct != null) {
            config.setCreateTime(ct.toLocalDateTime());
        }
        
        Timestamp ut = rs.getTimestamp("update_time");
        if (ut != null) {
            config.setUpdateTime(ut.toLocalDateTime());
        }
        
        return config;
    }
}
