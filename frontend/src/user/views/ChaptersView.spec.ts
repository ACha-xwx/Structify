import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, mockApi } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: number; email: string; roles: string[] } } },
  mockApi: { listChapters: vi.fn() },
}));

vi.mock("../runtime", () => ({ userApi: mockApi }));
vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));

import ChaptersView from "./ChaptersView.vue";
import { setLocale } from "../../shared/i18n/locale";

const chapter = {
  id: "stack",
  chapterNumber: 2,
  title: "栈与队列",
  summary: "用受限访问顺序理解线性结构的操作边界。",
};

async function mountView(path = "/user/chapters", stubFrame = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      "/user/chapters",
      "/user/chapters/:chapterId",
      "/user/resources/:resourceId",
      "/user/presentation",
      "/user/animation",
      "/user/knowledge",
      "/user/classroom",
      "/user/code",
      "/user/coach",
      "/user/home",
      "/user/progress",
      "/user/profile",
      "/login",
      "/",
    ].map((route) => ({ path: route, component: { template: "<div />" } })),
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(ChaptersView, {
    global: {
      plugins: [router],
      ...(stubFrame ? { stubs: { UserFrame: { template: "<div><slot /><slot name='rail' /></div>" } } } : {}),
    },
  });
  await flushPromises();
  return wrapper;
}

