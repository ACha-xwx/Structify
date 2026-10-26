package com.feng.dsagent.studyroom.checks.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassroomCheckMapper {
    public Map<String, Object> selectLastFragment(@Param("runId") String runId, @Param("unitId") String unitId, @Param("userId") long userId);
    public Map<String, Object> selectJob(@Param("jobId") String jobId);
    public Map<String, Object> selectUnitSpec(@Param("unitId") String unitId);
    public Map<String, Object> selectCheckSet(@Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public int insertCheckSetPending(@Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public int failCheckSet(@Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public int insertCheck(@Param("id") String id, @Param("userId") long userId, @Param("runId") Object runId,
        @Param("unitId") Object unitId, @Param("fragmentId") Object fragmentId, @Param("revision") Object revision,
        @Param("generationId") String generationId, @Param("order") int order, @Param("prompt") String prompt,
        @Param("optionsJson") String optionsJson, @Param("answerOption") String answerOption, @Param("analysis") String analysis);
    public int readyCheckSet(@Param("generationId") String generationId, @Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public List<Map<String, Object>> selectChecks(@Param("fragmentId") Object fragmentId, @Param("revision") Object revision, @Param("generationId") Object generationId);
    public List<Map<String, Object>> selectAttempts(@Param("checkId") Object checkId, @Param("userId") long userId);
    public Map<String, Object> selectCheck(@Param("checkId") String checkId, @Param("runId") String runId, @Param("userId") long userId);
    public Map<String, Object> selectGenerationId(@Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public Map<String, Object> selectAttempt(@Param("id") String id);
    public int insertAttempt(@Param("id") String id, @Param("checkId") String checkId, @Param("userId") long userId,
        @Param("selected") String selected, @Param("correct") boolean correct);
    public List<Map<String, Object>> selectActiveCheckJobs(@Param("runId") String runId);
    public int setJobPriority(@Param("jobId") String jobId, @Param("priority") int priority);
    public int resetCheckSetPending(@Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public int setPendingJob(@Param("jobId") String jobId, @Param("fragmentId") Object fragmentId, @Param("revision") Object revision);
    public List<String> selectLessonContent(@Param("runId") Object runId, @Param("unitId") String unitId);
    public int reconcileFailedPreparation();
}
