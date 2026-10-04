import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RouterLinkStub, enableAutoUnmount, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, nextTick } from "vue";
import CodeEditorView from "./CodeEditorView.vue";
import CCodeEditor from "./CCodeEditor.vue";
import { setLocale } from "../shared/i18n/locale";

enableAutoUnmount(afterEach);

const runCode = vi.fn();
const listCodeSamples = vi.fn();
const getCodeLibrary = vi.fn();
const startCodeSession = vi.fn();
const streamCodeSession = vi.fn();
const typeInCodeSession = vi.fn();
const stopCodeSession = vi.fn();

vi.mock("../user/runtime", () => ({
  userApi: {
    runCode: (...args: unknown[]) => runCode(...args),
    listCodeSamples: (...args: unknown[]) => listCodeSamples(...args),
    getCodeLibrary: (...args: unknown[]) => getCodeLibrary(...args),
    startCodeSession: (...args: unknown[]) => startCodeSession(...args),
    streamCodeSession: (...args: unknown[]) => streamCodeSession(...args),
    typeInCodeSession: (...args: unknown[]) => typeInCodeSession(...args),
    stopCodeSession: (...args: unknown[]) => stopCodeSession(...args),
  },
}));

/** The one-shot runner is the fallback: without a live sandbox, these tests exercise it. */
function sandboxUnavailable() {
  startCodeSession.mockRejectedValue(Object.assign(new Error("交互式运行环境未启用"), { code: "SANDBOX_DISABLED" }));
}

/**
 * A stream that stays open until the test says otherwise - a live program keeps the connection
 * while it waits to be typed at.
 */
function openStream() {
  const pending: Array<{ event: string; parsed?: unknown; data?: string }> = [];
  let wake: ((event: { event: string; parsed?: unknown; data?: string }) => void) | null = null;
  const events = (async function* () {
    for (;;) {
      const next = pending.shift();
      if (next) yield next;
      else yield await new Promise<{ event: string; parsed?: unknown; data?: string }>((resolve) => { wake = resolve; });
    }
  })();
  return {
    events,
    push(event: { event: string; parsed?: unknown; data?: string }) {
      if (wake) {
        const resolve = wake;
        wake = null;
        resolve(event);
      } else {
        pending.push(event);
      }
    },
  };
}

beforeEach(() => {
  setLocale("zh-CN");
  runCode.mockReset();
  listCodeSamples.mockReset();
  getCodeLibrary.mockReset();
  startCodeSession.mockReset();
  streamCodeSession.mockReset();
  typeInCodeSession.mockReset();
  stopCodeSession.mockReset();
  sandboxUnavailable();
});

function library() {
  return {
    fragmentCount: 3,
    chapters: [
      {
        chapter: "ch03",
        title: "第 3 章 限定性线性表",
        fragments: [
          {
            id: "ch03-3.1",
            file: "3.1.c",
            title: "3.1 · 构造一个空栈S",
            kind: "algorithm" as const,
            startWith: "main",
            code: "void InitStack(SeqStack *S)\n{\n\tS->top = -1;\n}\n",
            example: {
              code: "#include <stdio.h>\ntypedef char StackElementType;\nint main(void)\n{\n  printf(\"ok\\n\");\n  return 0;\n}\n",
              stdin: "",
              expectedStdout: "ok\n",
              note: "示例补上了顺序栈的类型定义和一个 main。",
            },
            blocked: "",
          },
          {
            id: "ch03-seqstack",
            file: "seqstack.h",
            title: "seqstack · 顺序栈",
            kind: "type" as const,
            startWith: "types",
            code: "#define Stack_Size 50\n",
            example: null,
            blocked: "",
          },
          {
            id: "ch03-3.15",
            file: "3.15.c",
            title: "3.15 · hanoi(3,A,B,C)",
            kind: "type" as const,
            startWith: "types",
            code: "hanoi(3,A,B,C)\n",
            example: null,
            blocked: "这一段是汉诺塔的执行过程记录，不是可编译的代码。",
          },
        ],
      },
    ],
  };
}

function samples() {
  return {
    sampleCount: 1,
    lessons: [
      {
        coursewareKey: "03-01",
        lessonTitle: "栈与队列-栈的定义和表示实现",
        chapterId: "03-stack-queue",
        samples: [
          {
            id: "03-01-s1",
            title: "顺序栈的四个基本运算",
            sourceFile: "ch03/code/seqstack.h",
            sections: ["3.1.2"],
            targets: ["3.1"],
            summary: "",
            stdin: "abcde\n",
            expectedStdout: "",
            code: "#include <stdio.h>\nint main(void) { return 0; }\n",
          },
        ],
      },
    ],
  };
}

