import { flushPromises, mount } from "@vue/test-utils";
import { reactive } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setLocale } from "../../shared/i18n/locale";

const { authMock, mockApi } = vi.hoisted(() => ({
  authMock: { state: { user: null as null | { id: number; email: string; roles: string[] } } },
  mockApi: { getResource: vi.fn(), getResourceContent: vi.fn(), recordLearningEvent: vi.fn() },
}));
vi.mock("../runtime", () => ({ userApi: mockApi }));
vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));
import ResourceView from "./ResourceView.vue";

async function mountView(path = "/user/resources/resource-1") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/user/resources/:resourceId", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/user/chapters/:chapterId", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  return mount(ResourceView, { global: { plugins: [router], stubs: { UserFrame: { template: "<div><slot /><slot name=\"rail\" /></div>" } } } });
}

describe("ResourceView", () => {
  beforeEach(() => {
    setLocale("zh-CN");
    vi.clearAllMocks();
    authMock.state = reactive({ user: { id: 7, email: "learner@example.com", roles: ["STUDENT"] } });
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:resource-preview"),
      revokeObjectURL: vi.fn(),
    });
    mockApi.getResource.mockResolvedValue({ id: "resource-1", chapterId: "stack", type: "PDF", title: "栈讲义", description: "", sourceName: "课程组", versionLabel: "v1", reviewStatus: "PUBLISHED", licenseScope: "PUBLIC", contentUrl: null });
    mockApi.getResourceContent.mockResolvedValue({ bytes: new ArrayBuffer(4), disposition: "inline; filename=stack.pdf", contentType: "application/pdf" });
    mockApi.recordLearningEvent.mockResolvedValue({ id: 1 });
  });

  afterEach(() => {
    setLocale("zh-CN");
    vi.unstubAllGlobals();
  });

  it("同时读取元数据和二进制内容，且不显示服务器路径", async () => {
    const wrapper = await mountView();
    await flushPromises();
    expect(mockApi.getResource).toHaveBeenCalledWith("resource-1");
    expect(mockApi.getResourceContent).toHaveBeenCalledWith("resource-1");
    expect(wrapper.text()).toContain("栈讲义");
    expect(wrapper.text()).not.toContain("course-content");
  });

  it("将 404 显示为资源不可访问状态", async () => {
    mockApi.getResource.mockRejectedValueOnce({ status: 404, message: "not found" });
    const wrapper = await mountView();
    await flushPromises();
    expect(wrapper.text()).toContain("资源不可访问");
  });

  it("英文 locale 会将资源访问失败映射为可恢复的状态", async () => {
    setLocale("en-US");
    mockApi.getResource.mockRejectedValueOnce({ status: 404, message: "not found" });
    const wrapper = await mountView();
    await flushPromises();

    expect(wrapper.text()).toContain("Resource is unavailable");
    expect(wrapper.text()).not.toContain("资源不可访问");
  });

  it("游客打开本地资源预览时不请求真实 API 或写入个人学习事件", async () => {
    authMock.state.user = null;
    const wrapper = await mountView("/user/resources/preview-sequential-list-handout");
    await flushPromises();
    expect(wrapper.text()).toContain("顺序表插入步骤图");
    expect(wrapper.text()).toContain("示例预览");
    expect(mockApi.getResource).not.toHaveBeenCalled();
    expect(mockApi.getResourceContent).not.toHaveBeenCalled();
    expect(mockApi.recordLearningEvent).not.toHaveBeenCalled();
    expect(wrapper.find(".user-resource-image").exists()).toBe(true);
  });

  it("同一资源地址会随登录状态在本地预览与真实接口之间切换", async () => {
    authMock.state = reactive({ user: null });
    mockApi.getResource.mockResolvedValue({ id: "preview-sequential-list-handout", chapterId: "sequential-list", type: "PDF", title: "线上顺序表讲义", description: "", sourceName: "课程组", versionLabel: "v1", reviewStatus: "PUBLISHED", licenseScope: "PUBLIC", contentUrl: null });
    const wrapper = await mountView("/user/resources/preview-sequential-list-handout");
    await flushPromises();

    expect(wrapper.text()).toContain("示例预览");
    expect(mockApi.getResource).not.toHaveBeenCalled();

    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    await flushPromises();

    expect(mockApi.getResource).toHaveBeenCalledWith("preview-sequential-list-handout");
    expect(mockApi.getResourceContent).toHaveBeenCalledWith("preview-sequential-list-handout");
    expect(wrapper.text()).toContain("线上顺序表讲义");
    expect(wrapper.text()).not.toContain("示例预览");

    authMock.state.user = null;
    await flushPromises();

    expect(wrapper.text()).toContain("示例预览");
    expect(wrapper.text()).toContain("顺序表插入步骤图");
    expect(mockApi.getResource).toHaveBeenCalledTimes(1);
  });

  it("英文 locale 会翻译资源 chrome 和 fixture 枚举，不显示内部 fixture 标识", async () => {
    setLocale("en-US");
    authMock.state.user = null;
    const wrapper = await mountView("/user/resources/preview-sequential-list-handout");
    await flushPromises();

    expect(wrapper.text()).toContain("Course materials");
    expect(wrapper.text()).toContain("Example preview");
    expect(wrapper.text()).toContain("Example handout image");
    expect(wrapper.text()).toContain("Example content");
    expect(wrapper.text()).toContain("Public material");
    expect(wrapper.text()).not.toContain("LOCAL FIXTURE");
  });
});
