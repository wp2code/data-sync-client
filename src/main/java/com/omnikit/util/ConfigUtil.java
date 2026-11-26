package com.omnikit.util;

import com.omnikit.model.AiEnvConfig;
import com.omnikit.model.DataSource;
import com.omnikit.model.GitLabAuthConfig;
import com.omnikit.model.KafkaConnection;
import com.omnikit.model.KafkaTopic;
import com.omnikit.model.ProgramConfig;
import com.omnikit.model.Script;
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
     * 仅更新脚本所属分组（拖拽分组使用，不刷新更新时间）
     */
    public static boolean updateScriptGroupName(Long id, String groupName) {
        return SQLiteConfigUtil.getInstance().updateScriptGroupName(id, groupName);
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
     * 程序名称是否已被占用（全局唯一，不区分大小写）：新增时 excludeId 传 null， 修改时传自身 id 以排除自身；空白名称不视为占用
     */
    public static boolean isProgramNameTaken(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        ProgramConfig existing = SQLiteConfigUtil.getInstance().loadProgramConfigByNameIgnoreCase(name.trim());
        return existing != null && (excludeId == null || !excludeId.equals(existing.getId()));
    }
    
    /**
     * 更新已有的程序容器配置
     */
    public static boolean updateProgramConfig(ProgramConfig config) {
        return SQLiteConfigUtil.getInstance().updateProgramConfig(config);
    }
    
    /**
     * 仅更新程序所属分组（拖拽分组使用，不刷新更新时间）
     */
    public static boolean updateProgramGroupName(Long id, String groupName) {
        return SQLiteConfigUtil.getInstance().updateProgramGroupName(id, groupName);
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
    
    // ────────── Kafka 连接 / Topic 配置（消息队列数据推送） ──────────
    
    /**
     * 读取字符串配置（app_config 键值表），不存在或读取失败时返回默认值
     */
    public static String getAppConfig(String key, String defaultValue) {
        return SQLiteConfigUtil.getInstance().getAppConfig(key, defaultValue);
    }
    
    /**
     * 写入字符串配置（app_config 键值表，不存在则插入，存在则更新）
     */
    public static boolean setAppConfig(String key, String value) {
        return SQLiteConfigUtil.getInstance().setAppConfig(key, value);
    }
    
    /**
     * 加载全部 Kafka 连接配置
     */
    public static List<KafkaConnection> loadKafkaConnections() {
        return SQLiteConfigUtil.getInstance().loadKafkaConnections();
    }
    
    /**
     * 新增 Kafka 连接配置，返回生成的自增 ID（失败返回 -1）
     */
    public static long saveKafkaConnection(KafkaConnection config) {
        return SQLiteConfigUtil.getInstance().saveKafkaConnection(config);
    }
    
    /**
     * 更新 Kafka 连接配置
     */
    public static boolean updateKafkaConnection(KafkaConnection config) {
        return SQLiteConfigUtil.getInstance().updateKafkaConnection(config);
    }
    
    /**
     * 删除 Kafka 连接配置（同时删除该连接下的全部 Topic）
     */
    public static boolean deleteKafkaConnection(Long id) {
        return SQLiteConfigUtil.getInstance().deleteKafkaConnection(id);
    }
    
    /**
     * 加载指定连接下的全部 Topic
     */
    public static List<KafkaTopic> loadKafkaTopics(Long connectionId) {
        return SQLiteConfigUtil.getInstance().loadKafkaTopics(connectionId);
    }
    
    /**
     * 新增 Kafka Topic（同连接下唯一，重复插入静默忽略）
     */
    public static boolean saveKafkaTopic(Long connectionId, String topicName, String remark) {
        return SQLiteConfigUtil.getInstance().saveKafkaTopic(connectionId, topicName, remark);
    }
    
    /**
     * 删除 Kafka Topic
     */
    public static boolean deleteKafkaTopic(Long id) {
        return SQLiteConfigUtil.getInstance().deleteKafkaTopic(id);
    }
    
    /**
     * 按 Topic 名称查找记录 ID（同连接下唯一，找不到返回 null）
     */
    public static Long findKafkaTopicId(Long connectionId, String topicName) {
        return SQLiteConfigUtil.getInstance().findKafkaTopicId(connectionId, topicName);
    }
}
