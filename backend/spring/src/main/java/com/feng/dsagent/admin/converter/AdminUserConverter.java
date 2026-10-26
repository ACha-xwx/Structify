package com.feng.dsagent.admin.converter;

import com.feng.dsagent.admin.entity.AdminUserEntity;
import com.feng.dsagent.admin.vo.AdminUserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将管理端用户实体转换为对外 VO（含状态名与排序后的角色列表）。
 */
@Mapper(componentModel = "spring")
public interface AdminUserConverter {

    @Mapping(target = "status", expression = "java(entity.status().name())")
    @Mapping(target = "roles", expression = "java(entity.roles().stream().sorted().toList())")
    AdminUserVO toView(AdminUserEntity entity);
}
