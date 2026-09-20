import type { JsonRecord } from "./api";

export type AnimationType = "stack" | "list" | "tree" | "queue" | "heap" | "hash" | "array";

/**
 * A panel of one engine frame. `role` says what the panel means (`array`, `tree`, `graph`, `table`,
 * `probe`, `meta`, ...); `values` is the payload. A tree or graph panel carries `nodes`/`edges` instead.
 * Any other key is frame metadata the header renders as a chip (front/rear/top/index/pivot/depth...).
 */
export interface DsvpPanel {
  role: string;
  values?: unknown;
  nodes?: DsvpNode[];
  edges?: unknown[];
  [key: string]: unknown;
}

export interface DsvpNode {
  id: string | number;
  label?: string;
  keys?: unknown[];
  depth?: number;
  weight?: number;
  index?: number;
  [key: string]: unknown;
}

export interface DsvpHighlights {
  nodes?: unknown[];
  edges?: unknown[];
  cells?: unknown[];
  pointers?: unknown[];
}

export interface DsvpAction {
  type?: string;
  description?: string;
  target?: unknown;
  from?: unknown;
  to?: unknown;
  value?: unknown;
  [key: string]: unknown;
}

/**
 * One authoritative frame computed by the local engine. `kind` names the structure family (38 of them
 * across the reviewed textbook); the two legacy shapes (`stack`, `queue`, `sequential_list`,
 * `sequential_list_merge`) carry their fields at the top level instead of inside `view`, which is exactly
 * what the frame normalizer in `animation/frame.ts` reconciles.
 */
export interface DsvpState {
  kind?: string;
  view?: DsvpPanel[];
  meta?: JsonRecord;
  [key: string]: unknown;
}

export interface AnimationStep {
  /** Flat value list kept for the legacy linear renderer; `dsvpState` is the authoritative frame. */
  state?: unknown[] | null;
  op: string;
  label: string;
  note: string;
  value?: string | number | boolean | JsonRecord | null;
  index?: number | null;
  node?: number | null;
  i?: number | null;
  j?: number | null;
  key?: string | null;
  val?: string | null;
  /** Teaching phase reported by the engine, e.g. `new-links` while a double list rewires pointers. */
  phase?: string | null;
  /** Engine frame: the tree, graph, table or array to draw for this step. */
  dsvpState?: DsvpState | null;
  dsvpHighlights?: DsvpHighlights | null;
  dsvpActions?: DsvpAction[] | null;
}

export interface AnimationDefinition {
  animation: true;
  type: AnimationType;
  title: string;
  description: string;
  initial: unknown[];
  steps: AnimationStep[];
}

export interface AnimationResponse {
  definition: AnimationDefinition;
  recordId?: string | null;
  persisted: boolean;
}

export interface GenerateAnimationRequest {
  prompt: string;
  preferredType?: AnimationType;
  chapterId?: string;
}

/**
 * The nine values frozen in DSVP 1.0 plus the reviewed textbook structures the local engine serves.
 * Only the frozen nine can be executed by the in-process fallback.
 */
export type DsvpStructure =
  | "stack"
  | "queue"
  | "sequential_list"
  | "linked_list"
  | "tree"
  | "graph"
  | "heap"
  | "hash"
  | "array"
  | "circular_linked_list"
  | "static_linked_list"
  | "doubly_linked_list"
  | "polynomial"
  | "double_stack"
  | "linked_stack"
  | "stack_app"
  | "recursion"
  | "linked_queue"
  | "circular_queue"
  | "queue_app"
  | "circular_buffer"
  | "string"
  | "heap_string"
  | "special_matrix"
  | "sparse_matrix"
  | "generalized_list"
  | "forest"
  | "huffman"
  | "union_find"
  | "search"
  | "bst"
  | "avl"
  | "btree"
  | "hash_table"
  | "hash_function"
  | "sort"
  | "external_sort";

export type DsvpSourceType = "API" | "CLASSROOM" | "PPT";

