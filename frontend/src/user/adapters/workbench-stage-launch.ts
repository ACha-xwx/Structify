import type { AnimationDefinition, AnimationStep, DsvpRequest, DsvpStructure, LearningWorkbenchScene } from "../../shared/types/contracts";
import type { Chapter } from "../../shared/types/course";
import { simulateAlgorithmStage, type AlgorithmStageSimulationApi, type AlgorithmStageSnapshot } from "./algorithm-stage";

export type WorkbenchLiveStageStep = {
  id: string;
  label: string;
  detail: string;
};

export type WorkbenchLiveStageNode = {
  value: string | number;
  state: "base" | "active" | "inserted" | "shifted";
};

/**
 * A renderer projection for a DSVP response. It deliberately does not use the
 * workbench fixture shape: its chapter id, steps, values, and record boundary
 * all come from the explicit server response.
 */
export interface WorkbenchLiveStage {
  mode: "live";
  chapterId: string;
  title: string;
  description: string;
  structureLabel: string;
  definition: AnimationDefinition;
  steps: WorkbenchLiveStageStep[];
  animationRecordId: string | null;
  evidencePersisted: boolean;
}

type StageRecipe = {
  structure: DsvpStructure;
  operation: string;
  params: Record<string, unknown>;
  initial: unknown[];
};

function chapterStageRecipe(title: string): StageRecipe {
  const normalized = title.toLocaleLowerCase();
  if (/\u6808|stack/.test(normalized)) return { structure: "stack", operation: "push", params: { value: 23, capacity: 8 }, initial: [7, 11, 19] };
  if (/\u961f\u5217|queue|deque/.test(normalized)) return { structure: "queue", operation: "enqueue", params: { value: 23, capacity: 8 }, initial: [7, 11, 19] };
  if (/\u94fe\u8868|linked/.test(normalized)) return { structure: "linked_list", operation: "append", params: { value: 23, capacity: 8 }, initial: [7, 11, 19] };
  if (/\u56fe|graph|\u5e7f\u5ea6|\u6df1\u5ea6|bfs|dfs/.test(normalized)) return { structure: "graph", operation: "bfs", params: { node: 0, capacity: 8 }, initial: ["A", "B", "C", "D"] };
  if (/\u5806|heap/.test(normalized)) return { structure: "heap", operation: "insert", params: { value: 23, capacity: 8 }, initial: [7, 11, 19] };
  if (/\u6811|tree/.test(normalized)) return { structure: "tree", operation: "visit", params: { node: 0, capacity: 8 }, initial: [8, 4, 12] };
  if (/\u6563\u5217|hash/.test(normalized)) return { structure: "hash", operation: "put", params: { key: "k", val: "23", capacity: 8 }, initial: ["k:7"] };
  if (/\u67e5\u627e|search/.test(normalized)) return { structure: "array", operation: "get", params: { index: 1, capacity: 8 }, initial: [7, 11, 23] };
  if (/\u6392\u5e8f|sort/.test(normalized)) return { structure: "array", operation: "swap", params: { i: 0, j: 1, capacity: 8 }, initial: [23, 11, 37] };
  if (/\u987a\u5e8f\u8868|\u7ebf\u6027\u8868|\u6570\u7ec4|array|list/.test(normalized)) return { structure: "sequential_list", operation: "insert", params: { index: 2, value: 23, capacity: 8 }, initial: [7, 11, 19] };
  return { structure: "array", operation: "set", params: { index: 1, value: 23, capacity: 8 }, initial: [7, 11, 19] };
}

/**
 * The chapter comes from the authenticated course response. No catalog lesson
 * ids or local fixture source refs are sent to a request that may be recorded.
 */
export function createPublishedChapterStageRequest(chapter: Pick<Chapter, "id" | "title">): DsvpRequest {
  const chapterId = chapter.id.trim();
  if (!chapterId) throw new Error("A published chapter id is required to request an interactive stage.");

  const sourceRef = `workbench/chapter/${chapterId}/stage`;
  const recipe = chapterStageRecipe(chapter.title);
  return {
    version: "1.0",
    structure: recipe.structure,
    operation: recipe.operation,
    params: recipe.params,
    initial_state: { data: recipe.initial, metadata: { capacity: 8 } },
    chapterId,
    source_ref: sourceRef,
    context: {
      chapter_id: chapterId,
      source_type: "API",
      source_ref: sourceRef,
    },
  };
}

function cleanText(value: string, fallback: string): string {
  const trimmed = value.trim();
  return trimmed || fallback;
}

function projectSteps(definition: AnimationDefinition): WorkbenchLiveStageStep[] {
  return definition.steps.map((step, index) => ({
    id: `live-step-${index + 1}`,
    label: cleanText(step.label, step.op),
    detail: cleanText(step.note, definition.description),
  }));
}

function projectWorkbenchStage(
  chapter: Pick<Chapter, "id" | "title">,
  definition: AnimationDefinition,
  animationRecordId: string | null,
  evidencePersisted: boolean,
): WorkbenchLiveStage {
  const chapterId = chapter.id.trim();
  if (!chapterId) throw new Error("A published chapter id is required to render an interactive stage.");

  return {
    mode: "live",
    chapterId,
    title: cleanText(definition.title, chapter.title),
    description: cleanText(definition.description, chapter.title),
    structureLabel: cleanText(definition.type, "algorithm").replaceAll("_", " "),
    definition,
    steps: projectSteps(definition),
    animationRecordId,
    evidencePersisted,
  };
}

