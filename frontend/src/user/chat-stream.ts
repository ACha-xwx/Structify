import type { ChatResponse, ChatSource } from "../shared/types";
import type { MessageKey } from "../shared/i18n/messages";

/**
 * Reading the answer as it arrives.
 *
 * The shared client splits the SSE wire into events but leaves their payloads opaque, so what lands in
 * the view is `{event, data, parsed}` and the chat contract lives on the other side of that. These
 * readers own the translation - and they are pure, because the one thing that has to be right is the
 * assembly of a streamed answer: a dropped delta is a sentence with a hole in it.
 *
 * The server sends `sources`, then any number of `delta`, then exactly one `done` or one `error`.
 */

/** A wire event as the client hands it over. Narrow on purpose: only what these readers touch. */
export interface ChatWireEvent {
  event: string;
  data: string;
  parsed?: unknown;
}

export interface ChatStreamError {
  code: string;
  message: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function payloadOf(event: ChatWireEvent): unknown {
  if (event.parsed !== undefined) return event.parsed;
  try {
    return JSON.parse(event.data);
  } catch {
    return undefined;
  }
}

/** A delta carries its text as `content`; `delta` is accepted because the contract once used it. */
export function deltaOf(event: ChatWireEvent): string {
  const payload = payloadOf(event);
  if (typeof payload === "string") return payload;
  if (!isRecord(payload)) return "";
  if (typeof payload.content === "string") return payload.content;
  return typeof payload.delta === "string" ? payload.delta : "";
}

export function sourcesOf(event: ChatWireEvent): ChatSource[] {
  const payload = payloadOf(event);
  const list = Array.isArray(payload)
    ? payload
    : isRecord(payload) && Array.isArray(payload.sources)
      ? payload.sources
      : [];
  return list.filter((item): item is ChatSource => isRecord(item) && typeof item.title === "string");
}

export function doneOf(event: ChatWireEvent): ChatResponse | null {
  const payload = payloadOf(event);
  if (!isRecord(payload) || typeof payload.answer !== "string") return null;
  return payload as unknown as ChatResponse;
}

export function errorOf(event: ChatWireEvent): ChatStreamError | null {
  const payload = payloadOf(event);
  if (!isRecord(payload)) return null;
  const code = typeof payload.code === "string" ? payload.code : "";
  const message = typeof payload.message === "string" ? payload.message : "";
  if (!code && !message) return null;
  return { code: code || "CHAT_STREAM_FAILED", message };
}

/**
 * The server answers refusals with a code and a bare English sentence. A learner reads neither, so each
 * known code maps onto a written line; anything unmapped keeps the server's own wording rather than
 * being flattened into "something went wrong".
 */
export function chatErrorKey(code: string): MessageKey {
  switch (code) {
    case "CHAT_RATE_LIMITED":
      return "chat.error.rateLimited";
    case "CHAT_EVIDENCE_UNAVAILABLE":
      return "chat.error.evidence";
    case "AI_QUOTA_EXHAUSTED":
    case "AI_QUOTA_CONCURRENCY_LIMITED":
      return "chat.error.quota";
    case "AI_QUOTA_NOT_CONFIGURED":
      return "chat.error.notConfigured";
    case "CHAT_SESSION_NOT_FOUND":
      return "chat.error.sessionGone";
    case "MODEL_REQUEST_TIMEOUT":
    case "MODEL_STREAM_IDLE_TIMEOUT":
    case "CHAT_STREAM_STALLED":
      return "chat.error.timeout";
    case "CHAT_PROMPT_TOO_LONG":
      return "chat.error.tooLong";
    case "CHAT_PROMPT_REQUIRED":
      return "chat.error.empty";
    default:
      return "chat.error.failed";
  }
}
