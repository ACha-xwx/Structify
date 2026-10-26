package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.animation.entity.AnimationRecordEntity;
import com.feng.dsagent.animation.mapper.AnimationMapper;
import com.feng.dsagent.animation.service.AnimationObservationService;
import com.feng.dsagent.animation.vo.AnimationObservationVO;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.learning.mapper.LearningEventMapper;
import com.feng.dsagent.learning.mapper.LearningEventMapper.LearningEventInsert;
import com.feng.dsagent.learning.service.LearningEventService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class AnimationObservationServiceTest {

    @Test
    void storesAnOwnedObservationAndAddsItToLearningProgress() {
        FakeAnimationMapper animations = new FakeAnimationMapper();
        animations.records.add(new AnimationRecordEntity("animation-1", "03-stack-queue"));
        FakeLearningEvents events = new FakeLearningEvents();
        AnimationObservationService service =
                new AnimationObservationService(animations, new LearningEventService(events, new ObjectMapper()));

        AnimationObservationVO view = service.record(7L, "animation-1", "后进先出，3 最后入栈却最先出栈。");

        assertThat(view.recordId()).isEqualTo("animation-1");
        assertThat(view.observation()).contains("后进先出");
        assertThat(animations.updated).containsExactly("animation-1:后进先出，3 最后入栈却最先出栈。");
        assertThat(events.saved).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("ANIMATION_OBSERVATION");
            assertThat(event.chapterId()).isEqualTo("03-stack-queue");
            assertThat(event.payloadJson()).contains("后进先出");
        });
    }

    @Test
    void doesNotRevealAnimationsOwnedByAnotherUser() {
        AnimationObservationService service = new AnimationObservationService(
                new FakeAnimationMapper(), new LearningEventService(new FakeLearningEvents(), new ObjectMapper()));

        assertThatThrownBy(() -> service.record(7L, "other-user-record", "观察"))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.code()).isEqualTo("ANIMATION_RECORD_NOT_FOUND");
                });
    }

    private static final class FakeAnimationMapper implements AnimationMapper {
        private final List<AnimationRecordEntity> records = new ArrayList<>();
        private final List<String> updated = new ArrayList<>();

        @Override
        public int countPublishedChapter(String chapterId) {
            return 1;
        }

        @Override
        public int insertRecord(
                String id, long userId, String chapterId, String animationType, String title, String payloadJson) {
            return 0;
        }

        @Override
        public AnimationRecordEntity findOwned(long userId, String recordId) {
            return records.stream().filter(record -> record.id().equals(recordId)).findFirst().orElse(null);
        }

        @Override
        public int insertObservation(
                String recordId,
                long userId,
                String observation,
                String sourceType,
                String sourceRef,
                String versionLabel) {
            updated.add(recordId + ":" + observation);
            return 1;
        }

        @Override
        public int insertObservationSimple(String recordId, long userId, String observation) {
            return insertObservation(recordId, userId, observation, null, null, null);
        }

        @Override
        public int updateRecordObservation(String recordId, long userId, String observation) {
            return 1;
        }
    }

    private static final class FakeLearningEvents implements LearningEventMapper {
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
