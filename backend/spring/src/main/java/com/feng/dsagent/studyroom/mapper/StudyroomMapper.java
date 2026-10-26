package com.feng.dsagent.studyroom.mapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudyroomMapper {
    String selectCurrentRunId(@Param("roomId") String roomId, @Param("userId") long userId);

    Map<String, Object> selectOwnedRoomForUpdate(@Param("roomId") String roomId, @Param("userId") long userId);

    Map<String, Object> selectOwnedRoom(@Param("roomId") String roomId, @Param("userId") long userId);

    List<Map<String, Object>> selectCurrentSteps(@Param("roomId") String roomId);

    List<Map<String, Object>> selectCurrentMessages(@Param("roomId") String roomId);

    int countCurrentStep(@Param("roomId") String roomId, @Param("stepId") String stepId);

    int bumpRoomVersion(@Param("roomId") String roomId);

    Integer nextMessageOrder(@Param("roomId") String roomId);

    int insertMessage(
            @Param("id") String id,
            @Param("roomId") String roomId,
            @Param("messageOrder") int messageOrder,
            @Param("role") String role,
            @Param("content") String content,
            @Param("sourcesJson") String sourcesJson,
            @Param("stepId") String stepId,
            @Param("createdAt") Timestamp createdAt);

    int clearProgress(@Param("roomId") String roomId, @Param("userId") long userId);

    int updateProgress(
            @Param("stepId") String stepId,
            @Param("roomId") String roomId,
            @Param("userId") long userId);

    /** Verified textbook/courseware source paths for a published chapter (PPT candidate binding). */
    List<String> selectVerifiedCoursewareSourcePaths(@Param("chapterId") String chapterId);
}