describe("ChaptersView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setLocale("zh-CN");
    authMock.state.user = null;
    mockApi.listChapters.mockResolvedValue([chapter]);
  });
  afterEach(() => {
    setLocale("zh-CN");
  });

  it("真实课程壳只保留一份课程导航，不再叠加章节内嵌侧栏", async () => {
    const wrapper = await mountView("/user/chapters?chapterId=sequential-list", false);

    expect(wrapper.findAll(".user-frame-sidebar")).toHaveLength(1);
    expect(wrapper.findAll(".user-frame-course-nav")).toHaveLength(1);
    expect(wrapper.findAll(".chapters-runtime__catalog")).toHaveLength(0);
    expect(wrapper.findAll("nav[aria-label='课程地图']")).toHaveLength(0);
    expect(wrapper.findAll(".user-frame-course-item").length).toBeGreaterThan(0);
    expect(wrapper.get('[data-course-id="sequential-list"]').classes()).toContain("is-active");
  });

  it("游客用完整课程图谱承接到真实的预览入口，不读取章节 API", async () => {
    const wrapper = await mountView();

    expect(mockApi.listChapters).not.toHaveBeenCalled();
    expect(wrapper.get(".chapters-runtime__scope").text()).toContain("6主题");
    expect(wrapper.get(".chapters-runtime__scope").text()).toContain("10章");
    expect(wrapper.get(".chapters-runtime__scope").text()).toContain("29单元");
    expect(wrapper.get('[data-action-id="presentation"]').attributes("href")).toBe("/user/presentation?lessonId=02-02B&chapterId=02-linear-list&from=chapter");
    expect(wrapper.get('[data-action-id="animation"]').attributes("href")).toBe("/user/animation?chapterId=02-linear-list&lessonId=sequential-list&from=chapter");
    expect(wrapper.get('[data-action-id="resource"]').attributes("href")).toBe("/user/resources/preview-sequential-list-handout");
    expect(wrapper.findAll(".chapters-runtime__tools a")[0]?.attributes("href")).toBe("/user/presentation?lessonId=02-02B&chapterId=02-linear-list&from=chapter");
    expect(wrapper.findAll(".chapters-runtime__tools a")[1]?.attributes("href")).toBe("/user/code?chapterId=02-linear-list&lessonId=sequential-list&from=chapter");
    expect(wrapper.findAll(".chapters-runtime__tools a")).toHaveLength(2);

    const more = wrapper.get(".chapters-runtime__more").element as HTMLDetailsElement;
    expect(more.open).toBe(false);
    await wrapper.get(".chapters-runtime__more summary").trigger("click");
    expect(more.open).toBe(true);
    expect(wrapper.findAll(".chapters-runtime__more .chapters-runtime__action")).toHaveLength(3);
    expect(wrapper.text()).toContain("模型动作需登录");
  });

  it("登录后使用课程 API，并以同一图谱显示服务端章节", async () => {
    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    mockApi.listChapters.mockResolvedValue([chapter]);
    const wrapper = await mountView();

    expect(mockApi.listChapters).toHaveBeenCalledTimes(1);
    expect(wrapper.get(".chapters-runtime__status").attributes("data-source")).toBe("published");
    expect(wrapper.get(".chapters-runtime__scope").text()).toContain("1主题");
    expect(wrapper.get(".chapters-runtime__heading").text()).toContain("栈与队列");
    expect(wrapper.get('[data-action-id="knowledge"]').attributes("href")).toBe("/user/knowledge?chapterId=stack&from=chapter");
  });

  it("登录态将工作台的顺序表单元别名解析到服务端第二章", async () => {
    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    mockApi.listChapters.mockResolvedValue([{ ...chapter, id: "02-linear-list", chapterNumber: 2, title: "线性表" }]);
    const wrapper = await mountView("/user/chapters?chapterId=sequential-list");

    expect(wrapper.get(".chapters-runtime__chapter").attributes("data-chapter-id")).toBe("02-linear-list");
    expect(wrapper.get('[data-action-id="presentation"]').attributes("href")).toContain("chapterId=02-linear-list");
  });

  it("从工作台查询参数选择对应的游客章节，并打开匹配的本地课堂与算法场景", async () => {
    const wrapper = await mountView("/user/chapters?chapterId=07-graph");

    expect(wrapper.get(".chapters-runtime__chapter").attributes("data-chapter-id")).toBe("07-graph");
    expect(wrapper.get(".chapters-runtime__chapter").text()).toContain("图");
    expect(wrapper.get(".chapters-runtime__chapter").text()).not.toContain("顺序表的插入");
    expect(wrapper.get('[data-action-id="presentation"]').attributes("href")).toBe("/user/presentation?lessonId=07-01&chapterId=07-graph&from=chapter");
    expect(wrapper.get('[data-action-id="animation"]').attributes("href")).toBe("/user/animation?chapterId=07-graph&from=chapter");
    expect(wrapper.get('[data-action-id="animation"]').text()).toContain("打开算法舞台");
    expect(wrapper.get('[data-action-id="classroom"]').attributes("href")).toBe("/user/classroom?chapterId=07-graph&from=chapter");
    expect(wrapper.get('[data-action-id="knowledge"]').attributes("href")).toBe("/user/knowledge?chapterId=07-graph&from=chapter");
  });

  it("课程地图选择具体 lesson 时把 lessonId 和父章节一起带入各学习入口", async () => {
    const wrapper = await mountView("/user/chapters?chapterId=07-graph&lessonId=bfs");

    expect(wrapper.get('[data-action-id="animation"]').attributes("href")).toBe("/user/animation?chapterId=07-graph&lessonId=bfs&from=chapter");
    expect(wrapper.get('[data-action-id="classroom"]').attributes("href")).toBe("/user/classroom?chapterId=07-graph&lessonId=bfs&from=chapter");
    expect(wrapper.get('[data-action-id="code"]').attributes("href")).toBe("/user/code?chapterId=07-graph&lessonId=bfs&from=chapter");
    expect(wrapper.get('[data-action-id="coach"]').attributes("href")).toBe("/user/coach?chapterId=07-graph&lessonId=bfs&from=chapter");
  });

  it("顺序表学习单元只标记自身，并在共享课程壳中显示当前章节", async () => {
    const wrapper = await mountView("/user/chapters?chapterId=sequential-list");

    expect(wrapper.get(".chapters-runtime__chapter").attributes("data-chapter-id")).toBe("sequential-list");
    expect(wrapper.get(".chapters-runtime__chapter").text()).toContain("顺序表的插入");
    expect(wrapper.find(".chapters-runtime__catalog").exists()).toBe(false);
  });

  it("同一目录内切换 chapterId 会更新当前章节与选中条目", async () => {
    const wrapper = await mountView("/user/chapters?chapterId=07-graph");
    const router = (wrapper.vm as unknown as { $router: { push: (to: string) => Promise<void> } }).$router;

    await router.push("/user/chapters?chapterId=06-tree");
    await flushPromises();

    expect(wrapper.get(".chapters-runtime__chapter").attributes("data-chapter-id")).toBe("06-tree");
    expect(wrapper.get(".chapters-runtime__chapter").text()).toContain("树");
  });

  it("章节二使用章节开场课件，顺序表 lesson 单独使用插入课件", async () => {
    const chapterWrapper = await mountView("/user/chapters?chapterId=02-linear-list");
    expect(chapterWrapper.get('[data-action-id="presentation"]').attributes("href")).toBe("/user/presentation?lessonId=02-01&chapterId=02-linear-list&from=chapter");

    const lessonWrapper = await mountView("/user/chapters?chapterId=sequential-list");
    expect(lessonWrapper.get('[data-action-id="presentation"]').attributes("href")).toBe("/user/presentation?lessonId=02-02B&chapterId=02-linear-list&from=chapter");
  });

  it("登录态查询未命中服务端章节时不回退到本地 fixture", async () => {
    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    mockApi.listChapters.mockResolvedValue([chapter]);
    const wrapper = await mountView("/user/chapters?chapterId=07-graph");

    expect(wrapper.get(".chapters-runtime__heading").text()).toContain("栈与队列");
    expect(wrapper.find(".chapters-runtime__chapter").exists()).toBe(false);
    expect(wrapper.get(".chapters-runtime__status").text()).toBe("已发布课程");
    expect(wrapper.text()).not.toContain("本地预览");
  });

  it("切换 English 时同步翻译目录操作与服务错误", async () => {
    setLocale("en-US");
    const guestWrapper = await mountView("/user/chapters?chapterId=sequential-list");

    expect(guestWrapper.text()).toContain("Course map");
    expect(guestWrapper.text()).toContain("Open courseware");
    expect(guestWrapper.text()).toContain("Model actions require sign-in");
    expect(guestWrapper.text()).not.toContain("播放本章课件");

    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    mockApi.listChapters.mockRejectedValue(Object.assign(new Error("gateway"), { status: 503 }));
    const errorWrapper = await mountView();

    expect(errorWrapper.text()).toContain("Learning service unavailable");
    expect(errorWrapper.text()).toContain("Retry");
  });
});
