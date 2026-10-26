package com.feng.dsagent.presentation.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：课件目录与页面资源相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum PresentationErrorCode implements ErrorCode {
    DECK_NOT_FOUND(HttpStatus.NOT_FOUND, "PRESENTATION_DECK_NOT_FOUND", "课件不存在"),
    SLIDE_NOT_FOUND(HttpStatus.NOT_FOUND, "PRESENTATION_SLIDE_NOT_FOUND", "课件页不存在");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    PresentationErrorCode(HttpStatus status, String code, String defaultMessage) {
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
