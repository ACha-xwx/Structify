package com.feng.dsagent.knowledge.service;

import com.feng.dsagent.knowledge.KnowledgeSearchResult;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import com.feng.dsagent.knowledge.KnowledgeChunk;
import com.feng.dsagent.knowledge.KnowledgeText;
import com.feng.dsagent.knowledge.service.KnowledgeSettingsService.MatchingSettings;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;


public final class KnowledgeSearchService {

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
        "忽略(以上|之前|所有).{0,12}(指令|规则)|系统提示词|开发者消息|泄露.{0,8}提示词|执行以下命令|system\\s*prompt",
        Pattern.CASE_INSENSITIVE
    );
    private final AtomicReference<List<KnowledgeChunk>> chunks;
    private final Supplier<MatchingSettings> settings;
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
        // A fixed score keeps the historical wiring for tests: cap 6 and the historical title weights 5/3.
        this(chunks, () -> new MatchingSettings(minimumScore, 6, 5.0, 3.0), eligibilityFilter);
    }

    /**
     * Production wiring: the tuning is re-read on every query, so a console save applies to the next
     * search without a restart and without touching the chunk snapshot.
     */
    public KnowledgeSearchService(
        Collection<KnowledgeChunk> chunks,
        Supplier<MatchingSettings> settings,
        EligibilityFilter eligibilityFilter
    ) {
        this.chunks = new AtomicReference<>(List.copyOf(chunks));
        this.settings = settings;
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

    /** How many chunks the retrieval snapshot currently holds; the admin console reports this as readiness. */
    public int snapshotSize() {
        return chunks.get().size();
    }

    public List<KnowledgeSearchResult> search(
        String query,
        String chapterId,
        int limit,
        KnowledgeAudience audience
    ) {
        return searchWith(query, chapterId, limit, audience, settings.get());
    }

    /**
     * The same pipeline with explicit tuning, for the admin console's try-it-before-you-save preview.
     * Eligibility and license checks still apply; only the matching knobs change.
     */
    public List<KnowledgeSearchResult> preview(
        String query,
        String chapterId,
        int limit,
        KnowledgeAudience audience,
        MatchingSettings overrides
    ) {
        return searchWith(query, chapterId, limit, audience, overrides);
    }

    private List<KnowledgeSearchResult> searchWith(
        String query,
        String chapterId,
        int limit,
        KnowledgeAudience audience,
        MatchingSettings tuning
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
            double score = score(chunk, queryTokens, tuning);
            if (score < tuning.minimumScore()) {
                continue;
            }
            KnowledgeSearchResult candidate = new KnowledgeSearchResult(chunk, score);
            String sourceKey = chunk.source() == null ? chunk.id() : chunk.source();
            uniqueBySource.merge(sourceKey, candidate, (left, right) -> left.score() >= right.score() ? left : right);
        }

        return uniqueBySource.values().stream()
            .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
            .limit(Math.min(limit, tuning.searchLimit()))
            .toList();
    }

    private boolean allows(KnowledgeChunk chunk, String chapterId, KnowledgeAudience audience) {
        return audience != null
            && audience.allows(chunk.licenseScope())
            && (chapterId == null || chapterId.isBlank() || chapterId.equals(chunk.chapterId()));
    }

    /** Fragment teaching needs distinct pages, including multiple pages from the same source. */
    public List<KnowledgeSearchResult> searchForFragment(String query, String chapterId, int limit, KnowledgeAudience audience) {
        if(query==null||query.isBlank()||limit<1||INJECTION_PATTERN.matcher(query).find())return List.of();
        var tokens=KnowledgeText.tokens(query);
        if(tokens.isEmpty())return List.of();
        var tuning=settings.get();
        var snapshot=chunks.get();var eligible=eligibleIds(snapshot,audience);
        Map<String,KnowledgeSearchResult> unique=new LinkedHashMap<>();
        for(var chunk:snapshot) {
            if(!eligible.contains(chunk.id())||!allows(chunk,chapterId,audience))continue;
            double relevance=score(chunk,tokens,tuning);
            if(relevance<tuning.minimumScore())continue;
            var candidate=new KnowledgeSearchResult(chunk,relevance);
            String key=KnowledgeText.normalize(chunk.content());
            unique.merge(key,candidate,(a,b)->a.score()>=b.score()?a:b);
        }
        return unique.values().stream().sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
            .limit(Math.min(limit,tuning.searchLimit())).toList();
    }

    private Set<String> eligibleIds(Collection<KnowledgeChunk> snapshot, KnowledgeAudience audience) {
        if (audience == null || snapshot.isEmpty()) {
            return Set.of();
        }
        return eligibilityFilter.eligibleIds(snapshot, audience);
    }

    /** Text search weights a chunk title far above its body because a lesson title names the topic. */
    private double score(KnowledgeChunk chunk, Set<String> queryTokens, MatchingSettings tuning) {
        String normalizedTitle = KnowledgeText.normalize(chunk.title());
        double titleOnly = 0;
        for (String token : queryTokens) {
            titleOnly += KnowledgeText.countOccurrences(normalizedTitle, token)
                * (token.length() >= 3 ? tuning.titleWeightLong() : tuning.titleWeightShort());
        }
        return titleOnly + KnowledgeText.bodyScore(chunk.content(), queryTokens);
    }

    public record EvidenceInventory(int knowledgeChunkCount, int sourceCount) {
    }

    @FunctionalInterface
    public interface EligibilityFilter {
        Set<String> eligibleIds(Collection<KnowledgeChunk> candidates, KnowledgeAudience audience);
    }
}
