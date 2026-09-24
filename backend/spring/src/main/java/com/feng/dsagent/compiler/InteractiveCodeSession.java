package com.feng.dsagent.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One learner program, kept alive between keystrokes.
 *
 * <p>The upstream batch sandboxes (Judge0, Piston) hand the program its whole standard input up
 * front and kill it the moment it asks for more, which is why the console could never read like a
 * terminal. Here the program runs as a child process with its standard input left open, so a call
 * to {@code scanf} simply blocks until the learner types - exactly what a terminal does.
 *
 * <p>The other half of the problem is buffering: on a pipe the C runtime buffers standard output,
 * so a prompt written without a trailing newline never reaches the browser. Rather than allocate a
 * pseudo terminal, the build injects a header that switches standard output to unbuffered mode
 * before {@code main} runs. See {@code sandbox/structify-io.h}.
 */
final class InteractiveCodeSession implements AutoCloseable {

    /** What a connected console needs to hear about. */
    interface Listener {
        void onChunk(String stream, String text);

        void onExit(int exitCode);
    }

    private static final Logger log = LoggerFactory.getLogger(InteractiveCodeSession.class);
    private static final String TRUNCATION_MARKER = "[truncated]";
    private static final int COMPILE_OUTPUT_LIMIT = 8_000;

    private final String id;
    private final SupportedLanguage language;
    private final SandboxProperties properties;
    private final Path workDirectory;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<String[]> history = new CopyOnWriteArrayList<>();
    private final AtomicInteger emittedLength = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long createdAt = System.currentTimeMillis();

    private volatile Process process;
    private volatile Writer stdinWriter;
    private volatile Integer exitCode;
    private volatile long lastActivityAt = System.currentTimeMillis();

    InteractiveCodeSession(String id, SupportedLanguage language, SandboxProperties properties, Path workDirectory) {
        this.id = id;
        this.language = language;
        this.properties = properties;
        this.workDirectory = workDirectory;
    }

    String id() {
        return id;
    }

    long lastActivityAt() {
        return lastActivityAt;
    }

    boolean isExpired(long now, long idleMillis, long lifetimeMillis) {
        return now - lastActivityAt > idleMillis || now - createdAt > lifetimeMillis;
    }

