package com.feng.dsagent.studyroom.fragment.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FragmentPublicationMapper {
    public Long lockUser(@Param("userId") long userId);
    public Map<String, Object> selectOwnedRun(@Param("runId") String runId, @Param("userId") long userId);
    public Map<String, Object> selectRoomCurrentRun(@Param("roomId") Object roomId, @Param("runId") String runId);
    public Map<String, Object> selectRoomForUpdate(@Param("roomId") Object roomId);
    public Map<String, Object> selectOwnedFragment(@Param("fragmentId") String fragmentId, @Param("runId") String runId);
    public Map<String, Object> selectJobActive(@Param("jobId") Object jobId, @Param("leaseToken") Object leaseToken);
    public Map<String, Object> selectUnitOrder(@Param("unitId") Object unitId);
    public int insertStep(@Param("id") String id, @Param("roomId") Object roomId, @Param("stepKey") String stepKey,
        @Param("stepType") String stepType, @Param("title") String title, @Param("explanation") String explanation,
        @Param("evidenceJson") String evidenceJson, @Param("resourcesJson") String resourcesJson,
        @Param("stepOrder") int stepOrder, @Param("orchestrationJson") String orchestrationJson,
        @Param("unitId") Object unitId, @Param("runId") Object runId, @Param("runScope") Object runScope,
        @Param("stepKeyInUnit") String stepKeyInUnit);
    public int updateStep(@Param("stepType") String stepType, @Param("title") String title,
        @Param("explanation") String explanation, @Param("evidenceJson") String evidenceJson,
        @Param("resourcesJson") String resourcesJson, @Param("orchestrationJson") String orchestrationJson,
        @Param("stepId") String stepId, @Param("runId") Object runId);
    public int updateFragmentVersionReady(@Param("contentJson") String contentJson,
        @Param("resourceRevision") String resourceRevision, @Param("fragmentId") Object fragmentId,
        @Param("revision") int revision);
    public int updateFragmentReady(@Param("revision") int revision, @Param("stepId") String stepId,
        @Param("contentJson") String contentJson, @Param("resourceRevision") String resourceRevision,
        @Param("fragmentId") Object fragmentId);
    public int activateRun(@Param("runId") Object runId);
    public int bumpRoom(@Param("stepId") String stepId, @Param("roomId") Object roomId);
    public int touchRun(@Param("runId") Object runId);
    public int finishJob(@Param("jobId") Object jobId, @Param("leaseToken") Object leaseToken);
}