/** Let the console's own async loops run: a run is several awaits deep. */
async function flush() {
  for (let round = 0; round < 6; round++) {
    await Promise.resolve();
    await nextTick();
  }
}

async function mountView() {
  listCodeSamples.mockResolvedValue(samples());
  getCodeLibrary.mockResolvedValue(library());
  const wrapper = mount(CodeEditorView, { global: { stubs: {
    RouterLink: RouterLinkStub,
    Teleport: true,
    CCodeEditor: defineComponent({
      name: "CCodeEditor",
      props: ["modelValue", "documentKey"],
      emits: ["update:modelValue", "run"],
      template: '<div></div>',
    }),
  } } });
  for (let round = 0; round < 4; round++) {
    await flush();
  }
  return wrapper;
}

function editorCode(wrapper: VueWrapper): string {
  return wrapper.getComponent(CCodeEditor).props("modelValue");
}

async function openStructure(wrapper: VueWrapper, group: string, chapter: string, structure: string) {
  const region = wrapper.get(`[data-library-group="${group}"]`);
  await region.get(`[data-chapter="${chapter}"]`).trigger("click");
  await flush();
  await region.findAll(".code-menu__item").find((item) => item.get(".code-menu__label").text() === structure)!.trigger("click");
  await flush();
  return region;
}

async function selectSample(wrapper: VueWrapper) {
  const region = await openStructure(wrapper, "samples", "03", "顺序栈");
  await region.get('[data-entry-id="03-01-s1"]').trigger("click");
}

