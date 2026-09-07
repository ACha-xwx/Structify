import { describe, expect, it } from "vitest";
import { createRouteGuard } from "./guards";
import { adminRoutes } from "../admin/routes";

function adminSettingsMeta() {
  const route = adminRoutes.find((item) => item.name === "admin-settings");
  if (!route) throw new Error("admin settings route is missing");
  return route.meta ?? {};
}

describe("路由守卫公共边界", () => {
  it("admin 主机内导航到学习路径时跳转到公共主机并保留书签", async () => {
    let redirectedTo = "";
    let restoreAttempts = 0;
    const guard = createRouteGuard({
      auth: {
        state: { status: "idle", user: null, capabilities: null, error: null },
        restoreSession: async () => { restoreAttempts += 1; },
      } as never,
      location: { hostname: "admin.structify.cn" },
      redirectToPublic: (url) => { redirectedTo = url; },
    });

    await expect(guard({
      path: "/user/chapters",
      fullPath: "/user/chapters?chapter=03#resources",
      meta: { layout: "workbench", module: "主线学习" },
    } as never)).resolves.toBe(false);

    expect(redirectedTo).toBe("https://structify.cn/user/chapters?chapter=03#resources");
    expect(restoreAttempts).toBe(0);
  });

  it("admin 主机根路径在前端导航时也回到管理总览", async () => {
    const guard = createRouteGuard({
      auth: {
        state: { status: "anonymous", user: null, capabilities: null, error: null },
        restoreSession: async () => undefined,
      } as never,
      location: { hostname: "admin.structify.cn" },
      redirectToPublic: () => { throw new Error("根路径不应跳转到公共主机"); },
    });

    await expect(guard({ path: "/", fullPath: "/", meta: {} } as never)).resolves.toEqual({ path: "/admin" });
  });

  it("admin 登录与管理路径不触发公共主机跳转", async () => {
    let redirected = false;
    const guard = createRouteGuard({
      auth: {
        state: { status: "anonymous", user: null, capabilities: null, error: null },
        restoreSession: async () => undefined,
      } as never,
      location: { hostname: "admin.structify.cn" },
      redirectToPublic: () => { redirected = true; },
    });

    await expect(guard({ path: "/login", fullPath: "/login?redirect=%2Fadmin", meta: {} } as never)).resolves.toBe(true);
    expect(redirected).toBe(false);
  });

  it("未登录访问显式受保护路由时保留回跳地址", async () => {
    const guard = createRouteGuard({
      auth: {
        state: { status: "anonymous", user: null, capabilities: null, error: null },
        restoreSession: async () => undefined,
      } as never,
    });
    const result = await guard({ path: "/account/billing", fullPath: "/account/billing?from=coach", meta: { requiresAuth: true } } as never);
    expect(result).toMatchObject({ name: "login", query: { redirect: "/account/billing?from=coach" } });
  });

  it("游客可直接进入普通学习页面，不触发会话恢复或登录跳转", async () => {
    let restoreAttempts = 0;
    const guard = createRouteGuard({
      auth: {
        state: { status: "idle", user: null, capabilities: null, error: null },
        restoreSession: async () => { restoreAttempts += 1; },
      } as never,
    });

    await expect(guard({
      path: "/user/chapters",
      fullPath: "/user/chapters?chapter=03",
      meta: { layout: "workbench", module: "主线学习" },
    } as never)).resolves.toBe(true);
    expect(restoreAttempts).toBe(0);
  });

  it("localhost 本地预览页不触发会话恢复", async () => {
    let restoreAttempts = 0;
    const guard = createRouteGuard({
      auth: {
        state: { status: "idle", user: null, capabilities: null, error: null },
        restoreSession: async () => { restoreAttempts += 1; },
      } as never,
      location: { hostname: "127.0.0.1" },
    });

    await expect(guard({
      path: "/user/presentation",
      fullPath: "/user/presentation?lessonId=01-01A",
      meta: { requiresAuth: true, allowsLocalPreview: true },
    } as never)).resolves.toBe(true);

    expect(restoreAttempts).toBe(0);
  });

  it("角色不足时进入 403，而不是只隐藏导航", async () => {
    const guard = createRouteGuard({
      auth: {
        state: { status: "authenticated", user: { id: 1, email: "student@example.com", roles: ["STUDENT"] }, capabilities: null, error: null },
        restoreSession: async () => undefined,
      } as never,
    });
    const result = await guard({ path: "/admin", fullPath: "/admin", meta: { requiresAuth: true, roles: ["ADMIN"] } } as never);
    expect(result).toEqual({ name: "forbidden" });
  });

  it("暂时离线时保留已验证会话和原导航", async () => {
    const guard = createRouteGuard({
      auth: {
        state: {
          status: "offline",
          user: { id: 1, email: "student@example.com", roles: ["STUDENT"] },
          capabilities: null,
          error: Object.assign(new Error("Failed to fetch"), { code: "NETWORK_ERROR" }),
        },
        restoreSession: async () => undefined,
      } as never,
    });

    await expect(guard({ path: "/account/billing", fullPath: "/account/billing", meta: { requiresAuth: true } } as never)).resolves.toBe(true);
  });

  it("暂时离线时仍拒绝已知角色不足的管理路由", async () => {
    const guard = createRouteGuard({
      auth: {
        state: {
          status: "offline",
          user: { id: 1, email: "student@example.com", roles: ["STUDENT"] },
          capabilities: null,
          error: Object.assign(new Error("Failed to fetch"), { code: "NETWORK_ERROR" }),
        },
        restoreSession: async () => undefined,
      } as never,
    });

    await expect(guard({ path: "/admin", fullPath: "/admin", meta: { requiresAuth: true, roles: ["ADMIN"] } } as never))
      .resolves.toEqual({ name: "forbidden" });
  });

  it("冷启动离线且没有已保留用户时仍拒绝显式受保护路由", async () => {
    const guard = createRouteGuard({
      auth: {
        state: {
          status: "offline",
          user: null,
          capabilities: null,
          error: Object.assign(new Error("Failed to fetch"), { code: "NETWORK_ERROR" }),
        },
        restoreSession: async () => undefined,
      } as never,
    });

    await expect(guard({ path: "/account/billing", fullPath: "/account/billing", meta: { requiresAuth: true } } as never))
      .resolves.toEqual({ name: "login", query: { redirect: "/account/billing" } });
  });

  it("游客不能绕过显式能力保护", async () => {
    const guard = createRouteGuard({
      auth: {
        state: { status: "anonymous", user: null, capabilities: null, error: null },
        restoreSession: async () => undefined,
        loadCapabilities: async () => null,
      } as never,
    });

    await expect(guard({
      path: "/admin/settings",
      fullPath: "/admin/settings",
      meta: { requiresAuth: true, roles: ["ADMIN"], requiresCapability: "modelSettings" },
    } as never)).resolves.toMatchObject({ name: "login", query: { redirect: "/admin/settings" } });
  });

  it("能力接口暂时不可用时不误跳转到 403", async () => {
    const guard = createRouteGuard({
      auth: {
        state: {
          status: "authenticated",
          user: { id: 1, email: "admin@example.com", roles: ["ADMIN"] },
          capabilities: null,
          error: Object.assign(new Error("unavailable"), { status: 503, code: "SERVICE_UNAVAILABLE" }),
        },
        restoreSession: async () => undefined,
        loadCapabilities: async () => null,
      } as never,
    });

    await expect(guard({ path: "/admin/settings", fullPath: "/admin/settings", meta: adminSettingsMeta() } as never)).resolves.toBe(true);
  });

  it.each([
    ["NOT_CONFIGURED", { available: false, status: "NOT_CONFIGURED", reason: "NOT_CONFIGURED" }],
    ["disabled configuration", { available: false, status: "UNAVAILABLE", reason: "PERSISTED_CONFIGURATION_DISABLED" }],
    ["zero quota", { available: false, status: "UNAVAILABLE", reason: "PERSISTED_QUOTA_NOT_CONFIGURED" }],
    ["model service unavailable", { available: false, status: "UNAVAILABLE", reason: "MODEL_CONFIG_UNAVAILABLE" }],
  ])("allows an ADMIN into settings when model settings are %s", async (_state, modelSettings) => {
    const guard = createRouteGuard({
      auth: {
        state: {
          status: "authenticated",
          user: { id: 1, email: "admin@example.com", roles: ["ADMIN"] },
          capabilities: {
            userId: 1,
            roles: ["ADMIN"],
            modules: { modelSettings },
            service: { name: "spring", version: "test", status: "AVAILABLE" },
          },
          error: null,
        },
        restoreSession: async () => undefined,
      } as never,
    });

    await expect(guard({ path: "/admin/settings", fullPath: "/admin/settings", meta: adminSettingsMeta() } as never)).resolves.toBe(true);
  });
});
