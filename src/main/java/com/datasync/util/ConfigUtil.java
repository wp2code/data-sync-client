package com.datasync.util;

import com.datasync.model.AiEnvConfig;
import com.datasync.model.DataSource;
import com.datasync.model.GitLabAuthConfig;
import com.datasync.model.ProgramConfig;
import com.datasync.model.Script;
import java.util.List;

/**
 * 数据源配置门面工具类。 委托给 SQLiteConfigUtil 单例执行实际的持久化操作。
 */
public final class ConfigUtil {
    
    private ConfigUtil() {
    }
    
    /**
     * 保存新的数据源配置
     */
    public static boolean saveDataSource(DataSource dataSource) {
        return SQLiteConfigUtil.getInstance().saveDataSource(dataSource);
    }
    
    /**
     * 加载所有已保存的数据源名称
     */
    public static List<String> loadAllSourceNames() {
        return SQLiteConfigUtil.getInstance().loadAllSourceNames();
    }
    
    /**
     * 根据名称加载数据源配置
     */
    public static DataSource loadDataSourceByName(String sourceName) {
        return SQLiteConfigUtil.getInstance().loadDataSourceByName(sourceName);
    }
    
    /**
     * 更新已有的数据源配置
     */
    public static boolean updateDataSource(DataSource dataSource) {
        return SQLiteConfigUtil.getInstance().updateDataSource(dataSource);
    }
    
    /**
     * 删除数据源配置
     */
    public static boolean deleteDataSource(String sourceName) {
        return SQLiteConfigUtil.getInstance().deleteDataSource(sourceName);
    }
    
    /**
     * 保存新的脚本配置
     */
    public static boolean saveScript(Script script) {
        return SQLiteConfigUtil.getInstance().saveScript(script);
    }
    
    /**
     * 加载所有脚本配置
     */
    public static List<Script> loadAllScripts() {
        return SQLiteConfigUtil.getInstance().loadAllScripts();
    }
    
    /**
     * 根据名称加载脚本配置
     */
    public static Script loadScriptByName(String scriptName) {
        return SQLiteConfigUtil.getInstance().loadScriptByName(scriptName);
    }
    
    /**
     * 更新已有的脚本配置
     */
    public static boolean updateScript(Script script) {
        return SQLiteConfigUtil.getInstance().updateScript(script);
    }
    
    /**
     * 删除脚本配置
     */
    public static boolean deleteScript(Long id) {
        return SQLiteConfigUtil.getInstance().deleteScript(id);
    }
    
    /**
     * 加载所有 GitLab 配置
     */
    public static List<GitLabAuthConfig> loadAllGitLabAuthConfigs() {
        return SQLiteConfigUtil.getInstance().loadAllGitLabAuthConfigs();
    }
    
    /**
     * 根据 ID 加载 GitLab 配置
     */
    public static GitLabAuthConfig loadGitLabAuthConfigById(Long id) {
        return SQLiteConfigUtil.getInstance().loadGitLabAuthConfigById(id);
    }
    
    // ────────── AI 问题保存环境（问题数据通过外部接口存取，本地仅维护环境配置） ──────────
    
    /**
     * 加载全部问题保存环境配置
     */
    public static List<AiEnvConfig> loadAiEnvConfigs() {
        return SQLiteConfigUtil.getInstance().loadAiEnvConfigs();
    }
    
    /**
     * 新增问题保存环境配置
     */
    public static boolean saveAiEnvConfig(AiEnvConfig config) {
        return SQLiteConfigUtil.getInstance().saveAiEnvConfig(config);
    }
    
    /**
     * 更新问题保存环境配置（环境重命名时同步更新问题关联）
     */
    public static boolean updateAiEnvConfig(AiEnvConfig config) {
        return SQLiteConfigUtil.getInstance().updateAiEnvConfig(config);
    }
    
    /**
     * 删除问题保存环境配置
     */
    public static boolean deleteAiEnvConfig(Long id) {
        return SQLiteConfigUtil.getInstance().deleteAiEnvConfig(id);
    }
    
    /**
     * 切换全局选中的环境（其余环境取消选中）
     */
    public static boolean updateSelectedAiEnv(Long id) {
        return SQLiteConfigUtil.getInstance().updateSelectedAiEnv(id);
    }
    
    // ────────── 程序容器配置 ──────────
    
    /**
     * 保存新的程序容器配置
     */
    public static boolean saveProgramConfig(ProgramConfig config) {
        return SQLiteConfigUtil.getInstance().saveProgramConfig(config);
    }
    
    /**
     * 加载所有程序容器配置
     */
    public static List<ProgramConfig> loadProgramConfigs() {
        return SQLiteConfigUtil.getInstance().loadProgramConfigs();
    }
    
    /**
     * 根据名称加载程序容器配置（唯一性校验）
     */
    public static ProgramConfig loadProgramConfigByName(String name) {
        return SQLiteConfigUtil.getInstance().loadProgramConfigByName(name);
    }
    
    /**
     * 更新已有的程序容器配置
     */
    public static boolean updateProgramConfig(ProgramConfig config) {
        return SQLiteConfigUtil.getInstance().updateProgramConfig(config);
    }
    
    /**
     * 删除程序容器配置
     */
    public static boolean deleteProgramConfig(Long id) {
        return SQLiteConfigUtil.getInstance().deleteProgramConfig(id);
    }
    
    /**
     * 单独更新程序最近一次启动的进程号（启动成功后写入，进程退出后置 null）
     */
    public static boolean updateProgramPid(long id, Long pid) {
        return SQLiteConfigUtil.getInstance().updateProgramPid(id, pid);
    }
}
