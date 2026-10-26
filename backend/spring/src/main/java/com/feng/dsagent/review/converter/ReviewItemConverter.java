package com.feng.dsagent.review.converter;

import com.feng.dsagent.review.entity.ReviewEntity;
import com.feng.dsagent.review.vo.ReviewItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将审核项实体转换为对外列表/摘要 VO（含来源是否完整）。
 */
@Mapper(componentModel = "spring")
public interface ReviewItemConverter {

    @Mapping(target = "type", expression = "java(entity.type().name())")
    @Mapping(target = "sourceComplete", source = "sourceComplete")
    ReviewItemVO toView(ReviewEntity entity, boolean sourceComplete);
}
