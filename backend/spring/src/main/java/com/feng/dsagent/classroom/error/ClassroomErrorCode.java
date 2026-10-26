package com.feng.dsagent.classroom.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：课堂脚本、会话、备课与课件对齐相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum ClassroomErrorCode implements ErrorCode {
    SCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASSROOM_SCRIPT_NOT_FOUND", "课堂脚本不存在或尚未发布"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASSROOM_SESSION_NOT_FOUND", "课堂会话不存在"),
    STEP_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "CLASSROOM_STEP_OUT_OF_RANGE", "这一步不在这节课里"),
    ACTION_INVALID(HttpStatus.BAD_REQUEST, "CLASSROOM_ACTION_INVALID", "课堂操作无效"),
    ACTION_CONFLICT(HttpStatus.CONFLICT, "CLASSROOM_ACTION_INVALID", "课堂操作无效"),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASSROOM_BOOKMARK_NOT_FOUND", "收藏不存在"),
    PREPARATION_ACTIVE(HttpStatus.CONFLICT, "CLASSROOM_PREPARATION_ACTIVE", "已有课时正在备课，请等待完成"),
    PREPARATION_BUSY(HttpStatus.TOO_MANY_REQUESTS, "CLASSROOM_PREPARATION_BUSY", "备课服务繁忙"),
    TEXTBOOK_MISSING(HttpStatus.NOT_FOUND, "CLASSROOM_TEXTBOOK_MISSING", "该课时没有已审核教材来源，请先配置教材知识库"),
    PREPARATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASSROOM_PREPARATION_NOT_FOUND", "备课任务不存在或已过期"),
    SLIDES_UNAVAILABLE(HttpStatus.CONFLICT, "CLASSROOM_SLIDES_UNAVAILABLE", "本课时没有配套课件，无法指定页面"),
    SLIDE_STEP_INVALID(HttpStatus.BAD_REQUEST, "CLASSROOM_SLIDE_STEP_INVALID", "步骤序号无效"),
    SLIDE_NOT_IN_LESSON(HttpStatus.BAD_REQUEST, "CLASSROOM_SLIDE_NOT_IN_LESSON", "该页面不属于本课时课件"),
    SCRIPT_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "CLASSROOM_SCRIPT_INVALID", "课堂脚本格式无效"),
    MODEL_JSON_INVALID(HttpStatus.BAD_GATEWAY, "CLASSROOM_MODEL_JSON_INVALID", "模型修正后仍未通过校验");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ClassroomErrorCode(HttpStatus status, String code, String defaultMessage) {
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
