package com.feng.dsagent.animation;

import com.feng.dsagent.common.ApiException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Client for the local deterministic DSVP engine ({@code backend/dsvp/dsvp-service.js}).
 *
 * <p>The animation labour split is deliberate: the large model only decides *whether* to animate and
 * *which* capability with which arguments; every frame of every trace is computed by this local script.
 * That keeps the classroom and the lab honest — a model can ask for a red-black rotation, but it can
 * never invent the frames of one.
 *
 * <p>The engine runs as one long-lived child process speaking line-delimited JSON, so an animation costs
 * one round trip instead of a Node start-up. A dead or missing engine is reported as "unavailable" rather
 * than an error: the caller decides whether to fall back.
 */
@Component
public final class DsvpLocalEngine {

    private static final Logger log = LoggerFactory.getLogger(DsvpLocalEngine.class);
    private static final int MAXIMUM_REQUEST_BYTES = 256 * 1024;

    private final boolean enabled;
    private final String command;
    private final Path script;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    /**
     * Guards the child process's writer. A {@code ReentrantLock} rather than a monitor on purpose: a
     * virtual thread that blocks on a monitor is pinned to its carrier, and this JVM runs one carrier
     * per core - a wedged write here would take the whole virtual-thread scheduler down with it, so
     * the lock is also only ever acquired with a bound.
     */
    private final java.util.concurrent.locks.ReentrantLock lifecycle = new java.util.concurrent.locks.ReentrantLock();
    /**
     * Writes go through one dedicated platform thread so a wedged child process - which stops reading
     * its stdin and lets the pipe fill - can never block the caller. The caller waits on the write with
     * the same bound it uses for the reply, and restarts the process when that runs out.
     */
    private final java.util.concurrent.ExecutorService engineWriter =
        java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "dsvp-engine-writer");
            thread.setDaemon(true);
            return thread;
        });
    private volatile Process process;
    private volatile BufferedWriter writer;

    public DsvpLocalEngine(
        @Value("${app.dsvp.engine.enabled:true}") boolean enabled,
        @Value("${app.dsvp.engine.command:node}") String command,
        @Value("${app.dsvp.engine.script:backend/dsvp/dsvp-service.js}") String script,
        @Value("${app.dsvp.engine.timeout-ms:6000}") long timeoutMillis,
        ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.command = command;
        this.script = Path.of(script).toAbsolutePath().normalize();
        this.timeout = Duration.ofMillis(timeoutMillis < 200 ? 200 : timeoutMillis);
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return enabled;
    }

    /** True when the engine answers a health probe; used by readiness reporting and tests. */
    public boolean healthy() {
        return call("health", node -> {}).isPresent();
    }

    public Optional<JsonNode> capabilities(String lessonId) {
        return call("capabilities", node -> {
            if (lessonId != null && !lessonId.isBlank()) node.put("lessonId", lessonId);
        });
    }

    /** Chapter and lesson ids both begin with the textbook chapter number ("08-tree", "08-02"). */
    private static final java.util.regex.Pattern LEADING_CHAPTER = java.util.regex.Pattern.compile("^(\\d{1,2})");

    /**
     * The scope the registry filters by: the leading chapter number plus a dash, so "08-tree" and "08-02"
     * both ask for chapter 8. A value without a leading number is passed through and the engine answers
     * with the whole registry.
     */
    public static String chapterScope(String chapterOrLessonId) {
        String value = chapterOrLessonId == null ? "" : chapterOrLessonId.trim();
        java.util.regex.Matcher matcher = LEADING_CHAPTER.matcher(value);
        return matcher.find() ? matcher.group(1) + "-" : value;
    }

    /**
     * The registry's own prompt fragment for one chapter: every capability it can really simulate, each
     * with its required arguments. Callers pass this to the model instead of a hand-written list, so a
     * prompt can never claim an implemented algorithm is missing - or offer one that does not exist.
     * Blank means the engine is unreachable, and the caller should fall back rather than guess.
     */
    public String catalogueFor(String chapterOrLessonId) {
        return capabilities(chapterScope(chapterOrLessonId))
            .map(node -> node.path("prompt").asText(""))
            .orElse("");
    }

    public Optional<JsonNode> resolve(JsonNode intent, JsonNode options) {
        return call("resolve", node -> {
            node.set("intent", intent == null ? objectMapper.createObjectNode() : intent);
            node.set("options", options == null ? objectMapper.createObjectNode() : options);
        });
    }

    /**
     * Simulates one DSVP request locally. Empty means the engine is unreachable — not that the request was
     * invalid; a rejected request throws with the engine's own error code so the client sees the reason.
     */
    public Optional<JsonNode> simulate(JsonNode request) {
        return call("simulate", node -> node.set("request", request));
    }

    private Optional<JsonNode> call(String operation, java.util.function.Consumer<ObjectNode> fill) {
        if (!enabled) return Optional.empty();
        if (!start()) return Optional.empty();
        long id = sequence.incrementAndGet();
        ObjectNode message = objectMapper.createObjectNode();
        message.put("id", id);
        message.put("op", operation);
        fill.accept(message);
        String line = message.toString();
        if (line.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_REQUEST_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DSVP_REQUEST_TOO_LARGE", "DSVP 请求过大");
        }
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        long startedAt = System.nanoTime();
        try {
            if (!lifecycle.tryLock(2, TimeUnit.SECONDS)) {
                pending.remove(id);
                log.warn("DSVP 本地引擎写入通道被占用超过 2 秒，本次调用放弃：{}", operation);
                return Optional.empty();
            }
            try {
                BufferedWriter out = writer;
                if (out == null) {
                    pending.remove(id);
                    return Optional.empty();
                }
                java.util.concurrent.Future<?> write = engineWriter.submit(() -> {
                    try {
                        out.write(line);
                        out.newLine();
                        out.flush();
                    } catch (IOException error) {
                        throw new java.io.UncheckedIOException(error);
                    }
                });
                try {
                    write.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException error) {
                    write.cancel(true);
                    pending.remove(id);
                    log.warn("DSVP 本地引擎写入超时（{}），重启子进程", operation);
                    shutdown();
                    return Optional.empty();
                } catch (ExecutionException error) {
                    pending.remove(id);
                    log.warn("DSVP 本地引擎写入失败：{}", String.valueOf(error.getCause()));
                    shutdown();
                    return Optional.empty();
                }
            } finally {
                lifecycle.unlock();
            }
            JsonNode reply = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            if (elapsedMillis > 1000) {
                log.warn("DSVP 本地引擎响应较慢：{} 用时 {} ms", operation, elapsedMillis);
            }
            if (reply.path("ok").asBoolean(false)) return Optional.of(reply);
            throw engineFailure(reply.path("error"));
        } catch (TimeoutException error) {
            pending.remove(id);
            log.warn("DSVP 本地引擎超时（{}），重启子进程", operation);
            shutdown();
            return Optional.empty();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            pending.remove(id);
            return Optional.empty();
        } catch (ExecutionException error) {
            log.warn("DSVP 本地引擎调用失败：{}", error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
            return Optional.empty();
        }
    }

    private ApiException engineFailure(JsonNode error) {
        String code = error.path("code").asText("DSVP_ENGINE_ERROR");
        String message = error.path("message").asText("本地动画引擎拒绝了这次请求");
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private boolean start() {
        if (process != null && process.isAlive()) return true;
        lifecycle.lock();
        try {
            if (process != null && process.isAlive()) return true;
            if (!Files.isRegularFile(script)) {
                log.warn("DSVP 本地引擎脚本不存在：{}", script);
                return false;
            }
            try {
                ProcessBuilder builder = new ProcessBuilder(List.of(command, script.toString()));
                builder.directory(script.getParent().toFile());
                Process started = builder.start();
                this.process = started;
                this.writer = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
                // Virtual threads are always daemon threads, so there is nothing to mark here; calling
                // setDaemon on an already-started thread throws IllegalThreadStateException.
                Thread.ofVirtual().name("dsvp-engine-reader").start(() -> read(started));
                Thread.ofVirtual().name("dsvp-engine-stderr").start(() -> drain(started));
                return true;
            } catch (IOException error) {
                log.warn("DSVP 本地引擎启动失败（{} {}）：{}", command, script, error.getMessage());
                this.process = null;
                this.writer = null;
                return false;
            }
        } finally {
            lifecycle.unlock();
        }
    }

    private void read(Process source) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(source.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                JsonNode reply;
                try {
                    reply = objectMapper.readTree(line);
                } catch (RuntimeException ignored) {
                    continue;
                }
                CompletableFuture<JsonNode> future = pending.remove(reply.path("id").asLong(-1));
                if (future != null) future.complete(reply);
            }
        } catch (IOException error) {
            log.debug("DSVP 本地引擎输出结束：{}", error.getMessage());
        } finally {
            failPending();
            process = null;
            writer = null;
        }
    }

    private void drain(Process source) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(source.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) log.debug("dsvp-engine: {}", line);
        } catch (IOException ignored) {
            // The engine exited; the reader thread reports the meaningful failure.
        }
    }

    private void failPending() {
        List<Long> ids = new ArrayList<>(pending.keySet());
        for (Long id : ids) {
            CompletableFuture<JsonNode> future = pending.remove(id);
            if (future != null) future.completeExceptionally(new IllegalStateException("DSVP engine stopped"));
        }
    }

    /** Stops the child process; the next call starts a fresh one. */
    public void shutdown() {
        lifecycle.lock();
        try {
            failPending();
            Process current = process;
            process = null;
            writer = null;
            if (current != null && current.isAlive()) current.destroy();
        } finally {
            lifecycle.unlock();
        }
    }

    @jakarta.annotation.PreDestroy
    void onShutdown() {
        shutdown();
    }

    private final AtomicBoolean closed = new AtomicBoolean();

    /** Marks the component closed so a late probe cannot restart it during context shutdown. */
    void close() {
        if (closed.compareAndSet(false, true)) shutdown();
    }
}
