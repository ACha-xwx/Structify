package com.feng.dsagent.compiler.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：代码编译运行、沙箱配置与课堂代码相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum CompilerErrorCode implements ErrorCode {
    CODE_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "COMPILER_CODE_TOO_LONG", "代码长度超过允许上限"),
    INPUT_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "COMPILER_INPUT_TOO_LONG", "标准输入长度超过允许上限"),
    OUTPUT_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "COMPILER_OUTPUT_TOO_LONG", "标准输出长度超过允许上限"),
    OUTPUT_STDERR_TOO_LONG(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "COMPILER_OUTPUT_TOO_LONG",
        "错误输出长度超过允许上限"
    ),
    CODE_REQUIRED(HttpStatus.BAD_REQUEST, "COMPILER_CODE_REQUIRED", "代码不能为空"),
    LANGUAGE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "COMPILER_LANGUAGE_UNSUPPORTED", "仅支持 C 和 Python"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "COMPILER_REQUEST_INVALID", "请输入代码执行参数"),
    CHAPTER_INVALID(HttpStatus.BAD_REQUEST, "COMPILER_CHAPTER_INVALID", "章节不存在或尚未发布"),
    CHAPTER_PARAM_INVALID(HttpStatus.BAD_REQUEST, "COMPILER_CHAPTER_INVALID", "章节参数无效"),
    ANALYSIS_INVALID(HttpStatus.BAD_REQUEST, "COMPILER_ANALYSIS_INVALID", "请输入代码分析参数"),
    STATUS_INVALID(HttpStatus.BAD_REQUEST, "COMPILER_STATUS_INVALID", "代码运行状态无效"),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPILER_RUN_NOT_FOUND", "代码运行记录不存在"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "COMPILER_RATE_LIMITED", "代码运行请求过于频繁，请稍后重试"),
    CONCURRENCY_LIMITED(
        HttpStatus.TOO_MANY_REQUESTS,
        "COMPILER_CONCURRENCY_LIMITED",
        "代码执行任务较多，请稍后重试"
    ),
    NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "COMPILER_NOT_CONFIGURED", "代码执行服务尚未配置"),
    UPSTREAM_TIMEOUT(
        HttpStatus.GATEWAY_TIMEOUT,
        "COMPILER_UPSTREAM_TIMEOUT",
        "代码执行服务响应超时，请稍后重试"
    ),
    UPSTREAM_UNAVAILABLE(
        HttpStatus.BAD_GATEWAY,
        "COMPILER_UPSTREAM_UNAVAILABLE",
        "代码执行服务暂时不可用，请稍后重试"
    ),
    UPSTREAM_BUSY(
        HttpStatus.TOO_MANY_REQUESTS,
        "COMPILER_UPSTREAM_BUSY",
        "此刻同时运行的人较多，请过几秒再试"
    ),
    UPSTREAM_INVALID_RESPONSE(
        HttpStatus.BAD_GATEWAY,
        "COMPILER_UPSTREAM_INVALID_RESPONSE",
        "代码执行服务返回了无效结果，请稍后重试"
    ),
    EXECUTION_INTERRUPTED(
        HttpStatus.SERVICE_UNAVAILABLE,
        "COMPILER_EXECUTION_INTERRUPTED",
        "代码执行请求已中断，请重试"
    ),
    SANDBOX_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_DISABLED", "交互式运行环境未启用"),
    SANDBOX_SESSION_LIMITED(
        HttpStatus.TOO_MANY_REQUESTS,
        "SANDBOX_SESSION_LIMITED",
        "正在运行的程序太多，请稍后再试"
    ),
    SANDBOX_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_UNAVAILABLE", "运行环境暂时不可用"),
    SANDBOX_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SANDBOX_SESSION_NOT_FOUND", "运行会话不存在或已结束"),
    SANDBOX_CONFIG_PROVIDER_UNSUPPORTED(
        HttpStatus.BAD_REQUEST,
        "SANDBOX_CONFIG_PROVIDER_UNSUPPORTED",
        "仅支持 Piston 或 Judge0 沙箱"
    ),
    SANDBOX_CONFIG_NOT_CONFIGURED(
        HttpStatus.CONFLICT,
        "SANDBOX_CONFIG_NOT_CONFIGURED",
        "尚未保存沙箱配置"
    ),
    SANDBOX_CONFIG_DISABLED(HttpStatus.CONFLICT, "SANDBOX_CONFIG_DISABLED", "当前沙箱配置已停用"),
    SANDBOX_CONFIG_INVALID(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SANDBOX_CONFIG_INVALID",
        "已保存的沙箱提供商无效"
    ),
    SANDBOX_CONFIG_URL_UNSAFE(
        HttpStatus.BAD_REQUEST,
        "SANDBOX_CONFIG_URL_UNSAFE",
        "沙箱服务地址不安全"
    ),
    CODE_SAMPLES_LESSON_UNKNOWN(
        HttpStatus.NOT_FOUND,
        "CODE_SAMPLES_LESSON_UNKNOWN",
        "该课时还没有配套的课堂代码"
    );

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    CompilerErrorCode(HttpStatus status, String code, String defaultMessage) {
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
