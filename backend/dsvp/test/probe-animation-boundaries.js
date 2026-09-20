#!/usr/bin/env node
/**
 * Animation boundary probe.
 *
 * Every animation frame in this project is computed by the deterministic simulators in ./engine, so a
 * simulator that quietly swallows or reshapes its input is a teaching-correctness problem: the learner
 * sees a confident animation that does not answer the question they asked. This script exercises the
 * boundaries of the capability contract (167 capabilities, 320 declared argument slots) and the
 * DSVP request surface, and reports what the engine *actually consumed* versus what it *claims*.
 *
 * Usage:
 *   node backend/dsvp/test/probe-animation-boundaries.js            # full sweep, skips the expensive hazard case
 *   node backend/dsvp/test/probe-animation-boundaries.js --hazards  # adds the runaway-loop case (~2s, ~2GB RSS)
 *
 * Exit code is 0: this is a report, not a gate. Findings are grouped by class so a fix can be verified by
 * re-running and watching a class disappear.
 */
"use strict";

const path = require("node:path");
const engineDir = path.join(__dirname, "..", "engine");
const { simulateOperation, normalizeOperationRequest, traceToPlayerData } = require(path.join(engineDir, "dsvp-engine"));
const {
  ANIMATION_CAPABILITY_REGISTRY,
  animationCapabilityList,
  resolveVisualizationIntent
} = require(path.join(engineDir, "animation-capabilities"));

const WANT_HAZARDS = process.argv.includes("--hazards");

function resolveIntent(capability, args, allowDemoFallback) {
  return resolveVisualizationIntent(
    { needed: true, confidence: 1.0, capability, arguments: args },
    { allowDemoFallback: allowDemoFallback === true, minimumConfidence: 0.0 }
  );
}

function resolveAndSimulate(capability, args, allowDemoFallback) {
  const resolution = resolveIntent(capability, args, allowDemoFallback);
  if (resolution.status !== "ready") {
    return { status: resolution.status, detail: resolution.error || (resolution.missingArguments || []).join(",") };
  }
  try {
    const trace = simulateOperation(normalizeOperationRequest(resolution.toolRequest.request));
    return { status: "ready", request: resolution.toolRequest.request, trace, demoFallback: resolution.toolRequest.demoFallback };
  } catch (error) {
    return { status: "error", detail: `${error && (error.code || error.name)}: ${error && error.message}` };
  }
}

/** Trace fingerprint with the volatile id removed, used to detect "the argument changed nothing". */
function tracePrint(trace) {
  const copy = JSON.parse(JSON.stringify(trace));
  delete copy.trace_id;
  return JSON.stringify(copy);
}

function declaredArguments(capability) {
  const def = ANIMATION_CAPABILITY_REGISTRY[capability];
  return [...(def.requiredArguments || []), ...(def.optionalArguments || [])];
}

function summaryOf(trace) {
  return trace && trace.summary ? JSON.stringify(trace.summary) : "null";
}

const report = [];
function section(title) {
  console.log(`\n=============================== ${title} ===============================`);
}
function finding(klass, subject, detail) {
  report.push({ klass, subject, detail });
  console.log(`  [${klass}] ${subject}\n      ${detail}`);
}

const capabilities = animationCapabilityList().map((item) => item.capability);

/* ------------------------------------------------------------------ */
section("1. 基线：167 条能力的标准示例");

