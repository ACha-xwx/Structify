package com.feng.dsagent.knowledge.service;

import com.feng.dsagent.knowledge.domain.KnowledgeEligibilityChanged;
import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class KnowledgeIndexRefreshService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeIndexRefreshService.class);

    private final KnowledgeChunkService repository;
    private final KnowledgeSearchService search;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public KnowledgeIndexRefreshService(KnowledgeChunkService repository, KnowledgeSearchService search) {
        this.repository = repository;
        this.search = search;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshAfterEligibilityChange(KnowledgeEligibilityChanged ignored) {
        refreshLock.lock();
        try {
            List<KnowledgeChunkEntity> eligible = repository.findPublished();
            search.replace(eligible);
            LOGGER.info("Refreshed knowledge retrieval index with {} eligible chunks", eligible.size());
        } catch (RuntimeException error) {
            // A failed refresh must fail closed instead of serving an old snapshot after a revocation.
            search.replace(List.of());
            LOGGER.error(
                "Knowledge retrieval index refresh failed and was cleared: {}",
                error.getClass().getSimpleName()
            );
        } finally {
            refreshLock.unlock();
        }
    }
}
