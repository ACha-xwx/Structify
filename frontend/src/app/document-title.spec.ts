import { describe, expect, it } from "vitest";
import { documentTitleForRoute } from "./document-title";

describe("documentTitleForRoute", () => {
  it.each([
    [{ name: "home", path: "/", meta: {} }, "首页 | 数筑 · Structify", "Home | 数筑 · Structify"],
    [{ name: "begin", path: "/begin", meta: {} }, "学习入口 | 数筑 · Structify", "Learning entry | 数筑 · Structify"],
    [{ name: "compiler", path: "/compiler", meta: {} }, "C 编辑器 | 数筑 · Structify", "C Compiler | 数筑 · Structify"],
    [{ name: "chat", path: "/chat", meta: {} }, "课程问答 | 数筑 · Structify", "Ask the course | 数筑 · Structify"],
    [{ name: "user-code", path: "/user/code", meta: {} }, "C 编译器 | 数筑 · Structify", "C Compiler | 数筑 · Structify"],
    [{ name: "user-progress", path: "/user/progress", meta: {} }, "学习复盘 | 数筑 · Structify", "Review | 数筑 · Structify"],
    [{ name: "admin-settings", path: "/admin/settings", meta: {} }, "模型设置 | 数筑 · Structify", "Model settings | 数筑 · Structify"],
  ])("maps %o in both locales", (route, zh, en) => {
    expect(documentTitleForRoute(route, "zh-CN")).toBe(zh);
    expect(documentTitleForRoute(route, "en-US")).toBe(en);
  });

  it("keeps the brand fixed and falls back safely for unknown routes", () => {
    expect(documentTitleForRoute({ name: "unknown", path: "/user/unknown", meta: {} }, "en-US")).toBe("Workbench | 数筑 · Structify");
    expect(documentTitleForRoute({ path: "/something-new", meta: {} }, "zh-CN")).toBe("首页 | 数筑 · Structify");
  });
});
