package com.feng.dsagent.admin.converter;

import com.feng.dsagent.admin.entity.AdminAuditEventEntity;
import com.feng.dsagent.admin.vo.AdminAuditEventVO;
import org.mapstruct.Mapper;

/**
 * 职责：将管理端审计事件实体转换为对外 VO。
 */
@Mapper(componentModel = "spring")
public interface AdminAuditEventConverter {

    AdminAuditEventVO toView(AdminAuditEventEntity entity);
}
