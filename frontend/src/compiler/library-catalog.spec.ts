import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { buildLibraryGroups, classifyCode, type LibraryEntry } from "./library-catalog";
import { compilerTemplates } from "./templates";

interface SampleCatalog {
  lessons: { chapterId: string; lessonTitle: string; samples: { id: string; title: string; file: string }[] }[];
}
interface FragmentCatalog {
  chapters: { chapter: string; title: string; fragments: { id: string; title: string; file: string }[] }[];
}
const catalogRoot = resolve(process.cwd(), "../backend/spring/src/main/resources/classroom-code");
const samples: SampleCatalog = JSON.parse(readFileSync(resolve(catalogRoot, "lessons.json"), "utf8"));
const examples: FragmentCatalog = JSON.parse(readFileSync(resolve(catalogRoot, "textbook/library.json"), "utf8"));
const base = { code: "", stdin: "", blocked: "", example: null };
const entries: LibraryEntry[] = [
  ...samples.lessons.flatMap((lesson) => lesson.samples.map((sample) => ({
    ...base, id: sample.id, title: sample.title, sourceFile: sample.file, origin: lesson.lessonTitle,
    group: "samples" as const, kind: "sample" as const,
    ...classifyCode(lesson.chapterId, sample.file, sample.title, sample.id),
  }))),
  ...examples.chapters.flatMap((chapter) => chapter.fragments.map((fragment) => ({
    ...base, id: fragment.id, title: fragment.title, sourceFile: fragment.file, origin: chapter.title,
    group: "examples" as const, kind: "algorithm" as const,
    ...classifyCode(chapter.chapter, fragment.file, fragment.title, fragment.id),
  }))),
  ...compilerTemplates.map((template) => ({
    ...base, id: `template-${template.id}`, title: template.id, sourceFile: template.file,
    origin: template.file, group: "templates" as const, kind: "template" as const, chapterId: "", topicId: "",
  })),
];

describe("compiler catalog classification", () => {
  it("places every current backend entry exactly once without an unclassified fallback", () => {
    const groups = buildLibraryGroups(entries, "", "zh-CN");
    expect(groups.map((group) => group.count)).toEqual([112, 99, 5]);
    const ids = groups.flatMap((group) => group.menus.flatMap((menu) => menu.items.flatMap((item) => item.children ?? [item]))).map((item) => item.id);
    expect(ids.sort()).toEqual(entries.map((entry) => entry.id).sort());
    expect(new Set(ids).size).toBe(entries.length);
    expect(entries.filter((entry) => entry.group !== "templates").every((entry) => entry.chapterId !== "other" && entry.topicId !== "other")).toBe(true);
    expect(groups[0].menus.map((menu) => menu.count)).toEqual([0, 15, 26, 5, 19, 12, 23, 8, 4]);
    expect(groups[1].menus.map((menu) => menu.count)).toEqual([0, 7, 37, 3, 18, 4, 23, 5, 2]);
  });

  it("uses the requested chapter names and the chapter to structure to code hierarchy", () => {
    const groups = buildLibraryGroups(entries, "", "zh-CN");
    expect(groups[0].menus.map((menu) => menu.label)).toEqual([
      "绪论", "线性表", "栈与队列", "串", "数组与广义表", "树与二叉树", "图", "查找", "内部排序和外部排序",
    ]);
    const linear = groups[0].menus.find((menu) => menu.id === "02")!;
    expect(linear.items.map((item) => item.label)).toEqual(["顺序表", "单链表", "循环链表", "双向循环链表", "静态链表", "一元多项式"]);
    const sequential = linear.items.find((item) => item.label === "顺序表")!;
    expect(sequential.children?.map((item) => item.label)).toEqual([
      "elem 数组与 last 下标", "尾插时 last 怎么动", "改一个元素要挪后面所有元素", "顺序表基本结构复习",
    ]);
    expect(linear.items.find((item) => item.label === "单链表")!.children?.some((item) => item.id === "02-03-s2")).toBe(true);
    expect(groups[2].menus[0].items).toHaveLength(5);
    expect(groups[2].menus[0].items.every((item) => !item.children)).toBe(true);
  });

  it("classifies by source rather than misleading words in the title", () => {
    expect(classifyCode("07-graph", "ch07/code/7.2.c", "用邻接矩阵建立有向图的十字链表", "x")).toEqual({ chapterId: "07", topicId: "orthlist" });
    expect(classifyCode("06-tree", "ch06/code/threadtree.h", "线索二叉树", "x")).toEqual({ chapterId: "06", topicId: "threadtree" });
    expect(classifyCode("", "ch02\\code\\linklist1.h", "整型单链表", "x")).toEqual({ chapterId: "02", topicId: "linklist" });
    expect(classifyCode("ch10", "merge.c", "外部排序", "x")).toEqual({ chapterId: "09", topicId: "other" });
  });

  it("filters leaves across all regions while keeping their navigation path", () => {
    const groups = buildLibraryGroups(entries, "elem 数组与 last 下标", "zh-CN");
    expect(groups).toHaveLength(1);
    expect(groups[0].menus).toHaveLength(1);
    expect(groups[0].menus[0].label).toBe("线性表");
    expect(groups[0].menus[0].items[0].label).toBe("顺序表");
    expect(groups[0].menus[0].items[0].children?.map((item) => item.id)).toEqual(["02-01-s1"]);
    expect(buildLibraryGroups(entries, "Binary tree", "en-US").some((group) => group.key === "samples")).toBe(true);
    expect(buildLibraryGroups(entries, "不存在的代码", "zh-CN")).toEqual([]);
  });

  it("keeps new backend chapters and unfamiliar structures accessible", () => {
    const fresh: LibraryEntry = {
      ...entries[0], id: "future-code", title: "新增结构", sourceFile: "future.h",
      ...classifyCode("ch11", "future.h", "新增结构", "future-code"),
    };
    const groups = buildLibraryGroups([fresh], "", "zh-CN");
    const menu = groups[0].menus.find((item) => item.id === "other")!;
    expect(menu.items[0].children?.[0].id).toBe("future-code");
  });
});
