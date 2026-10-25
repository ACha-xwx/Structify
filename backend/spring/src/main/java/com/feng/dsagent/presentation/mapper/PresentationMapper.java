package com.feng.dsagent.presentation.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PresentationMapper {

    public List<SourceLessonRow> selectTextbookLessons();

    public record SourceLessonRow(String chapterId, String sourcePath, String title) {
    }
}
