package com.feng.dsagent.common.support;

import com.feng.dsagent.common.error.ErrorCode;
import com.feng.dsagent.common.exception.BizException;

/**
 * 职责：分页参数校验与 offset 预计算（供 MyBatis {@code OFFSET #{offset}}）。
 */
public final class PageParams {

    public static final int DEFAULT_MAX_SIZE = 100;

    private PageParams() {
    }

    public static void requireValid(int page, int size, ErrorCode pageInvalid) {
        requireValid(page, size, DEFAULT_MAX_SIZE, pageInvalid);
    }

    public static void requireValid(int page, int size, int maxSize, ErrorCode pageInvalid) {
        if (page < 0 || size < 1 || size > maxSize) {
            throw new BizException(pageInvalid);
        }
    }

    public static int offset(int page, int size) {
        return page * size;
    }
}
