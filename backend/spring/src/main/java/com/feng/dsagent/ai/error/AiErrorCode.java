package com.feng.dsagent.ai.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：AI 就绪检查相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum AiErrorCode implements ErrorCode {
    READINESS_OPERATION_INVALID(
        HttpStatus.BAD_REQUEST,
        "AI_READINESS_OPERATION_INVALID",
        "Unsupported AI readiness operation"
    );

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    AiErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
