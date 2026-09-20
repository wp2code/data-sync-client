/*
 * Copyright 2025 深圳曼顿科技有限公司 All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by 软件研究中心（深圳曼顿科技有限公司）
 */
package com.datasync.core;

import com.datasync.model.AiAnswer;
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
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 问题外部接口客户端。
 * <p>
 * 按环境配置（host + 接口路径）调用远端服务，完成问题的批量保存、更新、删除（单个 / 批量）、列表查询（训练状态服务端筛选）、触发训练与批量更新（用户信息 / 训练参数）、问题回复详情查询，以及问题回复的列表查询、更新与删除（回复审计）。<br> 接口约定：POST + JSON，响应格式
 * {code:"0", message, data}，code 为 "0" 表示成功。
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
     * 批量删除问题
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String deleteQuestions(AiEnvConfig env, List<Long> ids) throws AiApiException {
        String url = buildUrl(env, env.getDeleteApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(ids));
        return postForMessage(env, url, body);
    }
    
    /**
     * 删除单个问题（批量删除的便捷封装）
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String deleteQuestion(AiEnvConfig env, Long id) throws AiApiException {
        return deleteQuestions(env, Collections.singletonList(id));
    }
    
    /**
     * 查询问题列表（训练状态筛选随请求提交由服务端过滤，其余关键字过滤与分页在本地完成）
     *
     * @param trainingStatus 训练状态筛选（null 表示不筛选，返回全部；-1-待训练；0-成功；1-失败；2-成功(同步回复)；3-训练中；4-超时）
     */
    public List<AiQuestion> listQuestions(AiEnvConfig env, Integer trainingStatus) throws AiApiException {
        String url = buildUrl(env, env.getListApi());
        ObjectNode body = objectMapper.createObjectNode();
        if (trainingStatus != null) {
            body.put("trainingStatus", trainingStatus);
        }
        String responseText = executePost(env, url, body);
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
     * 批量更新选中问题的用户ID / 用户Session / 训练参数（留空的字段不提交，接口侧保持原值不变）
     *
     * @param userId        用户ID（null / 空白表示不更新该字段）
     * @param userSession   用户session（null / 空白表示不更新该字段）
     * @param trainingParam 训练参数（null / 空白表示不更新该字段）
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String batchUpdateUser(AiEnvConfig env, List<Long> ids, String userId, String userSession, String trainingParam) throws AiApiException {
        String url = buildUrl(env, env.getUpdateUserApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(ids));
        if (userId != null && !userId.isBlank()) {
            body.put("userId", userId);
        }
        if (userSession != null && !userSession.isBlank()) {
            body.put("userSession", userSession);
        }
        if (trainingParam != null && !trainingParam.isBlank()) {
            body.put("trainingParam", trainingParam);
        }
        return postForMessage(env, url, body);
    }
    
    /**
     * 按问题回复 ID 查询回复详情（问题列表点击回复ID超链接触发）
     * <p>
     * answerId 以 URL 查询参数提交，响应 data 为回复详情对象；返回原始键值对（字段名 → 文本值）供界面展示。
     *
     * @param answerId 问题回复 ID
     */
    public LinkedHashMap<String, String> getAnswerInfo(AiEnvConfig env, Long answerId) throws AiApiException {
        String url = buildUrl(env, env.getAnswerInfoApi());
        url += "?answerId=" + answerId;
        String responseText = executePost(env, url, null);
        JsonNode root = parseResponse(url, responseText);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        JsonNode data = root.get("data");
        if (data != null && data.isObject()) {
            data.fields().forEachRemaining(entry -> fields.put(entry.getKey(),
                    entry.getValue() instanceof com.fasterxml.jackson.databind.node.NullNode nullNode ? "" : entry.getValue().asText("")));
        }
        return fields;
    }
    
    /**
     * 查询问题回复列表（回复审计，本地再做分页）
     * <p>
     * 问题 / 回复内容模糊查询与是否允许修改、是否来源训练筛选随请求提交，参数为空时不提交对应字段（不参与筛选）。
     *
     * @param query           问题内容关键字（null / 空白表示不筛选）
     * @param answer          回复内容关键字（null / 空白表示不筛选）
     * @param allowModify     是否允许修改筛选（null 表示不筛选；0-不允许；1-允许）
     * @param sourceTraining  是否来源训练筛选（null 表示不筛选；true-是；false-否）
     */
    public List<AiAnswer> listAnswers(AiEnvConfig env, String query, String answer, Integer allowModify, Boolean sourceTraining) throws AiApiException {
        String url = buildUrl(env, env.getAnswerListApi());
        ObjectNode body = objectMapper.createObjectNode();
        if (query != null && !query.isBlank()) {
            body.put("query", query);
        }
        if (answer != null && !answer.isBlank()) {
            body.put("answer", answer);
        }
        if (allowModify != null) {
            body.put("allowModify", allowModify);
        }
        if (sourceTraining != null) {
            body.put("sourceTraining", sourceTraining);
        }
        String responseText = executePost(env, url, body);
        JsonNode root = parseResponse(url, responseText);
        JsonNode data = root.get("data");
        List<AiAnswer> answers = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                try {
                    answers.add(objectMapper.treeToValue(item, AiAnswer.class));
                } catch (Exception e) {
                    logger.error("[AiApi] 解析问题回复数据失败: {}", item);
                    throw new AiApiException("解析问题回复数据失败: " + e.getMessage());
                }
            }
        }
        return answers;
    }
    
    /**
     * 更新单条问题回复（问题、回复内容与是否允许修改状态）
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String updateAnswer(AiEnvConfig env, Long id, String query, String answer, Integer allowModify) throws AiApiException {
        String url = buildUrl(env, env.getAnswerUpdateApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(Collections.singletonList(id)));
        if (query != null) {
            body.put("query", query);
        }
        if (answer != null) {
            body.put("answer", answer);
        }
        if (allowModify != null) {
            body.put("allowModify", allowModify);
        }
        return postForMessage(env, url, body);
    }
    
    /**
     * 批量更新勾选问题回复的是否允许修改状态（批量更新仅支持该字段）
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String batchUpdateAnswerModify(AiEnvConfig env, List<Long> ids, int allowModify) throws AiApiException {
        String url = buildUrl(env, env.getAnswerUpdateApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(ids));
        body.put("allowModify", allowModify);
        return postForMessage(env, url, body);
    }
    
    /**
     * 批量删除问题回复
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String deleteAnswers(AiEnvConfig env, List<Long> ids) throws AiApiException {
        String url = buildUrl(env, env.getAnswerDeleteApi());
        ObjectNode body = objectMapper.createObjectNode();
        body.set("ids", objectMapper.valueToTree(ids));
        return postForMessage(env, url, body);
    }
    
    /**
     * 删除单条问题回复（批量删除的便捷封装）
     *
     * @return 成功时返回接口 message，失败时抛出异常
     */
    public String deleteAnswer(AiEnvConfig env, Long id) throws AiApiException {
        return deleteAnswers(env, Collections.singletonList(id));
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
        String json = "{}";
        if (body != null) {
            try {
                json = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                throw new AiApiException("序列化请求参数失败: " + e.getMessage());
            }
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
