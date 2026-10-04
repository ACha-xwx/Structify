import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enableAutoUnmount, flushPromises, mount } from "@vue/test-utils";
import { activateHover, closeHoverTooltips, EditorView, hasHoverTooltips } from "@codemirror/view";
import { insertNewlineAndIndent, undo } from "@codemirror/commands";
import { getSearchQuery, SearchQuery, setSearchQuery } from "@codemirror/search";
import { setDiagnostics } from "@codemirror/lint";
import CCodeEditor from "./CCodeEditor.vue";
import leftArrow from "../assets/classroom/left-arrow.svg";
import rightArrow from "../assets/classroom/right-arrow.svg";
import { setLocale } from "../shared/i18n/locale";

const { formatCCode } = vi.hoisted(() => ({ formatCCode: vi.fn() }));
vi.mock("./format-code", () => ({ formatCCode }));
enableAutoUnmount(afterEach);

const clipboard = { readText: vi.fn(), writeText: vi.fn() };

beforeEach(() => {
  setLocale("zh-CN");
  formatCCode.mockReset();
  clipboard.readText.mockReset().mockResolvedValue("pasted");
  clipboard.writeText.mockReset().mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { configurable: true, value: clipboard });
  // jsdom has no layout engine; CodeMirror uses these DOM methods to measure text.
  Object.defineProperties(Range.prototype, {
    getClientRects: { configurable: true, value: () => [] },
    getBoundingClientRect: { configurable: true, value: () => new DOMRect() },
  });
});

function editor(code = "int main(void) { return 0; }") {
  const wrapper = mount(CCodeEditor, { attachTo: document.body, props: { modelValue: code, documentKey: 1 } });
  const view = (wrapper.vm as unknown as { getView: () => EditorView }).getView();
  return { wrapper, view };
}

function key(view: EditorView, key: string, options: KeyboardEventInit = {}) {
  view.focus();
  return view.contentDOM.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true, ...options }));
}

async function action(wrapper: ReturnType<typeof editor>["wrapper"], index: number) {
  await wrapper.get(".cm-content").trigger("keydown", { key: "F10", shiftKey: true });
  const button = document.querySelectorAll<HTMLButtonElement>('.editor-menu [role="menuitem"]')[index]!;
  button.click();
  await flushPromises();
}

