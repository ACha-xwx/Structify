import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setLocale } from "../../shared/i18n/locale";

const { authMock, mockApi } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: number; email: string; roles: string[] } } },
  mockApi: {
    runCode: vi.fn(),
    analyzeCode: vi.fn(),
    getReadiness: vi.fn(),
  },
}));

vi.mock("../runtime", () => ({ userApi: mockApi }));
vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));

import CodeView from "./CodeView.vue";

const runResponse = {
  language: "c" as const,
  status: "success" as const,
  stdout: "hello, data structure!\n",
  stderr: "",
  durationMs: 12,
  runId: "run-1",
};

const analysisReady = {
  operation: "CODE_ANALYSIS" as const,
  evidenceRequired: false,
  modelAvailable: true,
  modelReason: "PERSISTED_CONFIGURATION_READY" as const,
  evidenceAvailable: false,
  evidenceReason: "CONTEXT_EVIDENCE_UNAVAILABLE" as const,
  currentContext: { chapterId: "stack", queryScoped: true },
  availableResourceCount: 0,
  availableKnowledgeChunkCount: 0,
  availableSourceCount: 0,
  excludedOrUnverifiedCount: 0,
  remainingDailyTokenQuota: 1000,
  quotaStatus: "AVAILABLE" as const,
  allowFormalGeneration: true,
  blockingReasons: [],
};

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/user/code", component: { template: "<div />" } },
      { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/user/code?chapterId=stack");
  await router.isReady();
  return mount(CodeView, {
    global: {
      plugins: [router],
      stubs: {
        UserFrame: { template: "<div><slot /><aside><slot name=\"rail\" /></aside></div>" },
      },
    },
  });
}

