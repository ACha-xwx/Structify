package com.feng.dsagent.auth.vo;

/**
 * 职责：向仍走 Node 兼容鉴权路径的客户端返回短期令牌。
 */
public record NodeCompatibilityTokenVO(String token, long expiresInSeconds) {}
