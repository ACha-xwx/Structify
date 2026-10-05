import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { normalizeFrame, type AnimationFrame } from "./frame";

/**
 * The engine's output must be drawable - every capability, every frame.
 *
 * The engine and the renderer are two halves of one contract and each has its own tests; nothing checked the
 * seam. A simulator that reports its content in a field the renderer does not read (or reports nothing at all)
 * passes every engine test and still shows a learner an empty canvas, and that is exactly how the "空白画面"
 * and "高亮不动" bugs got in. So this spec takes the engine's *standard* examples for all of its capabilities,
 * runs them through the real engine, and pushes every frame through the real `normalizeFrame`.
 *
 * It loads the engine directly (CommonJS, outside the frontend tsconfig) rather than over HTTP: no server, no
 * credentials, no model quota, and it fails on the pull request instead of in class.
 */
/**
 * 默认用仓里的引擎。`STRUCTIFY_ENGINE_DIR` 可以指向**另一棵树**（`git worktree add <dir> <base>` 检出的基线），
 * 用来回答"这帧同画面是这次改动引进的，还是本来就有的"——判定渲染器不变、只换引擎，差异就是引擎的。
 */
const ENGINE =
  process.env.STRUCTIFY_ENGINE_DIR ?? path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../backend/dsvp/engine");
const require = createRequire(import.meta.url);
const { ANIMATION_CAPABILITY_REGISTRY, resolveVisualizationIntent } = require(path.join(ENGINE, "animation-capabilities"));
const { simulateOperation, traceToPlayerData } = require(path.join(ENGINE, "dsvp-engine"));

/** What the learner actually sees, in one string: the panels, the pointers drawn on them, and the captions. */
function picture(frame: AnimationFrame): string {
  return JSON.stringify({
    panels: frame.panels.map((panel) => [
      panel.role,
      panel.values,
      panel.nodes,
      panel.edges,
      panel.range,
      panel.focusCell,
      panel.cursors.map((cursor) => `${cursor.key}@${cursor.index}`),
      panel.chips,
    ]),
    chips: frame.chips,
    invariant: frame.invariant,
  });
}

/** A frame with nothing on it: no panel carries content and there is no caption either. */
function blank(frame: AnimationFrame): boolean {
  return frame.panels.length === 0 && frame.chips.length === 0 && !frame.invariant;
}

interface Audited {
  capability: string;
  player: { steps: Array<Record<string, unknown>> };
}

const audited: Audited[] = [];
const notReady: string[] = [];
for (const definition of Object.values(ANIMATION_CAPABILITY_REGISTRY) as Array<{ capability: string }>) {
  const resolution = resolveVisualizationIntent(
    { needed: true, confidence: 1, capability: definition.capability, arguments: {}, sourceChunkIds: [] },
    { allowDemoFallback: true, demoSourceRef: "全能力可绘制性检查" },
  );
  if (resolution.status !== "ready") {
    notReady.push(`${definition.capability}: ${resolution.status}`);
    continue;
  }
  audited.push({
    capability: definition.capability,
    player: traceToPlayerData(simulateOperation(resolution.toolRequest.request)),
  });
}

/**
 * Frames that draw the same thing as the frame before them, as of 2026-10-06.
 *
 * The baseline (teammate's v2.0.0 tree, before this round) had **24** of them; this round fixed 8 and left 16.
 * The entries are mostly not defects but "解说/收尾" beats - 读栈顶、出栈完成、一段归并完毕 - where the caption
 * says what happened while the picture is legitimately unchanged. The point of the list is the ratchet: it may
 * only get shorter. Adding a capability here means answering "这一步真的动了什么吗" - which is exactly the
 * question that caught six hash-probe frames and a shell-sort insert frame that changed nothing.
 */
const KNOWN_IDENTICAL = new Set([
  "stack.push#3",
  "stack.pop#1",
  "stack.pop#3",
  "queue.enqueue#1",
  "queue.dequeue#3",
  "string.kmp_match#3",
  "string.kmp_match#8",
  "heap_string.insert#1",
  "tree.thread_first#2",
  "sort.merge#3",
  "sort.merge#7",
  "sort.merge#11",
  "sort.merge#17",
  "sort.merge#22",
  "sort.merge#31",
  "external_sort.multiway_merge#11",
]);

describe("engine frames are drawable", () => {
  it("resolves a runnable standard example for every capability", () => {
    expect(notReady).toEqual([]);
    expect(audited.length).toBeGreaterThanOrEqual(160);
  });

  it("gives every step a frame the renderer can paint, and never two steps in a row that look the same", () => {
    const missingFrame: string[] = [];
    const blanks: string[] = [];
    const noText: string[] = [];
    const identical: string[] = [];

    for (const { capability, player } of audited) {
      let previous: string | null = null;
      player.steps.forEach((step, index) => {
        const where = `${capability}#${index}`;
        if (!step.label || !step.note) noText.push(where);
        const state = step.dsvpState;
        if (!state) {
          missingFrame.push(where);
          previous = null;
          return;
        }
        const frame = normalizeFrame(state as never);
        if (blank(frame)) blanks.push(where);
        const now = picture(frame);
        // 两帧画得一模一样 = 学生点「下一步」什么都没发生。空面板、没画出来的高亮都是这么变成"看着像坏了"的。
        if (previous !== null && now === previous && !KNOWN_IDENTICAL.has(where)) identical.push(where);
        previous = now;
      });
    }

    expect(missingFrame).toEqual([]);
    expect(blanks).toEqual([]);
    expect(noText).toEqual([]);
    expect(identical).toEqual([]);
  });

  it("prints the ledger the animation work is tracked against", () => {
    const frames = audited.reduce((total, item) => total + item.player.steps.length, 0);
    const panels = audited.reduce(
      (total, item) =>
        total + item.player.steps.reduce((sum, step) => sum + normalizeFrame(step.dsvpState as never).panels.length, 0),
      0,
    );
    const singleFrame = audited.filter((item) => item.player.steps.length <= 1).map((item) => item.capability);
    // 台账只打印、不断言：数字变了要有人看见，但它本身不是判据。
    console.log(
      `\n能力 ${audited.length} / 帧 ${frames} / 面板 ${panels}` +
        `\n天生一帧、没有过程可播的能力 ${singleFrame.length} 个：${singleFrame.join(", ")}`,
    );
    expect(panels).toBeGreaterThan(0);
  });
});
