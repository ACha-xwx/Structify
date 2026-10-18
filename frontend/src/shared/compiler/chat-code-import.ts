/** Transfer source through navigation state without putting code in the URL. */
export const CHAT_CODE_STATE_KEY = "structifyChatCode";

/** Consume once so revisiting this history entry cannot overwrite later edits. */
export function takeChatCode(): string | undefined {
  const state = window.history.state;
  if (!state || typeof state[CHAT_CODE_STATE_KEY] !== "string") return undefined;
  const source = state[CHAT_CODE_STATE_KEY] as string;
  const remaining = { ...state };
  // Null also clears the source when Vue Router merges its cached history state later.
  remaining[CHAT_CODE_STATE_KEY] = null;
  window.history.replaceState(remaining, "");
  return source;
}