describe("code library page", () => {
  it("offers three independent regions and a separate menu for each chapter", async () => {
    const wrapper = await mountView();

    expect(wrapper.text()).toContain("可运行样本");
    expect(wrapper.text()).toContain("示例");
    expect(wrapper.text()).toContain("入门样例");
    expect(wrapper.get('[data-library-group="samples"]').findAll(".code-menu__trigger")).toHaveLength(9);
    expect(wrapper.get('[data-library-group="examples"]').findAll(".code-menu__trigger")).toHaveLength(9);
    expect(wrapper.text()).not.toContain("3.1 · 构造一个空栈S");
    await wrapper.get('[data-library-group="templates"] .code-menu__trigger').trigger("click");
    await flush();
    expect(wrapper.findAll('[data-library-group="templates"] .code-menu__item')).toHaveLength(5);
    expect(wrapper.text()).toContain("二叉树遍历");
  });

  it("opens a blank main.c and does not run an empty document", async () => {
    const wrapper = await mountView();

    expect(editorCode(wrapper)).toBe("");
    expect(wrapper.get(".library__document-title").text()).toBe("main.c");
    expect(wrapper.find(".library__origin").exists()).toBe(false);
    expect((wrapper.get(".console__input").element as HTMLTextAreaElement).value).toBe("");
    await wrapper.get(".library__run button").trigger("click");
    await flush();
    expect(startCodeSession).not.toHaveBeenCalled();
    expect(runCode).not.toHaveBeenCalled();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
  });

  it("loads a manually selected runnable sample and runs it", async () => {
    runCode.mockResolvedValue({ language: "c", status: "success", stdout: "ok\n", stderr: "", durationMs: 5, runId: null });
    const wrapper = await mountView();
    await selectSample(wrapper);

    expect(editorCode(wrapper)).toContain("int main(void)");
    await wrapper.get(".library__run button").trigger("click");
    await flush();

    expect(runCode).toHaveBeenCalledWith(expect.objectContaining({ language: "c", stdin: "abcde\n" }));
    expect(wrapper.text()).toContain("运行完成");
  });

  it("starts a new blank document without selecting a library entry", async () => {
    const wrapper = await mountView();
    await selectSample(wrapper);
    expect(editorCode(wrapper)).toContain("int main(void)");

    await wrapper.get('[aria-label="新建空白文件"]').trigger("click");
    expect(editorCode(wrapper)).toBe("");
    expect(wrapper.get(".library__document-title").text()).toBe("main.c");
    expect(wrapper.find(".code-menu__item--selected").exists()).toBe(false);
  });

  it("imports a C source into a blank document and preserves its filename", async () => {
    const wrapper = await mountView();
    const source = "#include <stdio.h>\n// 导入的中文注释\nint main(void) { return 0; }\n";
    const file = { name: "lesson.c", arrayBuffer: async () => new TextEncoder().encode(source).buffer };
    const input = wrapper.get('input[type="file"]');
    Object.defineProperty(input.element, "files", { configurable: true, value: [file] });
    await input.trigger("change");
    await flush();

    expect(editorCode(wrapper)).toBe(source);
    expect(wrapper.get(".library__document-title").text()).toBe("lesson.c");
    expect(wrapper.find(".code-menu__item--selected").exists()).toBe(false);
  });

  it("exports the current source with its document filename", async () => {
    const wrapper = await mountView();
    const source = "int main(void) { return 7; }\n";
    const editor = wrapper.getComponent(CCodeEditor);
    editor.vm.$emit("update:modelValue", source);
    await nextTick();
    const createObjectURL = vi.fn(() => "blob:structify-test");
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, "createObjectURL", { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revokeObjectURL });
    let downloadName = "";
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
      downloadName = this.download;
    });

    await wrapper.get('[aria-label="导出代码"]').trigger("click");
    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(downloadName).toBe("main.c");
    expect(click).toHaveBeenCalledOnce();
    click.mockRestore();
  });

  it("exports a complete catalog program with a C source extension", async () => {
    const wrapper = await mountView();
    await selectSample(wrapper);
    const createObjectURL = vi.fn(() => "blob:structify-test");
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, "createObjectURL", { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revokeObjectURL });
    let downloadName = "";
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
      downloadName = this.download;
    });

    await wrapper.get('[aria-label="导出代码"]').trigger("click");
    expect(downloadName).toBe("seqstack.c");
    click.mockRestore();
  });

  /** The console holds both halves: the program's output and the input typed at its prompt. */
  it("keeps the program's output and its input in one console", async () => {
    runCode.mockResolvedValue({ language: "c", status: "success", stdout: "ok\n", stderr: "", durationMs: 5, runId: null });
    const wrapper = await mountView();
    await selectSample(wrapper);

    expect(wrapper.get(".console__screen").text()).toContain("输出会出现在这里");
    expect(wrapper.find(".library__stdin").exists()).toBe(false);

    await wrapper.get(".console__input").setValue("42\n");
    await wrapper.get(".library__run button").trigger("click");
    await flush();

    expect(runCode).toHaveBeenCalledWith(expect.objectContaining({ stdin: "42\n" }));
    expect(wrapper.get(".console__screen").text()).toContain("ok");
  });

  it("loads the verified example around a listing, input included", async () => {
    runCode.mockResolvedValue({ language: "c", status: "success", stdout: "ok\n", stderr: "", durationMs: 5, runId: null });
    const wrapper = await mountView();

    const region = await openStructure(wrapper, "examples", "03", "顺序栈");
    await region.get('[data-entry-id="ch03-3.1"]').trigger("click");
    expect(wrapper.text()).toContain("缺类型、缺函数、也缺 main");

    expect(wrapper.get(".library__chip").text()).toBe("补齐代码");
    await wrapper.get(".library__chip").trigger("click");
    expect(editorCode(wrapper)).toContain("printf(\"ok\\n\")");
    expect(wrapper.text()).toContain("示例补上了顺序栈的类型定义和一个 main");

    await wrapper.get(".library__run button").trigger("click");
    await flush();
    expect(wrapper.text()).toContain("输出和示例的预期一致");
  });

  it("says why a listing has no example instead of offering a button that cannot work", async () => {
    const wrapper = await mountView();

    const region = await openStructure(wrapper, "examples", "03", "汉诺塔");
    await region.get('[data-entry-id="ch03-3.15"]').trigger("click");

    expect(wrapper.text()).toContain("不是可编译的代码");
    expect(wrapper.find(".library__chip").exists()).toBe(false);
  });

  it("filters the whole library from one search box", async () => {
    const wrapper = await mountView();

    await wrapper.get(".library__search").setValue("seqstack");
    const region = await openStructure(wrapper, "examples", "03", "顺序栈");
    expect(wrapper.text()).toContain("seqstack · 顺序栈");
    expect(wrapper.text()).not.toContain("入门样例");
    await region.get('[data-entry-id="ch03-seqstack"]').trigger("click");
    expect(editorCode(wrapper)).toBe("#define Stack_Size 50\n");

    await wrapper.get(".library__search").setValue("不存在的代码");
    expect(wrapper.text()).toContain("没有匹配的代码");
  });

  it("shows an empty introduction without substituting another chapter's code", async () => {
    const wrapper = await mountView();
    const before = editorCode(wrapper);
    await wrapper.get('[data-library-group="samples"] [data-chapter="01"]').trigger("click");
    await flush();
    expect(wrapper.text()).toContain("本章节暂无代码");
    expect(wrapper.findAll(".code-menu__item")).toHaveLength(0);
    expect(editorCode(wrapper)).toBe(before);
  });

  it("keeps the selected program when changing the navigation language", async () => {
    const wrapper = await mountView();
    await selectSample(wrapper);
    const before = editorCode(wrapper);
    setLocale("en-US");
    await flush();
    expect(wrapper.text()).toContain("Stacks and queues");
    expect(wrapper.text()).toContain("Internal and external sorting");
    await wrapper.get('[data-library-group="templates"] .code-menu__trigger').trigger("click");
    await flush();
    expect(wrapper.text()).toContain("Binary tree traversal");
    expect(editorCode(wrapper)).toBe(before);
  });

  it("runs edits emitted by the editor through the existing backend path", async () => {
    runCode.mockResolvedValue({ language: "c", status: "success", stdout: "", stderr: "", durationMs: 5, runId: null });
    const wrapper = await mountView();
    const editor = wrapper.getComponent(CCodeEditor);
    const edited = "int main(void) { return 42; }";
    editor.vm.$emit("update:modelValue", edited);
    await nextTick();
    editor.vm.$emit("run");
    await flush();
    expect(startCodeSession).toHaveBeenCalledWith({ language: "c", code: edited });
    expect(runCode).toHaveBeenCalledWith(expect.objectContaining({ code: edited, stdin: "" }));
    // A new blank document has no library entry to restore.
    expect(wrapper.find(".library__restore").exists()).toBe(false);
  });
});

