import type { CodeRunRequest, CodeRunResponse } from "../types/contracts";

/** Error codes that mean "the sandbox is saturated right now", not "your code is broken". */
const BUSY_CODES = new Set(["COMPILER_UPSTREAM_BUSY", "COMPILER_CONCURRENCY_LIMITED"]);

export interface BusyRetryOptions {
  attempts?: number;
  delayMs?: number;
  /** Called before each retry so the surface can say what is happening. */
  onRetry?: (attempt: number) => void;
}

/**
 * Runs code, and when the sandbox answers "busy" waits a moment and tries again — a learner who
 * clicks 运行 in a full classroom should not have to understand rate limiting. Anything else
 * (compile error, quota, a broken sandbox) is passed straight through.
 */
export async function runCodeWithBusyRetry(
  run: (input: CodeRunRequest) => Promise<CodeRunResponse>,
  input: CodeRunRequest,
  options: BusyRetryOptions = {},
): Promise<CodeRunResponse> {
  const attempts = options.attempts ?? 2;
  const delayMs = options.delayMs ?? 3000;
  let lastError: unknown;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await run(input);
    } catch (error) {
      lastError = error;
      const busy = BUSY_CODES.has(String((error as { code?: string } | null)?.code ?? ""));
      if (!busy || attempt === attempts) throw error;
      options.onRetry?.(attempt);
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  throw lastError;
}
