import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setLocale } from "../../shared/i18n/locale";

const { authMock, mockPresentationApi } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: string } } },
  mockPresentationApi: { getPlan: vi.fn() },
}));

vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));
vi.mock("../runtime", () => ({ presentationApi: mockPresentationApi }));

import PresentationView from "./PresentationView.vue";

const presentationResponse = {
  ok: true as const,
  ready: true,
  source: "local-preview" as const,
  lessonId: "01-01A",
  plan: {
    lessonId: "01-01A",
    title: "顺序表",
    scenes: {
      intro: { slides: ["slide-1"], primarySlideId: "slide-1", coverage: "导入", score: 1 },
    },
    slideOrder: ["slide-1"],
  },
  slides: {
    "slide-1": {
      id: "slide-1",
      deckId: "deck-1",
      deckTitle: "顺序表课件",
      slideNumber: 1,
      chapter: "01",
      title: "顺序表的插入",
      rawText: "",
      speakerNotes: "",
      semanticSummary: "顺序表插入",
      teachingRole: "concept",
      teachingFocus: "观察元素移动",
      concepts: ["顺序表"],
      visualAnchors: [],
      animationCapabilities: [],
      imageUrl: "/presentation/rendered/deck/001.png",
    },
  },
  meta: { ready: true, builtAt: "2026-08-19T00:00:00Z", slideCount: 1, lessonCount: 1 },
};

function installMatchMedia() {
  vi.stubGlobal("matchMedia", vi.fn(() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })));
}

async function mountView(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/user/home", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
      { path: "/user/coach", component: { template: "<div />" } },
      { path: "/user/classroom", component: { template: "<div />" } },
      { path: "/user/presentation", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(PresentationView, {
    global: {
      plugins: [router],
      stubs: { UserFrame: { template: "<div><slot /><aside><slot name=\"rail\" /></aside></div>" } },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("PresentationView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setLocale("zh-CN");
    installMatchMedia();
    authMock.state.user = null;
    mockPresentationApi.getPlan.mockResolvedValue(presentationResponse);
  });

  afterEach(() => { setLocale("zh-CN"); vi.unstubAllGlobals(); });

  it.each([
    ["home", "/user/presentation?lessonId=01-01A&from=home", "/", "返回产品首页"],
    ["workbench", "/user/presentation?lessonId=01-01A&from=workbench", "/user/home", "返回学习台"],
    ["chapter", "/user/presentation?lessonId=01-01A&chapterId=stack&from=chapter", "/user/chapters/stack", "返回本章"],
    ["coach", "/user/presentation?lessonId=01-01A&chapterId=stack&from=coach", "/user/coach?chapterId=stack", "返回问答陪练"],
    ["classroom", "/user/presentation?lessonId=01-01A&chapterId=stack&sessionId=classroom-1&from=classroom", "/user/classroom?chapterId=stack&sessionId=classroom-1", "返回课堂"],
  ])("从 %s 打开课件时返回原学习上下文", async (_source, path, expectedHref, expectedLabel) => {
    const wrapper = await mountView(path);

    expect(wrapper.get('[data-testid="presentation-return"]').attributes("href")).toBe(expectedHref);
    expect(wrapper.get('[data-testid="presentation-return"]').text()).toBe(expectedLabel);
    expect(mockPresentationApi.getPlan).toHaveBeenCalledWith("01-01A", { preferLocalPreview: true });
  });

  it("以 section 呈现课件舞台，避免嵌套 main landmark", async () => {
    const wrapper = await mountView("/user/presentation?lessonId=01-01A&from=workbench");

    expect(wrapper.get("section.presentation-stage").attributes("aria-label")).toBe("课件播放舞台");
    expect(wrapper.find("main.presentation-stage").exists()).toBe(false);
  });

  it("游客本地预览明确标识来源，而不伪装成已连接的服务端课件", async () => {
    const wrapper = await mountView("/user/presentation?lessonId=01-01A&from=workbench");

    expect(wrapper.text()).toContain("本地课件预览");
    expect(wrapper.text()).toContain("本地已核验课件预览");
    expect(wrapper.text()).not.toContain("已连接课件服务");
  });

  it("英文 locale 会将课件服务故障映射为可恢复状态", async () => {
    setLocale("en-US");
    mockPresentationApi.getPlan.mockRejectedValueOnce({ status: 503, message: "service unavailable" });
    const wrapper = await mountView("/user/presentation?lessonId=01-01A&from=workbench");

    expect(wrapper.text()).toContain("Learning services are temporarily unavailable");
    expect(wrapper.text()).toContain("Reconnect");
  });

  it("英文 locale 会翻译课件控制、状态、披露区和无障碍标签", async () => {
    setLocale("en-US");
    const wrapper = await mountView("/user/presentation?lessonId=01-01A&from=workbench");

    expect(wrapper.get('[data-testid="presentation-return"]').text()).toBe("Back to workbench");
    expect(wrapper.text()).toContain("Courseware preview");
    expect(wrapper.get("section.presentation-stage").attributes("aria-label")).toBe("Courseware player");
    expect(wrapper.get("details.presentation-scenes").attributes("aria-label")).toBe("Courseware scenes");
    expect(wrapper.get(".presentation-stage__topline").text()).toContain("Slide 1 of 1");
    expect(wrapper.get(".presentation-controls__play").attributes("aria-label")).toBe("Play courseware");
    expect(wrapper.text()).toContain("Verified local courseware preview");
  });
});
