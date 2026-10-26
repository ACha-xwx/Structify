package com.feng.dsagent.modelconfig.converter;

import com.feng.dsagent.modelconfig.entity.ModelConfigEntity;
import com.feng.dsagent.modelconfig.vo.ModelConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将模型连接配置实体转换为对外 VO（含 apiKeyConfigured）。
 */
@Mapper(componentModel = "spring")
public interface ModelConfigConverter {

    @Mapping(target = "apiKeyConfigured", constant = "true")
    ModelConfigVO toView(ModelConfigEntity entity);
}
