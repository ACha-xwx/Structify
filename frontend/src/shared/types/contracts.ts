/** TypeScript representations of the frozen Spring v1 API contract. */

// Imported as well as re-exported below: the learning-workbench shape in this file is built on it.
import type { AnimationDefinition } from "./animation";

export type Role = "STUDENT" | "TEACHER" | "ADMIN";
export type LicenseScope = "PUBLIC" | "TEAM_ONLY" | "CLASSROOM_ONLY";

export interface User {
  id: number;
  email: string;
  username?: string | null;
  roles: Role[];
}

export interface AuthResponse {
  token: string;
  user: User;
}

export type VerificationPurpose = "register" | "reset";
export interface RequestCodeRequest {
  email: string;
  purpose: VerificationPurpose;
}
export interface VerificationCodeDelivery {
  message: string;
}
export interface LoginRequest {
  email?: string;
  username?: string;
  password: string;
}
export interface RegisterRequest {
  email: string;
  code: string;
  password: string;
}
export interface ResetPasswordRequest extends RegisterRequest {}

export interface Chapter {
  id: string;
  chapterNumber: number;
  title: string;
  summary: string;
}

export type ReviewStatus = "PUBLISHED";
export interface Resource {
  id: string;
  chapterId: string;
  type: string;
  title: string;
  description: string;
  sourceName: string;
  versionLabel: string;
  reviewStatus: ReviewStatus;
  licenseScope: LicenseScope;
  contentUrl: string | null;
}

export type KnowledgeKind = "answer" | "textbook";
export interface KnowledgeSearchResult {
  id: string;
  chapterId: string | null;
  /** Optional learning-unit context; the v1 API may omit this field. */
  lessonId?: string | null;
  title: string;
  lessonNumber: string | null;
  kind: KnowledgeKind;
  source: string;
  pageLabel: string | null;
  sourceLabel: string;
  locationLabel: string;
  reviewStatus: string;
  publicationStatus: "PUBLISHED";
  excerpt: string;
  score: number;
}
export interface KnowledgeSearchResponse {
  ok: true;
  query: string;
  results: KnowledgeSearchResult[];
}

export type ChatRole = "user" | "assistant";
export interface ChatTurn {
  role: ChatRole;
  content: string;
}
export interface ChatRequest {
  prompt: string;
  chapterId?: string;
  sessionId?: string;
  history?: ChatTurn[];
}
export interface ChatSource {
  id: string;
  chapterId: string;
  title: string;
  content: string;
  source: string;
  pageLabel: string | null;
  score: number;
  evidenceHash: string;
}
export interface ChatResponse {
  answer: string;
  sessionId?: string | null;
  sources: ChatSource[];
  persisted: boolean;
}
export interface ChatSessionSummary {
  id: string;
  chapterId: string | null;
  title: string;
  updatedAt: string;
  messageCount: number;
}
export interface ChatMessage {
  id: number;
  role: ChatRole;
  content: string;
  sources: ChatSource[];
  createdAt: string;
}
export interface ChatSession {
  id: string;
  chapterId: string | null;
  title: string;
  updatedAt: string;
  messages: ChatMessage[];
}

export interface ClassroomScript {
  id: string;
  chapterId: string;
  title: string;
  versionLabel: string;
}
export type ClassroomState = "OPENING" | "EXPLAIN" | "QUESTION" | "WAITING" | "DISCUSS" | "BLACKBOARD" | "SUMMARY";
export type ClassroomAction = "ASK" | "ANSWER" | "HINT" | "SKIP" | "PAUSE" | "RESUME" | "CONTINUE" | "FINISH";
export type ClassroomAnswerStatus = "CORRECT" | "MISCONCEPTION" | "INCORRECT";
export interface ClassroomAnswerEvaluation {
  status: ClassroomAnswerStatus;
  misconception: string | null;
  feedback: string;
}
export interface ClassroomSession {
  id: string;
  userId: number;
  scriptId: string;
  state: ClassroomState;
  paused: boolean;
  summary: string | null;
  stage: Record<string, unknown>;
  answerEvaluation?: ClassroomAnswerEvaluation | null;
}
export interface ClassroomActionRequest {
  action: ClassroomAction;
  content?: string;
  expectedRevision?: number;
}

