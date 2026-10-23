import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../shared/api";
import { flushPromises, mount } from "@vue/test-utils";
import AdminUsersView from "./AdminUsersView.vue";

const listUser = {
  id: 7,
  email: "admin@example.edu",
  username: "ACha_",
  status: "ACTIVE" as const,
  disabledReason: null,
  disabledAt: null,
  roles: ["ADMIN" as const],
  createdAt: "2026-08-10T01:00:00Z",
  updatedAt: "2026-08-10T02:00:00Z",
};

const detailUser = {
  ...listUser,
  updatedAt: "2026-08-12T09:45:00Z",
};

const users = vi.hoisted(() => vi.fn());
const user = vi.hoisted(() => vi.fn());
const userLearning = vi.hoisted(() => vi.fn());
const updateUserStatus = vi.hoisted(() => vi.fn());
const updateUserRoles = vi.hoisted(() => vi.fn());

const learningFootprint = {
  userId: 7,
  email: "admin@example.edu",
  username: "ACha_",
  status: "ACTIVE",
  courses: [
    {
      id: "course-1",
      title: "数据结构 2026 秋",
      status: "ACTIVE",
      totalPoints: 57,
      completedPoints: 12,
      readableFragments: 20,
      lastPointTitle: "链表",
      lastLearningAt: "2026-10-20T08:00:00Z",
      activityAt: "2026-10-20T08:00:00Z",
    },
  ],
  bookmarks: 3,
  classroomSessions: 2,
  classroomEvents: 1009,
  lastActivityAt: "2026-10-15T10:00:00Z",
  tokensLast30Days: 684666,
  callsLast30Days: 180,
  recentJobs: [
    { id: "job-1", type: "FRAGMENT", status: "SUCCEEDED", createdAt: "2026-10-21T09:00:00Z", tokens: 12500 },
    { id: "job-2", type: "OUTLINE", status: "FAILED", createdAt: "2026-10-20T09:00:00Z", tokens: 300 },
  ],
  learningMap: {
    generatedAt: "2026-10-23T00:00:00Z",
    nodes: [
      {
        id: "root", parentId: null, level: 0, title: "数据结构", mastery: 0.559, confidence: 0.7, status: "ESTIMATED" as const,
        components: { classroom: 0.6, animation: null, chat: null, code: null },
        signals: { classroomSteps: 1009, classroomAnswered: 0, classroomCorrect: 0, classroomHints: 0, classroomSkips: 0, classroomAsks: 0, animations: 0, animationRepeats: 0, chatSessions: 0, chatQuestions: 0, codeRuns: 0, codePassed: 0, codeFailed: 0 },
      },
      {
        id: "ch1", parentId: "root", level: 1, title: "绪论", mastery: 0.8, confidence: 0.6, status: "ESTIMATED" as const,
        components: { classroom: 0.8, animation: null, chat: null, code: null },
        signals: { classroomSteps: 100, classroomAnswered: 0, classroomCorrect: 0, classroomHints: 0, classroomSkips: 0, classroomAsks: 0, animations: 0, animationRepeats: 0, chatSessions: 0, chatQuestions: 0, codeRuns: 0, codePassed: 0, codeFailed: 0 },
      },
      {
        id: "ch2", parentId: "root", level: 1, title: "线性表", mastery: 0.3, confidence: 0.5, status: "LOW_EVIDENCE" as const,
        components: { classroom: 0.3, animation: null, chat: null, code: null },
        signals: { classroomSteps: 50, classroomAnswered: 0, classroomCorrect: 0, classroomHints: 0, classroomSkips: 0, classroomAsks: 0, animations: 0, animationRepeats: 0, chatSessions: 0, chatQuestions: 0, codeRuns: 0, codePassed: 0, codeFailed: 0 },
      },
    ],
  },
};

vi.mock("../api", () => ({
  adminApi: { users, user, userLearning, updateUserStatus, updateUserRoles },
  adminErrorMessage: (failure: unknown, action: string) => {
    const error = failure as { code?: string; requestId?: string };
    return `${action}未完成（${error.code || "NETWORK_ERROR"}）。请求 ID：${error.requestId || "无"}`;
  },
  formatDate: (value?: string | null) => value || "未记录",
}));

