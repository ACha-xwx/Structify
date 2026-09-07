import { describe, expect, it } from "vitest";
import { documentTitleForRoute } from "./document-title";

describe("documentTitleForRoute", () => {
  it.each([
    [{ name: "home", path: "/", meta: {} }, "首页 | Structify", "Home | Structify"],
    [{ name: "user-code", path: "/user/code", meta: {} }, "C 编译器 | Structify", "C Compiler | Structify"],
    [{ name: "user-progress", path: "/user/progress", meta: {} }, "学习复盘 | Structify", "Review | Structify"],
    [{ name: "admin-settings", path: "/admin/settings", meta: {} }, "模型设置 | Structify", "Model settings | Structify"],
  ])("maps %o in both locales", (route, zh, en) => {
    expect(documentTitleForRoute(route, "zh-CN")).toBe(zh);
    expect(documentTitleForRoute(route, "en-US")).toBe(en);
  });

  it("keeps Structify fixed and falls back safely for unknown routes", () => {
    expect(documentTitleForRoute({ name: "unknown", path: "/user/unknown", meta: {} }, "en-US")).toBe("Workbench | Structify");
    expect(documentTitleForRoute({ path: "/something-new", meta: {} }, "zh-CN")).toBe("首页 | Structify");
  });
});
