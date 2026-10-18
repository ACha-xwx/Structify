import { beforeEach, describe, expect, it, vi } from "vitest";
import { RouterLinkStub, flushPromises, mount } from "@vue/test-utils";
import UserHomeView from "./UserHomeView.vue";
import { DEFAULT_LOCALE, setLocale } from "../../shared/i18n/locale";
import type { ClassroomBookmark } from "../../shared/types/contracts";

const api = vi.hoisted(() => ({ listBookmarks: vi.fn(), deleteBookmark: vi.fn() }));
const navigation = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock("../runtime", () => ({ userApi: api }));
vi.mock("vue-router", () => ({ useRouter: () => ({ push: navigation.push }) }));

function bookmark(over: Partial<ClassroomBookmark>): ClassroomBookmark {
  return {
    id: 1, sessionId: "session-1", lessonId: "lesson-1", lessonTitle: "栈与队列基础",
    chapterId: "03-stack-queue", stepIndex: 0, slideId: "slide-1", title: "栈只在顶端进出",
    createdAt: "2026-10-08T09:12:00Z", ...over,
  };
}

function mountPage() {
  return mount(UserHomeView, { global: { stubs: { RouterLink: RouterLinkStub } } });
}

beforeEach(() => {
  setLocale(DEFAULT_LOCALE);
  vi.resetAllMocks();
  api.listBookmarks.mockResolvedValue([]);
});

describe("personal page", () => {
  it("says so when nothing has been saved", async () => {
    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.get(".profile__status").text()).toContain("还没有收藏");
    expect(wrapper.find(".profile__group").exists()).toBe(false);
    wrapper.unmount();
  });

  it("groups saved steps under their lesson and opens the page a bookmark names", async () => {
    api.listBookmarks.mockResolvedValue([
      bookmark({ id: 2, stepIndex: 4, title: "出栈顺序", slideId: "slide-9" }),
      bookmark({ id: 1 }),
      bookmark({ id: 3, lessonId: "lesson-2", lessonTitle: "二叉树", sessionId: "session-2", stepIndex: 1, title: "前序遍历" }),
    ]);

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.findAll(".profile__lesson").map((node) => node.text())).toEqual(["栈与队列基础", "二叉树"]);
    // Within a lesson the steps read in the order they were taught, not the order they were saved.
    expect(wrapper.findAll(".profile__title").map((node) => node.text())).toEqual(["栈只在顶端进出", "出栈顺序", "前序遍历"]);
    expect(wrapper.findAll(".profile__step").map((node) => node.text())).toEqual(["第 1 步", "第 5 步", "第 2 步"]);

    await wrapper.findAll(".profile__action")[0].trigger("click");
    expect(navigation.push).toHaveBeenCalledWith({ path: "/courseware", query: { lesson: "lesson-1", slide: "slide-1" } });

    const back = wrapper.findAll(".profile__action").filter((node) => node.text() === "回到这一节");
    await back[0].trigger("click");
    expect(navigation.push).toHaveBeenCalledWith({ path: "/classroom", query: { session: "session-1" } });
    wrapper.unmount();
  });

  it("drops a bookmark the learner removes, and reports a failure instead of pretending", async () => {
    api.listBookmarks.mockResolvedValue([bookmark({ id: 1 }), bookmark({ id: 2, stepIndex: 1, title: "第二个" })]);
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.findAll('[aria-label="删除收藏"]')[0].trigger("click");
    await flushPromises();
    expect(api.deleteBookmark).toHaveBeenCalledWith(1);
    expect(wrapper.findAll(".profile__item")).toHaveLength(1);
    expect(wrapper.find(".profile__status").exists()).toBe(false);

    api.deleteBookmark.mockRejectedValue(new Error("offline"));
    await wrapper.findAll('[aria-label="删除收藏"]')[0].trigger("click");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("收藏没能载入");
    expect(wrapper.findAll(".profile__item")).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps the page usable when the list cannot be read", async () => {
    api.listBookmarks.mockRejectedValue(new Error("offline"));
    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("收藏没能载入");
    expect(wrapper.find(".profile__group").exists()).toBe(false);
    wrapper.unmount();
  });
});
