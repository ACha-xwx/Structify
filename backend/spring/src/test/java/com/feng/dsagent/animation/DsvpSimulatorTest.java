package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.common.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DsvpSimulatorTest {
    final ObjectMapper mapper = new ObjectMapper();
    final DsvpAnimationAdapter adapter = new DsvpAnimationAdapter(mapper,new AnimationValidator());
    JsonNode run(String structure, String operation, String data, String params) {
        return adapter.adapt(mapper.readTree("{\"version\":\"1.0\",\"structure\":\""+structure+"\",\"operation\":\""+operation+"\",\"params\":"+params+",\"initial_state\":{\"data\":"+data+"}}")).trace();
    }
    @Test void mergesInOrderAndPreservesDuplicates() {
        JsonNode trace = run("sequential_list","merge","[[2,2,3],[1,3,3,4]]","{\"capacity\":10}");
        assertThat(trace.path("final_state")).isEqualTo(mapper.readTree("[1,2,2,3,3,3,4]"));
        assertThat(trace.path("steps")).hasSize(7);
        assertThat(trace.path("steps").get(0).path("state")).isEqualTo(mapper.readTree("[1]"));
    }
    @Test void rejectsUnsortedAndOverflowingMergeInsteadOfTruncating() {
        assertThatThrownBy(() -> run("sequential_list","merge","[[3,1],[2]]","{}")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> run("sequential_list","merge","[[1,3],[2,4]]","{\"capacity\":3}")).isInstanceOf(ApiException.class);
    }
    @Test void insertAndDeleteHaveCorrectIntermediateStates() {
        JsonNode trace = run("sequential_list","insert","[12,18,27,31,44]","{\"index\":2,\"value\":23,\"capacity\":8}");
        assertThat(trace.path("final_state")).isEqualTo(mapper.readTree("[12,18,23,27,31,44]"));
        assertThat(trace.path("steps").get(0).path("state")).isEqualTo(mapper.readTree("[12,18,27,31,44,44]"));
        assertThat(run("sequential_list","delete","[1,2,3,4]","{\"index\":1}").path("final_state")).isEqualTo(mapper.readTree("[1,3,4]"));
    }
    @Test void rejectsEmptyPopAndMissingPosition() {
        assertThatThrownBy(() -> run("stack","pop","[]","{}")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> run("array","insert","[1,2]","{\"value\":3}")).isInstanceOf(ApiException.class);
    }
    @Test void traversesGraphsRatherThanReturningOneHighlight() {
        String graph = "{\"node\":0,\"edges\":[[0,1],[0,2],[1,3]],\"directed\":true}";
        assertThat(run("graph","bfs","[\"A\",\"B\",\"C\",\"D\"]",graph).path("visited")).isEqualTo(mapper.readTree("[0,1,2,3]"));
        assertThat(run("graph","dfs","[\"A\",\"B\",\"C\",\"D\"]",graph).path("visited")).isEqualTo(mapper.readTree("[0,1,3,2]"));
    }
    @Test void postorderRespectsNullSlots() {
        assertThat(run("tree","traverse","[1,2,3,null,4]","{\"order\":\"postorder\"}").path("visited")).isEqualTo(mapper.readTree("[4,1,2,0]"));
    }
    @Test void heapMaintainsHeapOrderAndHashUpdatesAnExistingKey() {
        JsonNode heap = run("heap","insert","[2,4,6]","{\"value\":1}").path("final_state");
        assertThat(heap.get(0).asInt()).isEqualTo(1);
        for(int i=1;i<heap.size();i++) assertThat(heap.get((i-1)/2).asInt()).isLessThanOrEqualTo(heap.get(i).asInt());
        assertThat(run("heap","extract","[1,2,6,4]","{}").path("final_state")).isEqualTo(mapper.readTree("[2,4,6]"));
        assertThat(run("hash","put","[{\"key\":\"a\",\"val\":1}]","{\"key\":\"a\",\"val\":2}").path("final_state")).isEqualTo(mapper.readTree("[{\"key\":\"a\",\"val\":2}]"));
    }

    @Test void persistedAnimationKeepsExactLocalSnapshotsIncludingNullTreeSlots() {
        for (String request : new String[] {
            "{\"version\":\"1.0\",\"structure\":\"heap\",\"operation\":\"extract\",\"initial_state\":{\"data\":[1,2,6,4]},\"params\":{}}",
            "{\"version\":\"1.0\",\"structure\":\"tree\",\"operation\":\"traverse\",\"initial_state\":{\"data\":[1,2,3,null,4]},\"params\":{\"order\":\"postorder\"}}"
        }) {
            var simulation = adapter.adapt(mapper.readTree(request));
            var restored = mapper.readTree(mapper.writeValueAsString(simulation.animationData()));
            for (int i = 0; i < restored.path("steps").size(); i++) {
                assertThat(restored.path("steps").get(i).path("state"))
                    .isEqualTo(simulation.trace().path("steps").get(i).path("state"));
            }
        }
    }
}
