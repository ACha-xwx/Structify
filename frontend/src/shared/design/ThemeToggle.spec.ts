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
});
