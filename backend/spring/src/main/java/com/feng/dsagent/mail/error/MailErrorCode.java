package com.feng.dsagent.mail.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：邮件 SMTP 配置、模板校验与发信相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum MailErrorCode implements ErrorCode {
    AUTH_EMAIL_INVALID(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_INVALID", "请输入有效的邮箱地址"),
    AUTH_MAIL_DELIVERY_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_MAIL_DELIVERY_FAILED", "验证码邮件暂时无法发送"),
    MAIL_CONFIG_FIELD_TOO_LONG(HttpStatus.BAD_REQUEST, "MAIL_CONFIG_FIELD_TOO_LONG", "邮件配置字段过长"),
    MAIL_CONFIG_INVALID(HttpStatus.BAD_REQUEST, "MAIL_CONFIG_INVALID", "邮件配置不能为空"),
    MAIL_CONFIG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_CONFIG_UNAVAILABLE", "邮件配置密钥不可用"),
    MAIL_FROM_EMAIL_INVALID(HttpStatus.BAD_REQUEST, "MAIL_FROM_EMAIL_INVALID", "请输入有效的邮箱地址"),
    MAIL_FROM_NAME_INVALID(HttpStatus.BAD_REQUEST, "MAIL_FROM_NAME_INVALID", "发件人名称不能为空"),
    MAIL_FROM_REQUIRED(HttpStatus.BAD_REQUEST, "MAIL_FROM_REQUIRED", "启用邮件发送时必须填写发件人邮箱"),
    MAIL_PASSWORD_CLEAR_CONFLICT(HttpStatus.BAD_REQUEST, "MAIL_PASSWORD_CLEAR_CONFLICT", "清除密码时不能同时填写新密码"),
    MAIL_RESEND_INTERVAL_INVALID(HttpStatus.BAD_REQUEST, "MAIL_RESEND_INTERVAL_INVALID", "重发间隔必须在 5 到 3600 秒之间"),
    MAIL_SECURITY_MODE_INVALID(HttpStatus.BAD_REQUEST, "MAIL_SECURITY_MODE_INVALID", "安全模式必须是 NONE、STARTTLS 或 SSL"),
    MAIL_SESSION_TTL_INVALID(HttpStatus.BAD_REQUEST, "MAIL_SESSION_TTL_INVALID", "登录会话周期必须在 1 到 90 天之间"),
    MAIL_SITE_NAME_INVALID(HttpStatus.BAD_REQUEST, "MAIL_SITE_NAME_INVALID", "站点名称不能为空"),
    MAIL_SMTP_HOST_FORBIDDEN(HttpStatus.BAD_REQUEST, "MAIL_SMTP_HOST_FORBIDDEN", "SMTP 主机不能指向本机或私有网络"),
    MAIL_SMTP_HOST_INVALID(HttpStatus.BAD_REQUEST, "MAIL_SMTP_HOST_INVALID", "SMTP 主机必须是公开域名"),
    MAIL_SMTP_PASSWORD_REQUIRED(HttpStatus.BAD_REQUEST, "MAIL_SMTP_PASSWORD_REQUIRED", "启用 SMTP 用户认证时必须填写密码"),
    MAIL_SMTP_PORT_INVALID(HttpStatus.BAD_REQUEST, "MAIL_SMTP_PORT_INVALID", "SMTP 端口仅允许 25、465、587 或 2525"),
    MAIL_SUBJECT_INVALID(HttpStatus.BAD_REQUEST, "MAIL_SUBJECT_INVALID", "邮件主题不能为空"),
    MAIL_TEMPLATE_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_CODE_REQUIRED", "邮件模板必须包含 {{code}} 变量"),
    MAIL_TEMPLATE_INVALID(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_INVALID", "邮件模板不能为空"),
    MAIL_TEMPLATE_UNSAFE_MARKUP(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_UNSAFE_MARKUP", "邮件模板不能包含脚本或事件处理器"),
    MAIL_TEMPLATE_VARIABLE_INVALID(HttpStatus.BAD_REQUEST, "MAIL_TEMPLATE_VARIABLE_INVALID", "邮件模板包含未允许的变量"),
    MAIL_TEST_RECIPIENT_INVALID(HttpStatus.BAD_REQUEST, "MAIL_TEST_RECIPIENT_INVALID", "请输入有效的邮箱地址"),
    MAIL_TIMEOUT_INVALID(HttpStatus.BAD_REQUEST, "MAIL_TIMEOUT_INVALID", "连接超时必须在 1 到 30 秒之间"),
    MAIL_VERIFICATION_TTL_INVALID(HttpStatus.BAD_REQUEST, "MAIL_VERIFICATION_TTL_INVALID", "验证码有效期必须在 1 到 60 分钟之间");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    MailErrorCode(HttpStatus status, String code, String defaultMessage) {
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
