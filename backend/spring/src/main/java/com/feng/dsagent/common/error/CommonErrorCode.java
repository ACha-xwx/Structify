package com.feng.dsagent.common.error;

import org.springframework.http.HttpStatus;

/**
 * Cross-capability error codes. OpenAPI {@code code} strings are stable; do not rename values.
 */
public enum CommonErrorCode implements ErrorCode {
    CHAT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "提问过于频繁，请稍后重试"),
    MODEL_FEATURE_RATE_LIMITED(
        HttpStatus.TOO_MANY_REQUESTS,
        "MODEL_FEATURE_RATE_LIMITED",
        "请求过于频繁，请稍后重试"
    );

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    CommonErrorCode(HttpStatus status, String code, String defaultMessage) {
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
