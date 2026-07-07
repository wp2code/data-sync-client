package com.datasync.util;

import com.datasync.model.DataSource;
import com.datasync.model.GitLabAuthConfig;
import com.datasync.model.Script;

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

    /** 保存新的脚本配置 */
    public static boolean saveScript(Script script) {
        return SQLiteConfigUtil.getInstance().saveScript(script);
    }

    /** 加载所有脚本配置 */
    public static List<Script> loadAllScripts() {
        return SQLiteConfigUtil.getInstance().loadAllScripts();
    }

    /** 根据名称加载脚本配置 */
    public static Script loadScriptByName(String scriptName) {
        return SQLiteConfigUtil.getInstance().loadScriptByName(scriptName);
    }

    /** 更新已有的脚本配置 */
    public static boolean updateScript(Script script) {
        return SQLiteConfigUtil.getInstance().updateScript(script);
    }

    /** 删除脚本配置 */
    public static boolean deleteScript(Long id) {
        return SQLiteConfigUtil.getInstance().deleteScript(id);
    }

    /** 加载所有 GitLab 配置 */
    public static List<GitLabAuthConfig> loadAllGitLabAuthConfigs() {
        return SQLiteConfigUtil.getInstance().loadAllGitLabAuthConfigs();
    }

    /** 根据 ID 加载 GitLab 配置 */
    public static GitLabAuthConfig loadGitLabAuthConfigById(Long id) {
        return SQLiteConfigUtil.getInstance().loadGitLabAuthConfigById(id);
    }
}
