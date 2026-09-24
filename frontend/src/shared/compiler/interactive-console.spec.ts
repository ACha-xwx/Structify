import { describe, expect, it, vi } from "vitest";
import { createInteractiveConsole, type ConsoleTransport } from "./interactive-console";
import type { SseEvent } from "../api/client";
import type { CodeSessionChunk } from "../types/contracts";

/** A stream that stays open, the way a program that has not finished yet does. */
function openStream() {
  const queue: Array<SseEvent<CodeSessionChunk>> = [];
  let wake: ((event: SseEvent<CodeSessionChunk>) => void) | null = null;
  const events = (async function* () {
    for (;;) {
      const next = queue.shift();
      if (next) yield next;
      else yield await new Promise<SseEvent<CodeSessionChunk>>((resolve) => { wake = resolve; });
    }
  })();
  return {
    events,
    push(event: SseEvent<CodeSessionChunk>) {
      if (wake) {
        const resolve = wake;
        wake = null;
        resolve(event);
      } else {
        queue.push(event);
      }
    },
  };
}

function transportWith(overrides: Partial<ConsoleTransport> = {}) {
  const stream = openStream();
  const transport: ConsoleTransport = {
    start: vi.fn().mockResolvedValue({ sessionId: "s1", status: "running", output: "" }),
    stream: vi.fn().mockResolvedValue(stream),
    send: vi.fn().mockResolvedValue(undefined),
    stop: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  return { transport, stream };
}

async function settle() {
  for (let round = 0; round < 4; round++) await Promise.resolve();
}

describe("interactive console", () => {
  it("keeps what the program printed before anyone was watching", async () => {
    const { transport, stream } = transportWith();
    const console_ = createInteractiveConsole(transport);

    await console_.start("c", "int main(void){ return 0; }");
    stream.push({ event: "chunk", data: "", parsed: { stream: "stdout", text: "请输入第一个数：" } });
    await settle();

    expect(console_.phase.value).toBe("live");
    expect(console_.output.value).toBe("请输入第一个数：");
    expect(console_.awaitingInput.value).toBe(true);
  });

  it("hands what was typed to the program and echoes it", async () => {
    const { transport } = transportWith();
    const console_ = createInteractiveConsole(transport);

    await console_.start("c", "int main(void){ return 0; }");
    await console_.send("5");

    expect(transport.send).toHaveBeenCalledWith("s1", "5\n");
    expect(console_.output.value).toBe("5\n");
  });

  it("keeps stderr apart so a warning is not lost in the output", async () => {
    const { transport, stream } = transportWith();
    const console_ = createInteractiveConsole(transport);

    await console_.start("c", "int main(void){ return 0; }");
    stream.push({ event: "chunk", data: "", parsed: { stream: "stderr", text: "warning: unused\n" } });
    await settle();

    expect(console_.output.value).toBe("");
    expect(console_.errors.value).toBe("warning: unused\n");
  });

  it("shows the compiler's complaint and never asks for input", async () => {
    const { transport } = transportWith({
      start: vi.fn().mockResolvedValue({ sessionId: null, status: "compile_error", output: "error: expected ';'\n" }),
    });
    const console_ = createInteractiveConsole(transport);

    const started = await console_.start("c", "int main(void){ return 0 }");

    expect(started).toBe(true);
    expect(console_.phase.value).toBe("failed");
    expect(console_.errors.value).toContain("expected ';'");
    expect(console_.awaitingInput.value).toBe(false);
  });

  it("says the runner is unavailable so the caller can run the code in one shot", async () => {
    const { transport } = transportWith({
      start: vi.fn().mockRejectedValue(Object.assign(new Error("交互式运行环境未启用"), { code: "SANDBOX_DISABLED" })),
    });
    const console_ = createInteractiveConsole(transport);

    await expect(console_.start("c", "int main(void){ return 0; }")).resolves.toBe(false);
    expect(console_.phase.value).toBe("idle");
  });

  it("reports any other failure instead of silently falling back", async () => {
    const { transport } = transportWith({
      start: vi.fn().mockRejectedValue(new Error("网络断了")),
    });
    const console_ = createInteractiveConsole(transport, "运行失败");

    await expect(console_.start("c", "int main(void){ return 0; }")).resolves.toBe(true);
    expect(console_.phase.value).toBe("failed");
    expect(console_.failure.value).toBe("网络断了");
  });

  it("ends the program and forgets the session", async () => {
    const { transport } = transportWith();
    const console_ = createInteractiveConsole(transport);

    await console_.start("c", "int main(void){ return 0; }");
    await console_.stop();

    expect(transport.stop).toHaveBeenCalledWith("s1");
    expect(console_.phase.value).toBe("done");
    expect(console_.awaitingInput.value).toBe(false);
  });

  it("turns the old Windows line endings into the newlines a console should show", async () => {
    const { transport, stream } = transportWith();
    const console_ = createInteractiveConsole(transport);

    await console_.start("c", "int main(void){ return 0; }");
    stream.push({ event: "chunk", data: "", parsed: { stream: "stdout", text: "a\r\nb\r\n" } });
    await settle();

    expect(console_.output.value).toBe("a\nb\n");
  });
});
