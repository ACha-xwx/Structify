package com.feng.dsagent.learning.vo;

import java.util.List;

import com.feng.dsagent.learning.vo.ChapterLearningProgressVO;
public record LearningProgressVO(long totalActivities, List<ChapterLearningProgressVO> chapters) {

    public LearningProgressVO {
        chapters = List.copyOf(chapters);
    }
}
