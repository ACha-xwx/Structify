package com.feng.dsagent.model;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：上游模型客户端失败码。OpenAPI {@code code} 取枚举名，勿改语义。
 */
public enum ModelErrorCode implements ErrorCode {
    MODEL_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Model service is not configured"),
    MODEL_REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Model request timed out"),
    MODEL_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "Model request failed"),
    MODEL_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "Model service returned an error"),
    MODEL_RESPONSE_READ_FAILED(HttpStatus.BAD_GATEWAY, "Model response could not be read"),
    MODEL_RESPONSE_TOO_LARGE(HttpStatus.BAD_GATEWAY, "Model response exceeded the configured limit"),
    MODEL_STREAM_IDLE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Model stream timed out while waiting for data"),
    MODEL_STREAM_READ_FAILED(HttpStatus.BAD_GATEWAY, "Model stream could not be read"),
    MODEL_OUTPUT_TRUNCATED(HttpStatus.BAD_GATEWAY, "Model output was truncated by the token limit"),
    MODEL_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "Model service returned no content");

    private final HttpStatus status;
    private final String safeMessage;

    ModelErrorCode(HttpStatus status, String safeMessage) {
        this.status = status;
        this.safeMessage = safeMessage;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String defaultMessage() {
        return safeMessage;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
