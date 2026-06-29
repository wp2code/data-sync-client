/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.util;

import com.datasync.core.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author liuweiping
 * @date 2026-06-26
 **/
public class GlobalUtil {
    
    private static final Map<String, DataSource> currDataSourceMap = new HashMap<>(2);
    
    public static void addSrcDataSource(DataSource dataSource) {
        currDataSourceMap.put("SOURCE", dataSource);
    }
    
    public static void addTargetDataSource(DataSource dataSource) {
        currDataSourceMap.put("TARGET", dataSource);
    }
    
    public static String getSrcDataSource() {
        final DataSource source = currDataSourceMap.get("SOURCE");
        if (source == null) {
            return null;
        }
        return source.getSourceName();
    }
    
    public static void removeSrcDataSource() {
        currDataSourceMap.remove("SOURCE");
    }
    
    public static void removeTargetDataSource() {
        currDataSourceMap.remove("TARGET");
    }
    
    public static String getTargetDataSource() {
        final DataSource target = currDataSourceMap.get("TARGET");
        if (target == null) {
            return null;
        }
        return target.getSourceName();
    }
}
