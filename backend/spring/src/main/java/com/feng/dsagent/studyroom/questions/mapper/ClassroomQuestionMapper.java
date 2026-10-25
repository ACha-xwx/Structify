package com.feng.dsagent.studyroom.questions.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassroomQuestionMapper {
    public Map<String, Object> selectOwned(@Param("questionId") String questionId, @Param("roomId") String roomId,
        @Param("userId") long userId, @Param("lock") boolean lock);
    public Map<String, Object> selectFragmentSource(@Param("fragmentId") String fragmentId, @Param("runId") String runId,
        @Param("roomId") String roomId, @Param("userId") long userId);
    public Map<String, Object> selectActiveQuestion(@Param("userId") long userId, @Param("runId") String runId);
    public int insertQuestion(@Param("id") String id, @Param("userId") long userId, @Param("roomId") String roomId,
        @Param("runId") String runId, @Param("fragmentId") String fragmentId, @Param("revision") int revision,
        @Param("question") String question, @Param("contextJson") String contextJson, @Param("audience") String audience,
        @Param("assistantMessageId") String assistantMessageId, @Param("userMessageId") String userMessageId);
    public int setJobPriority(@Param("jobId") String jobId, @Param("priority") int priority);
    public int setQuestionJob(@Param("jobId") String jobId, @Param("questionId") String questionId);
    public Map<String, Object> selectActiveQuestionInRoom(@Param("userId") long userId, @Param("roomId") String roomId);
    public List<Map<String, Object>> selectStoppedQuestions();
    public Map<String, Object> selectJobStatus(@Param("jobId") Object jobId);
    public int updateQuestionFromJob(@Param("status") Object status, @Param("failureCode") Object failureCode,
        @Param("failureMessage") Object failureMessage, @Param("questionId") String questionId);
    public Map<String, Object> selectJobForUpdate(@Param("jobId") Object jobId);
    public int cancelQuestion(@Param("questionId") String questionId);
    public int cancelJob(@Param("jobId") Object jobId);
}
