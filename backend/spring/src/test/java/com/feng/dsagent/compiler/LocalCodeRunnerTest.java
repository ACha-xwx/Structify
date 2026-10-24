package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.feng.dsagent.common.ApiException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The local runner is the path that keeps a learner's C program on this machine. These tests have to
 * pass on a machine with no compiler at all, so the routing decisions are proved with a compiler
 * that cannot possibly exist, and only the real compile-and-run case asks whether gcc is present.
 */
class LocalCodeRunnerTest {

    @Test
    void servesOnlyCAndOnlyWhenTheHostEnabledIt() {
        LocalCodeRunner disabled = new LocalCodeRunner(sandbox(false, "gcc"), properties());
        LocalCodeRunner enabled = new LocalCodeRunner(sandbox(true, "gcc"), properties());

        assertThat(disabled.canExecute(SupportedLanguage.C)).isFalse();
        assertThat(enabled.canExecute(SupportedLanguage.C)).isTrue();
        assertThat(enabled.canExecute(SupportedLanguage.PYTHON)).isFalse();
    }

    @Test
    void sendsCToTheUpstreamWhileTheSandboxIsOff() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        CompilerService service = new CompilerService(
            properties(),
            (language, code, stdin) -> {
                calls.incrementAndGet();
                saved.set(language == SupportedLanguage.C ? 1 : 0);
                return new CompilerExecution("success", "from-upstream", "");
            },
            null,
            new LocalCodeRunner(sandbox(false, "gcc"), properties())
        );

        RunCodeResponse response = service.run(new RunCodeRequest("c", "int main(){}", ""));

        assertThat(response.stdout()).isEqualTo("from-upstream");
        assertThat(calls).hasValue(1);
        assertThat(saved).hasValue(1);
    }

    @Test
    void leavesPythonToTheUpstreamEvenWhenTheSandboxIsOn() {
        AtomicInteger calls = new AtomicInteger();
        CompilerService service = new CompilerService(
            properties(),
            (language, code, stdin) -> {
                calls.incrementAndGet();
                return new CompilerExecution("success", "from-upstream", "");
            },
            null,
            new LocalCodeRunner(sandbox(true, "gcc"), properties())
        );

        RunCodeResponse response = service.run(new RunCodeRequest("python", "print('x')", ""));

        assertThat(response.stdout()).isEqualTo("from-upstream");
        assertThat(calls).hasValue(1);
    }

    /**
     * A broken local toolchain must say so, not quietly fall back: the fallback is the remote
     * sandbox, and the remote sandbox is what produced the 504s this runner exists to remove.
     */
    @Test
    void reportsABrokenToolchainInsteadOfFallingBackToTheUpstream() {
        AtomicInteger calls = new AtomicInteger();
        LocalCodeRunner runner = new LocalCodeRunner(
            sandbox(true, "structify-compiler-that-does-not-exist"),
            properties()
        );
        CompilerService service = new CompilerService(
            properties(),
            (language, code, stdin) -> {
                calls.incrementAndGet();
                return new CompilerExecution("success", "from-upstream", "");
            },
            null,
            runner
        );

        assertThatThrownBy(() -> service.run(new RunCodeRequest("c", "int main(){}", "")))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.code()).isEqualTo("SANDBOX_UNAVAILABLE");
            });
        assertThat(calls).hasValue(0);
    }

    @Test
    void compilesAndRunsAProgramBesideTheApi() {
        assumeTrue(gccAvailable(), "gcc is not installed on this machine");
        LocalCodeRunner runner = new LocalCodeRunner(sandbox(true, "gcc"), properties());

        CompilerExecution ok = runner.execute(
            SupportedLanguage.C,
            "#include <stdio.h>\nint main(void){printf(\"hi\\n\");return 0;}",
            ""
        );
        assertThat(ok.status()).isEqualTo("success");
        assertThat(ok.stdout()).isEqualTo("hi\n");
        assertThat(ok.stderr()).isEmpty();

        CompilerExecution broken = runner.execute(SupportedLanguage.C, "int main(void){return", "");
        assertThat(broken.status()).isEqualTo("compile_error");
        assertThat(broken.stderr()).isNotBlank();

        CompilerExecution crashed = runner.execute(
            SupportedLanguage.C,
            "#include <stdio.h>\nint main(void){printf(\"x\\n\");fflush(stdout);*(int*)0=1;return 0;}",
            ""
        );
        assertThat(crashed.status()).isEqualTo("runtime_error");
    }

    /**
     * Textbook samples print Chinese, and a batch run has to hand a program its input in one piece.
     * Both are the things a remote sandbox used to quietly get wrong, and both are only observable
     * with a real compiler in front of them.
     */
    @Test
    void carriesChineseOutputAndFeedsTheWholeStandardInput() {
        assumeTrue(gccAvailable(), "gcc is not installed on this machine");
        LocalCodeRunner runner = new LocalCodeRunner(sandbox(true, "gcc"), properties());

        CompilerExecution chinese = runner.execute(
            SupportedLanguage.C,
            "#include <stdio.h>\nint main(void){printf(\"链表中文输出\\n\");return 0;}",
            ""
        );
        assertThat(chinese.status()).isEqualTo("success");
        assertThat(chinese.stdout()).isEqualTo("链表中文输出\n");

        CompilerExecution scanning = runner.execute(
            SupportedLanguage.C,
            "#include <stdio.h>\nint main(void){int n;if(scanf(\"%d\",&n)==1)printf(\"%d\\n\",n*2);return 0;}",
            "21\n"
        );
        assertThat(scanning.status()).isEqualTo("success");
        assertThat(scanning.stdout()).isEqualTo("42\n");
    }

    @Test
    void killsAProgramThatNeverFinishes() {
        assumeTrue(gccAvailable(), "gcc is not installed on this machine");
        LocalCodeRunner runner = new LocalCodeRunner(sandbox(true, "gcc"), properties());

        CompilerExecution execution = runner.execute(
            SupportedLanguage.C,
            "int main(void){while(1){}return 0;}",
            ""
        );

        assertThat(execution.status()).isEqualTo("runtime_error");
        assertThat(execution.stderr()).contains("运行超时");
    }

    private static SandboxProperties sandbox(boolean enabled, String gccCommand) {
        return new SandboxProperties(enabled, gccCommand, "python3", 8, 60, 300, 10_000, 4_096, null);
    }

    private static CompilerProperties properties() {
        return new CompilerProperties(
            "http://127.0.0.1:1",
            Duration.ofSeconds(1),
            100,
            50,
            64,
            4,
            1,
            10_000,
            3_000
        );
    }

    private static boolean gccAvailable() {
        try {
            Process probe = new ProcessBuilder("gcc", "--version").start();
            return probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
