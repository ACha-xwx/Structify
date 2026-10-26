package com.feng.dsagent.classroom.mapper;

import com.feng.dsagent.classroom.domain.ClassroomAskExchange;
import com.feng.dsagent.classroom.domain.ClassroomScript;
import com.feng.dsagent.classroom.domain.ClassroomStepAttempts;
import com.feng.dsagent.classroom.entity.ClassroomSessionRecordEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassroomMapper {

    List<ClassroomScript> findPublishedScripts();

    List<ClassroomScript> findPublishedScriptsByChapter(@Param("chapterId") String chapterId);

    ClassroomScript findPublishedScript(@Param("id") String id);

    int insertSession(
        @Param("id") String id,
        @Param("userId") long userId,
        @Param("scriptId") String scriptId,
        @Param("state") String state,
        @Param("paused") boolean paused,
        @Param("scriptJson") String scriptJson,
        @Param("chapterId") String chapterId
    );

    ClassroomSessionRecordEntity findSession(@Param("id") String id, @Param("userId") long userId);

    int updateSession(
        @Param("id") String id,
        @Param("userId") long userId,
        @Param("state") String state,
        @Param("paused") boolean paused,
        @Param("summary") String summary
    );

    int insertAsk(
        @Param("sessionId") String sessionId,
        @Param("stepIndex") int stepIndex,
        @Param("question") String question,
        @Param("answer") String answer
    );

    List<AskRow> selectAsks(@Param("sessionId") String sessionId);

    default List<ClassroomAskExchange> asksOf(String sessionId) {
        return selectAsks(sessionId).stream()
            .map(row -> new ClassroomAskExchange(
                row.stepIndex(),
                row.question(),
                row.answer(),
                row.createdAt() == null ? null : row.createdAt().toString()
            ))
            .toList();
    }

    ClassroomStepAttempts attemptsAt(@Param("sessionId") String sessionId, @Param("stepIndex") int stepIndex);

    record AskRow(int stepIndex, String question, String answer, Instant createdAt) {
    }

    int insertEvent(
        @Param("sessionId") String sessionId,
        @Param("action") String action,
        @Param("content") String content,
        @Param("fromState") String fromState,
        @Param("toState") String toState,
        @Param("answerStatus") String answerStatus,
        @Param("misconception") String misconception,
        @Param("feedback") String feedback,
        @Param("stepIndex") int stepIndex,
        @Param("questionId") String questionId,
        @Param("attemptNo") int attemptNo,
        @Param("hintUsed") boolean hintUsed
    );

    List<VerifiedLessonSourceRow> selectVerifiedLessonSources();

    List<PageLabelRow> selectVerifiedPageLabels();

    List<Map<String, Object>> selectVerifiedChunks(
        @Param("sourcePath") String sourcePath,
        @Param("chapterId") String chapterId
    );

    int insertDraftScript(
        @Param("id") String id,
        @Param("chapterId") String chapterId,
        @Param("title") String title,
        @Param("scriptJson") String scriptJson
    );

    RuntimeCursorRow selectRuntimeCursor(@Param("id") String id);

    int updateRuntime(
        @Param("index") int index,
        @Param("response") String response,
        @Param("id") String id,
        @Param("userId") long userId,
        @Param("revision") int revision
    );

    int countEventsByAction(@Param("sessionId") String sessionId, @Param("action") String action);

    String selectSlideOverride(@Param("scriptId") String scriptId, @Param("stepIndex") int stepIndex);

    int upsertSlideOverride(
        @Param("scriptId") String scriptId,
        @Param("stepIndex") int stepIndex,
        @Param("slideId") String slideId,
        @Param("updatedBy") long updatedBy
    );

    record VerifiedLessonSourceRow(String chapterId, String sourcePath, String title) {
    }

    record PageLabelRow(String sourcePath, String pageLabel) {
    }

    record RuntimeCursorRow(int runtimeIndex, int runtimeRevision, String runtimeResponse) {
    }
}