describe("AdminUsersView", () => {
  beforeEach(() => {
    users.mockReset().mockResolvedValue({ items: [listUser], page: 0, size: 20, total: 1 });
    user.mockReset().mockResolvedValue(detailUser);
    userLearning.mockReset().mockResolvedValue(learningFootprint);
    updateUserStatus.mockReset();
    updateUserRoles.mockReset();
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("sends zero-based filters and keeps a protected 409 visible", async () => {
    updateUserStatus.mockRejectedValue(new ApiClientError({
      status: 409,
      code: "ADMIN_LAST_ADMIN_PROTECTED",
      message: "不能禁用最后一个管理员",
      requestId: "req-user-409",
      details: [],
    }));
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    const search = wrapper.get("input[placeholder='按邮箱或用户名搜索']");
    await search.setValue("admin@example.edu");
    const selects = wrapper.findAll("form select");
    await selects[0].setValue("ACTIVE");
    await selects[1].setValue("ADMIN");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(users).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, search: "admin@example.edu", status: "ACTIVE", role: "ADMIN" }));

    const disable = wrapper.findAll("button").find((button) => button.text() === "禁用");
    await disable!.trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("ADMIN_LAST_ADMIN_PROTECTED");
    expect(wrapper.text()).toContain("req-user-409");
  });

  it("loads the real user detail through the public view action", async () => {
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    expect(wrapper.text()).toContain("ACha_");

    const viewButton = wrapper.findAll("button").find((button) => button.text() === "查看");
    expect(viewButton).toBeDefined();
    await viewButton!.trigger("click");
    await flushPromises();

    expect(user).toHaveBeenCalledWith(7);
    expect(wrapper.get("section[aria-label='用户详情']").text()).toContain("2026-08-12T09:45:00Z");
    expect(wrapper.find("section[aria-label='用户列表'] + section[aria-label='用户详情']").exists()).toBe(true);
  });

  it("shows the learning footprint inside the detail inspector", async () => {
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    const viewButton = wrapper.findAll("button").find((button) => button.text() === "查看");
    await viewButton!.trigger("click");
    await flushPromises();

    expect(userLearning).toHaveBeenCalledWith(7);
    const inspector = wrapper.get("section[aria-label='用户详情']");
    expect(inspector.text()).toContain("学习情况");
    expect(inspector.text()).toContain("684,666");
    expect(inspector.text()).toContain("整体掌握 56%");
    expect(inspector.text()).toContain("绪论");
    expect(inspector.text()).toContain("数据结构 2026 秋");
    expect(inspector.text()).toContain("12/57 知识点");
    expect(inspector.text()).toContain("最近生成作业");
    expect(inspector.text()).toContain("SUCCEEDED");
  });

  it("says a learner without records has not started, instead of calling it a zero", async () => {
    userLearning.mockResolvedValue({
      ...learningFootprint,
      courses: [],
      bookmarks: 0,
      classroomSessions: 0,
      classroomEvents: 0,
      lastActivityAt: null,
      tokensLast30Days: 0,
      callsLast30Days: 0,
      recentJobs: [],
      learningMap: {
        generatedAt: "2026-10-23T00:00:00Z",
        nodes: [
          {
            id: "root", parentId: null, level: 0, title: "数据结构", mastery: null, confidence: 0, status: "NOT_STARTED" as const,
            components: { classroom: null, animation: null, chat: null, code: null },
            signals: { classroomSteps: 0, classroomAnswered: 0, classroomCorrect: 0, classroomHints: 0, classroomSkips: 0, classroomAsks: 0, animations: 0, animationRepeats: 0, chatSessions: 0, chatQuestions: 0, codeRuns: 0, codePassed: 0, codeFailed: 0 },
          },
        ],
      },
    });
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    const viewButton = wrapper.findAll("button").find((button) => button.text() === "查看");
    await viewButton!.trigger("click");
    await flushPromises();

    const inspector = wrapper.get("section[aria-label='用户详情']");
    expect(inspector.text()).toContain("还没有任何学习记录");
    expect(inspector.text()).toContain("从未");
  });

  it("keeps the detail usable when the learning footprint read fails", async () => {
    userLearning.mockRejectedValue(new ApiClientError({
      status: 500,
      code: "MODEL_UPSTREAM_ERROR",
      message: "boom",
      requestId: "req-learning-500",
      details: [],
    }));
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    const viewButton = wrapper.findAll("button").find((button) => button.text() === "查看");
    await viewButton!.trigger("click");
    await flushPromises();

    const inspector = wrapper.get("section[aria-label='用户详情']");
    expect(inspector.text()).toContain("2026-08-12T09:45:00Z");
    expect(inspector.text()).toContain("学习情况读取失败");
    expect(inspector.text()).toContain("req-learning-500");
  });

  it("exposes the table filter, sort, column visibility, and row selection controls", async () => {
    const wrapper = mount(AdminUsersView);
    await flushPromises();
    expect(wrapper.get("[role='toolbar'][aria-label='表格工具']")).toBeDefined();
    expect(wrapper.get("input[placeholder='筛选当前页']")).toBeDefined();
    expect(wrapper.get("button[aria-label='排序']")).toBeDefined();
    expect(wrapper.get("summary[aria-label='列显示']")).toBeDefined();
    const rowSelection = wrapper.get("input[aria-label='选择用户 ACha_']");
    await rowSelection.setValue(true);
    expect(wrapper.text()).toContain("已选 1 / 1");
  });

  it("keeps every unsaved role toggle before submitting the public update", async () => {
    updateUserRoles.mockResolvedValue({ ...detailUser, roles: ["ADMIN", "STUDENT", "TEACHER"] });
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    const roleCheckboxes = wrapper.findAll(".admin-role-cluster input[type='checkbox']");
    await roleCheckboxes[0].setValue(true);
    await roleCheckboxes[1].setValue(true);
    await flushPromises();

    const saveButton = wrapper.findAll("button").find((button) => button.text() === "保存角色");
    expect(saveButton).toBeDefined();
    await saveButton!.trigger("click");
    await flushPromises();

    expect(updateUserRoles).toHaveBeenCalledWith(7, { roles: ["ADMIN", "STUDENT", "TEACHER"] });
  });

  it("refreshes the active filter after a successful user status update", async () => {
    users.mockReset()
      .mockResolvedValueOnce({ items: [listUser], page: 0, size: 20, total: 1 })
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, total: 0 });
    updateUserStatus.mockResolvedValue({ ...detailUser, status: "DISABLED" });
    const wrapper = mount(AdminUsersView);
    await flushPromises();

    const disable = wrapper.findAll("button").find((button) => button.text() === "禁用");
    await disable!.trigger("click");
    await flushPromises();

    expect(users).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("没有匹配用户");
  });
});
