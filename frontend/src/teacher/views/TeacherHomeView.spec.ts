import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import TeacherHomeView from "./TeacherHomeView.vue";

const classes = vi.hoisted(() => vi.fn());
const students = vi.hoisted(() => vi.fn());
const studentFootprint = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({
  teacherApi: { classes, students, studentFootprint },
  teacherErrorMessage: (failure: unknown, action: string) => {
    const error = failure as { code?: string; requestId?: string };
    return `${action}未完成（${error.code || "NETWORK_ERROR"}）。请求 ID：${error.requestId || "无"}`;
  },
}));

vi.mock("../../shared/api", () => ({
  formatDateTime: (value?: string | null) => value ?? "未记录",
}));

const baseNode = {
  components: { classroom: null, animation: null, chat: null, code: null },
  signals: {
    classroomSteps: 0, classroomAnswered: 0, classroomCorrect: 0, classroomHints: 0, classroomSkips: 0,
    classroomAsks: 0, animations: 0, animationRepeats: 0, chatSessions: 0, chatQuestions: 0,
    codeRuns: 0, codePassed: 0, codeFailed: 0,
  },
};

function footprint(overrides: Record<string, unknown> = {}) {
  return {
    studentId: 7,
    username: "小王",
    email: "wang@example.test",
    courses: [
      {
        id: "course-1", title: "数据结构 2026 秋", status: "ACTIVE", totalPoints: 57, completedPoints: 12,
        readableFragments: 20, lastPointTitle: "链表", lastLearningAt: "2026-10-20T08:00:00Z", activityAt: "2026-10-20T08:00:00Z",
      },
    ],
    bookmarks: 3,
    classroomSessions: 2,
    classroomEvents: 120,
    lastActivityAt: "2026-10-22T10:00:00Z",
    learningMap: {
      generatedAt: "2026-10-23T08:00:00Z",
      nodes: [
        { ...baseNode, id: "root", parentId: null, level: 0, title: "数据结构", mastery: 0.55, confidence: 0.5, status: "ESTIMATED" },
        { ...baseNode, id: "01-introduction", parentId: "root", level: 1, title: "绪论", mastery: 0.62, confidence: 0.4, status: "ESTIMATED" },
        { ...baseNode, id: "02-linear-list", parentId: "root", level: 1, title: "线性表", mastery: null, confidence: 0, status: "NOT_STARTED" },
      ],
    },
    ...overrides,
  };
}

