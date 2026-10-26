package com.feng.dsagent.admin.vo;

import com.feng.dsagent.admin.vo.AdminModuleCapabilityVO;
import com.feng.dsagent.admin.vo.AdminServiceStatusVO;
import java.util.List;
import java.util.Map;

/**
 * 职责：管理控制台首页能力聚合（模块可用性与服务状态）。
 */
public record AdminCapabilityVO(
    long userId,
    List<String> roles,
    Map<String, AdminModuleCapabilityVO> modules,
    AdminServiceStatusVO service
) {
}
