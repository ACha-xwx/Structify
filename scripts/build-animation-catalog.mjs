#!/usr/bin/env node
/**
 * Builds the animation-lab capability catalog from the local DSVP engine.
 *
 * The lab needs the same capability list the model sees — structure, operation, argument names, the
 * canonical teaching example, and the textbook reference — but it must not ask the backend (and therefore
 * the engine process) for it just to draw a picker. So the registry is snapshotted into a JSON module at
 * build time. Re-run this script whenever `backend/dsvp/engine/animation-capabilities.js` changes.
 *
 *   node scripts/build-animation-catalog.mjs
 */
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(here, "..");
const engineDir = resolve(projectRoot, "backend/dsvp/engine");
const target = resolve(projectRoot, "frontend/src/animation/capability-catalog.json");

const require = createRequire(import.meta.url);
const { ANIMATION_CAPABILITY_REGISTRY, animationCapabilityList } = require(resolve(engineDir, "animation-capabilities.js"));

/** Learner-facing structure names. Keys not listed fall back to the raw identifier. */
const STRUCTURE_LABELS = {
  stack: "栈", queue: "队列", double_stack: "双端顺序栈", linked_stack: "链栈", stack_app: "栈的应用",
  sequential_list: "顺序表", linked_list: "单链表", circular_linked_list: "循环单链表",
  static_linked_list: "静态链表", doubly_linked_list: "双向链表", polynomial: "一元多项式",
  recursion: "递归", linked_queue: "链队列", circular_queue: "循环队列", queue_app: "队列的应用",
  circular_buffer: "循环缓冲", string: "串", heap_string: "堆串", special_matrix: "特殊矩阵",
  sparse_matrix: "稀疏矩阵", generalized_list: "广义表", tree: "树与二叉树", forest: "树与森林",
  huffman: "哈夫曼树", union_find: "并查集", graph: "图", search: "查找", bst: "二叉排序树",
  avl: "平衡二叉树", btree: "B 树", hash_table: "哈希表", hash_function: "哈希函数构造",
  sort: "排序", external_sort: "外部排序"
};

/** Chapter buckets keep the picker navigable: 167 rows in one flat list would be unusable. */
const STRUCTURE_CHAPTERS = {
  sequential_list: 2, linked_list: 2, circular_linked_list: 2, static_linked_list: 2,
  doubly_linked_list: 2, polynomial: 2,
  stack: 3, double_stack: 3, linked_stack: 3, stack_app: 3, recursion: 3, linked_queue: 3,
  circular_queue: 3, queue_app: 3, circular_buffer: 3, queue: 3,
  string: 4, heap_string: 4,
  special_matrix: 5, sparse_matrix: 5, generalized_list: 5,
  tree: 6, forest: 6, huffman: 6, union_find: 6,
  graph: 7,
  search: 8, bst: 8, avl: 8, btree: 8, hash_table: 8, hash_function: 8,
  sort: 9, external_sort: 10
};

const byStructure = new Map();
for (const item of animationCapabilityList()) {
  const def = ANIMATION_CAPABILITY_REGISTRY[item.capability];
  if (!byStructure.has(item.structure)) byStructure.set(item.structure, []);
  byStructure.get(item.structure).push({
    capability: item.capability,
    operation: item.operation,
    label: item.label,
    description: item.description,
    requiredArguments: item.requiredArguments,
    optionalArguments: item.optionalArguments,
    demoArguments: def?.demoArguments ?? {},
    textbook: item.textbook ?? ""
  });
}

const structures = [...byStructure.entries()]
  .map(([structure, capabilities]) => ({
    structure,
    label: STRUCTURE_LABELS[structure] ?? structure,
    chapter: STRUCTURE_CHAPTERS[structure] ?? null,
    capabilities: capabilities.sort((a, b) => a.label.localeCompare(b.label, "zh-Hans-CN"))
  }))
  .sort((a, b) => (a.chapter ?? 99) - (b.chapter ?? 99) || a.label.localeCompare(b.label, "zh-Hans-CN"));

const catalog = {
  generatedFrom: "backend/dsvp/engine/animation-capabilities.js",
  total: animationCapabilityList().length,
  structures
};

mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, `${JSON.stringify(catalog, null, 2)}\n`, "utf8");
console.log(`animation catalog written: ${structures.length} structures, ${catalog.total} capabilities -> ${target}`);