describe("teacher home", () => {
  beforeEach(() => {
    classes.mockReset().mockResolvedValue([{ id: "class-1", name: "数据结构 1 班", term: "2026 秋", students: 2 }]);
    students.mockReset().mockResolvedValue([
      { id: 7, username: "小王", email: "wang@example.test", status: "ACTIVE", lastActivityAt: "2026-10-22T10:00:00Z", classroomSteps: 120 },
      { id: 8, username: null, email: "li@example.test", status: "ACTIVE", lastActivityAt: null, classroomSteps: 0 },
    ]);
    studentFootprint.mockReset().mockResolvedValue(footprint());
  });

  it("lists the class roster with each student's activity", async () => {
    const wrapper = mount(TeacherHomeView);
    await flushPromises();

    const text = wrapper.text();
    expect(text).toContain("数据结构 1 班");
    expect(text).toContain("小王");
    expect(text).toContain("120");
    expect(text).toContain("未记录");
    wrapper.unmount();
  });

  it("opens one student's footprint only when asked, in a single call", async () => {
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    expect(studentFootprint).not.toHaveBeenCalled();

    await wrapper.findAll("button").find((button) => button.text().includes("学习情况"))?.trigger("click");
    await flushPromises();

    expect(studentFootprint).toHaveBeenCalledTimes(1);
    expect(studentFootprint).toHaveBeenCalledWith(7);
    const panel = wrapper.get("section[aria-label='学生学习情况']");
    expect(panel.text()).toContain("整体掌握 55%");
    expect(panel.text()).toContain("课程进度");
    expect(panel.text()).toContain("数据结构 2026 秋");
    expect(panel.text()).toContain("12/57 知识点");
    expect(panel.text()).toContain("学到「链表」");
    expect(panel.text()).toContain("绪论");
    expect(panel.text()).toContain("62%");
    // A chapter with nothing recorded reads as a dash, not as zero.
    expect(panel.text()).toContain("—");
    wrapper.unmount();
  });

  it("says so when the teacher has no class yet", async () => {
    classes.mockResolvedValue([]);
    const wrapper = mount(TeacherHomeView);
    await flushPromises();

    expect(wrapper.text()).toContain("你名下还没有班级");
    wrapper.unmount();
  });

  it("opens the lessons of a chapter only when the teacher asks for them", async () => {
    studentFootprint.mockResolvedValue(footprint({
      learningMap: {
        generatedAt: "2026-10-23T08:00:00Z",
        nodes: [
          { ...baseNode, id: "root", parentId: null, level: 0, title: "数据结构", mastery: 0.4, confidence: 0.5, status: "ESTIMATED" },
          { ...baseNode, id: "01-introduction", parentId: "root", level: 1, title: "绪论", mastery: 0.4, confidence: 0.5, status: "ESTIMATED" },
          { ...baseNode, id: "01-introduction:01-01", parentId: "01-introduction", level: 2, title: "数据结构的基础概念", mastery: 0.8, confidence: 0.6, status: "ESTIMATED" },
          { ...baseNode, id: "01-introduction:01-02", parentId: "01-introduction", level: 2, title: "数据结构的内容", mastery: null, confidence: 0, status: "NOT_STARTED" },
        ],
      },
    }));
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text().includes("学习情况"))?.trigger("click");
    await flushPromises();

    // A chapter can hold dozens of lessons, so they stay folded until one is opened.
    expect(wrapper.text()).not.toContain("数据结构的基础概念");
    const toggle = wrapper.get(".lmc__toggle");
    expect(toggle.text()).toBe("2 个课时");
    expect(toggle.attributes("aria-expanded")).toBe("false");

    await toggle.trigger("click");

    expect(wrapper.text()).toContain("数据结构的基础概念");
    expect(wrapper.text()).toContain("80%");
    expect(wrapper.text()).toContain("未开始");
    wrapper.unmount();
  });

  it("says the student has no records yet instead of drawing empty chapters", async () => {
    studentFootprint.mockResolvedValue(footprint({
      courses: [],
      bookmarks: 0,
      classroomSessions: 0,
      classroomEvents: 0,
      lastActivityAt: null,
      learningMap: {
        generatedAt: "2026-10-23T08:00:00Z",
        nodes: [
          { ...baseNode, id: "root", parentId: null, level: 0, title: "数据结构", mastery: null, confidence: 0, status: "NOT_STARTED" },
          { ...baseNode, id: "01-introduction", parentId: "root", level: 1, title: "绪论", mastery: null, confidence: 0, status: "NOT_STARTED" },
        ],
      },
    }));
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text().includes("学习情况"))?.trigger("click");
    await flushPromises();

    const panel = wrapper.get("section[aria-label='学生学习情况']");
    expect(panel.text()).toContain("还没有任何学习记录");
    expect(panel.text()).toContain("从未");
    expect(panel.find(".lmc").exists()).toBe(false);
    wrapper.unmount();
  });

  it("keeps the roster usable when the footprint read fails, and offers a retry", async () => {
    studentFootprint.mockRejectedValue({ code: "AUTH_FORBIDDEN", requestId: "req-t-403", status: 403 });
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text().includes("学习情况"))?.trigger("click");
    await flushPromises();

    const panel = wrapper.get("section[aria-label='学生学习情况']");
    expect(panel.text()).toContain("学习情况读取失败");
    expect(panel.text()).toContain("req-t-403");
    // The roster above is untouched, and the retry button is wired.
    expect(wrapper.text()).toContain("小王");
    studentFootprint.mockResolvedValue(footprint());
    await panel.findAll("button").find((button) => button.text() === "重试")!.trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("整体掌握 55%");
    wrapper.unmount();
  });
});
