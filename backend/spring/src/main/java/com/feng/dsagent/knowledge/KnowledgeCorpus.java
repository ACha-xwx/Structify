package com.feng.dsagent.knowledge;

import java.util.List;

public record KnowledgeCorpus(List<KnowledgeChunk> chunks, KnowledgeCorpusStats stats) {

    public KnowledgeCorpus {
        chunks = List.copyOf(chunks);
    }

    static KnowledgeCorpus empty() {
        return new KnowledgeCorpus(List.of(), KnowledgeCorpusStats.empty());
    }

    static KnowledgeCorpus empty(List<KnowledgeImportManifest.Rejection> rejections) {
        return new KnowledgeCorpus(List.of(), new KnowledgeCorpusStats(false, 0, 0, rejections));
    }
}
