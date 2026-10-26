package com.feng.dsagent.aiquota.entity;

/**
 * Distinguishes a newly reserved provider call from a retry carrying an existing idempotency key.
 */
public record AiQuotaReservationAttemptEntity(AiQuotaReservationEntity reservation, boolean newlyReserved) {
}
