package com.omnikit.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Kafka Topic 实体类：每个 Kafka 连接下单独维护的 Topic 列表
 *
 * @author liuweiping
 * @date 2026-11-26
 **/
@Data
public class KafkaTopic {
    
    private Long id;
    
    /**
     * 所属 Kafka 连接配置 ID
     */
    private Long connectionId;
    
    /**
     * Topic 名称（同一连接下唯一）
     */
    private String topicName;
    
    /**
     * 备注
     */
    private String remark;
    
    private LocalDateTime createTime;
    
    public KafkaTopic() {
    }
    
    @Override
    public String toString() {
        return topicName != null ? topicName : "";
    }
}
