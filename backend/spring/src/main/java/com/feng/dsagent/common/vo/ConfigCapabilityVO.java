package com.feng.dsagent.common.vo;

/**
 * 职责：管理端配置能力是否可用及其原因（含脱敏后的配置载荷）。
 */
public record ConfigCapabilityVO<T>(boolean available, String reason, T configuration) {}
