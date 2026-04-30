package com.docconv.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "AI 风险处理响应")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRiskResponse {

    @Schema(description = "AI 处理后的内容（含风险标注）")
    private String content;

    @Schema(description = "总体风险等级 (none/low/medium/high)")
    private String overallSeverity;
}
