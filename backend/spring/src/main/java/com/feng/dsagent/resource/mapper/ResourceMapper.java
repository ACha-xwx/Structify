package com.feng.dsagent.resource.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResourceMapper {

    public List<ChapterRow> selectPublishedChapters();

    public List<ResourceRow> selectPublishedByChapterId(
        @Param("chapterId") String chapterId,
        @Param("allowClassroomOnly") boolean allowClassroomOnly,
        @Param("allowTeamOnly") boolean allowTeamOnly
    );

    public ResourceRow selectPublishedById(
        @Param("id") String id,
        @Param("allowClassroomOnly") boolean allowClassroomOnly,
        @Param("allowTeamOnly") boolean allowTeamOnly
    );

    public record ChapterRow(String id, int chapterNumber, String title, String summary) {
    }

    public record ResourceRow(
        String id,
        String chapterId,
        String resourceType,
        String title,
        String description,
        String filePath,
        String sourceName,
        String versionLabel,
        String reviewStatus,
        String licenseScope
    ) {
    }
}
