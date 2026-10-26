package com.feng.dsagent.aiquota.entity;

import java.time.LocalDate;

public record AiQuotaBucketEntity(long userId, LocalDate quotaDate, long dailyTokenQuota, long reservedTokens, long consumedTokens) {
}
