package com.feng.dsagent.modelconfig.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 职责：模型连接配置存储与校验相关业务错误码。OpenAPI {@code code} 字符串稳定，勿改语义。
 */
public enum ModelConfigErrorCode implements ErrorCode {
    API_KEY_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "MODEL_CONFIG_API_KEY_REQUIRED",
        "模型服务地址或提供商变更时必须重新输入密钥"
    ),
    NOT_CONFIGURED(HttpStatus.CONFLICT, "MODEL_CONFIG_NOT_CONFIGURED", "尚未保存模型配置"),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_CONFIG_UNAVAILABLE", "模型配置当前不可用"),
    SETTINGS_INVALID(HttpStatus.BAD_REQUEST, "MODEL_CONFIG_SETTINGS_INVALID", "模型运行参数无效"),
    QUOTA_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "MODEL_CONFIG_QUOTA_REQUIRED",
        "启用模型配置前必须设置单用户每日令牌配额"
    ),
    URL_UNSAFE(HttpStatus.BAD_REQUEST, "MODEL_CONFIG_URL_UNSAFE", "模型服务地址不安全");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ModelConfigErrorCode(HttpStatus status, String code, String defaultMessage) {
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
