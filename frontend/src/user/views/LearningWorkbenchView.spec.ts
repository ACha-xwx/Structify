import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, createWorkbenchPreviewMock, loadWorkbenchMock, userApiMock } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: string; username?: string; email?: string } } },
  createWorkbenchPreviewMock: vi.fn(),
  loadWorkbenchMock: vi.fn(),
  userApiMock: {
    listChapters: vi.fn(),
    getLearningProgress: vi.fn(),
    getReadiness: vi.fn(),
    simulateAnimation: vi.fn(),
  },
}));

vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));
vi.mock("../runtime", () => ({ userApi: userApiMock }));
vi.mock("../adapters/learning-workbench", () => ({ createLearningWorkbenchPreview: createWorkbenchPreviewMock, loadLearningWorkbench: loadWorkbenchMock }));

import LearningWorkbenchView from "./LearningWorkbenchView.vue";
import { learningWorkbenchFixture } from "../fixtures/learning-workbench";
import { setLocale } from "../../shared/i18n/locale";
import { setTheme } from "../../shared/design/theme";
import type { DsvpSimulationResponse } from "../../shared/types/contracts";
import type { LearningWorkbenchSnapshot } from "../adapters/learning-workbench";

const liveContextSnapshot: LearningWorkbenchSnapshot = {
  mode: "live",
  scene: null,
  persistedStage: null,
  contextMode: "live",
  fixtureReason: null,
  chapters: [
    { id: "linear-list", chapterNumber: 1, title: "线性表", summary: "掌握线性结构的基本操作。" },
    { id: "sequential-list", chapterNumber: 2, title: "顺序表", summary: "理解连续存储与插入边界。" },
  ],
  currentChapter: { id: "sequential-list", chapterNumber: 2, title: "顺序表", summary: "理解连续存储与插入边界。" },
  currentProgress: {
    chapterId: "sequential-list",
    chapterNumber: 2,
    title: "顺序表",
    chatCount: 1,
    classroomCount: 0,
    animationCount: 2,
    codeRunCount: 1,
    eventCount: 4,
    totalActivities: 4,
    lastActivityAt: "2026-08-25T07:00:00Z",
  },
  readiness: null,
  contextLabel: "第 2 章 顺序表",
  contextDetail: "最近活动 4 项。",
};

const liveSimulationResponse: DsvpSimulationResponse = {
  protocol: "dsvp/1.0",
  request: {
    version: "1.0",
    structure: "sequential_list",
    operation: "insert",
    params: { index: 2, value: 23, capacity: 8 },
    initial_state: { data: [7, 11, 19], metadata: { capacity: 8 } },
    chapterId: "sequential-list",
    source_ref: "workbench/chapter/sequential-list/stage",
    context: {
      chapter_id: "sequential-list",
      source_type: "API",
      source_ref: "workbench/chapter/sequential-list/stage",
    },
  },
  trace: { protocol: "dsvp/1.0", steps: [] },
  animationData: {
    animation: true,
    type: "array",
    title: "顺序表插入演示",
    description: "服务端返回的顺序表插入步骤。",
    initial: [7, 11, 19],
    steps: [
      { op: "insert", label: "写入 23", note: "在下标 2 写入新值。", value: 23, index: 2 },
    ],
  },
  recordId: "animation-1",
  evidencePersisted: true,
  animationRecordId: "animation-1",
  resolvedChapterId: "sequential-list",
  matchSource: "EXPLICIT_CHAPTER",
};

const persistedLiveStage = {
  mode: "live" as const,
  chapterId: "sequential-list",
  title: "顺序表插入演示",
  description: "从已保存的学习场景恢复。",
  structureLabel: "array",
  definition: liveSimulationResponse.animationData,
  steps: [
    { id: "live-step-1", label: "写入 23", detail: "在下标 2 写入新值。" },
  ],
  animationRecordId: "saved-animation-1",
  evidencePersisted: true,
};

