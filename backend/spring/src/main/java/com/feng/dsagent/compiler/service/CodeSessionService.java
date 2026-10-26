package com.feng.dsagent.compiler.service;


import org.springframework.stereotype.Service;
import com.feng.dsagent.compiler.support.InteractiveCodeSession;
import com.feng.dsagent.compiler.config.SandboxProperties;
import com.feng.dsagent.compiler.domain.SupportedLanguage;
import com.feng.dsagent.compiler.error.CompilerErrorCode;
import com.feng.dsagent.common.exception.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public final class CodeSessionService {

    /** What starting a session produced - either a live program, or the compiler's complaint. */
    public record SessionStart(String sessionId, String status, String output) {}

    private static final Logger log = LoggerFactory.getLogger(CodeSessionService.class);
    private static final long SWEEP_INTERVAL_SECONDS = 15;
    private static final int MAXIMUM_SESSIONS_PER_CLIENT = 3;

    private final SandboxProperties properties;
    private final Map<String, InteractiveCodeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashSet<String>> ownedSessions = new ConcurrentHashMap<>();
    /**
     * Named single-thread sweeper (not an unbounded pool): periodically reaps idle sandbox sessions.
     * Kept as a dedicated platform thread so session cleanup is independent of request threads.
     */
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sandbox-sweeper");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger rejectedForCapacity = new AtomicInteger();

    @Autowired
    public CodeSessionService(SandboxProperties properties) {
        this.properties = properties;
        sweeper.scheduleAtFixedRate(
            this::sweep,
            SWEEP_INTERVAL_SECONDS,
            SWEEP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public SessionStart start(String clientKey, SupportedLanguage language, String code) {
        if (!properties.enabled()) {
            throw CompilerErrorCode.SANDBOX_DISABLED.toException();
        }
        if (sessions.size() >= properties.maximumSessions()) {
            rejectedForCapacity.incrementAndGet();
            throw CompilerErrorCode.SANDBOX_SESSION_LIMITED.toException();
        }
        Path workDirectory;
        try {
            Path parent = properties.workDirectoryPathOrNull();
            workDirectory = parent == null
                ? Files.createTempDirectory("structify-sandbox-")
                : Files.createTempDirectory(parent, "structify-sandbox-");
        } catch (IOException error) {
            throw CompilerErrorCode.SANDBOX_UNAVAILABLE.toException();
        }

        String id = UUID.randomUUID().toString();
        InteractiveCodeSession session = new InteractiveCodeSession(id, language, properties, workDirectory);
        try {
            String buildOutput = session.build(code);
            if (!buildOutput.isBlank()) {
                session.close();
                return new SessionStart(null, "compile_error", buildOutput);
            }
            session.start();
        } catch (IOException | InterruptedException error) {
            session.close();
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("sandbox could not start a session: {}", error.getMessage());
            throw CompilerErrorCode.SANDBOX_UNAVAILABLE.toException();
        }
        sessions.put(id, session);
        // The execute routes are open to anonymous callers, so a single browser must not be able to
        // fill the whole pool - keeping a few each leaves room for the rest of the class.
        LinkedHashSet<String> owned = ownedSessions.computeIfAbsent(clientKey, key -> new LinkedHashSet<>());
        owned.add(id);
        while (owned.size() > MAXIMUM_SESSIONS_PER_CLIENT) {
            String oldest = owned.iterator().next();
            owned.remove(oldest);
            close(oldest);
        }
        return new SessionStart(id, "running", "");
    }

    public InteractiveCodeSession require(String id) {
        InteractiveCodeSession session = id == null ? null : sessions.get(id);
        if (session == null) {
            throw CompilerErrorCode.SANDBOX_SESSION_NOT_FOUND.toException();
        }
        return session;
    }

    public void close(String id) {
        InteractiveCodeSession session = id == null ? null : sessions.remove(id);
        if (session != null) {
            session.close();
        }
    }

    public int activeSessions() {
        return sessions.size();
    }

    public int rejectedForCapacity() {
        return rejectedForCapacity.get();
    }

    /** Drops sessions that went idle, or that have simply lived long enough. */
    public void sweep() {
        long now = System.currentTimeMillis();
        long idleMillis = TimeUnit.SECONDS.toMillis(properties.sessionIdleTimeoutSeconds());
        long lifetimeMillis = TimeUnit.SECONDS.toMillis(properties.sessionMaximumLifetimeSeconds());
        for (Map.Entry<String, InteractiveCodeSession> entry : sessions.entrySet()) {
            InteractiveCodeSession session = entry.getValue();
            if (session.isExpired(now, idleMillis, lifetimeMillis)) {
                sessions.remove(entry.getKey());
                ownedSessions.values().forEach(owned -> owned.remove(entry.getKey()));
                session.close();
                log.info("sandbox session {} reaped", entry.getKey());
            }
        }
    }
}
