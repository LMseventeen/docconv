package com.docconv.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "AI 风险处理请求")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRiskRequest {

    @Schema(description = "解析后的 Markdown 内容")
    private String content;

    @Schema(description = "原始文档格式 (pdf/docx等)")
    private String sourceFormat;
}