let baselineFailures = 0;
for (const capability of capabilities) {
  const demo = ANIMATION_CAPABILITY_REGISTRY[capability].demoArguments || {};
  const result = resolveAndSimulate(capability, demo, false);
  if (result.status !== "ready") {
    baselineFailures += 1;
    finding("BASELINE", capability, `标准示例都跑不起来：${result.status} ${result.detail}`);
    continue;
  }
  let player;
  try {
    player = traceToPlayerData(result.trace);
  } catch (error) {
    baselineFailures += 1;
    finding("BASELINE", capability, `播放数据不可用：${error.message}`);
    continue;
  }
  if (!player.steps.length) {
    baselineFailures += 1;
    finding("BASELINE", capability, "播放数据没有任何可播放步骤");
  } else if (player.steps.length === 1 && result.trace.steps.length === 1) {
    baselineFailures += 1;
    finding("BASELINE", capability, `只有 1 帧（${summaryOf(result.trace)}），播放器没有过程可播`);
  }
}
console.log(`  167 条中 ${capabilities.length - baselineFailures} 条基线正常`);

/* ------------------------------------------------------------------ */
section("2. 声明了但引擎从未读取的参数（契约说谎）");

const deadArguments = [];
for (const capability of capabilities) {
  const demo = ANIMATION_CAPABILITY_REGISTRY[capability].demoArguments || {};
  const names = declaredArguments(capability);
  if (!names.length) continue;
  const base = resolveAndSimulate(capability, demo, false);
  if (base.status !== "ready") continue;
  const basePrint = tracePrint(base.trace);
  for (const name of names) {
    const demoValue = demo[name];
    // A clearly different value of the same kind: numbers and strings move, arrays get new contents.
    // Object rows are skipped — they cannot be re-shaped here without changing the argument's meaning.
    const bump = (value, index, tier) => {
      if (typeof value === "number") return 900 + tier * 10 + index;
      if (typeof value === "string") return "Z" + tier + index + value.slice(0, 1);
      if (Array.isArray(value)) return value.map((item, i) => bump(item, i, tier + 1));
      return value;
    };
    let changed;
    if (Array.isArray(demoValue)) {
      if (demoValue.length && typeof demoValue[0] === "object" && demoValue[0] !== null && !Array.isArray(demoValue[0])) continue;
      changed = bump(demoValue, 0, 0);
    } else if (typeof demoValue === "number") changed = demoValue + 37;
    else if (typeof demoValue === "boolean") changed = !demoValue;
    else if (typeof demoValue === "string") changed = "Z" + demoValue.slice(0, 1);
    else if (demoValue === undefined) {
      // Declared but never demoed (the `directed` flag is the only such case). A boolean that changes
      // nothing in either position is dead: both true and false reproduce the canonical example exactly.
      const asTrue = resolveAndSimulate(capability, { ...demo, [name]: true }, false);
      const asFalse = resolveAndSimulate(capability, { ...demo, [name]: false }, false);
      if (
        asTrue.status === "ready" &&
        asFalse.status === "ready" &&
        tracePrint(asTrue.trace) === basePrint &&
        tracePrint(asFalse.trace) === basePrint
      ) {
        deadArguments.push({ capability, argument: name, demo: "（未提供示例值）", tried: "分别传 true 与 false" });
      }
      continue;
    } else continue;
    if (JSON.stringify(changed) === JSON.stringify(demoValue)) continue;

    const probe = resolveAndSimulate(capability, { ...demo, [name]: changed }, false);
    if (probe.status !== "ready") continue;
    if (tracePrint(probe.trace) === basePrint) {
      deadArguments.push({ capability, argument: name, demo: JSON.stringify(demoValue).slice(0, 60), tried: JSON.stringify(changed).slice(0, 60) });
    }
  }
}
for (const item of deadArguments) {
  finding("DEAD-ARG", `${item.capability}.${item.argument}`, `传 ${item.tried}（示例值 ${item.demo}）后动画与示例逐帧完全相同`);
}
console.log(`  320 个参数槽位中有 ${deadArguments.length} 个从未被读取`);

/* ------------------------------------------------------------------ */
section("3. 能力名的原型链边界");

for (const name of ["__proto__", "constructor", "toString", "hasOwnProperty", "valueOf", "prototype"]) {
  try {
    const result = resolveIntent(name, { value: 1 }, true);
    if (result.status !== "unsupported") finding("PROTO-KEY", name, `未被判为 unsupported，而是 status=${result.status}`);
    else console.log(`  ${name} -> unsupported（正确）`);
  } catch (error) {
    finding("PROTO-KEY", name, `抛出未捕获异常：${error && error.name}: ${error && error.message}`);
  }
}

