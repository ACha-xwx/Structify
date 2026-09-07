import { describe, expect, it } from "vitest";
import { coursePreviewFixture, previewChapter, previewPresentationLessonId, previewResources, searchPreviewKnowledge } from "./course-preview";

describe("课程本地预览 fixture", () => {
  it("明确是预览内容且不包含生产统计", () => {
    expect(coursePreviewFixture.mode).toBe("fixture");
    expect(coursePreviewFixture.label).toBe("本地课程预览");
    expect(coursePreviewFixture.detail).toContain("不代表");
    expect(coursePreviewFixture.chapters).toHaveLength(10);
    expect(coursePreviewFixture.chapters.map((chapter) => chapter.id)).toEqual([
      "01-introduction",
      "02-linear-list",
      "03-stack-queue",
      "04-string",
      "05-array-generalized-list",
      "06-tree",
      "07-graph",
      "08-search",
      "09-internal-sort",
      "10-external-sort",
    ]);
    expect(coursePreviewFixture.chapters[5]).toMatchObject({
      id: "06-tree",
      title: "树与二叉树",
      summary: "树、二叉树、遍历、哈夫曼树与并查集",
    });
    expect(Object.keys(coursePreviewFixture.resourcesByChapter)).toEqual(["sequential-list"]);
    expect(coursePreviewFixture.knowledge.length).toBeGreaterThanOrEqual(20);
    expect(new Set(coursePreviewFixture.knowledge.map((result) => result.chapterId))).toEqual(new Set([
      "sequential-list",
      "01-introduction",
      "02-linear-list",
      "03-stack-queue",
      "04-string",
      "05-array-generalized-list",
      "06-tree",
      "07-graph",
      "08-search",
      "09-internal-sort",
      "10-external-sort",
    ]));
  });

  it("保留顺序表本地 lesson 别名，但不把它伪装成章节目录条目", () => {
    expect(coursePreviewFixture.chapters.some((chapter) => chapter.id === "sequential-list")).toBe(false);
    expect(previewChapter("sequential-list")).toMatchObject({
      id: "sequential-list",
      chapterNumber: 2,
      title: "顺序表的插入",
    });
    expect(previewChapter("02-linear-list")).toMatchObject({
      id: "02-linear-list",
      chapterNumber: 2,
      title: "线性表",
    });
    expect(previewResources("sequential-list")).toHaveLength(1);
    expect(previewResources("02-linear-list")).toEqual([]);
    expect(previewPresentationLessonId({ id: "sequential-list", chapterNumber: 2 })).toBe("02-02B");
    expect(previewPresentationLessonId({ id: "07-graph", chapterNumber: 7 })).toBe("07-01");
    expect(previewPresentationLessonId({ id: "04-string", chapterNumber: 4 })).toBe("04-01");
    expect(searchPreviewKnowledge("尾部", "sequential-list", 4)).toHaveLength(1);
    expect(searchPreviewKnowledge("尾部", "02-linear-list", 4)).toHaveLength(1);
    expect(searchPreviewKnowledge("顺序栈", "03-stack-queue", 4)).toHaveLength(1);
  });

  it("只返回匹配章节的本地知识卡片", () => {
    expect(searchPreviewKnowledge("尾部", "sequential-list", 4)).toHaveLength(1);
    expect(searchPreviewKnowledge("尾部", "unknown", 4)).toEqual([]);
    expect(searchPreviewKnowledge("", "sequential-list", 4)).toEqual([]);
  });

  it("示例知识覆盖课程主题并保留本地内容边界", () => {
    const result = searchPreviewKnowledge("二叉树遍历", "06-tree", 4);
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({ chapterId: "06-tree", reviewStatus: "预览内容", sourceLabel: "本地 fixture" });
  });
});
