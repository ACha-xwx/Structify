package com.feng.dsagent.compiler.service;

import com.feng.dsagent.compiler.vo.CodeAnalysisVO;
import com.feng.dsagent.compiler.dto.CodeAnalysisDTO;
import com.feng.dsagent.compiler.config.CompilerProperties;
import com.feng.dsagent.compiler.mapper.CodeRunMapper;
import com.feng.dsagent.compiler.domain.SupportedLanguage;
import com.feng.dsagent.compiler.entity.CodeRunSnapshotEntity;
import com.feng.dsagent.compiler.prompt.CodeAnalysisPrompts;

import com.feng.dsagent.aiquota.support.AiQuotaExecution;
import com.feng.dsagent.compiler.error.CompilerErrorCode;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.learning.dto.LearningEventCommandDTO;
import com.feng.dsagent.learning.service.LearningEventService;
import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.exception.ModelClientException;
import com.feng.dsagent.model.ModelMessage;
import com.feng.dsagent.model.ModelRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class CodeAnalysisService {

    private static final Set<String> RUN_STATUSES = Set.of(
        "success",
        "compile_error",
        "runtime_error",
        "unknown"
    );

    private final CompilerProperties properties;
    private final AiQuotaExecution execution;
    private final CodeRunMapper codeRuns;
    private final LearningEventService learningEvents;

    public CodeAnalysisService(CompilerProperties properties, ModelClient model) {
        this(properties, AiQuotaExecution.unmetered(model), null, null);
    }

    public CodeAnalysisService(
        CompilerProperties properties,
        ModelClient model,
        CodeRunMapper codeRuns,
        LearningEventService learningEvents
    ) {
        this(properties, AiQuotaExecution.unmetered(model), codeRuns, learningEvents);
    }

    @Autowired
    public CodeAnalysisService(
        CompilerProperties properties,
        AiQuotaExecution execution,
        CodeRunMapper codeRuns,
        LearningEventService learningEvents
    ) {
        this.properties = properties;
        this.execution = execution;
        this.codeRuns = codeRuns;
        this.learningEvents = learningEvents;
    }

    public CodeAnalysisVO analyze(CodeAnalysisDTO request) {
        return analyze(request, null);
    }

    public CodeAnalysisVO analyze(CodeAnalysisDTO request, Long userId) {
        return analyze(request, userId, null);
    }

    public CodeAnalysisVO analyze(CodeAnalysisDTO request, Long userId, String requestId) {
        execution.requireFormalAuthentication(userId);
        if (request == null) {
            throw CompilerErrorCode.ANALYSIS_INVALID.toException();
        }
        AnalysisInput input = input(request, userId);

        String content = """
            <code_run language="%s" status="%s">
            <source>
            %s
            </source>
            <stdin>
            %s
            </stdin>
            <stdout>
            %s
            </stdout>
            <stderr>
            %s
            </stderr>
            </code_run>
            """.formatted(
                input.language().apiName(),
                input.status(),
                input.code(),
                input.stdin(),
                input.stdout(),
                input.stderr()
            );
        try {
            String analysis = execution.complete(userId, "code-analysis", requestId, new ModelRequest(
                List.of(
                    new ModelMessage("system", CodeAnalysisPrompts.SYSTEM_PROMPT),
                    new ModelMessage("user", content)
                ),
                0.2,
                1200
            )).content();
            recordTrustedReview(userId, input, analysis);
            return new CodeAnalysisVO(analysis);
        } catch (ModelClientException error) {
            throw modelFailure(error);
        }
    }

    private AnalysisInput input(CodeAnalysisDTO request, Long userId) {
        String runId = normalizeRunId(request.runId());
        if (runId != null) {
            if (userId == null || codeRuns == null) {
                throw runNotFound();
            }
            CodeRunSnapshotEntity run = Optional.ofNullable(codeRuns.selectOwned(runId, userId)).orElseThrow(this::runNotFound);
            return new AnalysisInput(
                language(run.language()),
                checkedCode(run.code()),
                limited(run.stdin(), properties.maximumInputLength(), CompilerErrorCode.INPUT_TOO_LONG),
                limited(run.stdout(), properties.maximumOutputLength(), CompilerErrorCode.OUTPUT_TOO_LONG),
                limited(run.stderr(), properties.maximumOutputLength(), CompilerErrorCode.OUTPUT_STDERR_TOO_LONG),
                runStatus(run.status()),
                run.chapterId(),
                run.id()
            );
        }
        return new AnalysisInput(
            language(request.language()),
            checkedCode(request.code()),
            limited(request.stdin(), properties.maximumInputLength(), CompilerErrorCode.INPUT_TOO_LONG),
            limited(request.stdout(), properties.maximumOutputLength(), CompilerErrorCode.OUTPUT_TOO_LONG),
            limited(request.stderr(), properties.maximumOutputLength(), CompilerErrorCode.OUTPUT_STDERR_TOO_LONG),
            runStatus(request.status()),
            normalizeChapterId(request.chapterId()),
            null
        );
    }

    private SupportedLanguage language(String value) {
        return SupportedLanguage.fromApiName(value)
            .orElseThrow(CompilerErrorCode.LANGUAGE_UNSUPPORTED::toException);
    }

    private String checkedCode(String value) {
        String code = required(value, CompilerErrorCode.CODE_REQUIRED);
        if (code.length() > properties.maximumCodeLength()) {
            throw CompilerErrorCode.CODE_TOO_LONG.toException();
        }
        return code;
    }

    private String normalizeRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        String normalized = runId.trim();
        if (normalized.length() > 64) {
            throw runNotFound();
        }
        return normalized;
    }

    private String normalizeChapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) {
            return null;
        }
        String normalized = chapterId.trim();
        if (normalized.length() > 64 || !normalized.matches("^[0-9]{2}-[a-z0-9-]+$")) {
            throw CompilerErrorCode.CHAPTER_PARAM_INVALID.toException();
        }
        return normalized;
    }

    private void recordTrustedReview(Long userId, AnalysisInput input, String analysis) {
        if (userId == null || input.runId() == null || learningEvents == null) {
            return;
        }
        tools.jackson.databind.node.ObjectNode payload = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        payload.put("language", input.language().apiName());
        payload.put("status", input.status());
        payload.put("analysis", analysis.length() <= 4_000 ? analysis : analysis.substring(0, 4_000));
        learningEvents.record(userId, new LearningEventCommandDTO(
            "CODE_REVIEW",
            input.chapterId(),
            input.runId(),
            payload
        ));
    }

    private BizException runNotFound() {
        return CompilerErrorCode.RUN_NOT_FOUND.toException();
    }

    private String required(String value, CompilerErrorCode error) {
        if (value == null || value.isBlank()) {
            throw error.toException();
        }
        return value;
    }

    private String limited(String value, int maximumLength, CompilerErrorCode error) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > maximumLength) {
            throw error.toException();
        }
        return normalized;
    }

    private String runStatus(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        if (!RUN_STATUSES.contains(normalized)) {
            throw CompilerErrorCode.STATUS_INVALID.toException();
        }
        return normalized;
    }

    private BizException modelFailure(ModelClientException error) {
        // Preserve legacy status remapping: only NOT_CONFIGURED / REQUEST_TIMEOUT keep ModelErrorCode
        // statuses; every other model failure stays BAD_GATEWAY for analysis.
        return switch (error.errorCode()) {
            case MODEL_NOT_CONFIGURED, MODEL_REQUEST_TIMEOUT ->
                error.errorCode().toException("代码分析服务暂时不可用");
            default -> new BizException(
                HttpStatus.BAD_GATEWAY,
                error.code(),
                "代码分析服务暂时不可用"
            );
        };
    }

    private record AnalysisInput(
        SupportedLanguage language,
        String code,
        String stdin,
        String stdout,
        String stderr,
        String status,
        String chapterId,
        String runId
    ) {
    }
}
