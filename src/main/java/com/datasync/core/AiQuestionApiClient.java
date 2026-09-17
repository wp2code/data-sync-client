/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.core;

import com.datasync.model.AiEnvConfig;
import com.datasync.model.AiQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 问题外部接口客户端。
 * <p>
 * 按环境配置（host + 接口路径）调用远端服务，完成问题的批量保存、更新、删除、全量列表查询、触发训练与批量更新用户信息。<br> 接口约定：POST + JSON，响应格式 {code:"0", message, data}，code 为 "0" 表示成功。
 *
 * @author liuweiping
 * @date 2026-09-16
 **/
public final class AiQuestionApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AiQuestionApiClient.class);
    
    private static final AiQuestionApiClient INSTANCE = new AiQuestionApiClient();
    
    /**
     * 响应 code 成功标识
     */
    private static final String SUCCESS_CODE = "0";
    
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private AiQuestionApiClient() {
    }
    
    public static AiQuestionApiClient getInstance() {
        return INSTANCE;
    }
    
    /**
     * 批量保存问题
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String saveQuestions(AiEnvConfig env, List<AiQuestion> questions) throws AiApiException {
        String url = buildUrl(env, env.getSaveApi());
        return postForMessage(env, url, questions);
    }
    
    /**
     * 更新问题
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String updateQuestion(AiEnvConfig env, AiQuestion question) throws AiApiException {
        String url = buildUrl(env, env.getUpdateApi());
        return postForMessage(env, url, question);
    }
    
    /**
     * 删除问题
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String deleteQuestion(AiEnvConfig env, Long id) throws AiApiException {
        String url = buildUrl(env, env.getDeleteApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(Collections.singleton(id)));
        return postForMessage(env, url, body);
    }
    
    /**
     * 查询全部问题列表（本地再做关键字过滤与分页）
     */
    public List<AiQuestion> listQuestions(AiEnvConfig env) throws AiApiException {
        String url = buildUrl(env, env.getListApi());
        String responseText = executePost(env, url, objectMapper.createObjectNode());
        JsonNode root = parseResponse(url, responseText);
        JsonNode data = root.get("data");
        List<AiQuestion> questions = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                try {
                    questions.add(objectMapper.treeToValue(item, AiQuestion.class));
                } catch (Exception e) {
                    logger.error("[AiApi] 解析问题数据失败: {}", item);
                    throw new AiApiException("解析问题数据失败: " + e.getMessage());
                }
            }
        }
        return questions;
    }
    
    /**
     * 触发训练选中的问题（按问题 ID 列表批量提交，agentType 指定训练的智能体类型）
     *
     * @param agentType 智能体类型（safety / system / ops / auto）
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String triggerTraining(AiEnvConfig env, List<Long> ids, String agentType) throws AiApiException {
        String url = buildUrl(env, env.getTrainApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("questionIds", objectMapper.valueToTree(ids));
        body.put("agentType", agentType);
        return postForMessage(env, url, body);
    }
    
    /**
     * 批量更新选中问题的用户ID / 用户Session（留空的字段不提交，接口侧保持原值不变）
     *
     * @param userId      用户ID（null / 空白表示不更新该字段）
     * @param userSession 用户session（null / 空白表示不更新该字段）
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String batchUpdateUser(AiEnvConfig env, List<Long> ids, String userId, String userSession) throws AiApiException {
        String url = buildUrl(env, env.getUpdateUserApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(ids));
        if (userId != null && !userId.isBlank()) {
            body.put("userId", userId);
        }
        if (userSession != null && !userSession.isBlank()) {
            body.put("userSession", userSession);
        }
        return postForMessage(env, url, body);
    }
    
    // ────────── 私有方法 ──────────
    
    /**
     * POST 请求并校验响应 code，成功返回 message（附加环境通用请求头）
     */
    private String postForMessage(AiEnvConfig env, String url, Object body) throws AiApiException {
        String responseText = executePost(env, url, body);
        JsonNode root = parseResponse(url, responseText);
        return root.path("message").asText("success");
    }
    
    /**
     * 执行 POST 请求，返回响应体文本（附加环境通用请求头）
     */
    private String executePost(AiEnvConfig env, String url, Object body) throws AiApiException {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiApiException("序列化请求参数失败: " + e.getMessage());
        }
        logger.debug("[AiApi] POST {} body: {}", url, json);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            // 附加环境通用请求头（Content-Type 系统已维护，跳过避免被覆盖）
            if (env != null) {
                for (java.util.Map.Entry<String, String> entry : env.parseHeaders().entrySet()) {
                    if (!"Content-Type".equalsIgnoreCase(entry.getKey())) {
                        requestBuilder.header(entry.getKey(), entry.getValue());
                    }
                }
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new AiApiException("HTTP " + response.statusCode() + " " + abbreviate(response.body(), 120));
            }
            return response.body();
        } catch (AiApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiApiException("请求被中断: " + url);
        } catch (Exception e) {
            throw new AiApiException("连接失败: " + e.getMessage() + " (" + url + ")");
        }
    }
    
    /**
     * 解析响应并校验 code，成功时返回响应根节点
     */
    private JsonNode parseResponse(String url, String responseText) throws AiApiException {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseText == null || responseText.isBlank() ? "{}" : responseText);
        } catch (Exception e) {
            throw new AiApiException("解析响应失败: " + abbreviate(responseText, 120));
        }
        JsonNode code = root.get("code");
        String codeText = code == null ? null : code.asText();
        if (!SUCCESS_CODE.equals(codeText)) {
            String message = root.path("message").asText("未知错误");
            throw new AiApiException("接口返回失败 code=" + codeText + " message=" + message + " (" + url + ")");
        }
        return root;
    }
    
    /**
     * 组合环境 host 与接口路径为完整 URL（host 缺协议时补 http://）
     */
    private String buildUrl(AiEnvConfig env, String apiPath) throws AiApiException {
        String host = env != null && env.getHost() != null ? env.getHost().trim() : "";
        if (host.isEmpty()) {
            throw new AiApiException("环境 [" + (env != null ? env.getEnvName() : "") + "] 未配置 Host");
        }
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        String path = apiPath != null ? apiPath.trim() : "";
        if (path.isEmpty()) {
            throw new AiApiException("环境 [" + (env != null ? env.getEnvName() : "") + "] 未配置接口路径");
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return host + path;
    }
    
    /**
     * 长文本截断（用于错误信息展示）
     */
    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String flattened = text.replace("\r", "").replace("\n", " ").trim();
        return flattened.length() <= maxLength ? flattened : flattened.substring(0, maxLength) + "…";
    }
    
    /**
     * 接口调用异常（message 可直接展示给用户）
     */
    public static class AiApiException extends Exception {
        
        public AiApiException(String message) {
            super(message);
        }
    }
}
