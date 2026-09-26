package com.feng.dsagent.knowledge;

import java.util.List;

/**
 * Loading outcome for the local reviewed textbook directory.
 *
 * <p>{@code rejections} explains every candidate lesson file that stayed unpublished, so a mistyped
 * hash or a missing manifest entry is visible instead of silently reducing the corpus.
 */
public record KnowledgeCorpusStats(
    boolean available,
    int lessonFiles,
    int chunkCount,
    List<KnowledgeImportManifest.Rejection> rejections
) {

    public KnowledgeCorpusStats {
        rejections = List.copyOf(rejections);
    }

    static KnowledgeCorpusStats empty() {
        return new KnowledgeCorpusStats(false, 0, 0, List.of());
    }
}
