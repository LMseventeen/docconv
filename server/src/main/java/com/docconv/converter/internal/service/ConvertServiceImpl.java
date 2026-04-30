package com.docconv.converter.internal.service;

import com.docconv.ai.dto.AIRiskRequest;
import com.docconv.ai.dto.AIRiskResponse;
import com.docconv.ai.service.AIRiskService;
import com.docconv.converter.ConvertService;
import com.docconv.converter.dto.ConvertDocumentParseResult;
import com.docconv.converter.dto.UploadFile;
import com.docconv.converter.internal.normalizer.MarkdownNormalizer;
import com.docconv.converter.internal.support.documentparser.DocumentParser;
import com.docconv.support.exception.Errors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConvertServiceImpl implements ConvertService {

    private final DocumentParser documentParser;
    private final MarkdownNormalizer normalizer;
    private final AIRiskService aiRiskService;

    @Override
    public ConvertDocumentParseResult convert(UploadFile document) {
        validateFile(document);

        String format = detectSourceFormat(document.getFilename());
        log.info("Converting file: {} ({} bytes)", document.getFilename(), document.getSize());

        // Parse: extract raw content from document format
        var parseResult = documentParser.parse(document);

        // Normalize: clean and structure the Markdown
        String markdown = normalizer.normalize(parseResult.getContent(), format);

        // AI processing: fix structure, normalize math, annotate risks
        AIRiskResponse aiResult = aiRiskService.process(
                AIRiskRequest.builder()
                        .content(markdown)
                        .sourceFormat(format)
                        .build()
        );

        // Build result
        var result = new ConvertDocumentParseResult();
        result.setContent(aiResult.getContent());
        result.setRiskSeverity(aiResult.getOverallSeverity());

        log.info("Conversion complete: length={}, risk={}", aiResult.getContent().length(), aiResult.getOverallSeverity());
        return result;
    }

    private void validateFile(UploadFile document) {
        if (document == null) {
            throw Errors.FILE_EMPTY.toException("未提供上传文件");
        }
        if (document.getSize() == 0) {
            throw Errors.FILE_EMPTY.toException("上传文件为空");
        }
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
