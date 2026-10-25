package com.feng.dsagent.animation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChapterLookupMapper {

    public String findPublishedTitle(@Param("id") String id);
}
