package com.feng.dsagent.learning.converter;

import com.feng.dsagent.learning.mapper.LearningEventMapper.LearningEventInsert;
import com.feng.dsagent.learning.vo.LearningEventVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将学习事件落库行转换为对外学习事件 VO。
 */
@Mapper(componentModel = "spring")
public interface LearningEventConverter {

    @Mapping(target = "id", source = "id")
    LearningEventVO toView(LearningEventInsert row);
}
