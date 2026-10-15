/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * AI 元数据配置（GET /pilot/knowledge/training/ai-meta/config?id=default 响应 data）。
 * <p>
 * 提供触发训练弹窗可下拉选择 / 手动输入的训练模型、意图分析模型与智能体对象候选值。
 *
 * @author liuweiping
 * @date 2026-10-15
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiMetaConfig {
    
    /**
     * 训练模型名称列表（coreLlmName）
     */
    private List<String> coreLlmNameList = new ArrayList<>();
    
    /**
     * 意图分析模型名称列表（intentionLlmName）
     */
    private List<String> intentionLlmNameList = new ArrayList<>();
    
    /**
     * 智能体对象列表（name-名称；desc-描述）
     */
    private List<Agent> agentList = new ArrayList<>();
    
    /**
     * 空配置（接口未返回或调用失败时的兜底值，下拉仍支持手动输入）
     */
    public static AiMetaConfig empty() {
        return new AiMetaConfig();
    }
    
    /**
     * 是否未加载到任何可选值（用于触发训练弹窗打开时按需重新拉取）
     */
    public boolean isEmpty() {
        return emptyList(coreLlmNameList) && emptyList(intentionLlmNameList) && (agentList == null || agentList.isEmpty());
    }
    
    /**
     * 智能体对象列表（空值兜底为空集合，触发训练弹窗的智能体类型下拉选项）
     */
    public List<Agent> agentOptions() {
        return agentList == null ? Collections.emptyList() : agentList;
    }
    
    /**
     * 训练模型名称列表（空值兜底为空集合）
     */
    public List<String> coreLlmNameOptions() {
        return coreLlmNameList == null ? Collections.emptyList() : coreLlmNameList;
    }
    
    /**
     * 意图分析模型名称列表（空值兜底为空集合）
     */
    public List<String> intentionLlmNameOptions() {
        return intentionLlmNameList == null ? Collections.emptyList() : intentionLlmNameList;
    }
    
    private static boolean emptyList(List<String> list) {
        return list == null || list.isEmpty();
    }
    
    /**
     * 智能体对象（名称 / 描述）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Agent {
        
        /**
         * 智能体名称（触发训练提交 agentName）
         */
        private String name;
        
        /**
         * 智能体描述
         */
        private String desc;
    }
}
