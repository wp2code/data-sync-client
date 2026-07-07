package com.datasync.model;

import lombok.Data;

/**
 * @author liuweiping
 * @date 2026-07-07
 **/
@Data
public class GitLabAuthConfig {
    
    private Long id;
    
    private String name;
    
    private String url;
    
    private String username;
    
    private String password;
    
    private String remark;
    
}
