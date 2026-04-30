package com.docconv.ai.exception;

import com.docconv.converter.support.exception.ErrorCode;

public class AIErrorCode {

    public static final ErrorCode AI_SERVICE_ERROR = ErrorCode.of(502, "AI_SERVICE_ERROR", "AI 服务调用失败");

    public static final ErrorCode AI_TIMEOUT = ErrorCode.of(504, "AI_TIMEOUT", "AI 服务响应超时");

    public static final ErrorCode AI_INVALID_RESPONSE = ErrorCode.of(502, "AI_INVALID_RESPONSE", "AI 返回格式无效");

    public static final ErrorCode AI_NOT_CONFIGURED = ErrorCode.of(500, "AI_NOT_CONFIGURED", "AI 服务未配置");
}
