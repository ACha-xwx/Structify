import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import LiquidMetalButton from "./LiquidMetalButton.vue";

describe("LiquidMetalButton", () => {
  it("透传原生按钮属性，支持文本和图标 slot，并转发点击", async () => {
    const wrapper = mount(LiquidMetalButton, {
      props: { type: "submit" },
      attrs: { "aria-label": "保存当前设置", name: "settings-action", value: "save" },
      slots: {
        default: "保存设置",
        icon: "<svg data-test-icon=\"true\" viewBox=\"0 0 1 1\" />",
      },
    });

    const button = wrapper.get("button");
    expect(button.attributes("type")).toBe("submit");
    expect(button.attributes("name")).toBe("settings-action");
    expect(button.attributes("value")).toBe("save");
    expect(button.attributes("aria-label")).toBe("保存当前设置");
    expect(wrapper.find("[data-test-icon=true]").exists()).toBe(true);
    expect(wrapper.text()).toContain("保存设置");
    expect(button.text()).toContain("保存设置");
    expect(button.get(".liquid-metal-button__native-label").attributes("aria-hidden")).toBe("true");
    expect(wrapper.find(".liquid-metal-button__content-layer").exists()).toBe(true);
    expect(wrapper.find(".liquid-metal-button__surface-layer").exists()).toBe(true);
    expect(wrapper.find(".liquid-metal-button__shader-layer").exists()).toBe(true);
    expect(button.find(".liquid-metal-button__scene").exists()).toBe(false);
    expect(wrapper.get(".liquid-metal-button").element.tagName).toBe("DIV");

    await button.trigger("click");
    expect(wrapper.emitted("click")).toHaveLength(1);
  });

  it("仅有装饰性箭头时采用 46px 图标基准", () => {
    const wrapper = mount(LiquidMetalButton, {
      attrs: { "aria-label": "继续" },
      slots: { default: "<span aria-hidden=\"true\">&rarr;</span>" },
    });

    expect(wrapper.get(".liquid-metal-button").classes()).toContain("liquid-metal-button--icon-only");
  });

  it("将文本 slot 作为独立原生点击层的无障碍名称", () => {
    const wrapper = mount(LiquidMetalButton, { slots: { default: "保存设置" } });

    const button = wrapper.get("button");
    expect(button.attributes("aria-label")).toBe("保存设置");
    expect(button.find(".liquid-metal-button__scene").exists()).toBe(false);
    expect(wrapper.find(".liquid-metal-button__scene").attributes("aria-hidden")).toBe("true");
  });

  it("正常态提供独立的色散折射边缘层，加载态不渲染彩色层", () => {
    const normal = mount(LiquidMetalButton, { slots: { default: "保存设置" } });
    const rim = normal.find(".liquid-metal-button__refractive-rim");

    expect(rim.exists()).toBe(true);
    expect(rim.attributes("aria-hidden")).toBe("true");
    expect(rim.element.parentElement?.classList.contains("liquid-metal-button__shader-layer")).toBe(true);

    const loading = mount(LiquidMetalButton, { props: { loading: true }, slots: { default: "保存设置" } });
    expect(loading.find(".liquid-metal-button__refractive-rim").exists()).toBe(false);
  });

  it("click 生成短促涟漪并在结束后移除", async () => {
    vi.useFakeTimers();
    const wrapper = mount(LiquidMetalButton, { slots: { default: "保存设置" } });

    await wrapper.get("button").trigger("click", { clientX: 8, clientY: 8 });
    expect(wrapper.find(".liquid-metal-button__ripple").exists()).toBe(true);

    vi.advanceTimersByTime(420);
    await wrapper.vm.$nextTick();
    expect(wrapper.find(".liquid-metal-button__ripple").exists()).toBe(false);
    wrapper.unmount();
  });

  it("loading 时仅显示灰阶 spinner，且禁用提交并声明忙碌状态", async () => {
    const wrapper = mount(LiquidMetalButton, {
      props: { loading: true },
      slots: {
        default: "保存设置",
        icon: "<svg data-test-icon=\"true\" viewBox=\"0 0 1 1\" />",
      },
    });

    const button = wrapper.get("button");
    expect(button.attributes("disabled")).toBeDefined();
    expect(button.attributes("aria-busy")).toBe("true");
    expect(wrapper.get(".liquid-metal-button").classes()).toContain("is-disabled");
    expect(wrapper.find(".liquid-metal-button__spinner").exists()).toBe(true);
    expect(wrapper.find(".liquid-metal-button__spinner").isVisible()).toBe(true);
    expect(wrapper.find(".liquid-metal-button__scene").exists()).toBe(false);
    expect(wrapper.find(".liquid-metal-button__shader").exists()).toBe(false);
    expect(wrapper.find(".liquid-metal-button__content-layer").exists()).toBe(false);
    expect(wrapper.find(".liquid-metal-button__ripple").exists()).toBe(false);
    expect(button.text()).toBe("");

    await button.trigger("click");
    expect(wrapper.emitted("click")).toBeUndefined();
  });

  afterEach(() => {
    vi.useRealTimers();
  });
});
