import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setLocale } from "../../shared/i18n/locale";
import { searchKnowledgePreview } from "../fixtures/knowledge-preview";

const { authMock, mockApi } = vi.hoisted(() => ({
  authMock: { state: { user: { id: 1, email: "learner@example.com", roles: ["STUDENT"] } as null | { id: number; email: string; roles: string[] } } },
  mockApi: { listChapters: vi.fn(), searchKnowledge: vi.fn() },
}));
vi.mock("../runtime", () => ({ userApi: mockApi }));
vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));
import KnowledgeView from "./KnowledgeView.vue";

async function mountView(path = "/user/knowledge?chapterId=stack") {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: "/user/knowledge", component: { template: "<div />" } },
    { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
  ] });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(KnowledgeView, { global: { plugins: [router], stubs: { UserFrame: { template: "<div><slot /><slot name=\"rail\" /></div>" } } } });
  return Object.assign(wrapper, { testRouter: router });
}

describe("KnowledgeView", () => {
  beforeEach(() => { setLocale("zh-CN"); vi.clearAllMocks(); authMock.state.user = { id: 1, email: "learner@example.com", roles: ["STUDENT"] }; mockApi.listChapters.mockResolvedValue([{ id: "stack", chapterNumber: 1, title: "栈", summary: "" }]); mockApi.searchKnowledge.mockResolvedValue({ ok: true, query: "栈", results: [] }); });

  it("在客户端阻止超过契约长度的检索，而不请求接口", async () => {
    const wrapper = await mountView();
    await flushPromises();
    await wrapper.get("input").setValue("x".repeat(501));
    await wrapper.get("form").trigger("submit");
    expect(mockApi.searchKnowledge).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("不能超过 500 个字符");
  });

  it("服务端空结果时直接展示独立的本地示例，并保留真实资料边界", async () => {
    const wrapper = await mountView("/user/knowledge");
    await flushPromises();
    await wrapper.get("input").setValue("二叉树");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(mockApi.searchKnowledge).toHaveBeenCalledWith({ query: "二叉树", chapterId: undefined, limit: 4 });
    expect(wrapper.text()).toContain("没有找到可见结果");
    expect(wrapper.text()).toContain("课程资料还没有完成章节绑定");
    expect(wrapper.text()).toContain("二叉树结构");
    expect(wrapper.text()).toContain("以下为独立的本地示例");
    expect(wrapper.text()).toContain("不代表账号权限");
  });

  it("登录态可以主动查看链表示例，但会保留示例边界提示", async () => {
    mockApi.searchKnowledge.mockResolvedValue({ ok: true, query: "链表", results: [] });
    const wrapper = await mountView("/user/knowledge?chapterId=02-linear-list");
    await flushPromises();
    await wrapper.get("input").setValue("链表");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("没有找到可见结果");
    const previewButton = wrapper.findAll("button").find((node) => node.text() === "查看本地示例");
    expect(previewButton).toBeUndefined();
    expect(wrapper.text()).toContain("单链表");
    expect(wrapper.text()).toContain("以下为独立的本地示例");
    expect(wrapper.text()).toContain("不代表账号权限");
    expect(wrapper.text()).toContain("2 条示例内容");
    expect(wrapper.text()).toContain("返回账号资料结果");

    const resetButton = wrapper.findAll("button").find((node) => node.text() === "返回账号资料结果");
    expect(resetButton).toBeDefined();
    await resetButton!.trigger("click");
    expect(wrapper.text()).toContain("没有找到可见结果");
  });

  it.each(["树", "二叉树", "链表", "顺序栈"]) ("登录态搜索 %s 时 API 为空也能看到明确标注的示例", async (term) => {
    const wrapper = await mountView("/user/knowledge");
    await flushPromises();
    await wrapper.get("input").setValue(term);
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(mockApi.searchKnowledge).toHaveBeenCalledWith({ query: term, chapterId: undefined, limit: 4 });
    expect(wrapper.text()).toContain("以下为独立的本地示例");
    expect(wrapper.text()).toContain("示例内容");
    expect(wrapper.text()).toContain("不代表账号权限");
  });

  it("游客可以从示例主题入口检索跨章节知识", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/knowledge");
    await flushPromises();

    expect(wrapper.text()).toContain("先试试这些示例主题");
    const suggestion = wrapper.get('button[aria-label="检索示例：顺序栈"]');
    await suggestion.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("顺序栈的入栈条件");
    expect(wrapper.text()).toContain("示例内容");
    expect(wrapper.text()).toContain("1 条示例内容");
    expect(wrapper.text()).not.toContain("已审核来源");
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("顺序栈");
    expect(wrapper.testRouter.currentRoute.value.fullPath).toContain("chapterId=03-stack-queue");
  });

  it("从带 lesson 上下文的知识页选择章节示例时清除旧 lesson 过滤", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/knowledge?chapterId=02-linear-list&lessonId=linked-list");
    await flushPromises();

    await wrapper.get('button[aria-label="检索示例：顺序栈"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("顺序栈的入栈条件");
    expect(wrapper.testRouter.currentRoute.value.fullPath).not.toContain("lessonId=linked-list");
  });

  it("游客在章节检索中不应被旧 lesson 上下文挡住链表知识", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/knowledge?chapterId=02-linear-list&lessonId=sequential-list&q=%E9%93%BE%E8%A1%A8");
    await flushPromises();

    expect(wrapper.text()).toContain("单链表");
    expect(wrapper.text()).not.toContain("没有找到可见结果");
  });

  it("当前章节没有结果时会明确扩展到全课程范围继续检索", async () => {
    authMock.state.user = null;
    mockApi.listChapters.mockResolvedValue([
      { id: "02-linear-list", chapterNumber: 2, title: "线性表", summary: "" },
      { id: "06-tree", chapterNumber: 6, title: "树与二叉树", summary: "" },
    ]);
    const wrapper = await mountView("/user/knowledge?chapterId=02-linear-list&q=%E4%BA%8C%E5%8F%89%E6%A0%91");
    await flushPromises();

    expect(wrapper.text()).toContain("已扩展到全部章节检索");
    expect(wrapper.text()).toContain("二叉树结构");
    expect((wrapper.get("select").element as HTMLSelectElement).value).toBe("");

    const restoreButton = wrapper.findAll("button").find((node) => node.text() === "恢复原章节范围");
    expect(restoreButton).toBeDefined();
    await restoreButton!.trigger("click");
    await flushPromises();

    expect((wrapper.get("select").element as HTMLSelectElement).value).toBe("02-linear-list");
  });

  it.each(["树", "二叉树", "链表", "顺序栈"]) ("本地预览索引可以检索 %s", (term) => {
    const results = searchKnowledgePreview(term);
    expect(results.length, `${term} should match preview knowledge`).toBeGreaterThan(0);
  });

  it.each(["树", "二叉树", "链表"]) ("游客知识页可以直接检索 %s", async (term) => {
    authMock.state.user = null;
    const wrapper = await mountView(`/user/knowledge?q=${encodeURIComponent(term)}`);
    await flushPromises();

    expect(wrapper.text()).toContain(term === "树" ? "树" : term);
    expect(wrapper.text()).not.toContain("没有找到可见结果");
  });

  it("章节目录加载失败时显示错误并支持重试", async () => {
    mockApi.listChapters
      .mockRejectedValueOnce({ status: 503, message: "chapters unavailable" })
      .mockResolvedValueOnce([{ id: "stack", chapterNumber: 1, title: "栈", summary: "" }]);
    const wrapper = await mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("学习服务暂不可用");
    expect(wrapper.text()).toContain("重新加载章节");

    await wrapper.get('[role="alert"] button').trigger("click");
    await flushPromises();

    expect(mockApi.listChapters).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get("select").attributes("disabled")).toBeUndefined();
  });

  it("英文 locale 会将章节加载错误映射为学习者可理解的恢复状态", async () => {
    setLocale("en-US");
    mockApi.listChapters.mockRejectedValueOnce({ status: 503, message: "chapters unavailable" });
    const wrapper = await mountView();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("Learning services are temporarily unavailable");
    expect(wrapper.text()).toContain("Reload chapters");
  });

  it("英文 locale 会翻译检索 chrome、fixture 状态和表单校验，同时保留课程内容", async () => {
    setLocale("en-US");
    authMock.state.user = null;
    const wrapper = await mountView("/user/knowledge");
    await flushPromises();

    expect(wrapper.text()).toContain("Knowledge search");
    expect(wrapper.text()).toContain("Example preview");
    expect(wrapper.text()).toContain("Search for a concept");

    await wrapper.get("input").setValue("顺序表");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(wrapper.text()).toContain("Example content");
    expect(wrapper.text()).toContain("Open chapter");
    expect(wrapper.text()).not.toContain("本地 fixture");

    await wrapper.get("input").setValue("");
    await wrapper.get("form").trigger("submit");
    expect(wrapper.text()).toContain("Enter a concept to search for.");
  });
});
