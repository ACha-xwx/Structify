let worker: Worker | undefined;
let sequence = 0;
const pending = new Map<number, { resolve: (html: string) => void; reject: (error: Error) => void }>();

export async function highlightChatCode(code: string, language: string): Promise<string> {
  if (typeof Worker === "undefined") {
    const { renderCode } = await import("./chat-code-highlight");
    return renderCode(code, language);
  }
  if (!worker) {
    worker = new Worker(new URL("./chat-code.worker.ts", import.meta.url), { type: "module" });
    worker.onmessage = (event: MessageEvent<{ id: number; html: string; error?: boolean }>) => {
      const task = pending.get(event.data.id);
      pending.delete(event.data.id);
      if (event.data.error) task?.reject(new Error("Code highlighting unavailable"));
      else task?.resolve(event.data.html);
    };
    worker.onerror = () => {
      for (const task of pending.values()) task.reject(new Error("Code highlighting unavailable"));
      pending.clear();
      worker?.terminate();
      worker = undefined;
    };
  }
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    pending.set(id, { resolve, reject });
    worker!.postMessage({ id, code, language });
  });
}
