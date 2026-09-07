import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setLocale } from "../../shared/i18n/locale";
import { classroomPreviewNavigationContext, classroomPreviewScripts, classroomPreviewScriptsForChapter, createClassroomPreviewSession } from "../fixtures/classroom-preview";
import { flattenCourseGroups, learningCourseGroups } from "../fixtures/learning-workbench";

const { authMock, mockApi } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: number; email: string; roles: string[] } } },
  mockApi: {
    listClassroomScripts: vi.fn(),
    startClassroom: vi.fn(),
    getClassroomSession: vi.fn(),
    actInClassroom: vi.fn(),
  },
}));

vi.mock("../runtime", () => ({ userApi: mockApi }));
vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));

import ClassroomView from "./ClassroomView.vue";

const scripts = [{ id: "script-1", chapterId: "stack", title: "Stack seminar", versionLabel: "v1" }];
const waitingSession = {
  id: "session-1",
  userId: 7,
  scriptId: "script-1",
  state: "WAITING" as const,
  paused: false,
  summary: null,
  stage: { question: "What rule does a stack follow?" },
};
const discussedSession = {
  ...waitingSession,
  state: "DISCUSS" as const,
  stage: { discussion: "Correct." },
  answerEvaluation: { status: "CORRECT" as const, misconception: null, feedback: "Correct: LIFO." },
};

async function mountView(path = "/user/classroom?chapterId=stack") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/user/classroom", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
      { path: "/user/animation", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  return mount(ClassroomView, {
    global: {
      plugins: [router],
      stubs: { UserFrame: { template: "<div><slot /><slot name=\"rail\" /></div>" } },
    },
  });
}

