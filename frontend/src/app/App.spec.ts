import { afterEach, describe, expect, it } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import App from "./App.vue";
import { setLocale } from "../shared/i18n/locale";

afterEach(() => {
  setLocale("zh-CN");
});

describe("App bootstrap", () => {
  it("does not flash the learning shell while the initial route is resolving", async () => {
    const adminPage = { template: "<div data-testid='admin-content'>admin content</div>" };
    const history = createMemoryHistory();
    history.push("/admin");
    const router = createRouter({
      history,
      routes: [{ path: "/admin", component: adminPage, meta: { layout: "admin" } }],
    });
    router.beforeEach(async () => {
      await new Promise((resolve) => setTimeout(resolve, 25));
    });

    const wrapper = mount(App, { global: { plugins: [router] } });

    expect(wrapper.get(".morphing-square-loader").attributes("aria-label")).toBe("正在准备页面");
    expect(wrapper.find('[aria-label="学习端导航"]').exists()).toBe(false);

    await router.isReady();
    await flushPromises();

    expect(wrapper.get("[data-testid='admin-content']").text()).toBe("admin content");
    expect(wrapper.find('[aria-label="学习端导航"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("keeps the browser title aligned with route and locale", async () => {
    const history = createMemoryHistory();
    history.push("/login");
    const router = createRouter({
      history,
      routes: [
        { path: "/login", name: "login", component: { template: "<div />" }, meta: { layout: "auth" } },
        { path: "/user/code", name: "user-code", component: { template: "<div />" }, meta: { layout: "workbench" } },
      ],
    });
    const wrapper = mount(App, { global: { plugins: [router] } });

    await router.isReady();
    await flushPromises();
    expect(document.title).toBe("登录 | Structify");

    setLocale("en-US");
    await flushPromises();
    expect(document.title).toBe("Sign in | Structify");

    await router.push("/user/code");
    await flushPromises();
    expect(document.title).toBe("C Compiler | Structify");
    wrapper.unmount();
  });
});
