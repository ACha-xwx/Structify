import { describe, expect, it } from "vitest";
import { routes } from "./index";

/**
 * The public landing page, signed-in chooser and classroom have separate paths.
 */
describe("workbench entry routes", () => {
  it("loads page code on demand so opening chat does not download the compiler or admin pages", () => {
    for (const route of routes.filter((item) => item.component)) {
      expect(typeof route.component, String(route.name)).toBe('function');
    }
  });
  it("keeps the root public and moves the protected chooser to /begin", () => {
    const home = routes.find((route) => route.name === "home");
    const begin = routes.find((route) => route.name === "begin");
    const classroom = routes.find((route) => route.name === "classroom");
    const studyroom = routes.find((route) => route.name === "studyroom");
    const courseChat = routes.find((route) => route.name === "chat");
    const studyroomEntry = routes.find((route) => route.name === "studyroom-new");
    const studyroomOutline = routes.find((route) => route.name === "studyroom-outline");

    expect(home?.path).toBe("/");
    expect(home?.meta?.requiresAuth).not.toBe(true);
    expect(home?.meta?.layout).toBe("workbench");
    expect(begin?.path).toBe("/begin");
    expect(begin?.meta?.requiresAuth).toBe(true);
    expect(begin?.meta?.layout).toBe("workbench");
    expect(classroom?.path).toBe("/classroom");
    expect(home?.component).not.toBe(classroom?.component);
    expect(begin?.component).not.toBe(home?.component);
    expect(studyroom?.path).toBe("/studyroom/:id");
    expect(studyroom?.meta?.requiresAuth).toBe(true);
    expect(courseChat?.path).toBe("/chat");
    expect(courseChat?.component).not.toBe(studyroomEntry?.component);
    expect(studyroomEntry?.path).toBe("/studyroom");
    expect(studyroomOutline?.path).toBe("/studyroom/outlines/:draftId");
    expect(routes.some((route) => route.path === "/chat-legacy")).toBe(false);
  });

  it("exposes the resource demo as a local preview route in development", () => {
    const demo = routes.find((route) => route.name === "studyroom-demo-new");

    expect(Boolean(demo)).toBe(import.meta.env.DEV);
    if (demo) {
      expect(demo.meta?.requiresAuth).toBe(true);
      expect(demo.meta?.allowsLocalPreview).toBeUndefined();
      expect(demo.redirect).toBe("/studyroom");
      expect(demo.props).not.toEqual({ mock: true });
    }
  });
});
