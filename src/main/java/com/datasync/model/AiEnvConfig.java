package com.datasync.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import lombok.Data;

/**
 * AI 问题保存环境配置实体类（多环境 host 配置）
 *
 * @author liuweiping
 * @date 2026-09-16
 **/
@Data
public class AiEnvConfig {
    
    /**
     * 请求头 JSON 解析器（ObjectMapper 线程安全，静态复用）
     */
    private static final ObjectMapper HEADER_JSON_MAPPER = new ObjectMapper();
    
    /**
     * 批量保存接口默认路径
     */
    public static final String DEFAULT_SAVE_API = "/pilot/training/knowledge/batch-save";
    
    /**
     * 更新接口默认路径
     */
    public static final String DEFAULT_UPDATE_API = "/pilot/training/knowledge/update";
    
    /**
     * 删除接口默认路径
     */
    public static final String DEFAULT_DELETE_API = "/pilot/training/knowledge/delete";
    
    /**
     * 列表查询接口默认路径
     */
    public static final String DEFAULT_LIST_API = "/pilot/training/knowledge/all-list";
    
    /**
     * 触发训练接口默认路径
     */
    public static final String DEFAULT_TRAIN_API = "/pilot/training/knowledge/runBatchTraining";
    
    /**
     * 批量更新用户接口默认路径
     */
    public static final String DEFAULT_UPDATE_USER_API = "/pilot/training/knowledge/batch-update";
    
    /**
     * 问题回复详情查询接口默认路径（问题列表点击回复ID查询回复详情）
     */
    public static final String DEFAULT_ANSWER_INFO_API = "/pilot/training/knowledge/answer/info";
    
    /**
     * 问题回复列表接口默认路径（回复审计页查询问题回复）
     */
    public static final String DEFAULT_ANSWER_LIST_API = "/pilot/training/knowledge/answer/list";
    
    /**
     * 问题回复更新接口默认路径（回复审计页更新单条回复 / 批量更新是否允许修改状态）
     */
    public static final String DEFAULT_ANSWER_UPDATE_API = "/pilot/training/knowledge/answer/update";
    
    /**
     * 问题回复删除接口默认路径（回复审计页删除问题回复）
     */
    public static final String DEFAULT_ANSWER_DELETE_API = "/pilot/training/knowledge/answer/delete";
    
    private Long id;
    
    /**
     * 环境名称（唯一，问题表通过该字段关联环境）
     */
    private String envName;
    
    /**
     * 环境 Host 地址（如 http://192.168.1.10:8080）
     */
    private String host;
    
    /**
     * 批量保存接口路径
     */
    private String saveApi = DEFAULT_SAVE_API;
    
    /**
     * 更新接口路径
     */
    private String updateApi = DEFAULT_UPDATE_API;
    
    /**
     * 删除接口路径
     */
    private String deleteApi = DEFAULT_DELETE_API;
    
    /**
     * 列表查询接口路径
     */
    private String listApi = DEFAULT_LIST_API;
    
    /**
     * 触发训练接口路径（问题列表选中问题后批量触发训练）
     */
    private String trainApi = DEFAULT_TRAIN_API;
    
    /**
     * 批量更新用户接口路径（问题列表勾选问题后批量更新用户ID与用户Session）
     */
    private String updateUserApi = DEFAULT_UPDATE_USER_API;
    
    /**
     * 问题回复详情查询接口路径（问题列表点击回复ID查询回复详情）
     */
    private String answerInfoApi = DEFAULT_ANSWER_INFO_API;
    
    /**
     * 问题回复列表接口路径（回复审计页查询问题回复）
     */
    private String answerListApi = DEFAULT_ANSWER_LIST_API;
    
    /**
     * 问题回复更新接口路径（回复审计页更新单条回复 / 批量更新是否允许修改状态）
     */
    private String answerUpdateApi = DEFAULT_ANSWER_UPDATE_API;
    
    /**
     * 问题回复删除接口路径（回复审计页删除问题回复）
     */
    private String answerDeleteApi = DEFAULT_ANSWER_DELETE_API;
    
    /**
     * 环境通用请求头参数 JSON（如 {"Authorization":"Bearer xxx"}），调用该环境所有接口时附加
     */
    private String headers;
    
    /**
     * 是否为全局选中的环境（全局唯一；进入环境管理 / AiMock 界面时默认选中）
     */
    private Boolean selected;
    
    /**
     * 备注
     */
    private String remark;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    public AiEnvConfig() {
    }
    
    /**
     * 解析通用请求头 JSON 为有序键值对（未配置或解析失败时返回空 Map，不影响接口调用）
     */
    public LinkedHashMap<String, String> parseHeaders() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (headers == null || headers.isBlank()) {
            return result;
        }
        try {
            JsonNode node = HEADER_JSON_MAPPER.readTree(headers);
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText("")));
            }
        } catch (Exception ignored) {
            // 请求头配置非法时按无配置处理
        }
        return result;
    }
    
    @Override
    public String toString() {
        return envName != null ? envName : "";
    }
}
