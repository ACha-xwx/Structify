import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import AppShell from "./AppShell.vue";

const authMock = vi.hoisted(() => ({
  state: {
    status: "offline" as string,
    user: { id: 7, email: "student@example.com", roles: ["STUDENT"] },
    capabilities: null,
    error: null,
  },
  logout: vi.fn(async () => undefined),
}));

vi.mock("../providers/runtime", () => ({ auth: authMock }));

async function mountAdminShell() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/admin", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/users", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/reviews", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/tasks", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/audit", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/settings", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/mail", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/admin/sandbox", component: { template: "<div />" }, meta: { layout: "admin" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/admin");
  await router.isReady();
  authMock.state.user = { id: 1, email: "admin@example.com", roles: ["ADMIN"] };
  return { router, wrapper: mount(AppShell, { attachTo: document.body, global: { plugins: [router] }, slots: { default: "<p>content</p>" } }) };
}

async function mountWorkbenchShell() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", name: "home", component: { template: "<div />" } },
      { path: "/user/home", component: { template: "<div />" }, meta: { layout: "workbench" } },
      { path: "/login", component: { template: "<div />" } },
    ],
  });
  await router.push("/");
  await router.isReady();
  return { router, wrapper: mount(AppShell, { attachTo: document.body, global: { plugins: [router] }, slots: { default: "<p>content</p>" } }) };
}

