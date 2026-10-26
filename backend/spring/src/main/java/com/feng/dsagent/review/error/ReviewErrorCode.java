package com.feng.dsagent.review.error;

import com.feng.dsagent.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Review capability error codes. OpenAPI {@code code} strings are stable; do not rename values.
 */
public enum ReviewErrorCode implements ErrorCode {
    PAGE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_PAGE_INVALID", "Pagination parameters are invalid"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_REVIEW_NOT_FOUND", "review item was not found"),
    TYPE_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_REVIEW_TYPE_INVALID", "Unknown review type"),
    STATUS_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_REVIEW_STATUS_INVALID", "Unknown review status"),
    ID_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_REVIEW_ID_INVALID", "Review item identifier is invalid"),
    SOURCE_INCOMPLETE(
        HttpStatus.CONFLICT,
        "ADMIN_REVIEW_SOURCE_INCOMPLETE",
        "Verified review status requires a verified, complete source chain in a published chapter"
    );

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ReviewErrorCode(HttpStatus status, String code, String defaultMessage) {
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
