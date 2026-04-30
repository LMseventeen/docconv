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
            你是一个智能题目分析助手。请从用户上传的Word或PDF文档中提取题目，并按照指定格式输出。

            题目类型及输出格式：

            【单选题模板】
            1. 题干内容

            A.选项 A

            B.选项 B

            C.选项 C

            D.选项 D

            答案： A

            难度：适中

            解析：这里是解析的内容

            知识点：

            标签：

            【多选题模板】
            1. 题干内容

            A.选项 A

            B.选项 B

            C.选项 C

            D.选项 D

            答案： AB

            难度：适中

            解析：这里是解析的内容

            知识点：

            标签：

            【不定项选择题模板】
            1. [不定向选择题] 题干内容

            A.选项 A

            B.选项 B

            C.选项 C

            D.选项 D

            答案： AB

            难度：适中

            解析：这里是解析的内容

            知识点：

            标签：

            【判断题模板】
            1. 题干内容

            答案：正确

            难度：适中

            解析：这里是解析的内容

            知识点：

            标签：

            【填空题模板】
            1. 题干内容，第一个填空____，第二个填空____。

            答案：第1个空答案|第1个空备选答案；第2个空答案

            难度：适中

            解析：这里是解析的内容

            知识点：

            标签：

            【问答题模板】
            1. 题干内容。

            答案：这里是问答题的答案。

            难度：适中

            解析：这里是解析的内容

            知识点：

            标签：

            【材料题模板】
            1. 题干内容

            标签：

            (1)题干内容

            A.选项 A

            B.选项 B

            C.选项 C

            D.选项 D

            答案： A

            解析：这里是解析的内容

            (2)题干内容

            A.选项 A

            B.选项 B

            C.选项 C

            D.选项 D

            答案： AB

            解析：这里是解析的内容

            (3)[不定向选择题] 题干内容

            A.选项 A

            B.选项 B

            C.选项 C

            D.选项 D

            答案： AB

            解析：这里是解析的内容

            (4)题干内容

            答案：正确

            解析：这里是解析的内容

            (5)题干内容，第一个填空____，第二个填空____。

            答案：第1个空答案|第1个空备选答案；第2个空答案

            解析：这里是解析的内容

            (6)题干内容。

            答案：这里是问答题的答案。

            解析：这里是解析的内容

            重要规则：
            - 每个选项必须单独占一行，A、B、C、D选项之间必须用空行分隔
            - 选项格式示例：
              A.选项 A

              B.选项 B

              C.选项 C

              D.选项 D
            - 如果文档中的内容有缺失，比如解析缺失，则对应的内容输出为 "-"
            - 其他字段缺失同理，都用 "-" 代替
            - 严格按照上述模板格式输出，不要添加额外的说明或总结
            - 题目序号要连续排列
            - 难度可选：简单、适中、困难
            - 知识点和标签如果没有明确给出，请根据题目内容推断或留空
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
     * SSE 流式处理，分批调用AI，返回进度更新
     */
    public Flux<String> processStream(AIRiskRequest request) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("DeepSeek API key not configured, returning original content");
            return Flux.just("{\"type\":\"complete\",\"content\":\"" + escapeJson(request.getContent()) + "\",\"severity\":\"none\"}\n");
        }

        log.info("[AI] 开始流式处理文档，内容长度: {} 字符", request.getContent().length());
        log.info("[AI] 调用模型: {}, API地址: {}", config.getModel(), config.getApiUrl());

        // 将内容分批，每批最多50题
        List<String> batches = splitIntoBatches(request.getContent(), 50);
        log.info("[AI] 内容已分割成 {} 批进行处理", batches.size());

        long startTime = System.currentTimeMillis();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 顺序处理每一批
        processBatchesSequentially(batches, sink, startTime);

        return sink.asFlux();
    }

    /**
     * 将内容分割成多个批次，按题目编号分割
     */
    private List<String> splitIntoBatches(String content, int questionsPerBatch) {
        List<String> batches = new ArrayList<>();

        // 使用正则表达式分割题目，找到 "数字. " 开头的位置
        String[] parts = content.split("(?=\\d+\\.\\s)");

        StringBuilder currentBatch = new StringBuilder();
        int questionCount = 0;

        for (String part : parts) {
            if (part.trim().isEmpty()) continue;

            // 检查是否是题目（以数字开头）
            if (part.matches("\\d+\\..*")) {
                questionCount++;
            }

            currentBatch.append(part);

            if (questionCount >= questionsPerBatch) {
                batches.add(currentBatch.toString());
                currentBatch = new StringBuilder();
                questionCount = 0;
            }
        }

        // 添加剩余内容
        if (questionCount > 0 || currentBatch.length() > 0) {
            batches.add(currentBatch.toString());
        }

        return batches;
    }

    /**
     * 顺序处理所有批次
     */
    private void processBatchesSequentially(List<String> batches, Sinks.Many<String> sink, long startTime) {
        if (batches.isEmpty()) {
            String complete = "{\"type\":\"complete\",\"content\":\"\",\"severity\":\"none\"}\n";
            sink.tryEmitNext(complete);
            sink.tryEmitComplete();
            return;
        }

        StringBuilder fullContent = new StringBuilder();
        int[] batchIndex = {0};

        Runnable processNextBatch = new Runnable() {
            @Override
            public void run() {
                if (batchIndex[0] >= batches.size()) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[AI] 所有批次处理完成，共 {} 批，总耗时: {}ms，内容长度: {} 字符",
                            batches.size(), duration, fullContent.length());
                    log.info("[AI] 完整内容:\n{}", fullContent.toString());

                    String complete = "{\"type\":\"complete\",\"content\":\"" + escapeJson(fullContent.toString()) + "\",\"severity\":\"none\"}\n";
                    sink.tryEmitNext(complete);
                    sink.tryEmitComplete();
                    return;
                }

                String batchContent = batches.get(batchIndex[0]);
                log.info("[AI] 开始处理第 {} 批，内容长度: {} 字符", batchIndex[0] + 1, batchContent.length());

                String batchStart = "{\"type\":\"chunk\",\"content\":\"\\n\\n=== 第 " + (batchIndex[0] + 1) + "/" + batches.size() + " 批开始 ===\\n\"}\n";
                sink.tryEmitNext(batchStart);

                processSingleBatch(batchContent, sink, fullContent, batchIndex, this);
            }
        };

        processNextBatch.run();
    }

    /**
     * 处理单个批次
     */
    private void processSingleBatch(String batchContent, Sinks.Many<String> sink, StringBuilder fullContent, int[] batchIndex, Runnable onComplete) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", true);

        ObjectNode thinkingNode = requestBody.putObject("thinking");
        thinkingNode.put("type", "disabled");

        com.fasterxml.jackson.databind.node.ArrayNode messagesNode = requestBody.putArray("messages");

        ObjectNode systemMsg = messagesNode.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);

        ObjectNode userMsg = messagesNode.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", "请将以下题目按照指定格式输出（只输出题目内容，不需要额外说明）。重要：题目编号必须保持原文的连续编号，不能重新从1开始编号。\n\n" + batchContent);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("[AI] Failed to serialize request body", e);
            String errorMsg = "{\"type\":\"error\",\"message\":\"Failed to serialize request\"}\n";
            sink.tryEmitNext(errorMsg);
            onComplete.run();
            return;
        }

        final boolean[] batchFinished = {false};
        StringBuilder batchContentBuilder = new StringBuilder();

        webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(error -> {
                    log.error("[AI] 第 {} 批处理失败: {}", batchIndex[0] + 1, error.getMessage());
                    String errorMsg = "{\"type\":\"error\",\"message\":\"批次 " + (batchIndex[0] + 1) + " 处理失败: " + escapeJson(error.getMessage()) + "\"}\n";
                    sink.tryEmitNext(errorMsg);
                    if (!batchFinished[0]) {
                        batchFinished[0] = true;
                        batchIndex[0]++;
                        onComplete.run();
                    }
                })
                .subscribe(chunk -> {
                    try {
                        String line = chunk;
                        if (line.startsWith("data: ")) {
                            line = line.substring(6);
                        }

                        if ("[DONE]".equals(line.trim())) {
                            batchFinished[0] = true;
                            String finalBatchContent = batchContentBuilder.toString();
                            fullContent.append(finalBatchContent);

                            String batchEnd = "{\"type\":\"chunk\",\"content\":\"\\n=== 第 " + (batchIndex[0] + 1) + " 批结束 ===\\n\"}\n";
                            sink.tryEmitNext(batchEnd);

                            log.info("[AI] 第 {} 批处理完成，长度: {} 字符", batchIndex[0] + 1, finalBatchContent.length());

                            batchIndex[0]++;
                            onComplete.run();
                            return;
                        }

                        JsonNode root = objectMapper.readTree(line);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            JsonNode contentNode = delta.path("content");
                            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                String textContent = contentNode.asText();
                                batchContentBuilder.append(textContent);
                                String out = "{\"type\":\"chunk\",\"content\":\"" + escapeJson(textContent) + "\"}\n";
                                sink.tryEmitNext(out);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[AI] Failed to parse SSE chunk: {}", chunk);
                    }
                });
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

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("[AI] Request body: {}", jsonBody);

            JsonNode response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonBody)
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
