package com.datasync.model;

import java.sql.Connection;

/**
 * @author liuweiping
 * @date 2026-06-26
 **/
public class ConnectionWrapper {
    
    private Connection connection;
    
    private DataSource dataSource;
    
    public ConnectionWrapper(DataSource dataSource, Connection connection) {
        this.dataSource = dataSource;
        this.connection = connection;
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
    
    public DataSource getDataSource() {
        return dataSource;
    }
    
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
