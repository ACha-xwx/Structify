package com.feng.dsagent.knowledge.support;

import com.feng.dsagent.knowledge.domain.KnowledgeCorpusStats;
import com.feng.dsagent.knowledge.domain.KnowledgeImportManifest;
import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;
import java.util.List;

public record KnowledgeCorpus(List<KnowledgeChunkEntity> chunks, KnowledgeCorpusStats stats) {

    public KnowledgeCorpus {
        chunks = List.copyOf(chunks);
    }

    public static KnowledgeCorpus empty() {
        return new KnowledgeCorpus(List.of(), KnowledgeCorpusStats.empty());
    }

    public static KnowledgeCorpus empty(List<KnowledgeImportManifest.Rejection> rejections) {
        return new KnowledgeCorpus(List.of(), new KnowledgeCorpusStats(false, 0, 0, rejections));
    }
}
