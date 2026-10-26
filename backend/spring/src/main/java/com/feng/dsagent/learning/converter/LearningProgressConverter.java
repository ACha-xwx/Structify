package com.feng.dsagent.learning.converter;

import com.feng.dsagent.learning.vo.ChapterLearningProgressVO;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将章节活动累计字段转换为对外章节学习进度 VO。
 */
@Mapper(componentModel = "spring")
public interface LearningProgressConverter {

    @Mapping(
        target = "totalActivities",
        expression = "java(chatCount + classroomCount + animationCount + codeRunCount + eventCount)"
    )
    ChapterLearningProgressVO toView(
        String chapterId,
        int chapterNumber,
        String title,
        long chatCount,
        long classroomCount,
        long animationCount,
        long codeRunCount,
        long eventCount,
        Instant lastActivityAt
    );
}
