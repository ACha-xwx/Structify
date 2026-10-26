package com.feng.dsagent.learning.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：学习事件写入与载荷校验相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum LearningErrorCode implements ErrorCode {
    EVENT_SERVER_MANAGED(
        HttpStatus.BAD_REQUEST,
        "LEARNING_EVENT_SERVER_MANAGED",
        "该学习记录只能由对应学习流程生成"
    ),
    CHAPTER_INVALID(HttpStatus.BAD_REQUEST, "LEARNING_CHAPTER_INVALID", "章节不存在或尚未发布"),
    REFERENCE_INVALID(HttpStatus.BAD_REQUEST, "LEARNING_REFERENCE_INVALID", "学习记录关联编号无效"),
    PAYLOAD_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "LEARNING_PAYLOAD_TOO_LONG", "学习记录内容过长"),
    PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "LEARNING_PAYLOAD_INVALID", "学习记录内容无效"),
    EVENT_TYPE_INVALID(HttpStatus.BAD_REQUEST, "LEARNING_EVENT_TYPE_INVALID", "学习记录类型无效");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    LearningErrorCode(HttpStatus status, String code, String defaultMessage) {
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
