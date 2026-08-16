import { describe, expect, it } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import App from "./App.vue";

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
});
