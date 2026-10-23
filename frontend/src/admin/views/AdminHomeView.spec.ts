import { describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import AdminHomeView from "./AdminHomeView.vue";

const capabilities = vi.hoisted(() => vi.fn(async () => ({
  userId: 9,
  roles: ["ADMIN"],
  service: { name: "spring", version: "1.0.18", status: "AVAILABLE" },
    modules: {
    users: { available: true, status: "AVAILABLE" },
    reviewQueue: { available: true, status: "AVAILABLE" },
    backgroundTasks: { available: true, status: "AVAILABLE" },
    audit: { available: true, status: "AVAILABLE" },
    modelSettings: { available: false, status: "NOT_CONFIGURED", reason: "NOT_CONFIGURED" },
      mailSettings: { available: false, status: "UNAVAILABLE", reason: "MAIL_CONFIG_UNAVAILABLE" },
      sandboxSettings: { available: false, status: "UNAVAILABLE", reason: "SANDBOX_CONFIG_UNAVAILABLE" },
  },
})));

const observabilityModules = vi.hoisted(() => vi.fn(async () => ([
  { module: "studyroom", available: true, status: "AVAILABLE", reason: null, counts: { jobs: 34 } },
  { module: "learning", available: true, status: "AVAILABLE", reason: null, counts: { classroomEvents: 1009 } },
  { module: "quota", available: true, status: "AVAILABLE", reason: null, counts: { reservationsToday: 3 } },
])));

vi.mock("../api", () => ({
  adminApi: { capabilities, observabilityModules },
  adminErrorMessage: () => "读取管理能力未完成（NETWORK_ERROR）。",
}));

describe("AdminHomeView", () => {
  it("keeps model settings reachable when the real capability says NOT_CONFIGURED", async () => {
    const component = { template: "<div />" };
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: "/admin", component },
      { path: "/admin/users", component },
      { path: "/admin/reviews", component },
      { path: "/admin/tasks", component },
      { path: "/admin/jobs", component },
      { path: "/admin/classes", component },
      { path: "/admin/audit", component },
      { path: "/admin/settings", component },
      { path: "/admin/mail", component },
      { path: "/admin/sandbox", component },
    ] });
    await router.push("/admin");
    await router.isReady();
    const wrapper = mount(AdminHomeView, { global: { plugins: [router] } });
    await flushPromises();

    expect(capabilities).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("尚未完成服务端配置");
    expect(wrapper.get("a[href='/admin/settings']").text()).toContain("进入模型配置");
    expect(wrapper.get("a[href='/admin/mail']").text()).toContain("进入邮件发送设置");
    expect(wrapper.get("a[href='/admin/sandbox']").text()).toContain("进入沙箱配置");
    expect(wrapper.text()).toContain("服务端尚未提供沙箱配置管理接口");
    expect(wrapper.text()).toContain("邮件配置服务暂时不可用");
    expect(wrapper.text()).not.toMatch(/总用户|待审核|任务总数/);
    // The console home lists the studyroom, learning, quota and classes modules the old capabilities never reported.
    expect(wrapper.get("a[href='/admin/jobs']").text()).toContain("课程生成链路");
    expect(wrapper.get("a[href='/admin/classes']").text()).toContain("班级");
  });
});
