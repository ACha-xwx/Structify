package com.feng.dsagent.studyroom.fragment.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FragmentReadMapper {

    public Map<String, Object> selectCurrentRunId(@Param("roomId") String roomId, @Param("userId") long userId);

    public Map<String, Object> selectOwnedRun(@Param("runId") String runId, @Param("userId") long userId);

    public List<Map<String, Object>> selectUnits(@Param("runId") String runId);

    public List<Map<String, Object>> selectFragmentsByUnit(@Param("unitId") String unitId);

    public Map<String, Object> selectCompletedKnowledge(
            @Param("userId") long userId, @Param("runId") String runId, @Param("unitId") String unitId);

    public Map<String, Object> selectLearningProgress(@Param("userId") long userId, @Param("runId") String runId);
}
