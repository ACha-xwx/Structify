package com.feng.dsagent.admin.converter;

import com.feng.dsagent.admin.vo.KnowledgeSettingsVO;
import com.feng.dsagent.knowledge.service.KnowledgeSettingsService.MatchingSettings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将检索匹配设置与定制状态转换为对外知识库设置 VO。
 */
@Mapper(componentModel = "spring")
public interface KnowledgeSettingsConverter {

    @Mapping(target = "minimumScore", source = "current.minimumScore")
    @Mapping(target = "searchLimit", source = "current.searchLimit")
    @Mapping(target = "titleWeightLong", source = "current.titleWeightLong")
    @Mapping(target = "titleWeightShort", source = "current.titleWeightShort")
    @Mapping(target = "customized", source = "customized")
    @Mapping(target = "defaults", source = "defaults")
    KnowledgeSettingsVO toView(MatchingSettings current, boolean customized, MatchingSettings defaults);
}
