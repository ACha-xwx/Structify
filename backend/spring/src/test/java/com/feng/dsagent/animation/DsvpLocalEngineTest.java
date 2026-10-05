package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the real local engine ({@code backend/dsvp}) through its JSONL service.
 *
 * <p>These tests need a Node runtime on PATH. They are skipped rather than failed when it is missing,
 * because a developer machine without Node should still be able to run the rest of the suite; the
 * animation endpoints themselves degrade to the in-process simulator in that case.
 */
class DsvpLocalEngineTest {

    private static DsvpLocalEngine engine() {
        return new DsvpLocalEngine(true, "node", "../dsvp/dsvp-service.js", 20_000, new ObjectMapper());
    }

    @Test
    void exposesTheWholeReviewedTextbookCapabilityCatalog() {
        var engine = engine();
        try {
            var capabilities = engine.capabilities("08-");
            assumeTrue(capabilities.isPresent(), "本地 node 引擎不可用");
            assertThat(capabilities.get().path("total").asInt()).isEqualTo(167);
            String prompt = capabilities.get().path("prompt").asText();
            // Chapter scoping: the model sees the current chapter's capabilities, not all 167.
            assertThat(prompt).contains("bst.insert").contains("hash_table.linear_probe_insert");
            assertThat(prompt).doesNotContain("sort.quick");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void computesEveryFrameLocallyInsteadOfAskingTheModel() {
        var engine = engine();
        try {
            ObjectNode request = new ObjectMapper().createObjectNode();
            request.put("version", "1.0");
            request.put("structure", "sort");
            request.put("operation", "quick");
            request.putObject("params").put("capacity", 64);
            request.putObject("initial_state").putArray("data").add(49).add(38).add(65).add(97).add(76).add(13).add(27);

            var reply = engine.simulate(request);
            assumeTrue(reply.isPresent(), "本地 node 引擎不可用");
            var player = reply.get().path("player");
            assertThat(player.path("type").asText()).isEqualTo("sort");
            assertThat(player.path("steps")).isNotEmpty();
            // Each step carries the engine's own snapshot, which is what a renderer draws.
            assertThat(player.path("steps").get(0).path("dsvpState").path("kind").asText()).isEqualTo("sort");
        } finally {
            engine.shutdown();
        }
    }

    /**
     * Pins the three annotations a learner actually reads off the picture: which cells the named pointers
     * stand on (`i`/`j`/`low`/`mid`), what currently holds about the structure (`invariant`), and how much
     * work has been done so far (`compareCount`/`swapCount`).
     *
     * <p>The renderer draws these fields and nothing else, so they are pinned at the engine boundary too: a
     * simulator that stops reporting a comparison would otherwise only show up as a counter stuck at zero on
     * a learner's screen. The counts asserted here are the textbook ones for four elements - ten adjacent
     * comparisons are not needed for 5,3,8,1 and the last pass stops early, so 6 comparisons and 4 swaps.
     */
    @Test
    void annotatesEverySortStepWithCursorsCountersAndWhatCurrentlyHolds() {
        var engine = engine();
        try {
            ObjectNode request = new ObjectMapper().createObjectNode();
            request.put("version", "1.0");
            request.put("structure", "sort");
            request.put("operation", "bubble");
            request.putObject("initial_state").putArray("data").add(5).add(3).add(8).add(1);

            var reply = engine.simulate(request);
            assumeTrue(reply.isPresent(), "本地 node 引擎不可用");
            var steps = reply.get().path("player").path("steps");
            assertThat(steps).isNotEmpty();

            for (var step : steps) {
                assertThat(meta(step).path("invariant").asText()).isNotBlank();
            }

            var totals = meta(steps.get(steps.size() - 1));
            assertThat(totals.path("compareCount").asInt()).isEqualTo(6);
            assertThat(totals.path("swapCount").asInt()).isEqualTo(4);

            // 比较两格的一步必须同时报出 i 和 j，否则学生只看到一个没有名字的高亮。
            var first = meta(steps.get(0));
            assertThat(first.path("i").isNumber()).isTrue();
            assertThat(first.path("j").isNumber()).isTrue();
        } finally {
            engine.shutdown();
        }
    }

    private static JsonNode meta(JsonNode step) {
        for (var panel : step.path("dsvpState").path("view")) {
            if ("meta".equals(panel.path("role").asText())) return panel;
        }
        return new ObjectMapper().createObjectNode();
    }

    @Test
    void rejectsAnUnknownCapabilityWithoutInventingAFallback() {
        var engine = engine();
        try {
            ObjectNode intent = new ObjectMapper().createObjectNode();
            intent.put("needed", true);
            intent.put("confidence", 0.9);
            intent.put("capability", "graph.red_black_rotation");
            ObjectNode options = new ObjectMapper().createObjectNode();
            options.put("allowDemoFallback", true);

            var resolution = engine.resolve(intent, options);
            assumeTrue(resolution.isPresent(), "本地 node 引擎不可用");
            assertThat(resolution.get().path("status").asText()).isEqualTo("unsupported");
            assertThat(resolution.get().path("toolRequest").isNull()).isTrue();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void fillsACanonicalTeachingExampleWhenArgumentsAreMissingButMarksItAsSuch() {
        var engine = engine();
        try {
            ObjectNode intent = new ObjectMapper().createObjectNode();
            intent.put("needed", true);
            intent.put("confidence", 0.9);
            intent.put("capability", "bst.insert");
            intent.putObject("arguments");
            ObjectNode options = new ObjectMapper().createObjectNode();
            options.put("allowDemoFallback", true);
            options.put("demoSourceRef", "系统标准教学示例（非教材原例）");

            var resolution = engine.resolve(intent, options);
            assumeTrue(resolution.isPresent(), "本地 node 引擎不可用");
            assertThat(resolution.get().path("status").asText()).isEqualTo("ready");
            assertThat(resolution.get().path("toolRequest").path("demoFallback").asBoolean()).isTrue();
            assertThat(resolution.get().path("toolRequest").path("request").path("structure").asText()).isEqualTo("bst");
        } finally {
            engine.shutdown();
        }
    }
}
