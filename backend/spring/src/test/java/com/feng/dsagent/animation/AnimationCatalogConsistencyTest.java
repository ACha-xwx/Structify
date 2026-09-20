package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The animation lab's picker is a build-time snapshot of the engine's capability registry.
 *
 * <p>A snapshot can drift, and a picker that offers an operation the engine no longer serves is worse than
 * no picker: the learner chooses it and gets an error. This test is the guard - it fails the build when
 * {@code frontend/src/animation/capability-catalog.json} no longer matches the engine, and names the
 * refresh command.
 */
class AnimationCatalogConsistencyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theLabPickerStillMatchesTheEngineCatalog() throws Exception {
        Path catalog = Path.of(System.getProperty("user.dir"), "..", "..", "frontend", "src", "animation", "capability-catalog.json")
            .toAbsolutePath()
            .normalize();
        assertThat(Files.isRegularFile(catalog))
            .as("缺失 %s；请运行 node scripts/build-animation-catalog.mjs", catalog)
            .isTrue();

        DsvpLocalEngine engine = new DsvpLocalEngine(true, "node", "../dsvp/dsvp-service.js", 20_000, mapper);
        try {
            var capabilities = engine.capabilities(null);
            assumeTrue(capabilities.isPresent(), "本地 node 引擎不可用，已跳过目录一致性断言");
            JsonNode engineList = capabilities.get();

            JsonNode snapshot = mapper.readTree(Files.readString(catalog, StandardCharsets.UTF_8));
            assertThat(snapshot.path("total").asInt())
                .as("目录能力总数与引擎不一致；请重跑 node scripts/build-animation-catalog.mjs")
                .isEqualTo(engineList.path("total").asInt());

            Set<String> engineCapabilities = new LinkedHashSet<>();
            for (JsonNode item : engineList.path("capabilities")) engineCapabilities.add(item.path("capability").asText());
            engineCapabilities.remove("");

            Set<String> snapshotCapabilities = new LinkedHashSet<>();
            List<String> undocumentedStructures = new ArrayList<>();
            for (JsonNode structure : snapshot.path("structures")) {
                for (JsonNode item : structure.path("capabilities")) snapshotCapabilities.add(item.path("capability").asText());
                // Every structure the picker offers must name a chapter, or the chapter filter hides it.
                if (!structure.path("chapter").isInt()) undocumentedStructures.add(structure.path("structure").asText());
            }
            snapshotCapabilities.remove("");

            assertThat(snapshotCapabilities)
                .as("目录与引擎的能力清单不一致；请重跑 node scripts/build-animation-catalog.mjs")
                .isEqualTo(engineCapabilities);
            assertThat(undocumentedStructures)
                .as("这些结构没有章号，会在章节筛选下消失；请在 build-animation-catalog.mjs 的 STRUCTURE_CHAPTERS 里补上")
                .isEmpty();
        } finally {
            engine.shutdown();
        }
    }
}
