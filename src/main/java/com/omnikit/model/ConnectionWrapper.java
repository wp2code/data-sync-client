package com.omnikit.model;

import java.sql.Connection;
import lombok.Getter;
import lombok.Setter;

/**
 * @author liuweiping
 * @date 2026-06-26
 **/
@Getter
@Setter
public class ConnectionWrapper {
    
    private Connection connection;
    
    private DataSource dataSource;
    
    public ConnectionWrapper(DataSource dataSource, Connection connection) {
        this.dataSource = dataSource;
        this.connection = connection;
    }
}
