package com.feng.dsagent.review.converter;

import com.feng.dsagent.review.entity.ReviewEntity;
import com.feng.dsagent.review.entity.ReviewSourceEntity;
import com.feng.dsagent.review.vo.ReviewSourceVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将审核来源实体或审核项实体转换为来源链 VO。
 */
@Mapper(componentModel = "spring")
public interface ReviewSourceConverter {

    ReviewSourceVO toView(ReviewSourceEntity entity);

    @Mapping(target = "type", expression = "java(entity.type().name())")
    ReviewSourceVO toView(ReviewEntity entity);
}
