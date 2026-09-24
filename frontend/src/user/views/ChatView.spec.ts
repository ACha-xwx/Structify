import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import ChatView from "./ChatView.vue";

const listChapters = vi.fn();
const streamChat = vi.fn();
const listChatSessions = vi.fn();
const getChatSession = vi.fn();
const deleteChatSession = vi.fn();

let signedIn = true;

vi.mock("../runtime", () => ({
  userApi: {
    listChapters: (...args: unknown[]) => listChapters(...args),
    streamChat: (...args: unknown[]) => streamChat(...args),
    listChatSessions: (...args: unknown[]) => listChatSessions(...args),
    getChatSession: (...args: unknown[]) => getChatSession(...args),
    deleteChatSession: (...args: unknown[]) => deleteChatSession(...args),
  },
}));

vi.mock("../../app/providers/runtime", () => ({
  auth: {
    state: { get user() { return signedIn ? { id: 7 } : null; } },
    logout: vi.fn(),
  },
}));

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return { ...actual, useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) };
});

/**
 * A stream that yields its events, and stops the moment the caller aborts - as the real reader does.
 * With `hold` the answer then stays open, which is the state a learner actually presses "stop" in: an
 * answer still being written, not one that already finished.
 */
function stream(events: Array<{ event: string; parsed?: unknown }>, hold = false) {
  return async function* (signal?: AbortSignal) {
    for (const event of events) {
      if (signal?.aborted) return;
      yield { data: "", ...event };
    }
    if (!hold) return;
    await new Promise<void>((resolve) => {
      if (signal?.aborted) return resolve();
      signal?.addEventListener("abort", () => resolve(), { once: true });
    });
  };
}

/** BrandStage renders the brand lockup as a router link; the router itself is not the subject here. */
function mountView() {
  return mount(ChatView, { attachTo: document.body, global: { stubs: { RouterLink: true } } });
}

async function ask(view: ReturnType<typeof mountView>, question: string) {
  const box = view.get("textarea");
  await box.setValue(question);
  await view.get("textarea").trigger("keydown", { key: "Enter" });
  await flushPromises();
}

beforeEach(() => {
  vi.clearAllMocks();
  signedIn = true;
  listChapters.mockResolvedValue([{ id: "ch03", title: "栈和队列" }]);
  listChatSessions.mockResolvedValue([]);
  getChatSession.mockResolvedValue({ id: "s1", chapterId: null, title: "栈", updatedAt: "", messages: [] });
  deleteChatSession.mockResolvedValue(undefined);
  streamChat.mockReset();
  document.body.innerHTML = "";
});

describe("ChatView", () => {
  it("loads the chapter scope and the saved conversations on open", async () => {
    const view = mountView();
    await flushPromises();

    expect(listChapters).toHaveBeenCalled();
    expect(listChatSessions).toHaveBeenCalled();
    expect(view.text()).toContain("课程问答");
    view.unmount();
  });

  it("assembles a streamed answer from the deltas and keeps the evidence", async () => {
    const source = { id: "1", chapterId: "ch03", title: "栈的定义", content: "…", source: "教材", pageLabel: "第 41 页", score: 0.9, evidenceHash: "h1" };
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([
        { event: "sources", parsed: [source] },
        { event: "delta", parsed: { content: "栈是" } },
        { event: "delta", parsed: { content: "受限的线性表。" } },
        { event: "done", parsed: { answer: "栈是受限的线性表。", sessionId: "s1", sources: [source], persisted: true } },
      ])(),
    }));

    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");

    expect(view.text()).toContain("什么是栈？");
    expect(view.text()).toContain("栈是受限的线性表。");
    expect(view.text()).toContain("栈的定义");
    view.unmount();
  });

  it("says what went wrong when the server refuses, in the learner's own language", async () => {
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "error", parsed: { code: "CHAT_EVIDENCE_UNAVAILABLE", message: "no evidence" } }])(),
    }));

    const view = mountView();
    await flushPromises();
    await ask(view, "量子纠缠和栈有关系吗");

    expect(document.body.textContent).toContain("教材里没有这个问题的依据");
    // The question stays; the empty reply it produced does not.
    expect(view.text()).toContain("量子纠缠和栈有关系吗");
    expect(view.text()).not.toContain("正在查教材");
    view.unmount();
  });

  it("marks the answer stopped rather than leaving it half-written as if finished", async () => {
    streamChat.mockImplementation(async (_input: unknown, signal?: AbortSignal) => ({
      kind: "sse",
      stream: null,
      events: stream([
        { event: "delta", parsed: { content: "先说结论" } },
        { event: "delta", parsed: { content: "还有很多" } },
      ], true)(signal),
    }));

    const view = mountView();
    await flushPromises();
    await ask(view, "讲讲树");

    await view.findAll("button").find((button) => button.text() === "停止")?.trigger("click");
    await flushPromises();

    const text = view.text();
    expect(text).toContain("先说结论");
    expect(text).toContain("已停止");
    view.unmount();
  });

  it("refuses to send a question longer than the server accepts", async () => {
    const view = mountView();
    await flushPromises();
    await ask(view, "栈".repeat(4001));

    expect(streamChat).not.toHaveBeenCalled();
    expect(view.text()).toContain("问题太长了");
    view.unmount();
  });

  it("tells a guest that conversations are only kept once signed in", async () => {
    signedIn = false;
    const view = mountView();
    await flushPromises();

    expect(view.text()).toContain("登录后才能保存和回看对话");
    expect(listChatSessions).not.toHaveBeenCalled();
    view.unmount();
  });

  it("reopens a saved conversation and can delete it", async () => {
    listChatSessions.mockResolvedValue([{ id: "s1", chapterId: null, title: "栈的疑问", updatedAt: "", messageCount: 2 }]);
    getChatSession.mockResolvedValue({
      id: "s1",
      chapterId: null,
      title: "栈的疑问",
      updatedAt: "",
      messages: [
        { id: 1, role: "user", content: "什么是栈？", sources: [], createdAt: "" },
        { id: 2, role: "assistant", content: "栈是受限的线性表。", sources: [], createdAt: "" },
      ],
    });

    const view = mountView();
    await flushPromises();
    await view.findAll("button").find((button) => button.text() === "栈的疑问")?.trigger("click");
    await flushPromises();

    expect(view.text()).toContain("什么是栈？");
    expect(view.text()).toContain("栈是受限的线性表。");

    await view.findAll("button").find((button) => button.attributes("aria-label") === "删除")?.trigger("click");
    await flushPromises();
    expect(document.body.textContent).toContain("删除这段对话");
    view.unmount();
  });
});