/**
 * The animation surface lives in one place (`./animation`) so the classroom and the animation lab cannot
 * drift apart. These re-exports keep the classroom's existing import path working.
 */
export type {
  AnimationDefinition,
  AnimationObservation,
  AnimationObservationRequest,
  AnimationResponse,
  AnimationStep,
  AnimationType,
  DsvpEvidenceContext,
  DsvpRequest,
  DsvpResolution,
  DsvpSimulationResponse,
  DsvpStructure
} from "./animation";
export type { DsvpMatchSource as MatchSource } from "./animation";

export type CodeLanguage = "c" | "python";
export type CodeRunStatus = "success" | "compile_error" | "runtime_error";
export interface CodeRunRequest { language: CodeLanguage; code: string; stdin?: string; chapterId?: string }
export interface CodeRunResponse { language: CodeLanguage; status: CodeRunStatus; stdout: string; stderr: string; durationMs: number; runId: string | null }
/**
 * Starting an interactive run: either a live program (sessionId set, status "running"), or the
 * compiler refusing the code before anything ran (status "compile_error", output holds the
 * diagnosis).
 */
export interface CodeSessionStart { sessionId: string | null; status: "running" | "compile_error"; output: string }
/** One piece of output from a program that is still running. */
export interface CodeSessionChunk { stream: "stdout" | "stderr"; text: string }
/** One runnable classroom sample: the textbook listing kept verbatim, plus its driver and input. */
export interface ClassroomCodeSample {
  id: string;
  title: string;
  sourceFile: string;
  sections: string[];
  targets: string[];
  summary: string;
  stdin: string;
  expectedStdout: string;
  code: string;
}
export interface ClassroomCodeLesson {
  coursewareKey: string;
  lessonTitle: string;
  chapterId: string;
  samples: ClassroomCodeSample[];
}
export interface ClassroomCodeSamplesResponse { lessons: ClassroomCodeLesson[]; sampleCount: number }
/** A complete runnable program built around one listing, plus the output it is expected to print. */
export interface TextbookCodeExample {
  code: string;
  stdin: string;
  expectedStdout: string;
  note: string;
}
/** One class listing: verbatim source, and either a runnable example or the reason there is none. */
export interface TextbookCodeFragment {
  id: string;
  file: string;
  title: string;
  kind: "algorithm" | "type";
  startWith: string;
  code: string;
  example: TextbookCodeExample | null;
  blocked: string;
}
export interface TextbookCodeChapter { chapter: string; title: string; fragments: TextbookCodeFragment[] }
export interface TextbookCodeLibraryResponse { chapters: TextbookCodeChapter[]; fragmentCount: number }
export type CodeAnalysisRequest =
  | { runId: string }
  | { runId?: null; language: CodeLanguage; code: string; stdin?: string; stdout?: string; stderr?: string; status?: CodeRunStatus | "unknown"; chapterId?: string };
export interface CodeAnalysisResponse { analysis: string }

export interface ChapterProgress {
  chapterId: string;
  chapterNumber: number;
  title: string;
  chatCount: number;
  classroomCount: number;
  animationCount: number;
  codeRunCount: number;
  eventCount: number;
  totalActivities: number;
  lastActivityAt: string | null;
}
export interface LearningProgress { totalActivities: number; chapters: ChapterProgress[] }
/**
 * A previously persisted, renderable algorithm scene owned by the current
 * learner. The definition is intentionally separate from a simulation
 * response: resuming a scene must not fabricate a new trace or request.
 */
export interface LearningWorkbenchScene {
  recordId: string;
  chapterId: string;
  definition: AnimationDefinition;
  updatedAt: string | null;
}
export interface LearningWorkbenchProjection {
  currentChapterId: string | null;
  progress: LearningProgress;
  scene: LearningWorkbenchScene | null;
}
export type LearningEventType = "RESOURCE_VIEW" | "RESOURCE_DOWNLOAD" | "REVIEW_COMPLETED" | "WEAKNESS_RECORDED";
export interface LearningEventRequest { eventType: LearningEventType; chapterId?: string | null; referenceId?: string | null; payload?: Record<string, unknown> | null }
export interface LearningEvent { id: number; eventType: string; chapterId: string | null; referenceId: string | null; createdAt: string }

