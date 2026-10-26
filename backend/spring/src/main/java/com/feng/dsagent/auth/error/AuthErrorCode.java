package com.feng.dsagent.auth.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：认证注册、验证码、会话与管理员开通相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum AuthErrorCode implements ErrorCode {
    AUTH_ADMIN_PROVISION_CONFLICT(HttpStatus.CONFLICT, "AUTH_ADMIN_PROVISION_CONFLICT", "管理员账户状态冲突，未执行修复"),
    AUTH_ADMIN_PROVISION_TARGET_MISMATCH(HttpStatus.CONFLICT, "AUTH_ADMIN_PROVISION_TARGET_MISMATCH", "管理员邮箱和用户名指向不同账户，已拒绝修复"),
    AUTH_CODE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码无效或已过期"),
    AUTH_CODE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_CODE_RATE_LIMITED", "验证码请求过于频繁"),
    AUTH_CODE_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "AUTH_CODE_RESEND_TOO_SOON", "验证码发送过于频繁，请稍后再试"),
    AUTH_EMAIL_INVALID(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_INVALID", "请输入有效的邮箱地址"),
    AUTH_EMAIL_REGISTERED(HttpStatus.CONFLICT, "AUTH_EMAIL_REGISTERED", "该邮箱已注册，请直接登录"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "邮箱或密码错误"),
    AUTH_LOGIN_IDENTIFIER_INVALID(HttpStatus.BAD_REQUEST, "AUTH_LOGIN_IDENTIFIER_INVALID", "请提供邮箱或用户名之一"),
    AUTH_LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_RATE_LIMITED", "登录请求过于频繁，请稍后重试"),
    AUTH_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_INVALID", "密码长度必须为 8 到 128 位"),
    AUTH_PURPOSE_INVALID(HttpStatus.BAD_REQUEST, "AUTH_PURPOSE_INVALID", "验证码用途无效"),
    AUTH_RESET_INVALID(HttpStatus.BAD_REQUEST, "AUTH_RESET_INVALID", "无法重置该账号的密码"),
    AUTH_SESSION_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_INVALID", "登录状态已失效"),
    AUTH_USERNAME_INVALID(HttpStatus.BAD_REQUEST, "AUTH_USERNAME_INVALID", "用户名只能包含 3 到 32 位英文字母、数字或下划线"),
    AUTH_USERNAME_REGISTERED(HttpStatus.CONFLICT, "AUTH_USERNAME_REGISTERED", "该用户名已被使用"),
    NODE_COMPAT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "NODE_COMPAT_DISABLED", "课件兼容服务尚未启用");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus status, String code, String defaultMessage) {
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
