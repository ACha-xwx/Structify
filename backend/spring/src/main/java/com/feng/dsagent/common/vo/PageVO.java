package com.feng.dsagent.common.vo;

import java.util.List;

/**
 * Shared pagination envelope. JSON field names stay {@code items}, {@code page}, {@code size}, {@code total}.
 */
public record PageVO<T>(List<T> items, int page, int size, long total) {

    public static <T> PageVO<T> of(int page, int size, long total, List<T> items) {
        return new PageVO<>(items, page, size, total);
    }
}
