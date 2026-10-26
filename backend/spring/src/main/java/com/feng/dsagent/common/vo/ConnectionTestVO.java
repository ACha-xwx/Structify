package com.feng.dsagent.common.vo;

/**
 * 职责：管理端配置连通性探测结果（是否连通及受控结果码）。
 */
public record ConnectionTestVO(boolean connected, String code) {}
