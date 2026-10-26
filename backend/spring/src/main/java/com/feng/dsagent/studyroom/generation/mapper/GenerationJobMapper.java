package com.feng.dsagent.studyroom.generation.mapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationJobMapper {
    Long lockUser(@Param("userId") long userId);

    Map<String, Object> selectJobByKey(@Param("jobKey") String jobKey);

    int insertJob(
            @Param("id") String id,
            @Param("userId") long userId,
            @Param("draftId") String draftId,
            @Param("runId") String runId,
            @Param("unitId") String unitId,
            @Param("jobType") String jobType,
            @Param("jobKey") String jobKey,
            @Param("payloadJson") String payloadJson);

    List<Map<String, Object>> selectClaimCandidates();

    int countRunningForUser(@Param("userId") long userId);

    int claimQueued(
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Timestamp leaseUntil,
            @Param("heartbeatAt") Timestamp heartbeatAt,
            @Param("updatedAt") Timestamp updatedAt,
            @Param("jobId") Object jobId);

    Map<String, Object> selectJob(@Param("jobId") Object jobId);

    Map<String, Object> selectJobForUpdate(@Param("jobId") Object jobId);

    int countActiveLease(@Param("jobId") Object jobId, @Param("leaseToken") Object leaseToken);

    int heartbeat(
            @Param("leaseUntil") Timestamp leaseUntil,
            @Param("jobId") Object jobId,
            @Param("leaseToken") Object leaseToken);

    int finish(@Param("jobId") Object jobId, @Param("leaseToken") Object leaseToken);

    int failJob(
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("jobId") Object jobId,
            @Param("leaseToken") Object leaseToken);

    int failFragment(
            @Param("errorCode") String errorCode,
            @Param("issuesJson") String issuesJson,
            @Param("fragmentId") Object fragmentId);

    int touchRun(@Param("runId") Object runId);

    int failUnit(
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("unitId") Object unitId);

    List<Map<String, Object>> selectExpiredRunning();

    int countSentCalls(@Param("jobId") Object jobId);

    int requeueJob(@Param("jobId") Object jobId);

    int markCallsUncertain(@Param("jobId") Object jobId);

    int failJobUncertain(@Param("jobId") Object jobId);

    int failFragmentUncertain(@Param("issuesJson") String issuesJson, @Param("fragmentId") Object fragmentId);

    int failUnitUncertain(@Param("unitId") Object unitId);

    Map<String, Object> selectDraftPendingJob(@Param("draftId") Object draftId, @Param("jobId") Object jobId);

    Map<String, Object> selectRevision(@Param("draftId") String draftId, @Param("revision") int revision);

    int restoreDraft(
            @Param("depth") String depth,
            @Param("status") String status,
            @Param("draftId") Object draftId,
            @Param("jobId") Object jobId);

    Map<String, Object> selectFragmentForGeneration(@Param("fragmentId") Object fragmentId);

    int markFragmentGenerating(@Param("fragmentId") Object fragmentId);

    int updateJobPayload(@Param("payloadJson") String payloadJson, @Param("jobId") Object jobId);

    Map<String, Object> selectOwnedDraft(
            @Param("draftId") String draftId, @Param("userId") long userId, @Param("lock") boolean lock);

    Map<String, Object> selectJobByUser(@Param("jobId") String jobId, @Param("userId") long userId);
}
