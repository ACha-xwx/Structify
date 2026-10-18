import type { ChatSource } from "../shared/types";
import type { DsvpSimulationResponse } from "../shared/types/animation";
import type { ChatWireEvent } from "./chat-stream";

const sources: ChatSource[] = [
  {
    id: "local-demo-main", chapterId: "ch01", title: "C 语言入门：main 程序入口",
    content: "C 程序从 main 函数开始执行，返回 0 表示正常结束。",
    source: "本地示例知识库", pageLabel: null, score: 0.96, evidenceHash: "local-demo-main",
  },
  {
    id: "local-demo-printf", chapterId: "ch01", title: "标准输入输出：printf 与换行",
    content: "stdio.h 声明 printf；字符串中的 \\n 表示换行。",
    source: "本地示例知识库", pageLabel: null, score: 0.93, evidenceHash: "local-demo-printf",
  },
];

const reasoning = "已从示例知识库找到程序入口和标准输出的相关内容。\n\n先给出一个最小的 C 程序：包含 stdio.h，在 main 函数里用 printf 输出问候语，最后返回 0。\n\n还需要说明字符串中的换行符，并把代码和运行结果分别放进代码块，方便阅读和复制。";

const answer = [
  "这是一个简单的 **Hello World** C 程序：",
  "",
  "```c",
  "#include <stdio.h>",
  "",
  "int main(void)",
  "{",
  '    printf("Hello, world!\\n");',
  "    return 0;",
  "}",
  "```",
  "",
  "运行结果：",
  "",
  "```text",
  "Hello, world!",
  "```",
  "",
  "- `#include <stdio.h>` 引入标准输入输出函数的声明。",
  "- `main` 是程序的入口。",
  "- `printf` 输出文字，`\\n` 表示换行。",
  "- `return 0` 表示程序正常结束。",
].join("\n");

function event(name: string, parsed: unknown): ChatWireEvent {
  return { event: name, parsed, data: JSON.stringify(parsed) };
}

function pause(milliseconds: number, signal: AbortSignal): Promise<void> {
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      clearTimeout(timer);
      signal.removeEventListener("abort", onAbort);
      reject(signal.reason);
    };
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, milliseconds);
    signal.addEventListener("abort", onAbort, { once: true });
  });
}

/** Feed the normal reply pipeline; this module is imported only in local development. */
export async function* streamLocalDemo(signal: AbortSignal): AsyncGenerator<ChatWireEvent> {
  await pause(900, signal);
  yield event("sources", sources);
  await pause(350, signal);

  const thoughts = Array.from(reasoning);
  for (let index = 0; index < thoughts.length; index += 7) {
    await pause(160, signal);
    yield event("reasoning", { content: thoughts.slice(index, index + 7).join("") });
  }

  await pause(400, signal);
  const response = Array.from(answer);
  for (let index = 0; index < response.length; index += 12) {
    await pause(55, signal);
    yield event("delta", { content: response.slice(index, index + 12).join("") });
  }
  signal.throwIfAborted();
  yield event("done", { answer, reasoning, sources, persisted: false });
}

export function localDemoAnimation(): DsvpSimulationResponse {
  const characters = Array.from("Hello, world!");
  return {
    protocol: "dsvp/1.0",
    request: { version: "1.0", structure: "array", operation: "traverse", params: {}, initial_state: { data: characters } },
    trace: {}, evidencePersisted: false, matchSource: "NONE",
    animationData: {
      animation: true, type: "array", title: "Hello World 字符序列",
      description: "依次查看 Hello, world! 中的字符。", initial: characters,
      steps: characters.map((character, index) => ({
        op: "visit", label: `查看第 ${index + 1} 个字符`,
        note: `当前字符：${character === " " ? "空格" : character}`,
        index, state: characters,
        dsvpState: { kind: "array", view: [{ role: "array", values: characters, focusIndex: index }] },
      })),
    },
  };
}
