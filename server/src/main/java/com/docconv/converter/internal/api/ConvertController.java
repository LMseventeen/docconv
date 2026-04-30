package com.docconv.converter.internal.api;

import com.docconv.ai.dto.AIRiskRequest;
import com.docconv.ai.service.AIRiskService;
import com.docconv.converter.ConvertService;
import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.ConvertResponse;
import com.docconv.converter.internal.normalizer.MarkdownNormalizer;
import com.docconv.converter.internal.support.documentparser.DocumentParser;
import com.docconv.support.kit.MultipartFileKit;
import com.docconv.support.exception.Errors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/v1/convert")
@RequiredArgsConstructor
@Tag(name = "convert/文档转换", description = "文档格式转换相关接口")
public class ConvertController {

    private final ConvertService convertService;
    private final DocumentParser documentParser;
    private final MarkdownNormalizer normalizer;
    private final AIRiskService aiRiskService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "转换文档", description = "上传文档文件，解析并转换为 Markdown 格式。支持 .docx 和 .pdf 格式。")
    public ConvertResponse convert(@RequestParam("file") MultipartFile file) {
        log.info("Received upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        var uploadFile = MultipartFileKit.toUploadFile(file);
        ConvertDocumentParseResult result = convertService.convert(uploadFile);

        return ConvertResponse.builder()
                .markdown(result.getContent())
                .originalFilename(file.getOriginalFilename())
                .build();
    }

    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "转换文档（流式）", description = "上传文档文件，流式解析并转换为 Markdown 格式。实时推送处理进度。")
    public Flux<String> convertStream(@RequestParam("file") MultipartFile file) {
        log.info("Received streaming upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        String format = detectSourceFormat(file.getOriginalFilename());

        // Parse: extract raw content
        var uploadFile = MultipartFileKit.toUploadFile(file);
        var parseResult = documentParser.parse(uploadFile);

        // Normalize
        String markdown = normalizer.normalize(parseResult.getContent(), format);

        log.info("[Controller] 文档解析完成，内容长度: {} 字符，开始流式 AI 处理", markdown.length());

        // Stream AI processing
        return aiRiskService.processStream(
                AIRiskRequest.builder()
                        .content(markdown)
                        .sourceFormat(format)
                        .build()
        );
    }

    private String detectSourceFormat(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "unknown";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }
}
