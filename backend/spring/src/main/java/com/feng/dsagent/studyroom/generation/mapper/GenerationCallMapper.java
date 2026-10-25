package com.feng.dsagent.studyroom.generation.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationCallMapper {
    public Long lockUser(@Param("userId") long userId);
    public Map<String, Object> selectJobForUpdate(@Param("jobId") String jobId, @Param("userId") long userId);
    public Map<String, Object> selectJob(@Param("jobId") Object jobId);
    public Map<String, Object> selectJobStatus(@Param("jobId") String jobId, @Param("userId") long userId);
    public int countCurrentRun(@Param("runId") Object runId, @Param("userId") long userId);
    public Map<String, Object> selectPriorCall(@Param("jobId") String jobId, @Param("attempt") Object attempt, @Param("phase") String phase);
    public Map<String, Object> selectCall(@Param("callId") String callId);
    public int insertSentCall(@Param("id") String id, @Param("jobId") String jobId, @Param("attempt") Object attempt,
        @Param("phase") String phase, @Param("quotaRequestId") String quotaRequestId);
    public int updateCallContext(@Param("contextJson") String contextJson, @Param("callId") String callId);
    public int markCallSucceeded(@Param("responseJson") String responseJson, @Param("callId") Object callId);
    public int markCallFailed(@Param("callId") Object callId);
    public List<Map<String, Object>> selectCallsByJob(@Param("jobId") Object jobId);
    public int updateCallIssues(@Param("issuesJson") String issuesJson, @Param("jobId") String jobId,
        @Param("phase") String phase, @Param("userId") long userId);
    public Map<String, Object> selectFragmentVersionForUpdate(@Param("fragmentId") String fragmentId, @Param("revision") int revision);
    public int reserveFragmentBudget(@Param("tokens") long tokens, @Param("fragmentId") String fragmentId, @Param("revision") int revision);
    public int updateCallUsage(@Param("usageJson") String usageJson, @Param("elapsedMs") long elapsedMs, @Param("callId") Object callId);
    public int adjustFragmentUsage(@Param("tokenDelta") long tokenDelta, @Param("elapsedMs") long elapsedMs,
        @Param("fragmentId") Object fragmentId, @Param("revision") int revision);
    public Map<String, Object> selectActiveJob(@Param("jobId") Object jobId, @Param("leaseToken") Object leaseToken);
}
