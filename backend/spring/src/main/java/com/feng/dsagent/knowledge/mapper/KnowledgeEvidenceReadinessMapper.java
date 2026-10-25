package com.feng.dsagent.knowledge.mapper;

import com.feng.dsagent.knowledge.KnowledgeAudience;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeEvidenceReadinessMapper {

    /** {@code audience} must be a {@link com.feng.dsagent.knowledge.KnowledgeAudience} name from server-side code. */
    public Integer countExcludedOrUnverified(
        @Param("audience") String audience,
        @Param("chapterId") String chapterId
    );
}
