package com.feng.dsagent.studyroom.library.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CourseLibraryMapper {

    public Long lockUser(@Param("userId") long userId);

    public List<Map<String, Object>> selectLibraryDrafts(@Param("userId") long userId);

    public Map<String, Object> selectRoomByCurrentRun(@Param("runId") String runId, @Param("userId") long userId);

    public int countUnits(@Param("runId") String runId);

    public int countCompletedKnowledge(@Param("runId") String runId, @Param("userId") long userId);

    public int countReadableFragments(@Param("runId") String runId);

    public int countFailedFragments(@Param("runId") String runId);

    public Map<String, Object> selectLearningProgress(@Param("userId") long userId, @Param("runId") String runId);

    public Map<String, Object> selectRevision(@Param("draftId") String draftId, @Param("revision") int revision);

    public Map<String, Object> selectOwnedDraft(@Param("userId") long userId, @Param("draftId") String draftId);

    public Map<String, Object> selectDraftForUpdate(@Param("userId") long userId, @Param("draftId") String draftId);

    public int updateDisplayTitle(
            @Param("title") String title, @Param("draftId") String draftId, @Param("userId") long userId);

    public int cancelJobsByDraftId(@Param("draftId") String draftId);

    public int cancelJobsBySourceRoom(@Param("roomId") String roomId);

    public int softDeleteDraft(@Param("draftId") String draftId);

    public int cancelRunsByRoom(@Param("roomId") String roomId, @Param("userId") long userId);

    public int softDeleteDraftsBySourceRoom(@Param("roomId") String roomId, @Param("userId") long userId);

    public int softDeleteRoom(@Param("roomId") String roomId, @Param("userId") long userId);
}