describe("ClassroomView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setLocale("zh-CN");
    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    mockApi.listClassroomScripts.mockResolvedValue(scripts);
    mockApi.startClassroom.mockResolvedValue(waitingSession);
    mockApi.actInClassroom.mockResolvedValue(discussedSession);
  });

  it("loads a chapter script, starts its session, and submits the learner answer", async () => {
    const wrapper = await mountView();
    await flushPromises();

    expect(mockApi.listClassroomScripts).toHaveBeenCalledWith("stack");
    expect(wrapper.text()).toContain("Stack seminar");

    await wrapper.get('[data-testid="classroom-start-script-1"]').trigger("click");
    await flushPromises();
    expect(mockApi.startClassroom).toHaveBeenCalledWith("script-1");
    expect(wrapper.vm.$router.currentRoute.value.query.sessionId).toBe("session-1");
    expect(wrapper.get('[data-testid="classroom-animation"]').attributes("href")).toBe("/user/animation?chapterId=stack&from=classroom&sessionId=session-1");

    await wrapper.get('[data-testid="classroom-answer"]').setValue("LIFO");
    await wrapper.get('[data-testid="classroom-action-ANSWER"]').trigger("click");
    await flushPromises();

    expect(mockApi.actInClassroom).toHaveBeenCalledWith("session-1", { action: "ANSWER", content: "LIFO" });
    expect(wrapper.text()).toContain("Correct: LIFO.");
  });

  it("provides local classroom entries for every mapped lesson and chapter", () => {
    expect(classroomPreviewScripts.length).toBeGreaterThanOrEqual(29);
    expect(classroomPreviewScripts.map((script) => script.chapterId)).toEqual(expect.arrayContaining([
      "01-introduction",
      "sequential-list",
      "sequential-list-delete",
      "stack",
      "queue",
      "binary-tree",
      "bfs",
      "binary-search",
      "insertion-sort",
      "10-external-sort",
    ]));
    expect(classroomPreviewScriptsForChapter("03-stack-queue").map((script) => script.chapterId)).toEqual(expect.arrayContaining(["stack", "queue"]));
    const stackScript = classroomPreviewScriptsForChapter("stack")[0]!;
    expect(createClassroomPreviewSession(stackScript.id).stage).toMatchObject({
      topic: "栈与括号匹配",
      prompt: "括号匹配扫描到右括号时，为什么必须检查栈顶而不是栈底？",
    });
    expect(classroomPreviewNavigationContext("preview-classroom-bfs")).toEqual({ chapterId: "07-graph", lessonId: "bfs" });
    expect(classroomPreviewNavigationContext("preview-classroom-sequential-list")).toEqual({ chapterId: "02-linear-list", lessonId: "sequential-list" });
  });

  it("keeps one classroom preview entry for every catalog learning entry", () => {
    const entries = flattenCourseGroups(learningCourseGroups).filter((item) => item.kind === "lesson" || !item.children?.length);

    expect(classroomPreviewScripts).toHaveLength(entries.length);
    for (const item of entries) {
      const scriptsForItem = classroomPreviewScriptsForChapter(item.id);
      expect(scriptsForItem, item.id).toHaveLength(1);
      expect(classroomPreviewNavigationContext(scriptsForItem[0]!.id), item.id).toMatchObject({
        chapterId: item.kind === "lesson" ? (item.parentId ?? item.chapterId ?? item.id) : (item.chapterId ?? item.id),
      });
    }
  });

  it("falls back to clearly labelled local scripts when the authenticated API returns no scripts", async () => {
    mockApi.listClassroomScripts.mockResolvedValue([]);
    const wrapper = await mountView("/user/classroom?chapterId=stack");
    await flushPromises();

    expect(wrapper.text()).toContain("示例预览");
    expect(wrapper.text()).toContain("栈与括号匹配 · 本地课堂");
    await wrapper.get('[data-testid="classroom-start-preview-classroom-stack"]').trigger("click");
    await flushPromises();
    expect(mockApi.startClassroom).not.toHaveBeenCalled();
    expect(wrapper.vm.$router.currentRoute.value.query.sessionId).toBe("preview-classroom-session-stack");
    expect(wrapper.text()).toContain("当前主题");
  });

  it("falls back to local scripts when the classroom service is temporarily unavailable", async () => {
    mockApi.listClassroomScripts.mockRejectedValue({ status: 503 });
    const wrapper = await mountView("/user/classroom?chapterId=queue");
    await flushPromises();

    expect(wrapper.text()).toContain("示例预览");
    expect(wrapper.text()).toContain("课堂服务暂时不可用");
    expect(wrapper.text()).toContain("队列与双端队列 · 本地课堂");
  });

  it("enters an existing session from the URL and restores its state", async () => {
    mockApi.getClassroomSession.mockResolvedValue(discussedSession);
    const wrapper = await mountView("/user/classroom?chapterId=stack&sessionId=session-1");
    await flushPromises();

    expect(mockApi.getClassroomSession).toHaveBeenCalledWith("session-1");
    expect(wrapper.text()).toContain("Correct: LIFO.");
    expect(wrapper.text()).toContain("讨论");
    expect(mockApi.startClassroom).not.toHaveBeenCalled();
  });

  it("shows a permission state instead of classroom scripts when URL session recovery is denied", async () => {
    mockApi.getClassroomSession.mockRejectedValue({ status: 403 });
    const wrapper = await mountView("/user/classroom?chapterId=stack&sessionId=session-1");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("当前账号没有权限");
    expect(wrapper.text()).not.toContain("Stack seminar");
    expect(wrapper.find('[data-testid="classroom-start-script-1"]').exists()).toBe(false);
    expect(wrapper.find("button").exists()).toBe(false);
  });

  it("retries a failed URL session recovery without falling back to classroom scripts", async () => {
    mockApi.getClassroomSession
      .mockRejectedValueOnce({ status: 503 })
      .mockResolvedValueOnce(discussedSession);
    const wrapper = await mountView("/user/classroom?chapterId=stack&sessionId=session-1");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("学习服务暂不可用");
    expect(wrapper.text()).not.toContain("Stack seminar");
    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(mockApi.getClassroomSession).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("Correct: LIFO.");
    expect(wrapper.find('[data-testid="classroom-start-script-1"]').exists()).toBe(false);
  });

  it("游客使用本地课堂预览，不请求会话接口", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/classroom?chapterId=sequential-list");
    await flushPromises();

    expect(mockApi.listClassroomScripts).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("示例预览");
    expect(wrapper.text()).toContain("顺序表插入 · 本地课堂");
    await wrapper.get('[data-testid="classroom-start-preview-classroom-sequential-list"]').trigger("click");
    await flushPromises();
    expect(mockApi.startClassroom).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="classroom-action-CONTINUE"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("当前主题");
    expect(wrapper.text()).toContain("课堂问题");
    expect(wrapper.text()).toContain("思考提示");
  });

  it("游客从无章节上下文开始预览课堂时，把脚本章节写入会话和算法舞台链接", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/classroom");
    await flushPromises();

    await wrapper.get('[data-testid="classroom-start-preview-classroom-sequential-list"]').trigger("click");
    await flushPromises();

    expect(wrapper.vm.$router.currentRoute.value.query).toMatchObject({
      chapterId: "02-linear-list",
      lessonId: "sequential-list",
      sessionId: "preview-classroom-session",
    });
    expect(wrapper.get('[data-testid="classroom-animation"]').attributes("href")).toBe("/user/animation?chapterId=02-linear-list&lessonId=sequential-list&from=classroom&sessionId=preview-classroom-session");
  });

  it("从预览会话直开算法舞台时使用 fixture 的规范章节范围", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/classroom?sessionId=preview-classroom-session");
    await flushPromises();

    expect(mockApi.listClassroomScripts).not.toHaveBeenCalled();
    expect(mockApi.getClassroomSession).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="classroom-animation"]').attributes("href")).toBe("/user/animation?chapterId=02-linear-list&lessonId=sequential-list&from=classroom&sessionId=preview-classroom-session");
  });

  it("从没有查询章节的认证会话恢复时，使用已加载脚本补齐学习范围", async () => {
    mockApi.getClassroomSession.mockResolvedValue(discussedSession);
    const wrapper = await mountView("/user/classroom?sessionId=session-1");
    await flushPromises();

    expect(wrapper.get('[data-testid="classroom-animation"]').attributes("href")).toBe("/user/animation?chapterId=stack&from=classroom&sessionId=session-1");
    expect(wrapper.get('.user-page__heading a').attributes("href")).toBe("/user/chapters?chapterId=stack&from=classroom");
    expect(wrapper.text()).toContain("学习范围当前章节");
  });

  it("无法解析课堂章节时不渲染损坏的算法舞台链接", async () => {
    mockApi.getClassroomSession.mockResolvedValue({ ...discussedSession, scriptId: "script-without-chapter" });
    const wrapper = await mountView("/user/classroom?sessionId=session-1");
    await flushPromises();

    expect(wrapper.find('[data-testid="classroom-animation"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="classroom-animation-unavailable"]').attributes("href")).toBe("/user/chapters");
    expect(wrapper.get('.user-page__heading a').attributes("href")).toBe("/user/chapters");
    expect(wrapper.text()).toContain("学习范围全部课程");
  });

  it("英文 locale 覆盖课堂壳层和本地示例教学文本", async () => {
    setLocale("en-US");
    authMock.state.user = null;
    const wrapper = await mountView("/user/classroom?chapterId=sequential-list");
    await flushPromises();

    expect(wrapper.text()).toContain("Example preview");
    expect(wrapper.text()).toContain("Sequential-list insertion · local classroom");
    expect(wrapper.get('[data-testid="classroom-start-preview-classroom-sequential-list"]').text()).toBe("Start classroom");
    expect(wrapper.text()).not.toContain("示例预览");

    await wrapper.get('[data-testid="classroom-start-preview-classroom-sequential-list"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Current topic");
    expect(wrapper.text()).toContain("When inserting 23 into 12, 18, 27, 31, 44, why do we shift from the end?");
    expect(wrapper.get('[data-testid="classroom-animation"]').text()).toBe("Open algorithm stage");
    expect(wrapper.get('[data-testid="classroom-action-CONTINUE"]').text()).toBe("Next step");
    expect(wrapper.text()).not.toContain("当前主题");
  });

  it("英文 locale 本地化课堂服务错误，而不改变权限处理", async () => {
    setLocale("en-US");
    mockApi.getClassroomSession.mockRejectedValue({ status: 403 });
    const wrapper = await mountView("/user/classroom?chapterId=stack&sessionId=session-1");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("Account access denied");
    expect(wrapper.get('[role="alert"]').text()).toContain("The service denied access to this learning resource or action.");
    expect(wrapper.text()).not.toContain("当前账号没有权限");
    expect(mockApi.startClassroom).not.toHaveBeenCalled();
  });
});
