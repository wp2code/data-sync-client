package com.datasync.util;

import com.datasync.core.DataSource;

import java.util.List;

/**
 * 数据源配置门面工具类。
 * 委托给 SQLiteConfigUtil 单例执行实际的持久化操作。
 */
public final class ConfigUtil {
    private ConfigUtil() {}

    /** 保存新的数据源配置 */
    public static boolean saveDataSource(DataSource dataSource) {
        return SQLiteConfigUtil.getInstance().saveDataSource(dataSource);
    }

    /** 加载所有已保存的数据源名称 */
    public static List<String> loadAllSourceNames() {
        return SQLiteConfigUtil.getInstance().loadAllSourceNames();
    }

    /** 根据名称加载数据源配置 */
    public static DataSource loadDataSourceByName(String sourceName) {
        return SQLiteConfigUtil.getInstance().loadDataSourceByName(sourceName);
    }

    /** 更新已有的数据源配置 */
    public static boolean updateDataSource(DataSource dataSource) {
        return SQLiteConfigUtil.getInstance().updateDataSource(dataSource);
    }

    /** 删除数据源配置 */
    public static boolean deleteDataSource(String sourceName) {
        return SQLiteConfigUtil.getInstance().deleteDataSource(sourceName);
    }
}
