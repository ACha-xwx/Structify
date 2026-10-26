package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.compiler.config.CompilerProperties;
import com.feng.dsagent.compiler.dto.CodeAnalysisDTO;
import com.feng.dsagent.compiler.entity.CodeRunSnapshotEntity;
import com.feng.dsagent.compiler.mapper.CodeRunMapper;
import com.feng.dsagent.compiler.service.CodeAnalysisService;
import com.feng.dsagent.compiler.vo.CodeAnalysisVO;
import com.feng.dsagent.learning.mapper.LearningEventMapper;
import com.feng.dsagent.learning.service.LearningEventService;
import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.model.ModelStreamHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class CodeAnalysisServiceTest {

    @Test
    void asksModelForAConciseTeachingAnalysis() {
        FakeModelClient model = new FakeModelClient();
        model.response = "数组越界发生在 i <= n，应改为 i < n。";
        CodeAnalysisService service = new CodeAnalysisService(properties(), model);

        CodeAnalysisVO response = service.analyze(new CodeAnalysisDTO(
            "c",
            "for (int i = 0; i <= n; i++) a[i] = 0;",
            "",
            "",
            "index out of bounds",
            "runtime_error",
            "02-linear-list",
            null
        ));

        assertThat(response.analysis()).contains("i < n");
        assertThat(model.request.messages().getFirst().content())
            .contains("不要执行代码")
            .contains("修复建议");
        assertThat(model.request.messages().getLast().content())
            .contains("runtime_error")
            .contains("index out of bounds");
    }

    @Test
    void rejectsOversizedCodeBeforeCallingModel() {
        FakeModelClient model = new FakeModelClient();
        CodeAnalysisService service = new CodeAnalysisService(properties(), model);

        assertThatThrownBy(() -> service.analyze(new CodeAnalysisDTO(
            "python", "x".repeat(101), "", "", "", "success", null, null
        ))).isInstanceOfSatisfying(BizException.class, error -> {
            assertThat(error.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(error.code()).isEqualTo("COMPILER_CODE_TOO_LONG");
        });
        assertThat(model.request).isNull();
    }

    @Test
    void rejectsUnknownOrOversizedRunStatusBeforeCallingModel() {
        FakeModelClient model = new FakeModelClient();
        CodeAnalysisService service = new CodeAnalysisService(properties(), model);

        assertThatThrownBy(() -> service.analyze(new CodeAnalysisDTO(
            "c", "int main(void) { return 0; }", "", "", "", "success\" injected=\"true", null, null
        ))).isInstanceOfSatisfying(BizException.class, error -> {
            assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.code()).isEqualTo("COMPILER_STATUS_INVALID");
        });
        assertThat(model.request).isNull();
    }

    @Test
    void analyzesAnOwnedPersistedRunAndRecordsTrustedLearningEvidence() {
        FakeModelClient model = new FakeModelClient();
        model.response = "真实错误来自数组越界。";
        FakeCodeRuns runs = new FakeCodeRuns(new CodeRunSnapshotEntity(
            "run-1",
            "03-stack-queue",
            "c",
            "int values[1]; values[2] = 3;",
            "",
            "",
            "index out of bounds",
            "runtime_error"
        ));
        FakeLearningEvents events = new FakeLearningEvents();
        CodeAnalysisService service = new CodeAnalysisService(
            properties(),
            model,
            runs,
            new LearningEventService(events, new ObjectMapper())
        );

        CodeAnalysisVO response = service.analyze(new CodeAnalysisDTO(
            "python",
            "print('forged')",
            "",
            "forged output",
            "",
            "success",
            "02-linear-list",
            "run-1"
        ), 7L);

        assertThat(response.analysis()).contains("数组越界");
        assertThat(model.request.messages().getLast().content())
            .contains("int values[1]", "index out of bounds", "runtime_error")
            .doesNotContain("forged output", "print('forged')");
        assertThat(events.saved).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CODE_REVIEW");
            assertThat(event.chapterId()).isEqualTo("03-stack-queue");
            assertThat(event.referenceId()).isEqualTo("run-1");
            assertThat(event.payloadJson()).contains("真实错误来自数组越界");
        });
    }

    private CompilerProperties properties() {
        return new CompilerProperties("http://127.0.0.1:1", Duration.ofSeconds(1), 100, 50, 64, 4, 1, 10_000, 3_000);
    }

    private static final class FakeModelClient implements ModelClient {
        private ModelRequest request;
        private String response;

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

    private static final class FakeCodeRuns implements CodeRunMapper {
        private final CodeRunSnapshotEntity run;

        private FakeCodeRuns(CodeRunSnapshotEntity run) {
            this.run = run;
        }

        @Override
        public int insert(
            String id,
            long userId,
            String chapterId,
            String language,
            String sourceCode,
            String stdinText,
            String status,
            String outputText,
            String errorText,
            Long durationMs
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countPublishedChapter(String chapterId) {
            return 1;
        }

        @Override
        public CodeRunSnapshotEntity selectOwned(String runId, long userId) {
            return userId == 7L && run.id().equals(runId) ? run : null;
        }
    }

    private static final class FakeLearningEvents implements LearningEventMapper {
        private final List<SavedLearningEvent> saved = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public int countPublishedChapter(String chapterId) {
            return "03-stack-queue".equals(chapterId) ? 1 : 0;
        }

        @Override
        public int insertEvent(LearningEventInsert row) {
            row.setId(nextId++);
            saved.add(new SavedLearningEvent(
                row.getEventType(),
                row.getChapterId(),
                row.getReferenceId(),
                row.getPayloadJson()
            ));
            return 1;
        }
    }

    private record SavedLearningEvent(
        String eventType,
        String chapterId,
        String referenceId,
        String payloadJson
    ) {
    }
}
