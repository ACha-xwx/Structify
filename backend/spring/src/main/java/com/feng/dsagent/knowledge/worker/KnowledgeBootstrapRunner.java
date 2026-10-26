package com.feng.dsagent.knowledge.worker;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.feng.dsagent.knowledge.config.KnowledgeProperties;
import com.feng.dsagent.knowledge.support.KnowledgeCorpus;
import com.feng.dsagent.knowledge.domain.KnowledgeImportManifest;
import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;
import com.feng.dsagent.knowledge.service.KnowledgeChunkService;
@Component
public final class KnowledgeBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBootstrapRunner.class);

    private final KnowledgeProperties properties;
    private final KnowledgeCorpus corpus;
    private final KnowledgeChunkService repository;
    private final KnowledgeSearchService search;

    public KnowledgeBootstrapRunner(
        KnowledgeProperties properties,
        KnowledgeCorpus corpus,
        KnowledgeChunkService repository,
        KnowledgeSearchService search
    ) {
        this.properties = properties;
        this.corpus = corpus;
        this.repository = repository;
        this.search = search;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            LOGGER.info("Knowledge indexing is disabled");
            return;
        }
        for (KnowledgeImportManifest.Rejection rejection : corpus.stats().rejections()) {
            LOGGER.warn("Textbook lesson kept unpublished: {} -> {}", rejection.fileName(), rejection.reason());
        }
        if (properties.autoPublishLocal()) {
            if (corpus.stats().available()) {
                int before = repository.findPublished().size();
                repository.replaceTextbook(corpus.chunks());
                search.replace(repository.findPublished());
                LOGGER.info(
                    "Loaded {} original course lessons into {} knowledge chunks; "
                        + "published chunk count {} -> {}",
                    corpus.stats().lessonFiles(),
                    corpus.stats().chunkCount(),
                    before,
                    corpus.stats().chunkCount()
                );
                return;
            }
            LOGGER.warn(
                "Local textbook loading is enabled but no lesson passed the import manifest; "
                    + "existing reviewed chunks are preserved"
            );
        }
        List<KnowledgeChunkEntity> persisted = repository.findPublished();
        search.replace(persisted);
        if (!properties.autoPublishLocal()) {
            LOGGER.info(
                "Local textbook loading is disabled; loaded {} reviewed chunks from the database",
                persisted.size()
            );
        } else {
            LOGGER.info("Loaded {} reviewed chunks from the database", persisted.size());
        }
    }
}
