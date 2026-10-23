import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import TeacherHomeView from "./TeacherHomeView.vue";

const classes = vi.hoisted(() => vi.fn());
const students = vi.hoisted(() => vi.fn());
const studentMap = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({
  teacherApi: { classes, students, studentMap },
}));

vi.mock("../../../admin/api", () => ({
  formatDate: (value?: string | null) => value ?? "未记录",
}));

const map = {
  generatedAt: "2026-10-23T08:00:00Z",
  nodes: [
    { id: "root", parentId: null, level: 0, title: "数据结构", mastery: 0.55, confidence: 0.5, status: "ESTIMATED" },
    { id: "01-introduction", parentId: "root", level: 1, title: "绪论", mastery: 0.62, confidence: 0.4, status: "ESTIMATED" },
    { id: "02-linear-list", parentId: "root", level: 1, title: "线性表", mastery: null, confidence: 0, status: "NO_DATA" },
  ],
};

describe("teacher home", () => {
  beforeEach(() => {
    classes.mockResolvedValue([{ id: "class-1", name: "数据结构 1 班", term: "2026 秋", students: 2 }]);
    students.mockResolvedValue([
      { id: 7, username: "小王", email: "wang@example.test", status: "ACTIVE", lastActivityAt: "2026-10-22T10:00:00Z", classroomSteps: 120 },
      { id: 8, username: null, email: "li@example.test", status: "ACTIVE", lastActivityAt: null, classroomSteps: 0 },
    ]);
    studentMap.mockResolvedValue(map);
  });

  it("lists the class roster with each student's activity", async () => {
    const wrapper = mount(TeacherHomeView);
    await flushPromises();

    const text = wrapper.text();
    expect(text).toContain("数据结构 1 班");
    expect(text).toContain("小王");
    expect(text).toContain("120");
    expect(text).toContain("未记录");
  });

  it("opens one student's map only when asked, and shows chapter mastery", async () => {
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    expect(studentMap).not.toHaveBeenCalled();

    await wrapper.findAll("button").find((button) => button.text().includes("学习图谱"))?.trigger("click");
    await flushPromises();

    expect(studentMap).toHaveBeenCalledWith(7);
    const text = wrapper.text();
    expect(text).toContain("整体掌握 55%");
    expect(text).toContain("绪论");
    expect(text).toContain("62%");
    // A chapter with nothing recorded reads as a dash, not as zero.
    expect(text).toContain("—");
  });

  it("says so when the teacher has no class yet", async () => {
    classes.mockResolvedValue([]);
    const wrapper = mount(TeacherHomeView);
    await flushPromises();

    expect(wrapper.text()).toContain("你名下还没有班级");
  });

  it("opens the lessons of a chapter only when the teacher asks for them", async () => {
    studentMap.mockResolvedValue({
      generatedAt: "2026-10-23T08:00:00Z",
      nodes: [
        { id: "root", parentId: null, level: 0, title: "数据结构", mastery: 0.4, confidence: 0.5, status: "ESTIMATED" },
        { id: "01-introduction", parentId: "root", level: 1, title: "绪论", mastery: 0.4, confidence: 0.5, status: "ESTIMATED" },
        { id: "01-introduction:01-01", parentId: "01-introduction", level: 2, title: "数据结构的基础概念", mastery: 0.8, confidence: 0.6, status: "ESTIMATED" },
        { id: "01-introduction:01-02", parentId: "01-introduction", level: 2, title: "数据结构的内容", mastery: null, confidence: 0, status: "NOT_STARTED" },
      ],
    });
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text().includes("学习图谱"))?.trigger("click");
    await flushPromises();

    // A chapter can hold dozens of lessons, so they stay folded until one is opened.
    expect(wrapper.text()).not.toContain("数据结构的基础概念");
    const toggle = wrapper.get(".tp__lessons-toggle");
    expect(toggle.text()).toBe("2 个课时");
    expect(toggle.attributes("aria-expanded")).toBe("false");

    await toggle.trigger("click");

    expect(wrapper.text()).toContain("数据结构的基础概念");
    expect(wrapper.text()).toContain("80%");
    expect(wrapper.text()).toContain("未开始");
  });

  it("says the student has no records yet instead of drawing empty chapters", async () => {
    studentMap.mockResolvedValue({
      generatedAt: "2026-10-23T08:00:00Z",
      nodes: [
        { id: "root", parentId: null, level: 0, title: "数据结构", mastery: null, confidence: 0, status: "NOT_STARTED" },
        { id: "01-introduction", parentId: "root", level: 1, title: "绪论", mastery: null, confidence: 0, status: "NOT_STARTED" },
        { id: "01-introduction:01-01", parentId: "01-introduction", level: 2, title: "数据结构的基础概念", mastery: null, confidence: 0, status: "NOT_STARTED" },
      ],
    });
    const wrapper = mount(TeacherHomeView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text().includes("学习图谱"))?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("还没有任何学习记录");
    expect(wrapper.find(".tp__chapters").exists()).toBe(false);
  });
});
