import { describe, expect, it, vi } from "vitest";
import type { DsvpRequest, DsvpSimulationResponse } from "../../shared/types/contracts";
import { createAlgorithmStagePreview, loadAlgorithmStage, simulateAlgorithmStage } from "./algorithm-stage";

const request: DsvpRequest = {
  version: "1.0",
  structure: "sequential_list",
  operation: "insert",
  params: { index: 2, value: 23 },
  initial_state: { data: [12, 18, 27, 31, 44] },
  chapterId: "sequential-list",
};

const response: DsvpSimulationResponse = {
  protocol: "dsvp/1.0",
  request,
  trace: { deterministic: true },
  animationData: {
    animation: true,
    type: "array",
    title: "服务端顺序表插入",
    description: "服务端返回的步骤",
    initial: [12, 18],
    steps: [],
  },
  recordId: "record-1",
  evidencePersisted: true,
  resolvedChapterId: "sequential-list",
  matchSource: "EXPLICIT_CHAPTER",
};

describe("算法舞台适配器", () => {
  it("首开使用本地教学预览，不隐式调用模拟接口", async () => {
    const api = { simulateAnimation: vi.fn() };

    const snapshot = await loadAlgorithmStage(api, undefined, { request, intent: "initial" });

    expect(snapshot).toEqual(createAlgorithmStagePreview());
    expect(api.simulateAnimation).not.toHaveBeenCalled();
  });

  it("首开根据章节或小节上下文选择对应的本地轨迹", async () => {
    const api = { simulateAnimation: vi.fn() };

    const stack = await loadAlgorithmStage(api, undefined, { chapterId: "stack" });
    const graphLesson = await loadAlgorithmStage(api, undefined, { chapterId: "07-graph", lessonId: "bfs" });
    const sortingChapter = await loadAlgorithmStage(api, undefined, { chapterId: "09-internal-sort" });

    expect(stack.definition.title).toBe("栈的入栈与出栈");
    expect(graphLesson.definition.title).toBe("广度优先搜索");
    expect(sortingChapter.definition.title).toBe("插入排序");
    expect(api.simulateAnimation).not.toHaveBeenCalled();
  });

  it("在显式模拟后优先采用服务端 DSVP 响应", async () => {
    const api = { simulateAnimation: vi.fn().mockResolvedValue(response) };

    const snapshot = await loadAlgorithmStage(api, request, { intent: "simulate" });

    expect(api.simulateAnimation).toHaveBeenCalledWith(request);
    expect(snapshot).toMatchObject({
      mode: "live",
      animationRecordId: "record-1",
      evidencePersisted: true,
      trace: { deterministic: true },
    });
    expect(snapshot.definition.title).toBe("服务端顺序表插入");
  });

  it("在模拟服务不可用时回落到明确标识的本地预览", async () => {
    const api = { simulateAnimation: vi.fn().mockRejectedValue(Object.assign(new Error("unavailable"), { status: 503 })) };

    const snapshot = await simulateAlgorithmStage(api, request);

    expect(snapshot).toMatchObject({
      mode: "fixture",
      fixtureReason: "api-unavailable",
      animationRecordId: null,
      evidencePersisted: false,
    });
    expect(snapshot.contextDetail).toContain("服务端模拟当前不可用");
  });

  it("服务端不可用时按请求的小节上下文回落，不把所有课程改成顺序表插入", async () => {
    const stackRequest: DsvpRequest = {
      ...request,
      structure: "stack",
      operation: "push",
      chapterId: "03-stack-queue",
      lessonId: "stack",
      initial_state: { data: [4, 7] },
    };
    const api = { simulateAnimation: vi.fn().mockRejectedValue(Object.assign(new Error("unavailable"), { status: 503 })) };

    const snapshot = await simulateAlgorithmStage(api, stackRequest);

    expect(snapshot.fixture?.chapterId).toBe("stack");
    expect(snapshot.definition.title).toBe("栈的入栈与出栈");
  });

  it("不掩盖显式模拟的权限失败，以便视图给出原位登录提示", async () => {
    const permissionError = Object.assign(new Error("login required"), { status: 401 });
    const api = { simulateAnimation: vi.fn().mockRejectedValue(permissionError) };

    await expect(simulateAlgorithmStage(api, request)).rejects.toBe(permissionError);
  });
});
