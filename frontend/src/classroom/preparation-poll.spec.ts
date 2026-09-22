import { describe, expect, it } from "vitest";
import { preparationPollCount, preparationPollDelayMs } from "./preparation-poll";

describe("preparation poll pacing", () => {
  it("asks almost at once, because a short lesson can be ready immediately", () => {
    expect(preparationPollDelayMs(0)).toBe(1200);
  });

  it("widens the gap after every unanswered poll", () => {
    const gaps = [0, 1, 2, 3, 4].map(preparationPollDelayMs);

    expect(gaps).toEqual([...gaps].sort((a, b) => a - b));
    expect(gaps[4]!).toBeGreaterThan(gaps[0]!);
  });

  it("stops widening so a finished lesson is still reported promptly", () => {
    expect(preparationPollDelayMs(50)).toBe(6000);
  });

  it("survives a nonsensical attempt number instead of scheduling nothing", () => {
    expect(preparationPollDelayMs(-3)).toBe(1200);
    expect(preparationPollDelayMs(Number.NaN)).toBe(1200);
    expect(preparationPollDelayMs(2.7)).toBe(preparationPollDelayMs(2));
  });

  it("spends a handful of requests on a lesson-sized job instead of one per second", () => {
    // A lesson takes roughly a minute and a half to prepare; a fixed 1.2s interval would cost about 75
    // requests, each paying a full round trip for the same "still working" answer.
    const polls = preparationPollCount(95_000);

    expect(polls).toBeLessThanOrEqual(20);
    expect(polls).toBeGreaterThanOrEqual(8);
    expect(preparationPollCount(1200)).toBe(1);
  });
});