describe("AppShell retained session presentation", () => {
  beforeEach(() => {
    authMock.state.status = "offline";
    authMock.state.user = { id: 7, email: "student@example.com", roles: ["STUDENT"] };
    authMock.logout.mockClear();
  });

  it("does not mount the retired header and duplicate theme switch on workbench surfaces", async () => {
    const { wrapper } = await mountWorkbenchShell();

    expect(wrapper.find(".app-header").exists()).toBe(false);
    expect(wrapper.find(".app-footer").exists()).toBe(false);
    expect(wrapper.get("main.app-main--workbench").text()).toContain("content");
    wrapper.unmount();
  });

  it("exposes every admin work area from the admin navigation", async () => {
    const { wrapper } = await mountAdminShell();

    const sidebar = wrapper.get("aside[data-layout=\"admin-sidebar\"]");
    const nav = sidebar.get("nav[aria-label=\"管理端导航\"]");
    expect(nav.find("a[href=\"/admin\"]").exists()).toBe(true);
    expect(nav.find("a[href=\"/admin/users\"]").exists()).toBe(true);
    expect(nav.find("a[href=\"/admin/reviews\"]").exists()).toBe(true);
    expect(nav.find("a[href=\"/admin/tasks\"]").exists()).toBe(true);
    expect(nav.find("a[href=\"/admin/audit\"]").exists()).toBe(true);
    expect(nav.find("a[href=\"/admin/settings\"]").exists()).toBe(true);
    expect(nav.find("a[href=\"/admin/mail\"]").exists()).toBe(true);
    expect(wrapper.find("header nav[aria-label=\"管理端导航\"]").exists()).toBe(false);
    wrapper.unmount();
  });

  it("uses compact semantic icons instead of single-character navigation thumbnails", async () => {
    const { wrapper } = await mountAdminShell();
    const marks = wrapper.get("aside[data-layout=\"admin-sidebar\"] nav").findAll(".admin-nav__mark");
    const icons = wrapper.get("aside[data-layout=\"admin-sidebar\"] nav").findAll("svg.admin-nav__icon");

    expect(marks).toHaveLength(8);
    expect(icons).toHaveLength(8);
    expect(marks.every((mark) => mark.text().trim() === "")).toBe(true);
    expect(icons.every((icon) => icon.attributes("aria-hidden") === "true")).toBe(true);
    wrapper.unmount();
  });

  it("lets keyboard users dismiss the mobile admin navigation with Escape", async () => {
    const { wrapper } = await mountAdminShell();
    const toggle = wrapper.get(".admin-menu-toggle");

    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    expect(wrapper.find("#admin-mobile-navigation").exists()).toBe(true);

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await wrapper.vm.$nextTick();

    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(wrapper.find("#admin-mobile-navigation").exists()).toBe(false);
    expect(document.activeElement).toBe(toggle.element);
    wrapper.unmount();
  });

  it("locks background scrolling while the mobile admin navigation is open", async () => {
    const previousBodyOverflow = document.body.style.overflow;
    const previousRootOverflow = document.documentElement.style.overflow;
    document.body.style.overflow = "auto";
    document.documentElement.style.overflow = "scroll";

    const { wrapper } = await mountAdminShell();
    try {
      await wrapper.get(".admin-menu-toggle").trigger("click");

      expect(document.body.style.overflow).toBe("hidden");
      expect(document.documentElement.style.overflow).toBe("hidden");

      await wrapper.get(".admin-mobile-nav-layer__close").trigger("click");

      expect(document.body.style.overflow).toBe("auto");
      expect(document.documentElement.style.overflow).toBe("scroll");
    } finally {
      wrapper.unmount();
      document.body.style.overflow = previousBodyOverflow;
      document.documentElement.style.overflow = previousRootOverflow;
    }
  });

  it("keeps Tab and Shift+Tab focus inside the mobile admin navigation", async () => {
    const { wrapper } = await mountAdminShell();
    try {
      await wrapper.get(".admin-menu-toggle").trigger("click");

      const layer = wrapper.get("#admin-mobile-navigation").element;
      const focusableElements = Array.from(layer.querySelectorAll<HTMLElement>([
        "a[href]",
        "button:not([disabled])",
        "input:not([disabled]):not([type='hidden'])",
        "select:not([disabled])",
        "textarea:not([disabled])",
        "[tabindex]:not([tabindex='-1'])",
      ].join(", "))).filter((element) => element.tabIndex >= 0);
      const firstFocusableElement = focusableElements[0];
      const lastFocusableElement = focusableElements[focusableElements.length - 1];
      lastFocusableElement.focus();

      const tabEvent = new KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true });
      window.dispatchEvent(tabEvent);

      expect(tabEvent.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(firstFocusableElement);

      firstFocusableElement.focus();
      const reverseTabEvent = new KeyboardEvent("keydown", { key: "Tab", shiftKey: true, bubbles: true, cancelable: true });
      window.dispatchEvent(reverseTabEvent);

      expect(reverseTabEvent.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(lastFocusableElement);
    } finally {
      wrapper.unmount();
    }
  });

  it("uses the requested collapsed desktop sidebar and expands it for pointer or keyboard access", async () => {
    const { wrapper } = await mountAdminShell();
    const sidebar = wrapper.get("aside[data-layout=\"admin-sidebar\"]");
    const workspace = wrapper.get(".admin-workspace");
    const pin = wrapper.get("button.admin-sidebar__pin");

    expect(workspace.classes()).not.toContain("is-sidebar-expanded");
    expect(pin.attributes("aria-hidden")).toBe("true");
    expect(pin.attributes("tabindex")).toBe("-1");
    expect(pin.attributes("disabled")).toBeDefined();
    expect(pin.attributes("aria-pressed")).toBe("false");
    expect(pin.html()).toContain("M11.9999 17V21");
    await sidebar.trigger("mouseenter");
    expect(workspace.classes()).toContain("is-sidebar-expanded");
    expect(pin.attributes("aria-hidden")).toBe("false");
    expect(pin.attributes("tabindex")).toBe("0");
    expect(pin.attributes("disabled")).toBeUndefined();
    await sidebar.trigger("mouseleave");
    expect(workspace.classes()).not.toContain("is-sidebar-expanded");
    expect(pin.attributes("aria-hidden")).toBe("true");
    expect(pin.attributes("tabindex")).toBe("-1");
    expect(pin.attributes("disabled")).toBeDefined();
    wrapper.unmount();
  });

  it("pins the desktop sidebar and keeps the pin control accessible only while expanded", async () => {
    const { wrapper } = await mountAdminShell();
    const sidebar = wrapper.get("aside[data-layout=\"admin-sidebar\"]");
    const workspace = wrapper.get(".admin-workspace");

    await sidebar.trigger("mouseenter");
    const pin = wrapper.get("button.admin-sidebar__pin");
    expect(sidebar.classes()).toContain("admin-sidebar--fixed");
    expect(pin.attributes("aria-label")).toBe("固定管理端导航");
    expect(pin.attributes("title")).toBe("固定侧边栏");
    await pin.trigger("click");
    expect(pin.attributes("aria-pressed")).toBe("true");
    expect(pin.attributes("aria-label")).toBe("取消固定管理端导航");
    expect(pin.attributes("title")).toBe("取消固定侧边栏");
    expect(workspace.classes()).toContain("is-sidebar-pinned");
    expect(workspace.classes()).toContain("is-sidebar-expanded");

    await sidebar.trigger("mouseleave");
    expect(workspace.classes()).toContain("is-sidebar-expanded");
    expect(pin.attributes("aria-hidden")).toBe("false");

    await pin.trigger("click");
    expect(pin.attributes("aria-pressed")).toBe("false");
    expect(workspace.classes()).not.toContain("is-sidebar-pinned");
    expect(workspace.classes()).not.toContain("is-sidebar-expanded");
    expect(pin.attributes("aria-hidden")).toBe("true");
    expect(pin.attributes("tabindex")).toBe("-1");
    wrapper.unmount();
  });

  it("uses the liquid metal logout action in the admin rail", async () => {
    const { wrapper } = await mountAdminShell();
    const logout = wrapper.get(".admin-sidebar__signout");
    expect(logout.find(".liquid-metal-button").exists()).toBe(true);
    expect(logout.get(".liquid-metal-button__native").attributes("aria-label")).toBe("退出");
    expect(logout.find(".admin-signout-glyph").exists()).toBe(true);
    expect(logout.html()).toContain("M13 8L9 12");
    wrapper.unmount();
  });
});
