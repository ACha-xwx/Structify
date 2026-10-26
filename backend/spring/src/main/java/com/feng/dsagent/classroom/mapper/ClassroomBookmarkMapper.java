package com.feng.dsagent.classroom.mapper;

import com.feng.dsagent.classroom.entity.ClassroomBookmarkEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassroomBookmarkMapper {

    int insert(
        @Param("userId") long userId,
        @Param("sessionId") String sessionId,
        @Param("lessonId") String lessonId,
        @Param("lessonTitle") String lessonTitle,
        @Param("chapterId") String chapterId,
        @Param("stepIndex") int stepIndex,
        @Param("slideId") String slideId,
        @Param("title") String title
    );

    BookmarkRow selectById(@Param("id") long id);

    BookmarkRow selectByStep(
        @Param("userId") long userId,
        @Param("sessionId") String sessionId,
        @Param("stepIndex") int stepIndex
    );

    List<BookmarkRow> selectByUser(@Param("userId") long userId);

    int delete(@Param("userId") long userId, @Param("id") long id);

    default ClassroomBookmarkEntity findById(long id) {
        return toBookmark(selectById(id));
    }

    default ClassroomBookmarkEntity findByStep(long userId, String sessionId, int stepIndex) {
        return toBookmark(selectByStep(userId, sessionId, stepIndex));
    }

    default List<ClassroomBookmarkEntity> listByUser(long userId) {
        return selectByUser(userId).stream().map(ClassroomBookmarkMapper::toBookmark).toList();
    }

    private static ClassroomBookmarkEntity toBookmark(BookmarkRow row) {
        if (row == null) {
            return null;
        }
        return new ClassroomBookmarkEntity(
            row.id(),
            row.sessionId(),
            row.lessonId(),
            row.lessonTitle(),
            row.chapterId(),
            row.stepIndex(),
            row.slideId(),
            row.title(),
            row.createdAt() == null ? null : row.createdAt().toString()
        );
    }

    record BookmarkRow(
        long id,
        String sessionId,
        String lessonId,
        String lessonTitle,
        String chapterId,
        int stepIndex,
        String slideId,
        String title,
        Instant createdAt
    ) {
    }
}
