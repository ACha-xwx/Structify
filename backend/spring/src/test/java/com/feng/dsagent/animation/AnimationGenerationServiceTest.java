package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.animation.domain.AnimationDefinition;
import com.feng.dsagent.animation.dto.AnimationGenerationCommandDTO;
import com.feng.dsagent.animation.entity.AnimationRecordEntity;
import com.feng.dsagent.animation.mapper.AnimationMapper;
import com.feng.dsagent.animation.service.AnimationGenerationService;
import com.feng.dsagent.animation.support.AnimationValidator;
import com.feng.dsagent.animation.vo.AnimationGenerationVO;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.model.ModelStreamHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class AnimationGenerationServiceTest {

    private final FakeModelClient model = new FakeModelClient();
    private final FakeAnimationMapper mapper = new FakeAnimationMapper();
    private final AnimationGenerationService service =
            new AnimationGenerationService(model, new AnimationValidator(), mapper, new ObjectMapper());

    @Test
    void parsesFencedModelJsonAndReturnsValidatedGuestAnimationWithoutPersisting() {
        model.response =
                """
            ```json
            {
              "type":"stack",
              "title":"入栈与出栈",
              "steps":[
                {"op":"push","note":"元素 1 进入栈顶","value":1},
                {"op":"pop","note":"栈顶元素离开","value":null}
              ]
            }
            ```
            """;

        AnimationGenerationVO response =
                service.generate(new AnimationGenerationCommandDTO("演示 1 入栈后再出栈", "stack", "03-stack-queue"), null);

        assertThat(response.definition().type()).isEqualTo("stack");
        assertThat(response.definition().steps()).hasSize(2);
        assertThat(response.persisted()).isFalse();
        assertThat(response.recordId()).isNull();
        assertThat(mapper.saved).isEmpty();
        assertThat(model.request.messages().getFirst().content())
                .contains("只返回 JSON")
                .contains("禁止 HTML")
                .contains("stack");
    }

    @Test
    void rejectsUnsupportedOperationReturnedByModel() {
        model.response =
                """
            {"type":"stack","title":"危险动画","steps":[
              {"op":"executeScript","note":"运行脚本","value":"alert(1)"}
            ]}
            """;

        assertThatThrownBy(() -> service.generate(new AnimationGenerationCommandDTO("演示栈", "stack", null), null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(error.code()).isEqualTo("ANIMATION_MODEL_INVALID");
                });
    }

    @Test
    void persistsValidatedAnimationForAuthenticatedUser() {
        model.response =
                """
            {"type":"queue","title":"队列操作","steps":[
              {"op":"enqueue","note":"元素 A 从队尾入队","value":"A"}
            ]}
            """;

        AnimationGenerationVO response =
                service.generate(new AnimationGenerationCommandDTO("演示入队", "queue", "03-stack-queue"), 9L);

        assertThat(response.persisted()).isTrue();
        assertThat(response.recordId()).isNotBlank();
        assertThat(mapper.saved).singleElement().satisfies(saved -> {
            assertThat(saved.userId()).isEqualTo(9);
            assertThat(saved.definition().type()).isEqualTo("queue");
            assertThat(saved.id()).isEqualTo(response.recordId());
        });
    }

    @Test
    void rejectsUnknownChapterBeforeCallingModel() {
        mapper.chapterExists = false;

        assertThatThrownBy(
                        () -> service.generate(new AnimationGenerationCommandDTO("演示未知章节", "stack", "99-missing"), 9L))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.code()).isEqualTo("ANIMATION_CHAPTER_INVALID");
                });

        assertThat(model.request).isNull();
        assertThat(mapper.saved).isEmpty();
    }

    private static final class FakeModelClient implements ModelClient {
        private String response;
        private ModelRequest request;

        @Override
        public ModelResponse complete(ModelRequest request) {
            this.request = request;
            return new ModelResponse(response);
        }

        @Override
        public void stream(ModelRequest request, ModelStreamHandler handler) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeAnimationMapper implements AnimationMapper {
        private final List<SavedAnimation> saved = new ArrayList<>();
        private boolean chapterExists = true;

        @Override
        public int countPublishedChapter(String chapterId) {
            return chapterExists ? 1 : 0;
        }

        @Override
        public int insertRecord(
                String id, long userId, String chapterId, String animationType, String title, String payloadJson) {
            saved.add(new SavedAnimation(
                    id, userId, new AnimationDefinition(animationType, title, List.of())));
            return 1;
        }

        @Override
        public AnimationRecordEntity findOwned(long userId, String recordId) {
            return null;
        }

        @Override
        public int insertObservation(
                String recordId,
                long userId,
                String observation,
                String sourceType,
                String sourceRef,
                String versionLabel) {
            return 0;
        }

        @Override
        public int insertObservationSimple(String recordId, long userId, String observation) {
            return 0;
        }

        @Override
        public int updateRecordObservation(String recordId, long userId, String observation) {
            return 0;
        }
    }

    private record SavedAnimation(String id, long userId, AnimationDefinition definition) {}
}
