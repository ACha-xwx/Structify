package com.feng.dsagent.chat;

import com.feng.dsagent.aiquota.AiQuotaRequestId;
import com.feng.dsagent.aiquota.AiStreamAbortedException;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeAudience;
import com.feng.dsagent.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /**
     * A platform-thread scheduler, deliberately not virtual: its whole job is to speak up when the
     * answer stops arriving, and a virtual thread cannot do that if the scheduler itself is the thing
     * that is stuck.
     */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "chat-stream-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /** How long a stream may go without writing anything before the learner is told. */
    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(45);

    private final ChatService chat;
    private final ChatHistoryService history;
    private final ChatRateLimiter rateLimiter;

    public ChatController(ChatService chat, ChatHistoryService history, ChatRateLimiter rateLimiter) {
        this.chat = chat;
        this.history = history;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    ChatResponse complete(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest,
        @Valid @RequestBody ChatRequest request
    ) {
        Long userId = user == null ? null : user.userId();
        rateLimiter.check(userId, servletRequest.getRemoteAddr());
        return chat.complete(request.command(), userId, KnowledgeAudience.from(user), AiQuotaRequestId.from(servletRequest));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(
        @AuthenticationPrincipal AuthenticatedUser user,
        HttpServletRequest servletRequest,
        @Valid @RequestBody ChatRequest request
    ) {
        Long userId = user == null ? null : user.userId();
        rateLimiter.check(userId, servletRequest.getRemoteAddr());
        chat.requireFormalAuthentication(userId);
        SseEmitter emitter = new SseEmitter(70_000L);
        // Commit the response headers now with an SSE comment (clients ignore ":" lines). Without it
        // the headers wait for the first real event - retrieval plus the model's first token - and a
        // slow model day reads to the browser as a network timeout of its own client clock.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException neverBeforeAsyncStart) {
            // Cannot happen before the emitter is returned; nothing to unwind yet.
        }
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        AtomicLong lastWrite = new AtomicLong(System.currentTimeMillis());
        AtomicReference<Thread> worker = new AtomicReference<>();
        long startedAt = System.nanoTime();
        Runnable abort = () -> {
            closed.set(true);
            if (!finished.get()) {
                Thread running = worker.get();
                if (running != null) {
                    running.interrupt();
                }
            }
            emitter.complete();
        };
        emitter.onCompletion(() -> {
            closed.set(true);
            if (!finished.get()) {
                Thread running = worker.get();
                if (running != null) {
                    running.interrupt();
                }
            }
        });
        emitter.onError(error -> abort.run());
        emitter.onTimeout(() -> {
            log.warn("chat stream: emitter timed out after {} ms without the worker finishing", millis(startedAt));
            abort.run();
        });
        ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
            if (finished.get() || closed.get()) return;
            long silentMillis = System.currentTimeMillis() - lastWrite.get();
            if (silentMillis < SILENCE_LIMIT.toMillis()) return;
            log.warn("chat stream: nothing written for {} ms, closing with an error", silentMillis);
            if (finished.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("code", "CHAT_STREAM_STALLED", "message", "模型迟迟没有回应")));
                } catch (IOException | IllegalStateException ignored) {
                    // The stream is already gone; the client sees the close either way.
                }
                emitter.complete();
            }
        }, SILENCE_LIMIT.toMillis(), 5_000L, TimeUnit.MILLISECONDS);
        KnowledgeAudience audience = KnowledgeAudience.from(user);
        // A platform thread on purpose. This box fits two carriers, and the worker writes to the
        // servlet response - a path that takes internal monitors - so keeping it off the virtual-thread
        // scheduler removes a way for one slow answer to hold up every other virtual thread on the box.
        Thread streamThread = Thread.ofPlatform().daemon().name("chat-stream-", 1).start(() -> runStream(
            emitter,
            request.command(),
            userId,
            audience,
            AiQuotaRequestId.from(servletRequest),
            closed,
            finished,
            lastWrite,
            startedAt,
            watchdog
        ));
        worker.set(streamThread);
        if (closed.get() && !finished.get()) {
            streamThread.interrupt();
        }
        return emitter;
    }

    private void runStream(
        SseEmitter emitter,
        ChatCommand command,
        Long userId,
        KnowledgeAudience audience,
        String requestId,
        AtomicBoolean closed,
        AtomicBoolean finished,
        AtomicLong lastWrite,
        long startedAt,
        ScheduledFuture<?> watchdog
    ) {
        AtomicBoolean firstDelta = new AtomicBoolean();
        try {
            log.info("chat stream: start user={} promptChars={}", userId,
                command.prompt() == null ? 0 : command.prompt().length());
            ChatResponse response = chat.stream(
                command,
                userId,
                audience,
                requestId,
                sources -> {
                    log.info("chat stream: sources={} after {} ms", sources.size(), millis(startedAt));
                    send(emitter, "sources", sources, closed, lastWrite);
                },
                content -> {
                    if (firstDelta.compareAndSet(false, true)) {
                        log.info("chat stream: first token after {} ms", millis(startedAt));
                    }
                    send(emitter, "delta", Map.of("content", content), closed, lastWrite);
                }
            );
            log.info("chat stream: done after {} ms answerChars={}", millis(startedAt),
                response.answer() == null ? 0 : response.answer().length());
            send(emitter, "done", response, closed, lastWrite);
            finished.set(true);
            emitter.complete();
        } catch (AiStreamAbortedException ignored) {
            log.info("chat stream: aborted after {} ms", millis(startedAt));
            finished.set(true);
            emitter.complete();
        } catch (ApiException error) {
            log.warn("chat stream: refused after {} ms code={} message={}", millis(startedAt), error.code(),
                error.getMessage());
            send(emitter, "error", Map.of("code", error.code(), "message", error.getMessage()), closed, lastWrite);
            finished.set(true);
            emitter.complete();
        } catch (RuntimeException error) {
            log.warn("chat stream: failed after {} ms", millis(startedAt), error);
            send(emitter, "error", Map.of("code", "CHAT_STREAM_FAILED", "message", "流式回答中断"), closed, lastWrite);
            finished.set(true);
            emitter.complete();
        } finally {
            watchdog.cancel(false);
        }
    }

    private static long millis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private void send(SseEmitter emitter, String name, Object data, AtomicBoolean closed, AtomicLong lastWrite) {
        if (closed.get()) {
            throw new AiStreamAbortedException();
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            lastWrite.set(System.currentTimeMillis());
        } catch (IOException error) {
            throw new AiStreamAbortedException(error);
        }
    }

    @GetMapping("/sessions")
    List<ChatSessionSummary> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return history.sessions(user.userId());
    }

    @GetMapping("/sessions/{id}")
    ChatSessionView session(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return history.session(user.userId(), id);
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> deleteSession(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        history.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    public record ChatRequest(
        @NotBlank @Size(max = 4000) String prompt,
        @Size(max = 64) String chapterId,
        @Size(max = 64) String sessionId,
        @Size(max = 12) List<@Valid TurnRequest> history
    ) {
        ChatCommand command() {
            List<ChatTurn> turns = history == null ? List.of() : history.stream()
                .map(turn -> new ChatTurn(turn.role(), turn.content()))
                .toList();
            return new ChatCommand(prompt, chapterId, sessionId, turns);
        }
    }

    public record TurnRequest(
        @NotBlank @Size(max = 16) String role,
        @NotBlank @Size(max = 4000) String content
    ) {
    }
}
