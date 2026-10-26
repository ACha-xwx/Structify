package com.feng.dsagent.aiquota.entity;

import java.time.LocalDate;

public record AiQuotaAccountEntity(
    long userId,
    LocalDate quotaDate,
    long dailyTokenQuota,
    long reservedTokens,
    long consumedTokens,
    long remainingTokens,
    int activeReservations
) {
}
