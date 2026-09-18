package com.datasync.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * AI 问题回复实体类（回复审计页数据）
 *
 * @author liuweiping
 * @date 2026-09-18
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnswer {
    
    /**
     * 是否允许修改：允许
     */
    public static final int MODIFY_ALLOWED = 1;
    
    /**
     * 是否允许修改：不允许
     */
    public static final int MODIFY_FORBIDDEN = 0;
    
    private Long id;
    
    /**
     * 回复所属环境名称（仅客户端本地展示用，不传输给远端接口）
     */
    @JsonIgnore
    private String envName;
    
    /**
     * 问题内容
     */
    private String query;
    
    /**
     * 回复内容
     */
    private String answer;
    
    /**
     * 用户ID（接口返回数据，客户端仅展示）
     */
    private String userId;
    
    /**
     * 是否允许修改：0-不允许；1-允许
     */
    private Integer allowModify;
    
    /**
     * 创建时间（接口返回字符串，客户端仅展示）
     */
    private String createTime;
    
    /**
     * 更新时间（接口返回字符串，客户端仅展示）
     */
    private String updateTime;
    
    public AiAnswer() {
    }
    
    /**
     * 是否允许修改
     */
    public boolean isModifyAllowed() {
        return allowModify != null && allowModify == MODIFY_ALLOWED;
    }
}
