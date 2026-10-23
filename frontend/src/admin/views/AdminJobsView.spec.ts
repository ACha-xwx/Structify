import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import AdminJobsView from "./AdminJobsView.vue";

const jobs = vi.hoisted(() => vi.fn());
const cost = vi.hoisted(() => vi.fn());
const observabilityModules = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({
  adminApi: { jobs, cost, observabilityModules },
  adminErrorMessage: (failure: unknown, action: string) => `${action}未完成（${(failure as { code?: string }).code ?? "NETWORK_ERROR"}）`,
  formatDate: (value?: string | null) => value ?? "未记录",
}));

const jobPage = {
  items: [
    {
      source: "STUDYROOM" as const,
      id: "job-1",
      type: "FRAGMENT",
      ownerUserId: 4,
      status: "FAILED",
      createdAt: "2026-10-22T10:00:00Z",
      updatedAt: "2026-10-22T10:02:00Z",
      error: "MODEL_TIMEOUT",
      tokens: 640,
    },
    {
      source: "BACKGROUND" as const,
      id: "9",
      type: "STALE_TASK_RECOVERY",
      ownerUserId: null,
      status: "SUCCEEDED",
      createdAt: "2026-10-22T09:00:00Z",
      updatedAt: null,
      error: null,
      tokens: 0,
    },
  ],
  page: 0,
  size: 20,
  total: 2,
};

const costReport = {
  from: "2026-09-24",
  to: "2026-10-23",
  groupBy: "DAY" as const,
  calls: 180,
  tokens: 684666,
  rows: [{ bucket: "2026-10-15", userId: null, calls: 45, tokens: 300720 }],
};

const moduleReport = [
  { module: "studyroom", available: true, status: "AVAILABLE", reason: null, counts: { jobs: 34, queued: 2, failed: 1, fragments: 28, rooms: 4, runs: 4 } },
  { module: "learning", available: true, status: "AVAILABLE", reason: null, counts: { classroomEvents: 1009, codeRuns: 114, animationRecords: 6, learners: 3 } },
  { module: "quota", available: true, status: "AVAILABLE", reason: null, counts: { reservationsToday: 5, tokensToday: 1234 } },
];

describe("admin jobs view", () => {
  beforeEach(() => {
    jobs.mockResolvedValue(jobPage);
    cost.mockResolvedValue(costReport);
    observabilityModules.mockResolvedValue(moduleReport);
  });

  it("shows both ledgers in one table, with the spend of each job", async () => {
    const wrapper = mount(AdminJobsView);
    await flushPromises();

    const rows = wrapper.findAll("tbody tr");
    expect(rows.length).toBeGreaterThanOrEqual(2);
    const text = wrapper.text();
    expect(text).toContain("FRAGMENT");
    expect(text).toContain("STALE_TASK_RECOVERY");
    expect(text).toContain("640");
    expect(text).toContain("生成");
  });

  it("reports the module counts the console home never had", async () => {
    const wrapper = mount(AdminJobsView);
    await flushPromises();

    const text = wrapper.text();
    expect(text).toContain("排队/运行 2");
    expect(text).toContain("失败 1");
    expect(text).toContain("1009");
    expect(text).toContain("1234");
  });

  it("totals the settled spend and lists it by day", async () => {
    const wrapper = mount(AdminJobsView);
    await flushPromises();

    const text = wrapper.text();
    expect(text).toContain("180");
    expect(text).toContain("684666");
    expect(text).toContain("2026-10-15");
  });

  it("keeps a failure message visible instead of swallowing it", async () => {
    jobs.mockRejectedValue({ code: "NETWORK_ERROR" });
    const wrapper = mount(AdminJobsView);
    await flushPromises();

    expect(wrapper.text()).toContain("读取作业列表未完成");
  });
});
