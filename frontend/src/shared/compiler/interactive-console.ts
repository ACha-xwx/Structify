import { computed, ref, type ComputedRef, type Ref } from "vue";
import type { SseEvent } from "../api/client";
import type { CodeLanguage, CodeSessionChunk, CodeSessionStart } from "../types/contracts";

/**
 * A console that talks to a program while it runs.
 *
 * The program is a live process on the server: it speaks whenever it wants (over server-sent
 * events), and the learner answers whenever they are ready (over an ordinary request). Everything
 * it prints is kept, because a program that asks a question has already printed the question by the
 * time anyone is watching.
 *
 * The transport is injected so the console can be driven by the real API or by a scripted one in
 * tests.
 */
export type ConsolePhase = "idle" | "starting" | "live" | "done" | "failed";

export interface ConsoleTransport {
  start(input: { language: CodeLanguage; code: string }): Promise<CodeSessionStart>;
  stream(sessionId: string, signal: AbortSignal): Promise<{ events: AsyncGenerator<SseEvent<CodeSessionChunk>> }>;
  send(sessionId: string, text: string): Promise<void>;
  stop(sessionId: string): Promise<void>;
}

export interface InteractiveConsole {
  phase: Ref<ConsolePhase>;
  /** Everything the program printed on stdout, in the order it printed it. */
  output: Ref<string>;
  /** Everything it printed on stderr, kept apart so a warning is not lost in the output. */
  errors: Ref<string>;
  exitCode: Ref<number | null>;
  failure: Ref<string>;
  /** The program is running and will read whatever is typed at it. */
  awaitingInput: ComputedRef<boolean>;
  /**
   * Starts the code. Returns false when there is no interactive runner to talk to - the caller
   * should fall back to running the code in one shot.
   */
  start(language: CodeLanguage, code: string): Promise<boolean>;
  /** Hands one line to the program, and echoes it the way a terminal would. */
  send(text: string): Promise<void>;
  /** Ends the program and forgets the session. */
  stop(): Promise<void>;
}

/** Failures that mean "no live runner right now" rather than "this code is broken". */
function isUnavailable(error: unknown): boolean {
  const code = (error as { code?: unknown } | null)?.code;
  return typeof code === "string" && code.startsWith("SANDBOX_");
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

/** Windows sandboxes end lines the old way; a console should not show the carriage returns. */
function normalize(text: string): string {
  return text.replace(/\r\n/g, "\n");
}

export function createInteractiveConsole(
  transport: ConsoleTransport,
  fallbackMessage = "运行失败",
): InteractiveConsole {
  const phase = ref<ConsolePhase>("idle");
  const output = ref("");
  const errors = ref("");
  const exitCode = ref<number | null>(null);
  const failure = ref("");
  const awaitingInput = computed(() => phase.value === "live");
  let sessionId: string | null = null;
  let controller: AbortController | null = null;

  function append(stream: string, text: string): void {
    const chunk = normalize(text);
    if (stream === "stderr") {
      errors.value += chunk;
    } else {
      output.value += chunk;
    }
  }

  async function drain(id: string, signal: AbortSignal): Promise<void> {
    const response = await transport.stream(id, signal);
    for await (const event of response.events) {
      if (signal.aborted) return;
      if (event.event === "exit") {
        const parsed = event.parsed;
        exitCode.value = typeof parsed === "number" ? parsed : Number(event.data);
        phase.value = "done";
        return;
      }
      if (event.event === "chunk" && event.parsed) {
        append(event.parsed.stream, event.parsed.text);
      }
    }
    // The stream ended without a goodbye: the program is gone either way.
    if (phase.value === "live" || phase.value === "starting") phase.value = "done";
  }

  async function stop(): Promise<void> {
    controller?.abort();
    controller = null;
    const id = sessionId;
    sessionId = null;
    if (id) {
      try {
        await transport.stop(id);
      } catch {
        // A program that already exited has nothing to stop.
      }
    }
    if (phase.value === "live" || phase.value === "starting") phase.value = "done";
  }

  async function start(language: CodeLanguage, code: string): Promise<boolean> {
    await stop();
    output.value = "";
    errors.value = "";
    exitCode.value = null;
    failure.value = "";
    phase.value = "starting";

    let started: CodeSessionStart;
    try {
      started = await transport.start({ language, code });
    } catch (error) {
      if (isUnavailable(error)) {
        phase.value = "idle";
        return false;
      }
      failure.value = messageOf(error, fallbackMessage);
      phase.value = "failed";
      return true;
    }

    if (started.status === "compile_error" || !started.sessionId) {
      errors.value = normalize(started.output ?? "");
      failure.value = "";
      phase.value = "failed";
      return true;
    }

    sessionId = started.sessionId;
    phase.value = "live";
    const signal = new AbortController();
    controller = signal;
    void drain(started.sessionId, signal.signal).catch((error: unknown) => {
      if (signal.signal.aborted) return;
      failure.value = messageOf(error, fallbackMessage);
      phase.value = "failed";
    });
    return true;
  }

  async function send(text: string): Promise<void> {
    const id = sessionId;
    if (!id || phase.value !== "live") return;
    const line = text.endsWith("\n") ? text : `${text}\n`;
    output.value += normalize(line);
    try {
      await transport.send(id, line);
    } catch (error) {
      failure.value = messageOf(error, fallbackMessage);
    }
  }

  return { phase, output, errors, exitCode, failure, awaitingInput, start, send, stop };
}
