package com.feng.dsagent.learning.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningWorkbenchMapper {

    List<PersistedSceneCandidate> findLatestOwnedDsvpScenes(
        @Param("userId") long userId,
        @Param("limit") int limit
    );

    record PersistedSceneCandidate(
        String recordId,
        String chapterId,
        String payloadJson,
        Instant updatedAt
    ) {
    }
}