/* ------------------------------------------------------------------ */
section("4. 非核心路径的宽松程度（未知参数名 / 空数组 / 类型错误）");

const LENIENT = [
  ["提示词拼错的参数名被静默忽略", "graph.dijkstra", { nodes: ["A", "B"], edges: [["A", "B", 4]], start: "A", weight: [99] }],
  ["空数组被当成缺参数", "search.binary", { initialData: [], key: 4 }],
  ["并查集传入的 parent 长度不符时被丢弃", "union_find.union", { parent: [-2, 0, -2, 2], a: 1, b: 3 }],
  ["多项式项的字段形状错误被吞成 0", "polynomial.add", { left: [[2, 5]], right: [[3, 2]] }],
  ["结点标签不存在仍然出新结点", "graph.dfs", { nodes: ["A", "B"], edges: [["A", "B", 1]], start: "Z" }],
  ["边引用未知顶点被整条丢弃", "graph.dfs", { nodes: ["A", "B"], edges: [["A", "Z"], ["Z", "B"]], start: "A" }]
];
for (const [label, capability, args] of LENIENT) {
  const result = resolveAndSimulate(capability, args, false);
  if (result.status !== "ready") {
    console.log(`  ${label} -> 被拒绝（${result.status} ${result.detail}）`);
  } else if (result.trace && Array.isArray(result.trace.errors) && result.trace.errors.length) {
    console.log(`  ${label} -> 运行期错误帧（${result.trace.errors.map((e) => e.code).join(",")}：${result.trace.errors[0].message}）`);
  } else {
    finding("LENIENT", label, `${capability} 接受请求并产出动画：${summaryOf(result.trace)}`);
  }
}

/* ------------------------------------------------------------------ */
section("5. 尺寸类参数的静默夹取");

const CLAMPED = [
  ["search.block", "blockSize", 0, { initialData: [1, 3, 5, 7, 9], key: 5, blockSize: 0 }],
  ["search.block", "blockSize", 99, { initialData: [1, 3, 5], key: 5, blockSize: 99 }],
  ["hash_table.linear_probe_search", "tableSize", 1000, { initialData: [10, 15], key: 99, tableSize: 1000 }],
  ["btree.search", "order", 2, { initialData: [10, 20, 30], key: 20, order: 2 }],
  ["btree.search", "order", 99, { initialData: [10, 20, 30], key: 20, order: 99 }],
  ["recursion.fibonacci_recursive", "n", 20, { n: 20 }],
  ["recursion.factorial_recursive", "n", 30, { n: 30 }],
  ["external_sort.multiway_merge", "ways", 99, { runs: [[1, 5], [2, 6], [3, 7]], ways: 99 }],
  ["external_sort.replacement_selection", "memorySize", 0, { input: [5, 3, 9, 1], memorySize: 0 }],
  ["union_find.union", "a/b", 99, { parent: [-1, -1, -1, -1, -1, -1], a: 99, b: -5 }]
];
for (const [capability, argument, asked, args] of CLAMPED) {
  const result = resolveAndSimulate(capability, args, false);
  if (result.status !== "ready") {
    console.log(`  ${capability}.${argument}=${asked} -> ${result.status} ${result.detail}`);
    continue;
  }
  finding("CLAMPED", `${capability}.${argument}=${asked}`, `请求原样保留 ${asked}，动画却按夹取后的值计算：${summaryOf(result.trace)}`);
}

/* ------------------------------------------------------------------ */
section("6. 超量输入被静默截断");

