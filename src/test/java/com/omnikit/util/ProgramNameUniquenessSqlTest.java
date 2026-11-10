package com.omnikit.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 程序名称全局唯一所依赖的 SQL 语义验证：在临时库上验证 COLLATE NOCASE 查询与忽略大小写唯一索引，
 * 不触碰应用真实配置库（data/datasource_config.db）。
 */
@DisplayName("程序名称唯一性 SQL 语义")
class ProgramNameUniquenessSqlTest {
    
    @TempDir
    Path tempDir;
    
    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("name_unique.db").toAbsolutePath());
    }
    
    private static void createTable(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE program_config (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(64) NOT NULL UNIQUE)");
    }
    
    @Test
    @DisplayName("按名称查询忽略大小写：仅大小写不同也能命中已存在程序")
    void testLookupIgnoreCase() throws SQLException {
        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {
            createTable(stmt);
            stmt.executeUpdate("INSERT INTO program_config (name) VALUES ('Order-Service')");
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM program_config WHERE name = ? COLLATE NOCASE")) {
                ps.setString(1, "order-service");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "小写名称应命中已存在的大写名称，作为重复处理");
                }
            }
        }
    }
    
    @Test
    @DisplayName("忽略大小写唯一索引：库级拒绝仅大小写不同的重复名称")
    void testUniqueIndexIgnoreCase() throws SQLException {
        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {
            createTable(stmt);
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uk_program_config_name_nocase ON program_config (name COLLATE NOCASE)");
            stmt.executeUpdate("INSERT INTO program_config (name) VALUES ('Order-Service')");
            SQLException error = assertThrows(SQLException.class,
                    () -> stmt.executeUpdate("INSERT INTO program_config (name) VALUES ('ORDER-SERVICE')"),
                    "仅大小写不同的名称应被唯一索引拒绝");
            assertTrue(error.getMessage().toUpperCase().contains("UNIQUE"), "错误信息应提示唯一约束冲突");
        }
    }
}
