import { describe, expect, it, vi } from "vitest";
import { runCodeWithBusyRetry } from "./run-with-retry";

const input = { language: "c" as const, code: "int main(void){}", stdin: "" };

function busyError() {
  return Object.assign(new Error("此刻同时运行的人较多，请过几秒再试"), { status: 429, code: "COMPILER_UPSTREAM_BUSY" });
}

describe("sandbox busy retry", () => {
  it("waits and tries again when the sandbox is busy", async () => {
    const run = vi.fn()
      .mockRejectedValueOnce(busyError())
      .mockResolvedValueOnce({ language: "c", status: "success", stdout: "ok", stderr: "", durationMs: 3, runId: null });
    const onRetry = vi.fn();

    await expect(runCodeWithBusyRetry(run, input, { delayMs: 0, onRetry })).resolves.toMatchObject({ stdout: "ok" });
    expect(run).toHaveBeenCalledTimes(2);
    expect(onRetry).toHaveBeenCalledWith(1);
  });

  it("also retries the app's own concurrency limit", async () => {
    const limited = Object.assign(new Error("代码执行任务较多"), { status: 429, code: "COMPILER_CONCURRENCY_LIMITED" });
    const run = vi.fn().mockRejectedValueOnce(limited).mockResolvedValueOnce({ status: "success" });

    await expect(runCodeWithBusyRetry(run, input, { delayMs: 0 })).resolves.toMatchObject({ status: "success" });
  });

  it("never retries a compile error, and gives up after the last attempt", async () => {
    const compileError = Object.assign(new Error("编译错误"), { status: 200, code: "COMPILER_UPSTREAM_BUSY" });
    compileError.code = "COMPILE_ERROR";
    const run = vi.fn().mockRejectedValue(compileError);
    await expect(runCodeWithBusyRetry(run, input, { delayMs: 0 })).rejects.toBe(compileError);
    expect(run).toHaveBeenCalledTimes(1);

    const alwaysBusy = vi.fn().mockRejectedValue(busyError());
    await expect(runCodeWithBusyRetry(alwaysBusy, input, { delayMs: 0, attempts: 2 })).rejects.toMatchObject({ code: "COMPILER_UPSTREAM_BUSY" });
    expect(alwaysBusy).toHaveBeenCalledTimes(2);
  });
});
