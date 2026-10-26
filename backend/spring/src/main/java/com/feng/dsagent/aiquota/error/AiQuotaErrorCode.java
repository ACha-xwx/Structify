package com.feng.dsagent.aiquota.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：AI 配额预留、结算与并发控制相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum AiQuotaErrorCode implements ErrorCode {
    RESERVATION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "AI_QUOTA_RESERVATION_NOT_FOUND",
        "AI 配额预留不存在"
    ),
    CONCURRENCY_LIMITED(
        HttpStatus.TOO_MANY_REQUESTS,
        "AI_QUOTA_CONCURRENCY_LIMITED",
        "AI 请求并发数已达到上限"
    ),
    EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "AI_QUOTA_EXHAUSTED", "AI 每日 token 配额不足"),
    RESERVATION_NOT_ACTIVE(
        HttpStatus.CONFLICT,
        "AI_QUOTA_RESERVATION_NOT_ACTIVE",
        "AI 配额预留状态已变化"
    ),
    USER_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_USER_INVALID", "用户标识无效"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_REQUEST_INVALID", "AI 配额预留参数不能为空"),
    NOT_CONFIGURED(
        HttpStatus.BAD_REQUEST,
        "AI_QUOTA_NOT_CONFIGURED",
        "AI 每日 token 配额尚未配置"
    ),
    NOT_CONFIGURED_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AI_QUOTA_NOT_CONFIGURED",
        "AI token quota is not configured for the active model"
    ),
    ESTIMATE_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_ESTIMATE_INVALID", "预估 token 数必须大于零"),
    ESTIMATE_TOO_LARGE(HttpStatus.BAD_REQUEST, "AI_QUOTA_ESTIMATE_INVALID", "AI request is too large"),
    CONCURRENCY_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_CONCURRENCY_INVALID", "AI 并发上限必须大于零"),
    REQUEST_ID_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_REQUEST_ID_INVALID", "AI 请求标识无效"),
    RESERVATION_TTL_INVALID(
        HttpStatus.BAD_REQUEST,
        "AI_QUOTA_RESERVATION_TTL_INVALID",
        "AI 配额预留时长无效"
    ),
    RESERVATION_ID_INVALID(
        HttpStatus.BAD_REQUEST,
        "AI_QUOTA_RESERVATION_ID_INVALID",
        "AI 配额预留标识无效"
    ),
    USAGE_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_USAGE_INVALID", "实际 token 数不能为负数"),
    USAGE_SOURCE_INVALID(
        HttpStatus.BAD_REQUEST,
        "AI_QUOTA_USAGE_SOURCE_INVALID",
        "AI usage source is required"
    ),
    FAILURE_CODE_INVALID(HttpStatus.BAD_REQUEST, "AI_QUOTA_FAILURE_CODE_INVALID", "AI 失败代码无效"),
    AUTHENTICATION_REQUIRED(
        HttpStatus.UNAUTHORIZED,
        "AI_QUOTA_AUTHENTICATION_REQUIRED",
        "Authentication is required for formal AI generation"
    ),
    REQUEST_IN_PROGRESS(
        HttpStatus.CONFLICT,
        "AI_QUOTA_REQUEST_IN_PROGRESS",
        "An AI request with this idempotency key is still in progress"
    ),
    REQUEST_ALREADY_FINALIZED(
        HttpStatus.CONFLICT,
        "AI_QUOTA_REQUEST_ALREADY_FINALIZED",
        "An AI request with this idempotency key has already completed"
    );

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    AiQuotaErrorCode(HttpStatus status, String code, String defaultMessage) {
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
