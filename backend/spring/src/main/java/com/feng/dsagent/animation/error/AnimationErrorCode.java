package com.feng.dsagent.animation.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：动画生成、观察与 DSVP 引擎相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum AnimationErrorCode implements ErrorCode {
    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节未发布或不存在"),
    OBSERVATION_REQUIRED(HttpStatus.BAD_REQUEST, "ANIMATION_OBSERVATION_REQUIRED", "请填写动画观察结论"),
    OBSERVATION_TOO_LONG(HttpStatus.BAD_REQUEST, "ANIMATION_OBSERVATION_TOO_LONG", "动画观察内容过长"),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "ANIMATION_RECORD_NOT_FOUND", "动画记录不存在"),
    MODEL_INVALID(HttpStatus.BAD_GATEWAY, "ANIMATION_MODEL_INVALID", "模型未返回有效的动画数据"),
    PROMPT_REQUIRED(HttpStatus.BAD_REQUEST, "ANIMATION_PROMPT_REQUIRED", "动画描述不能为空"),
    PROMPT_TOO_LONG(HttpStatus.BAD_REQUEST, "ANIMATION_PROMPT_TOO_LONG", "动画描述过长"),
    TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "ANIMATION_TYPE_UNSUPPORTED", "暂不支持该动画类型"),
    CHAPTER_INVALID(HttpStatus.BAD_REQUEST, "ANIMATION_CHAPTER_INVALID", "章节不存在或尚未发布"),
    NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_SUPPORTED", "能力表里没有这个演示"),
    DECLINED(HttpStatus.BAD_REQUEST, "ANIMATION_DECLINED", "这不是答应看演示"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "ANIMATION_REQUEST_INVALID", "本地引擎没有给出可执行请求"),
    NOT_NEEDED(HttpStatus.BAD_REQUEST, "ANIMATION_NOT_NEEDED", "这个问题不需要动画演示"),
    ARGUMENTS_MISSING(HttpStatus.BAD_REQUEST, "ANIMATION_ARGUMENTS_MISSING", "缺少参数"),
    DSVP_CHAPTER_INVALID(HttpStatus.BAD_REQUEST, "DSVP_CHAPTER_INVALID", "章节不存在或尚未发布"),
    DSVP_SOURCE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "DSVP_SOURCE_TYPE_INVALID", "来源类型与调用通道不匹配"),
    DSVP_SOURCE_FORBIDDEN(HttpStatus.FORBIDDEN, "DSVP_SOURCE_FORBIDDEN", "无法访问所请求的学习来源"),
    DSVP_CHAPTER_CONFLICT(HttpStatus.CONFLICT, "DSVP_CHAPTER_CONFLICT", "学习来源与章节上下文冲突"),
    DSVP_TRACE_COLLISION(HttpStatus.CONFLICT, "DSVP_TRACE_COLLISION", "DSVP trace id is already bound to different evidence"),
    DSVP_EXECUTION_INVALID(HttpStatus.BAD_REQUEST, "DSVP_EXECUTION_INVALID", "DSVP 执行无效"),
    DSVP_ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DSVP_ENGINE_UNAVAILABLE", "本地动画引擎不可用"),
    DSVP_REQUEST_TOO_LARGE(HttpStatus.BAD_REQUEST, "DSVP_REQUEST_TOO_LARGE", "DSVP 请求过大"),
    DSVP_CONCURRENCY_LIMITED(
        HttpStatus.TOO_MANY_REQUESTS,
        "DSVP_CONCURRENCY_LIMITED",
        "Animation requests are temporarily busy"
    ),
    DSVP_VERSION_UNSUPPORTED(HttpStatus.BAD_REQUEST, "DSVP_VERSION_UNSUPPORTED", "Unsupported DSVP version"),
    DSVP_OPERATION_UNSUPPORTED(
        HttpStatus.BAD_REQUEST,
        "DSVP_OPERATION_UNSUPPORTED",
        "Unsupported DSVP structure or operation"
    ),
    DSVP_INITIAL_STATE_INVALID(HttpStatus.BAD_REQUEST, "DSVP_INITIAL_STATE_INVALID", "initial_state.data must be an array"),
    DSVP_INITIAL_STATE_TOO_LARGE(HttpStatus.BAD_REQUEST, "DSVP_INITIAL_STATE_TOO_LARGE", "initial_state.data is too large"),
    DSVP_INITIAL_STATE_OVERFLOW(HttpStatus.BAD_REQUEST, "DSVP_INITIAL_STATE_OVERFLOW", "initial_state exceeds capacity"),
    DSVP_CONTEXT_INVALID(HttpStatus.BAD_REQUEST, "DSVP_CONTEXT_INVALID", "context field must be a bounded string"),
    DSVP_VALUE_REQUIRED(HttpStatus.BAD_REQUEST, "DSVP_VALUE_REQUIRED", "field is required"),
    DSVP_VALUE_INVALID(HttpStatus.BAD_REQUEST, "DSVP_VALUE_INVALID", "field must be scalar"),
    DSVP_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "DSVP_REQUEST_INVALID", "DSVP request is invalid"),
    DSVP_UNEXPECTED_FIELD(HttpStatus.BAD_REQUEST, "DSVP_UNEXPECTED_FIELD", "request contains an unsupported field"),
    DSVP_ENGINE_ERROR(HttpStatus.BAD_REQUEST, "DSVP_ENGINE_ERROR", "本地动画引擎拒绝了这次请求");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    AnimationErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Resolve a stable OpenAPI code string used by DSVP validation helpers.
     */
    public static AnimationErrorCode ofCode(String code) {
        for (AnimationErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown animation error code: " + code);
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
