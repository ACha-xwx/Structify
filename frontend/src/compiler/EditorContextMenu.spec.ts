import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { enableAutoUnmount, mount } from "@vue/test-utils";
import EditorContextMenu from "./EditorContextMenu.vue";
import { setLocale } from "../shared/i18n/locale";

enableAutoUnmount(afterEach);
beforeEach(() => setLocale("zh-CN"));

function menu(overrides: Partial<InstanceType<typeof EditorContextMenu>["$props"]> = {}) {
  return mount(EditorContextMenu, {
    attachTo: document.body,
    props: { x: 100, y: 100, canUndo: false, canRedo: false, canPaste: true, formatting: false, ...overrides },
  });
}

describe("editor context menu", () => {
  it("disables unavailable actions and skips them during keyboard navigation", async () => {
    const wrapper = menu({ canPaste: false });
    const items = wrapper.findAll('[role="menuitem"]');
    await items[2]!.trigger("click");
    expect(wrapper.emitted("select")).toBeUndefined();
    await wrapper.trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(items[0]!.element);
    await wrapper.trigger("keydown", { key: "ArrowDown" });
    await wrapper.trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(items[5]!.element);
    await wrapper.trigger("keydown", { key: "Enter" });
    expect(wrapper.emitted("select")).toEqual([["format"]]);
  });

  it("starts at the last enabled action when ArrowUp is pressed", async () => {
    const wrapper = menu({ formatting: true, canRedo: true });
    await wrapper.trigger("keydown", { key: "ArrowUp" });
    expect(document.activeElement).toBe(wrapper.findAll('[role="menuitem"]')[8]!.element);
  });

  it("exposes run code as the last action with an F6 shortcut", async () => {
    const wrapper = menu();
    const items = wrapper.findAll('[role="menuitem"]');
    expect(items).toHaveLength(9);
    expect(items[8]!.text()).toContain("运行代码");
    expect(items[8]!.text()).toContain("F6");
    await items[8]!.trigger("click");
    expect(wrapper.emitted("select")).toEqual([["run"]]);
  });

  it("keeps the enlarged menu fully visible without an inner scrollbar", () => {
    const wrapper = menu();
    expect((wrapper.element as HTMLElement).style.width).toBe("246px");
    expect((wrapper.element as HTMLElement).style.maxHeight).toBe("369px");
    expect(getComputedStyle(wrapper.element).overflow).toBe("hidden");
  });

  it("closes with focus restoration on Escape or Tab", async () => {
    const wrapper = menu();
    await wrapper.trigger("keydown", { key: "Escape" });
    await wrapper.trigger("keydown", { key: "Tab" });
    expect(wrapper.emitted("close")).toEqual([[true], [true]]);
  });

  it("dismisses on outside interaction but leaves internal clicks to the action", async () => {
    const wrapper = menu();
    await wrapper.findAll('[role="menuitem"]')[1]!.trigger("pointerdown");
    expect(wrapper.emitted("close")).toBeUndefined();
    document.body.dispatchEvent(new Event("pointerdown", { bubbles: true }));
    expect(wrapper.emitted("close")).toEqual([[false]]);
  });

  it("flips within the viewport when opened at the lower right edge", () => {
    const wrapper = menu({ x: window.innerWidth - 2, y: window.innerHeight - 2 });
    const style = (wrapper.element as HTMLElement).style;
    expect(Number.parseFloat(style.left)).toBeGreaterThanOrEqual(8);
    expect(Number.parseFloat(style.left) + Number.parseFloat(style.width)).toBeLessThanOrEqual(window.innerWidth - 8);
    expect(Number.parseFloat(style.top) + Number.parseFloat(style.maxHeight)).toBeLessThanOrEqual(window.innerHeight - 8);
  });

  it("removes outside listeners on unmount", () => {
    const wrapper = menu();
    wrapper.unmount();
    document.body.dispatchEvent(new Event("pointerdown", { bubbles: true }));
    window.dispatchEvent(new Event("resize"));
    expect(wrapper.emitted("close")).toBeUndefined();
  });
});
