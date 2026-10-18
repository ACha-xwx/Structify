package com.feng.dsagent.studyroom;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.function.Predicate;

final class StudyroomStepRepair {
    static String merge(
            ObjectMapper mapper, String original, String repaired, Predicate<String> valid) {
        ObjectNode base;
        try {
            base = (ObjectNode) mapper.readTree(original);
        } catch (RuntimeException invalidJson) {
            return repaired;
        }
        if (!base.path("steps").isArray()) return repaired;
        var patch = mapper.readTree(repaired);
        var steps = mapper.createArrayNode();
        for (var step : base.path("steps")) {
            var single = base.deepCopy();
            single.set("steps", mapper.createArrayNode().add(step));
            if (valid.test(mapper.writeValueAsString(single))) {
                steps.add(step);
                continue;
            }
            var replacement =
                    java.util.stream.StreamSupport.stream(patch.path("steps").spliterator(), false)
                            .filter(
                                    s ->
                                            Objects.equals(
                                                    s.path("id").asString(),
                                                    step.path("id").asString()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Repair omitted failed step: "
                                                            + step.path("id").asString()));
            steps.add(replacement);
        }
        base.set("steps", steps);
        return mapper.writeValueAsString(base);
    }
}
