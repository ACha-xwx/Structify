import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import AdminClassesView from "./AdminClassesView.vue";

const classes = vi.hoisted(() => vi.fn());
const createClass = vi.hoisted(() => vi.fn());
const classMembers = vi.hoisted(() => vi.fn());
const addClassMember = vi.hoisted(() => vi.fn());
const removeClassMember = vi.hoisted(() => vi.fn());
const deleteClass = vi.hoisted(() => vi.fn());

vi.mock("../api", () => ({
  adminApi: { classes, createClass, classMembers, addClassMember, removeClassMember, deleteClass },
  adminErrorMessage: (failure: unknown, action: string) => `${action}未完成（${(failure as { code?: string }).code ?? "NETWORK_ERROR"}）`,
  formatDate: (value?: string | null) => value ?? "未记录",
}));

const oneClass = {
  id: "class-1",
  name: "数据结构 1 班",
  term: "2026 秋",
  students: 1,
  teachers: ["李老师"],
};

const roster = [
  { userId: 65, username: "学生甲", email: "a@example.test", roleInClass: "STUDENT" as const, joinedAt: "2026-10-23T08:00:00Z" },
  { userId: 66, username: "李老师", email: "t@example.test", roleInClass: "TEACHER" as const, joinedAt: "2026-10-23T08:05:00Z" },
];

async function mountPage() {
  const wrapper = mount(AdminClassesView);
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  classes.mockResolvedValue([oneClass]);
  classMembers.mockResolvedValue(roster);
  window.confirm = () => true;
});

describe("AdminClassesView", () => {
  it("lists the classes and leaves the roster closed until one is picked", async () => {
    const wrapper = await mountPage();

    expect(classes).toHaveBeenCalledTimes(1);
    expect(classMembers).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("数据结构 1 班");
    expect(wrapper.text()).toContain("李老师");
    // The roster is the sensitive half and is only read for the class somebody actually opened.
    expect(wrapper.text()).not.toContain("a@example.test");
    wrapper.unmount();
  });

  it("opens the roster of the class that was picked", async () => {
    const wrapper = await mountPage();

    await wrapper.findAll("tbody tr")[0].trigger("click");
    await flushPromises();

    expect(classMembers).toHaveBeenCalledWith("class-1");
    expect(wrapper.text()).toContain("a@example.test");
    expect(wrapper.text()).toContain("学生");
    expect(wrapper.text()).toContain("教师");
    wrapper.unmount();
  });

  it("creates a class and opens it so the teacher can be added straight away", async () => {
    createClass.mockResolvedValue({ ...oneClass, id: "class-2", name: "数据结构 2 班", students: 0, teachers: [] });
    classes
      .mockResolvedValueOnce([oneClass])
      .mockResolvedValueOnce([oneClass, { ...oneClass, id: "class-2", name: "数据结构 2 班", students: 0, teachers: [] }]);
    classMembers.mockResolvedValue([]);
    const wrapper = await mountPage();

    await wrapper.get('input[placeholder="如：数据结构 1 班"]').setValue("数据结构 2 班");
    await wrapper.get('input[placeholder="如：2026 秋"]').setValue("2027 春");
    await wrapper.get('[data-testid="create-class"]').trigger("click");
    await flushPromises();

    expect(createClass).toHaveBeenCalledWith({ name: "数据结构 2 班", term: "2027 春" });
    expect(classMembers).toHaveBeenCalledWith("class-2");
    expect(wrapper.text()).toContain("记得把任课老师加进去");
    wrapper.unmount();
  });

  it("refuses a blank class name without calling the service", async () => {
    const wrapper = await mountPage();

    await wrapper.get('input[placeholder="如：数据结构 1 班"]').setValue("   ");
    await wrapper.get('[data-testid="create-class"]').trigger("click");
    await flushPromises();

    expect(createClass).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("请先填写班级名称");
    wrapper.unmount();
  });

  it("adds a teacher by email, which is what grants them the right to see the class", async () => {
    addClassMember.mockResolvedValue(roster[1]);
    const wrapper = await mountPage();
    await wrapper.findAll("tbody tr")[0].trigger("click");
    await flushPromises();

    await wrapper.get('input[placeholder="learner@example.com"]').setValue("teacher@example.test");
    await wrapper.get("select").setValue("TEACHER");
    await wrapper.get('[data-testid="add-class-member"]').trigger("click");
    await flushPromises();

    expect(addClassMember).toHaveBeenCalledWith("class-1", { identifier: "teacher@example.test", roleInClass: "TEACHER" });
    expect(wrapper.text()).toContain("该教师现在可以看到本班学生");
    wrapper.unmount();
  });

  it("removes a member and takes the class away when the administrator asks", async () => {
    const wrapper = await mountPage();
    await wrapper.findAll("tbody tr")[0].trigger("click");
    await flushPromises();

    const removeButtons = wrapper.findAll("button.cls__link").filter((button) => button.text() === "移出");
    await removeButtons[0].trigger("click");
    await flushPromises();
    expect(removeClassMember).toHaveBeenCalledWith("class-1", 65);

    await wrapper.findAll("button.cls__link").filter((button) => button.text() === "删除")[0].trigger("click");
    await flushPromises();
    expect(deleteClass).toHaveBeenCalledWith("class-1");
    wrapper.unmount();
  });

  it("says which class read failed instead of showing an empty page", async () => {
    classes.mockRejectedValue({ code: "NETWORK_ERROR" });
    const wrapper = await mountPage();

    expect(wrapper.text()).toContain("读取班级列表未完成（NETWORK_ERROR）");
    wrapper.unmount();
  });
});
