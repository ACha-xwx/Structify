import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ThemeToggle from "./ThemeToggle.vue";
import { setTheme } from "./theme";
import { LOCALE_STORAGE_KEY, setLocale } from "../i18n/locale";

beforeEach(() => {
  setLocale("zh-CN");
  window.localStorage.removeItem(LOCALE_STORAGE_KEY);
});

describe("ThemeToggle", () => {
  it("does not introduce a form label and explicitly names its switch", () => {
    const wrapper = mount(ThemeToggle);
    const toggle = wrapper.get('[role="switch"]');
    const labelId = toggle.attributes("aria-labelledby");

    expect(wrapper.find("label").exists()).toBe(false);
    expect(labelId).toBeTruthy();
    expect(wrapper.get(".theme-toggle__sr-only").attributes("id")).toBe(labelId);
  });

  it("renders the visible material track, icon affordances, and sliding thumb", () => {
    const wrapper = mount(ThemeToggle);
    const toggle = wrapper.get("button.theme-toggle__control");

    expect(toggle.attributes("type")).toBe("button");
    expect(toggle.attributes("role")).toBe("switch");
    expect(toggle.attributes("aria-checked")).toMatch(/^(true|false)$/);
    expect(wrapper.find(".theme-toggle__groove").exists()).toBe(true);
    expect(wrapper.find(".theme-toggle__thumb").exists()).toBe(true);
    expect(wrapper.findAll(".theme-toggle__ambient-icon")).toHaveLength(2);
  });

  it("updates the switch state when activated", async () => {
    setTheme("light");
    const wrapper = mount(ThemeToggle);
    const toggle = wrapper.get("button.theme-toggle__control");
    const initialState = toggle.attributes("aria-checked");

    await toggle.trigger("click");

    expect(toggle.attributes("aria-checked")).not.toBe(initialState);
  });

  it("applies the selected theme to the document when activated", async () => {
    setTheme("light");
    const wrapper = mount(ThemeToggle);
    const toggle = wrapper.get("[data-theme-toggle]");

    await toggle.trigger("click");

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(toggle.attributes("aria-checked")).toBe("true");

    setTheme("light");
  });

  it("keeps the native control as the click boundary", async () => {
    setTheme("light");
    const parentClick = vi.fn();
    const wrapper = mount(ThemeToggle, {
      attrs: { onClick: parentClick },
    });
    const toggle = wrapper.get("[data-theme-toggle]");

    await toggle.trigger("click");

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(toggle.attributes("aria-checked")).toBe("true");
    expect(parentClick).not.toHaveBeenCalled();

    setTheme("light");
  });

  it("can render a contextual copy without registering a duplicate control", () => {
    const wrapper = mount(ThemeToggle, { props: { registerControl: false } });
    const toggle = wrapper.get("[role=\"switch\"]");

    expect(toggle.attributes("data-theme-toggle")).toBeUndefined();
  });

  it("reconciles a remounted control with the visible document theme before toggling", async () => {
    setTheme("light");
    document.documentElement.dataset.theme = "dark";
    document.documentElement.style.colorScheme = "dark";

    const wrapper = mount(ThemeToggle);
    const toggle = wrapper.get("[data-theme-toggle]");

    expect(toggle.attributes("aria-checked")).toBe("true");

    await toggle.trigger("click");

    expect(document.documentElement.dataset.theme).toBe("light");
    expect(toggle.attributes("aria-checked")).toBe("false");

    setTheme("light");
  });

  it("keeps the language switch separate from the direct theme switch", async () => {
    setTheme("light");
    const wrapper = mount(ThemeToggle);
    const themeControl = wrapper.get("button.theme-toggle__control");
    const languageControl = wrapper.get("button.theme-toggle__locale-toggle");

    expect(languageControl.attributes("role")).toBe("switch");
    expect(languageControl.attributes("aria-checked")).toBe("false");
    expect(languageControl.attributes("aria-label")).toContain("中文");
    await languageControl.trigger("click");

    expect(document.documentElement.lang).toBe("en-US");
    expect(document.documentElement.dataset.locale).toBe("en-US");
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en-US");
    expect(themeControl.attributes("aria-checked")).toBe("false");
    expect(languageControl.attributes("aria-checked")).toBe("true");
    expect(languageControl.text()).toBe("EN");

    setLocale("zh-CN");
    setTheme("light");
  });

  it("preserves the selected language when remounted", () => {
    setLocale("en-US");
    const wrapper = mount(ThemeToggle);

    const languageControl = wrapper.get(".theme-toggle__locale-toggle");
    expect(languageControl.text()).toBe("EN");
    expect(languageControl.attributes("role")).toBe("switch");
    expect(languageControl.attributes("aria-checked")).toBe("true");
    expect(languageControl.attributes("aria-label")).toContain("English");

    setLocale("zh-CN");
  });

  it("localizes the theme action label and title with the selected language", async () => {
    setTheme("light");
    setLocale("en-US");
    const wrapper = mount(ThemeToggle);
    const themeControl = wrapper.get("button.theme-toggle__control");

    expect(wrapper.attributes("title")).toBe("Switch to dark theme");
    expect(wrapper.get(".theme-toggle__sr-only").text()).toBe("Switch to dark theme");

    await themeControl.trigger("click");

    expect(wrapper.attributes("title")).toBe("Switch to light theme");
    expect(wrapper.get(".theme-toggle__sr-only").text()).toBe("Switch to light theme");

    setLocale("zh-CN");
    setTheme("light");
  });
});
