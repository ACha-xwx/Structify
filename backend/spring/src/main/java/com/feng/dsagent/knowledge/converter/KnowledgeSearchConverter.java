package com.feng.dsagent.knowledge.converter;

import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;
import com.feng.dsagent.knowledge.vo.KnowledgeSearchResultVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 职责：将知识块实体与检索分转换为对外检索结果 VO。
 */
@Mapper(componentModel = "spring")
public interface KnowledgeSearchConverter {

    @Mapping(target = "score", source = "score")
    KnowledgeSearchResultVO toView(KnowledgeChunkEntity chunk, double score);
}
