import type { AnimationDefinition, DsvpRequest, DsvpSimulationResponse } from "../../shared/types/contracts";
import type { UserApi } from "../api";
import { algorithmStageFixtureFor, type AlgorithmStageFixture } from "../fixtures/algorithm-stage";

export type AlgorithmStageMode = "fixture" | "live";
export type AlgorithmStageFixtureReason = "initial-preview" | "api-unavailable";

export interface AlgorithmStageSnapshot {
  mode: AlgorithmStageMode;
  definition: AnimationDefinition;
  animationRecordId: string | null;
  evidencePersisted: boolean;
  contextLabel: string;
  contextDetail: string;
  sourceLabel: string;
  sourceDetail: string;
  /** Stable scene metadata for the view, null when the response is live. */
  scene: AlgorithmStageFixture | null;
  fixture: AlgorithmStageFixture | null;
  fixtureReason: AlgorithmStageFixtureReason | null;
  trace: Record<string, unknown> | null;
}

export interface LoadAlgorithmStageOptions {
  /**
   * Initial rendering intentionally remains local so a guest can begin the
   * lesson without an implicit API request. Use `simulate` only after an
   * explicit learner action from the stage controls.
   */
  intent?: "initial" | "simulate";
  request?: DsvpRequest;
  fallbackOnUnavailable?: boolean;
  /** Lesson context used for the side-effect-free local preview. */
  chapterId?: string | null;
  lessonId?: string | null;
}

export type AlgorithmStageSimulationApi = Pick<UserApi, "simulateAnimation">;

function fixtureSnapshot(reason: AlgorithmStageFixtureReason, contextId?: string | null): AlgorithmStageSnapshot {
  const fixture = algorithmStageFixtureFor(contextId);
  return {
    mode: "fixture",
    definition: fixture.definition,
    animationRecordId: null,
    evidencePersisted: false,
    contextLabel: fixture.contextLabel,
    contextDetail: reason === "api-unavailable"
      ? "服务端模拟当前不可用，正在显示固定的本地教学预览，不会保存学习记录。"
      : fixture.contextDetail,
    sourceLabel: fixture.sourceLabel,
    sourceDetail: fixture.sourceDetail,
    scene: fixture,
    fixture,
    fixtureReason: reason,
    trace: null,
  };
}

function liveSnapshot(response: DsvpSimulationResponse): AlgorithmStageSnapshot {
  return {
    mode: "live",
    definition: response.animationData,
    animationRecordId: response.recordId || response.animationRecordId || null,
    evidencePersisted: response.evidencePersisted,
    contextLabel: "服务端 DSVP 轨迹",
    contextDetail: response.evidencePersisted
      ? "服务端已返回并确认保存该次模拟的学习证据。"
      : "服务端已返回轨迹，但尚未确认保存学习证据。",
    sourceLabel: response.resolvedChapterId ? `章节 · ${response.resolvedChapterId}` : "服务端模拟",
    sourceDetail: `匹配来源：${response.matchSource}。自定义操作、步骤和状态均来自本次 DSVP 响应。`,
    scene: null,
    fixture: null,
    fixtureReason: null,
    trace: response.trace,
  };
}

function errorStatus(cause: unknown): number | null {
  if (!cause || typeof cause !== "object" || !("status" in cause)) return null;
  const status = (cause as { status?: unknown }).status;
  return typeof status === "number" && Number.isFinite(status) ? status : null;
}

/**
 * Authentication and validation failures are intentionally not considered
 * unavailable. The caller can preserve its local login/error affordance for
 * an explicitly requested simulation instead of masking it as a demo.
 */
export function isAlgorithmStageApiUnavailable(cause: unknown): boolean {
  if (cause instanceof TypeError) return true;
  const status = errorStatus(cause);
  return status === 404 || status === 408 || status === 502 || status === 503 || status === 504;
}

export function createAlgorithmStagePreview(contextId?: string | null): AlgorithmStageSnapshot {
  return fixtureSnapshot("initial-preview", contextId);
}

/**
 * Runs a real simulation only after a caller has explicitly requested it.
 * A missing/unavailable simulation endpoint falls back to the labelled local
 * teaching scene; authorization and input errors stay visible to the caller.
 */
export async function simulateAlgorithmStage(
  api: AlgorithmStageSimulationApi,
  request: DsvpRequest,
  options: { fallbackOnUnavailable?: boolean } = {},
): Promise<AlgorithmStageSnapshot> {
  try {
    return liveSnapshot(await api.simulateAnimation(request));
  } catch (cause) {
    if (options.fallbackOnUnavailable !== false && isAlgorithmStageApiUnavailable(cause)) {
      return fixtureSnapshot(
        "api-unavailable",
        request.lessonId || request.lesson_id || request.chapterId || request.chapter_id,
      );
    }
    throw cause;
  }
}

/**
 * Convenience entry point for the view. With its default `initial` intent it
 * has no side effects and always makes the local lesson immediately usable.
 */
export async function loadAlgorithmStage(
  api: AlgorithmStageSimulationApi,
  request?: DsvpRequest,
  options: LoadAlgorithmStageOptions = {},
): Promise<AlgorithmStageSnapshot> {
  // No request, or an explicit initial intent, is side-effect free guest entry.
  if (!request || options.intent === "initial") {
    return createAlgorithmStagePreview(options.lessonId || options.chapterId);
  }
  return simulateAlgorithmStage(api, request, { fallbackOnUnavailable: options.fallbackOnUnavailable });
}
