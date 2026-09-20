package com.datasync.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 问题实体类
 *
 * @author liuweiping
 * @date 2026-09-16
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiQuestion {
    
    /**
     * 开启训练：开启
     */
    public static final int TRAINING_ENABLED = 1;
    
    /**
     * 开启训练：不开启
     */
    public static final int TRAINING_DISABLED = 2;
    
    /**
     * 训练状态：待训练（尚未触发过训练）
     */
    public static final int TRAINING_STATUS_PENDING = -1;
    
    /**
     * 训练状态：成功
     */
    public static final int TRAINING_STATUS_SUCCESS = 0;
    
    /**
     * 训练状态：失败
     */
    public static final int TRAINING_STATUS_FAILED = 1;
    
    /**
     * 训练状态：成功（同步回复）
     */
    public static final int TRAINING_STATUS_SUCCESS_ANSWER = 2;
    
    /**
     * 训练状态：训练中
     */
    public static final int TRAINING_STATUS_RUNNING = 3;
    
    /**
     * 训练状态：超时
     */
    public static final int TRAINING_STATUS_TIMEOUT = 4;
    
    private Long id;
    
    /**
     * 保存环境名称（仅客户端本地展示用，不传输给远端接口）
     */
    @JsonIgnore
    private String envName;
    
    /**
     * 问题内容
     */
    private String question;
    
    /**
     * 问题分类
     */
    private String questionClassify;
    
    /**
     * 用户ID（关联的用户标识，问题归属人；非必填，会随问题一起提交到远端接口）
     */
    private String userId;
    
    /**
     * 用户session（用户会话标识，可通过问题列表批量更新接口修改）
     */
    private String userSession;
    
    /**
     * 备注（接口返回数据，客户端仅展示，不提供录入与编辑入口）
     */
    private String remark;
    
    /**
     * 问题回复ID（接口返回数据，客户端仅展示；有值时列表中以超链接样式展示，点击可查询回复详情）
     */
    private Long answerId;
    
    /**
     * 训练参数
     */
    private String trainingParam;
    
    /**
     * 训练状态：-1-待训练；0-成功；1-失败；2-成功(同步回复)；3-训练中；4-超时（接口返回数据，客户端仅展示）
     */
    private Integer trainingStatus;
    
    /**
     * 开始训练时间（毫秒时间戳，接口返回数据，客户端展示时格式化为 yyyy-MM-dd HH:mm:ss）
     */
    private Long startTrainingTime;
    
    /**
     * 最近完成训练时间（毫秒时间戳，接口返回数据，客户端展示时格式化为 yyyy-MM-dd HH:mm:ss）
     */
    private Long lastTrainingTime;
    
    /**
     * 固定答案（默认空，问题命中时优先返回的固定答案）
     */
    private String answer;
    
    /**
     * 是否开启训练：1-开启 2-不开启，默认 1
     */
    private Integer enableTraining = TRAINING_ENABLED;
    
    /**
     * 优先级别，越大越优先，默认 0
     */
    private Integer priority = 0;
    
    @JsonIgnore
    private LocalDateTime createTime;
    
    @JsonIgnore
    private LocalDateTime updateTime;
    
    public AiQuestion() {
    }
    
    /**
     * 是否开启训练
     */
    public boolean isTrainingEnabled() {
        return enableTraining != null && enableTraining == TRAINING_ENABLED;
    }
}
