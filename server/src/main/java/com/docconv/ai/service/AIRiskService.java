package com.docconv.ai.service;

import com.docconv.ai.config.DeepSeekConfig;
import com.docconv.ai.dto.AIRiskRequest;
import com.docconv.ai.dto.AIRiskResponse;
import com.docconv.ai.exception.AIErrorCode;
import com.docconv.ai.exception.AIProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AIRiskService {

    private final DeepSeekConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是一个 Markdown 文档质量分析助手。请处理以下内容并检测潜在风险。

            你的任务：
            1. 修复 Markdown 结构问题（标题层级、列表嵌套、表格格式等），但绝对不改变任何原文内容
            2. 规范化数学公式格式（LaTeX 语法标准化）
            3. 检测内容风险并按指定格式标注

            重要规则：
            - 标注必须紧跟在问题文本之后，立即标注，不要等到文末
            - 如果原文档有多个问题，每个问题后面都要立即标注
            - 不要在文末添加任何汇总或总结部分
            - 保持原文顺序，只在有问题的内容后添加标注

            标注格式：
            <!-- AI-RISK
            severity: high|medium|low
            description: 简短描述 -->

            示例：
            输入：表格 | A | B | 缺少C列
            输出：| A | B | <!-- AI-RISK severity: medium description: 缺少C列 -->
            """;

    public AIRiskService(DeepSeekConfig config) {
        this.config = config;

        String baseUrl = config.getApiUrl();
        if (baseUrl.endsWith("/chat/completions")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
        }

        log.info("[AI] 初始化 WebClient, baseUrl: {}, model: {}", baseUrl, config.getModel());

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * SSE 流式处理，返回进度更新
     */
    public Flux<String> processStream(AIRiskRequest request) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("DeepSeek API key not configured, returning original content");
            return Flux.just("{\"type\":\"complete\",\"content\":\"" + escapeJson(request.getContent()) + "\",\"severity\":\"none\"}\n");
        }

        log.info("[AI] 开始流式处理文档，内容长度: {} 字符", request.getContent().length());
        log.info("[AI] 调用模型: {}, API地址: {}", config.getModel(), config.getApiUrl());

        long startTime = System.currentTimeMillis();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        StringBuilder fullContent = new StringBuilder();

        // Build request body with thinking disabled
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", true);

        // Disable thinking to avoid long reasoning time
        ObjectNode thinkingNode = requestBody.putObject("thinking");
        thinkingNode.put("type", "disabled");

        // Build messages
        com.fasterxml.jackson.databind.node.ArrayNode messagesNode = requestBody.putArray("messages");

        ObjectNode systemMsg = messagesNode.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);

        ObjectNode userMsg = messagesNode.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", request.getContent());

        log.info("[AI] Request body: {}", requestBody);

        // Serialize to JSON string to ensure correct format
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("[AI] Failed to serialize request body", e);
            return Flux.just("{\"type\":\"error\",\"message\":\"Failed to serialize request\"}\n");
        }

        log.info("[AI] >>> 开始调用模型...");

        webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(error -> {
                    log.error("[AI] 流式处理失败: {}", error.getMessage(), error);
                    String errorMsg = "{\"type\":\"error\",\"message\":\"" + escapeJson(error.getMessage()) + "\"}\n";
                    sink.tryEmitNext(errorMsg);
                    sink.tryEmitComplete();
                })
                .subscribe(chunk -> {
                    // Parse DeepSeek SSE format: data: {"choices":[{"delta":{"content":"..."}}]}
                    String textContent = "";
                    try {
                        String line = chunk;
                        if (line.startsWith("data: ")) {
                            line = line.substring(6);
                        }

                        if ("[DONE]".equals(line.trim())) {
                            // Final cleanup: remove <br> tags from AI output
                            String finalContent = fullContent.toString();
                            finalContent = finalContent.replaceAll("(?i)<br\\s*/?>", "\n");
                            finalContent = finalContent.replaceAll("(?i)&lt;br\\s*/?&gt;", "\n");
                            String complete = "{\"type\":\"complete\",\"content\":\"" + escapeJson(finalContent) + "\",\"severity\":\"none\"}\n";
                            sink.tryEmitNext(complete);
                            sink.tryEmitComplete();
                            long duration = System.currentTimeMillis() - startTime;
                            log.info("[AI] 流式处理完成，耗时: {}ms", duration);
                            return;
                        }

                        JsonNode root = objectMapper.readTree(line);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            JsonNode contentNode = delta.path("content");
                            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                textContent = contentNode.asText();
                                fullContent.append(textContent);
                                String out = "{\"type\":\"chunk\",\"content\":\"" + escapeJson(textContent) + "\"}\n";
                                sink.tryEmitNext(out);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[AI] Failed to parse SSE chunk: {}", chunk);
                    }
                });

        return sink.asFlux();
    }

    /**
     * 非流式处理（保留兼容）
     */
    public AIRiskResponse process(AIRiskRequest request) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("DeepSeek API key not configured, returning original content");
            return AIRiskResponse.builder()
                    .content(request.getContent())
                    .overallSeverity("none")
                    .build();
        }

        try {
            log.info("[AI] 开始处理文档，内容长度: {} 字符", request.getContent().length());
            log.info("[AI] 调用模型: {}", config.getModel());

            long startTime = System.currentTimeMillis();

            // Build request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getModel());
            requestBody.put("stream", false);

            com.fasterxml.jackson.databind.node.ArrayNode messagesNode = requestBody.putArray("messages");

            ObjectNode systemMsg = messagesNode.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);

            ObjectNode userMsg = messagesNode.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", request.getContent());

            JsonNode response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            long duration = System.currentTimeMillis() - startTime;
            String content = response.path("choices").get(0).path("message").path("content").asText();

            log.info("[AI] 处理完成，耗时: {}ms", duration);

            return AIRiskResponse.builder()
                    .content(content)
                    .overallSeverity("none")
                    .build();
        } catch (Exception e) {
            log.error("AI processing failed: {}", e.getMessage(), e);
            throw new AIProcessingException(AIErrorCode.AI_SERVICE_ERROR, "AI 处理失败: " + e.getMessage(), e);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
