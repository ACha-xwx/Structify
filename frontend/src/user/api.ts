import type { ApiRequest, SseApiResponse } from "../shared/api";
import type {
  AiReadiness,
  AnimationObservation,
  AnimationObservationRequest,
  AnimationResponse,
  ChatRequest,
  ChatResponse,
  ChatSession,
  ChatSessionSummary,
  ClassroomActionRequest,
  ClassroomCodeSamplesResponse,
  ClassroomScript,
  ClassroomSession,
  CodeAnalysisRequest,
  CodeAnalysisResponse,
  CodeLanguage,
  CodeRunRequest,
  CodeRunResponse,
  CodeSessionChunk,
  CodeSessionStart,
  DsvpRequest,
  DsvpResolution,
  DsvpSimulationResponse,
  LearningEvent,
  LearningEventRequest,
  LearningProgress,
  LearningWorkbenchProjection,
  LessonCourseware,
  PresentationDeck,
  PresentationMeta,
  PresentationSlide,
  Resource,
  TextbookCodeLibraryResponse,
} from "../shared/types/contracts";
import type { Chapter, KnowledgeSearchResponse } from "../shared/types/course";

interface JsonResponse<T> { kind: "json"; data: T }
interface EmptyResponse { kind: "empty"; data: undefined }
type UserRequest = ApiRequest;

function jsonData<T>(response: JsonResponse<T> | EmptyResponse): T {
  if (response.kind !== "json") throw new Error("接口未返回 JSON 数据");
  return response.data;
}

function encoded(value: string): string {
  return encodeURIComponent(value);
}

export interface KnowledgeSearchInput {
  query: string;
  chapterId?: string;
  limit?: number | string;
}

export interface UserApi {
  listChapters(): Promise<Chapter[]>;
  listResources(chapterId: string): Promise<Resource[]>;
  getResource(resourceId: string): Promise<Resource>;
  getResourceContent(resourceId: string): Promise<{ bytes: ArrayBuffer; disposition: string | null; contentType: string | null }>;
  searchKnowledge(input: KnowledgeSearchInput): Promise<KnowledgeSearchResponse>;
  getReadiness(input?: { operation?: "CHAT" | "CODE_ANALYSIS" | "ANIMATION_GENERATION"; chapterId?: string; prompt?: string }): Promise<AiReadiness>;
  chat(input: ChatRequest): Promise<ChatResponse>;
  streamChat(input: ChatRequest, signal?: AbortSignal): Promise<SseApiResponse>;
  listChatSessions(): Promise<ChatSessionSummary[]>;
  getChatSession(sessionId: string): Promise<ChatSession>;
  deleteChatSession(sessionId: string): Promise<void>;
  listClassroomScripts(chapterId?: string): Promise<ClassroomScript[]>;
  listClassroomLessons(chapterId?: string): Promise<ClassroomLesson[]>;
  prepareClassroom(lessonId: string): Promise<ClassroomPreparationStatus>;
  getClassroomPreparation(id: string): Promise<ClassroomPreparationStatus>;
  startClassroom(scriptId: string): Promise<ClassroomSession>;
  getClassroomSession(sessionId: string): Promise<ClassroomSession>;
  actInClassroom(sessionId: string, action: ClassroomActionRequest): Promise<ClassroomSession>;
  pinClassroomSlide(sessionId: string, stepIndex: number, slideId: string): Promise<ClassroomSession>;
  getPresentationMeta(): Promise<PresentationMeta>;
  getLessonCourseware(lessonId: string): Promise<LessonCourseware>;
  listPresentationDecks(): Promise<PresentationDeck[]>;
  listDeckSlides(deckId: string): Promise<PresentationSlide[]>;
  coursewareImageUrl(slideId: string): string;
  generateAnimation(input: { prompt: string; preferredType?: string; chapterId?: string }): Promise<AnimationResponse>;
  simulateAnimation(input: DsvpRequest): Promise<DsvpSimulationResponse>;
  interpretAnimation(input: {chapterId: string; prompt?: string; currentRequest?: DsvpRequest; confirmed?: boolean}): Promise<DsvpRequest>;
  /**
   * Turns an explicitly chosen capability plus arguments into an executable DSVP request. The local
   * deterministic engine does the work, so this never consumes model quota.
   */
  planAnimation(input: { capability: string; arguments?: Record<string, unknown>; sourceRef?: string }): Promise<DsvpResolution>;
  saveObservation(animationId: string, input: AnimationObservationRequest): Promise<AnimationObservation>;
  runCode(input: CodeRunRequest): Promise<CodeRunResponse>;
  /** Runs the code in a live session, so the program can ask for input while it runs. */
  startCodeSession(input: { language: CodeLanguage; code: string }): Promise<CodeSessionStart>;
  /** Watches a live session: one event per piece of output, one when the program exits. */
  streamCodeSession(sessionId: string, signal?: AbortSignal): Promise<SseApiResponse<CodeSessionChunk>>;
  /** Hands the learner's keystrokes to a program that is waiting for them. */
  typeInCodeSession(sessionId: string, text: string): Promise<void>;
  stopCodeSession(sessionId: string): Promise<void>;
  /** The runnable classroom samples, optionally narrowed to one courseware lesson (03-01). */
  listCodeSamples(coursewareKey?: string): Promise<ClassroomCodeSamplesResponse>;
  /** Every textbook listing that ships with the app, so the editor is never empty. */
  getCodeLibrary(): Promise<TextbookCodeLibraryResponse>;
  analyzeCode(input: CodeAnalysisRequest): Promise<CodeAnalysisResponse>;
  getLearningProgress(): Promise<LearningProgress>;
  getLearningWorkbench(): Promise<LearningWorkbenchProjection>;
  recordLearningEvent(input: LearningEventRequest): Promise<LearningEvent>;
}

