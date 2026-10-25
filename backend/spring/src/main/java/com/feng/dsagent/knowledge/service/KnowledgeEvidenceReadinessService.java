package com.feng.dsagent.knowledge.service;

import com.feng.dsagent.knowledge.KnowledgeSearchResult;
import com.feng.dsagent.knowledge.KnowledgeAudience;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import com.feng.dsagent.knowledge.mapper.KnowledgeEvidenceReadinessMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeEvidenceReadinessService {

    private final KnowledgeSearchService search;
    private final KnowledgeEvidenceReadinessMapper evidence;

    public KnowledgeEvidenceReadinessService(KnowledgeSearchService search, KnowledgeEvidenceReadinessMapper evidence) {
        this.search = search;
        this.evidence = evidence;
    }

    public Snapshot snapshot(KnowledgeAudience audience, String chapterId, String prompt) {
        boolean queryScoped = prompt != null && !prompt.isBlank();
        KnowledgeSearchService.EvidenceInventory inventory = queryScoped
            ? matchingInventory(prompt, chapterId, audience)
            : search.inventory(chapterId, audience);
        return new Snapshot(
            inventory.knowledgeChunkCount(),
            inventory.sourceCount(),
            inventory.sourceCount(),
            excludedOrUnverifiedCount(audience, chapterId),
            queryScoped
        );
    }

    private KnowledgeSearchService.EvidenceInventory matchingInventory(
        String prompt,
        String chapterId,
        KnowledgeAudience audience
    ) {
        List<KnowledgeSearchResult> results = search.search(prompt, chapterId, 6, audience);
        long sources = results.stream()
            .map(result -> result.chunk().source() == null ? result.chunk().id() : result.chunk().source())
            .distinct()
            .count();
        return new KnowledgeSearchService.EvidenceInventory(results.size(), Math.toIntExact(sources));
    }

    private int excludedOrUnverifiedCount(KnowledgeAudience audience, String chapterId) {
        Integer count = evidence.countExcludedOrUnverified(audience.name(), chapterId);
        return count == null ? 0 : count;
    }

    public record Snapshot(
        int availableKnowledgeChunkCount,
        int availableResourceCount,
        int availableSourceCount,
        int excludedOrUnverifiedCount,
        boolean queryScoped
    ) {

        public boolean evidenceAvailable() {
            return availableKnowledgeChunkCount > 0;
        }
    }
}
