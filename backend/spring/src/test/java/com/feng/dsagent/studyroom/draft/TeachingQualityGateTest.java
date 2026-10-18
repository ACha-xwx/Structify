package com.feng.dsagent.studyroom.draft;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.feng.dsagent.studyroom.generation.*;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

class TeachingQualityGateTest {
    @Test
    void rejectsSourceMismatchAndAcceptsEvidenceBasedReview() {
        var calls = mock(GenerationCallService.class);
        var gate = new TeachingQualityGate(calls, new OutlineJson(new ObjectMapper()));
        when(calls.complete(
                        anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"valid\":false,\"reason\":\"函数名不符\",\"issues\":[\"源码只有show，讲解声称存在PreOrder\"]}");
        assertThatThrownBy(
                        () ->
                                gate.review(
                                        1,
                                        "job",
                                        "activity-1",
                                        Map.of(),
                                        Map.of(),
                                        List.of(),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("源码只有show");
        when(calls.complete(
                        anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("{\"valid\":true,\"reason\":\"讲解对应show中的递归调用\",\"issues\":[]}");
        assertThatCode(
                        () ->
                                gate.review(
                                        1,
                                        "job",
                                        "repair-1",
                                        Map.of(),
                                        Map.of(),
                                        List.of(),
                                        List.of()))
                .doesNotThrowAnyException();
    }
}
