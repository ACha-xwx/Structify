import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../shared/api";
import { createPresentationApi } from "./presentation";

const response = {
  ok: true as const,
  ready: true,
  lessonId: "01-01A",
  plan: { lessonId: "01-01A", title: "数据、数据元素与数据对象", scenes: {}, slideOrder: ["slide-1"] },
  slides: {
    "slide-1": {
      id: "slide-1",
      deckId: "deck-1",
      deckTitle: "第一章",
      slideNumber: 1,
      chapter: "01",
      title: "数据结构",
      rawText: "",
      speakerNotes: "",
      semanticSummary: "",
      teachingRole: "title",
      teachingFocus: "",
      concepts: [],
      visualAnchors: [],
      animationCapabilities: [],
      imageUrl: "/presentation/rendered/deck/001.png?expires=1&token=abc",
    },
  },
  meta: { ready: true, builtAt: "2026-08-08T00:00:00Z", slideCount: 1, lessonCount: 1 },
};

describe("legacy 课件播放适配器", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("保留 Node 原始播放计划并发送 lessonId 查询参数", async () => {
    const request = vi.fn().mockResolvedValue({ kind: "json", data: response });
    const api = createPresentationApi({ request } as never);

    await expect(api.getPlan(" 01-01A ")).resolves.toEqual({ ...response, source: "api" });
    expect(request).toHaveBeenCalledWith("/classroom/presentation-plan", { query: { lessonId: "01-01A" } });
  });

  it("在匿名本地预览时直接使用 bundle，不再先请求课件接口", async () => {
    vi.stubGlobal("window", { location: { hostname: "127.0.0.1" } } as never);
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200,
      headers: { "content-type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetch as never);
    const request = vi.fn();
    const api = createPresentationApi({ request } as never);

    await expect(api.getPlan("01-01A", { preferLocalPreview: true })).resolves.toEqual({ ...response, source: "local-preview" });
    expect(request).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("在 localhost 遇到 ready=false / plan=null 时回退到本地预览 bundle", async () => {
    vi.stubGlobal("window", { location: { hostname: "127.0.0.1" } } as never);
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200,
      headers: { "content-type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetch as never);

    const request = vi.fn().mockResolvedValue({
      kind: "json",
      data: {
        ...response,
        ready: false,
        plan: null,
        slides: {},
        meta: { ...response.meta, ready: false, slideCount: 0 },
      },
    });
    const api = createPresentationApi({ request } as never);

    await expect(api.getPlan(" 01-01A ")).resolves.toEqual({ ...response, source: "local-preview" });
    expect(fetch).toHaveBeenCalledWith("/__preview/presentation-plan?lessonId=01-01A", expect.objectContaining({
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    }));
  });

  it("拒绝非课件 JSON，避免页面把错误响应当成空播放", async () => {
    vi.stubGlobal("window", undefined as never);
    const request = vi.fn().mockResolvedValue({ kind: "json", data: { ok: true, slides: [] } });
    const api = createPresentationApi({ request } as never);

    await expect(api.getPlan("01-01A")).rejects.toThrow("无法识别的播放计划");
  });

  it("在 localhost 遇到 401 时回退到本地预览 bundle", async () => {
    vi.stubGlobal("window", { location: { hostname: "localhost" } } as never);
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200,
      headers: { "content-type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetch as never);

    const request = vi.fn().mockRejectedValue(new ApiClientError({
      status: 401,
      code: "AUTH_REQUIRED",
      message: "请先登录",
      requestId: "req-1",
      details: [],
    }));
    const api = createPresentationApi({ request } as never);

    await expect(api.getPlan("01-01A")).resolves.toEqual({ ...response, source: "local-preview" });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("为已认证的 Node 课件请求先换取短时兼容令牌", async () => {
    const request = vi.fn().mockResolvedValue({ kind: "json", data: response });
    const issueNodeCompatibilityToken = vi.fn().mockResolvedValue("node-token");
    const api = createPresentationApi({ request } as never, { issueNodeCompatibilityToken });

    await expect(api.getPlan("01-01A")).resolves.toEqual({ ...response, source: "api" });
    expect(issueNodeCompatibilityToken).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith("/classroom/presentation-plan", {
      headers: { Authorization: "Bearer node-token" },
      query: { lessonId: "01-01A" },
    });
  });
});
