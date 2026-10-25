package com.feng.dsagent.knowledge.mapper;

import com.feng.dsagent.knowledge.service.KnowledgeSettingsService.MatchingSettings;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeSettingsMapper {

    public List<Integer> selectSettingsId();

    public List<MatchingSettings> selectMatchingSettings();

    public int deleteSettings();

    public int insertSettings(
        @Param("minimumScore") double minimumScore,
        @Param("searchLimit") int searchLimit,
        @Param("titleWeightLong") double titleWeightLong,
        @Param("titleWeightShort") double titleWeightShort,
        @Param("editorUserId") long editorUserId
    );
}
