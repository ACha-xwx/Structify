package com.feng.dsagent.chat.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：对话会话、附件与提问校验相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum ChatErrorCode implements ErrorCode {
    SESSION_UPDATE_EMPTY(
        HttpStatus.BAD_REQUEST,
        "CHAT_SESSION_UPDATE_EMPTY",
        "至少需要修改名称或置顶状态"
    ),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "对话会话不存在"),
    RETRY_CONFLICT(HttpStatus.CONFLICT, "CHAT_RETRY_CONFLICT", "对话已更新，请重新打开会话后重试"),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_ATTACHMENT_NOT_FOUND", "对话附件不存在"),
    RETRY_INVALID(HttpStatus.BAD_REQUEST, "CHAT_RETRY_INVALID", "重试消息无效"),
    REASONING_INVALID(HttpStatus.BAD_REQUEST, "CHAT_REASONING_INVALID", "思考强度无效"),
    EVIDENCE_UNAVAILABLE(
        HttpStatus.CONFLICT,
        "CHAT_EVIDENCE_UNAVAILABLE",
        "No authorized published evidence is available for this question"
    ),
    PROMPT_REQUIRED(HttpStatus.BAD_REQUEST, "CHAT_PROMPT_REQUIRED", "问题不能为空"),
    PROMPT_TOO_LONG(HttpStatus.BAD_REQUEST, "CHAT_PROMPT_TOO_LONG", "问题内容过长"),
    CHAPTER_INVALID(HttpStatus.BAD_REQUEST, "CHAT_CHAPTER_INVALID", "章节不存在或尚未发布"),
    SESSION_TITLE_INVALID(
        HttpStatus.BAD_REQUEST,
        "CHAT_SESSION_TITLE_INVALID",
        "对话名称不能为空且不能超过 200 个字符"
    ),
    ATTACHMENT_INVALID(
        HttpStatus.BAD_REQUEST,
        "CHAT_ATTACHMENT_INVALID",
        "附件格式或大小不符合要求，请重新选择文本文件或图片"
    );

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ChatErrorCode(HttpStatus status, String code, String defaultMessage) {
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
