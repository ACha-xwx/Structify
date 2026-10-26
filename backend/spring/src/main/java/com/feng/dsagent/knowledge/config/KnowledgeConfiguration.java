package com.feng.dsagent.knowledge.config;

import com.feng.dsagent.knowledge.converter.KnowledgeSearchConverter;
import com.feng.dsagent.knowledge.service.KnowledgeSearchService;
import com.feng.dsagent.knowledge.service.KnowledgeSettingsService;

import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import com.feng.dsagent.knowledge.entity.KnowledgeChunkEntity;
import com.feng.dsagent.knowledge.service.KnowledgeChunkService;
import com.feng.dsagent.knowledge.domain.KnowledgeCorpusStats;
import com.feng.dsagent.knowledge.support.KnowledgeCorpus;
import com.feng.dsagent.knowledge.support.KnowledgeCorpusLoader;
@Configuration
public class KnowledgeConfiguration {

    @Bean
    public KnowledgeCorpusLoader knowledgeCorpusLoader(ObjectMapper mapper) {
        return new KnowledgeCorpusLoader(mapper);
    }

    @Bean
    public KnowledgeCorpus knowledgeCorpus(KnowledgeProperties properties, KnowledgeCorpusLoader loader) {
        if (!properties.enabled() || !properties.autoPublishLocal()) {
            return KnowledgeCorpus.empty();
        }
        Path directory = properties.directory() == null || properties.directory().isBlank()
            ? null
            : Path.of(properties.directory());
        return loader.load(directory, properties.chunkSize());
    }

    @Bean
    public KnowledgeCorpusStats knowledgeCorpusStats(KnowledgeCorpus corpus) {
        return corpus.stats();
    }

    @Bean
    public KnowledgeSearchService knowledgeSearchService(
        KnowledgeCorpus corpus,
        KnowledgeProperties properties,
        KnowledgeChunkService repository,
        KnowledgeSettingsService settings,
        KnowledgeSearchConverter converter
    ) {
        List<KnowledgeChunkEntity> initialChunks = properties.enabled() && properties.autoPublishLocal()
            ? corpus.chunks()
            : List.of();
        return new KnowledgeSearchService(
            initialChunks,
            settings::current,
            (candidates, audience) -> repository.findEligibleIds(
                candidates.stream().map(KnowledgeChunkEntity::id).toList(),
                audience
            ),
            converter
        );
    }
}
