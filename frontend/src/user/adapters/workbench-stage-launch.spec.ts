import { describe, expect, it, vi } from "vitest";
import type { DsvpSimulationResponse } from "../../shared/types/contracts";
import {
  createPublishedChapterStageRequest,
  launchPublishedChapterStage,
  liveStagePointerIndex,
  liveStageValue,
  nodesAtLiveWorkbenchStep,
  projectPersistedWorkbenchStage,
} from "./workbench-stage-launch";

const chapter = {
  id: "published-linear-42",
  chapterNumber: 42,
  title: "顺序表",
  summary: "来自课程服务的已发布章节。",
};

const request = createPublishedChapterStageRequest(chapter);

const response: DsvpSimulationResponse = {
  protocol: "dsvp/1.0",
  request,
  trace: { deterministic: true },
  animationData: {
    animation: true,
    type: "array",
    title: "顺序表插入演示",
    description: "把 23 写入顺序表。",
    initial: [7, 11, 19],
    steps: [
      { op: "insert", label: "写入 23", note: "把新值写入目标位置。", value: 23, index: 2 },
      { op: "get", label: "确认结果", note: "检查插入后的顺序。", index: 2 },
    ],
  },
  recordId: "record-42",
  evidencePersisted: true,
  resolvedChapterId: chapter.id,
  matchSource: "EXPLICIT_CHAPTER",
};

describe("学习台实时舞台启动适配器", () => {
  it("只向服务端发送已发布章节的嵌套上下文", () => {
    expect(request).toMatchObject({
      version: "1.0",
      structure: "sequential_list",
      operation: "insert",
      chapterId: "published-linear-42",
      source_ref: "workbench/chapter/published-linear-42/stage",
      context: {
        chapter_id: "published-linear-42",
        source_type: "API",
        source_ref: "workbench/chapter/published-linear-42/stage",
      },
    });
    expect(request.lessonId).toBeUndefined();
    expect(request.lesson_id).toBeUndefined();
  });

  it("拒绝空的已发布章节 ID", () => {
    expect(() => createPublishedChapterStageRequest({ ...chapter, id: "  " })).toThrow("published chapter id");
  });

  it("显式请求后仅投影服务端返回的实时舞台", async () => {
    const api = { simulateAnimation: vi.fn().mockResolvedValue(response) };

    const stage = await launchPublishedChapterStage(api, chapter);

    expect(api.simulateAnimation).toHaveBeenCalledWith(request);
    expect(stage).toMatchObject({
      mode: "live",
      chapterId: chapter.id,
      title: "顺序表插入演示",
      animationRecordId: "record-42",
      evidencePersisted: true,
    });
    expect(nodesAtLiveWorkbenchStep(stage, 0)).toEqual([
      { value: 7, state: "base" },
      { value: 11, state: "base" },
      { value: 23, state: "inserted" },
      { value: 19, state: "base" },
    ]);
    expect(liveStageValue(stage, 0)).toBe(23);
    expect(liveStagePointerIndex(stage, 0, 4)).toBe(2);
  });

  it("恢复已保存场景时只投影已有定义，不重新请求模拟服务", () => {
    const stage = projectPersistedWorkbenchStage(chapter, {
      recordId: "saved-record-42",
      chapterId: chapter.id,
      definition: response.animationData,
    });

    expect(stage).toMatchObject({
      mode: "live",
      chapterId: chapter.id,
      animationRecordId: "saved-record-42",
      evidencePersisted: true,
      title: "顺序表插入演示",
    });
    expect(stage.steps).toHaveLength(2);
    expect(() => projectPersistedWorkbenchStage(chapter, {
      recordId: "saved-record-42",
      chapterId: "another-chapter",
      definition: response.animationData,
    })).toThrow("must match");
  });

  it("服务不可用时不把认证章节替换为本地 fixture", async () => {
    const unavailable = Object.assign(new Error("unavailable"), { status: 503 });
    const api = { simulateAnimation: vi.fn().mockRejectedValue(unavailable) };

    await expect(launchPublishedChapterStage(api, chapter)).rejects.toBe(unavailable);
    expect(api.simulateAnimation).toHaveBeenCalledWith(request);
  });
});
