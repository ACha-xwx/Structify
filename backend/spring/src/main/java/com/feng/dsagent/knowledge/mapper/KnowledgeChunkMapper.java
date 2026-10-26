package com.feng.dsagent.knowledge.mapper;

import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeChunkMapper {

    int deleteBySourcePrefix(@Param("prefix") String prefix);

    int insertChunks(@Param("chunks") Collection<KnowledgeChunkEntity> chunks);

    List<KnowledgeChunkEntity> selectPublished();

    List<String> selectEligibleIds(
        @Param("chunkIds") Collection<String> chunkIds,
        @Param("licenseScopes") Collection<String> licenseScopes
    );
}
