package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs one learner program on this machine instead of posting it to a remote sandbox.
 *
 * <p>The remote sandboxes sit on the far side of a slow public link: a submission that compiles and
 * runs in milliseconds spends several seconds in the network, and whenever the public instance is
 * busy the request dies at the egress timeout and the learner is handed a 504. The image already
 * carries gcc for the interactive console, so a batch run can be answered the same way - as a child
 * process beside the API, with no third party in the path at all.
 *
 * <p>Only C is served here, and this runner never pretends otherwise. A program that outlives its
 * deadline is killed rather than left spinning, and its work directory is deleted with it, so neither
 * an infinite loop nor a full disk can outlive the request.
 */
@Service
public final class LocalCodeRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalCodeRunner.class);
    private static final String TIMEOUT_MESSAGE = "运行超时，程序可能存在死循环";
    /**
     * The remote deadline is mostly wire, so it cannot be the local one. Five seconds of this host's
     * own CPU is already an unusually long classroom program, and stopping there keeps one learner's
     * stray loop from holding a compiler while the rest of the class waits behind it.
     */
    private static final long LOCAL_DEADLINE_MILLIS = 5_000L;

    private final SandboxProperties sandbox;
    private final CompilerProperties compiler;
    /**
     * Compiling is CPU-bound work now, and it costs real memory inside a container that measures it
     * in megabytes - measured at roughly 21 MB per build here. A remote run only held a socket, so
     * the concurrency the gateway was allowed is far more than this host can carry. One compile per
     * core is what the machine can actually do in parallel; anything above that just makes every
     * learner slower and risks the container's own limit.
     */
    private final Semaphore slots = new Semaphore(
        Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())),
        true
    );

    @Autowired
    public LocalCodeRunner(SandboxProperties sandbox, CompilerProperties compiler) {
        this.sandbox = sandbox;
        this.compiler = compiler;
    }

    /** C only, and only where the host actually enabled the sandbox. */
    public boolean canExecute(SupportedLanguage language) {
        return sandbox.enabled() && language == SupportedLanguage.C;
    }

    public CompilerExecution execute(SupportedLanguage language, String code, String stdin) {
        try {
            slots.acquire();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw interrupted();
        }
        try {
            return buildAndRun(language, code, stdin);
        } finally {
            slots.release();
        }
    }

    private CompilerExecution buildAndRun(SupportedLanguage language, String code, String stdin) {
        Path workDirectory = createWorkDirectory();
        // The session owns the build - source file, the unbuffered-output header, the compiler flags
        // and the cleanup. Only the run below differs: a batch run hands the whole standard input
        // over at once instead of keeping the program alive between keystrokes.
        InteractiveCodeSession session = new InteractiveCodeSession(
            UUID.randomUUID().toString(),
            language,
            sandbox,
            workDirectory
        );
        try {
            String build = session.build(code);
            if (!build.isBlank()) {
                return new CompilerExecution("compile_error", "", build);
            }
            return run(workDirectory, stdin);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("local code runner could not execute a program: {}", error.getMessage());
            throw sandboxUnavailable();
        } finally {
            session.close();
        }
    }

    private CompilerExecution run(Path workDirectory, String stdin) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(executable(workDirectory).toString())
            .directory(workDirectory.toFile());
        Process process = builder.start();

        // Both streams are drained on their own threads before the input is written. Reading them in
        // turn would deadlock the moment a chatty program fills a pipe the parent has not started
        // emptying, and these are blocking reads, so they get platform threads rather than virtual ones.
        // The cap is the batch response's own: whatever the service will not hand back is not worth
        // holding in memory here, and stopping the reader at it is what makes a runaway printer block
        // on a full pipe instead of filling the heap.
        int limit = compiler.maximumOutputLength();
        AtomicReference<String> stdout = new AtomicReference<>("");
        AtomicReference<String> stderr = new AtomicReference<>("");
        Thread out = drain(process.getInputStream(), stdout, limit);
        Thread err = drain(process.getErrorStream(), stderr, limit);

        try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            if (stdin != null && !stdin.isEmpty()) {
                writer.write(stdin);
                writer.flush();
            }
        } catch (IOException closed) {
            // A program that never reads its input exits early; a closed standard input is an EOF.
        }

        // The gateway's deadline is mostly wire, so it cannot be this one: a program that compiles in
        // a fraction of a second and then runs on this host's own CPU still gets the local ceiling,
        // and that ceiling is short enough that a learner's stray loop is answered rather than held.
        boolean finished = process.waitFor(executionDeadlineMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            join(out, err);
            String partial = stderr.get();
            return new CompilerExecution(
                "runtime_error",
                stdout.get(),
                partial.isEmpty() ? TIMEOUT_MESSAGE : partial + "\n" + TIMEOUT_MESSAGE
            );
        }
        join(out, err);
        int exitCode = process.exitValue();
        return new CompilerExecution(exitCode == 0 ? "success" : "runtime_error", stdout.get(), stderr.get());
    }

    private long executionDeadlineMillis() {
        return Math.min(compiler.timeout().toMillis(), LOCAL_DEADLINE_MILLIS);
    }

    private Path createWorkDirectory() {
        Path parent = sandbox.workDirectoryPathOrNull();
        try {
            return parent == null
                ? Files.createTempDirectory("structify-run-")
                : Files.createTempDirectory(parent, "structify-run-");
        } catch (IOException error) {
            log.warn("local code runner could not create a work directory: {}", error.getMessage());
            throw sandboxUnavailable();
        }
    }

    private static Thread drain(InputStream source, AtomicReference<String> target, int limit) {
        return Thread.ofPlatform().name("local-code-reader").start(() -> target.set(read(source, limit)));
    }

    private static void join(Thread... threads) {
        for (Thread thread : threads) {
            try {
                thread.join(2_000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String read(InputStream source, int limit) {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[2048];
        // Decoding through a reader instead of slicing raw bytes: a multi-byte Chinese character that
        // straddled a buffer edge would otherwise arrive as a pair of replacement characters, and
        // textbook code prints Chinese constantly.
        try (InputStreamReader reader = new InputStreamReader(source, StandardCharsets.UTF_8)) {
            int read;
            while (out.length() < limit && (read = reader.read(buffer)) > 0) {
                out.append(buffer, 0, read);
            }
        } catch (IOException ended) {
            // Killed or closed: whatever arrived is what the learner sees.
        }
        return out.toString();
    }

    private static Path executable(Path workDirectory) {
        String name = System.getProperty("os.name", "").toLowerCase().contains("win")
            ? "program.exe"
            : "program";
        return workDirectory.resolve(name);
    }

    private static ApiException sandboxUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_UNAVAILABLE", "运行环境暂时不可用");
    }

    private static ApiException interrupted() {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "COMPILER_EXECUTION_INTERRUPTED",
            "代码执行请求已中断，请重试"
        );
    }
}
