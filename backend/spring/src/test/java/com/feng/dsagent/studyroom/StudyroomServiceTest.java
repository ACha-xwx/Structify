package com.feng.dsagent.studyroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StudyroomServiceTest {

    @Test
    void rejectsFollowUpContextFromAnotherStepBeforeCallingModel() {
        var repository = mock(StudyroomRepository.class);
        var graph = mock(StudyroomGraph.class);
        var now = Instant.now();
        var room = new StudyroomRecords.Room("room", 9L, null, "标题", "问题", "摘要", null, 1, now, now);
        when(repository.findOwned("room", 9L)).thenReturn(Optional.of(new StudyroomRecords.Aggregate(room, List.of(), List.of())));
        var service = new StudyroomService(mock(AiQuotaExecution.class), graph, repository, new StudyroomResourceCatalog(List.of()), new ObjectMapper());
        assertThatThrownBy(() -> service.followUp(9L, "room", "为什么", KnowledgeAudience.STUDENT, "r",
            new StudyroomFollowUpContext("foreign", "main", null, null, null)))
            .isInstanceOf(ApiException.class).hasMessageContaining("当前步骤");
        verifyNoInteractions(graph);
    }

    @Test
    void requiresFormalAuthenticationBeforeStartingTheGraph() {
        AiQuotaExecution execution = mock(AiQuotaExecution.class);
        StudyroomGraph graph = mock(StudyroomGraph.class);
        StudyroomRecords.Aggregate expected = new StudyroomRecords.Aggregate(
            new StudyroomRecords.Room("room-id", 9L, null, "标题", "问题", "摘要", null, 1, Instant.now(), Instant.now()),
            List.of(),
            List.of()
        );
        when(graph.create(9L, null, "问题", KnowledgeAudience.STUDENT, "req-1")).thenReturn(expected);
        StudyroomService service = new StudyroomService(execution, graph, mock(StudyroomRepository.class),
            new StudyroomResourceCatalog(List.of()), new ObjectMapper());

        StudyroomRecords.Aggregate actual = service.create(9L, null, "问题", KnowledgeAudience.STUDENT, "req-1");

        assertThat(actual).isSameAs(expected);
        verify(execution).requireFormalAuthentication(9L);
        verify(graph).create(9L, null, "问题", KnowledgeAudience.STUDENT, "req-1");
    }

    @Test
    void readsOwnedRoomAndMarksMissingStaticResourcesUnavailable() {
        AiQuotaExecution execution = mock(AiQuotaExecution.class);
        StudyroomGraph graph = mock(StudyroomGraph.class);
        StudyroomRepository repository = mock(StudyroomRepository.class);
        StudyroomResourceCatalog catalog = new StudyroomResourceCatalog(List.of());
        Instant now = Instant.now();
        StudyroomRecords.Aggregate aggregate = new StudyroomRecords.Aggregate(
            new StudyroomRecords.Room("room-id", 9L, "chapter-1", "标题", "问题", "摘要", "step-1", 2, now, now),
            List.of(new StudyroomRecords.Step("step-1", "s1", "lesson", "第一步", "图文讲解", "[]",
                "[{\"type\":\"ppt\",\"id\":\"slide-missing\",\"parameters\":{}}]", 0, false, null, now)),
            List.of(new StudyroomRecords.Message("message-1", "assistant", "欢迎学习", "[]", "step-1", now))
        );
        when(repository.findOwned("room-id", 9L)).thenReturn(Optional.of(aggregate));
        StudyroomService service = new StudyroomService(execution, graph, repository, catalog, new ObjectMapper());

        StudyroomView view = service.get(9L, "room-id");

        assertThat(view.id()).isEqualTo("room-id");
        assertThat(view.currentStepId()).isEqualTo("step-1");
        assertThat(view.steps()).singleElement().satisfies(step -> {
            assertThat(step.explanation()).isEqualTo("图文讲解");
            assertThat(step.resources()).singleElement().satisfies(resource -> {
                assertThat(resource.available()).isFalse();
                assertThat(resource.type()).isEqualTo("ppt");
            });
        });
        assertThat(view.messages()).hasSize(1);
    }

    @Test
    void hidesRoomsOwnedByAnotherUserAsNotFound() {
        AiQuotaExecution execution = mock(AiQuotaExecution.class);
        StudyroomGraph graph = mock(StudyroomGraph.class);
        StudyroomRepository repository = mock(StudyroomRepository.class);
        when(repository.findOwned("room-id", 8L)).thenReturn(Optional.empty());
        StudyroomService service = new StudyroomService(execution, graph, repository, new StudyroomResourceCatalog(List.of()), new ObjectMapper());

        assertThatThrownBy(() -> service.get(8L, "room-id"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> {
                assertThat(((ApiException) error).status()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(((ApiException) error).code()).isEqualTo("STUDYROOM_NOT_FOUND");
            });
    }

    @Test
    void updatesProgressWithoutCallingTheModelGraph() {
        AiQuotaExecution execution = mock(AiQuotaExecution.class);
        StudyroomGraph graph = mock(StudyroomGraph.class);
        StudyroomRepository repository = mock(StudyroomRepository.class);
        StudyroomRecords.Room updated = new StudyroomRecords.Room(
            "room-id", 9L, null, "标题", "问题", "摘要", "step-2", 3, Instant.now(), Instant.now()
        );
        when(repository.updateProgress(9L, "room-id", "step-2")).thenReturn(Optional.of(updated));
        StudyroomService service = new StudyroomService(execution, graph, repository, new StudyroomResourceCatalog(List.of()), new ObjectMapper());

        StudyroomView.Progress actual = service.updateProgress(9L, "room-id", "step-2");

        assertThat(actual.currentStepId()).isEqualTo("step-2");
        assertThat(actual.version()).isEqualTo(3);
        verify(execution).requireFormalAuthentication(9L);
        verifyNoInteractions(graph);
    }

    @Test
    void followsUpWithinOwnedStudyroomAndKeepsTheSameRoomIdentity() {
        AiQuotaExecution execution = mock(AiQuotaExecution.class);
        StudyroomGraph graph = mock(StudyroomGraph.class);
        StudyroomRepository repository = mock(StudyroomRepository.class);
        StudyroomRecords.Aggregate expected = new StudyroomRecords.Aggregate(
            new StudyroomRecords.Room("room-id", 9L, "chapter-1", "标题", "初问", "摘要", "step-1", 2,
                Instant.now(), Instant.now()), List.of(), List.of()
        );
        when(repository.findOwned("room-id", 9L)).thenReturn(Optional.of(expected));
        when(graph.followUp(9L, "room-id", "请再解释清楚一点", KnowledgeAudience.STUDENT, "req-followup")).thenReturn(expected);
        StudyroomService service = new StudyroomService(execution, graph, repository,
            new StudyroomResourceCatalog(List.of()), new ObjectMapper());

        StudyroomRecords.Aggregate actual = service.followUp(9L, "room-id", "请再解释清楚一点", KnowledgeAudience.STUDENT, "req-followup");

        assertThat(actual.room().id()).isEqualTo("room-id");
        verify(execution).requireFormalAuthentication(9L);
        verify(repository).findOwned("room-id", 9L);
        verify(graph).followUp(9L, "room-id", "请再解释清楚一点", KnowledgeAudience.STUDENT, "req-followup");
    }

    @Test
    void doesNotRunFollowUpGraphForAnotherUsersRoom() {
        AiQuotaExecution execution = mock(AiQuotaExecution.class);
        StudyroomGraph graph = mock(StudyroomGraph.class);
        StudyroomRepository repository = mock(StudyroomRepository.class);
        when(repository.findOwned("room-id", 8L)).thenReturn(Optional.empty());
        StudyroomService service = new StudyroomService(execution, graph, repository,
            new StudyroomResourceCatalog(List.of()), new ObjectMapper());

        assertThatThrownBy(() -> service.followUp(8L, "room-id", "追问", KnowledgeAudience.STUDENT, "req-unauthorized"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("STUDYROOM_NOT_FOUND"));
        verifyNoInteractions(graph);
    }
}
