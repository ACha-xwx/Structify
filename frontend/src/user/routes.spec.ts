import { describe, expect, it } from "vitest";
import { userRoutes } from "./routes";

describe("用户端路由", () => {
  it("覆盖学习闭环的主要页面", () => {
    const paths = userRoutes.map((route) => route.path);

    expect(paths).toEqual(expect.arrayContaining([
      "/user/home",
      "/user/chapters",
      "/user/chapters/:chapterId",
      "/user/resources/:resourceId",
      "/user/knowledge",
      "/user/coach",
      "/user/classroom",
      "/user/animation",
      "/user/code",
      "/user/progress",
      "/user/review",
      "/user/profile",
    ]));
  });

  it("保留历史学习入口，并将 /user 统一到学习工作台", () => {
    const legacyRoot = userRoutes.find((route) => route.path === "/user");
    const legacyHome = userRoutes.find((route) => route.path === "/user/home");

    expect(legacyRoot?.redirect).toBe("/user/home");
    expect(legacyHome?.name).toBe("user-home");
    expect(legacyHome?.component).toBeDefined();
  });

  it("将旧复盘书签重定向到当前复盘页面", () => {
    const review = userRoutes.find((route) => route.path === "/user/review");

    expect(review?.redirect).toBe("/user/progress");
    expect(review?.component).toBeUndefined();
  });

  it("将章节深链统一回 Runtime 课程地图并保留来源", () => {
    const chapterDetail = userRoutes.find((route) => route.path === "/user/chapters/:chapterId");
    const redirect = chapterDetail?.redirect;

    expect(chapterDetail?.component).toBeUndefined();
    expect(redirect).toBeTypeOf("function");
    expect((redirect as (to: { params: Record<string, string>; query: Record<string, string> }) => unknown)({
      params: { chapterId: "sequential-list" },
      query: { from: "workbench" },
    })).toEqual({
      path: "/user/chapters",
      query: { chapterId: "sequential-list", from: "workbench" },
    });
  });

  it("将课件标记为沉浸式学习工作台表面", () => {
    const presentation = userRoutes.find((route) => route.path === "/user/presentation");

    expect(presentation?.meta).toMatchObject({ layout: "workbench" });
  });

  it("学习页面对游客开放，耗费型操作由页面内功能自行要求登录", () => {
    const learnerPages = userRoutes.filter((route) => route.component);

    expect(learnerPages.length).toBeGreaterThan(0);
    expect(learnerPages.every((route) => route.meta?.requiresAuth !== true)).toBe(true);
    expect(learnerPages.every((route) => !route.meta?.requiresCapability)).toBe(true);
  });

  it("共享壳的上下文标签遵循五个一级学习区", () => {
    const moduleFor = (path: string) => userRoutes.find((route) => route.path === path)?.meta?.module;

    expect(moduleFor("/user")).toBe("学习路径");

    expect([
      ["/user/home", "学习路径"],
      ["/user/chapters", "学习路径"],
      ["/user/chapters/:chapterId", "学习路径"],
      ["/user/resources/:resourceId", "资料"],
      ["/user/knowledge", "资料"],
      ["/user/coach", "AI 伴学"],
      ["/user/classroom", "课堂"],
      ["/user/animation", "实践"],
      ["/user/presentation", "资料"],
      ["/user/code", "实践"],
      ["/user/progress", "账户工具"],
      ["/user/review", "账户工具"],
      ["/user/profile", "账户工具"],
    ].map(([path, module]) => [path, moduleFor(path)]))
      .toEqual([
        ["/user/home", "学习路径"],
        ["/user/chapters", "学习路径"],
        ["/user/chapters/:chapterId", "学习路径"],
        ["/user/resources/:resourceId", "资料"],
        ["/user/knowledge", "资料"],
        ["/user/coach", "AI 伴学"],
        ["/user/classroom", "课堂"],
        ["/user/animation", "实践"],
        ["/user/presentation", "资料"],
        ["/user/code", "实践"],
        ["/user/progress", "账户工具"],
        ["/user/review", "账户工具"],
        ["/user/profile", "账户工具"],
      ]);
  });
});
