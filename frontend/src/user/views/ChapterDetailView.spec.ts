import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, userApiMock } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: number; email: string; roles: string[] } } },
  userApiMock: { listChapters: vi.fn(), listResources: vi.fn() },
}));

vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));
vi.mock("../runtime", () => ({ userApi: userApiMock }));

import ChapterDetailView from "./ChapterDetailView.vue";
import { setLocale } from "../../shared/i18n/locale";

async function mountView(path = "/user/chapters/sequential-list") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      "/user/chapters",
      "/user/chapters/:chapterId",
      "/user/presentation",
      "/user/coach",
      "/user/classroom",
      "/user/animation",
      "/user/code",
      "/user/knowledge",
      "/user/resources/:resourceId",
    ].map((route) => ({ path: route, component: { template: "<div />" } })),
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(ChapterDetailView, {
    global: {
      plugins: [router],
      stubs: { UserFrame: { template: "<div><slot /><slot name='rail' /></div>" } },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("ChapterDetailView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authMock.state.user = null;
    setLocale("zh-CN");
  });

  afterEach(() => {
    setLocale("zh-CN");
  });

  it("English 翻译章节 chrome 与本地预览，而不读取游客 API", async () => {
    setLocale("en-US");
    const wrapper = await mountView();

    expect(userApiMock.listChapters).not.toHaveBeenCalled();
    expect(wrapper.get("h1").text()).toBe("Chapter 2: Insertion in a sequential list");
    expect(wrapper.text()).toContain("Local preview");
    expect(wrapper.text()).toContain("Open courseware");
    expect(wrapper.text()).toContain("Preview learning entry");
    expect(wrapper.text()).toContain("This local preview is available to browse");
    expect(wrapper.text()).not.toContain("播放本章课件");
  });

  it("English 在未知章节时显示本地化错误", async () => {
    setLocale("en-US");
    const wrapper = await mountView("/user/chapters/unknown-chapter");

    expect(wrapper.text()).toContain("Chapter unavailable");
    expect(wrapper.text()).toContain("Return to the course map");
    expect(wrapper.text()).not.toContain("暂时找不到这个章节");
  });
});
