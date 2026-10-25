package com.feng.dsagent.knowledge;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;
import com.feng.dsagent.knowledge.service.KnowledgeSettingsService;

import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

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
        KnowledgeChunkRepository repository,
        KnowledgeSettingsService settings
    ) {
        List<KnowledgeChunk> initialChunks = properties.enabled() && properties.autoPublishLocal()
            ? corpus.chunks()
            : List.of();
        return new KnowledgeSearchService(
            initialChunks,
            settings::current,
            (candidates, audience) -> repository.findEligibleIds(
                candidates.stream().map(KnowledgeChunk::id).toList(),
                audience
            )
        );
    }
}