export type AiOperation = "CHAT" | "CODE_ANALYSIS" | "ANIMATION_GENERATION";
export type AiModelReason = "PERSISTED_CONFIGURATION_READY" | "PERSISTED_CONFIGURATION_DISABLED" | "PERSISTED_QUOTA_NOT_CONFIGURED" | "PERSISTED_CONFIGURATION_UNAVAILABLE" | "ENVIRONMENT_CONFIGURATION_READY" | "ENVIRONMENT_QUOTA_NOT_CONFIGURED" | "ENVIRONMENT_CONFIGURATION_INCOMPLETE";
export type AiQuotaStatus = "NOT_CONFIGURED" | "AVAILABLE" | "EXHAUSTED" | "CONCURRENCY_LIMITED";
export interface AiCurrentContext { chapterId: string | null; queryScoped: boolean }
export interface AiReadiness {
  operation: AiOperation;
  evidenceRequired: boolean;
  modelAvailable: boolean;
  modelReason: AiModelReason;
  evidenceAvailable: boolean;
  evidenceReason: "QUESTION_EVIDENCE_UNAVAILABLE" | "CONTEXT_EVIDENCE_UNAVAILABLE" | null;
  currentContext: AiCurrentContext;
  availableResourceCount: number;
  availableKnowledgeChunkCount: number;
  availableSourceCount: number;
  excludedOrUnverifiedCount: number;
  remainingDailyTokenQuota: number | null;
  quotaStatus: AiQuotaStatus;
  allowFormalGeneration: boolean;
  blockingReasons: string[];
}

export type AdminModuleStatus = "AVAILABLE" | "UNAVAILABLE" | "NOT_CONFIGURED";
export interface AdminModuleCapability { available: boolean; status: AdminModuleStatus; reason?: string | null }
export interface AdminServiceStatus { name: "spring"; version: string; status: "AVAILABLE" | "UNAVAILABLE" }
export interface AdminCapability { userId: number; roles: Role[]; modules: Record<string, AdminModuleCapability>; service: AdminServiceStatus }
export type AdminUserStatus = "ACTIVE" | "DISABLED";
export interface AdminUser { id: number; email: string; status: AdminUserStatus; disabledReason: string | null; disabledAt: string | null; roles: Role[]; createdAt: string; updatedAt: string }
export interface Page<T> { items: T[]; page: number; size: number; total: number }
export interface AdminUserStatusRequest { status: AdminUserStatus; reason?: string | null }
export interface AdminUserRolesRequest { roles: Role[] }
export interface AdminAuditEvent { id: number; actorUserId: number; action: string; targetType: string; targetId: string; result: string; requestId: string; beforeSummary: string; afterSummary: string; createdAt: string }
export interface BackgroundTask { id: number; taskType: string; status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELED"; createdAt: string; startedAt: string | null; deadlineAt: string | null; heartbeatAt: string | null; retryCount: number; maxAttempts: number; requestId: string }

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
  shouldShow: boolean;
  lessonIds: string[];
  imageUrl: string;
  section: string;
  role: string;
  terms: string[];
}

/** Why the current page is on screen, reported by the classroom API for the active step. */
export interface ClassroomSlideMatch {
  slideId: string;
  slideTitle?: string;
  kind: "DIRECT" | "CONTINUITY" | "NONE";
  score: number;
  source: "override" | "script" | "auto";
  /** Why this page: the step's own page, a declared range with no page of its own, or a fallback match. */
  reason?: "pinned" | "script-refs" | "scope-only" | "opening" | "aligned";
  subLessonId?: string;
  subLessonTitle?: string;
  scene?: string;
}
export interface PresentationDeck {
  deckId: string;
  title: string;
  chapter: string;
  slideCount: number;
  coverSlideId: string;
}
export interface LessonCourseware {
  lessonId: string;
  coursewareKey: string;
  title: string;
  source: string;
  ready: boolean;
  builtAt: string;
  slides: PresentationSlide[];
}
export interface PresentationMeta {
  ready: boolean;
  builtAt: string;
  slideCount: number;
  deckCount: number;
  lessonCount: number;
}

export type SseEvent =
  | { event: "sources"; data: { sources: ChatSource[] } | ChatSource[] }
  | { event: "delta"; data: { content?: string; delta?: string } }
  | { event: "done"; data: ChatResponse }
  | { event: "error"; data: { code?: string; message?: string; requestId?: string; details?: string[] } };
