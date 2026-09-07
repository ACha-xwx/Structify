import { describe, expect, it, vi } from "vitest";
import { loadLearningWorkbench } from "./learning-workbench";
import type { UserApi } from "../api";

const chapter = {
  id: "sequential-list",
  chapterNumber: 2,
  title: "顺序表",
  summary: "理解连续存储与插入边界。",
};

const progress = {
  totalActivities: 2,
  chapters: [{
    chapterId: chapter.id,
    chapterNumber: chapter.chapterNumber,
    title: chapter.title,
    chatCount: 1,
    classroomCount: 0,
    animationCount: 1,
    codeRunCount: 0,
    eventCount: 2,
    totalActivities: 2,
    lastActivityAt: "2026-08-25T07:00:00Z",
  }],
};

const savedScene = {
  recordId: "saved-stage-1",
  chapterId: chapter.id,
  definition: {
    animation: true as const,
    type: "array" as const,
    title: "顺序表插入演示",
    description: "从上次学习位置恢复。",
    initial: [7, 11, 19],
    steps: [{ op: "insert", label: "写入 23", note: "把 23 放入目标位置。", value: 23, index: 2 }],
  },
  updatedAt: "2026-09-02T10:00:00Z",
};

function api(): Pick<UserApi, "listChapters" | "getLearningWorkbench" | "getReadiness"> {
  return {
    listChapters: vi.fn().mockResolvedValue([chapter]),
    getLearningWorkbench: vi.fn().mockResolvedValue({ currentChapterId: null, progress, scene: null }),
    getReadiness: vi.fn().mockResolvedValue({}) as unknown as UserApi["getReadiness"],
  };
}

describe("learning-workbench adapter", () => {
  it("keeps a live course context without a saved scene separate from the local fixture", async () => {
    const client = api();
    const snapshot = await loadLearningWorkbench(client);

    expect(snapshot.mode).toBe("live");
    expect(snapshot.contextMode).toBe("live");
    expect(snapshot.scene).toBeNull();
    expect(snapshot.persistedStage).toBeNull();
    expect(snapshot.fixtureReason).toBeNull();
    expect(snapshot.currentChapter).toEqual(chapter);
  });

  it("rejects API failures instead of silently returning a normal preview", async () => {
    const client = api();
    const failure = Object.assign(new Error("gateway"), { status: 503 });
    vi.spyOn(client, "getLearningWorkbench").mockRejectedValue(failure);

    await expect(loadLearningWorkbench(client)).rejects.toBe(failure);
  });

  it("keeps an authenticated response without a current chapter as an honest empty context", async () => {
    const client = api();
    vi.spyOn(client, "listChapters").mockResolvedValue([chapter]);
    vi.spyOn(client, "getLearningWorkbench").mockResolvedValue({
      currentChapterId: null,
      progress: { totalActivities: 0, chapters: [] },
      scene: null,
    });

    const snapshot = await loadLearningWorkbench(client);

    expect(snapshot.mode).toBe("live");
    expect(snapshot.contextMode).toBe("live");
    expect(snapshot.fixtureReason).toBeNull();
    expect(snapshot.scene).toBeNull();
    expect(snapshot.persistedStage).toBeNull();
    expect(snapshot.chapters).toEqual([chapter]);
    expect(snapshot.contextDetail).toContain("已读取课程目录");
  });

  it("restores a saved scene for its published chapter ahead of newer aggregate progress", async () => {
    const stack = { id: "stack", chapterNumber: 3, title: "栈", summary: "理解后进先出。" };
    const client = api();
    vi.spyOn(client, "listChapters").mockResolvedValue([chapter, stack]);
    vi.spyOn(client, "getLearningWorkbench").mockResolvedValue({
      currentChapterId: chapter.id,
      progress: {
        totalActivities: 5,
        chapters: [
          progress.chapters[0],
          {
            chapterId: stack.id,
            chapterNumber: stack.chapterNumber,
            title: stack.title,
            chatCount: 2,
            classroomCount: 0,
            animationCount: 0,
            codeRunCount: 0,
            eventCount: 3,
            totalActivities: 3,
            lastActivityAt: "2026-09-02T11:00:00Z",
          },
        ],
      },
      scene: savedScene,
    });

    const snapshot = await loadLearningWorkbench(client);

    expect(snapshot.currentChapter).toEqual(chapter);
    expect(snapshot.currentProgress?.chapterId).toBe(chapter.id);
    expect(snapshot.persistedStage).toMatchObject({
      mode: "live",
      chapterId: chapter.id,
      animationRecordId: savedScene.recordId,
      evidencePersisted: true,
    });
    expect(snapshot.persistedStage?.steps[0]?.label).toBe("写入 23");
  });

  it("keeps a persisted stage available when the optional readiness request fails", async () => {
    const client = api();
    vi.spyOn(client, "getLearningWorkbench").mockResolvedValue({
      currentChapterId: chapter.id,
      progress,
      scene: savedScene,
    });
    vi.spyOn(client, "getReadiness").mockRejectedValue(Object.assign(new Error("readiness unavailable"), { status: 503 }));

    const snapshot = await loadLearningWorkbench(client);

    expect(snapshot.currentChapter).toEqual(chapter);
    expect(snapshot.persistedStage?.animationRecordId).toBe(savedScene.recordId);
    expect(snapshot.readiness).toBeNull();
  });

  it("does not render a saved scene whose chapter is no longer published", async () => {
    const client = api();
    vi.spyOn(client, "getLearningWorkbench").mockResolvedValue({
      currentChapterId: "unpublished-chapter",
      progress,
      scene: { ...savedScene, chapterId: "unpublished-chapter" },
    });

    const snapshot = await loadLearningWorkbench(client);

    expect(snapshot.persistedStage).toBeNull();
    expect(snapshot.scene).toBeNull();
    expect(snapshot.currentChapter).toEqual(chapter);
  });
});
