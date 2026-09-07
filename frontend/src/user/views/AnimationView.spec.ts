import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { mockApi, authMock } = vi.hoisted(() => ({
  authMock: { state: { user: { id: "user-1", username: "learner" } as null | { id: string; username?: string; email?: string } } },
  mockApi: {
    simulateAnimation: vi.fn(),
    generateAnimation: vi.fn(),
    saveObservation: vi.fn(),
  },
}));

vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));
vi.mock("../runtime", () => ({ userApi: mockApi }));

import AnimationView from "./AnimationView.vue";
import { setLocale } from "../../shared/i18n/locale";

const mountedWrappers: Array<{ unmount: () => void }> = [];

const deterministicResponse = {
  protocol: "dsvp/1.0" as const,
  request: {
    version: "1.0" as const,
    structure: "stack" as const,
    operation: "push",
    params: { value: 8 },
    initial_state: { data: [] },
    chapterId: "stack",
  },
  trace: {},
  animationData: {
    animation: true as const,
    type: "stack" as const,
    title: "压栈",
    description: "将 8 压入栈顶",
    initial: [],
    steps: [{ op: "push", label: "压栈 8", note: "栈顶变为 8", value: 8 }],
  },
  evidencePersisted: true,
  animationRecordId: "animation-1",
  resolvedChapterId: "stack",
  matchSource: "EXPLICIT_CHAPTER" as const,
};

async function mountView(path = "/user/animation?chapterId=stack&from=chapter") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/user/animation", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
      { path: "/", component: { template: "<div />" } },
      { path: "/user/home", component: { template: "<div />" } },
      { path: "/user/coach", component: { template: "<div />" } },
      { path: "/user/classroom", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(AnimationView, {
    global: {
      plugins: [router],
      stubs: { UserFrame: { template: "<div><slot /><slot name=\"rail\" /></div>" } },
    },
    attachTo: document.body,
  });
  mountedWrappers.push(wrapper);
  return wrapper;
}

async function openCustomOperation(wrapper: Awaited<ReturnType<typeof mountView>>) {
  await flushPromises();
  const details = wrapper.get("details.algorithm-custom-operation");
  const element = details.element as HTMLDetailsElement;
  if (!element.open) await wrapper.get('[data-testid="animation-customize"]').trigger("click");
  element.open = true;
  await flushPromises();
  return details;
}

