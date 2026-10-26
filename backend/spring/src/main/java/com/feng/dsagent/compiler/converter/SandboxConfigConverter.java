package com.feng.dsagent.compiler.converter;

import com.feng.dsagent.compiler.entity.SandboxConfigEntity;
import com.feng.dsagent.compiler.vo.SandboxConfigVO;
import org.mapstruct.Mapper;

/**
 * 职责：将沙箱运行时配置实体转换为对外 VO。
 */
@Mapper(componentModel = "spring")
public interface SandboxConfigConverter {

    SandboxConfigVO toView(SandboxConfigEntity entity);
}
