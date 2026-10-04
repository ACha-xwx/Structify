import { renderCode } from "./chat-code-highlight";

self.onmessage = async (event: MessageEvent<{ id: number; code: string; language: string }>) => {
  const { id, code, language } = event.data;
  try { self.postMessage({ id, html: await renderCode(code, language) }); }
  catch { self.postMessage({ id, error: true }); }
};
