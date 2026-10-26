package com.feng.dsagent.aiquota.domain;

/**
 * Explains whether a quota settlement came from a provider meter or a conservative fallback.
 */
public enum AiQuotaUsageSource {
    PROVIDER_REPORTED,
    RESERVATION_ESTIMATE,
    NO_USAGE_REPORTED
}
