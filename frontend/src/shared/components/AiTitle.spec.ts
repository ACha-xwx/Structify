import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import AiTitle from "./AiTitle.vue";
import LandingView from "../views/LandingView.vue";

describe("title mascot grouping", () => {
  it("keeps the heading and tracking mascot as siblings in one layout group", () => {
    const view = mount(AiTitle, { slots: { default: '<h1 id="title">课堂学习</h1>' } });
    expect(view.get("h1").element.parentElement).toBe(view.element);
    expect(view.get(".ai-ball").element.parentElement).toBe(view.element);
    expect(view.get(".ai-ball").attributes("data-follow-pointer")).toBe("true");
    view.unmount();
  });

  it("passes expression playback to a title mascot without changing pointer tracking", () => {
    const view = mount(AiTitle, { props: { expressive: true }, slots: { default: "<h1>首页</h1>" } });
    expect(view.get(".ai-ball").attributes("data-expressive")).toBe("true");
    expect(view.get(".ai-ball").attributes("data-follow-pointer")).toBe("true");
    view.unmount();
  });

  it("groups the root landing brand with the mascot without adding the mascot to its heading text", () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: "/", component: LandingView }] });
    const view = mount(LandingView, { global: { plugins: [router] } });
    const group = view.get(".landing > .ai-title");
    expect(group.get("#landing-title").text()).toBe("数筑 · Structify");
    expect(group.findAll(".ai-ball")).toHaveLength(1);
    expect(group.get(".ai-ball").attributes("data-expressive")).toBe("true");
    expect(group.get(".ai-ball").attributes("data-follow-pointer")).toBe("true");
    expect(view.get(".landing__start").text()).toContain("开始");
    view.unmount();
  });
});
