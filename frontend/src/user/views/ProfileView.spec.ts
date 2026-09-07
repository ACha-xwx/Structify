import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setLocale } from "../../shared/i18n/locale";

const { authMock } = vi.hoisted(() => ({
  authMock: {
    state: {
      user: null as null | { id: number; email: string; roles: Array<"STUDENT" | "TEACHER" | "ADMIN"> },
      status: "anonymous",
    },
    logout: vi.fn(),
  },
}));

vi.mock("../../app/providers/runtime", () => ({ auth: authMock }));

import ProfileView from "./ProfileView.vue";

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/user/profile", component: { template: "<div />" } },
      { path: "/user/home", component: { template: "<div />" } },
      { path: "/user/chapters", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/user/profile");
  await router.isReady();
  return mount(ProfileView, {
    global: {
      plugins: [router],
      stubs: { UserFrame: { template: "<div><slot /><slot name=\"rail\" /></div>" } },
    },
  });
}

describe("ProfileView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setLocale("zh-CN");
    authMock.state.user = null;
    authMock.state.status = "anonymous";
  });

  it("为游客显示可行动的账户边界，不伪称正在展示已验证资料", async () => {
    const wrapper = await mountView();

    expect(wrapper.text()).toContain("当前没有可用会话");
    expect(wrapper.text()).toContain("游客可以继续学习");
    expect(wrapper.text()).toContain("游客可用");
    expect(wrapper.text()).not.toContain("当前显示已验证的账户信息。");
    expect(wrapper.get('a[href="/user/home"]').text()).toBe("进入学习台");
    expect(wrapper.get('a[href="/user/chapters"]').text()).toBe("浏览课程");
  });

  it("仅在会话存在时说明账户资料已经验证", async () => {
    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    authMock.state.status = "authenticated";
    const wrapper = await mountView();

    expect(wrapper.text()).toContain("learner@example.com");
    expect(wrapper.text()).toContain("当前显示已验证的账户信息。");
    expect(wrapper.text()).not.toContain("游客可以继续学习");
  });

  it("英文 locale 覆盖账户页面的游客与会话边界文案", async () => {
    setLocale("en-US");
    const guestWrapper = await mountView();

    expect(guestWrapper.text()).toContain("No active session");
    expect(guestWrapper.text()).toContain("Guests can keep learning");
    expect(guestWrapper.text()).toContain("Guest access");
    expect(guestWrapper.get('a[href="/user/home"]').text()).toBe("Open workbench");
    expect(guestWrapper.get('a[href="/user/chapters"]').text()).toBe("Browse courses");
    expect(guestWrapper.text()).not.toContain("当前没有可用会话");

    authMock.state.user = { id: 7, email: "learner@example.com", roles: ["STUDENT"] };
    authMock.state.status = "authenticated";
    const accountWrapper = await mountView();

    expect(accountWrapper.text()).toContain("Current account");
    expect(accountWrapper.text()).toContain("Student");
    expect(accountWrapper.text()).toContain("Session status");
    expect(accountWrapper.text()).toContain("The account information shown here has been verified.");
  });
});
