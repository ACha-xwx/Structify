package com.feng.dsagent.compiler.mapper;

import com.feng.dsagent.compiler.entity.CodeRunSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodeRunMapper {

    int insert(
        @Param("id") String id,
        @Param("userId") long userId,
        @Param("chapterId") String chapterId,
        @Param("language") String language,
        @Param("sourceCode") String sourceCode,
        @Param("stdinText") String stdinText,
        @Param("status") String status,
        @Param("outputText") String outputText,
        @Param("errorText") String errorText,
        @Param("durationMs") Long durationMs
    );

    int countPublishedChapter(@Param("chapterId") String chapterId);

    CodeRunSnapshotEntity selectOwned(@Param("runId") String runId, @Param("userId") long userId);
}
