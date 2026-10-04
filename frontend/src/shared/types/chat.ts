import type { IsoDateTime } from "./api";

export type ChatRole = "user" | "assistant";

export type ChatReasoningEffort = "low" | "high" | "max";

export interface ChatAttachment {
  name: string;
  type: "file" | "image";
  mimeType: string;
  /** Decoded text, or an inline image data URL. */
  content: string;
  /** Original bytes, encoded for transport when the attachment comes from the composer. */
  rawBase64?: string;
  /** Encoding used to decode a text attachment, kept with the stored bytes. */
  encoding?: string;
  byteSize?: number;
  attachmentId?: string;
  downloadUrl?: string;
}

export interface ChatTurn {
  role: ChatRole;
  content: string;
  attachments?: ChatAttachment[];
}

export interface ChatRequest {
  prompt: string;
  chapterId?: string;
  sessionId?: string;
  history?: ChatTurn[];
  thinkingEnabled?: boolean;
  reasoningEffort?: ChatReasoningEffort;
  attachments?: ChatAttachment[];
  retryMessageId?: number;
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
  reasoning?: string | null;
}

export interface ChatSessionSummary {
  id: string;
  chapterId?: string | null;
  title: string;
  updatedAt: IsoDateTime;
  messageCount: number;
  pinned?: boolean;
}

export interface ChatMessage {
  id: number;
  role: ChatRole;
  content: string;
  sources: ChatSource[];
  createdAt: IsoDateTime;
  attachments?: ChatAttachment[];
  reasoning?: string | null;
  chapterId?: string | null;
  thinkingEnabled?: boolean | null;
  reasoningEffort?: ChatReasoningEffort | null;
  generationStatus?: "PENDING" | "COMPLETED" | "FAILED" | "STOPPED" | string | null;
  failureCode?: string | null;
}

export interface ChatSession {
  id: string;
  chapterId?: string | null;
  title: string;
  updatedAt: IsoDateTime;
  messages: ChatMessage[];
  pinned?: boolean;
}

export interface ChatStreamSourcesPayload {
  sources: ChatSource[];
}

export interface ChatStreamDeltaPayload {
  content: string;
}

export interface ChatStreamPendingPayload {
  sessionId: string;
  messageId: number;
}

export interface ChatStreamErrorPayload {
  code: string;
  message: string;
}

export type ChatStreamPayload =
  | ChatSource[]
  | ChatStreamDeltaPayload
  | ChatStreamPendingPayload
  | ChatResponse
  | ChatStreamErrorPayload;
