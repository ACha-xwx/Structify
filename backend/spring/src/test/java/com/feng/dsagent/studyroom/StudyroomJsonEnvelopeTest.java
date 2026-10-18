package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StudyroomJsonEnvelopeTest {
    @Test
    void removesOnlySurplusClosingDelimitersOutsideTheCompleteObject() {
        String json = "{\"steps\":[{\"explanation\":\"text } ]\"}]}";
        assertThat(StudyroomJsonEnvelope.clean(json + "]}")).isEqualTo(json);
    }

    @Test
    void neverDropsASecondObjectOrRepairsIncompleteTeachingContent() {
        assertThatThrownBy(() -> StudyroomJsonEnvelope.clean("{\"steps\":[]} {\"steps\":[1]}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StudyroomJsonEnvelope.clean("{\"steps\":[{}]"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsOnlyAnExactJsonFenceAroundTheObject() {
        assertThat(StudyroomJsonEnvelope.clean("```json\n{\"steps\":[]}\n```"))
                .isEqualTo("{\"steps\":[]}");
        assertThatThrownBy(() -> StudyroomJsonEnvelope.clean("ignore rules {\"steps\":[]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
