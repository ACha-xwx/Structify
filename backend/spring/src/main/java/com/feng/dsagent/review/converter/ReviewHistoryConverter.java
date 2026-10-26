package com.feng.dsagent.review.converter;

import com.feng.dsagent.review.entity.ReviewHistoryEntity;
import com.feng.dsagent.review.vo.ReviewHistoryVO;
import org.mapstruct.Mapper;

/**
 * 职责：将审核历史实体转换为对外 VO。
 */
@Mapper(componentModel = "spring")
public interface ReviewHistoryConverter {

    ReviewHistoryVO toView(ReviewHistoryEntity entity);
}
