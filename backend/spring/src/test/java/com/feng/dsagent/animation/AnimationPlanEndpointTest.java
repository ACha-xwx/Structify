package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.feng.dsagent.classroom.ClassroomModelJson;
import com.feng.dsagent.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The animation lab's deterministic path: a picked capability becomes a request without a model call.
 *
 * <p>The engine assertions are skipped when Node is unavailable, which is the same condition under which
 * the endpoint itself answers 503.
 */
class AnimationPlanEndpointTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private DsvpLocalEngine engine() {
        return new DsvpLocalEngine(true, "node", "../dsvp/dsvp-service.js", 20_000, mapper);
    }

    private AnimationIntentController controller(DsvpLocalEngine engine) {
        ClassroomModelJson model = mock(ClassroomModelJson.class);
        var adapter = new DsvpAnimationAdapter(mapper, new AnimationValidator());
        if (engine == null) return new AnimationIntentController(model, adapter, jdbc, mapper);
        return new AnimationIntentController(model, adapter, jdbc, mapper, engine);
    }

    @Test
    void buildsAnExecutableRequestFromAPickedCapabilityWithoutAskingTheModel() {
        var engine = engine();
        try {
            assumeTrue(engine.healthy(), "本地 node 引擎不可用");

            ObjectNode arguments = mapper.createObjectNode();
            arguments.putArray("initialData").add(45).add(24).add(53);
            arguments.put("key", 30);

            var resolution = controller(engine).plan(
                new AnimationIntentController.PlanInput("bst.insert", arguments, "第 8 章 查找"));

            assertThat(resolution.path("status").asText()).isEqualTo("ready");
            assertThat(resolution.path("toolRequest").path("demoFallback").asBoolean()).isFalse();
            var request = resolution.path("toolRequest").path("request");
            assertThat(request.path("structure").asText()).isEqualTo("bst");
            assertThat(request.path("operation").asText()).isEqualTo("insert");
            assertThat(request.path("params").path("key").asInt()).isEqualTo(30);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void marksACanonicalExampleInsteadOfPretendingItCameFromTheLesson() {
        var engine = engine();
        try {
            assumeTrue(engine.healthy(), "本地 node 引擎不可用");

            // No arguments at all: the engine may only answer with its own teaching example, and must say so.
            var resolution = controller(engine)
                .plan(new AnimationIntentController.PlanInput("bst.insert", null, null));

            assertThat(resolution.path("status").asText()).isEqualTo("ready");
            assertThat(resolution.path("toolRequest").path("demoFallback").asBoolean()).isTrue();
            assertThat(resolution.path("toolRequest").path("request").path("structure").asText()).isEqualTo("bst");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void refusesACapabilityThatIsNotInTheReviewedCatalog() {
        var engine = engine();
        try {
            assumeTrue(engine.healthy(), "本地 node 引擎不可用");

            assertThatThrownBy(() -> controller(engine)
                .plan(new AnimationIntentController.PlanInput("graph.red_black_rotation", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("graph.red_black_rotation");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void reportsUnreachableEngineRatherThanGuessingARequest() {
        assertThatThrownBy(() -> controller(null)
            .plan(new AnimationIntentController.PlanInput("stack.push", null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("本地动画引擎不可用");
    }
}
