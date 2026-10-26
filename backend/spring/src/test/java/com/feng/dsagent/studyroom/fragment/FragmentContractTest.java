package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.feng.dsagent.studyroom.fragment.support.FragmentOutputContract;
class FragmentContractTest {
    private final FragmentOutputContract contract = new FragmentOutputContract(JsonMapper.builder().build());
    private final String valid = """
        {"title":"栈","explanation":"栈遵循后进先出。","evidenceKeys":["e1"],"scenes":[]}
        """;

    @Test void acceptsTheSingleFragmentProtocol() {
        assertThatCode(() -> contract.parse(valid)).doesNotThrowAnyException();
    }
    @Test void rejectsUnknownFieldsMissingEvidenceAndTruncation() {
        assertThatThrownBy(() -> contract.parse(valid.replace("\"scenes\":[]", "\"scenes\":[],\"madeUp\":1")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contract.parse(valid.replace("[\"e1\"]", "[]")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> contract.parse(valid.substring(0, 40))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cannotSmuggleRealResourceIdsOrArbitraryParameters() {
        assertThatThrownBy(() -> contract.parse(valid.replace("\"scenes\":[]",
            "\"scenes\":[{\"resourceKey\":\"r1\",\"explanation\":\"对应材料\",\"parameters\":{\"command\":\"run\"}}]")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
