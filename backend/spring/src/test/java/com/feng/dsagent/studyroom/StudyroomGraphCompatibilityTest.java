package com.feng.dsagent.studyroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.model.ModelMessage;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bsc.langgraph4j.serializer.plain_text.gson.GsonStateSerializer;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

class StudyroomGraphCompatibilityTest {

    @Test
    void routesConditionallyAndCallsExistingQuotaBoundaryOnce() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<String> operation = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AiQuotaExecution quota = (userId, op, id, request) -> {
            assertEquals(42L, userId);
            operation.set(op);
            requestId.set(id);
            callCount.incrementAndGet();
            return new ModelResponse("valid outline");
        };

        StateGraph<ProbeState> graph = new StateGraph<>(new ProbeStateSerializer());
        graph.addNode("generate", AsyncNodeAction.node_async((NodeAction<ProbeState>) state -> {
            ModelResponse response = quota.complete(
                ((Number) state.value("userId").orElseThrow()).longValue(),
                "studyroom_create",
                (String) state.value("requestId").orElseThrow(),
                new ModelRequest(List.of(new ModelMessage("user", (String) state.value("question").orElseThrow())))
            );
            return Map.of("answer", response.content());
        }));
        graph.addNode("accepted", AsyncNodeAction.node_async((NodeAction<ProbeState>) state -> Map.of("route", "accepted")));
        graph.addNode("rejected", AsyncNodeAction.node_async((NodeAction<ProbeState>) state -> Map.of("route", "rejected")));
        graph.addEdge(StateGraph.START, "generate");
        graph.addConditionalEdges(
            "generate",
            AsyncEdgeAction.edge_async(state -> ((String) state.value("answer").orElse("")).isBlank() ? "rejected" : "accepted"),
            Map.of("accepted", "accepted", "rejected", "rejected")
        );
        graph.addEdge("accepted", StateGraph.END);
        graph.addEdge("rejected", StateGraph.END);

        ProbeState result = graph.compile().invoke(Map.of(
            "userId", 42L,
            "requestId", "compatibility-probe-1",
            "question", "Explain linked lists"
        )).orElseThrow();

        assertEquals("accepted", result.value("route").orElseThrow());
        assertEquals(1, callCount.get());
        assertEquals("studyroom_create", operation.get());
        assertEquals("compatibility-probe-1", requestId.get());
        assertTrue(result.data().containsKey("answer"));
    }

    private static final class ProbeState extends AgentState {

        private ProbeState(Map<String, Object> data) {
            super(data);
        }
    }

    private static final class ProbeStateSerializer extends GsonStateSerializer<ProbeState> {

        private ProbeStateSerializer() {
            super(ProbeState::new);
        }
    }
}