export interface ClassroomLesson {
  id: string;
  chapterId: string;
  title: string;
  source: string;
  pages: string;
}

export interface ClassroomPreparationStatus {
  id: string;
  state: "preparing" | "ready" | "failed";
  phase: string;
  error?: string | null;
  session?: ClassroomSession | null;
}

export function createUserApi(client: { request: UserRequest }): UserApi {
  const request = client.request;
  return {
    async listChapters() { return jsonData(await request<Chapter[]>("/chapters")); },
    async listResources(chapterId) { return jsonData(await request<Resource[]>(`/chapters/${encoded(chapterId)}/resources`)); },
    async getResource(resourceId) { return jsonData(await request<Resource>(`/resources/${encoded(resourceId)}`)); },
    async getResourceContent(resourceId) {
      const response = await request(`/resources/${encoded(resourceId)}/content`, { responseType: "binary" });
      return { bytes: response.data, disposition: response.headers.get("Content-Disposition"), contentType: response.headers.get("Content-Type") };
    },
    async searchKnowledge(input) {
      return jsonData(await request<KnowledgeSearchResponse>("/knowledge/search", { query: { q: input.query, chapterId: input.chapterId, limit: input.limit } }));
    },
    async getReadiness(input = {}) {
      return jsonData(await request<AiReadiness>("/ai/readiness", { query: input }));
    },
    async chat(input) { return jsonData(await request<ChatResponse>("/chat", { method: "POST", body: input })); },
    async streamChat(input, signal) {
      const response = await request("/chat/stream", { method: "POST", body: input, signal, responseType: "sse" });
      if (response.kind !== "sse") throw new Error("接口未返回 SSE 数据流");
      return response;
    },
    async listChatSessions() { return jsonData(await request<ChatSessionSummary[]>("/chat/sessions")); },
    async getChatSession(sessionId) { return jsonData(await request<ChatSession>(`/chat/sessions/${encoded(sessionId)}`)); },
    async deleteChatSession(sessionId) { await request(`/chat/sessions/${encoded(sessionId)}`, { method: "DELETE" }); },
    async listClassroomScripts(chapterId) { return jsonData(await request<ClassroomScript[]>("/classroom/scripts", { query: { chapterId } })); },
    async listClassroomLessons(chapterId) { return jsonData(await request<ClassroomLesson[]>("/classroom/lessons", { query: { chapterId } })); },
    async prepareClassroom(lessonId) { return jsonData(await request<ClassroomPreparationStatus>("/classroom/preparations", { method: "POST", body: { lessonId } })); },
    async getClassroomPreparation(id) { return jsonData(await request<ClassroomPreparationStatus>(`/classroom/preparations/${encoded(id)}`)); },
    async startClassroom(scriptId) { return jsonData(await request<ClassroomSession>("/classroom/sessions", { method: "POST", body: { scriptId } })); },
    async getClassroomSession(sessionId) { return jsonData(await request<ClassroomSession>(`/classroom/sessions/${encoded(sessionId)}`)); },
    async actInClassroom(sessionId, action) { return jsonData(await request<ClassroomSession>(`/classroom/sessions/${encoded(sessionId)}/actions`, { method: "POST", body: action })); },
    async pinClassroomSlide(sessionId, stepIndex, slideId) { return jsonData(await request<ClassroomSession>(`/classroom/sessions/${encoded(sessionId)}/slides`, { method: "PUT", body: { stepIndex, slideId } })); },
    async getPresentationMeta() { return jsonData(await request<PresentationMeta>("/presentation/meta")); },
    async getLessonCourseware(lessonId) { return jsonData(await request<LessonCourseware>(`/presentation/lessons/${encoded(lessonId)}/slides`)); },
    async listPresentationDecks() { return jsonData(await request<PresentationDeck[]>("/presentation/decks")); },
    async listDeckSlides(deckId) { return jsonData(await request<PresentationSlide[]>(`/presentation/decks/${encoded(deckId)}/slides`)); },
    coursewareImageUrl(slideId) { return `/api/v1/presentation/slides/${encoded(slideId)}/image`; },
    async generateAnimation(input) { return jsonData(await request<AnimationResponse>("/animations/generate", { method: "POST", body: input })); },
    async simulateAnimation(input) { return jsonData(await request<DsvpSimulationResponse>("/animations/simulate", { method: "POST", body: input })); },
    async interpretAnimation(input) { return jsonData(await request<DsvpRequest>("/animations/interpret", { method: "POST", body: input })); },
    async planAnimation(input) { return jsonData(await request<DsvpResolution>("/animations/plan", { method: "POST", body: input })); },
    async saveObservation(animationId, input) { return jsonData(await request<AnimationObservation>(`/animations/${encoded(animationId)}/observations`, { method: "POST", body: input })); },
    async runCode(input) { return jsonData(await request<CodeRunResponse>("/code/runs", { method: "POST", body: input })); },
    async startCodeSession(input) { return jsonData(await request<CodeSessionStart>("/code/sessions", { method: "POST", body: input })); },
    async streamCodeSession(sessionId, signal) {
      const response = await request<CodeSessionChunk>(
        `/code/sessions/${encoded(sessionId)}/stream`,
        { method: "GET", signal, responseType: "sse" },
        { responseType: "sse", parseSseData: true },
      );
      if (response.kind !== "sse") throw new Error("接口未返回 SSE 数据流");
      return response;
    },
    async typeInCodeSession(sessionId, text) {
      await request(`/code/sessions/${encoded(sessionId)}/stdin`, { method: "POST", body: { text } });
    },
    async stopCodeSession(sessionId) {
      await request(`/code/sessions/${encoded(sessionId)}`, { method: "DELETE" });
    },
    async listCodeSamples(coursewareKey) {
      const query = coursewareKey ? `?coursewareKey=${encoded(coursewareKey)}` : "";
      return jsonData(await request<ClassroomCodeSamplesResponse>(`/code/samples${query}`));
    },
    async getCodeLibrary() { return jsonData(await request<TextbookCodeLibraryResponse>("/code/library")); },
    async analyzeCode(input) { return jsonData(await request<CodeAnalysisResponse>("/code/analyze", { method: "POST", body: input })); },
    async getLearningProgress() { return jsonData(await request<LearningProgress>("/learning/progress")); },
    async getLearningWorkbench() { return jsonData(await request<LearningWorkbenchProjection>("/learning/workbench")); },
    async recordLearningEvent(input) { return jsonData(await request<LearningEvent>("/learning/events", { method: "POST", body: input })); },
  };
}
