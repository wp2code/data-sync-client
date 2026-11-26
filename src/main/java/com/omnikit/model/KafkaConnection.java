package com.omnikit.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Kafka 连接配置实体类（消息队列数据推送模块）
 *
 * @author liuweiping
 * @date 2026-11-26
 **/
@Data
public class KafkaConnection {
    
    private Long id;
    
    /**
     * 连接名称（全局唯一）
     */
    private String name;
    
    /**
     * Kafka 集群地址（如 192.168.1.10:9092,192.168.1.11:9092）
     */
    private String bootstrapServers;
    
    /**
     * 生产者/管理客户端附加参数 JSON（如 {"security.protocol":"SASL_PLAINTEXT"}），可选
     */
    private String extraConfig;
    
    /**
     * 备注
     */
    private String remark;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    public KafkaConnection() {
    }
    
    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
