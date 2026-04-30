package com.docconv.converter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "文档解析结果")
@Data
public class ConvertDocumentParseResult {

    @Schema(description = "解析后的 Markdown 内容")
    private String content;

    @Schema(description = "AI 风险等级 (none/low/medium/high)")
    private String riskSeverity;
}
