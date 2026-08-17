import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import Textarea from "./Textarea.vue";

describe("Textarea", () => {
  it("forwards textarea attributes and emits its model value", async () => {
    const wrapper = mount(Textarea, {
      props: { modelValue: "初始内容" },
      attrs: { "aria-label": "消息", rows: 6, placeholder: "请输入" },
    });
    const textarea = wrapper.get("textarea");

    expect(textarea.attributes("aria-label")).toBe("消息");
    expect(textarea.attributes("rows")).toBe("6");
    expect((textarea.element as HTMLTextAreaElement).value).toBe("初始内容");

    await textarea.setValue("更新内容");

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual(["更新内容"]);
    wrapper.unmount();
  });
});
