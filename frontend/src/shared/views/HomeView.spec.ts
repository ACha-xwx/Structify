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

    expect(wrapper.findAll(".choice__name").map((node) => node.text())).toEqual(["课堂", "动画学习"]);
    expect(wrapper.findAll(".choice").map((node) => node.attributes("href"))).toEqual(["/classroom", "/animation"]);
    // Nothing classroom-shaped is mounted here: no lesson picker, and no session was opened for us.
    expect(wrapper.find("select").exists()).toBe(false);
    wrapper.unmount();
  });

  it("offers the last lesson only as an explicit choice", async () => {
    localStorage.setItem("structify.classroom.last", "session-9");

    const { wrapper } = mountHome();
    await flushPromises();

    expect(wrapper.get(".entry__resume").text()).toBe("继续上次课堂");
    expect(wrapper.get(".entry__resume").attributes("href")).toBe("/classroom?session=session-9");
    wrapper.unmount();
  });

  it("hides the resume link when there is no lesson to go back to", async () => {
    const { wrapper } = mountHome();
    await flushPromises();

    expect(wrapper.find(".entry__resume").exists()).toBe(false);
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
    expect(wrapper.findAll(".choice__name").map((node) => node.text())).toEqual(["Classroom", "Animation lab"]);
    expect(wrapper.get(".entry__resume").text()).toBe("Resume last class");
    wrapper.unmount();
  });
});
