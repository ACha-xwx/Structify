package com.feng.dsagent.knowledge.service;

import com.feng.dsagent.knowledge.domain.KnowledgeAudience;
import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;
import com.feng.dsagent.knowledge.mapper.KnowledgeChunkMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeChunkService {

    private final KnowledgeChunkMapper mapper;

    public KnowledgeChunkService(KnowledgeChunkMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void replaceTextbook(Collection<KnowledgeChunkEntity> chunks) {
        boolean courseware = chunks.stream().anyMatch(chunk -> chunk.source().startsWith("courseware/"));
        mapper.deleteBySourcePrefix(courseware ? "courseware/%" : "textbook/%");
        if (courseware) {
            mapper.deleteBySourcePrefix("dev-mock/%");
        }
        if (chunks != null && !chunks.isEmpty()) {
            mapper.insertChunks(chunks);
        }
    }

    public List<KnowledgeChunkEntity> findPublished() {
        return mapper.selectPublished();
    }

    /**
     * Rechecks a cached candidate set against the current database source chain
     * and the caller's allowed license scope. Fail closed.
     */
    public Set<String> findEligibleIds(Collection<String> chunkIds, KnowledgeAudience audience) {
        if (audience == null || chunkIds == null || chunkIds.isEmpty()) {
            return Set.of();
        }
        List<String> ids = chunkIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }

        List<String> scopes = licenseScopes(audience);
        Set<String> eligible = new LinkedHashSet<>();
        for (int start = 0; start < ids.size(); start += 500) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + 500));
            eligible.addAll(mapper.selectEligibleIds(batch, scopes));
        }
        return Set.copyOf(eligible);
    }

    private static List<String> licenseScopes(KnowledgeAudience audience) {
        return switch (audience) {
            case GUEST -> List.of("PUBLIC");
            case STUDENT -> List.of("PUBLIC", "CLASSROOM_ONLY");
            case TEAM -> List.of("PUBLIC", "CLASSROOM_ONLY", "TEAM_ONLY");
        };
    }
}
