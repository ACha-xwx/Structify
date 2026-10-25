package com.feng.dsagent.studyroom.fragment.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FragmentLearningMapper {
    public Long lockUser(@Param("userId") long userId);
    public Map<String, Object> selectOwnedRun(@Param("runId") String runId, @Param("userId") long userId);
    public Map<String, Object> selectRoomCurrentRun(@Param("roomId") Object roomId, @Param("runId") String runId);
    public Map<String, Object> selectOwnedFragment(@Param("fragmentId") String fragmentId, @Param("runId") String runId, @Param("lock") boolean lock);
    public Map<String, Object> findIdempotency(@Param("userId") long userId, @Param("operation") String operation, @Param("key") String key);
    public int saveIdempotency(@Param("userId") long userId, @Param("operation") String operation, @Param("key") String key,
        @Param("payloadHash") String payloadHash, @Param("resultId") String resultId);
    public int countRemainingFragments(@Param("unitId") String unitId, @Param("fragmentOrder") Object fragmentOrder);
    public Map<String, Object> selectCompletedKnowledge(@Param("userId") long userId, @Param("runId") String runId, @Param("unitId") String unitId);
    public int insertCompletedKnowledge(@Param("userId") long userId, @Param("runId") String runId, @Param("unitId") String unitId);
    public Map<String, Object> selectLearningProgressForUpdate(@Param("userId") long userId, @Param("runId") String runId);
    public int insertLearningProgress(@Param("userId") long userId, @Param("runId") String runId, @Param("unitId") String unitId,
        @Param("fragmentId") String fragmentId, @Param("completed") boolean completed);
    public int updateLearningProgress(@Param("unitId") String unitId, @Param("fragmentId") String fragmentId,
        @Param("completed") boolean completed, @Param("userId") long userId, @Param("runId") String runId);
    public int updateRoomLearning(@Param("stepId") Object stepId, @Param("runId") String runId, @Param("userId") long userId);
    public int touchRun(@Param("runId") String runId);
    public Map<String, Object> selectActiveFragmentJob(@Param("fragmentId") String fragmentId);
    public Integer nextFragmentRevision(@Param("fragmentId") String fragmentId);
    public int insertFragmentVersion(@Param("fragmentId") String fragmentId, @Param("revision") int revision);
    public Map<String, Object> selectFragmentVersionForUpdate(@Param("fragmentId") String fragmentId, @Param("revision") int revision);
    public Map<String, Object> selectExistingJobKey(@Param("jobKey") String jobKey);
    public int insertGenerationJob(@Param("id") String id, @Param("userId") long userId, @Param("draftId") String draftId,
        @Param("runId") String runId, @Param("unitId") String unitId, @Param("jobType") String jobType,
        @Param("jobKey") String jobKey, @Param("payloadJson") String payloadJson);
    public int requeueFragmentJob(@Param("fragmentId") String fragmentId, @Param("jobId") String jobId);
    public int queueFragment(@Param("revision") int revision, @Param("fragmentId") String fragmentId);
}
