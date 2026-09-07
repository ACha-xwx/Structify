import { describe, expect, it, vi } from "vitest";

const post = vi.fn(async () => ({ kind: "json", data: { connected: false, code: "CONNECTION_FAILED" } }));
const get = vi.fn(async () => ({ kind: "json", data: { available: false, reason: "NOT_CONFIGURED", configuration: null } }));
const put = vi.fn(async () => ({ kind: "json", data: { provider: "PISTON", baseUrl: "https://sandbox.example", enabled: true } }));

vi.mock("../app/providers/runtime", () => ({ api: { get, post, put, patch: vi.fn() } }));

describe("admin API contract", async () => {
  const { adminApi } = await import("./api");

  it("reads model settings without treating NOT_CONFIGURED as a transport error", async () => {
    await expect(adminApi.getModelConfig()).resolves.toMatchObject({ available: false, reason: "NOT_CONFIGURED", configuration: null });
    expect(get).toHaveBeenCalledWith("/admin/model-config");
  });

  it("calls the connection test endpoint without a request body", async () => {
    await adminApi.testModelConnection();
    expect(post).toHaveBeenCalledWith("/admin/model-config/test");
    expect(post.mock.calls[0]).toHaveLength(1);
  });

  it("keeps sandbox configuration behind the admin v1 boundary", async () => {
    await adminApi.getSandboxConfig();
    expect(get).toHaveBeenLastCalledWith("/admin/sandbox-config");
  });

  it("sends only the provider, address, and enabled flag when updating sandbox config", async () => {
    const payload = { provider: "PISTON" as const, baseUrl: "https://sandbox.example", enabled: true };
    await adminApi.updateSandboxConfig(payload);
    expect(put).toHaveBeenCalledWith("/admin/sandbox-config", payload);
  });

  it("uses the saved sandbox connection test endpoint without a browser-side target", async () => {
    await adminApi.testSandboxConnection();
    expect(post).toHaveBeenLastCalledWith("/admin/sandbox-config/test");
  });
});