export interface DsvpEvidenceContext {
  chapter_id?: string;
  lesson_id?: string;
  presentation_id?: string;
  presentation_page_id?: string;
  classroom_session_id?: string;
  source_type?: DsvpSourceType;
  source_ref?: string;
}

export interface DsvpInitialState {
  data: unknown[];
  metadata?: JsonRecord;
}

export interface DsvpRequest {
  version: "1.0";
  structure: DsvpStructure;
  operation: string;
  params: JsonRecord;
  initial_state: DsvpInitialState;
  options?: JsonRecord;
  context?: DsvpEvidenceContext;
  chapter_id?: string;
  chapterId?: string;
  lesson_id?: string;
  lessonId?: string;
  presentation_id?: string;
  presentationId?: string;
  presentation_page_id?: string;
  presentationPageId?: string;
  classroom_session_id?: string;
  classroomSessionId?: string;
  source_ref?: string;
}

export type DsvpMatchSource =
  | "NONE"
  | "CLASSROOM_SESSION"
  | "PRESENTATION_PAGE"
  | "EXPLICIT_CHAPTER"
  | "ANIMATION_DEFINITION";

export interface DsvpSimulationResponse {
  protocol: "dsvp/1.0";
  request: DsvpRequest;
  trace: JsonRecord;
  animationData: AnimationDefinition;
  recordId?: string | null;
  evidencePersisted: boolean;
  animationRecordId?: string | null;
  resolvedChapterId?: string | null;
  matchSource: DsvpMatchSource;
}

export interface AnimationObservationRequest {
  observation: string;
}

export interface AnimationObservation {
  recordId: string;
  observation: string;
}

/* ---------------------------------------------------------------------------------------------------
 * The model-facing half of the animation labour split.
 *
 * The large model never emits a DSVP request and never emits a frame. It answers one question - should
 * this step be animated, which capability, with which arguments - and the local engine does the rest.
 * --------------------------------------------------------------------------------------------------- */

export type DsvpIntentStatus =
  | "ready"
  | "not-needed"
  | "low-confidence"
  | "missing-arguments"
  | "invalid-arguments"
  | "unsupported";

/** What the model is allowed to decide. `arguments` is passed through to the engine untouched. */
export interface DsvpIntent {
  needed: boolean;
  confidence: number;
  capability: string;
  purpose?: string;
  arguments: JsonRecord;
  sourceChunkIds?: string[];
}

export interface DsvpResolution {
  status: DsvpIntentStatus;
  /** The engine's registry entry for the chosen capability, when it recognised it. */
  capability: DsvpCapabilityArgument | null;
  missingArguments: string[];
  error?: string | null;
  /** Present only when `status` is `ready`: the engine-built, executable request. */
  toolRequest: {
    kind: "animation";
    protocol: string;
    capability: string;
    purpose: string;
    confidence: number;
    sourceChunkIds: string[];
    /** True when required arguments were missing and the engine filled in its canonical teaching example. */
    demoFallback: boolean;
    request: DsvpRequest;
  } | null;
}

export interface DsvpCapabilityArgument {
  capability: string;
  operation: string;
  label: string;
  description: string;
  requiredArguments: string[];
  optionalArguments: string[];
  demoArguments: JsonRecord | null;
  textbook: string;
}

export interface DsvpCapability {
  capability: string;
  operation: string;
  label: string;
  description: string;
  requiredArguments: string[];
  optionalArguments: string[];
  textbook: string;
  hasCanonicalDemo: boolean;
}

/** One structure entry of the local capability catalog (generated by scripts/build-animation-catalog.mjs). */
export interface AnimationCatalogStructure {
  structure: DsvpStructure;
  label: string;
  chapter: number;
  capabilities: DsvpCapabilityArgument[];
}

export interface AnimationCatalog {
  generatedFrom: string;
  total: number;
  structures: AnimationCatalogStructure[];
}
