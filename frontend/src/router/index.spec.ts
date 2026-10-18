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

    expect(home?.path).toBe("/");
    expect(home?.meta?.requiresAuth).not.toBe(true);
    expect(home?.meta?.layout).toBe("workbench");
    expect(begin?.path).toBe("/begin");
    expect(begin?.meta?.requiresAuth).toBe(true);
    expect(begin?.meta?.layout).toBe("workbench");
    expect(classroom?.path).toBe("/classroom");
    expect(home?.component).not.toBe(classroom?.component);
    expect(begin?.component).not.toBe(home?.component);
  });
});
