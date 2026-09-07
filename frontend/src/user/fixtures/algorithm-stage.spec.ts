import { describe, expect, it } from "vitest";
import { algorithmStageFixtureFor, algorithmStageFixtures, sequentialListInsertionStageFixture } from "./algorithm-stage";
import { flattenCourseGroups, learningCourseGroups } from "./learning-workbench";

function replayFixtureSteps(): unknown[] {
  const values = [...sequentialListInsertionStageFixture.definition.initial];
  for (const step of sequentialListInsertionStageFixture.definition.steps) {
    if (step.op === "insert" && step.index != null && step.value !== undefined) values.splice(step.index, 0, step.value);
    if (step.op === "set" && step.index != null) values[step.index] = step.value;
  }
  return values;
}

describe("算法舞台本地教学 fixture", () => {
  it("明确标识本地预览，且不伪造学习记录或生产统计", () => {
    expect(sequentialListInsertionStageFixture.mode).toBe("fixture");
    expect(sequentialListInsertionStageFixture.contextLabel).toBe("本地教学预览");
    expect(sequentialListInsertionStageFixture.contextDetail).toContain("不代表服务端计算");
    expect(sequentialListInsertionStageFixture).not.toHaveProperty("completionRate");
    expect(sequentialListInsertionStageFixture).not.toHaveProperty("userCount");
  });

  it("用尾部到目标位置的右移步骤重放出正确的插入结果", () => {
    expect(sequentialListInsertionStageFixture.definition.initial).toEqual([12, 18, 27, 31, 44]);
    expect(sequentialListInsertionStageFixture.definition.steps.map((step) => step.op)).toEqual([
      "inspect", "insert", "set", "set", "set",
    ]);
    expect(replayFixtureSteps()).toEqual([12, 18, 23, 27, 31, 44]);
  });

  it("为课程地图的每个教学小节提供独立可操作场景", () => {
    const lessonIds = [
      "linear-list", "sequential-list", "sequential-list-delete", "linked-list", "doubly-linked-list",
      "stack", "queue", "circular-queue", "expression-evaluation", "binary-tree", "tree-traversal",
      "binary-search-tree", "heap", "union-find", "graph-storage", "bfs", "dfs", "shortest-path",
      "minimum-spanning-tree", "topological-sort", "sequential-search", "binary-search", "hash-table",
      "balanced-search", "insertion-sort", "selection-sort", "merge-sort", "quick-sort", "complexity-review",
      "01-introduction", "04-string", "05-array-generalized-list", "10-external-sort",
    ];

    for (const lessonId of lessonIds) {
      const fixture = algorithmStageFixtureFor(lessonId);
      expect(fixture.definition.animation).toBe(true);
      expect(fixture.definition.steps.length, lessonId).toBeGreaterThanOrEqual(2);
      expect(fixture.requestTemplate.source_ref).toMatch(/^local-preview\//);
      expect(fixture.contextDetail).toContain("不代表服务端计算");
    }

    expect(Object.keys(algorithmStageFixtures)).toEqual(expect.arrayContaining([
      "stack", "queue", "linked-list", "binary-tree", "bfs", "binary-search", "insertion-sort",
    ]));
  });

  it("不让课程目录中的任何教学入口静默回落到顺序表插入", () => {
    const entries = flattenCourseGroups(learningCourseGroups).filter((item) => item.kind === "lesson" || !item.children?.length);

    for (const item of entries) {
      const fixture = algorithmStageFixtureFor(item.id);
      if (item.id !== "sequential-list") expect(fixture.definition.title, item.id).not.toBe("顺序表的插入");
      expect(fixture.requestTemplate.source_ref, item.id).toContain(`local-preview/${item.id}`);
    }
  });

  it("章节级入口解析到该章节的第一个可播放小节，而不是猜测用户进度", () => {
    expect(algorithmStageFixtureFor("03-stack-queue").chapterId).toBe("stack");
    expect(algorithmStageFixtureFor("07-graph").chapterId).toBe("graph-storage");
    expect(algorithmStageFixtureFor("09-internal-sort").chapterId).toBe("insertion-sort");
    expect(algorithmStageFixtureFor("unknown-context").chapterId).toBe("sequential-list");
  });
});