    /**
     * Subscribes a console, replaying what the program already said.
     *
     * <p>Starting a session and subscribing to it are two requests, and a program that greets you
     * immediately has finished talking before the second one arrives. Without the replay the
     * console would open blank on exactly the programs that matter most - the ones that prompt.
     */
    void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            for (String[] past : history) {
                listener.onChunk(past[0], past[1]);
            }
        }
        Integer finished = exitCode;
        if (finished != null) {
            listener.onExit(finished);
        }
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Writes the source, injects the unbuffered-output header, and builds it. Returns the compiler's
     * own words so a failed build can be shown verbatim instead of hidden behind a generic message.
     */
    String build(String code) throws IOException, InterruptedException {
        Path source = workDirectory.resolve(language.fileName());
        Files.writeString(source, code, StandardCharsets.UTF_8);
        Path header = workDirectory.resolve("structify-io.h");
        Files.writeString(header, InteractiveRuntimeHeader.CONTENT, StandardCharsets.UTF_8);

        List<String> command = switch (language) {
            case C -> List.of(
                properties.gccCommand(),
                "-include",
                header.toString(),
                // Keep the program's own bytes honest so Chinese prompts survive the trip to the
                // browser on every platform, not just on UTF-8 hosts.
                "-fexec-charset=UTF-8",
                "-finput-charset=UTF-8",
                "-o",
                executableName(),
                source.toString()
            );
            case PYTHON -> List.of(properties.pythonCommand(), "-m", "py_compile", source.toString());
        };

        ProcessBuilder builder = new ProcessBuilder(command).directory(workDirectory.toFile());
        builder.redirectErrorStream(true);
        Process build = builder.start();
        String output = readFully(build.getInputStream(), COMPILE_OUTPUT_LIMIT);
        boolean finished = build.waitFor(properties.compileTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            build.destroyForcibly();
            return "编译超时，请检查代码是否过长或过于复杂";
        }
        return build.exitValue() == 0 ? "" : output;
    }

    /** Starts the program and begins forwarding whatever it prints. */
    void start() throws IOException {
        List<String> command = switch (language) {
            case C -> List.of(workDirectory.resolve(executableName()).toString());
            case PYTHON -> List.of(properties.pythonCommand(), "-u", language.fileName());
        };
        ProcessBuilder builder = new ProcessBuilder(command).directory(workDirectory.toFile());
        process = builder.start();
        stdinWriter = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);

        CountDownLatch drained = new CountDownLatch(2);
        pump(process.getInputStream(), "stdout", drained);
        pump(process.getErrorStream(), "stderr", drained);

        Thread watcher = new Thread(() -> awaitExit(drained), "sandbox-exit-" + id);
        watcher.setDaemon(true);
        watcher.start();
        lastActivityAt = System.currentTimeMillis();
    }

    /** Hands the learner's line to the program, exactly as pressing Enter in a terminal would. */
    void write(String text) throws IOException {
        Writer writer = stdinWriter;
        if (writer == null) {
            throw new IOException("session is not running");
        }
        writer.write(text);
        writer.flush();
        lastActivityAt = System.currentTimeMillis();
    }

    boolean isRunning() {
        Process current = process;
        return current != null && current.isAlive();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Process current = process;
        if (current != null) {
            current.destroyForcibly();
        }
        deleteRecursively(workDirectory);
    }

    private String executableName() {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
            ? "program.exe"
            : "program";
    }

    private void pump(InputStream in, String stream, CountDownLatch drained) {
        Thread thread = new Thread(() -> {
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) > 0) {
                    lastActivityAt = System.currentTimeMillis();
                    if (!emit(stream, new String(buffer, 0, read))) {
                        // The program will not stop printing, so stop listening to it.
                        Process current = process;
                        if (current != null) {
                            current.destroyForcibly();
                        }
                        break;
                    }
                }
            } catch (IOException error) {
                log.debug("sandbox {} stopped reading {}: {}", id, stream, error.getMessage());
            } finally {
                drained.countDown();
            }
        }, "sandbox-" + stream + "-" + id);
        thread.setDaemon(true);
        thread.start();
    }

    private void awaitExit(CountDownLatch drained) {
        try {
            process.waitFor();
            // Give the pumps a moment to deliver whatever is still in flight before announcing death.
            drained.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        int finished = process.exitValue();
        exitCode = finished;
        for (Listener listener : listeners) {
            listener.onExit(finished);
        }
    }

    /**
     * Forwards one chunk, refusing to grow past the configured cap.
     *
     * <p>The text is escaped to pure ASCII on the way out: the edge proxy rejects responses that
     * carry raw non-ASCII bytes, and a learner's program is free to print Chinese. The console
     * unescapes it after JSON parsing.
     */
    private boolean emit(String stream, String text) {
        int budget = properties.maximumOutputLength();
        int already = emittedLength.get();
        if (already >= budget) {
            return false;
        }
        String chunk = text.length() + already <= budget ? text : text.substring(0, budget - already);
        emittedLength.set(already + chunk.length());
        // The text travels as it is: the console's JSON writer escapes it to ASCII on the way out,
        // and escaping here as well would leave the learner looking at literal backslash-u codes.
        history.add(new String[] { stream, chunk });
        synchronized (listeners) {
            for (Listener listener : listeners) {
                listener.onChunk(stream, chunk);
            }
        }
        if (emittedLength.get() >= budget) {
            history.add(new String[] { stream, TRUNCATION_MARKER });
            synchronized (listeners) {
                for (Listener listener : listeners) {
                    listener.onChunk(stream, TRUNCATION_MARKER);
                }
            }
            return false;
        }
        return true;
    }

    private static String readFully(InputStream in, int limit) throws IOException {
        StringBuilder out = new StringBuilder();
        byte[] buffer = new byte[4096];
        int read;
        while (out.length() < limit && (read = in.read(buffer)) > 0) {
            out.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return out.length() >= limit ? out.substring(0, limit) : out.toString();
    }

    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    log.debug("sandbox could not delete {}: {}", path, error.getMessage());
                }
            });
        } catch (IOException error) {
            log.debug("sandbox could not list {}: {}", root, error.getMessage());
        }
    }
}
