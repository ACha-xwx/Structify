package com.feng.dsagent.knowledge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class KnowledgeSearchService {

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
        "忽略(以上|之前|所有).{0,12}(指令|规则)|系统提示词|开发者消息|泄露.{0,8}提示词|执行以下命令|system\\s*prompt",
        Pattern.CASE_INSENSITIVE
    );
    private final AtomicReference<List<KnowledgeChunk>> chunks;
    private final double minimumScore;
    private final EligibilityFilter eligibilityFilter;

    public KnowledgeSearchService(Collection<KnowledgeChunk> chunks, double minimumScore) {
        this(chunks, minimumScore, (candidates, audience) -> candidates.stream()
            .map(KnowledgeChunk::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    KnowledgeSearchService(
        Collection<KnowledgeChunk> chunks,
        double minimumScore,
        EligibilityFilter eligibilityFilter
    ) {
        this.chunks = new AtomicReference<>(List.copyOf(chunks));
        this.minimumScore = minimumScore;
        this.eligibilityFilter = eligibilityFilter;
    }

    public void replace(Collection<KnowledgeChunk> replacement) {
        chunks.set(List.copyOf(replacement));
    }

    /**
     * Returns only the chunks that the active retrieval path can use before a question is supplied.
     * This keeps readiness reporting aligned with {@link #search(String, String, int, KnowledgeAudience)}.
     */
    public EvidenceInventory inventory(String chapterId, KnowledgeAudience audience) {
        List<KnowledgeChunk> snapshot = chunks.get();
        Set<String> eligibleIds = eligibleIds(snapshot, audience);
        int chunkCount = 0;
        Set<String> sources = new LinkedHashSet<>();
        for (KnowledgeChunk chunk : snapshot) {
            if (!eligibleIds.contains(chunk.id()) || !allows(chunk, chapterId, audience)) {
                continue;
            }
            String sourceKey = chunk.source() == null ? chunk.id() : chunk.source();
            chunkCount++;
            sources.add(sourceKey);
        }
        return new EvidenceInventory(chunkCount, sources.size());
    }

    public List<KnowledgeSearchResult> search(
        String query,
        String chapterId,
        int limit,
        KnowledgeAudience audience
    ) {
        if (query == null || query.isBlank() || limit < 1 || INJECTION_PATTERN.matcher(query).find()) {
            return List.of();
        }

        Set<String> queryTokens = KnowledgeText.tokens(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunk> snapshot = chunks.get();
        Set<String> eligibleIds = eligibleIds(snapshot, audience);
        Map<String, KnowledgeSearchResult> uniqueBySource = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : snapshot) {
            if (!eligibleIds.contains(chunk.id()) || !allows(chunk, chapterId, audience)) {
                continue;
            }
            double score = score(chunk, queryTokens);
            if (score < minimumScore) {
                continue;
            }
            KnowledgeSearchResult candidate = new KnowledgeSearchResult(chunk, score);
            String sourceKey = chunk.source() == null ? chunk.id() : chunk.source();
            uniqueBySource.merge(sourceKey, candidate, (left, right) -> left.score() >= right.score() ? left : right);
        }

        return uniqueBySource.values().stream()
            .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
            .limit(Math.min(limit, 6))
            .toList();
    }

    private boolean allows(KnowledgeChunk chunk, String chapterId, KnowledgeAudience audience) {
        return audience != null
            && audience.allows(chunk.licenseScope())
            && (chapterId == null || chapterId.isBlank() || chapterId.equals(chunk.chapterId()));
    }

    private Set<String> eligibleIds(Collection<KnowledgeChunk> snapshot, KnowledgeAudience audience) {
        if (audience == null || snapshot.isEmpty()) {
            return Set.of();
        }
        return eligibilityFilter.eligibleIds(snapshot, audience);
    }

    /** Text search weights a chunk title far above its body because a lesson title names the topic. */
    private double score(KnowledgeChunk chunk, Set<String> queryTokens) {
        String normalizedTitle = KnowledgeText.normalize(chunk.title());
        double titleOnly = 0;
        for (String token : queryTokens) {
            titleOnly += KnowledgeText.countOccurrences(normalizedTitle, token) * (token.length() >= 3 ? 5 : 3);
        }
        return titleOnly + KnowledgeText.bodyScore(chunk.content(), queryTokens);
    }

    public record EvidenceInventory(int knowledgeChunkCount, int sourceCount) {
    }

    @FunctionalInterface
    interface EligibilityFilter {
        Set<String> eligibleIds(Collection<KnowledgeChunk> candidates, KnowledgeAudience audience);
    }
}
