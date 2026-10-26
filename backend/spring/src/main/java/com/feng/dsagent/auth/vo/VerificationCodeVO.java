package com.feng.dsagent.auth.vo;

/**
 * 职责：验证码请求的对外回执（仅文案，不含验证码明文）。
 */
public record VerificationCodeVO(String message) {}
