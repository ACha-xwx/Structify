package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The adapter asks the local engine first and falls back to the in-process simulator.
 *
 * <p>The fallback assertions run everywhere (no Node needed); the engine assertions are skipped when the
 * local engine cannot start, which is exactly the situation the fallback exists for.
 */
class DsvpAnimationAdapterRoutingTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnimationValidator validator = new AnimationValidator();

    private DsvpLocalEngine localEngine() {
        return new DsvpLocalEngine(true, "node", "../dsvp/dsvp-service.js", 20_000, mapper);
    }

    private JsonNode request(String json) {
        return mapper.readTree(json);
    }

    @Test
    void keepsTheFrozenNineStructureSurfaceWorkingWithoutTheEngine() {
        var adapter = new DsvpAnimationAdapter(mapper, validator);
        var response = adapter.adapt(request(
            "{\"version\":\"1.0\",\"structure\":\"stack\",\"operation\":\"push\",\"params\":{\"value\":3,\"capacity\":10},"
                + "\"initial_state\":{\"data\":[1,2]}}"));
        assertThat(response.animationData().steps()).isNotEmpty();
        assertThat(response.animationData().type()).isEqualTo("stack");
        assertThat(response.protocol()).isEqualTo("dsvp/1.0");
    }

    @Test
    void stillRejectsUndocumentedStructuresWhenNoEngineIsPresent() {
        var adapter = new DsvpAnimationAdapter(mapper, validator);
        assertThatThrownBy(() -> adapter.adapt(request(
            "{\"version\":\"1.0\",\"structure\":\"sort\",\"operation\":\"quick\",\"params\":{},"
                + "\"initial_state\":{\"data\":[3,1,2]}}")))
            .hasMessageContaining("Unsupported DSVP structure or operation");
    }

    @Test
    void servesTextbookCapabilitiesThroughTheLocalEngine() {
        var engine = localEngine();
        try {
            assumeTrue(engine.healthy(), "本地 node 引擎不可用，已跳过引擎路径断言");
            var adapter = new DsvpAnimationAdapter(mapper, validator, engine);

            var response = adapter.adapt(request(
                "{\"version\":\"1.0\",\"structure\":\"bst\",\"operation\":\"insert\",\"params\":{\"key\":30},"
                    + "\"initial_state\":{\"data\":[45,24,53,12,37]}}"));
            assertThat(response.animationData().type()).isEqualTo("bst");
            assertThat(response.animationData().steps()).isNotEmpty();
            // The rich snapshot is what a tree renderer draws; a flat row of values could not express it.
            assertThat(response.animationData().steps().get(0).dsvpState().path("kind").asText()).isEqualTo("tree");
            assertThat(response.trace().path("steps")).isNotEmpty();
            assertThat(response.protocol()).isEqualTo("dsvp/1.0");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void fallsBackInProcessForOperationsTheEngineDoesNotServe() {
        var engine = localEngine();
        try {
            assumeTrue(engine.healthy(), "本地 node 引擎不可用，已跳过混合路由断言");
            var adapter = new DsvpAnimationAdapter(mapper, validator, engine);

            // array/swap is a frozen-contract operation that is not a textbook capability: the engine says
            // UNSUPPORTED_OPERATION and the in-process simulator answers instead.
            var response = adapter.adapt(request(
                "{\"version\":\"1.0\",\"structure\":\"array\",\"operation\":\"swap\",\"params\":{\"i\":0,\"j\":1},"
                    + "\"initial_state\":{\"data\":[5,9,1]}}"));
            assertThat(response.animationData().type()).isEqualTo("array");
            assertThat(response.animationData().steps()).isNotEmpty();
        } finally {
            engine.shutdown();
        }
    }
}
