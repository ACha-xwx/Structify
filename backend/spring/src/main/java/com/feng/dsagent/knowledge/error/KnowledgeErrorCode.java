package com.feng.dsagent.knowledge.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：知识检索请求参数相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum KnowledgeErrorCode implements ErrorCode {
    QUERY_REQUIRED(HttpStatus.BAD_REQUEST, "KNOWLEDGE_QUERY_REQUIRED", "请输入检索内容"),
    QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "KNOWLEDGE_QUERY_TOO_LONG", "检索内容过长"),
    CHAPTER_TOO_LONG(HttpStatus.BAD_REQUEST, "KNOWLEDGE_CHAPTER_TOO_LONG", "章节编号无效"),
    LIMIT_INVALID(HttpStatus.BAD_REQUEST, "KNOWLEDGE_LIMIT_INVALID", "返回数量参数无效");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    KnowledgeErrorCode(HttpStatus status, String code, String defaultMessage) {
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
