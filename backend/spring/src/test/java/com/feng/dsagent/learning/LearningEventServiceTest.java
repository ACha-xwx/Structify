package com.feng.dsagent.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.learning.dto.LearningEventCommandDTO;
import com.feng.dsagent.learning.mapper.LearningEventMapper;
import com.feng.dsagent.learning.mapper.LearningEventMapper.LearningEventInsert;
import com.feng.dsagent.learning.service.LearningEventService;
import com.feng.dsagent.learning.vo.LearningEventVO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class LearningEventServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordsAWhitelistedEventForAPublishedChapter() throws Exception {
        FakeLearningEventMapper mapper = new FakeLearningEventMapper();
        LearningEventService service = new LearningEventService(mapper, objectMapper);

        LearningEventVO event = service.record(
                7L,
                new LearningEventCommandDTO(
                        "resource_view", "03-stack-queue", "stack-pdf", objectMapper.readTree("{\"page\":2}")));

        assertThat(event.eventType()).isEqualTo("RESOURCE_VIEW");
        assertThat(event.chapterId()).isEqualTo("03-stack-queue");
        assertThat(mapper.saved).singleElement().satisfies(saved -> {
            assertThat(saved.userId()).isEqualTo(7L);
            assertThat(saved.payloadJson()).contains("page");
        });
    }

    @Test
    void rejectsUnknownEventTypesAndUnpublishedChapters() throws Exception {
        FakeLearningEventMapper mapper = new FakeLearningEventMapper();
        LearningEventService service = new LearningEventService(mapper, objectMapper);

        assertApiError(
                () -> service.record(7L, new LearningEventCommandDTO("SHELL_EXEC", null, null, null)),
                HttpStatus.BAD_REQUEST,
                "LEARNING_EVENT_TYPE_INVALID");
        assertApiError(
                () -> service.record(7L, new LearningEventCommandDTO("RESOURCE_VIEW", "99-hidden", null, null)),
                HttpStatus.BAD_REQUEST,
                "LEARNING_CHAPTER_INVALID");
    }

    @Test
    void userSubmittedEventsCannotClaimServerManagedActivities() {
        FakeLearningEventMapper mapper = new FakeLearningEventMapper();
        LearningEventService service = new LearningEventService(mapper, objectMapper);

        assertApiError(
                () -> service.recordUserSubmitted(
                        7L, new LearningEventCommandDTO("CLASSROOM_ANSWER", "03-stack-queue", "session-1", null)),
                HttpStatus.BAD_REQUEST,
                "LEARNING_EVENT_SERVER_MANAGED");
        assertThat(mapper.saved).isEmpty();
    }

    private static void assertApiError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class, error -> {
            assertThat(error.status()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
        });
    }

    private static final class FakeLearningEventMapper implements LearningEventMapper {
        private final List<SavedEvent> saved = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public int countPublishedChapter(String chapterId) {
            return "03-stack-queue".equals(chapterId) ? 1 : 0;
        }

        @Override
        public int insertEvent(LearningEventInsert row) {
            row.setId(nextId++);
            saved.add(new SavedEvent(
                    row.getUserId(), row.getEventType(), row.getChapterId(), row.getReferenceId(), row.getPayloadJson()));
            return 1;
        }
    }

    private record SavedEvent(
            long userId, String eventType, String chapterId, String referenceId, String payloadJson) {}
}