describe("AnimationView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setLocale("zh-CN");
    authMock.state.user = { id: "user-1", username: "learner" };
  });
  afterEach(() => {
    for (const wrapper of mountedWrappers.splice(0)) wrapper.unmount();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    authMock.state.user = { id: "user-1", username: "learner" };
    setLocale("zh-CN");
  });

  it("首开显示可操作的本地教学预览且不隐式调用服务端", async () => {
    const wrapper = await mountView("/user/animation?chapterId=sequential-list&from=workbench");
    await flushPromises();

    expect(wrapper.get('[data-testid="animation-fixture-badge"]').text()).toContain("示例预览");
    expect(wrapper.text()).toContain("教学预览");
    expect(wrapper.text()).toContain("顺序表的插入");
    expect(mockApi.simulateAnimation).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="animation-next"]').attributes("disabled")).toBeUndefined();

    await wrapper.get('[data-testid="animation-next"]').trigger("click");
    expect(wrapper.get(".signal-stage__scene-label").text()).toContain("01 / 05");
  });

  it("首开按当前章节或小节加载对应的本地算法场景", async () => {
    const stack = await mountView("/user/animation?chapterId=stack&from=chapter");
    await flushPromises();
    expect(stack.text()).toContain("栈的入栈与出栈");
    expect(stack.text()).not.toContain("顺序表的插入");
    expect(stack.get(".signal-stage__array").attributes("aria-label")).toBe("栈当前状态");
    expect((stack.get('[data-testid="animation-structure"]').element as HTMLSelectElement).value).toBe("stack");
    expect((stack.get('[data-testid="animation-operation"]').element as HTMLSelectElement).value).toBe("push");

    const bfs = await mountView("/user/animation?chapterId=07-graph&lessonId=bfs&from=chapter");
    await flushPromises();
    expect(bfs.text()).toContain("广度优先搜索");
    expect(bfs.get(".signal-stage__scene-label").text()).toContain("00 / 04");
    expect(bfs.find(".signal-stage__graph").exists()).toBe(true);
    expect((bfs.get('[data-testid="animation-operation"]').element as HTMLSelectElement).value).toBe("bfs");

    const dfs = await mountView("/user/animation?chapterId=07-graph&lessonId=dfs&from=chapter");
    await flushPromises();
    expect(dfs.text()).toContain("深度优先搜索");
    expect(dfs.find(".signal-stage__graph").exists()).toBe(true);
    expect((dfs.get('[data-testid="animation-operation"]').element as HTMLSelectElement).value).toBe("dfs");
    expect(mockApi.simulateAnimation).not.toHaveBeenCalled();
  });

  it("在同一算法舞台内切换学习上下文时重载对应本地场景", async () => {
    const wrapper = await mountView("/user/animation?chapterId=stack&from=chapter");
    await flushPromises();
    const router = (wrapper.vm as unknown as { $router: { push: (to: string) => Promise<void> } }).$router;

    await router.push("/user/animation?chapterId=07-graph&lessonId=bfs&from=chapter");
    await flushPromises();

    expect(wrapper.text()).toContain("广度优先搜索");
    expect(wrapper.find(".signal-stage__graph").exists()).toBe(true);
    expect(wrapper.get(".signal-stage__graph").attributes("aria-label")).toBe("图当前状态");
  });

  it("从重复 query 参数中选取首个非空学习上下文", async () => {
    const wrapper = await mountView("/user/animation?chapterId=&chapterId=07-graph&lessonId=&lessonId=bfs&from=chapter");
    await flushPromises();

    expect(wrapper.text()).toContain("广度优先搜索");
    expect(wrapper.find(".signal-stage__graph").exists()).toBe(true);
    expect(wrapper.find('[data-testid="animation-context-return"]').exists()).toBe(false);
  });

  it("为层级和散列结构使用与数据结构相符的本地可视表示", async () => {
    const tree = await mountView("/user/animation?chapterId=06-tree&lessonId=tree-traversal&from=chapter");
    await flushPromises();
    expect(tree.find(".signal-stage__tree").exists()).toBe(true);
    expect(tree.findAll(".signal-stage__tree-node")).toHaveLength(7);

    const hash = await mountView("/user/animation?chapterId=08-search&lessonId=hash-table&from=chapter");
    await flushPromises();
    expect(hash.find(".signal-stage__array--hash").exists()).toBe(true);
    expect(hash.text()).toContain("B0");
  });

  it("提交确定性 DSVP 请求并展示服务端返回的演示", async () => {
    mockApi.simulateAnimation.mockResolvedValue(deterministicResponse);
    const wrapper = await mountView();

    await openCustomOperation(wrapper);
    await wrapper.get('[data-testid="animation-structure"]').setValue("stack");
    await wrapper.get('[data-testid="animation-value"]').setValue("8");
    await wrapper.get('[data-testid="animation-operation"]').setValue("push");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(mockApi.simulateAnimation).toHaveBeenCalledWith(expect.objectContaining({
      version: "1.0",
      structure: "stack",
      operation: "push",
      params: expect.objectContaining({ value: 8 }),
      chapterId: "stack",
      source_ref: "api/v1/animations/simulate",
      context: {
        chapter_id: "stack",
        source_type: "API",
        source_ref: "api/v1/animations/simulate",
      },
    }));
    expect(wrapper.text()).toContain("压栈");
    expect(wrapper.text()).toContain("本次观察");
  });

  it.each([
    ["chapter", "/user/chapters/stack"],
    ["coach", "/user/coach?chapterId=stack"],
    ["home", "/"],
    ["workbench", "/user/home"],
  ])("从 %s 学习来源进入时返回对应学习位置", async (from, expectedHref) => {
    const wrapper = await mountView(`/user/animation?chapterId=stack&from=${from}`);
    await openCustomOperation(wrapper);

    expect(wrapper.get('[data-testid="animation-return"]').attributes("href")).toBe(expectedHref);
    expect(wrapper.get('[data-testid="animation-run"]').attributes("disabled")).toBeUndefined();
  });

  it("从课堂真实会话进入时保留会话并提交课堂 DSVP 上下文", async () => {
    mockApi.simulateAnimation.mockResolvedValue(deterministicResponse);
    const sessionId = "classroom-session-1";
    const wrapper = await mountView(`/user/animation?chapterId=stack&from=classroom&sessionId=${sessionId}`);
    await openCustomOperation(wrapper);

    expect(wrapper.get('[data-testid="animation-return"]').attributes("href")).toBe(`/user/classroom?chapterId=stack&sessionId=${sessionId}`);
    expect(wrapper.text()).toContain("返回课堂");

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(mockApi.simulateAnimation).toHaveBeenCalledWith(expect.objectContaining({
      chapterId: "stack",
      classroomSessionId: sessionId,
      source_ref: sessionId,
      context: {
        chapter_id: "stack",
        classroom_session_id: sessionId,
        source_type: "CLASSROOM",
        source_ref: sessionId,
      },
    }));
  });

  it.each([
    ["缺少会话", "/user/animation?chapterId=stack&from=classroom"],
    ["会话超过 160 字符", `/user/animation?chapterId=stack&from=classroom&sessionId=${"a".repeat(161)}`],
  ])("课堂来源%s时阻止模拟并返回课堂", async (_label, path) => {
    const wrapper = await mountView(path);

    expect(wrapper.text()).toContain("课堂来源缺少有效会话");
    expect(wrapper.get('[data-testid="animation-context-return"]').attributes("href")).toBe("/user/classroom?chapterId=stack");
    expect(wrapper.text()).toContain("返回课堂");
    expect(wrapper.find('[data-testid="animation-run"]').exists()).toBe(false);
    expect(mockApi.simulateAnimation).not.toHaveBeenCalled();
  });

  it("问答来源保留章节 API 上下文且不伪造课堂会话", async () => {
    mockApi.simulateAnimation.mockResolvedValue(deterministicResponse);
    const wrapper = await mountView("/user/animation?chapterId=stack&from=coach");

    await openCustomOperation(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    const request = mockApi.simulateAnimation.mock.calls[0][0];
    expect(request).toMatchObject({
      chapterId: "stack",
      source_ref: "api/v1/animations/simulate",
      context: {
        chapter_id: "stack",
        source_type: "API",
        source_ref: "api/v1/animations/simulate",
      },
    });
    expect(request).not.toHaveProperty("classroomSessionId");
  });

  it.each([
    ["缺少学习来源", "/user/animation?chapterId=stack", "/user/chapters/stack"],
    ["缺少章节范围", "/user/animation?from=chapter", "/user/chapters"],
    ["学习来源无效", "/user/animation?chapterId=stack&from=search", "/user/chapters/stack"],
  ])("%s 时阻止模拟并提供中文返回入口", async (_label, path, expectedHref) => {
    const wrapper = await mountView(path);

    expect(wrapper.text()).toContain("学习上下文不可用");
    expect(wrapper.text()).toContain("算法舞台不能单独打开");
    expect(wrapper.get('[data-testid="animation-context-return"]').attributes("href")).toBe(expectedHref);
    expect(wrapper.find('[data-testid="animation-run"]').exists()).toBe(false);
    expect(mockApi.simulateAnimation).not.toHaveBeenCalled();
  });

  it("为已持久化动画记录观察内容", async () => {
    mockApi.simulateAnimation.mockResolvedValue(deterministicResponse);
    mockApi.saveObservation.mockResolvedValue({ recordId: "animation-1", observation: "后进先出" });
    const wrapper = await mountView();

    await openCustomOperation(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    await wrapper.get('[data-testid="animation-observation"]').setValue("后进先出");
    await wrapper.get('[data-testid="animation-save-observation"]').trigger("click");
    await flushPromises();

    expect(mockApi.saveObservation).toHaveBeenCalledWith("animation-1", { observation: "后进先出" });
    expect(wrapper.text()).toContain("观察已保存");
  });

  it("遵循减少动态偏好，仅提供手动单步控制", async () => {
    vi.stubGlobal("matchMedia", vi.fn(() => ({ matches: true })));
    const wrapper = await mountView();

    await flushPromises();

    expect(wrapper.text()).toContain("手动步进");
    expect(wrapper.find('[data-testid="animation-play"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="animation-next"]').attributes("disabled")).toBeUndefined();
  });

  it("服务端不可用时保留可操作舞台并明确标记本地回落", async () => {
    mockApi.simulateAnimation.mockRejectedValue(Object.assign(new Error("unavailable"), { status: 503 }));
    const wrapper = await mountView();

    await openCustomOperation(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.get('[data-testid="animation-fixture-badge"]').text()).toContain("示例预览");
    expect(wrapper.text()).toContain("实时演示暂时不可用");
    expect(wrapper.text()).toContain("不会写入个人学习记录");
  });

  it("显式模拟需要登录时显示原位权限提示，不跳转登录页", async () => {
    mockApi.simulateAnimation.mockRejectedValue(Object.assign(new Error("login required"), { status: 401 }));
    const wrapper = await mountView();

    await openCustomOperation(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("登录后解锁此功能");
    expect(wrapper.find("a[href^=\"/login\"]").exists()).toBe(false);
  });

  it("游客提交自定义模拟前先显示权限提示，不发送无意义请求", async () => {
    authMock.state.user = null;
    const wrapper = await mountView();

    await openCustomOperation(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(mockApi.simulateAnimation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("登录后解锁此功能");
    expect(wrapper.find("a[href^=\"/login\"]").exists()).toBe(false);
  });

  it("实时轨迹没有记录编号时明确说明观察无法持久化", async () => {
    mockApi.simulateAnimation.mockResolvedValue({ ...deterministicResponse, animationRecordId: null });
    const wrapper = await mountView();

    await openCustomOperation(wrapper);
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("暂时没有可保存的学习记录");
    await wrapper.get('[data-testid="animation-observation"]').setValue("需要解释尾部移动");
    expect(wrapper.get('[data-testid="animation-save-observation"]').attributes("disabled")).toBeDefined();
  });

  it("Escape 关闭自定义操作并恢复焦点，同时卸载时移除键盘监听", async () => {
    const removeEventListener = vi.spyOn(window, "removeEventListener");
    const wrapper = await mountView();
    const details = await openCustomOperation(wrapper);
    const summary = wrapper.get('[data-testid="animation-customize"]').element as HTMLElement;
    summary.focus();

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));

    expect((details.element as HTMLDetailsElement).open).toBe(false);
    expect(document.activeElement).toBe(summary);

    wrapper.unmount();
    expect(removeEventListener).toHaveBeenCalledWith("keydown", expect.any(Function));
  });

  it("切换 English 时翻译本地教学舞台与原位权限提示", async () => {
    setLocale("en-US");
    authMock.state.user = null;
    const wrapper = await mountView("/user/animation?chapterId=sequential-list&from=workbench");
    await flushPromises();

    expect(wrapper.text()).toContain("Insertion in a sequential list");
    expect(wrapper.text()).toContain("Locate the insertion point");
    expect(wrapper.get('[data-testid="animation-next"]').attributes("aria-label")).toBe("Next step");

    await openCustomOperation(wrapper);
    expect(wrapper.get('[data-testid="animation-structure"]').text()).toContain("Sequential list");
    expect(wrapper.get('[data-testid="animation-operation"]').text()).toContain("Insert");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("Sign in to unlock this feature");
    expect(wrapper.text()).not.toContain("登录后解锁此功能");
  });
});
