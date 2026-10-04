import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { ApiClientError } from "../../shared/api/client";
import ChatView from "./ChatView.vue";
import ChatReasoning from "../components/ChatReasoning.vue";
import ConfirmDialog from "../../shared/components/ConfirmDialog.vue";
import type { ChatSession } from "../../shared/types/chat";
import { setLocale } from "../../shared/i18n/locale";
import chatAddIcon from "../../assets/chat/chat-add.svg";

const listChapters = vi.fn();
const streamChat = vi.fn();
const listChatSessions = vi.fn();
const getChatSession = vi.fn();
const updateChatSession = vi.fn();
const deleteChatSession = vi.fn();
const interpretAnimation = vi.fn();
const simulateAnimation = vi.fn();

let signedIn = true;

vi.mock("../runtime", () => ({
  userApi: {
    listChapters: (...args: unknown[]) => listChapters(...args),
    streamChat: (...args: unknown[]) => streamChat(...args),
    listChatSessions: (...args: unknown[]) => listChatSessions(...args),
    getChatSession: (...args: unknown[]) => getChatSession(...args),
    updateChatSession: (...args: unknown[]) => updateChatSession(...args),
    deleteChatSession: (...args: unknown[]) => deleteChatSession(...args),
    interpretAnimation: (...args: unknown[]) => interpretAnimation(...args),
    simulateAnimation: (...args: unknown[]) => simulateAnimation(...args),
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function savedSession(id: string): ChatSession {
  return { id, title: id, updatedAt: "2026-10-04T12:00:00Z", messages: [
    { id: 11, role: "user", content: `question ${id}`, sources: [], createdAt: "" },
    { id: 12, role: "assistant", content: `answer ${id}`, sources: [], createdAt: "" },
  ] };
}

async function confirmDelete(view: ReturnType<typeof mountView>, index = 0) {
  await view.findAll(".session__more")[index].trigger("click");
  await view.get(".session__delete").trigger("click");
  view.getComponent(ConfirmDialog).vm.$emit("confirm");
  await view.vm.$nextTick();
}

beforeEach(() => {
  vi.clearAllMocks();
  setLocale("zh-CN");
  signedIn = true;
  listChapters.mockResolvedValue([{ id: "ch03", title: "栈和队列" }]);
  listChatSessions.mockResolvedValue([]);
  getChatSession.mockResolvedValue({ id: "s1", chapterId: null, title: "栈", updatedAt: "", messages: [] });
  updateChatSession.mockImplementation(async (id: string, input: { title?: string; pinned?: boolean }) => ({
    id, chapterId: null, title: input.title ?? "栈", updatedAt: new Date().toISOString(), messageCount: 2, pinned: input.pinned ?? false,
  }));
  deleteChatSession.mockResolvedValue(undefined);
  streamChat.mockReset();
  interpretAnimation.mockReset();
  simulateAnimation.mockReset();
  simulateAnimation.mockResolvedValue({
    animationRecordId: "a1",
    animationData: { title: "栈的入栈出栈", steps: [{ label: "入栈", note: "压入 5" }] },
    trace: null,
  });
  document.body.innerHTML = "";
});

afterEach(() => {
  setLocale("zh-CN");
  vi.useRealTimers();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("ChatView", () => {
  describe("session navigation", () => {
    beforeEach(() => {
      listChatSessions.mockResolvedValue(["a", "b"].map((id) => ({
        id, title: id, updatedAt: savedSession(id).updatedAt, messageCount: 2,
      })));
      getChatSession.mockImplementation(async (id: string) => savedSession(id));
    });

    it("reopens a cached conversation without another detail request", async () => {
      const view = mountView();
      await flushPromises();
      for (const index of [0, 1, 0]) {
        await view.findAll(".session__open")[index].trigger("click");
        await flushPromises();
      }
      expect(getChatSession).toHaveBeenCalledTimes(2);
      expect(view.get(".message--assistant").text()).toContain("answer a");
      view.unmount();
    });

    it("refreshes an expired cache entry", async () => {
      const now = vi.spyOn(Date, "now").mockReturnValue(100_000);
      const view = mountView();
      await flushPromises();
      for (const index of [0, 1]) {
        await view.findAll(".session__open")[index].trigger("click");
        await flushPromises();
      }
      now.mockReturnValue(160_001);
      await view.findAll(".session__open")[0].trigger("click");
      await flushPromises();
      expect(getChatSession).toHaveBeenCalledTimes(3);
      view.unmount();
      now.mockRestore();
    });

    it("aborts a slow request and ignores its late response after switching", async () => {
      const slow = deferred<ChatSession>();
      getChatSession.mockImplementation((id: string) => id === "a" ? slow.promise : Promise.resolve(savedSession(id)));
      const view = mountView();
      await flushPromises();
      await view.findAll(".session__open")[0].trigger("click");
      const signal = getChatSession.mock.calls[0][1] as AbortSignal;
      expect(view.get(".thread").attributes("aria-busy")).toBe("true");
      expect(view.get("textarea").attributes()).toHaveProperty("disabled");
      await view.findAll(".session__open")[1].trigger("click");
      await flushPromises();
      expect(signal.aborted).toBe(true);
      slow.resolve(savedSession("a"));
      await flushPromises();
      expect(view.get(".message--assistant").text()).toContain("answer b");
      expect(view.get(".thread").attributes("aria-busy")).toBe("false");
      view.unmount();
    });

    it("starts a new conversation from the collapsed button during loading", async () => {
      const slow = deferred<ChatSession>();
      getChatSession.mockReturnValue(slow.promise);
      const view = mountView();
      await flushPromises();
      await view.findAll(".session__open")[0].trigger("click");
      const signal = getChatSession.mock.calls[0][1] as AbortSignal;
      await view.get(".sessions__head-actions .sidebar-icon").trigger("click");
      expect(view.get(".chat__grid").classes()).toContain("chat__grid--sidebar-collapsed");
      expect(view.get(".sidebar-collapsed-new .chat-add-icon").attributes("style")).toContain(chatAddIcon);
      await view.get(".sidebar-collapsed-new").trigger("click");
      slow.resolve(savedSession("a"));
      await flushPromises();
      expect(signal.aborted).toBe(true);
      expect(view.find(".message").exists()).toBe(false);
      expect(view.get(".thread__empty").text()).toBe("你想学习什么？");
      view.unmount();
    });

    it("removes a conversation immediately while deletion is still pending", async () => {
      const deletion = deferred<void>();
      deleteChatSession.mockReturnValue(deletion.promise);
      const view = mountView();
      await flushPromises();
      await view.findAll(".session__open")[0].trigger("click");
      await flushPromises();
      await confirmDelete(view);
      expect(deleteChatSession).toHaveBeenCalledWith("a");
      expect(view.findAll(".session__open").map((item) => item.text())).toEqual(["b"]);
      expect(view.find(".message").exists()).toBe(false);
      deletion.resolve();
      await flushPromises();
      view.unmount();
    });

    it("restores the list and active messages when deletion fails", async () => {
      const deletion = deferred<void>();
      deleteChatSession.mockReturnValue(deletion.promise);
      const view = mountView();
      await flushPromises();
      await view.findAll(".session__open")[0].trigger("click");
      await flushPromises();
      await confirmDelete(view);
      deletion.reject(new Error("delete failed"));
      await flushPromises();
      expect(view.findAll(".session__open").map((item) => item.text())).toEqual(["a", "b"]);
      expect(view.get(".message--assistant").text()).toContain("answer a");
      expect(view.get(".session__open--active").text()).toBe("a");
      view.unmount();
    });

    it("reloads a conversation when deletion fails during its first load", async () => {
      const slow = deferred<ChatSession>();
      getChatSession.mockImplementationOnce(() => slow.promise);
      const deletion = deferred<void>();
      deleteChatSession.mockReturnValue(deletion.promise);
      const view = mountView();
      await flushPromises();
      await view.findAll(".session__open")[0].trigger("click");
      const signal = getChatSession.mock.calls[0][1] as AbortSignal;
      await confirmDelete(view);
      expect(signal.aborted).toBe(true);
      deletion.reject(new Error("delete failed"));
      await flushPromises();
      expect(getChatSession).toHaveBeenCalledTimes(2);
      expect(view.get(".message--assistant").text()).toContain("answer a");
      slow.resolve(savedSession("a"));
      await flushPromises();
      expect(view.findAll(".message--assistant")).toHaveLength(1);
      view.unmount();
    });

    it("keeps a newly selected conversation when an earlier deletion fails", async () => {
      const deletion = deferred<void>();
      deleteChatSession.mockReturnValue(deletion.promise);
      const view = mountView();
      await flushPromises();
      await view.findAll(".session__open")[0].trigger("click");
      await flushPromises();
      await confirmDelete(view);
      await view.get(".session__open").trigger("click");
      await flushPromises();
      deletion.reject(new Error("delete failed"));
      await flushPromises();
      expect(view.get(".message--assistant").text()).toContain("answer b");
      expect(view.get(".session__open--active").text()).toBe("b");
      expect(view.findAll(".session__open")).toHaveLength(2);
      view.unmount();
    });
  });

  describe("local demo", () => {
    beforeEach(async () => {
      await import("../chat-local-demo");
      vi.useFakeTimers();
      vi.stubEnv("DEV", true);
      vi.stubGlobal("location", { hostname: "127.0.0.1" });
    });

    it("streams retrieval, Thinking with a cursor, and Hello World with elapsed time without a model call", async () => {
      const view = mountView();
      await flushPromises();
      await ask(view, "测试");
      expect(view.getComponent(ChatReasoning).props("retrieved")).toBe(false);

      await vi.advanceTimersByTimeAsync(900);
      expect(view.getComponent(ChatReasoning).props("retrieved")).toBe(true);
      expect(view.getComponent(ChatReasoning).props("sources")).toHaveLength(2);
      await vi.advanceTimersByTimeAsync(800);
      expect(view.getComponent(ChatReasoning).props("reasoning")).toContain("已从示例知识库");
      expect(view.find(".reasoning__cursor").exists()).toBe(true);
      expect(view.find("pre code").exists()).toBe(false);

      await vi.advanceTimersByTimeAsync(15000);
      await flushPromises();
      expect(view.get("pre code.language-c").element.textContent).toBe(
        '#include <stdio.h>\n\nint main(void)\n{\n    printf("Hello, world!\\n");\n    return 0;\n}\n',
      );
      expect(view.get("pre code.language-text").text()).toBe("Hello, world!");
      expect(view.getComponent(ChatReasoning).props("working")).toBe(false);
      expect(view.getComponent(ChatReasoning).props("seconds")).toBeGreaterThan(0);
      expect(view.getComponent(ChatReasoning).props("reasoningSeconds")).toBeGreaterThan(0);
      expect(view.find(".reasoning__cursor").exists()).toBe(false);
      expect(view.find(".message__retry").exists()).toBe(true);
      expect(streamChat).not.toHaveBeenCalled();
      expect(listChatSessions).toHaveBeenCalledTimes(1);
      expect(getChatSession).not.toHaveBeenCalled();
      view.unmount();
    });

    it("stops an in-progress demo and cancels its remaining output", async () => {
      const view = mountView();
      await flushPromises();
      await ask(view, "测试");
      await vi.advanceTimersByTimeAsync(1700);
      const partial = view.getComponent(ChatReasoning).props("reasoning");
      await view.get('button[aria-label="停止"]').trigger("click");
      await flushPromises();
      await vi.advanceTimersByTimeAsync(15000);
      expect(view.getComponent(ChatReasoning).props("reasoning")).toBe(partial);
      expect(view.text()).toContain("已停止");
      expect(view.find("pre code").exists()).toBe(false);
      expect(view.getComponent(ChatReasoning).props("working")).toBe(false);
      expect(streamChat).not.toHaveBeenCalled();
      view.unmount();
    });

    it("retries the demo through the same simulated flow", async () => {
      const view = mountView();
      await flushPromises();
      await ask(view, "测试");
      await vi.advanceTimersByTimeAsync(15000);
      await view.get(".message__retry").trigger("click");
      await flushPromises();
      expect(view.find("pre code").exists()).toBe(false);
      expect(view.getComponent(ChatReasoning).props("working")).toBe(true);
      await vi.advanceTimersByTimeAsync(15000);
      expect(view.findAll(".message--user")).toHaveLength(1);
      expect(view.findAll(".message--assistant")).toHaveLength(1);
      expect(view.get("pre code.language-c").text()).toContain("int main(void)");
      expect(streamChat).not.toHaveBeenCalled();
      view.unmount();
    });

    it("excludes simulated turns from subsequent real requests and their retries", async () => {
      streamChat.mockResolvedValue({ events: stream([
        { event: "done", parsed: { answer: "栈是后进先出。", sources: [], persisted: false } },
      ])() });
      const view = mountView();
      await flushPromises();
      await ask(view, "测试");
      await vi.advanceTimersByTimeAsync(15000);
      await ask(view, "什么是栈？");
      expect(streamChat).toHaveBeenLastCalledWith(expect.objectContaining({ prompt: "什么是栈？", history: [] }), expect.any(AbortSignal));
      streamChat.mockResolvedValue({ events: stream([
        { event: "done", parsed: { answer: "栈的解释。", sources: [], persisted: false } },
      ])() });
      await view.findAll(".message__retry")[1].trigger("click");
      await flushPromises();
      expect(streamChat).toHaveBeenLastCalledWith(expect.objectContaining({ prompt: "什么是栈？", history: [] }), expect.any(AbortSignal));
      expect(view.findAll(".message--assistant")).toHaveLength(2);
      view.unmount();
    });

    it.each([
      { dev: false, hostname: "127.0.0.1" },
      { dev: true, hostname: "structify.cn" },
      { dev: true, hostname: "192.168.1.10" },
    ])("uses the real endpoint for 测试 with dev=$dev and host=$hostname", async ({ dev, hostname }) => {
      vi.stubEnv("DEV", dev);
      vi.stubGlobal("location", { hostname });
      streamChat.mockResolvedValue({ events: stream([
        { event: "done", parsed: { answer: "真实回答", sources: [], persisted: false } },
      ])() });
      const view = mountView();
      await flushPromises();
      await ask(view, "测试");
      expect(streamChat).toHaveBeenCalledWith(expect.objectContaining({ prompt: "测试" }), expect.any(AbortSignal));
      expect(view.text()).toContain("真实回答");
      expect(view.find("pre code.language-c").exists()).toBe(false);
      view.unmount();
    });

    it("opens and reopens the demo animation without requesting a model", async () => {
      const view = mountView();
      await flushPromises();
      await ask(view, "测试");
      await vi.advanceTimersByTimeAsync(15000);
      await view.get(".message__action").trigger("click");
      await flushPromises();
      expect(document.body.querySelector(".animation-dialog__title")?.textContent).toBe("Hello World 字符序列");
      (document.body.querySelector(".animation-dialog__close") as HTMLButtonElement).click();
      await flushPromises();
      expect(document.body.querySelector(".animation-dialog")).toBeNull();
      await view.get(".message__action").trigger("click");
      await flushPromises();
      expect(document.body.querySelector(".animation-dialog__title")?.textContent).toBe("Hello World 字符序列");
      expect(streamChat).not.toHaveBeenCalled();
      expect(interpretAnimation).not.toHaveBeenCalled();
      expect(simulateAnimation).not.toHaveBeenCalled();
      view.unmount();
    });
  });

  it("centers the learning prompt before a conversation starts", async () => {
    const view = mountView();
    await flushPromises();
    expect(view.get('.thread__empty').text()).toBe('你想学习什么？');
    expect(view.get('.thread').classes()).toContain('thread--empty');
    view.unmount();
  });

  it("keeps reasoning separate from the Markdown answer and retains the glass animation action", async () => {
    const reasoning = '需要先区分栈顶和栈底。';
    const answer = '```c\nint top = -1;\n```\n需要我用动画演示入栈吗？';
    streamChat.mockResolvedValue({ events: stream([
      { event: 'sources', parsed: [] },
      { event: 'reasoning', parsed: { content: reasoning } },
      { event: 'delta', parsed: { content: answer } },
      { event: 'done', parsed: { answer, reasoning, sources: [], persisted: false } },
    ])() });
    const view = mountView();
    await flushPromises();
    await ask(view, '解释入栈');
    const reply = view.get('.message--assistant');
    expect(reply.find('.reasoning').exists()).toBe(true);
    expect(reply.get('.message__body').text()).not.toContain(reasoning);
    expect(reply.get('pre code.language-c').element.textContent).toBe('int top = -1;\n');
    expect(reply.get('.message__action').text()).toBe('看动画演示');
    expect(reply.find('.message__glass-veil').exists()).toBe(true);
    view.unmount();
  });

  it("copies the user question and the original Markdown answer", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    streamChat.mockResolvedValue({ events: stream([
      { event: 'done', parsed: { answer: '**后进先出**', sources: [], persisted: false } },
    ])() });
    const view = mountView();
    await flushPromises();
    await ask(view, '什么是栈？');
    expect(view.get('.message--user .message__bubble').find('.message__copy').exists()).toBe(false);
    expect(view.get('.message--user .message__tools').element.previousElementSibling?.classList.contains('message__bubble')).toBe(true);
    await view.get('.message--user .message__copy').trigger('click');
    await view.get('.message--assistant .message__copy').trigger('click');
    await flushPromises();
    expect(writeText.mock.calls).toEqual([['什么是栈？'], ['**后进先出**']]);
    expect(view.get('.message--assistant .message__copy').attributes('aria-label')).toBe('已复制');
    view.unmount();
  });

  it("renders uploaded files above the user bubble as message cards", async () => {
    listChatSessions.mockResolvedValue([{ id: "s1", title: "代码分析", messageCount: 2 }]);
    getChatSession.mockResolvedValue({ id: "s1", messages: [
      {
        id: 1,
        role: "user",
        content: "我这个代码怎么样",
        attachments: [{ name: "HuffmanTree.c", type: "file", mimeType: "text/plain", content: "int main() {}", byteSize: 2478 }],
        createdAt: "2026-10-04T10:00:00Z",
      },
      { id: 2, role: "assistant", content: "代码整体结构清晰。", createdAt: "2026-10-04T10:01:00Z" },
    ] });

    const view = mountView();
    await flushPromises();
    await view.get('.session__open').trigger('click');
    await flushPromises();

    const user = view.get('.message--user');
    const card = user.get('.message__attachment');
    expect(card.get('.message__attachment-name').text()).toBe("HuffmanTree.c");
    expect(card.get('.message__attachment-meta').text()).toBe("C 2.42KB");
    expect(card.element.parentElement?.nextElementSibling?.classList.contains("message__content")).toBe(true);
    expect(user.get('.message__bubble').find('.message__attachments').exists()).toBe(false);
    expect(card.attributes("href")).toBeUndefined();
    view.unmount();
  });

  it("records send and reply completion times separately without replacing them during persistence", async () => {
    vi.useFakeTimers();
    const sentAt = new Date(2026, 9, 4, 8, 5);
    const repliedAt = new Date(2026, 9, 4, 8, 6);
    const storedAt = new Date(2026, 9, 4, 8, 7).toISOString();
    vi.setSystemTime(sentAt);
    let finish!: () => void;
    streamChat.mockResolvedValue({ events: (async function* () {
      yield { event: "delta", data: "", parsed: { content: "栈是后进先出。" } };
      await new Promise<void>((resolve) => { finish = resolve; });
      yield { event: "done", data: "", parsed: { answer: "栈是后进先出。", sessionId: "s1", sources: [], persisted: true } };
    })() });
    getChatSession.mockResolvedValue({ id: "s1", messages: [
      { id: 1, role: "user", content: "什么是栈？", sources: [], createdAt: storedAt },
      { id: 2, role: "assistant", content: "栈是后进先出。", sources: [], createdAt: storedAt },
    ] });
    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");
    expect(view.get('.message--user time').attributes('datetime')).toBe(sentAt.toISOString());
    expect(view.find('.message--assistant time').exists()).toBe(false);

    vi.setSystemTime(repliedAt);
    finish();
    await flushPromises();
    const userTime = view.get('.message--user time');
    const assistantTime = view.get('.message--assistant time');
    expect(userTime.text()).toBe("2026年10月4日 08:05");
    expect(userTime.attributes('datetime')).toBe(sentAt.toISOString());
    expect(userTime.element.nextElementSibling?.classList.contains('message__copy')).toBe(true);
    expect(assistantTime.text()).toBe("2026年10月4日 08:06");
    expect(assistantTime.attributes('datetime')).toBe(repliedAt.toISOString());
    expect(assistantTime.element.previousElementSibling?.classList.contains('message__copy')).toBe(true);
    view.unmount();
  });

  it("uses stored history times and reformats them when the language changes", async () => {
    const sentAt = new Date(2026, 9, 4, 0, 5).toISOString();
    const repliedAt = new Date(2026, 9, 4, 0, 6).toISOString();
    listChatSessions.mockResolvedValue([{ id: "s1", title: "旧问题", messageCount: 2 }]);
    getChatSession.mockResolvedValue({ id: "s1", messages: [
      { id: 1, role: "user", content: "什么是栈？", sources: [], createdAt: sentAt },
      { id: 2, role: "assistant", content: "栈是后进先出。", sources: [], createdAt: repliedAt },
    ] });
    const view = mountView();
    await flushPromises();
    await view.get('.session__open').trigger('click');
    await flushPromises();
    expect(view.get('.message--user time').text()).toBe("2026年10月4日 00:05");
    expect(view.get('.message--assistant time').text()).toBe("2026年10月4日 00:06");
    setLocale("en-US");
    await flushPromises();
    expect(view.get('.message--user time').text()).toBe("Oct 4, 2026, 00:05");
    expect(view.get('.message--assistant time').text()).toBe("Oct 4, 2026, 00:06");
    expect(view.get('.message--user time').attributes('datetime')).toBe(sentAt);
    view.unmount();
  });

  it("does not invent timestamps for history without a valid creation time", async () => {
    listChatSessions.mockResolvedValue([{ id: "s1", title: "旧问题", messageCount: 2 }]);
    getChatSession.mockResolvedValue({ id: "s1", messages: [
      { id: 1, role: "user", content: "什么是栈？", sources: [], createdAt: "" },
      { id: 2, role: "assistant", content: "栈是后进先出。", sources: [], createdAt: "invalid" },
    ] });
    const view = mountView();
    await flushPromises();
    await view.get('.session__open').trigger('click');
    await flushPromises();
    expect(view.find('.message__timestamp').exists()).toBe(false);
    expect(view.findAll('.message__copy')).toHaveLength(2);
    view.unmount();
  });

  it("retries a saved turn with its original settings and removes subsequent messages only on success", async () => {
    const uploads = [{ type: 'file', name: 'stack.c', mimeType: 'text/plain', content: 'int top;' }];
    listChatSessions.mockResolvedValue([{ id: 's1', title: '旧问题', messageCount: 4 }]);
    getChatSession.mockResolvedValue({ id: 's1', messages: [
      { id: 11, role: 'user', content: '原问题', attachments: uploads, chapterId: 'ch03', thinkingEnabled: true, reasoningEffort: 'max' },
      { id: 12, role: 'assistant', content: '原回答' },
      { id: 13, role: 'user', content: '后续问题' },
      { id: 14, role: 'assistant', content: '后续回答' },
    ] });
    streamChat.mockResolvedValue({ events: stream([
      { event: 'done', parsed: { answer: '新回答', sessionId: 's1', sources: [], persisted: true } },
    ])() });
    const view = mountView();
    await flushPromises();
    await view.get('.session__open').trigger('click');
    await flushPromises();
    await view.findAll('.message__retry')[0].trigger('click');
    await flushPromises();
    expect(streamChat).toHaveBeenCalledWith(expect.objectContaining({
      prompt: '原问题', chapterId: 'ch03', attachments: uploads, thinkingEnabled: true,
      reasoningEffort: 'max', sessionId: 's1', retryMessageId: 11, history: [],
    }), expect.any(AbortSignal));
    expect(view.text()).toContain('新回答');
    expect(view.text()).not.toContain('原回答');
    expect(view.text()).not.toContain('后续问题');
    view.unmount();
  });

  it("restores the original answer and later turns when retry fails", async () => {
    listChatSessions.mockResolvedValue([{ id: 's1', title: '旧问题', messageCount: 4 }]);
    getChatSession.mockResolvedValue({ id: 's1', messages: [
      { id: 11, role: 'user', content: '原问题' }, { id: 12, role: 'assistant', content: '原回答' },
      { id: 13, role: 'user', content: '后续问题' }, { id: 14, role: 'assistant', content: '后续回答' },
    ] });
    streamChat.mockResolvedValue({ events: stream([
      { event: 'error', parsed: { code: 'CHAT_RETRY_CONFLICT', message: '' } },
    ])() });
    const view = mountView();
    await flushPromises();
    await view.get('.session__open').trigger('click');
    await flushPromises();
    await view.findAll('.message__retry')[0].trigger('click');
    await flushPromises();
    expect(view.text()).toContain('原回答');
    expect(view.text()).toContain('后续回答');
    expect(document.body.textContent).toContain('此会话已在别处更新');
    view.unmount();
  });

  it("loads the chapter scope and the saved conversations on open", async () => {
    const view = mountView();
    await flushPromises();

    expect(listChapters).toHaveBeenCalled();
    expect(listChatSessions).toHaveBeenCalled();
    expect(view.text()).toContain("课程问答");
    view.unmount();
  });

  it("assembles a streamed answer and keeps retrieved source details collapsed", async () => {
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
    expect(view.get('.message--assistant .message__body').text()).not.toContain("栈的定义");
    expect(view.get('.reasoning__sources-channel').classes()).not.toContain('reasoning__channel--open');
    expect(view.get('.reasoning__sources-channel').element.hasAttribute('inert')).toBe(true);
    expect(view.text()).not.toContain("第 41 页");
    view.unmount();
  });

  /**
   * The whole point of the offer: "好的" has to produce the animation, not another paragraph and not a
   * refusal dialog.
   */
  it("turns a yes into the animation the answer offered instead of asking the model again", async () => {
    const answer = "栈是后进先出的线性表。需要我用动画演示入栈出栈的过程吗？";
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "done", parsed: { answer, sessionId: "s1", sources: [], persisted: true } }])(),
    }));
    interpretAnimation.mockResolvedValue({ structure: "stack", operation: "push" });

    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");
    await ask(view, "好的");
    await flushPromises();

    expect(streamChat).toHaveBeenCalledTimes(1);
    // The demo is requested in the offer's own words, not by the bare question: a concept comparison
    // ("栈和队列有什么区别？") rightly refuses a frame demo, but the offer names a concrete process.
    // No chapter is chosen here, so none is sent: the request has to stay as wide as the learner left it.
    expect(interpretAnimation).toHaveBeenCalledWith(expect.objectContaining({
      prompt: expect.stringContaining("动画演示入栈出栈"),
      chapterId: undefined,
      reply: "好的",
    }));
    expect(view.text()).toContain("入栈");
    view.unmount();
  });

  /**
   * Agreement is a question of meaning, not of spelling. "okok" burned a learner once when the yes
   * words were matched whole; "包的" and "o而k之" would burn the next one. None of these are compared
   * against anything on this side - they go to the server as the learner wrote them.
   */
  it.each(["okok", "包的", "o而k之", "整一个", "why not", "👍", "好嘞好嘞"])(
    "sends the learner's own words (%s) to be read instead of matching a list here",
    async (reply) => {
      const answer = "单链表查找要从头结点挨个往后找。要不要我帮你生成一个单链表查找过程的交互式动画演示？";
      streamChat.mockImplementation(async () => ({
        kind: "sse",
        stream: null,
        events: stream([{ event: "done", parsed: { answer, sessionId: "s1", sources: [], persisted: true } }])(),
      }));
      interpretAnimation.mockResolvedValue({ structure: "singly-linked-list", operation: "search" });

      const view = mountView();
      await flushPromises();
      await ask(view, "单链表查找我不会");
      await ask(view, reply);
      await flushPromises();

      expect(streamChat).toHaveBeenCalledTimes(1);
      expect(interpretAnimation).toHaveBeenCalledWith(expect.objectContaining({ reply }));
      // No fresh question, no failure dialog - the offer simply becomes the demo.
      expect(document.body.textContent).not.toContain("操作失败");
      view.unmount();
    },
  );

  /**
   * The other half of reading the reply: "那队列呢？" is a new question wearing the same short shape,
   * and the server saying declined has to answer it rather than fail.
   */
  it("answers the reply as a question when the server reads no agreement in it", async () => {
    const answer = "栈是后进先出的线性表。需要我用动画演示入栈出栈的过程吗？";
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "done", parsed: { answer, sessionId: "s1", sources: [], persisted: true } }])(),
    }));
    interpretAnimation.mockRejectedValue(new ApiClientError({
      status: 400, code: "ANIMATION_DECLINED", message: "学生在问队列，不是答应看演示", requestId: "r1", details: [],
    }));

    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");
    await ask(view, "那队列呢");
    await flushPromises();

    expect(interpretAnimation).toHaveBeenCalledWith(expect.objectContaining({ reply: "那队列呢" }));
    expect(streamChat).toHaveBeenCalledTimes(2);
    expect(document.body.textContent).not.toContain("操作失败");
    expect(view.text()).toContain("那队列呢");
    view.unmount();
  });

  it("builds the animation when the learner presses the button under an answer", async () => {
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "done", parsed: { answer: "栈是后进先出。", sessionId: "s1", sources: [], persisted: true } }])(),
    }));
    interpretAnimation.mockResolvedValue({ structure: "stack", operation: "push" });

    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");

    const button = view.findAll("button").find((item) => item.text() === "看动画演示");
    expect(button).toBeTruthy();
    await button!.trigger("click");
    await flushPromises();

    expect(interpretAnimation).toHaveBeenCalled();
    // The demo is a dialog over the conversation (it teleports to the body), not a block inside the reply.
    expect(document.body.textContent).toContain("入栈");
    view.unmount();
  });

  /**
   * A demo that already exists is shown again, not rebuilt: pressing the button twice used to spend a
   * second interpreter call, which could even answer differently the second time and contradict the demo
   * already on screen.
   */
  it("reopens a demo it already has instead of asking the server for it again", async () => {
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "done", parsed: { answer: "栈是后进先出。", sessionId: "s1", sources: [], persisted: true } }])(),
    }));
    interpretAnimation.mockResolvedValue({ structure: "stack", operation: "push" });

    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");

    const button = view.findAll("button").find((item) => item.text() === "看动画演示");
    await button!.trigger("click");
    await flushPromises();
    expect(interpretAnimation).toHaveBeenCalledTimes(1);

    // Close it, then ask for it again from the answer's own button.
    const close = document.body.querySelector<HTMLButtonElement>('.animation-dialog__close');
    expect(close?.getAttribute('aria-label')).toBe('关闭');
    expect(close).toBeTruthy();
    close!.click();
    await flushPromises();
    expect(document.body.textContent).not.toContain("入栈");

    await button!.trigger("click");
    await flushPromises();
    expect(interpretAnimation).toHaveBeenCalledTimes(1);
    expect(document.body.textContent).toContain("入栈");
    view.unmount();
  });

  it("answers an engine refusal in plain language instead of echoing the error code", async () => {
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "done", parsed: { answer: "栈是后进先出。", sessionId: "s1", sources: [], persisted: true } }])(),
    }));
    interpretAnimation.mockRejectedValue(new Error("未匹配到可视化能力 ANIMATION_NOT_SUPPORTED"));

    const view = mountView();
    await flushPromises();
    await ask(view, "什么是栈？");
    const button = view.findAll("button").find((item) => item.text() === "看动画演示");
    await button!.trigger("click");
    await flushPromises();

    // NoticeDialog teleports to the body, so the dialog is read from there rather than from the view.
    expect(document.body.textContent).toContain("这个主题还没有动画演示");
    expect(document.body.textContent).not.toContain("ANIMATION_NOT_SUPPORTED");
    view.unmount();
  });

  /**
   * The server's own reasons name capabilities and operations - "linked_list 仅支持 append/delete/find/insert",
   * "tree 仅支持 highlight/traverse/visit" - and a learner reading those sees a broken product rather than a
   * missing feature. A refusal is one short line, whatever the engine wrote behind it.
   */
  it("never shows the engine's own explanation of what it cannot do", async () => {
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "done", parsed: { answer: "逆置要改每个结点的 next。", sessionId: "s1", sources: [], persisted: true } }])(),
    }));
    interpretAnimation.mockRejectedValue(new Error(
      "当前内建演示的 linked_list 仅支持 append/delete/find/insert，未实现单链表逆置（reverse）操作，无法生成该动画请求。",
    ));

    const view = mountView();
    await flushPromises();
    await ask(view, "单链表逆置怎么讲？");
    const button = view.findAll("button").find((item) => item.text() === "看动画演示");
    await button!.trigger("click");
    await flushPromises();

    const dialog = document.body.textContent ?? "";
    expect(dialog).toContain("这个主题还没有动画演示");
    for (const leak of ["append", "delete", "find", "insert", "reverse", "linked_list", "未实现"]) {
      expect(dialog).not.toContain(leak);
    }
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

  /**
   * A stalled upstream used to close the connection with neither done nor error, and the page kept
   * the searching note up until the learner gave up. A stream that ends with nothing now says the
   * answer did not come back in time.
   */
  it("says the answer never came when the stream closes without done or error", async () => {
    streamChat.mockImplementation(async () => ({
      kind: "sse",
      stream: null,
      events: stream([{ event: "sources", parsed: [{ id: "s1", title: "线性表-单链表" }] }])(),
    }));

    const view = mountView();
    await flushPromises();
    await ask(view, "单链表我不会");
    await flushPromises();

    expect(document.body.textContent).toContain("等了一会儿没回应");
    expect(view.text()).not.toContain("正在查教材");
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

    await view.findAll("button").find((button) => button.attributes("aria-label") === "更多操作")?.trigger("click");
    await view.findAll("button").find((button) => button.attributes("aria-label") === "删除")?.trigger("click");
    await flushPromises();
    expect(document.body.textContent).toContain("删除这段对话");
    view.unmount();
  });
});
