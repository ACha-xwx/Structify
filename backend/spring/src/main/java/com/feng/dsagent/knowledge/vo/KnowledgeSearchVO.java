package com.feng.dsagent.knowledge.vo;

import java.util.List;

public record KnowledgeSearchVO(boolean ok, String query, List<KnowledgeResultVO> results) {}
