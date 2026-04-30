package com.docconv.ai.exception;

import com.docconv.converter.support.exception.AppException;
import com.docconv.converter.support.exception.ErrorCode;
import lombok.Getter;

@Getter
public class AIProcessingException extends AppException {

    public AIProcessingException(ErrorCode code, String message) {
        super(code.getStatus(), code.getCode(), message);
    }

    public AIProcessingException(ErrorCode code, String message, Throwable cause) {
        super(code.getStatus(), code.getCode(), message, cause);
    }
}
