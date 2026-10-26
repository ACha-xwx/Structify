package com.feng.dsagent.common.exception;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Primary business failure path. Renders as {@link ApiError} via {@link ApiExceptionHandler}.
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BizException(ErrorCode errorCode) {
        this(errorCode.status(), errorCode.code(), errorCode.defaultMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.status(), errorCode.code(), message);
    }

    public BizException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
