package com.feng.dsagent.animation.mapper;

import com.feng.dsagent.animation.entity.AnimationRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnimationMapper {

    int countPublishedChapter(@Param("chapterId") String chapterId);

    int insertRecord(
        @Param("id") String id,
        @Param("userId") long userId,
        @Param("chapterId") String chapterId,
        @Param("animationType") String animationType,
        @Param("title") String title,
        @Param("payloadJson") String payloadJson
    );

    AnimationRecordEntity findOwned(@Param("userId") long userId, @Param("recordId") String recordId);

    int insertObservation(
        @Param("recordId") String recordId,
        @Param("userId") long userId,
        @Param("observation") String observation,
        @Param("sourceType") String sourceType,
        @Param("sourceRef") String sourceRef,
        @Param("versionLabel") String versionLabel
    );

    int insertObservationSimple(
        @Param("recordId") String recordId,
        @Param("userId") long userId,
        @Param("observation") String observation
    );

    int updateRecordObservation(
        @Param("recordId") String recordId,
        @Param("userId") long userId,
        @Param("observation") String observation
    );
}
