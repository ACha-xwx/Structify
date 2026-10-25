package com.feng.dsagent.animation.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DsvpEvidenceMapper {

    public Long lockUser(@Param("userId") long userId);

    public List<String> selectRequestHash(@Param("traceId") String traceId);

    public int insertSnapshot(
        @Param("traceId") String traceId,
        @Param("animationRecordId") String animationRecordId,
        @Param("protocol") String protocol,
        @Param("requestJson") String requestJson,
        @Param("requestHash") String requestHash,
        @Param("sourceType") String sourceType,
        @Param("sourceRef") String sourceRef,
        @Param("versionLabel") String versionLabel,
        @Param("reviewStatus") String reviewStatus
    );

    public List<ExistingSnapshotRow> selectExistingSnapshot(@Param("traceId") String traceId);

    public record ExistingSnapshotRow(
        String requestHash,
        String animationRecordId,
        String sourceType,
        String sourceRef,
        String chapterId,
        Long userId
    ) {
    }
}
