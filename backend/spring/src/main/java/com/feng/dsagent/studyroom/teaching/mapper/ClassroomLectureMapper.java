package com.feng.dsagent.studyroom.teaching.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassroomLectureMapper {
    public Long lockUser(@Param("userId") long userId);

    public Map<String, Object> selectOwnedRun(@Param("runId") String runId, @Param("userId") long userId);

    public Map<String, Object> selectRoomCurrentRun(@Param("roomId") Object roomId, @Param("runId") String runId);

    public Map<String, Object> selectOwnedFragment(
            @Param("fragmentId") String fragmentId, @Param("runId") String runId);

    public List<String> selectRegionJson(
            @Param("fragmentId") String fragmentId, @Param("revision") int revision);

    public Map<String, Object> selectLaterFragment(
            @Param("unitId") Object unitId, @Param("fragmentOrder") Object fragmentOrder);

    public Map<String, Object> selectRegionById(@Param("id") String id);

    public int insertRegion(
            @Param("id") String id,
            @Param("userId") long userId,
            @Param("runId") String runId,
            @Param("fragmentId") String fragmentId,
            @Param("revision") int revision,
            @Param("sceneId") String sceneId,
            @Param("resourceId") String resourceId,
            @Param("regionJson") String regionJson);

    public int softDeleteRegion(
            @Param("id") String id,
            @Param("userId") long userId,
            @Param("runId") String runId,
            @Param("fragmentId") String fragmentId,
            @Param("revision") int revision);
}
