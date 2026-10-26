package com.feng.dsagent.auth.converter;

import com.feng.dsagent.auth.entity.UserAccountEntity;
import com.feng.dsagent.auth.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将用户账号实体转换为对外 UserVO（不含 passwordHash）。
 */
@Mapper(componentModel = "spring")
public interface UserAccountConverter {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "roles", source = "roles")
    UserVO toView(UserAccountEntity entity);
}
