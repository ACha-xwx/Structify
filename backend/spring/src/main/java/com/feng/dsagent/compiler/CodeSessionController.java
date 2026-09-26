package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.annotation.JsonSerialize;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The interactive console's other half: a session to run the program in, a stream to watch it, and
 * a place to type.
 *
 * <p>Output travels over server-sent events because the program decides when to speak; input travels
 * over ordinary requests because the learner decides when to type.
 */
@RestController
@RequestMapping("/api/v1/code/sessions")
public final class CodeSessionController {

    record StartRequest(String language, String code) {}

    record StdinRequest(String text) {}

    /**
     * One piece of the program's output. The text is written ASCII-escaped for the same reason the
     * batch route does it: the edge proxy refuses responses carrying raw non-ASCII bytes, and a
     * learner's program is free to print Chinese. The console unescapes it when it parses the JSON.
     */
    record Chunk(String stream, @JsonSerialize(using = AsciiSafeStringSerializer.class) String text) {}

    private static final Logger log = LoggerFactory.getLogger(CodeSessionController.class);

    private final CodeSessionService sessions;

    @Autowired
    public CodeSessionController(CodeSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public CodeSessionService.SessionStart start(
        @RequestBody StartRequest request,
        HttpServletRequest servletRequest,
        Authentication authentication
    ) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPILER_CODE_REQUIRED", "代码不能为空");
        }
        SupportedLanguage language = SupportedLanguage.fromApiName(request.language())
            .orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMPILER_LANGUAGE_UNSUPPORTED",
                "仅支持 C 和 Python"
            ));
        return sessions.start(clientKey(authentication, servletRequest), language, request.code());
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        try {
            return openStream(id);
        } catch (RuntimeException error) {
            log.error("console stream for session {} failed", id, error);
            throw error;
        }
    }

    private SseEmitter openStream(String id) {
        InteractiveCodeSession session = sessions.require(id);
        // No timeout of our own: the program decides how long the console lives. A zero timeout
        // here would mean "expire immediately", which is why this is the no-argument form.
        SseEmitter emitter = new SseEmitter();
        InteractiveCodeSession.Listener listener = new InteractiveCodeSession.Listener() {
            @Override
            public void onChunk(String stream, String text) {
                send(emitter, "chunk", new Chunk(stream, text));
            }

            @Override
            public void onExit(int exitCode) {
                send(emitter, "exit", exitCode);
                emitter.complete();
            }
        };
        session.addListener(listener);
        emitter.onCompletion(() -> session.removeListener(listener));
        emitter.onTimeout(() -> session.removeListener(listener));
        emitter.onError(error -> {
            log.warn("console stream {} failed: {}", id, error.getMessage());
            session.removeListener(listener);
        });
        return emitter;
    }

    @PostMapping("/{id}/stdin")
    public void type(@PathVariable String id, @RequestBody StdinRequest request) {
        InteractiveCodeSession session = sessions.require(id);
        String text = request == null || request.text() == null ? "" : request.text();
        try {
            session.write(text);
        } catch (IOException error) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SANDBOX_SESSION_NOT_FOUND",
                "运行会话已结束，请重新运行"
            );
        }
    }

    @DeleteMapping("/{id}")
    public void stop(@PathVariable String id) {
        sessions.close(id);
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException error) {
            emitter.completeWithError(error);
        }
    }

    private static String clientKey(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.userId();
        }
        String remoteAddress = request.getRemoteAddr();
        return "ip:" + (remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress);
    }
}
