import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import HomeView from "./HomeView.vue";
import { DEFAULT_LOCALE, setLocale } from "../i18n/locale";

const session = vi.hoisted(() => ({
  logout: vi.fn(),
  state: { user: null as { email?: string } | null },
}));

vi.mock("../../app/providers/runtime", () => ({ auth: session }));

function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/classroom", component: { template: "<div />" } },
      { path: "/animation", component: { template: "<div />" } },
      { path: "/compiler", component: { template: "<div />" } },
      { path: "/chat", component: { template: "<div />" } },
      { path: "/user", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  return { router, wrapper: mount(HomeView, { global: { plugins: [router] } }) };
}

beforeEach(() => {
  localStorage.clear();
  setLocale(DEFAULT_LOCALE);
  session.logout.mockReset();
  session.state.user = null;
});

describe("entry page", () => {
  it("asks which surface to open instead of starting a lesson", async () => {
    const { wrapper } = mountHome();
    await flushPromises();

    expect(wrapper.findAll(".choice__name").map((node) => node.text())).toEqual(["课堂", "动画学习", "C 编辑器", "课程问答"]);
    expect(wrapper.findAll(".choice__destination").map((node) => node.attributes("href"))).toEqual(["/classroom", "/animation", "/compiler", "/chat"]);
    // Nothing classroom-shaped is mounted here: no lesson picker, and no session was opened for us.
    expect(wrapper.find("select").exists()).toBe(false);
    wrapper.unmount();
  });

  it("offers the profile placeholder instead of a resume link even when a lesson is saved", async () => {
    localStorage.setItem("structify.classroom.last", "session-9");

    const { wrapper } = mountHome();
    await flushPromises();

    expect(wrapper.get(".entry__profile").text()).toBe("个人主页");
    expect(wrapper.get(".entry__profile").attributes("href")).toBe("/user");
    expect(wrapper.find(".entry__resume").exists()).toBe(false);
    wrapper.unmount();
  });

  it("navigates from each silver entry button", async () => {
    const { router, wrapper } = mountHome();
    await flushPromises();

    const paths = ["/classroom", "/animation", "/compiler", "/chat"];
    const controls = wrapper.findAll(".choice__go button");
    expect(controls).toHaveLength(4);
    for (const [index, path] of paths.entries()) {
      await controls[index].trigger("click");
      await flushPromises();
      expect(router.currentRoute.value.path).toBe(path);
    }
    wrapper.unmount();
  });

  it("signs out from the entry page", async () => {
    session.state.user = { email: "learner@example.com" };

    const { router, wrapper } = mountHome();
    await flushPromises();

    await wrapper.get(".entry__signout").trigger("click");
    await flushPromises();

    expect(session.logout).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value.path).toBe("/login");
    wrapper.unmount();
  });

  it("repaints in English the moment the language switch flips", async () => {
    // The switch used to flip a stored locale that no surface ever read, so the page stayed Chinese.
    localStorage.setItem("structify.classroom.last", "session-9");
    const { wrapper } = mountHome();
    await flushPromises();

    expect(wrapper.get(".entry__title").text()).toBe("从哪开始？");

    setLocale("en-US");
    await nextTick();

    expect(wrapper.get(".entry__title").text()).toBe("Where do you want to start?");
    expect(wrapper.findAll(".choice__name").map((node) => node.text())).toEqual(["Classroom", "Animation lab", "C editor", "Ask the course"]);
    expect(wrapper.get(".entry__profile").text()).toBe("My profile");
    wrapper.unmount();
  });
});
