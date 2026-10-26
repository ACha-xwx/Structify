package com.feng.dsagent.studyroom.draft.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutlineMapper {
    public Long lockUser(@Param("userId") long userId);
    public Map<String, Object> selectOwnedDraft(@Param("draftId") String draftId, @Param("userId") long userId,
        @Param("lock") boolean lock);
    public Map<String, Object> selectRevision(@Param("draftId") String draftId, @Param("revision") int revision);
    public Map<String, Object> findIdempotency(@Param("userId") long userId, @Param("operation") String operation,
        @Param("key") String key);
    public int saveIdempotency(@Param("userId") long userId, @Param("operation") String operation,
        @Param("key") String key, @Param("payloadHash") String payloadHash, @Param("resultId") String resultId);
    public Integer nextMessageOrder(@Param("draftId") String draftId);
    public int insertMessage(@Param("id") String id, @Param("draftId") String draftId, @Param("messageOrder") int messageOrder,
        @Param("role") String role, @Param("content") String content, @Param("revision") int revision);
    public Map<String, Object> selectOwnedRoom(@Param("roomId") String roomId, @Param("userId") long userId,
        @Param("lock") boolean lock);
    public int insertDraft(@Param("id") String id, @Param("userId") long userId, @Param("question") String question,
        @Param("chapterId") String chapterId, @Param("depth") String depth, @Param("sourceRoomId") String sourceRoomId,
        @Param("sourceRunId") Object sourceRunId);
    public int setPendingJob(@Param("jobId") String jobId, @Param("draftId") String draftId);
    public int setPendingJobGenerating(@Param("depth") String depth, @Param("jobId") String jobId, @Param("draftId") String draftId);
    public int touchActivity(@Param("draftId") String draftId);
    public List<Map<String, Object>> selectMessages(@Param("draftId") String draftId);
    public List<Map<String, Object>> selectRevisionSummaries(@Param("draftId") String draftId);
    public Map<String, Object> selectLatestGeneration(@Param("draftId") String draftId);
    public int insertRevision(@Param("id") String id, @Param("draftId") String draftId, @Param("revision") int revision,
        @Param("baseRevision") int baseRevision, @Param("outlineJson") String outlineJson, @Param("issuesJson") String issuesJson,
        @Param("changeSummaryJson") String changeSummaryJson, @Param("feedback") Object feedback,
        @Param("curriculumVersion") Object curriculumVersion);
    public int setReviewable(@Param("revision") int revision, @Param("draftId") String draftId);
    public int setReviewableClearPending(@Param("revision") int revision, @Param("draftId") String draftId);
    public int setReviewableRestore(@Param("revision") int revision, @Param("depth") String depth, @Param("draftId") String draftId);
    public int insertRoom(@Param("id") String id, @Param("userId") long userId, @Param("chapterId") Object chapterId,
        @Param("title") String title, @Param("question") Object question, @Param("summary") String summary);
    public int copyDisplayTitle(@Param("title") Object title, @Param("draftId") String draftId);
    public Integer nextRunRevision(@Param("roomId") String roomId);
    public int insertRun(@Param("id") String id, @Param("roomId") String roomId, @Param("userId") long userId,
        @Param("draftId") String draftId, @Param("outlineRevision") int outlineRevision, @Param("runRevision") int runRevision,
        @Param("outlineJson") String outlineJson);
    public int insertUnit(@Param("id") String id, @Param("runId") String runId, @Param("unitKey") String unitKey,
        @Param("unitOrder") int unitOrder, @Param("specJson") String specJson, @Param("contentHash") String contentHash);
    public int cancelQueuedJobsForRoom(@Param("roomId") String roomId, @Param("runId") String runId);
    public int supersedeRuns(@Param("roomId") String roomId, @Param("runId") String runId);
    public int setCurrentRun(@Param("runId") String runId, @Param("title") String title, @Param("roomId") String roomId);
    public int confirmDraft(@Param("runId") String runId, @Param("roomId") String roomId, @Param("draftId") String draftId);
    public Map<String, Object> selectRoomId(@Param("runId") String runId);
    public Map<String, Object> selectFirstUnit(@Param("runId") String runId);
    public Map<String, Object> selectFailedJob(@Param("draftId") String draftId);
    public Map<String, Object> selectRevisionById(@Param("revisionId") String revisionId);
    public Map<String, Object> selectRoomForUpdate(@Param("roomId") String roomId, @Param("userId") long userId);
    public Map<String, Object> selectChapter(@Param("chapterId") String chapterId);
    public Map<String, Object> selectPreviousDisplayTitle(@Param("runId") Object runId, @Param("userId") long userId);
    public Map<String, Object> selectConfirmedRoom(@Param("runId") Object runId, @Param("userId") long userId);
}
