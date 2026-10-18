import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { enableAutoUnmount, mount } from "@vue/test-utils";
import { nextTick } from "vue";
import CodeLibraryMenu from "./CodeLibraryMenu.vue";
import { setLocale } from "../shared/i18n/locale";

enableAutoUnmount(afterEach);
beforeEach(() => setLocale("zh-CN"));

const items = [
  { id: "seqlist", label: "顺序表", children: [
    { id: "elem", label: "elem 数组与 last 下标", keywords: "顺序表：elem 数组与 last 下标" },
    { id: "append", label: "尾插时 last 怎么动" },
  ] },
  { id: "linklist", label: "单链表", children: [{ id: "next", label: "结点类型与 next 指针" }] },
];

async function flush() {
  for (let i = 0; i < 5; i++) await nextTick();
}
function createMenu() {
  return mount(CodeLibraryMenu, {
    props: { label: "线性表", items, selectedId: "elem", count: 3 },
    attachTo: document.body,
    global: { stubs: { Teleport: true } },
  });
}

describe("compiler multilevel menu", () => {
  it("enters on click, goes back, and selects only a code leaf", async () => {
    const wrapper = createMenu();
    await wrapper.get(".code-menu__trigger").trigger("click");
    await flush();
    expect(wrapper.get('[role="menu"]').text()).toContain("顺序表");
    expect(wrapper.text()).not.toContain("elem 数组与 last 下标");
    await wrapper.get(".code-menu__item").trigger("mouseenter");
    expect(wrapper.find(".code-menu__header").exists()).toBe(false);
    await wrapper.get(".code-menu__item").trigger("click");
    await flush();
    expect(wrapper.get('[role="menu"]').attributes("aria-label")).toBe("顺序表");
    expect(wrapper.get('[data-entry-id="elem"]').attributes("aria-checked")).toBe("true");
    expect(wrapper.emitted("select")).toBeUndefined();
    await wrapper.get(".code-menu__back").trigger("click");
    await flush();
    expect(wrapper.get('[role="menu"]').attributes("aria-label")).toBe("线性表");
    await wrapper.get(".code-menu__item").trigger("click");
    await flush();
    await wrapper.get('[data-entry-id="append"]').trigger("click");
    expect(wrapper.emitted("select")).toEqual([["append"]]);
    expect(wrapper.get(".code-menu__trigger").attributes("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(wrapper.get(".code-menu__trigger").element);
  });

  it("searches the current panel and clears that search when going back", async () => {
    const wrapper = createMenu();
    await wrapper.get(".code-menu__trigger").trigger("click");
    await wrapper.get(".code-menu__item").trigger("click");
    await flush();
    await wrapper.get(".code-menu__search").setValue("尾插");
    expect(wrapper.findAll(".code-menu__item")).toHaveLength(1);
    expect(wrapper.get(".code-menu__item").text()).toContain("尾插时 last 怎么动");
    await wrapper.get(".code-menu__search").setValue("不存在");
    expect(wrapper.text()).toContain("没有匹配的代码");
    await wrapper.get(".code-menu__back").trigger("click");
    await wrapper.get(".code-menu__item").trigger("click");
    await flush();
    expect((wrapper.get(".code-menu__search").element as HTMLInputElement).value).toBe("");
    expect(wrapper.findAll(".code-menu__item")).toHaveLength(2);
  });

  it("supports arrow navigation and Escape with focus restoration", async () => {
    const wrapper = createMenu();
    await wrapper.get(".code-menu__trigger").trigger("keydown", { key: "ArrowDown" });
    await flush();
    expect(document.activeElement).toBe(wrapper.get(".code-menu__item").element);
    await wrapper.get(".code-menu__item").trigger("keydown", { key: "ArrowRight" });
    await flush();
    expect(document.activeElement).toBe(wrapper.get(".code-menu__search").element);
    await wrapper.get(".code-menu__search").trigger("keydown", { key: "ArrowDown" });
    expect(document.activeElement).toBe(wrapper.get('[data-entry-id="elem"]').element);
    await wrapper.get('[data-entry-id="elem"]').trigger("keydown", { key: "ArrowLeft" });
    await flush();
    expect(wrapper.get('[role="menu"]').attributes("aria-label")).toBe("线性表");
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    await flush();
    expect(wrapper.find(".code-menu__popup").exists()).toBe(false);
    expect(document.activeElement).toBe(wrapper.get(".code-menu__trigger").element);
  });

  it("closes on an outside click and starts at the root on reopening", async () => {
    const wrapper = createMenu();
    await wrapper.get(".code-menu__trigger").trigger("click");
    await wrapper.get(".code-menu__item").trigger("click");
    document.body.dispatchEvent(new Event("pointerdown", { bubbles: true }));
    await flush();
    expect(wrapper.find(".code-menu__popup").exists()).toBe(false);
    await wrapper.get(".code-menu__trigger").trigger("click");
    await flush();
    expect(wrapper.find(".code-menu__header").exists()).toBe(false);
    expect(wrapper.get('[role="menu"]').attributes("aria-label")).toBe("线性表");
  });
});
