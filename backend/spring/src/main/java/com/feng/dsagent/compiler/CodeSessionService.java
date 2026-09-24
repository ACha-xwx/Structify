package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Keeps the interactive sessions alive, and makes sure a bored browser tab cannot leave a program
 * running forever.
 */
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
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sandbox-sweeper");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger rejectedForCapacity = new AtomicInteger();

    @Autowired
    CodeSessionService(SandboxProperties properties) {
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
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SANDBOX_DISABLED",
                "交互式运行环境未启用"
            );
        }
        if (sessions.size() >= properties.maximumSessions()) {
            rejectedForCapacity.incrementAndGet();
            throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "SANDBOX_SESSION_LIMITED",
                "正在运行的程序太多，请稍后再试"
            );
        }
        Path workDirectory;
        try {
            Path parent = properties.workDirectoryPathOrNull();
            workDirectory = parent == null
                ? Files.createTempDirectory("structify-sandbox-")
                : Files.createTempDirectory(parent, "structify-sandbox-");
        } catch (IOException error) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SANDBOX_UNAVAILABLE",
                "运行环境暂时不可用"
            );
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
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SANDBOX_UNAVAILABLE",
                "运行环境暂时不可用"
            );
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
            throw new ApiException(HttpStatus.NOT_FOUND, "SANDBOX_SESSION_NOT_FOUND", "运行会话不存在或已结束");
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
    void sweep() {
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
