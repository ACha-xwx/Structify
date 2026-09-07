import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import AdminSandboxConfigView from "./AdminSandboxConfigView.vue";
import { ApiClientError } from "../../shared/api";

const getSandboxConfig = vi.hoisted(() => vi.fn());
const updateSandboxConfig = vi.hoisted(() => vi.fn());
const testSandboxConnection = vi.hoisted(() => vi.fn());

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return {
    ...actual,
    adminApi: { getSandboxConfig, updateSandboxConfig, testSandboxConnection },
    adminErrorMessage: () => "管理操作未完成（NETWORK_ERROR）。",
  };
});

const storedConfig = {
  provider: "PISTON" as const,
  baseUrl: "https://sandbox.example",
  enabled: true,
  updatedAt: "2026-08-29T00:00:00Z",
};

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/admin/sandbox", component: AdminSandboxConfigView },
      { path: "/admin", component: { template: "<p>总览</p>" } },
    ],
  });
  await router.push("/admin/sandbox");
  await router.isReady();
  const wrapper = mount(AdminSandboxConfigView, { global: { plugins: [router] } });
  await flushPromises();
  return { router, wrapper };
}

function health(configured = false) {
  return {
    ok: true,
    codeExecutionConfigured: configured,
    timestamp: "2026-08-29T00:01:00Z",
  };
}

describe("AdminSandboxConfigView", () => {
  beforeEach(() => {
    getSandboxConfig.mockReset();
    updateSandboxConfig.mockReset();
    testSandboxConnection.mockReset();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(health(false)), { status: 200, headers: { "content-type": "application/json" } })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("keeps an honest disabled state when the backend has no sandbox config API", async () => {
    getSandboxConfig.mockRejectedValue(new ApiClientError({ status: 404, code: "HTTP_404", requestId: "req-404", message: "not found", details: [] }));
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain("接口待接入");
    expect(wrapper.text()).toContain("不会假装已经保存或启用");
    const input = wrapper.get("input[type='url']");
    await input.setValue("https://sandbox.example");
    const save = wrapper.get("button[type='submit']");
    expect(save.attributes("disabled")).toBeDefined();
    expect(updateSandboxConfig).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("disables server save when the deployment master key is unavailable", async () => {
    getSandboxConfig.mockResolvedValue({
      available: false,
      reason: "MASTER_KEY_UNAVAILABLE",
      configuration: null,
      runtime: null,
    });
    const { wrapper } = await mountView();

    const input = wrapper.get("input[type='url']");
    await input.setValue("https://sandbox.example");
    const save = wrapper.find("button[type='submit']");

    expect(wrapper.text()).toContain("部署环境缺少配置加密根密钥");
    expect(save.attributes("disabled")).toBeDefined();
    expect(save.text()).toContain("服务端接口未就绪");
    expect(updateSandboxConfig).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("labels a persisted but disabled configuration as stopped", async () => {
    const disabledConfig = { ...storedConfig, enabled: false };
    getSandboxConfig.mockResolvedValue({
      available: false,
      reason: "PERSISTED_CONFIGURATION_DISABLED",
      configuration: disabledConfig,
      runtime: null,
    });
    const { wrapper } = await mountView();

    expect(wrapper.text()).toContain("已停用");
    expect(wrapper.text()).toContain("已保存的执行器配置当前处于停用状态");
    const submit = wrapper.get("button[type='submit']");
    expect(submit.text()).toContain("保存到服务端");
    expect(submit.attributes("disabled")).toBeUndefined();
    wrapper.unmount();
  });

  it("saves a server-backed configuration and tests only the saved value", async () => {
    getSandboxConfig.mockResolvedValue({ available: true, reason: null, configuration: storedConfig, runtime: null });
    updateSandboxConfig.mockResolvedValue({ ...storedConfig, baseUrl: "https://sandbox-next.example" });
    testSandboxConnection.mockResolvedValue({ connected: true, code: "CONNECTION_OK" });
    const { wrapper } = await mountView();

    await wrapper.get("input[type='url']").setValue("https://sandbox-next.example/");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(updateSandboxConfig).toHaveBeenCalledWith({ provider: "PISTON", baseUrl: "https://sandbox-next.example", enabled: true });
    expect(wrapper.text()).toContain("沙箱配置已由服务端保存");
    const test = wrapper.findAll("button").find((button) => button.text().includes("测试已保存连接"));
    expect(test).toBeTruthy();
    await test!.trigger("click");
    await flushPromises();
    expect(testSandboxConnection).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("CONNECTION_OK");
    wrapper.unmount();
  });

  it("rejects credentials and query strings in the browser before saving", async () => {
    getSandboxConfig.mockResolvedValue({ available: false, reason: "NOT_CONFIGURED", configuration: null, runtime: null });
    const { wrapper } = await mountView();
    await wrapper.get("input[type='url']").setValue("https://user:pass@sandbox.example?token=secret");
    const draft = wrapper.findAll("button").find((button) => button.text().includes("仅保留当前草稿"));
    await draft!.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("服务地址不能包含账号、密码、查询参数或片段");
    expect(updateSandboxConfig).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
