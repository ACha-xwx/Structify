import { describe, expect, it } from "vitest";
import { chatErrorKey, deltaOf, doneOf, errorOf, sourcesOf } from "./chat-stream";
import { messages, type MessageKey } from "../shared/i18n/messages";

/** Wire events carry either an already-parsed payload or the raw JSON text; both have to work. */
const parsed = (event: string, data: unknown) => ({ event, data: JSON.stringify(data), parsed: data });
const raw = (event: string, data: unknown) => ({ event, data: JSON.stringify(data) });

describe("deltaOf", () => {
  it("reads the text a delta carries", () => {
    expect(deltaOf(parsed("delta", { content: "链式" }))).toBe("链式");
    expect(deltaOf(raw("delta", { content: "存储" }))).toBe("存储");
  });

  it("accepts the older delta field so a mixed stream does not lose text", () => {
    expect(deltaOf(parsed("delta", { delta: "结构" }))).toBe("结构");
  });

  it("returns nothing rather than printing a placeholder into the answer", () => {
    expect(deltaOf(parsed("delta", {}))).toBe("");
    expect(deltaOf({ event: "delta", data: "not json" })).toBe("");
  });
});

describe("sourcesOf", () => {
  const source = { id: "1", chapterId: "ch03", title: "栈的定义", content: "…", source: "教材", pageLabel: "第 41 页", score: 0.8, evidenceHash: "abc" };

  it("reads a bare array and a wrapped one", () => {
    expect(sourcesOf(parsed("sources", [source]))).toHaveLength(1);
    expect(sourcesOf(parsed("sources", { sources: [source] }))).toHaveLength(1);
  });

  it("drops entries that are not evidence instead of rendering a blank line", () => {
    expect(sourcesOf(parsed("sources", [source, { nope: true }, null]))).toHaveLength(1);
    expect(sourcesOf({ event: "sources", data: "" })).toEqual([]);
  });
});

describe("doneOf", () => {
  it("takes the finished answer", () => {
    const done = doneOf(parsed("done", { answer: "答", sessionId: "s1", sources: [], persisted: true }));
    expect(done?.answer).toBe("答");
    expect(done?.sessionId).toBe("s1");
  });

  it("is null for a stream event that is not an answer", () => {
    expect(doneOf(parsed("delta", { content: "x" }))).toBeNull();
  });
});

describe("errorOf", () => {
  it("keeps the server code and wording", () => {
    expect(errorOf(parsed("error", { code: "CHAT_RATE_LIMITED", message: "too fast" }))).toEqual({
      code: "CHAT_RATE_LIMITED",
      message: "too fast",
    });
  });

  it("still reports an error that arrives without a code", () => {
    expect(errorOf(parsed("error", { message: "断了" }))?.code).toBe("CHAT_STREAM_FAILED");
  });

  it("is null when the event is not a failure", () => {
    expect(errorOf(parsed("error", {}))).toBeNull();
  });
});

describe("chatErrorKey", () => {
  it("writes a learner-readable line for every refusal the server is known to give", () => {
    const known: Array<[string, MessageKey]> = [
      ["CHAT_RATE_LIMITED", "chat.error.rateLimited"],
      ["CHAT_EVIDENCE_UNAVAILABLE", "chat.error.evidence"],
      ["AI_QUOTA_EXHAUSTED", "chat.error.quota"],
      ["CHAT_SESSION_NOT_FOUND", "chat.error.sessionGone"],
      ["CHAT_PROMPT_TOO_LONG", "chat.error.tooLong"],
    ];
    for (const [code, key] of known) {
      expect(chatErrorKey(code), code).toBe(key);
      expect(messages[key].zh.trim()).not.toBe("");
      expect(messages[key].en.trim()).not.toBe("");
    }
  });

  it("falls back to a generic line that admits what happened", () => {
    expect(chatErrorKey("SOMETHING_ELSE")).toBe("chat.error.failed");
  });
});