const oversized = Array.from({ length: 130 }, (_, i) => 130 - i);
const truncated = resolveAndSimulate("sort.bubble", { initialData: oversized }, false);
if (truncated.status === "ready") {
  const used = truncated.request.initial_state.data.length;
  if (used !== oversized.length) {
    finding("TRUNCATED", "sort.bubble initialData 130 个元素", `只有前 ${used} 个进入了动画，其余 ${oversized.length - used} 个无声消失`);
  } else {
    console.log(`  未截断（使用 ${used} 个）`);
  }
}

/* ------------------------------------------------------------------ */
section("7. 教学语义边界（技术成功但结论错误）");

const DIDACTIC = [
  ["折半查找的输入未排序", "search.binary", { initialData: [5, 1, 9, 3, 7], key: 7 }, "折半查找前提是顺序存储且有序"],
  ["折半查找的原始顺序被改写", "search.binary", { initialData: [5, 1, 9, 3, 7], key: 7 }, "首帧用输入顺序、后续帧用引擎自己排好的顺序"],
  ["拓扑排序遇到有环图", "graph.topological_sort", { nodes: ["A", "B", "C"], edges: [["A", "B", 1], ["B", "C", 1], ["C", "A", 1]] }, "应指出存在回路而不是给出空结果"],
  ["Prim 遇到不连通图", "graph.prim", { nodes: ["A", "B", "C", "D"], edges: [["A", "B", 1]], start: "A" }, "应指出图不连通而不是给出总权值"],
  ["Kruskal 遇到不连通图", "graph.kruskal", { nodes: ["A", "B", "C", "D"], edges: [["A", "B", 1]] }, "同上"],
  ["二叉排序树插入重复键", "bst.insert", { initialData: [45, 24, 53], key: 24 }, "应提示键已存在"],
  ["二叉排序树删除不存在的键", "bst.delete", { initialData: [45, 24, 53], key: 99 }, "应提示键不存在"],
  ["单链表删除位置越界", "linked_list.delete", { initialData: [10, 20, 30], position: 999 }, "顺序表同参数会报错，链表却删了最后一个结点"],
  ["树结点目标不存在", "tree.path_to_node", { initialData: ["A", "B", "C"], target: "Z" }, "只有 1 帧，播放器无过程可播"]
];
for (const [label, capability, args, expectation] of DIDACTIC) {
  const result = resolveAndSimulate(capability, args, false);
  if (result.status !== "ready") {
    console.log(`  ${label} -> 被拒绝（${result.status} ${result.detail}）`);
    continue;
  }
  if (result.trace && Array.isArray(result.trace.errors) && result.trace.errors.length) {
    console.log(`  ${label} -> 运行期错误帧（${result.trace.errors.map((e) => e.code).join(",")}：${result.trace.errors[0].message}）`);
    continue;
  }
  const frames = result.trace.steps.length;
  finding("DIDACTIC", label, `${expectation}｜实际：${frames} 帧，${summaryOf(result.trace)}`);
}

/* ------------------------------------------------------------------ */
section("8. 运行期危险输入");

const HAZARD = ["union_find.find", { parent: [-1, 1, 1, -1, -1, -1], element: 2 }];
{
  const t0 = Date.now();
  const result = resolveAndSimulate(HAZARD[0], HAZARD[1], false);
  const elapsed = Date.now() - t0;
  if (result.status !== "ready") {
    console.log(`  union_find parent 自指环 -> 已在校验层拦截（${elapsed} ms）：${result.status} ${result.detail}`);
  } else {
    finding("HAZARD", "union_find parent 自指环", `${elapsed} ms 后仍被放行：${summaryOf(result.trace)}`);
  }
}

/* ------------------------------------------------------------------ */
section("汇总");

const classes = {};
for (const item of report) classes[item.klass] = (classes[item.klass] || 0) + 1;
for (const klass of Object.keys(classes).sort()) console.log(`  ${klass.padEnd(12)} ${classes[klass]}`);
console.log(`\n  共 ${report.length} 条发现（本脚本只报告，不判定成败）`);
