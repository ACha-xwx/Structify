import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import LoadingState from "./LoadingState.vue";
import MorphingSquareLoader from "./MorphingSquareLoader.vue";

describe("MorphingSquareLoader", () => {
  it("默认提供无障碍 loading 状态和 40px 形状", () => {
    const wrapper = mount(MorphingSquareLoader);
    const status = wrapper.get("[role=status]");

    expect(status.attributes("aria-live")).toBe("polite");
    expect(status.attributes("aria-label")).toBe("正在加载");
    expect(status.attributes("data-message-placement")).toBe("bottom");
    expect(wrapper.find(".morphing-square-loader__message").exists()).toBe(false);
    expect(wrapper.get(".morphing-square-loader__shape").attributes("aria-hidden")).toBe("true");
  });

  it.each(["top", "bottom", "left", "right"] as const)("支持 %s 文案位置", (messagePlacement) => {
    const wrapper = mount(MorphingSquareLoader, {
      props: { message: "正在同步数据", messagePlacement },
    });

    expect(wrapper.get("[role=status]").attributes("data-message-placement")).toBe(messagePlacement);
    expect(wrapper.get(".morphing-square-loader__message").text()).toBe("正在同步数据");
    expect(wrapper.get("[role=status]").attributes("aria-label")).toBe("正在同步数据");
  });

  it("供 LoadingState 复用时不改变原有 label、role 与 aria-live 行为", () => {
    const wrapper = mount(LoadingState, { props: { label: "正在恢复会话" } });
    const status = wrapper.get("[role=status]");

    expect(status.attributes("aria-live")).toBe("polite");
    expect(status.text()).toContain("正在恢复会话");
    expect(wrapper.get(".morphing-square-loader").attributes("aria-hidden")).toBe("true");
  });
});
