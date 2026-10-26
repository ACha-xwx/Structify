package com.feng.dsagent.learning.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningProgressMapper {

    List<ChapterRow> selectPublishedChapters();

    List<ActivityRow> selectChatActivity(@Param("userId") long userId);

    List<ActivityRow> selectClassroomActivity(@Param("userId") long userId);

    List<ActivityRow> selectAnimationActivity(@Param("userId") long userId);

    List<ActivityRow> selectCodeActivity(@Param("userId") long userId);

    List<ActivityRow> selectEventActivity(@Param("userId") long userId);

    record ChapterRow(String id, int chapterNumber, String title) {
    }

    record ActivityRow(String chapterId, long activityCount, Timestamp lastAt) {
    }
}