describe("console that runs the program live", () => {
  async function mountLive() {
    const stream = openStream();
    startCodeSession.mockResolvedValue({ sessionId: "s1", status: "running", output: "" });
    streamCodeSession.mockResolvedValue(stream);
    typeInCodeSession.mockResolvedValue(undefined);
    stopCodeSession.mockResolvedValue(undefined);
    const wrapper = await mountView();
    await selectSample(wrapper);
    await wrapper.get(".library__run button").trigger("click");
    await flush();
    return { wrapper, stream };
  }

  it("runs the program live and answers it line by line", async () => {
    const { wrapper, stream } = await mountLive();

    expect(wrapper.text()).toContain("运行中，等你输入");
    stream.push({ event: "chunk", parsed: { stream: "stdout", text: "请输入第一个数：" } });
    await flush();
    expect(wrapper.get(".console__screen").text()).toBe("请输入第一个数：");

    // The prompt is a terminal: what you type is echoed, and handed to the program.
    await wrapper.get(".console__input").setValue("5");
    await wrapper.get(".console__input").trigger("keydown", { key: "Enter" });
    await flush();
    expect(typeInCodeSession).toHaveBeenCalledWith("s1", "5\n");
    // A terminal echoes what was typed, newline included - text() trims, so read the node itself.
    expect((wrapper.get(".console__screen").element as HTMLElement).textContent).toContain("5\n");

    stream.push({ event: "chunk", parsed: { stream: "stdout", text: "两数之和是 12\n" } });
    stream.push({ event: "exit", parsed: 0 });
    await flush();
    expect(wrapper.text()).toContain("运行完成");
    expect(wrapper.get(".console__screen").text()).toContain("两数之和是 12");
  });

  it("shows the compiler's complaint before anything runs", async () => {
    startCodeSession.mockResolvedValue({ sessionId: null, status: "compile_error", output: "error: expected ';'\n" });
    const wrapper = await mountView();

    await selectSample(wrapper);
    await wrapper.get(".library__run button").trigger("click");
    await flush();

    expect(wrapper.text()).toContain("编译错误");
    expect(wrapper.get(".console__screen").text()).toContain("expected ';'");
    expect(runCode).not.toHaveBeenCalled();
  });

  it("keeps the one-shot run when there is no live runner to talk to", async () => {
    runCode.mockResolvedValue({ language: "c", status: "success", stdout: "ok\n", stderr: "", durationMs: 5, runId: null });
    const wrapper = await mountView();

    await selectSample(wrapper);
    await wrapper.get(".library__run button").trigger("click");
    await flush();

    expect(startCodeSession).toHaveBeenCalled();
    expect(runCode).toHaveBeenCalledWith(expect.objectContaining({ language: "c", stdin: "abcde\n" }));
    expect(wrapper.text()).toContain("运行完成");
    expect(wrapper.find(".console__send").exists()).toBe(false);
  });

  it("ends the program when the run button is pressed again", async () => {
    const { wrapper } = await mountLive();

    expect(wrapper.get(".library__run button").attributes("aria-label")).toBe("结束运行");
    await wrapper.get(".library__run button").trigger("click");
    await flush();

    expect(stopCodeSession).toHaveBeenCalledWith("s1");
    expect(wrapper.get(".library__run button").attributes("aria-label")).toBe("运行代码");
  });
});
