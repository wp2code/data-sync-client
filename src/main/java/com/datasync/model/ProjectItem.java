package com.datasync.model;

import lombok.Data;

/**
 * @author liuweiping
 * @date 2026-07-07
 **/
@Data
public class ProjectItem {
    
    private final Long id;
    
    private final String pathWithNamespace;
    
    private final String name;
    
    private Long configId;
    
    public ProjectItem(Long id, String pathWithNamespace, String name) {
        this.id = id;
        this.pathWithNamespace = pathWithNamespace;
        this.name = name;
    }
    
    public String getProjectOrId() {
        if (pathWithNamespace != null && !pathWithNamespace.isEmpty()) {
            return pathWithNamespace;
        }
        return id != null ? String.valueOf(id) : "";
    }
    
    @Override
    public String toString() {
        if (name != null && !name.isEmpty() && pathWithNamespace != null && !pathWithNamespace.isEmpty()) {
            return name + " (" + pathWithNamespace + ")";
        }
        return name != null && !name.isEmpty() ? name : getProjectOrId();
    }
}
