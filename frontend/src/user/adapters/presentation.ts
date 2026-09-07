import { ApiClientError, type ApiRequest } from "../../shared/api";

export interface PresentationSlide {
  id: string;
  deckId: string;
  deckTitle: string;
  slideNumber: number;
  chapter: string;
  title: string;
  rawText: string;
  speakerNotes: string;
  semanticSummary: string;
  teachingRole: string;
  teachingFocus: string;
  concepts: string[];
  visualAnchors: string[];
  animationCapabilities: string[];
  imageUrl: string;
}

export interface PresentationScene {
  slides: string[];
  primarySlideId: string;
  coverage: string;
  score: number;
}

export interface PresentationPlan {
  lessonId: string;
  title: string;
  scenes: Record<string, PresentationScene>;
  slideOrder: string[];
}

export interface PresentationMeta {
  ready: boolean;
  builtAt: string;
  slideCount: number;
  lessonCount: number;
}

export interface PresentationPlanResponse {
  ok: true;
  ready: boolean;
  lessonId: string;
  plan: PresentationPlan | null;
  slides: Record<string, PresentationSlide>;
  meta: PresentationMeta;
  source?: "local-preview" | "api";
}

export interface PresentationApi {
  getPlan(lessonId: string, options?: PresentationApiRequestOptions): Promise<PresentationPlanResponse>;
}

export interface PresentationApiOptions {
  issueNodeCompatibilityToken?: () => Promise<string | null>;
}

export interface PresentationApiRequestOptions {
  preferLocalPreview?: boolean;
}

interface LocalPreviewTokenResponse {
  ok: true;
  ready: boolean;
  lessonId: string;
  plan: PresentationPlan | null;
  slides: Record<string, PresentationSlide>;
  meta: PresentationMeta;
}

function isPresentationResponse(value: unknown): value is PresentationPlanResponse {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<PresentationPlanResponse>;
  return candidate.ok === true
    && typeof candidate.ready === "boolean"
    && typeof candidate.lessonId === "string"
    && (candidate.plan === null || typeof candidate.plan === "object")
    && !!candidate.slides
    && typeof candidate.slides === "object";
}

/**
 * Node returns a raw JSON document, unlike the Spring v1 envelope. Keep that
 * normalization in one adapter so the view only knows the stable presentation
 * contract and can be replaced by a future Spring endpoint without rewrites.
 */
export function createPresentationApi(client: { request: ApiRequest }, apiOptions: PresentationApiOptions = {}): PresentationApi {
  async function requestPlan(lessonId: string, authorization?: string) {
    return client.request<PresentationPlanResponse>("/classroom/presentation-plan", {
      ...(authorization ? { headers: { Authorization: authorization } } : {}),
      query: { lessonId: lessonId.trim() },
    });
  }

  function canUseLocalPreviewToken() {
    if (typeof window === "undefined") return false;
    const hostname = window.location.hostname.toLowerCase();
    return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "[::1]";
  }

  async function getLocalPreviewPlan(lessonId: string) {
    const response = await fetch(`/__preview/presentation-plan?lessonId=${encodeURIComponent(lessonId.trim())}`, {
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error("本地课件预览不可用");
    }
    const payload = await response.json() as LocalPreviewTokenResponse;
    if (!payload.ok || !payload.plan || !payload.meta) {
      throw new Error("本地课件预览格式无效");
    }
    return { ...payload, source: "local-preview" as const };
  }

  return {
    async getPlan(lessonId, requestOptions = {}) {
      if (requestOptions.preferLocalPreview && canUseLocalPreviewToken()) {
        return await getLocalPreviewPlan(lessonId);
      }
      let response;
      let authorization: string | undefined;
      if (!requestOptions.preferLocalPreview && apiOptions.issueNodeCompatibilityToken) {
        const token = await apiOptions.issueNodeCompatibilityToken();
        if (token) authorization = `Bearer ${token}`;
      }
      try {
        response = await requestPlan(lessonId, authorization);
      } catch (error) {
        if (!(error instanceof ApiClientError) || error.status !== 401 || !canUseLocalPreviewToken()) {
          throw error;
        }
        return await getLocalPreviewPlan(lessonId);
      }
      if (response.kind !== "json" || !isPresentationResponse(response.data)) {
        if (canUseLocalPreviewToken()) return await getLocalPreviewPlan(lessonId);
        throw new Error("课件服务返回了无法识别的播放计划");
      }
      const data = response.data;
      if (canUseLocalPreviewToken() && (!data.ready || !data.plan || Object.keys(data.slides).length === 0)) {
        return await getLocalPreviewPlan(lessonId);
      }
      return { ...data, source: "api" as const };
    },
  };
}