/** Convert a live DSVP response without treating it as a local lesson fixture. */
export function projectLiveWorkbenchStage(
  chapter: Pick<Chapter, "id" | "title">,
  snapshot: Pick<AlgorithmStageSnapshot, "mode" | "definition" | "animationRecordId" | "evidencePersisted">,
): WorkbenchLiveStage {
  if (snapshot.mode !== "live") throw new Error("The workbench can only project an explicit live simulation response.");

  return projectWorkbenchStage(chapter, snapshot.definition, snapshot.animationRecordId, snapshot.evidencePersisted);
}

/**
 * Restore an already persisted workbench scene without calling the simulation
 * endpoint again. The chapter match protects the stage from being displayed
 * against a different or unpublished course context.
 */
export function projectPersistedWorkbenchStage(
  chapter: Pick<Chapter, "id" | "title">,
  scene: Pick<LearningWorkbenchScene, "recordId" | "chapterId" | "definition">,
): WorkbenchLiveStage {
  const chapterId = chapter.id.trim();
  if (!chapterId || scene.chapterId.trim() !== chapterId) {
    throw new Error("A saved workbench scene must match its published chapter.");
  }
  return projectWorkbenchStage(chapter, scene.definition, scene.recordId.trim() || null, true);
}

/**
 * The stage is intentionally launched only from an explicit workbench action.
 * `fallbackOnUnavailable: false` keeps a failed live request visible instead
 * of replacing the authenticated chapter with a local fixture.
 */
export async function launchPublishedChapterStage(
  api: AlgorithmStageSimulationApi,
  chapter: Pick<Chapter, "id" | "title">,
): Promise<WorkbenchLiveStage> {
  const snapshot = await simulateAlgorithmStage(api, createPublishedChapterStageRequest(chapter), { fallbackOnUnavailable: false });
  return projectLiveWorkbenchStage(chapter, snapshot);
}

function displayNodeValue(value: unknown): string | number {
  if (typeof value === "string" || typeof value === "number") return value;
  if (typeof value === "boolean") return String(value);
  if (value === null || value === undefined) return "-";
  try {
    const serialized = JSON.stringify(value);
    return serialized && serialized.length <= 20 ? serialized : "...";
  } catch {
    return "...";
  }
}

function normalizedIndex(index: number | null | undefined, length: number, fallback: number): number {
  if (!Number.isInteger(index)) return Math.max(0, Math.min(fallback, Math.max(length - 1, 0)));
  return Math.max(0, Math.min(index as number, Math.max(length - 1, 0)));
}

function insertionIndex(step: AnimationStep, values: unknown[]): number {
  if (step.index != null) return Math.max(0, Math.min(step.index, values.length));
  return values.length;
}

function replayStep(values: unknown[], step: AnimationStep): void {
  const index = insertionIndex(step, values);
  if (["push", "enqueue", "append", "insert"].includes(step.op) && step.value !== undefined) {
    values.splice(index, 0, step.value);
  } else if (["pop", "dequeue", "delete", "extract"].includes(step.op)) {
    values.splice(normalizedIndex(step.index, values.length, step.op === "pop" ? values.length - 1 : 0), 1);
  } else if (step.op === "set" && step.value !== undefined) {
    values[normalizedIndex(step.index, values.length, 0)] = step.value;
  } else if (step.op === "swap" && step.i != null && step.j != null && values.length > 1) {
    const left = normalizedIndex(step.i, values.length, 0);
    const right = normalizedIndex(step.j, values.length, 0);
    [values[left], values[right]] = [values[right], values[left]];
  } else if (step.op === "put" && step.key) {
    values.push(`${step.key}:${step.val ?? ""}`);
  }
}

function highlightedIndex(step: AnimationStep | undefined, values: unknown[]): number {
  if (!step) return 0;
  if (step.op === "swap" && step.i != null) return normalizedIndex(step.i, values.length, 0);
  if (step.node != null) return normalizedIndex(step.node, values.length, 0);
  return normalizedIndex(step.index, values.length, values.length - 1);
}

/** Render the returned DSVP values with the workbench's existing node states. */
export function nodesAtLiveWorkbenchStep(stage: WorkbenchLiveStage, stepIndex: number): WorkbenchLiveStageNode[] {
  const values = [...stage.definition.initial];
  const completed = Math.max(0, Math.min(stepIndex + 1, stage.definition.steps.length));
  for (const step of stage.definition.steps.slice(0, completed)) replayStep(values, step);

  const active = stage.definition.steps[Math.min(Math.max(stepIndex, 0), Math.max(stage.definition.steps.length - 1, 0))];
  const focused = highlightedIndex(active, values);
  const inserted = Boolean(active && ["push", "enqueue", "append", "insert"].includes(active.op));
  return values.map((value, index) => ({
    value: displayNodeValue(value),
    state: index === focused ? (inserted ? "inserted" : "active") : "base",
  }));
}

export function liveStageValue(stage: WorkbenchLiveStage, stepIndex: number): string | number {
  const step = stage.definition.steps[Math.min(Math.max(stepIndex, 0), Math.max(stage.definition.steps.length - 1, 0))];
  if (!step) return "-";
  if (step.value !== undefined && step.value !== null) return displayNodeValue(step.value);
  if (step.val) return step.val;
  if (step.key) return step.key;
  return step.op;
}

export function liveStagePointerIndex(stage: WorkbenchLiveStage, stepIndex: number, nodeCount: number): number {
  const step = stage.definition.steps[Math.min(Math.max(stepIndex, 0), Math.max(stage.definition.steps.length - 1, 0))];
  return highlightedIndex(step, Array.from({ length: Math.max(nodeCount, 1) }));
}
