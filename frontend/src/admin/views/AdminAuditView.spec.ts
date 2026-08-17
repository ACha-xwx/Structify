import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import AdminAuditView from "./AdminAuditView.vue";

const auditEvents = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({
  adminApi: { auditEvents },
  adminErrorMessage: () => "读取审计日志未完成（NETWORK_ERROR）。请重试。",
  formatDate: (value?: string | null) => value || "未记录",
}));

describe("AdminAuditView", () => {
  beforeEach(() => auditEvents.mockReset().mockResolvedValue({ items: [], page: 0, size: 50, total: 0 }));

  it("sends actor and ISO date-time filters while preserving zero-based pagination", async () => {
    const wrapper = mount(AdminAuditView);
    await flushPromises();

    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("23");
    await inputs[1].setValue("USER_ROLES_CHANGED");
    await inputs[2].setValue("USER");
    await inputs[3].setValue("42");
    await inputs[4].setValue("2026-08-12T09:30");
    await inputs[5].setValue("2026-08-12T10:45");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(auditEvents).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0,
      actorUserId: 23,
      action: "USER_ROLES_CHANGED",
      targetType: "USER",
      targetId: "42",
      from: new Date("2026-08-12T09:30").toISOString(),
      to: new Date("2026-08-12T10:45:59.999").toISOString(),
    }));
    expect(inputs[1].attributes("maxlength")).toBe("64");
  });

  it("keeps the current event list and reports an invalid time range without calling the API", async () => {
    const wrapper = mount(AdminAuditView);
    await flushPromises();
    const initialCalls = auditEvents.mock.calls.length;
    const inputs = wrapper.findAll("input");
    await inputs[4].setValue("2026-08-12T10:45");
    await inputs[5].setValue("2026-08-12T09:30");

    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(auditEvents).toHaveBeenCalledTimes(initialCalls);
    expect(wrapper.text()).toContain("开始时间不能晚于结束时间");
  });

  it("provides table interaction controls for the audit list", async () => {
    auditEvents.mockResolvedValueOnce({
      items: [{ id: 1, actorUserId: 23, action: "USER_ROLES_CHANGED", targetType: "USER", targetId: "42", result: "SUCCESS", beforeSummary: "", afterSummary: "", requestId: "req-1", createdAt: "2026-08-12T10:00:00Z" }],
      page: 0,
      size: 50,
      total: 1,
    });
    const wrapper = mount(AdminAuditView);
    await flushPromises();
    expect(wrapper.get("[role='toolbar'][aria-label='表格工具']")).toBeDefined();
    await wrapper.get("input[aria-label='选择审计事件 1']").setValue(true);
    expect(wrapper.text()).toContain("已选 1 / 1");
    await wrapper.get("button.admin-action-button").trigger("click");
    expect(wrapper.find("section[aria-label='审计事件摘要']").exists()).toBe(true);
  });
});
