import { describe, expect, it, vi } from "vitest";
import { createUserApi } from "./api";

describe("用户端 API 边界", () => {
  it("按冻结契约加载章节、资源和知识检索", async () => {
    const request = vi.fn()
      .mockResolvedValueOnce({ kind: "json", data: [{ id: "stack", chapterNumber: 1, title: "栈", summary: "后进先出" }] })
      .mockResolvedValueOnce({ kind: "json", data: [{ id: "resource-1", chapterId: "stack", type: "PDF", title: "讲义", description: "", sourceName: "课程组", versionLabel: "v1", reviewStatus: "PUBLISHED", licenseScope: "PUBLIC", contentUrl: null }] })
      .mockResolvedValueOnce({ kind: "json", data: { ok: true, query: "栈", results: [] } });
    const api = createUserApi({ request } as never);

    await expect(api.listChapters()).resolves.toHaveLength(1);
    await expect(api.listResources("stack")).resolves.toHaveLength(1);
    await expect(api.searchKnowledge({ query: "栈", chapterId: "stack", limit: 4 })).resolves.toMatchObject({ results: [] });

    expect(request).toHaveBeenNthCalledWith(1, "/chapters");
    expect(request).toHaveBeenNthCalledWith(2, "/chapters/stack/resources");
    expect(request).toHaveBeenNthCalledWith(3, "/knowledge/search", { query: { q: "栈", chapterId: "stack", limit: 4 } });
  });

  it("对话流、代码执行和学习事件使用 Spring v1 的正式路径", async () => {
    const request = vi.fn()
      .mockResolvedValueOnce({ kind: "sse", events: {} })
      .mockResolvedValueOnce({ kind: "json", data: { language: "c", status: "compile_error", stdout: "", stderr: "error", durationMs: 1, runId: null } })
      .mockResolvedValueOnce({ kind: "json", data: { id: 1, eventType: "RESOURCE_VIEW", chapterId: "stack", referenceId: "resource-1", createdAt: "2026-08-12T00:00:00Z" } });
    const api = createUserApi({ request } as never);

    await api.streamChat({ prompt: "栈是什么", chapterId: "stack" });
    await api.runCode({ language: "c", code: "int main(void){}", chapterId: "stack" });
    await api.recordLearningEvent({ eventType: "RESOURCE_VIEW", chapterId: "stack", referenceId: "resource-1" });

    expect(request).toHaveBeenNthCalledWith(1, "/chat/stream", expect.objectContaining({ method: "POST", responseType: "sse" }));
    expect(request).toHaveBeenNthCalledWith(2, "/code/runs", expect.objectContaining({ method: "POST" }));
    expect(request).toHaveBeenNthCalledWith(3, "/learning/events", expect.objectContaining({ method: "POST" }));
  });

  it("取教材片段库", async () => {
    const request = vi.fn().mockResolvedValue({ kind: "json", data: { chapters: [], fragmentCount: 99 } });
    const api = createUserApi({ request } as never);

    await expect(api.getCodeLibrary()).resolves.toMatchObject({ fragmentCount: 99 });
    expect(request).toHaveBeenCalledWith("/code/library");
  });

  it("按课时取课堂代码样本，也支持取全部", async () => {
    const request = vi.fn()
      .mockResolvedValueOnce({ kind: "json", data: { lessons: [{ coursewareKey: "03-01", lessonTitle: "栈", chapterId: "03", samples: [] }], sampleCount: 4 } })
      .mockResolvedValueOnce({ kind: "json", data: { lessons: [], sampleCount: 0 } });
    const api = createUserApi({ request } as never);

    await expect(api.listCodeSamples("03-01")).resolves.toMatchObject({ sampleCount: 4 });
    await api.listCodeSamples();

    expect(request).toHaveBeenNthCalledWith(1, "/code/samples?coursewareKey=03-01");
    expect(request).toHaveBeenNthCalledWith(2, "/code/samples");
  });

  it("读取认证学习台投影而不是拼接多次进度请求", async () => {    const request = vi.fn().mockResolvedValue({
      kind: "json",
      data: {
        currentChapterId: "sequential-list",
        progress: { totalActivities: 1, chapters: [] },
        scene: null,
      },
    });
    const api = createUserApi({ request } as never);

    await expect(api.getLearningWorkbench()).resolves.toMatchObject({ currentChapterId: "sequential-list" });
    expect(request).toHaveBeenCalledWith("/learning/workbench");
  });
});
