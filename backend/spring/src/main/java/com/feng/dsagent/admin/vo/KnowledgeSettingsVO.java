package com.feng.dsagent.admin.vo;

import com.feng.dsagent.knowledge.service.KnowledgeSettingsService.MatchingSettings;
import com.feng.dsagent.knowledge.service.KnowledgeSettingsService;

public record KnowledgeSettingsVO(
    double minimumScore,
    int searchLimit,
    double titleWeightLong,
    double titleWeightShort,
    boolean customized,
    MatchingSettings defaults
) {}