describe("CodeView", () => {
  afterEach(() => setLocale("zh-CN"));

  beforeEach(() => {
    vi.clearAllMocks();
    setLocale("zh-CN");
    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    mockApi.runCode.mockResolvedValue(runResponse);
    mockApi.getReadiness.mockResolvedValue(analysisReady);
    mockApi.analyzeCode.mockResolvedValue({ analysis: "代码可以正常输出。" });
  });

  it("先显示空状态，并在沙箱执行期间显示加载状态", async () => {
    let resolveRun!: (value: typeof runResponse) => void;
    mockApi.runCode.mockImplementationOnce(() => new Promise((resolve) => { resolveRun = resolve; }));
    const wrapper = await mountView();

    expect(wrapper.get('[data-testid="code-result-state"]').classes()).toContain("user-state--empty");
    await wrapper.get("form").trigger("submit");
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="code-result-state"]').classes()).toContain("user-state--loading");

    resolveRun(runResponse);
    await flushPromises();
    expect(wrapper.get('[data-testid="code-run-result"]').text()).toContain("hello, data structure!");
  });

  it("使用已持久化运行编号进行分析，并先检查 Spring v1 分析就绪状态", async () => {
    const wrapper = await mountView();
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    await wrapper.get('[data-testid="code-analyze"]').trigger("click");
    await flushPromises();

    expect(mockApi.getReadiness).toHaveBeenCalledWith({ operation: "CODE_ANALYSIS", chapterId: "stack" });
    expect(mockApi.analyzeCode).toHaveBeenCalledWith({ runId: "run-1" });
    expect(wrapper.get('[data-testid="code-analysis"]').text()).toContain("代码可以正常输出。");
  });

  it("沙箱失败时显示可重试错误，并保留同一份输入重试", async () => {
    mockApi.runCode.mockRejectedValueOnce({ status: 503, message: "sandbox unavailable" });
    const wrapper = await mountView();

    await wrapper.get("form").trigger("submit");
    await flushPromises();
    const state = wrapper.get('[data-testid="code-result-state"]');
    expect(state.classes()).toContain("user-state--error");
    await state.get("button").trigger("click");
    await flushPromises();

    expect(mockApi.runCode).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-testid="code-run-result"]').text()).toContain("hello, data structure!");
  });

  it("代码执行器未配置时显示明确原因，并不提供无效的重试按钮", async () => {
    mockApi.runCode.mockRejectedValueOnce({
      status: 503,
      code: "COMPILER_NOT_CONFIGURED",
      message: "代码执行服务尚未配置",
    });
    const wrapper = await mountView();

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    const state = wrapper.get('[data-testid="code-result-state"]');
    expect(state.text()).toContain("代码执行暂不可用");
    expect(state.text()).toContain("代码执行服务尚未连接");
    expect(state.text()).not.toContain("Piston");
    expect(state.text()).not.toContain("Judge0");
    expect(state.find("button").exists()).toBe(false);
  });

  it("英文模式保留代码执行器未配置的具体原因", async () => {
    setLocale("en-US");
    mockApi.runCode.mockRejectedValueOnce({ status: 503, code: "COMPILER_NOT_CONFIGURED", message: "not configured" });
    const wrapper = await mountView();

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    const state = wrapper.get('[data-testid="code-result-state"]');
    expect(state.text()).toContain("Code execution is temporarily unavailable");
    expect(state.text()).toContain("execution service is not connected");
    expect(state.text()).not.toContain("Piston");
    expect(state.text()).not.toContain("Judge0");
    expect(state.text()).not.toContain("Code services are temporarily unavailable");
  });

  it("切换语言后同步更新已显示的执行器未配置提示", async () => {
    mockApi.runCode.mockRejectedValueOnce({ status: 503, code: "COMPILER_NOT_CONFIGURED", message: "not configured" });
    const wrapper = await mountView();

    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(wrapper.get('[data-testid="code-result-state"]').text()).toContain("代码执行暂不可用");

    setLocale("en-US");
    await wrapper.vm.$nextTick();
    const state = wrapper.get('[data-testid="code-result-state"]');
    expect(state.text()).toContain("Code execution is temporarily unavailable");
    expect(state.text()).toContain("execution service is not connected");
  });

  it("代码运行被错误地返回权限或服务错误时，不把游客引导到登录页", async () => {
    authMock.state.user = null;
    mockApi.runCode.mockRejectedValueOnce({ status: 401, message: "login required" });
    const wrapper = await mountView();

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    const state = wrapper.get('[data-testid="code-result-state"]');
    expect(state.classes()).toContain("user-state--error");
    expect(state.text()).toContain("代码执行服务拒绝了本次游客请求");
    expect(state.find("a").exists()).toBe(false);
  });

  it("分析就绪检查要求登录时呈现权限状态，且不提交分析请求", async () => {
    mockApi.getReadiness.mockRejectedValueOnce({ status: 401, message: "login required" });
    const wrapper = await mountView();
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    await wrapper.get('[data-testid="code-analyze"]').trigger("click");
    await flushPromises();

    expect(wrapper.get('[data-testid="code-analysis-state"]').classes()).toContain("user-state--permission");
    expect(mockApi.analyzeCode).not.toHaveBeenCalled();
  });

  it("模型或配额未就绪时显示服务限制，并避免提交分析", async () => {
    mockApi.getReadiness.mockResolvedValueOnce({
      ...analysisReady,
      modelAvailable: false,
      quotaStatus: "NOT_CONFIGURED" as const,
      allowFormalGeneration: false,
      blockingReasons: ["PERSISTED_CONFIGURATION_DISABLED"],
    });
    const wrapper = await mountView();
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    await wrapper.get('[data-testid="code-analyze"]').trigger("click");
    await flushPromises();

    const state = wrapper.get('[data-testid="code-analysis-state"]');
    expect(state.classes()).toContain("user-state--error");
    expect(state.text()).toContain("代码分析模型当前不可用");
    expect(mockApi.analyzeCode).not.toHaveBeenCalled();
  });

  it("游客可以直接运行代码，只有智能分析在原位提示登录", async () => {
    authMock.state.user = null;
    const wrapper = await mountView();
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(mockApi.runCode).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-testid="code-run-result"]').text()).toContain("hello, data structure!");
    expect(wrapper.get('[data-testid="code-run-result"]').text()).not.toContain("run-1");

    const analyze = wrapper.get('[data-testid="code-analyze"]');
    expect(analyze.attributes("disabled")).toBeUndefined();
    await analyze.trigger("click");
    await flushPromises();

    expect(mockApi.getReadiness).not.toHaveBeenCalled();
    expect(mockApi.analyzeCode).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="code-analysis-state"]').text()).toContain("登录后解锁代码分析");
  });

  it("英文 locale 覆盖代码实验的控制、状态和服务错误，同时保留服务端输出", async () => {
    setLocale("en-US");
    mockApi.runCode.mockRejectedValueOnce({ status: 503, message: "sandbox unavailable" });
    const wrapper = await mountView();

    expect(wrapper.text()).toContain("Code lab");
    expect(wrapper.get("button[type=submit]").text()).toBe("Run code");
    expect(wrapper.get('[data-testid="code-result-state"]').text()).toContain("No code has run yet");
    expect(wrapper.text()).not.toContain("代码实验");

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    const state = wrapper.get('[data-testid="code-result-state"]');
    expect(state.text()).toContain("Code services are temporarily unavailable");
    expect(state.text()).not.toContain("学习服务暂不可用");
  });

  it("按编译器习惯切换输入、输出和诊断面板，并保留标准输入请求", async () => {
    const wrapper = await mountView();
    const tabs = wrapper.findAll('[role="tab"]');
    expect(tabs.map((tab) => tab.text().trim())).toEqual(["输入", "输出", "诊断", "智能分析"]);

    await tabs[0].trigger("click");
    await wrapper.get(".user-code-stdin").setValue("3 5");
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toContain("输入");

    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(mockApi.runCode).toHaveBeenCalledWith(expect.objectContaining({ stdin: "3 5", chapterId: "stack" }));
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toContain("输出");

    await wrapper.findAll('[role="tab"]')[2].trigger("click");
    expect(wrapper.get('[data-testid="code-diagnostics"]').text()).toContain("本次运行没有诊断信息");
  });

  it("把文件上下文收敛到编辑器顶部一行，输入只保留在控制台面板", async () => {
    const wrapper = await mountView();
    const workspaceBar = wrapper.get(".code-workbench__workspace-bar");
    const surface = wrapper.get(".code-workbench__surface");

    expect(surface.find(".code-workbench__sidebar").exists()).toBe(false);
    expect(workspaceBar.find(".code-workbench__file strong").text()).toBe("main.c");
    expect(workspaceBar.find("select").exists()).toBe(true);
    expect(workspaceBar.find(".code-workbench__state").text()).toContain("就绪");
    expect(workspaceBar.element.compareDocumentPosition(surface.find(".code-workbench__editor-shell").element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(surface.find(".code-workbench__main > .code-workbench__workspace-bar").exists()).toBe(true);
    await wrapper.get('[role="tab"]').trigger("click");
    expect(surface.findAll(".user-code-stdin")).toHaveLength(1);
  });

  it("支持 Ctrl+Enter 运行并可重置编辑器状态", async () => {
    const wrapper = await mountView();
    const editor = wrapper.get(".user-code-editor");
    await editor.setValue("int main(void) { return 0; }");
    expect(wrapper.get(".code-workbench__state").text()).toContain("本地改动");

    await editor.trigger("keydown", { key: "Enter", ctrlKey: true });
    await flushPromises();
    expect(mockApi.runCode).toHaveBeenCalledTimes(1);

    await wrapper.get(".code-icon-action").trigger("click");
    expect((wrapper.get(".user-code-editor").element as HTMLTextAreaElement).value).toContain("#include <stdio.h>");
    expect(wrapper.get(".code-workbench__state").text()).toContain("就绪");
  });
});
