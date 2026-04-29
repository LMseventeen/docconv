package com.docconv.converter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Schema(description = "文档转换响应")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertResponse {

    @Schema(description = "解析后的 Markdown 内容")
    private String markdown;

    @Schema(description = "原始文件名")
    private String originalFilename;
}
