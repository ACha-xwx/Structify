package com.feng.dsagent.learning.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningMapMapper {

    public List<ChapterRow> selectChapters();

    public List<ClassroomEventRow> selectClassroomEvents(@Param("userId") long userId);

    public List<ChunkPathRow> selectLessonChunkPaths();

    public List<SessionScriptRow> selectSessionScripts(@Param("userId") long userId);

    public List<AnimationRow> selectAnimations(@Param("userId") long userId);

    public List<CodeRunRow> selectCodeRuns(@Param("userId") long userId);

    public List<ChatMessageRow> selectChatMessages(@Param("userId") long userId);

    public List<LessonChunkRow> selectLessonChunksByChapter(@Param("chapterId") String chapterId);

    public record ChapterRow(String id, String title) {
    }

    public record ClassroomEventRow(
        String chapterId,
        String action,
        String answerStatus,
        Integer attemptNo,
        Boolean hintUsed,
        Timestamp createdAt,
        String sessionId,
        Integer stepIndex
    ) {
    }

    public record ChunkPathRow(String id, String sourcePath) {
    }

    public record SessionScriptRow(String sessionId, String scriptJson) {
    }

    public record AnimationRow(String chapterId, String title, String payloadJson, Timestamp createdAt) {
    }

    public record CodeRunRow(String chapterId, String sourceCode, String status, Timestamp createdAt) {
    }

    public record ChatMessageRow(String chapterId, String content) {
    }

    public record LessonChunkRow(String title, String sourcePath) {
    }
}
