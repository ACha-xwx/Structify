package com.feng.dsagent.common.error;

import com.feng.dsagent.common.exception.BizException;
import org.springframework.http.HttpStatus;

/**
 * Capability error codes implement this interface. Stable OpenAPI {@code code} strings must not change semantics.
 */
public interface ErrorCode {

    HttpStatus status();

    String code();

    String defaultMessage();

    default BizException toException() {
        return new BizException(this);
    }

    default BizException toException(String message) {
        return new BizException(this, message);
    }
}
