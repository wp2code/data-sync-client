package com.omnikit.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnikit.model.KafkaConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka 消息推送服务：连接测试、Topic 列表查询与 JSON 消息推送（无 UI 代码）。
 * 连接参数通过 {@link KafkaConnection#getExtraConfig()} 附加 JSON 配置（如 SASL 认证参数）。
 */
public final class KafkaPushService {
    
    /**
     * Topic 查询到的单条消息记录
     *
     * @param partition 分区号
     * @param offset 偏移量
     * @param timestamp 消息时间戳（epoch millis）
     * @param key 消息 Key（可能为 null）
     * @param value 消息内容
     * @param headers 消息头（按 UTF-8 解码为文本；无消息头时为空 Map）
     */
    public record KafkaMessage(int partition, long offset, long timestamp, String key, String value,
            Map<String, String> headers) {
    }
    
    /**
     * 查询拉取的整体超时（防止集群持续无数据时阻塞）
     */
    private static final long QUERY_TIMEOUT_MS = 15_000;
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaPushService.class);
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    /**
     * Topic 列表查询超时（连接失败时避免 UI 长时间等待）
     */
    private static final long ADMIN_TIMEOUT_MS = 10_000;
    
    /**
     * 单条消息发送确认超时
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);
    
    private KafkaPushService() {
    }
    
    /**
     * 将连接/推送异常翻译为附带排查提示的错误信息（连接类失败给出可操作的排查步骤）
     */
    public static String friendlyError(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String msg = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        boolean unreachable = cause instanceof org.apache.kafka.common.errors.TimeoutException
                || msg.contains("could not be established")
                || msg.contains("Timed out waiting");
        if (unreachable) {
            return msg + " —— 排查提示：1) 确认集群地址与端口（默认 9092）；2) 确认 Kafka 服务已启动；"
                    + "3) 确认本机到 broker 的网络连通（防火墙 / VPN / 安全组放行，可用 Test-NetConnection 验证）；"
                    + "4) 若集群启用 SASL/SSL 认证，请在连接「附加参数」JSON 中配置 security.protocol 等参数";
        }
        return msg;
    }
    
    /**
     * 构建管理客户端参数（连接测试 / Topic 列表查询）
     */
    public static Properties buildAdminProps(KafkaConnection conn) {
        Properties props = new Properties();
        props.put("bootstrap.servers", conn.getBootstrapServers());
        props.put("request.timeout.ms", String.valueOf(ADMIN_TIMEOUT_MS));
        props.put("default.api.timeout.ms", String.valueOf(ADMIN_TIMEOUT_MS + 5_000));
        props.putAll(parseExtraConfig(conn.getExtraConfig()));
        return props;
    }
    
    /**
     * 构建生产者参数：String 序列化、acks=all + 有限重试，附加连接的自定义参数
     */
    public static Properties buildProducerProps(KafkaConnection conn) {
        Properties props = new Properties();
        props.put("bootstrap.servers", conn.getBootstrapServers());
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("acks", "all");
        props.put("retries", "3");
        props.put("request.timeout.ms", "30000");
        props.put("delivery.timeout.ms", "60000");
        props.putAll(parseExtraConfig(conn.getExtraConfig()));
        return props;
    }
    
    /**
     * 解析连接附加参数 JSON 为 Properties（未配置或解析失败时返回空配置）
     */
    public static Map<String, String> parseExtraConfig(String extraConfigJson) {
        Map<String, String> result = new LinkedHashMap<>();
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return result;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(extraConfigJson);
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText("")));
            }
        } catch (Exception e) {
            logger.warn("[KAFKA] 附加参数 JSON 解析失败，按无配置处理: {}", e.getMessage());
        }
        return result;
    }
    
    /**
     * 测试连接：查询集群 Topic 列表（空列表也视为连接成功）
     */
    public static List<String> listTopics(KafkaConnection conn) throws Exception {
        try (AdminClient admin = AdminClient.create(buildAdminProps(conn))) {
            return new ArrayList<>(admin.listTopics(new ListTopicsOptions().listInternal(false))
                    .names().get(ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }
    
    /**
     * 推送 JSON 消息到指定 Topic。
     *
     * <p>payload 支持两种形式：
     * <ul>
     *     <li>单个 JSON 对象：作为一条消息推送</li>
     *     <li>JSON 数组：每个元素作为一条消息逐条推送</li>
     * </ul>
     *
     * @param conn Kafka 连接配置
     * @param topic 目标 Topic
     * @param partition 目标分区（null 表示按 Key 哈希 / 轮询自动分配）
     * @param key 消息 Key（可选，为空时不设置）
     * @param headers 消息头（可选，null 或空 Map 表示不携带；值按 UTF-8 编码写入）
     * @param payload JSON 消息内容（对象或数组）
     * @param log 日志回调（进度与每条消息的分区/偏移量）
     * @return 实际成功推送的消息条数
     * @throws IllegalArgumentException payload 不是合法 JSON
     * @throws Exception 推送过程任意失败（发送超时、 broker 不可达等）
     */
    public static int sendJson(KafkaConnection conn, String topic, Integer partition, String key,
            Map<String, String> headers, String payload, Consumer<String> log) throws Exception {
        JsonNode root = JSON_MAPPER.readTree(payload);
        List<String> messages = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(node -> messages.add(node.toString()));
        } else {
            messages.add(root.toString());
        }
        log.accept("解析 JSON 完成，共 " + messages.size() + " 条消息，开始推送...");
        
        int successCount = 0;
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(buildProducerProps(conn))) {
            for (int i = 0; i < messages.size(); i++) {
                ProducerRecord<String, String> record;
                if (partition != null) {
                    record = new ProducerRecord<>(topic, partition, key, messages.get(i));
                } else if (key == null || key.isBlank()) {
                    record = new ProducerRecord<>(topic, messages.get(i));
                } else {
                    record = new ProducerRecord<>(topic, key, messages.get(i));
                }
                if (headers != null && !headers.isEmpty()) {
                    headers.forEach((name, value) -> record.headers().add(name,
                            value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8)));
                }
                RecordMetadata metadata = producer.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                successCount++;
                log.accept("消息 " + (i + 1) + "/" + messages.size() + " 推送成功 → partition=" + metadata.partition()
                        + ", offset=" + metadata.offset());
            }
            producer.flush();
        }
        return successCount;
    }
    
    /**
     * 查询指定 Topic 的分区数量（失败或 Topic 不存在时返回 -1）
     */
    public static int getTopicPartitionCount(KafkaConnection conn, String topic) {
        Properties props = new Properties();
        props.put("bootstrap.servers", conn.getBootstrapServers());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.putAll(parseExtraConfig(conn.getExtraConfig()));
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<org.apache.kafka.common.PartitionInfo> partitions =
                    consumer.partitionsFor(topic, Duration.ofSeconds(ADMIN_TIMEOUT_MS));
            return partitions == null ? -1 : partitions.size();
        } catch (Exception e) {
            logger.warn("[KAFKA] 查询 Topic 分区数失败: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * 查询 Topic 中的消息（临时消费组、不提交位移，不影响线上消费）。
     *
     * @param conn Kafka 连接配置
     * @param topic 目标 Topic
     * @param limit 最多返回条数
     * @param fromLatest true 从每个分区末尾向前取最新消息；false 从分区开头取最早消息
     * @return 查询到的消息列表（最多 limit 条）
     * @throws Exception 分区信息查询失败、集群不可达等
     */
    public static List<KafkaMessage> queryTopicMessages(KafkaConnection conn, String topic, int limit, boolean fromLatest)
            throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", conn.getBootstrapServers());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("group.id", "omnikit-query-" + UUID.randomUUID());
        props.put("enable.auto.commit", "false");
        props.put("session.timeout.ms", "15000");
        props.put("max.poll.records", String.valueOf(Math.max(limit, 1)));
        props.putAll(parseExtraConfig(conn.getExtraConfig()));
        
        List<KafkaMessage> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<org.apache.kafka.common.PartitionInfo> partitions = consumer.partitionsFor(topic, Duration.ofSeconds(ADMIN_TIMEOUT_MS));
            if (partitions == null || partitions.isEmpty()) {
                return messages;
            }
            List<TopicPartition> topicPartitions = partitions.stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
            consumer.assign(topicPartitions);
            if (fromLatest) {
                // 每个分区定位到 末尾位移 - limit（不超过分区起点），保证能取到最新消息
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions, Duration.ofSeconds(ADMIN_TIMEOUT_MS));
                for (TopicPartition tp : topicPartitions) {
                    long end = endOffsets.getOrDefault(tp, 0L);
                    long begin = consumer.beginningOffsets(List.of(tp), Duration.ofSeconds(ADMIN_TIMEOUT_MS)).getOrDefault(tp, 0L);
                    consumer.seek(tp, Math.max(begin, end - limit));
                }
            } else {
                consumer.seekToBeginning(topicPartitions);
            }
            
            long deadline = System.currentTimeMillis() + QUERY_TIMEOUT_MS;
            int consecutiveEmpty = 0;
            while (messages.size() < limit && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) {
                    // 连续两次空轮询视为该位置已无更多数据，提前结束
                    if (++consecutiveEmpty >= 2) {
                        break;
                    }
                    continue;
                }
                consecutiveEmpty = 0;
                for (ConsumerRecord<String, String> record : records) {
                    Map<String, String> recordHeaders = new LinkedHashMap<>();
                    record.headers().forEach(header -> recordHeaders.put(header.key(),
                            header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8)));
                    messages.add(new KafkaMessage(record.partition(), record.offset(), record.timestamp(),
                            record.key(), record.value(), recordHeaders));
                    if (messages.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return messages;
    }
}
