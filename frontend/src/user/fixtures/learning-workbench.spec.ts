import { describe, expect, it } from "vitest";
import {
  courseGroupIdForChapter,
  courseItemNavigationContext,
  courseItemRouteId,
  flattenCourseGroups,
  groupChaptersByCourseTopic,
  learningCourseGroups,
  learningWorkbenchFixture,
  localizeCourseCapability,
  localizedCourseGroups,
  nodesAtWorkbenchStep,
} from "./learning-workbench";

describe("学习台本地演示 fixture", () => {
  it("keeps the teaching scene outside Vue templates", () => {
    expect(learningWorkbenchFixture.mode).toBe("fixture");
    expect(learningWorkbenchFixture.goalId).toBe("sequential-list-insertion-boundary");
    expect(learningWorkbenchFixture.goalStatus).toBe("active");
    expect(learningWorkbenchFixture.steps).toHaveLength(4);
    expect(learningWorkbenchFixture).not.toHaveProperty("completionRate");
    expect(learningWorkbenchFixture).not.toHaveProperty("userCount");
  });

  it("exposes a complete grouped curriculum and a full compatibility projection", () => {
    expect(learningCourseGroups.map((group) => group.id)).toEqual([
      "linear-structures",
      "stacks-queues",
      "trees-heaps",
      "graphs",
      "search-hash",
      "sorting-complexity",
    ]);
    expect(learningCourseGroups).toHaveLength(6);
    expect(learningCourseGroups.every((group) => group.summary.length > 0 && group.summaryEn.length > 0)).toBe(true);
    expect(learningCourseGroups.every((group) => group.capability === "catalog")).toBe(true);
    expect(flattenCourseGroups(learningCourseGroups, { includeLessons: false }).map((item) => item.id)).toEqual([
      "01-introduction",
      "02-linear-list",
      "04-string",
      "05-array-generalized-list",
      "03-stack-queue",
      "06-tree",
      "07-graph",
      "08-search",
      "09-internal-sort",
      "10-external-sort",
    ]);
    expect(flattenCourseGroups().find((item) => item.current)?.id).toBe("sequential-list");
    // The flat compatibility projection contains 10 chapter rows plus the
    // 29 nested teaching-unit rows. The visible product totals keep those
    // levels separate instead of presenting 39 as a single course count.
    expect(learningWorkbenchFixture.courseOutline).toHaveLength(39);
    expect(learningWorkbenchFixture.courseOutline.map((item) => item.id)).toContain("10-external-sort");
    expect(learningWorkbenchFixture.courseOutline.map((item) => item.id)).toContain("sequential-list");
  });

  it("makes the 10 chapters and 29 units useful catalog entries without inventing availability", () => {
    const catalog = flattenCourseGroups();
    const chapters = catalog.filter((item) => item.kind === "chapter");
    const units = catalog.filter((item) => item.kind === "lesson");
    const previewUnit = catalog.find((item) => item.id === "sequential-list");

    expect(chapters).toHaveLength(10);
    expect(units).toHaveLength(29);
    expect(catalog.every((item) => item.summary.length > 0 && item.summaryEn.length > 0)).toBe(true);
    expect(units.every((item) => item.lessonNumber !== undefined && item.parentId !== undefined)).toBe(true);
    expect(previewUnit).toMatchObject({
      label: "顺序表的插入",
      capability: "local-interactive-preview",
      routeId: "sequential-list",
      parentId: "02-linear-list",
    });
    expect(catalog.filter((item) => item.capability === "local-interactive-preview").map((item) => item.id)).toEqual([
      "sequential-list",
    ]);
    expect(catalog.some((item) => item.capability === "api-published")).toBe(false);
    expect(catalog.filter((item) => item.id !== "sequential-list").every((item) => item.capability === "catalog")).toBe(true);
  });

  it("localizes groups and keeps route/context ids explicit", () => {
    const english = localizedCourseGroups("en-US");
    expect(english[0].label).toBe("Linear structures");
    expect(english[0].items[1].label).toBe("Linear lists");
    expect(english[0].items[1].summary).toContain("contiguous");
    expect(localizeCourseCapability("catalog")).toMatchObject({ label: "课程目录" });
    expect(localizeCourseCapability("local-interactive-preview", "en-US")).toMatchObject({ label: "Local interactive preview" });
    expect(localizeCourseCapability("api-published", "en-US").detail).toContain("course service");
    const insertion = flattenCourseGroups().find((item) => item.id === "sequential-list");
    expect(insertion?.chapterId).toBe("sequential-list");
    expect(courseItemRouteId(insertion!)).toBe("sequential-list");
    expect(courseGroupIdForChapter("03-stack-queue")).toBe("stacks-queues");
    expect(courseGroupIdForChapter("quick-sort")).toBe("sorting-complexity");
  });

  it("keeps a lesson id distinct from legacy child route ids", () => {
    const graph = flattenCourseGroups().find((item) => item.id === "bfs");
    const stack = flattenCourseGroups().find((item) => item.id === "stack");

    expect(graph).toBeDefined();
    expect(courseItemRouteId(graph!)).toBe("07-graph");
    expect(courseItemNavigationContext(graph!)).toEqual({ chapterId: "07-graph", lessonId: "bfs" });
    expect(courseItemNavigationContext(stack!)).toEqual({ chapterId: "03-stack-queue", lessonId: "stack" });
  });

  it("adapts a flat API chapter response without inventing progress", () => {
    const grouped = groupChaptersByCourseTopic([
      { id: "07-graph", chapterNumber: 7, title: "图", summary: "图的存储与遍历" },
      { id: "03-stack-queue", chapterNumber: 3, title: "栈与队列", summary: "栈和队列" },
    ], "03-stack-queue");
    expect(grouped).toHaveLength(6);
    expect(grouped.find((group) => group.id === "stacks-queues")?.items[0]).toMatchObject({
      id: "03-stack-queue",
      kind: "chapter",
      current: true,
      meta: "第 03 章",
      summary: "栈和队列",
      capability: "api-published",
    });
    expect(grouped.find((group) => group.id === "graphs")?.items[0].current).toBe(false);
    expect(grouped.find((group) => group.id === "stacks-queues")?.items[0].children).toBeUndefined();
  });

  it("resolves a local lesson id to its canonical API chapter", () => {
    const grouped = groupChaptersByCourseTopic([
      { id: "02-linear-list", chapterNumber: 2, title: "线性表", summary: "顺序与链式存储" },
    ], "sequential-list");
    expect(grouped.find((group) => group.id === "linear-structures")?.items[0].current).toBe(true);
  });

  it("replays the insertion without mutating the source array", () => {
    expect(nodesAtWorkbenchStep(0).map((node) => node.value)).toEqual([12, 18, 27, 31, 44]);
    expect(nodesAtWorkbenchStep(2).some((node) => node.state === "shifted")).toBe(true);
    expect(nodesAtWorkbenchStep(3).map((node) => node.value)).toEqual([12, 18, 23, 27, 31, 44]);
    expect(learningWorkbenchFixture.initialNodes).toEqual([12, 18, 27, 31, 44]);
  });
});