async function mountView(user: { id: string; username?: string; email?: string } | null = null) {
  authMock.state.user = user;
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/user/home", component: { template: "<div />" } },
      { path: "/user/animation", component: { template: "<div />" } },
      { path: "/user/presentation", component: { template: "<div />" } },
      { path: "/user/code", component: { template: "<div />" } },
      { path: "/user/coach", component: { template: "<div />" } },
      { path: "/user/classroom", component: { template: "<div />" } },
      { path: "/user/knowledge", component: { template: "<div />" } },
      { path: "/user/progress", component: { template: "<div />" } },
      { path: "/user/profile", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/user/home");
  await router.isReady();
  const wrapper = mount(LearningWorkbenchView, {
    attachTo: document.body,
    global: {
      plugins: [router],
    },
  });
  await flushPromises();
  return Object.assign(wrapper, { testRouter: router });
}

describe("LearningWorkbenchView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setTheme("light");
    setLocale("zh-CN");
    authMock.state.user = null;
    createWorkbenchPreviewMock.mockReturnValue({
      mode: "fixture",
      scene: learningWorkbenchFixture,
      chapters: [],
      currentChapter: null,
      currentProgress: null,
      readiness: null,
      contextLabel: "本地教学预览",
      contextDetail: learningWorkbenchFixture.sourceDetail,
    });
    loadWorkbenchMock.mockResolvedValue({
      mode: "fixture",
      scene: learningWorkbenchFixture,
      chapters: [],
      currentChapter: null,
      currentProgress: null,
      readiness: null,
      contextLabel: "本地教学预览",
      contextDetail: learningWorkbenchFixture.sourceDetail,
    });
  });

  afterEach(() => {
    setTheme("light");
    setLocale("zh-CN");
    document.body.replaceChildren();
  });

  it("保持工具链接的工作台上下文，并将内容来源状态藏在无障碍说明中", async () => {
    const wrapper = await mountView();

    expect(wrapper.text()).not.toContain("本地预览");
    expect(wrapper.text()).not.toContain("预览内容，不保存学习记录");
    expect(wrapper.text()).not.toContain("示例学习上下文");
    expect(wrapper.text()).not.toContain("完成率");
    expect(wrapper.find(".workbench-runtime-context").exists()).toBe(false);
    expect(wrapper.find(".workbench-context-bar__mode").exists()).toBe(false);
    expect(wrapper.get(".workbench-current-task").attributes("data-goal-state")).toBe("active");
    expect(wrapper.get(".workbench-current-task").attributes("data-goal-id")).toBe("sequential-list-insertion-boundary");
    expect(wrapper.get(".workbench-current-task").attributes("data-context-mode")).toBe("fixture");
    expect(wrapper.get(".workbench-current-task .sr-only").text()).toContain("互动教学示例");
    expect(wrapper.find(".workbench-current-task__boundary").exists()).toBe(false);
    expect(wrapper.find(".workbench-current-task--fallback").exists()).toBe(false);
    expect(wrapper.get(".learning-workbench-v2").attributes("data-locale")).toBe("zh-CN");
    expect(wrapper.get(".workbench-layout").attributes("data-shell")).toBe("dashboard-sidebar");
    expect(wrapper.find(".user-frame-runtime-content").exists()).toBe(false);
    expect(wrapper.find(".user-frame-layout").exists()).toBe(false);
    expect(wrapper.find(".ai-runtime-frame").exists()).toBe(false);
    expect(wrapper.findAll(".user-frame-mobile-course-link")).toHaveLength(0);
    expect(wrapper.findAll(".workbench-mobile-outline__group")).toHaveLength(6);
    expect(wrapper.find(".workbench-heading__position").exists()).toBe(false);
    expect(wrapper.find(".workbench-heading__context").exists()).toBe(false);
    expect(wrapper.find(".workbench-heading__meta").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("4 个学习单元");
    expect(wrapper.get(".workbench-rail__head").text()).toContain("资料与下一步");
    expect(wrapper.get(".workbench-rail__head").text()).not.toMatch(/\d{2}\s*\/\s*\d{2}/);
    expect(wrapper.get(".workbench-rail__head").text()).not.toContain("顺序表的插入");
    expect(wrapper.get('[data-testid="workbench-context-drawer"]').element.tagName).toBe("DETAILS");
    expect((wrapper.get('[data-testid="workbench-context-drawer"]').element as HTMLDetailsElement).open).toBe(false);
    expect(wrapper.find("main .workbench-context-drawer .workbench-rail").exists()).toBe(true);
    expect(loadWorkbenchMock).not.toHaveBeenCalled();
    expect(wrapper.get(".workbench-workspace-mark").text()).toBe("DS");
    expect(wrapper.get(".workbench-primary-nav .workbench-nav-row.is-active").text()).toContain("学习台");
    expect(wrapper.get(".algorithm-stage__open").attributes("href")).toBe("/user/animation?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.get('[data-testid="workbench-play"] use').attributes("href")).toBe("#wb-play");
    expect(wrapper.get('[data-testid="workbench-play"]').attributes("aria-label")).toBe("播放轨迹");
    expect(wrapper.get(".workbench-primary-action").attributes("href")).toBe("/user/chapters?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.find(".workbench-secondary-action").exists()).toBe(false);
    expect(wrapper.get(".workbench-course-outline").text()).toContain("顺序表的插入");
    expect(wrapper.findAll(".workbench-course-group")).toHaveLength(6);
    expect(wrapper.findAll(".workbench-course-group > summary")).toHaveLength(6);
    expect(wrapper.findAll(".workbench-course-group__pip")).toHaveLength(1);
    expect(wrapper.findAll(".workbench-course-item").length).toBeGreaterThan(20);
    expect(wrapper.get(".workbench-course-outline__scope").text()).toContain("10 章");
    expect(wrapper.get(".workbench-course-outline__scope").text()).toContain("6 主题");
    expect(wrapper.get(".workbench-course-outline__scope").text()).toContain("29 单元");
    expect(wrapper.get('[data-capability="local-interactive-preview"]').text()).toContain("本地交互预览");
    expect(wrapper.findAll('[data-capability="catalog"]').length).toBeGreaterThan(20);
    expect(wrapper.findAll(".workbench-course-group__count")).toHaveLength(6);
    expect(wrapper.get(".workbench-course-item.is-active").attributes("aria-current")).toBe("page");
    expect(wrapper.get(".workbench-rail__link").attributes("href")).toBe("/user/knowledge?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");

    const toolLinks = wrapper.findAll(".workbench-main-tools > a");
    expect(toolLinks[0].attributes("href")).toBe("/user/presentation?lessonId=02-02B&chapterId=02-linear-list&from=workbench");
    expect(toolLinks[1].attributes("href")).toBe("/user/code?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.find(".workbench-tools-menu").exists()).toBe(false);
    expect(wrapper.get(".workbench-rail__review").attributes("href")).toBe("/user/progress?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.get(".workbench-rail__action").attributes("href")).toBe("/user/coach?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(wrapper.findAll(".workbench-rail__section")).toHaveLength(2);
    expect(wrapper.text()).not.toContain("当前步骤");
    expect((wrapper.get(".workbench-timeline").element as HTMLDetailsElement).open).toBe(false);
    expect(wrapper.findAll(".workbench-timeline__listitem")).toHaveLength(4);
    expect(wrapper.get(".workbench-timeline__listitem").element.tagName).toBe("LI");
    expect(wrapper.get(".workbench-timeline__item").element.tagName).toBe("BUTTON");
    expect(wrapper.get(".workbench-timeline__item").attributes("role")).toBeUndefined();
    expect(wrapper.get(".algorithm-stage__controls").attributes("role")).toBe("group");
    expect(wrapper.get('[data-testid="workbench-stage-announcement"]').attributes("aria-live")).toBe("polite");
  });

  it("登录后才读取真实学习上下文", async () => {
    await mountView({ id: "user-1", username: "learner" });

    expect(loadWorkbenchMock).toHaveBeenCalledTimes(1);
    expect(loadWorkbenchMock).toHaveBeenCalledWith(userApiMock);
  });

  it("把真实章节上下文与本地算法场景明确分开", async () => {
    loadWorkbenchMock.mockResolvedValue(liveContextSnapshot);
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    expect(wrapper.get(".workbench-main__heading").text()).toContain("顺序表");
    expect(wrapper.find(".workbench-current-task").exists()).toBe(false);
    expect(wrapper.find(".algorithm-stage").exists()).toBe(false);
    expect(wrapper.findAll('[data-capability="api-published"]').length).toBeGreaterThan(0);
    expect(wrapper.get(".workbench-context-handoff").text()).toContain("章节上下文");
    expect(wrapper.get(".workbench-context-handoff").text()).toContain("顺序表");
    expect(wrapper.get(".workbench-context-handoff").text()).not.toContain("预览目标");
    expect(wrapper.get(".workbench-context-handoff__overview").text()).toContain("章节概览");
    expect(wrapper.get(".workbench-context-handoff__overview").text()).toContain("当前可用");
    expect(wrapper.get(".workbench-context-handoff__desk").text()).toContain("本章单元");
    expect(wrapper.get(".workbench-context-handoff__desk").text()).toContain("顺序表的插入");
    expect(wrapper.get(".workbench-context-handoff__desk").text()).toContain("建议路径");
  });

  it("已登录的无场景章节不会自动请求实时演示", async () => {
    loadWorkbenchMock.mockResolvedValue(liveContextSnapshot);
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    expect(wrapper.get('[data-testid="workbench-launch-stage"]').text()).toContain("生成交互演示");
    expect(userApiMock.simulateAnimation).not.toHaveBeenCalled();
    expect(wrapper.find(".algorithm-stage").exists()).toBe(false);
  });

  it("登录后恢复已保存的算法场景，不重新请求模拟服务或抢占焦点", async () => {
    loadWorkbenchMock.mockResolvedValue({
      ...liveContextSnapshot,
      persistedStage: persistedLiveStage,
    });
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    expect(userApiMock.simulateAnimation).not.toHaveBeenCalled();
    expect(wrapper.get(".workbench-current-task").attributes("data-goal-id")).toBe("server-animation");
    expect(wrapper.get(".algorithm-stage").attributes("data-stage-mode")).toBe("live");
    expect(wrapper.get(".algorithm-stage").text()).toContain("顺序表插入演示");
    expect(wrapper.get('[data-testid="workbench-stage-announcement"]').text()).toContain("写入 23");
    expect(document.activeElement).not.toBe(wrapper.get("#stage-title").element);
  });

  it("没有当前已发布章节时不显示实时演示入口", async () => {
    loadWorkbenchMock.mockResolvedValue({
      ...liveContextSnapshot,
      currentChapter: null,
      currentProgress: null,
    });
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    expect(wrapper.find('[data-testid="workbench-launch-stage"]').exists()).toBe(false);
    expect(userApiMock.simulateAnimation).not.toHaveBeenCalled();
  });

  it("已登录后显式请求实时演示，并只发送当前课程服务章节的嵌套上下文", async () => {
    const publishedChapter = { id: "published-linear-42", chapterNumber: 42, title: "顺序表", summary: "来自课程服务的已发布章节。" };
    loadWorkbenchMock.mockResolvedValue({
      ...liveContextSnapshot,
      chapters: [publishedChapter],
      currentChapter: publishedChapter,
    });
    userApiMock.simulateAnimation.mockResolvedValue({ ...liveSimulationResponse, resolvedChapterId: publishedChapter.id });
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    await wrapper.get('[data-testid="workbench-launch-stage"]').trigger("click");
    await flushPromises();

    expect(userApiMock.simulateAnimation).toHaveBeenCalledTimes(1);
    const request = userApiMock.simulateAnimation.mock.calls[0]?.[0];
    expect(request).toMatchObject({
      version: "1.0",
      structure: "sequential_list",
      operation: "insert",
      chapterId: "published-linear-42",
      source_ref: "workbench/chapter/published-linear-42/stage",
      context: {
        chapter_id: "published-linear-42",
        source_type: "API",
        source_ref: "workbench/chapter/published-linear-42/stage",
      },
    });
    expect(request.lessonId).toBeUndefined();
    expect(request.lesson_id).toBeUndefined();
    expect(request.context.chapter_id).not.toBe("sequential-list");
    expect(wrapper.find(".workbench-context-handoff").exists()).toBe(false);
    expect(wrapper.get(".workbench-current-task").attributes("data-goal-id")).toBe("server-animation");
    expect(wrapper.get(".algorithm-stage").attributes("data-stage-mode")).toBe("live");
    expect(wrapper.get(".algorithm-stage").text()).toContain("顺序表插入演示");
    expect(wrapper.get(".algorithm-stage").text()).toContain("写入 23");
    expect(wrapper.get('[data-testid="workbench-stage-announcement"]').text()).toContain("写入 23");
    expect(document.activeElement).toBe(wrapper.get("#stage-title").element);
  });

  it("实时演示请求失败时保留真实章节上下文和原位错误，不回退为 fixture", async () => {
    loadWorkbenchMock.mockResolvedValue(liveContextSnapshot);
    userApiMock.simulateAnimation.mockRejectedValue(Object.assign(new Error("unavailable"), { status: 503 }));
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    await wrapper.get('[data-testid="workbench-launch-stage"]').trigger("click");
    await flushPromises();

    expect(userApiMock.simulateAnimation).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-context-status="stage-error"]').text()).toContain("学习服务暂不可用");
    expect(wrapper.find(".workbench-context-handoff").exists()).toBe(true);
    expect(wrapper.find(".workbench-current-task").exists()).toBe(false);
    expect(wrapper.find(".algorithm-stage").exists()).toBe(false);
    expect(wrapper.get(".learning-workbench-v2").attributes("data-context-mode")).toBe("live");
    expect(wrapper.get(".workbench-context-handoff").text()).not.toContain("本地教学预览");
  });

  it("让真实当前章节驱动上下文和直接工具入口", async () => {
    const linkedList = { id: "linked-list", chapterNumber: 3, title: "链表", summary: "理解节点与指针。" };
    loadWorkbenchMock.mockResolvedValue({
      ...liveContextSnapshot,
      chapters: [linkedList],
      currentChapter: linkedList,
    });

    const wrapper = await mountView({ id: "user-1", username: "learner" });

    expect(wrapper.get(".workbench-main__heading").text()).toContain("链表");
    expect(wrapper.find(".algorithm-stage").exists()).toBe(false);
    expect(wrapper.get(".workbench-context-handoff").get(".workbench-primary-action").attributes("href")).toBe("/user/chapters?chapterId=02-linear-list&lessonId=linked-list&from=workbench");
    expect(wrapper.get(".workbench-context-handoff__overview").text()).not.toContain("已登录章节上下文");
    expect(wrapper.get(".workbench-context-handoff__action-list").text()).toContain("进入课堂");
    expect(wrapper.get(".workbench-context-handoff__action-list").text()).toContain("进入问答");
    expect(wrapper.get(".workbench-context-handoff__lesson-list").text()).toContain("链表");
    expect(wrapper.get(".workbench-rail__action").attributes("href")).toContain("chapterId=02-linear-list");
    expect(wrapper.get(".workbench-rail__action").attributes("href")).toContain("lessonId=linked-list");
    expect(wrapper.findAll(".workbench-main-tools > a")[0].attributes("href")).toContain("chapterId=02-linear-list");
    expect(wrapper.findAll(".workbench-main-tools > a")[1].attributes("href")).toContain("chapterId=02-linear-list");

    await wrapper.get('button[aria-label="检索课程资料"]').trigger("click");
    await wrapper.get('input[aria-label="检索课程资料"]').setValue("指针");
    await wrapper.get('button[aria-label="提交检索"]').trigger("click");
    await flushPromises();
    expect(wrapper.testRouter.currentRoute.value.fullPath).toContain("chapterId=linked-list");
  });

  it("API 失败时保留工作台骨架并显示原位错误，不静默伪装成真实记录", async () => {
    loadWorkbenchMock.mockRejectedValue(Object.assign(new Error("gateway"), { status: 503 }));
    const wrapper = await mountView({ id: "user-1", username: "learner" });

    expect(wrapper.get(".workbench-context-error").text()).toContain("学习服务暂不可用");
    expect(wrapper.get(".workbench-context-error").text()).toContain("重试读取");
    expect(wrapper.find(".workbench-current-task").exists()).toBe(false);
    expect(wrapper.find(".workbench-current-task--fallback").exists()).toBe(false);
    expect(wrapper.get(".learning-workbench-v2").attributes("data-context-mode")).toBe("fixture");
    expect(wrapper.text()).not.toContain("本地预览");
    expect(wrapper.text()).not.toContain("预览内容，不保存学习记录");
    expect(wrapper.find("a[href^='/login']").exists()).toBe(false);
  });

  it("支持单步推进、重置和时间线跳转", async () => {
    const wrapper = await mountView();
    const next = wrapper.get('button[aria-label="下一步"]');
    const reset = wrapper.get('button[aria-label="重置演示"]');

    await next.trigger("click");
    expect(wrapper.text()).toContain("比较 27");
    await wrapper.get(".workbench-timeline__listitem:nth-child(3) .workbench-timeline__item").trigger("click");
    expect(wrapper.text()).toContain("右移 27");
    await reset.trigger("click");
    expect(wrapper.text()).toContain("定位空位");
  });

  it("把完整课程图谱保持在首屏入口，并允许独立展开每个主题", async () => {
    const wrapper = await mountView();
    const groups = wrapper.findAll<HTMLDetailsElement>(".workbench-course-group");

    expect(groups).toHaveLength(6);
    expect(groups.map((group) => group.get("summary").text())).toEqual(expect.arrayContaining([
      expect.stringContaining("线性结构"),
      expect.stringContaining("栈与队列"),
      expect.stringContaining("树与堆"),
      expect.stringContaining("图结构"),
      expect.stringContaining("查找与散列"),
      expect.stringContaining("排序与复杂度"),
    ]));
    expect(groups.every((group) => !(group.element as HTMLDetailsElement).open)).toBe(true);

    const stacks = groups[1];
    expect((stacks.element as HTMLDetailsElement).open).toBe(false);
    await stacks.get("summary").trigger("click");
    await flushPromises();
    expect((stacks.element as HTMLDetailsElement).open).toBe(true);

    const chapterLinks = wrapper.findAll(".workbench-main-tools > a");
    expect(chapterLinks).toHaveLength(2);
    expect(chapterLinks.map((link) => link.text())).toEqual(expect.arrayContaining(["课程课件公开预览", "C 编译器代码练习"]));
  });

  it("从工作台选择 BFS lesson 时保留父章节和 lessonId", async () => {
    const wrapper = await mountView();
    const graphGroup = wrapper.get('[data-group-id="graphs"]');
    await graphGroup.get("summary").trigger("click");

    const bfs = wrapper.get('[data-course-id="bfs"]');
    expect(bfs.attributes("href")).toBe("/user/chapters?chapterId=07-graph&lessonId=bfs&from=workbench");
  });

  it("Escape 关闭检索和工具菜单，并把焦点还给触发控件", async () => {
    const wrapper = await mountView();
    const searchTrigger = wrapper.get('button[aria-label="检索课程资料"]');

    await searchTrigger.trigger("click");
    const input = wrapper.get('input[aria-label="检索课程资料"]');
    expect(document.activeElement).toBe(input.element);
    await input.trigger("keydown", { key: "Escape" });
    await flushPromises();
    expect(wrapper.find(".workbench-search").exists()).toBe(false);
    expect(document.activeElement).toBe(wrapper.get('button[aria-label="检索课程资料"]').element);

    expect(wrapper.find(".workbench-tools-menu").exists()).toBe(false);
  });

  it("在桌面和移动导航中都保留可操作的主题开关", async () => {
    const wrapper = await mountView();
    const toggles = wrapper.findAll('.theme-toggle__control');

    expect(toggles).toHaveLength(1);
    expect(document.documentElement.dataset.theme).toBe("light");

    await toggles[0].trigger("click");
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(toggles[0].attributes("aria-checked")).toBe("true");

    await toggles[0].trigger("click");
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(toggles[0].attributes("aria-checked")).toBe("false");
    setTheme("light");
  });

  it("切换 English 时同步更新学习台正文和工具入口", async () => {
    const wrapper = await mountView();
    const localeTrigger = wrapper.get(".theme-toggle__locale-toggle");
    await localeTrigger.trigger("click");

    expect(wrapper.text()).toContain("Insertion in a sequential list");
    expect(wrapper.text()).toContain("Course outline");
    expect(wrapper.text()).toContain("Open full stage");
    expect(wrapper.text()).not.toContain("Preview only; learning records are not saved");
    expect(wrapper.get(".learning-workbench-v2").attributes("data-locale")).toBe("en-US");
    expect(wrapper.text()).not.toContain("本地教学预览");
    expect(wrapper.find('button[aria-label="Search course materials"]').exists()).toBe(true);

    setLocale("zh-CN");
  });

  it("English 状态下翻译真实章节映射和工作台服务错误", async () => {
    setLocale("en-US");
    loadWorkbenchMock.mockResolvedValue(liveContextSnapshot);
    const liveWrapper = await mountView({ id: "user-1", username: "learner" });

    expect(liveWrapper.get(".workbench-main__heading").text()).toContain("Insertion in a sequential list");
    expect(liveWrapper.get(".workbench-context-handoff").text()).toContain("CHAPTER CONTEXT");
    expect(liveWrapper.get(".workbench-context-handoff__overview").text()).toContain("Chapter overview");
    expect(liveWrapper.get(".workbench-context-handoff__overview").text()).toContain("Available now");

    loadWorkbenchMock.mockRejectedValue(Object.assign(new Error("gateway"), { status: 503 }));
    const errorWrapper = await mountView({ id: "user-1", username: "learner" });

    expect(errorWrapper.get(".workbench-context-error").text()).toContain("Learning service unavailable");
    expect(errorWrapper.get(".workbench-context-error").text()).toContain("Retry context");
    expect(errorWrapper.find(".workbench-current-task").exists()).toBe(false);
    expect(errorWrapper.find(".workbench-current-task--fallback").exists()).toBe(false);
    expect(errorWrapper.text()).not.toContain("Preview only; learning records are not saved");
  });
});
