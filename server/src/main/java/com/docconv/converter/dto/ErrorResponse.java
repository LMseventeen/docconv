package com.docconv.converter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "错误响应")
public record ErrorResponse(
    int status,
    String error,
    String message
) {}
