package com.feng.dsagent.compiler.service;

import com.feng.dsagent.compiler.config.CompilerProperties;
import com.feng.dsagent.compiler.mapper.CodeRunMapper;
import java.util.UUID;
import com.feng.dsagent.compiler.domain.CompilerExecution;
import com.feng.dsagent.compiler.domain.SupportedLanguage;
import com.feng.dsagent.compiler.support.CompilerGateway;
import com.feng.dsagent.compiler.worker.LocalCodeRunner;
import com.feng.dsagent.compiler.vo.RunCodeVO;
import com.feng.dsagent.compiler.dto.RunCodeDTO;

import com.feng.dsagent.compiler.error.CompilerErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class CompilerService {

    private static final String TRUNCATION_MARKER = "[truncated]";

    private final CompilerProperties properties;
    private final CompilerGateway gateway;
    private final CodeRunMapper codeRuns;
    private final LocalCodeRunner local;

    public CompilerService(CompilerProperties properties, CompilerGateway gateway) {
        this(properties, gateway, null, null);
    }

    public CompilerService(CompilerProperties properties, CompilerGateway gateway, CodeRunMapper codeRuns) {
        this(properties, gateway, codeRuns, null);
    }

    @Autowired
    public CompilerService(
        CompilerProperties properties,
        CompilerGateway gateway,
        CodeRunMapper codeRuns,
        LocalCodeRunner local
    ) {
        this.properties = properties;
        this.gateway = gateway;
        this.codeRuns = codeRuns;
        this.local = local;
    }

    public RunCodeVO run(RunCodeDTO request) {
        return run(request, null);
    }

    public RunCodeVO run(RunCodeDTO request, Long userId) {
        if (request == null) {
            throw CompilerErrorCode.REQUEST_INVALID.toException();
        }
        SupportedLanguage language = SupportedLanguage.fromApiName(request.language())
            .orElseThrow(CompilerErrorCode.LANGUAGE_UNSUPPORTED::toException);
        String code = request.code();
        String stdin = request.stdin() == null ? "" : request.stdin();

        if (code == null || code.isBlank()) {
            throw CompilerErrorCode.CODE_REQUIRED.toException();
        }
        if (code.length() > properties.maximumCodeLength()) {
            throw CompilerErrorCode.CODE_TOO_LONG.toException();
        }
        if (stdin.length() > properties.maximumInputLength()) {
            throw CompilerErrorCode.INPUT_TOO_LONG.toException();
        }
        String chapterId = normalizeChapterId(request.chapterId());
        if (userId != null && chapterId != null && codeRuns != null && codeRuns.countPublishedChapter(chapterId) == 0) {
            throw CompilerErrorCode.CHAPTER_INVALID.toException();
        }

        long startedAt = System.nanoTime();
        // A local run answers in milliseconds, so it always wins where it is possible. The remote
        // gateway only takes the languages this host cannot compile itself.
        CompilerExecution execution = local != null && local.canExecute(language)
            ? local.execute(language, code, stdin)
            : gateway.execute(language, code, stdin);
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        RunCodeVO response = new RunCodeVO(
            language.apiName(),
            execution.status(),
            truncate(execution.stdout()),
            truncate(execution.stderr()),
            durationMs,
            null
        );
        if (userId != null && codeRuns != null) {
            String runId = saveCodeRun(userId, chapterId, request, response);
            return new RunCodeVO(
                response.language(),
                response.status(),
                response.stdout(),
                response.stderr(),
                response.durationMs(),
                runId
            );
        }
        return response;
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

    private String truncate(String value) {
        String output = value == null ? "" : value;
        int maximumLength = properties.maximumOutputLength();
        if (output.length() <= maximumLength) {
            return output;
        }
        if (maximumLength <= TRUNCATION_MARKER.length()) {
            return TRUNCATION_MARKER.substring(0, maximumLength);
        }
        return output.substring(0, maximumLength - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
    }


    private String saveCodeRun(long userId, String chapterId, RunCodeDTO request, RunCodeVO response) {
        String id = UUID.randomUUID().toString();
        codeRuns.insert(
            id,
            userId,
            chapterId == null || chapterId.isBlank() ? null : chapterId.trim(),
            response.language(),
            request.code(),
            request.stdin(),
            response.status(),
            response.stdout(),
            response.stderr(),
            response.durationMs()
        );
        return id;
    }
}
