package com.docconv.converter.support.exception;

public class Errors {

    public static final ErrorCode BAD_REQUEST = ErrorCode.of(400, "BAD_REQUEST", "请求格式不正确");

    public static final ErrorCode INVALID_ARGUMENT = ErrorCode.of(400, "INVALID_ARGUMENT", "参数不正确");

    public static final ErrorCode NOT_FOUND = ErrorCode.of(404, "NOT_FOUND", "资源不存在");

    public static final ErrorCode INTERNAL_ERROR = ErrorCode.of(500, "INTERNAL_ERROR", "服务器内部错误");

    public static final ErrorCode UNSUPPORTED_FORMAT = ErrorCode.of(400, "UNSUPPORTED_FORMAT", "不支持的文件格式");

    public static final ErrorCode FILE_EMPTY = ErrorCode.of(400, "FILE_EMPTY", "上传文件为空");

    public static final ErrorCode PARSER_EXECUTION_ERROR = ErrorCode.of(500, "PARSER_EXECUTION_ERROR", "文档解析失败");

    public static final ErrorCode SYSTEM_DEPENDENCY_ERROR = ErrorCode.of(503, "SYSTEM_DEPENDENCY_ERROR", "系统依赖不可用");
}
