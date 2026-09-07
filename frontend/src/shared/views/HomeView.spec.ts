import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import HomeView from "./HomeView.vue";
import { setLocale } from "../i18n/locale";

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/user/home", component: { template: "<div />" } },
      { path: "/user/animation", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/user/knowledge", component: { template: "<div />" } },
      { path: "/user/presentation", component: { template: "<div />" } },
      { path: "/user/code", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/");
  await router.isReady();
  return mount(HomeView, { global: { plugins: [router] } });
}

afterEach(() => {
  document.body.replaceChildren();
  document.body.style.overflow = "";
  document.documentElement.style.overflow = "";
});

beforeEach(() => setLocale("zh-CN"));

describe("AI Runtime product home contract", () => {
  it("keeps the exact AI Runtime metric rhythm while disclosing preview-only values", async () => {
    const wrapper = await mountHome();

    expect(wrapper.findAll(".ai-runtime-frame__nav > a").map((link) => link.text())).toEqual(["首页", "产品", "案例", "联系"]);
    expect(wrapper.get(".runtime-landing__trust-copy").text()).toBe("面向数据结构的智能学习");
    expect(wrapper.find(".runtime-landing__ring--one .runtime-landing__brand-icon--microsoft use").attributes("href")).toContain("#brand-microsoft");
    expect(wrapper.find(".runtime-landing__ring--two .runtime-landing__brand-icon--amazon use").attributes("href")).toContain("#brand-amazon");
    expect(wrapper.find(".runtime-landing__ring--three .runtime-landing__brand-icon--google use").attributes("href")).toContain("#brand-google");
    expect(wrapper.get(".runtime-landing__summary").text()).toContain("同一张学习工作台");
    expect(wrapper.findAll(".runtime-capability").map((metric) => metric.get("strong").text())).toEqual([
      "120ms",
      "99.99%",
      "24/7",
      "2.4M",
    ]);
    expect(wrapper.findAll(".runtime-capability").map((metric) => metric.get("small").text())).toEqual([
      "推理耗时",
      "平台可用性",
      "自主运行",
      "上下文窗口",
    ]);
    expect(wrapper.find(".runtime-capability[href=\"/user/animation?chapterId=sequential-list&from=home\"]").exists()).toBe(true);
    expect(wrapper.get("#runtime-metrics-note").text()).toContain("视觉合同中的预览值");

    wrapper.unmount();
  });

  it("shows final metric values immediately for reduced-motion users", async () => {
    vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
      matches: query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })));

    const wrapper = await mountHome();

    expect(wrapper.findAll(".runtime-capability strong").map((metric) => metric.text())).toEqual([
      "120ms",
      "99.99%",
      "24/7",
      "2.4M",
    ]);
    wrapper.unmount();
    vi.unstubAllGlobals();
  });

  it("keeps Runtime typography at neutral tracking for crisp pixel copy", () => {
    const homeSource = Object.values(import.meta.glob("./HomeView.vue", { query: "?raw", import: "default", eager: true }))[0] as string;
    const frameSource = Object.values(import.meta.glob("../components/ai-runtime-frame.css", { query: "?raw", import: "default", eager: true }))[0] as string;

    expect(homeSource).not.toMatch(/letter-spacing:\s*-[^;]+;/);
    expect(frameSource).not.toMatch(/letter-spacing:\s*-[^;]+;/);
  });
});
