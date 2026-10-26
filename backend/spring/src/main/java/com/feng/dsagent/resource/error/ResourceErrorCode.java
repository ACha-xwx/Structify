package com.feng.dsagent.resource.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：学习资料元数据与内容读取相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum ResourceErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资料不存在"),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_CONTENT_NOT_FOUND", "资料内容不存在或不可访问");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ResourceErrorCode(HttpStatus status, String code, String defaultMessage) {
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
