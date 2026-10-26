package com.feng.dsagent.common.support;

import java.util.Locale;

/**
 * 职责：共享字符串规范化（trim / 可选空转 null / 大写），避免各 Service 复制私有方法。
 */
public final class StringNormalizer {

    private StringNormalizer() {
    }

    public static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String upper(String value) {
        String normalized = optional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
