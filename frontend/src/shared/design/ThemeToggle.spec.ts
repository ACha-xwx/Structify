import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ThemeToggle from "./ThemeToggle.vue";

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
    const wrapper = mount(ThemeToggle);
    const toggle = wrapper.get("button.theme-toggle__control");
    const initialState = toggle.attributes("aria-checked");

    await toggle.trigger("click");

    expect(toggle.attributes("aria-checked")).not.toBe(initialState);
  });
});
