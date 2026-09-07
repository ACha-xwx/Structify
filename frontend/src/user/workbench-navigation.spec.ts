import { describe, expect, it } from "vitest";
import {
  courseSelectionTarget,
  createLearningGlobalNavigation,
  createLearningNavigation,
  createLearningTools,
  isLearningGlobalNavigationActive,
  isLearningNavigationActive,
  localizedLearningGlobalNavigation,
  localizedLearningNavigation,
  localizedLearningTools,
  learningGlobalNavigation,
  learningNavigation,
  learningTools,
} from "./workbench-navigation";

describe("学生端工作台 IA", () => {
  it("把产品级导航和课程内学习模块分开", () => {
    expect(learningGlobalNavigation.map((item) => item.id)).toEqual(["overview", "course", "lab", "library"]);
    expect(localizedLearningGlobalNavigation("zh-CN").map((item) => item.label)).toEqual(["学习台", "课程", "代码实验", "资料库"]);
    expect(isLearningGlobalNavigationActive("overview", "/user/home")).toBe(true);
    expect(isLearningGlobalNavigationActive("lab", "/user/code")).toBe(true);
    expect(isLearningGlobalNavigationActive("library", "/user/presentation")).toBe(true);
  });

  it("无章节上下文时不猜测顺序表，并把课程选择和通用代码实验分开", () => {
    expect(courseSelectionTarget()).toBe("/user/chapters");
    expect(createLearningGlobalNavigation().find((item) => item.id === "course")?.to).toBe("/user/chapters");
    expect(createLearningGlobalNavigation().find((item) => item.id === "lab")).toMatchObject({
      to: "/user/code",
      label: { zh: "代码实验", en: "Code lab" },
    });
    expect(createLearningNavigation().find((item) => item.id === "practice")?.to).toBe("/user/chapters");
    expect(createLearningTools().map((item) => item.to)).toEqual(["/user/chapters", "/user/code"]);
    expect([...learningGlobalNavigation, ...learningNavigation, ...learningTools].some((item) => item.to.includes("sequential-list"))).toBe(false);
  });

  it("保留调用方传入的章节和来源，并只在课时明确时打开课件", () => {
    const context = { chapterId: "linked-list", from: "chapter" };
    expect(createLearningGlobalNavigation(context).find((item) => item.id === "course")?.to).toBe("/user/chapters?chapterId=linked-list&from=chapter");
    expect(createLearningGlobalNavigation(context).find((item) => item.id === "lab")).toMatchObject({
      to: "/user/animation?chapterId=linked-list&from=chapter",
      label: { zh: "算法舞台", en: "Algorithm stage" },
    });
    expect(createLearningNavigation(context).find((item) => item.id === "practice")?.to).toBe("/user/animation?chapterId=linked-list&from=chapter");
    expect(createLearningNavigation(context).find((item) => item.id === "library")?.to).toBe("/user/knowledge?chapterId=linked-list&from=chapter");
    expect(createLearningTools(context).map((item) => item.to)).toEqual([
      "/user/chapters?chapterId=linked-list&from=chapter",
      "/user/code?chapterId=linked-list&from=chapter",
    ]);
    expect(localizedLearningTools("zh-CN", { ...context, lessonId: "03-01A" })[0].to).toBe("/user/presentation?lessonId=03-01A&chapterId=linked-list&from=chapter");
  });

  it("接受 Vue Router 查询数组，并对只有来源的跳转保持中性", () => {
    const context = { chapterId: ["", "stack"], lessonId: ["04-01A"], from: ["classroom"] };
    expect(localizedLearningGlobalNavigation("zh-CN", context).find((item) => item.id === "lab")).toMatchObject({
      to: "/user/animation?chapterId=stack&lessonId=04-01A&from=classroom",
      label: "算法舞台",
    });
    expect(localizedLearningTools("zh-CN", context)[0].to).toBe("/user/presentation?lessonId=04-01A&chapterId=stack&from=classroom");
    expect(courseSelectionTarget({ from: "chapter" })).toBe("/user/chapters?from=chapter");
  });

  it("为算法 lesson 和课件 lesson 使用各自的稳定 id", () => {
    const context = {
      chapterId: "02-linear-list",
      lessonId: "sequential-list",
      coursewareLessonId: "02-02B",
      from: "workbench",
    };
    expect(createLearningNavigation(context).find((item) => item.id === "practice")?.to).toBe("/user/animation?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
    expect(createLearningTools(context)[0].to).toBe("/user/presentation?lessonId=02-02B&chapterId=02-linear-list&from=workbench");
    expect(createLearningTools(context)[1].to).toBe("/user/code?chapterId=02-linear-list&lessonId=sequential-list&from=workbench");
  });

  it("只定义一组可复用的五个学习区入口", () => {
    expect(learningNavigation.map((item) => item.id)).toEqual(["path", "coach", "classroom", "practice", "library"]);
    expect(localizedLearningNavigation("zh-CN").map((item) => item.label)).toEqual(["学习路径", "AI 伴学", "课堂", "实践", "资料"]);
    expect(localizedLearningNavigation("en-US").map((item) => item.label)).toEqual(["Learning path", "AI coach", "Classroom", "Practice", "Library"]);
  });

  it("把实践和资料下的页面归入正确的一级区域", () => {
    expect(isLearningNavigationActive("path", "/user/chapters/linear-list")).toBe(true);
    expect(isLearningNavigationActive("practice", "/user/code")).toBe(true);
    expect(isLearningNavigationActive("practice", "/user/animation")).toBe(true);
    expect(isLearningNavigationActive("library", "/user/resources/handout-1")).toBe(true);
    expect(isLearningNavigationActive("library", "/user/presentation")).toBe(true);
    expect(isLearningNavigationActive("coach", "/user/presentation")).toBe(false);
  });

  it("把课件和 C 编译器作为工作台直接工具，而不是隐藏菜单项", () => {
    expect(localizedLearningTools("zh-CN").map((item) => item.label)).toEqual(["课程课件", "C 编译器"]);
    expect(localizedLearningTools("en-US").map((item) => item.label)).toEqual(["Courseware", "C compiler"]);
    expect(localizedLearningTools("zh-CN")[1].to).toBe("/user/code");
  });
});