describe("C code editor", () => {
  it("boxes same-name identifiers when placing the cursor and excludes comments and strings", async () => {
    const code = 'int count;\nvoid visit(void) { count++; count++; }\n// count\nchar *text = "count";';
    const { wrapper, view } = editor(code);
    view.dispatch({ selection: { anchor: code.indexOf("count++") + 2 } });
    expect(wrapper.findAll('.cm-symbol-match[data-symbol="count"]')).toHaveLength(3);
    view.dispatch({ selection: { anchor: code.indexOf("// count") + 5 } });
    expect(wrapper.findAll(".cm-symbol-match")).toHaveLength(0);
    view.dispatch({ selection: { anchor: 5 } });
    view.dispatch({ changes: { from: 0, to: code.length, insert: "int renamed;" }, selection: { anchor: 6 } });
    expect(wrapper.findAll('.cm-symbol-match[data-symbol="renamed"]')).toHaveLength(1);
    expect(wrapper.findAll('[data-symbol="count"]')).toHaveLength(0);
    await wrapper.setProps({ modelValue: "", documentKey: 2 });
    expect(wrapper.findAll(".cm-symbol-match")).toHaveLength(0);
  });

  it("shows highlighted type, variable and function definitions on hover", async () => {
    const code = "typedef struct ArcNode { int value; } ArcNode;\nArcNode *head;\nint visit(const ArcNode *node, int count) { return count; }\nint main(void) { return visit(head, 1); }";
    const { wrapper, view } = editor(code);
    for (const [marker, label, signature] of [
      ["ArcNode *head", "类型别名", "typedef struct ArcNode { int value; } ArcNode;"],
      ["head, 1", "变量", "ArcNode *head;"],
      ["visit(head", "函数", "int visit(const ArcNode *node, int count);"],
    ]) {
      activateHover(view, code.lastIndexOf(marker) + 1, 1);
      await flushPromises();
      expect(wrapper.get(".c-symbol__kind").text()).toBe(label);
      expect(wrapper.get(".c-symbol__declaration").text()).toBe(signature);
      expect(wrapper.find(".c-symbol__declaration span").exists()).toBe(true);
      view.dispatch({ effects: closeHoverTooltips });
    }
  });

  it("shows standard function parameters and clears hover on edits, language and document changes", async () => {
    const code = '#include <stdio.h>\nint main(void) { printf("hello"); }';
    const { wrapper, view } = editor(code);
    activateHover(view, code.indexOf("printf") + 1, 1);
    await flushPromises();
    expect(wrapper.get(".c-symbol__declaration").text()).toBe("int printf(const char *restrict format, ...);");
    expect(wrapper.get(".c-symbol__location").text()).toBe("<stdio.h>");
    view.dispatch({ changes: { from: code.length, insert: "\n" } });
    expect(hasHoverTooltips(view.state)).toBe(false);
    setLocale("en-US");
    await flushPromises();
    activateHover(view, code.indexOf("printf") + 1, 1);
    await flushPromises();
    expect(wrapper.get(".c-symbol__kind").text()).toBe("Function");
    await wrapper.setProps({ modelValue: "", documentKey: 2 });
    expect(hasHoverTooltips(view.state)).toBe(false);
    expect(wrapper.find(".c-symbol").exists()).toBe(false);
  });

  it("emits the complete source when edited", () => {
    const { wrapper, view } = editor("int a;");
    view.dispatch({ changes: { from: 4, to: 5, insert: "value" } });
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["int value;"]);
  });

  it("comments a line with Ctrl+/ and supports Ctrl+Z and Ctrl+Y", () => {
    const { view } = editor("int a;");
    key(view, "/", { ctrlKey: true, code: "Slash", keyCode: 191 });
    expect(view.state.doc.toString()).toBe("// int a;");
    key(view, "z", { ctrlKey: true, code: "KeyZ", keyCode: 90 });
    expect(view.state.doc.toString()).toBe("int a;");
    key(view, "y", { ctrlKey: true, code: "KeyY", keyCode: 89 });
    expect(view.state.doc.toString()).toBe("// int a;");
  });

  it("uses two-space block indentation", () => {
    const { view } = editor("int main(void) {}");
    view.dispatch({ selection: { anchor: 16 } });
    insertNewlineAndIndent(view);
    expect(view.state.doc.toString()).toBe("int main(void) {\n  \n}");
  });

  it("runs with Ctrl+Enter and opens find with Ctrl+F", async () => {
    const { wrapper, view } = editor();
    key(view, "Enter", { ctrlKey: true, code: "Enter", keyCode: 13 });
    expect(wrapper.emitted("run")).toEqual([[]]);
    key(view, "f", { ctrlKey: true, code: "KeyF", keyCode: 70 });
    expect(wrapper.find(".cm-search").exists()).toBe(true);
    const input = wrapper.get(".cm-search input");
    const event = new MouseEvent("contextmenu", { bubbles: true, cancelable: true });
    expect(input.element.dispatchEvent(event)).toBe(true);
    expect(document.querySelector(".editor-menu")).toBeNull();
    await flushPromises();
  });

  it("runs with F6", () => {
    const { wrapper, view } = editor();
    key(view, "F6", { code: "F6", keyCode: 117 });
    expect(wrapper.emitted("run")).toEqual([[]]);
  });

  it("finds both directions with icon buttons and selects all matches", async () => {
    const { wrapper, view } = editor("int item;\nitem++;\nitem++;");
    key(view, "f", { ctrlKey: true });
    await wrapper.get('input[name="search"]').setValue("item");
    expect(wrapper.get('[name="prev"] img').attributes("src")).toBe(leftArrow);
    expect(wrapper.get('[name="next"] img').attributes("src")).toBe(rightArrow);
    await wrapper.get('button[name="next"]').trigger("click");
    expect(view.state.selection.main.from).toBe(4);
    await wrapper.get('button[name="next"]').trigger("click");
    expect(view.state.selection.main.from).toBe(10);
    await wrapper.get('button[name="prev"]').trigger("click");
    expect(view.state.selection.main.from).toBe(4);
    await wrapper.get('button[name="select"]').trigger("click");
    expect(view.state.selection.ranges).toHaveLength(3);
    await wrapper.get('button[name="close"]').trigger("click");
    expect(wrapper.find(".cm-search").exists()).toBe(false);
    expect(view.hasFocus).toBe(true);
  });

  it("keeps replace operations undoable and closes the panel with Escape", async () => {
    const { wrapper, view } = editor("int item;\nitem++;\nitem++;");
    key(view, "f", { ctrlKey: true });
    await wrapper.get('input[name="search"]').setValue("item");
    await wrapper.get('input[name="replace"]').setValue("value");
    await wrapper.get('button[name="next"]').trigger("click");
    await wrapper.get('button[name="replace"]').trigger("click");
    expect(view.state.doc.toString()).toBe("int value;\nitem++;\nitem++;");
    await wrapper.get('button[name="replaceAll"]').trigger("click");
    expect(view.state.doc.toString()).toBe("int value;\nvalue++;\nvalue++;");
    undo(view);
    expect(view.state.doc.toString()).toBe("int value;\nitem++;\nitem++;");
    await wrapper.get('input[name="search"]').trigger("keydown", { key: "Escape", keyCode: 27 });
    expect(wrapper.find(".cm-search").exists()).toBe(false);
  });

  it("syncs search options, external queries and translated controls", async () => {
    const { wrapper, view } = editor("int item;");
    key(view, "f", { ctrlKey: true });
    await wrapper.get('input[name="search"]').setValue("item");
    await wrapper.get('input[name="case"]').setValue(true);
    await wrapper.get('input[name="re"]').setValue(true);
    await wrapper.get('input[name="word"]').setValue(true);
    expect(getSearchQuery(view.state)).toMatchObject({ search: "item", caseSensitive: true, regexp: true, wholeWord: true });
    view.dispatch({ effects: setSearchQuery.of(new SearchQuery({ search: "int", replace: "long" })) });
    expect((wrapper.get('input[name="search"]').element as HTMLInputElement).value).toBe("int");
    expect((wrapper.get('input[name="replace"]').element as HTMLInputElement).value).toBe("long");
    expect((wrapper.get('input[name="case"]').element as HTMLInputElement).checked).toBe(false);
    setLocale("en-US");
    await flushPromises();
    expect(wrapper.get('button[name="prev"]').attributes("aria-label")).toBe("previous");
    expect(wrapper.get('button[name="next"]').attributes("aria-label")).toBe("next");
    expect(wrapper.get('input[name="search"]').attributes("placeholder")).toBe("Find");
    expect((wrapper.get('input[name="replace"]').element as HTMLInputElement).value).toBe("long");
  });

  it("resets history on sample selection and keeps code completion undoable", async () => {
    const { wrapper, view } = editor("int a;");
    view.dispatch({ changes: { from: 0, to: 6, insert: "int b;" } });
    await wrapper.setProps({ modelValue: "int c;", documentKey: 2 });
    expect(undo(view)).toBe(false);
    await wrapper.setProps({ modelValue: "int main(void) { return 0; }" });
    expect(undo(view)).toBe(true);
    expect(view.state.doc.toString()).toBe("int c;");
  });

  it("shows all diagnostics in a collapsible list and locates the selected code", async () => {
    const { wrapper, view } = editor("int a;\nint b;");
    await wrapper.get('[aria-label="语法问题"]').trigger("click");
    await flushPromises();
    view.dispatch(setDiagnostics(view.state, [
      { from: 4, to: 5, severity: "error", message: "短消息" },
      { from: 11, to: 12, severity: "warning", message: "这是一条比其他警告更长的消息，应该完整显示在警告列表里面。" },
    ]));
    expect(wrapper.find(".cm-panel-lint").exists()).toBe(false);
    expect(wrapper.get(".c-diagnostics__trigger").text()).toBe("语法问题 (2)");
    expect(wrapper.findAll(".c-diagnostics__message").map((item) => item.text())).toEqual([
      "短消息", "这是一条比其他警告更长的消息，应该完整显示在警告列表里面。",
    ]);
    const trigger = wrapper.get(".c-diagnostics__trigger");
    await trigger.trigger("click");
    expect(trigger.attributes("aria-expanded")).toBe("false");
    expect(wrapper.get(".c-diagnostics__content").attributes("aria-hidden")).toBe("true");
    await trigger.trigger("click");
    expect(trigger.attributes("aria-expanded")).toBe("true");
    await wrapper.findAll(".c-diagnostics__entry")[1].trigger("click");
    expect(view.state.selection.main).toMatchObject({ from: 11, to: 12 });
    expect(view.hasFocus).toBe(true);
  });

  it("maps diagnostic navigation and fixes to the current document positions", async () => {
    const { wrapper, view } = editor("int a；\nint b;");
    await wrapper.get('[aria-label="语法问题"]').trigger("click");
    await flushPromises();
    view.dispatch(setDiagnostics(view.state, [{
      from: 5, to: 6, severity: "error", message: "中文分号", actions: [{
        name: ";", apply: (editor, from, to) => editor.dispatch({ changes: { from, to, insert: ";" } }),
      }],
    }]));
    view.dispatch({ changes: { from: 0, insert: "// leading\n" } });
    await wrapper.get(".c-diagnostics__entry").trigger("click");
    expect(view.state.selection.main).toMatchObject({ from: 16, to: 17 });
    expect(wrapper.get(".c-diagnostics__position").text()).toBe("行 2, 列 6");
    await wrapper.get('button[name="fix-16-0"]').trigger("click");
    expect(view.state.doc.toString()).toBe("// leading\nint a;\nint b;");
  });

  it("opens diagnostics by shortcut and keeps its glass close action separate from search", async () => {
    const { wrapper, view } = editor();
    key(view, "M", { ctrlKey: true, shiftKey: true, code: "KeyM", keyCode: 77 });
    await flushPromises();
    expect(wrapper.get(".c-diagnostics__empty").text()).toBe("没有语法错误");
    const close = wrapper.get('.c-diagnostics button[name="close"]');
    expect(close.classes()).toContain("c-search__glass--icon");
    expect(close.find(".c-search__rim").exists()).toBe(true);
    key(view, "f", { ctrlKey: true, code: "KeyF", keyCode: 70 });
    await close.trigger("click");
    expect(wrapper.find(".c-diagnostics").exists()).toBe(false);
    expect(wrapper.find(".cm-search").exists()).toBe(true);
    expect(view.hasFocus).toBe(true);
  });

  it("supports diagnostic keyboard navigation, Escape, language changes and document reset", async () => {
    const { wrapper, view } = editor("int a;\nint b;");
    await wrapper.get('[aria-label="语法问题"]').trigger("click");
    await flushPromises();
    view.dispatch(setDiagnostics(view.state, [
      { from: 4, to: 5, severity: "error", message: "第一条警告" },
      { from: 11, to: 12, severity: "warning", message: "第二条警告" },
    ]));
    await wrapper.get(".c-diagnostics__trigger").trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(wrapper.findAll(".c-diagnostics__entry")[0].element);
    await wrapper.findAll(".c-diagnostics__entry")[0].trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(wrapper.findAll(".c-diagnostics__entry")[1].element);
    await wrapper.findAll(".c-diagnostics__entry")[1].trigger("keydown", { key: "Escape" });
    expect(wrapper.find(".c-diagnostics").exists()).toBe(false);
    expect(view.hasFocus).toBe(true);
    await wrapper.get('[aria-label="语法问题"]').trigger("click");
    setLocale("en-US");
    await flushPromises();
    expect(wrapper.get(".c-diagnostics").attributes("aria-label")).toBe("Syntax problems");
    await wrapper.setProps({ modelValue: "", documentKey: 2 });
    expect(wrapper.find(".c-diagnostics").exists()).toBe(false);
  });

  it("formats with Shift+Alt+F as one undoable operation", async () => {
    const before = "int main(){return 0;}";
    const formatted = "int main() {\n  return 0;\n}\n";
    formatCCode.mockResolvedValue(formatted);
    const { view } = editor(before);
    key(view, "F", { shiftKey: true, altKey: true, code: "KeyF", keyCode: 70 });
    await flushPromises();
    expect(formatCCode).toHaveBeenCalledWith(before);
    expect(view.state.doc.toString()).toBe(formatted);
    undo(view);
    expect(view.state.doc.toString()).toBe(before);
  });

  it("discards a formatting result if the user edited while it loaded", async () => {
    let finish!: (value: string) => void;
    formatCCode.mockImplementation(() => new Promise<string>((resolve) => { finish = resolve; }));
    const { wrapper, view } = editor("int a;");
    await wrapper.get('[aria-label="格式化文档"]').trigger("click");
    view.dispatch({ changes: { from: 0, to: 6, insert: "int fresh;" } });
    finish("int stale;\n");
    await flushPromises();
    expect(view.state.doc.toString()).toBe("int fresh;");
    expect(wrapper.get('[role="alert"]').text()).toContain("代码已发生变化");
  });

  it("cuts, undoes, copies and pastes through the context menu", async () => {
    const { wrapper, view } = editor("int a;\nint b;");
    view.dispatch({ selection: { anchor: 0, head: 6 } });
    await action(wrapper, 0);
    expect(clipboard.writeText).toHaveBeenLastCalledWith("int a;");
    expect(view.state.doc.toString()).toBe("\nint b;");
    await action(wrapper, 3);
    expect(view.state.doc.toString()).toBe("int a;\nint b;");
    view.dispatch({ selection: { anchor: 0, head: 6 } });
    await action(wrapper, 1);
    expect(clipboard.writeText).toHaveBeenLastCalledWith("int a;");
    await action(wrapper, 2);
    expect(view.state.doc.toString()).toBe("pasted\nint b;");
  });

  it("runs from the editor context menu", async () => {
    const { wrapper } = editor();
    await action(wrapper, 8);
    expect(wrapper.emitted("run")).toEqual([[]]);
  });

  it("does not insert delayed clipboard contents into a newly selected sample", async () => {
    let finish!: (value: string) => void;
    clipboard.readText.mockImplementation(() => new Promise<string>((resolve) => { finish = resolve; }));
    const { wrapper, view } = editor("int a;");
    await action(wrapper, 2);
    await wrapper.setProps({ modelValue: "int newSample;", documentKey: 2 });
    finish("stale clipboard");
    await flushPromises();
    expect(view.state.doc.toString()).toBe("int newSample;");
  });

  it("reports clipboard denial without removing code", async () => {
    clipboard.writeText.mockRejectedValue(new Error("Permission denied"));
    const { wrapper, view } = editor("int a;");
    await action(wrapper, 0);
    expect(view.state.doc.toString()).toBe("int a;");
    expect(wrapper.get('[role="alert"]').text()).toContain("无法访问剪贴板");
  });
});
