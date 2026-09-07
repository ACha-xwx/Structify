import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import { nextTick, ref } from "vue";
import RuntimeSelect from "./RuntimeSelect.vue";

const options = [
  { value: "c", label: "C" },
  { value: "python", label: "Python" },
  { value: "java", label: "Java", disabled: true },
];

function mountSelect(value = "c") {
  const selected = ref(value);
  const wrapper = mount({
    components: { RuntimeSelect },
    setup() { return { selected, options }; },
    template: '<RuntimeSelect v-model="selected" ariaLabel="语言" :options="options" test-id="language-select" />',
  }, { attachTo: document.body });
  return { wrapper, selected };
}

describe("RuntimeSelect", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("opens the reference-style list and selects an option", async () => {
    const { wrapper, selected } = mountSelect();
    const trigger = wrapper.get('.runtime-select__trigger');

    expect(trigger.attributes("aria-expanded")).toBe("false");
    await trigger.trigger("click");
    await nextTick();

    expect(trigger.attributes("aria-expanded")).toBe("true");
    const optionsInBody = Array.from(document.querySelectorAll<HTMLButtonElement>(".runtime-select__option"));
    expect(optionsInBody).toHaveLength(3);
    await optionsInBody[1].click();
    await nextTick();

    expect(selected.value).toBe("python");
    expect(trigger.text()).toContain("Python");
    expect(trigger.attributes("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(trigger.element);
  });

  it("supports arrow navigation and restores focus on Escape", async () => {
    const { wrapper, selected } = mountSelect();
    const trigger = wrapper.get('.runtime-select__trigger');

    await trigger.trigger("keydown", { key: "ArrowDown" });
    await nextTick();
    const selectedOption = document.querySelector<HTMLButtonElement>('[data-option-index="0"]');
    expect(document.activeElement).toBe(selectedOption);

    await selectedOption?.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
    await nextTick();
    const nextOption = document.querySelector<HTMLButtonElement>('[data-option-index="1"]');
    expect(document.activeElement).toBe(nextOption);

    await nextOption?.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await nextTick();
    expect(selected.value).toBe("python");
    expect(document.activeElement).toBe(trigger.element);

    await trigger.trigger("click");
    await nextTick();
    const activeOption = document.querySelector<HTMLButtonElement>('[data-option-index="1"]');
    await activeOption?.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    await nextTick();
    expect(trigger.attributes("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(trigger.element);
  });

  it("keeps a later selected label inside the trigger viewport", async () => {
    const { wrapper } = mountSelect("python");
    await nextTick();

    const viewport = wrapper.get(".runtime-select__value-viewport").element;
    const selected = wrapper.findAll(".runtime-select__value-option")[1].element;
    const viewportRect = viewport.getBoundingClientRect();
    const selectedRect = selected.getBoundingClientRect();

    expect(selectedRect.top).toBeGreaterThanOrEqual(viewportRect.top - 1);
    expect(selectedRect.bottom).toBeLessThanOrEqual(viewportRect.bottom + 1);
    expect(wrapper.get(".runtime-select__trigger").attributes("aria-label")).toContain("Python");
  });
});
