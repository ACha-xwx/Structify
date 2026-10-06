<script setup lang="ts">
import { computed } from "vue";
import { chipLabel, frameValueText, normalizeFrame, type AnimationFrame, type FramePanel } from "./frame";
import { useI18n } from "../shared/i18n/locale";
import type { AnimationStep, DsvpNode, DsvpState } from "../shared/types/animation";

/**
 * Draws one engine frame. Shared by the animation lab and the in-classroom demo so a learner sees the
 * same picture in both places.
 *
 * Colours come from local `--stage-*` variables that fall back to the app theme, so the renderer follows
 * the theme the page around it is standing on and both uses stay identical.
 */
const props = defineProps<{
  step: AnimationStep | null;
  /** A frame handed over directly, used for the initial state before any step has run. */
  state?: DsvpState | null;
  emptyLabel?: string;
}>();

const { t } = useI18n();

/**
 * The cursors standing on one cell: `i` and `j` can share a cell, and a step may be reading while a stack
 * marker sits elsewhere. Drawing them all is the point - a single highlight cannot say "i is here, j is
 * there", which is exactly what a learner has to see during a compare or a partition.
 */
function cursorsOf(panel: FramePanel, cell: number) {
  return panel.cursors.filter((cursor) => cursor.index === cell);
}

/** The lead cursor of a panel is the one the step is operating on; it gets the filled pill. */
function isLeadCursor(panel: FramePanel, cursor: { key: string }): boolean {
  return panel.cursors.length > 0 && panel.cursors[0].key === cursor.key;
}

/**
 * 尾结点末端要不要画"绕回头结点"的那根箭头。
 *
 * 不能只看结构形状（`chain.circular`）：循环表**被改过指针之后**，尾结点的 next 可能已经指向别的结点
 * （合并第一行 `p->next = B->next` 之后，A 的尾结点指向的是 2，不再指回自己的头结点）。
 * 所以以引擎给的槽位文字为准：末结点的 next 确实解析成头结点时才画那根绕回箭头。
 */
function chainWraps(panel: FramePanel): boolean {
  if (!panel.chain?.circular || panel.values.length === 0) return false;
  const lastNext = panel.chainText?.next?.[panel.values.length - 1];
  if (lastNext === undefined) return true;
  return lastNext === String(panel.values[0]);
}

/**
 * 结点框里某一栏写什么。
 *
 * **优先用引擎给的文字**（`panel.chainText.next[i]`）——引擎按结构定义知道第 i 个结点的 next 此刻指向谁，
 * 前端不该再推一份。引擎没给才退回按形状推：`next`/`prior` 在链表两端本来就没有指向（写 `NULL`），
 * 唯一例外是循环链表，它的末结点 `next` 指回 `head`。
 */
function chainSlotText(panel: FramePanel, cell: number, slot: string): string {
  const fromEngine = panel.chainText?.[slot]?.[cell];
  if (typeof fromEngine === "string") return fromEngine;
  // 引擎没给就按**结点顺序**推：next 指向下一个结点、prior 指向上一个，两端是 NULL
  // （循环链表的末结点 next 绕回首结点）。注意这里返回的是"指向谁"，不是字段名——
  // 字段名由槽位上那行小字给，两者各占一行。
  const labels = panel.values.map((value) => (isEmptyValue(value) ? "∅" : frameValueText(value)));
  const last = labels.length - 1;
  if (slot === "next") return cell < last ? labels[cell + 1] : panel.chain?.circular ? labels[0] ?? "head" : "NULL";
  if (slot === "prior") return cell === 0 ? "NULL" : labels[cell - 1];
  return "NULL";
}

const frame = computed<AnimationFrame>(() => normalizeFrame(props.state ?? props.step?.dsvpState ?? null));
// A frame whose only content is metadata still says something (n, result, front/rear...); treating it as
// "nothing to render" made such a step a completely blank canvas.
const hasContent = computed(() => frame.value.panels.length > 0 || frame.value.chips.length > 0);
const treePanels = computed(() => frame.value.panels.filter((panel) => panel.kind === "tree" || panel.kind === "graph"));
const flatPanels = computed(() => frame.value.panels.filter((panel) => panel.kind !== "tree" && panel.kind !== "graph"));

/**
 * The one cell a grid step stands on. When the engine names it the mark is exact; otherwise the fallback
 * keeps the older behaviour of outlining a whole column.
 */
function isFocusCell(panel: FramePanel, row: number, column: number): boolean {
  if (panel.focusCell) return panel.focusCell[0] === row && panel.focusCell[1] === column;
  return panel.focus === column;
}

/**
 * 链地址法的桶：`focusIndex` 亮整条同义词链（"正在看这一条"），`focusCell=[桶号, 链内序号]`
 * 再往下精确到链上的某个结点（"正在比较这一个"）。没有这两条时 11 条链一条都不亮。
 */
function isFocusBucket(panel: FramePanel, row: number): boolean {
  if (panel.focusCell) return panel.focusCell[0] === row;
  return panel.focus === row;
}

function isFocusBucketEntry(panel: FramePanel, row: number, cell: number): boolean {
  return panel.focusCell !== null && panel.focusCell[0] === row && panel.focusCell[1] === cell;
}

/** 空槽：引擎给的占位（`null`）或真的空值，都画成虚线空位而不是"一个没有内容的格子"。 */
function isEmptyValue(value: unknown): boolean {
  return value === null || value === undefined || value === "";
}

/** Node ids the engine has already finished with, so the renderer can fade them. */
function doneIds(panel: FramePanel): Set<string> {
  const done = new Set<string>();
  for (const key of ["visited", "settled"]) {
    const value = frame.value.raw[key];
    if (!Array.isArray(value)) continue;
    value.forEach((item) => {
      const text = String(item);
      done.add(text);
      // A tree reports visited labels while a graph reports visited ids; accept either.
      const match = panel.nodes.find((node) => node.label === text);
      if (match) done.add(String(match.id));
    });
  }
  return done;
}

/**
 * 一个结点的"关键字集合"文本：B 树的 label 是 `"30 | 40"`，meta 里的 current 是 `[30,40]`。
 * 两边都归一成 `"30,40"` 才比得上——不然整族 B 树动画一格高亮都没有。
 */
function keySetOf(label: string): string {
  return label.split(/[|,、\s]+/).filter(Boolean).join(",");
}

/** The single node the step is standing on. */
function activeId(panel: FramePanel): string | null {
  const current = frame.value.raw.current;
  // 引擎报"当前结点"的口径不止一种：图/线索树报结点 id，BST 报**关键字**（id 只是建树序号），
  // B 树报结点里的关键字**组**（数组 [30,40]），还有的报 label。少认一种，整族动画就一格高亮都没有。
  if (typeof current === "string" || typeof current === "number" || Array.isArray(current)) {
    const text = Array.isArray(current) ? current.map((item) => String(item)).join(",") : String(current);
    const hit = panel.nodes.find((node) =>
      String(node.id) === text
      || (node.key !== undefined && String(node.key) === text)
      || (Array.isArray(node.keys) && node.keys.map((item) => String(item)).join(",") === text)
      || (node.label !== undefined && (String(node.label) === text || keySetOf(String(node.label)) === text)));
    if (hit) return String(hit.id);
  }
  const currentValue = frame.value.raw.currentValue;
  if (typeof currentValue === "string") {
    const match = panel.nodes.find((node) => node.label === currentValue);
    if (match) return String(match.id);
  }
  return null;
}

/** Per-node annotations straight from the engine (`dist` for Dijkstra, `indegree` for topological sort). */
function nodeNotes(panel: FramePanel): Record<string, string> {
  const notes: Record<string, string> = {};
  for (const key of ["dist", "indegree"]) {
    const value = frame.value.raw[key];
    if (!value || typeof value !== "object" || Array.isArray(value)) continue;
    for (const [id, item] of Object.entries(value as Record<string, unknown>)) {
      notes[id] = notes[id] ? `${notes[id]} · ${chipLabel(key)} ${frameValueText(item)}` : `${chipLabel(key)} ${frameValueText(item)}`;
    }
  }
  return notes;
}

/** 一条边：`["A","B"]` / `["A","B",权重]`；也接受"边的列表"并取最后一条。 */
function asEdge(value: unknown): [string, string] | null {
  if (!Array.isArray(value) || value.length < 2) return null;
  if (Array.isArray(value[0])) return asEdge(value[value.length - 1]);
  return [String(value[0]), String(value[1])];
}

/**
 * The edge a relaxation or traversal step just walked, drawn emphasised.
 *
 * 引擎报"这一步的边"的口径随算法不同：最短路报 `relax`，最小生成树报 `chosenEdges`（已选边集合，
 * 最后一条才是刚选的）或 `skipped`（这一步跳过的边）。只认 `relax` 时 Kruskal/Prim 这类动画
 * 一条边都不高亮——选边过程完全看不出来。
 */
const activeEdge = computed<[string, string] | null>(() => {
  for (const key of ["relax", "chosenEdges", "skipped", "selectedEdge", "edge"]) {
    const edge = asEdge(frame.value.raw[key]);
    if (edge) return edge;
  }
  return null;
});

interface PlacedNode {
  node: DsvpNode;
  x: number;
  y: number;
  depth: number;
  key: string;
  state: "idle" | "done" | "active";
  note: string;
}

interface TreeLayout {
  width: number;
  height: number;
  nodes: PlacedNode[];
  edges: { x1: number; y1: number; x2: number; y2: number; active: boolean; loop: boolean }[];
  nodeWidth: (node: DsvpNode) => number;
}

const NODE_HEIGHT = 38;
const LEVEL_HEIGHT = 78;
const GAP_X = 22;
const PAD = 26;
/** A lone child would otherwise hang dead-center below its parent; binary trees
 * distinguish left/right subtrees, so the parent is biased sideways by this much. */
const SIDE_BIAS = 26;

/**
 * Tidy layout: leaves are spread left to right and every parent sits above the middle of its children.
 * The engine gives topology, not coordinates, so the geometry is computed here and is therefore the same
 * on every render.
 */
function layoutTree(panel: FramePanel): TreeLayout {
  const children = new Map<string, string[]>();
  const childSide = new Map<string, "L" | "R">();
  const hasParent = new Set<string>();
  const order: string[] = [];
  panel.nodes.forEach((node, index) => order.push(String(node.id ?? index)));
  panel.edges.forEach((edge) => {
    if (!Array.isArray(edge) || edge.length < 2) return;
    const parent = String(edge[0]);
    const child = String(edge[1]);
    if (!children.has(parent)) children.set(parent, []);
    children.get(parent)!.push(child);
    hasParent.add(child);
    // Side hint: engines tag edges "L"/"R" explicitly (BST rotations) or rely on
    // heap-array indexing (child === 2*parent+1 / 2*parent+2) for level-order trees.
    const rawSide = typeof edge[2] === "string" ? edge[2].trim().toUpperCase() : "";
    let side: "L" | "R" | "" = rawSide === "L" || rawSide === "R" ? rawSide : "";
    if (!side) {
      const p = Number(edge[0]);
      const c = Number(edge[1]);
      if (Number.isInteger(p) && Number.isInteger(c)) {
        if (c === p * 2 + 1) side = "L";
        else if (c === p * 2 + 2) side = "R";
      }
    }
    if (side) childSide.set(`${parent}->${child}`, side);
  });

  const roots = order.filter((id) => !hasParent.has(id));
  const effectiveRoots = roots.length ? roots : order;

  const widthOf = (node: DsvpNode) => {
    const keys = Array.isArray(node.keys) && node.keys.length > 1 ? node.keys.length : 1;
    const text = String(node.label ?? "");
    return Math.max(46, Math.min(140, keys * 30 + 16, text.length * 15 + 22));
  };
  const byId = new Map<string, DsvpNode>();
  panel.nodes.forEach((node, index) => byId.set(String(node.id ?? index), node));

  const positions = new Map<string, { x: number; depth: number }>();
  let cursor = 0;
  let maxDepth = 0;
  const seen = new Set<string>();

  const walk = (id: string, depth: number): number => {
    if (seen.has(id)) return cursor;
    seen.add(id);
    maxDepth = Math.max(maxDepth, depth);
    const kids = children.get(id) ?? [];
    let x: number;
    if (!kids.length) {
      const node = byId.get(id);
      const half = node ? widthOf(node) / 2 : 24;
      x = cursor + half;
      cursor += (node ? widthOf(node) : 48) + GAP_X;
    } else {
      const kidXs = kids.map((kid) => walk(kid, depth + 1));
      x = (Math.min(...kidXs) + Math.max(...kidXs)) / 2;
      // A single child would make the parent sit exactly above it (a vertical stem).
      // When the child's side is known, lean the parent the other way so the edge
      // reads as a proper left/right subtree link.
      if (kids.length === 1) {
        const side = childSide.get(`${id}->${kids[0]}`);
        if (side === "L") x = kidXs[0] + SIDE_BIAS;
        else if (side === "R") x = kidXs[0] - SIDE_BIAS;
      }
    }
    positions.set(id, { x, depth });
    return x;
  };
  effectiveRoots.forEach((root) => {
    walk(root, 0);
    cursor += GAP_X;
  });
  // A cycle or a detached node would otherwise never be placed.
  order.forEach((id) => {
    if (!positions.has(id)) walk(id, 0);
  });

  const done = doneIds(panel);
  const active = activeId(panel);
  const notes = nodeNotes(panel);
  const nodes: PlacedNode[] = order.map((id) => {
    const node = byId.get(id) as DsvpNode;
    const placed = positions.get(id) ?? { x: 0, depth: 0 };
    return {
      node,
      x: placed.x,
      y: PAD + placed.depth * LEVEL_HEIGHT,
      depth: placed.depth,
      key: id,
      state: active === id ? "active" : done.has(id) ? "done" : "idle",
      note: notes[node.label ?? ""] ?? notes[id] ?? "",
    };
  });
  const positionOf = new Map(nodes.map((placed) => [placed.key, placed]));

  // 内容比画布窄时整块居中，否则「只有一个头结点」这种结构会孤零零贴在左边。
  // 必须在算边坐标**之前**平移，否则线（和自环）会留在原地，与结点脱节。
  const contentWidth = cursor + PAD;
  const width = Math.max(320, contentWidth);
  const shift = (width - contentWidth) / 2;
  if (shift > 0) nodes.forEach((placed) => { placed.x += shift; });

  const edges = panel.edges
    .map((edge) => {
      if (!Array.isArray(edge) || edge.length < 2) return null;
      const from = positionOf.get(String(edge[0]));
      const to = positionOf.get(String(edge[1]));
      if (!from || !to) return null;
      const isActive = activeEdge.value !== null
        && ((activeEdge.value[0] === from.key && activeEdge.value[1] === to.key)
          || (activeEdge.value[0] === to.key && activeEdge.value[1] === from.key));
      // 自环（循环链表的 head.next = head）是一条零长度的线，画不出来：改成结点右侧的弧。
      if (from.key === to.key) {
        const node = byId.get(from.key);
        const half = (node ? widthOf(node) : 46) / 2;
        return { x1: from.x + half, y1: from.y - 10, x2: from.x + half, y2: from.y + 10, active: isActive, loop: true };
      }
      return {
        x1: from.x,
        y1: from.y + NODE_HEIGHT / 2,
        x2: to.x,
        y2: to.y - NODE_HEIGHT / 2,
        active: isActive,
        loop: false,
      };
    })
    .filter((edge): edge is TreeLayout["edges"][number] => edge !== null);

  return {
    width,
    height: PAD * 2 + maxDepth * LEVEL_HEIGHT + NODE_HEIGHT,
    nodes,
    edges,
    nodeWidth: widthOf,
  };
}

const treeLayouts = computed(() => treePanels.value.map((panel) => layoutTree(panel)));

interface GraphLayout {
  width: number;
  height: number;
  nodes: { node: DsvpNode; x: number; y: number; key: string; state: "idle" | "done" | "active"; note: string }[];
  edges: { x1: number; y1: number; x2: number; y2: number; label: string; active: boolean; directed: boolean }[];
}

const GRAPH_SIZE = 300;
const GRAPH_RADIUS = 108;

/** A circle is the one layout that is deterministic, readable and needs no coordinates from the engine. */
function layoutGraph(panel: FramePanel, directed: boolean): GraphLayout {
  const done = doneIds(panel);
  const active = activeId(panel);
  const notes = nodeNotes(panel);
  const count = Math.max(1, panel.nodes.length);
  const center = GRAPH_SIZE / 2;
  const placed = panel.nodes.map((node, index) => {
    const id = String(node.id ?? index);
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / count;
    return {
      node,
      key: id,
      x: center + GRAPH_RADIUS * Math.cos(angle),
      y: center + GRAPH_RADIUS * Math.sin(angle),
      state: (active === id ? "active" : done.has(id) ? "done" : "idle") as "idle" | "done" | "active",
      note: notes[node.label ?? ""] ?? notes[id] ?? "",
    };
  });
  const byId = new Map(placed.map((item) => [item.key, item]));
  const edges = panel.edges
    .map((edge) => {
      if (!Array.isArray(edge) || edge.length < 2) return null;
      const from = byId.get(String(edge[0]));
      const to = byId.get(String(edge[1]));
      if (!from || !to) return null;
      const weight = edge.length > 2 ? frameValueText(edge[2]) : "";
      const isActive = activeEdge.value !== null
        && ((activeEdge.value[0] === from.key && activeEdge.value[1] === to.key)
          || (activeEdge.value[0] === to.key && activeEdge.value[1] === from.key));
      // Stop the line at the node rim so an arrowhead (directed graphs) lands visibly on the circle.
      const dx = to.x - from.x;
      const dy = to.y - from.y;
      const length = Math.hypot(dx, dy) || 1;
      const offset = (length > 60 ? 26 : length / 2 - 2);
      return {
        x1: from.x + (dx / length) * offset,
        y1: from.y + (dy / length) * offset,
        x2: to.x - (dx / length) * offset,
        y2: to.y - (dy / length) * offset,
        label: weight === "1" ? "" : weight,
        active: isActive,
        directed,
      };
    })
    .filter((edge): edge is GraphLayout["edges"][number] => edge !== null);
  return { width: GRAPH_SIZE, height: GRAPH_SIZE, nodes: placed, edges };
}

const graphLayouts = computed(() => {
  const directed = frame.value.raw.directed === true;
  return treePanels.value.map((panel) => (panel.kind === "graph" ? layoutGraph(panel, directed) : null));
});
</script>

<template>
  <div class="stage" :class="{ 'stage--empty': !hasContent }">
    <template v-if="hasContent">
      <!-- The engine's meta fields (front/rear/top/k/formula/stored...) belong to the frame, not to one
           panel; without this row they were computed and then dropped, so a step that reported "k = 7"
           showed nothing at all. -->
      <!-- 不变式条：这一步"结构上什么成立"（"下标 0 到 i-1 已经排好"）。给学生的是能带到下一步去的那句话，
           所以它是这一帧的标题句、单独占一行放在画布上方，而不是混进下面那排字段胶囊里。 -->
      <p v-if="frame.invariant" class="stage__invariant">{{ frame.invariant }}</p>
      <div v-if="frame.chips.length" class="stage__meta">
        <span v-for="chip in frame.chips" :key="`meta-${chip.label}`" class="chip"><i>{{ chip.label }}</i>{{ chip.value }}</span>
      </div>
      <div v-for="(panel, index) in flatPanels" :key="`flat-${index}-${panel.role}`" class="panel">
        <p class="panel__label">{{ panel.label }}</p>

        <template v-if="panel.kind === 'array' || panel.kind === 'text'">
          <!-- 空的顺序结构：容量已知就摆出空槽，容量未知就给一个大「空」字——空白画布说明不了任何事。 -->
          <p v-if="!panel.values.length" class="panel__vacant">{{ t("stage.emptyBucket") }}</p>
          <!-- 链表家族：值本身没说明力，链接才是重点，所以画成结点框 + 箭头，而不是一排格子。 -->
          <ol v-else-if="panel.chain" class="chain">
            <li
              v-for="(value, cell) in panel.values"
              :key="`node-${cell}`"
              class="chain__node"
              :class="{ 'chain__node--focus': panel.focus === cell }"
            >
              <span class="chain__marks">
                <span v-if="cell === 0" class="cursor cursor--primary">head</span>
                <span
                  v-for="cursor in cursorsOf(panel, cell)"
                  :key="`${cell}-${cursor.key}`"
                  class="cursor"
                  :class="{ 'cursor--primary': cell === 0 && isLeadCursor(panel, cursor) }"
                >{{ cursor.label }}</span>
              </span>
              <span class="chain__body">
                <!-- 结点里有哪些槽位、写什么，全部来自结构定义（`panel.chain`）：双向链表多一格 prior，
                     循环链表末结点的 next 写 head 而不是 NULL。以前这两样都被写死成单链表的画法。
                     每一格**两行**：上面是字段名（prior / next），下面是它此刻指向谁——
                     只写目标值就看不出哪格是哪个字段，只写字段名又看不出"next 指向谁"。 -->
                <span
                  v-if="panel.chain.lead"
                  class="chain__slot chain__slot--lead"
                  :class="{ 'chain__slot--writing': panel.chainWrite?.slot === panel.chain.lead && panel.chainWrite.index === cell }"
                >
                  <i class="chain__slot-name">{{ panel.chain.lead }}</i>
                  <b class="chain__slot-target">{{ chainSlotText(panel, cell, panel.chain.lead) }}</b>
                </span>
                <span class="chain__value">{{ isEmptyValue(value) ? "∅" : frameValueText(value) }}</span>
                <!-- 正在被改写的那一格点亮——帧的标题就是那行代码（`p->next = B->next`），一格一亮对应一行。
                     改的是**哪个字段**由引擎说了算（`p->next->prior = s` 亮的是 prior 格，不是 next 格）。 -->
                <span
                  v-for="slot in panel.chain.slots"
                  :key="slot"
                  class="chain__slot"
                  :class="{ 'chain__slot--writing': panel.chainWrite?.slot === slot && panel.chainWrite.index === cell }"
                >
                  <i class="chain__slot-name">{{ slot }}</i>
                  <b class="chain__slot-target">{{ chainSlotText(panel, cell, slot) }}</b>
                </span>
              </span>
              <!-- 结点之间：正向一根；双向链表再在下面补一根反向的（prior 指向前驱）。 -->
              <span v-if="cell < panel.values.length - 1" class="chain__links">
                <span class="chain__link" aria-hidden="true"></span>
                <span v-if="panel.chain.back" class="chain__link chain__link--back" aria-hidden="true"></span>
              </span>
              <!-- 循环链表：尾结点的 next 绕回首结点时，明写成一根回到 head 的箭头。 -->
              <span v-else-if="chainWraps(panel)" class="chain__links chain__links--wrap">
                <span class="chain__link" aria-hidden="true"></span>
                <span class="chain__wrap">{{ t("stage.backToHead") }}</span>
              </span>
            </li>
          </ol>
          <ol v-else class="cells">
            <li
              v-for="(value, cell) in panel.values"
              :key="`${cell}-${value}`"
              class="cell"
              :class="{ 'cell--empty': isEmptyValue(value), 'cell--focus': panel.focus === cell, 'cell--inRange': panel.range && cell >= panel.range[0] && cell < panel.range[1] }"
            >
              <span v-if="cursorsOf(panel, cell).length" class="cell__cursors">
                <span
                  v-for="cursor in cursorsOf(panel, cell)"
                  :key="`${cell}-${cursor.key}`"
                  class="cursor"
                  :class="{ 'cursor--primary': isLeadCursor(panel, cursor) }"
                >{{ cursor.label }}</span>
              </span>
              <span v-if="!isEmptyValue(value)" class="cell__value">{{ frameValueText(value) }}</span>
              <span class="cell__index">{{ cell }}</span>
            </li>
          </ol>
        </template>

        <div v-else-if="panel.kind === 'matrix' && panel.variant === 'bucket'" class="buckets">
          <div
            v-for="(bucket, row) in panel.rows"
            :key="`bucket-${row}`"
            class="bucket"
            :class="{ 'bucket--focus': isFocusBucket(panel, row) }"
          >
            <span class="bucket__index">{{ row }}</span>
            <span v-if="!bucket.length" class="bucket__empty">{{ t("stage.emptyBucket") }}</span>
            <span
              v-for="(entry, cell) in bucket"
              :key="`bucket-${row}-${cell}`"
              class="bucket__entry"
              :class="{ 'bucket__entry--focus': isFocusBucketEntry(panel, row, cell) }"
            >{{ frameValueText(entry) }}</span>
          </div>
        </div>

        <table v-else-if="panel.kind === 'matrix'" class="matrix">
          <tbody>
            <tr v-for="(row, rowIndex) in panel.rows" :key="`row-${rowIndex}`">
              <th scope="row">{{ rowIndex }}</th>
              <td v-for="(value, column) in row" :key="`cell-${rowIndex}-${column}`" :class="{ 'cell--focus': isFocusCell(panel, rowIndex, column) }">{{ frameValueText(value) }}</td>
            </tr>
          </tbody>
        </table>

        <dl v-else-if="panel.kind === 'records'" class="records">
          <div v-for="(entry, rowIndex) in panel.values" :key="`record-${rowIndex}`" class="records__row" :class="{ 'records__row--focus': panel.focus === rowIndex }">
            <dt>{{ frameValueText((entry as Record<string, unknown>).label ?? (entry as Record<string, unknown>).key ?? rowIndex) }}</dt>
            <dd>{{ frameValueText((entry as Record<string, unknown>).value ?? (entry as Record<string, unknown>).val ?? entry) }}</dd>
          </div>
        </dl>

        <div v-if="panel.chips.length" class="panel__chips">
          <span v-for="chip in panel.chips" :key="`${panel.role}-${chip.label}`" class="chip"><i>{{ chip.label }}</i>{{ chip.value }}</span>
        </div>
      </div>

      <div v-for="(panel, index) in treePanels" :key="`tree-${index}-${panel.role}`" class="panel panel--graph">
        <p class="panel__label">{{ panel.label }}</p>
        <svg
          v-if="panel.kind === 'tree'"
          class="canvas"
          :viewBox="`0 0 ${treeLayouts[index].width} ${treeLayouts[index].height}`"
          preserveAspectRatio="xMidYMid meet"
          role="img"
          :aria-label="t('stage.panelLabel', { label: panel.label })"
        >
          <template v-for="(edge, edgeIndex) in treeLayouts[index].edges" :key="`edge-${edgeIndex}`">
            <!-- 自环：零长度的直线看不见，用一段弧表示 head.next = head。 -->
            <path
              v-if="edge.loop"
              class="edge"
              :class="{ 'edge--active': edge.active }"
              :d="`M ${edge.x1} ${edge.y1} C ${edge.x1 + 54} ${edge.y1 - 32}, ${edge.x2 + 54} ${edge.y2 + 32}, ${edge.x2} ${edge.y2}`"
            />
            <line
              v-else
              class="edge"
              :class="{ 'edge--active': edge.active }"
              :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
            />
          </template>
          <g v-for="placed in treeLayouts[index].nodes" :key="`node-${placed.key}`" class="node" :class="`node--${placed.state}`">
            <rect
              class="node__box"
              :x="placed.x - treeLayouts[index].nodeWidth(placed.node) / 2"
              :y="placed.y - NODE_HEIGHT / 2"
              :width="treeLayouts[index].nodeWidth(placed.node)"
              :height="NODE_HEIGHT"
              rx="9"
            />
            <text class="node__label" :x="placed.x" :y="placed.y + 6" text-anchor="middle">{{ placed.node.label }}</text>
            <text v-if="placed.note" class="node__note" :x="placed.x" :y="placed.y + NODE_HEIGHT / 2 + 17" text-anchor="middle">{{ placed.note }}</text>
          </g>
        </svg>

        <svg
          v-else-if="graphLayouts[index]"
          class="canvas"
          :viewBox="`0 0 ${graphLayouts[index]!.width} ${graphLayouts[index]!.height}`"
          preserveAspectRatio="xMidYMid meet"
          role="img"
          :aria-label="t('stage.panelLabel', { label: panel.label })"
        >
          <defs>
            <marker id="stage-arrow" class="arrow" viewBox="0 0 10 10" markerWidth="9" markerHeight="9" refX="8" refY="5" orient="auto" markerUnits="userSpaceOnUse">
              <path d="M 0 1 L 9 5 L 0 9 z" />
            </marker>
            <marker id="stage-arrow-active" class="arrow arrow--active" viewBox="0 0 10 10" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="userSpaceOnUse">
              <path d="M 0 1 L 9 5 L 0 9 z" />
            </marker>
          </defs>
          <g v-for="(edge, edgeIndex) in graphLayouts[index]!.edges" :key="`gedge-${edgeIndex}`">
            <line
              class="edge"
              :class="{ 'edge--active': edge.active }"
              :marker-end="edge.directed ? (edge.active ? 'url(#stage-arrow-active)' : 'url(#stage-arrow)') : undefined"
              :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
            />
            <text
              v-if="edge.label"
              class="edge__weight"
              :x="(edge.x1 + edge.x2) / 2"
              :y="(edge.y1 + edge.y2) / 2 - 5"
              text-anchor="middle"
            >{{ edge.label }}</text>
          </g>
          <g v-for="placed in graphLayouts[index]!.nodes" :key="`gnode-${placed.key}`" class="node" :class="`node--${placed.state}`">
            <circle class="node__box" :cx="placed.x" :cy="placed.y" r="22" />
            <text class="node__label" :x="placed.x" :y="placed.y + 6" text-anchor="middle">{{ placed.node.label }}</text>
            <text v-if="placed.note" class="node__note" :x="placed.x" :y="placed.y + 38" text-anchor="middle">{{ placed.note }}</text>
          </g>
        </svg>
      </div>
    </template>

    <p v-else class="stage__empty">{{ emptyLabel ?? t("player.empty") }}</p>
  </div>
</template>

<style scoped>
/*
 * The canvas. Everything here is shared by the animation lab and the in-classroom demo, so the type
 * scale and the panel recipe are one decision, not two: panels are raised cards (the site's double
 * hairline + glassy surface + short shadow), and the smallest text on the canvas is an index ruler at
 * 13px - the engine's own labels are 15-19px so nothing needs squinting at from the back of a room.
 */
.stage {
  --stage-ink: var(--text, #1c1c1b);
  --stage-ink-soft: var(--text-muted, #6f6d69);
  --stage-line: color-mix(in srgb, var(--stage-ink) 15%, transparent);
  --stage-face: var(--surface, #fbfaf7);
  --stage-cell: color-mix(in srgb, var(--stage-ink) 5%, var(--stage-face));
  --stage-focus: var(--stage-ink);
  display: grid;
  gap: 14px;
  align-content: start;
  width: 100%;
  color: var(--stage-ink);
}

.stage--empty {
  place-items: center;
  min-height: 180px;
}

.stage__empty {
  margin: 0;
  color: var(--stage-ink-soft);
  font-size: 19px;
  text-align: center;
}

.panel {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px 16px;
  border: 1px double color-mix(in srgb, var(--stage-ink) 15%, transparent);
  border-radius: 18px;
  background: color-mix(in srgb, var(--stage-face) 82%, transparent);
  box-shadow: 0 6px 14px color-mix(in srgb, var(--stage-ink) 7%, transparent);
}

.panel__label {
  margin: 0;
  color: var(--stage-ink);
  font-size: 16px;
  font-weight: 620;
  letter-spacing: .01em;
}

.cells {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cell {
  display: grid;
  min-width: 56px;
  min-height: 58px;
  justify-items: center;
  align-content: center;
  gap: 2px;
  padding: 10px 14px 6px;
  border: 1px solid var(--stage-line);
  border-radius: 14px;
  background: var(--stage-cell);
  transition: border-color .16s ease, background-color .16s ease, box-shadow .16s ease, transform .16s ease;
}

/* 空槽：结构已经建好，只是这一格还没有元素。虚线空位比"一个空白方块"清楚得多。 */
.cell--empty {
  border-style: dashed;
  border-color: color-mix(in srgb, var(--stage-ink) 22%, transparent);
  background: transparent;
}

/* 连容量都不知道的空结构：宁可给一个字，也不要一块什么都没有的画布。 */
.panel__vacant {
  margin: 0;
  padding: 14px 0 6px;
  color: var(--stage-ink-soft);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: .06em;
  text-align: center;
}

/* The step's element: a hairline turns into a ring, the tile lifts, and the value carries the weight. */
.cell--focus {
  border-color: color-mix(in srgb, var(--stage-focus) 55%, transparent);
  background: color-mix(in srgb, var(--stage-focus) 14%, var(--stage-face));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--stage-focus) 26%, transparent),
    0 8px 16px color-mix(in srgb, var(--stage-ink) 10%, transparent);
  transform: translateY(-4px);
}

.cell--focus .cell__value { font-weight: 660; }

/* 正在处理的那一段（排序的一趟、折半查找的候选区间）：虚线边框 + 顶上一道细条，
   一眼看出"工作量在缩小"。 */
.cell--inRange {
  border-style: dashed;
  box-shadow: inset 0 3px 0 color-mix(in srgb, var(--stage-ink) 26%, transparent);
}

/* 具名游标：引擎一直在报 i/j/mid/top/front/rear，以前只在页眉印一遍，格子上什么都没有。
   颜色在这是灰阶设计，靠"名字 + 主次填充"区分：当前那一个是实心，其余是描边。 */
.cell__cursors,
.chain__marks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.cursor {
  padding: 1px 7px;
  border: 1px solid color-mix(in srgb, var(--stage-ink) 30%, transparent);
  border-radius: 999px;
  background: var(--stage-face);
  color: var(--stage-ink-soft);
  font-size: 14px;
  line-height: 1.35;
  white-space: nowrap;
}

.cursor--primary {
  border-color: var(--stage-ink);
  background: var(--stage-ink);
  color: var(--stage-face);
}

/* 链表：结点框 [值 | next] + 结点之间的箭头。值挤在一排格子里看不出"谁指向谁"。 */
.chain {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chain__node {
  position: relative;
  display: flex;
  align-items: center;
  /* 给结点上方的具名指针留够一行的高度：`head` + `当前` 这种两枚标记必须并排在一行，
     换行后会掉进结点框里把值盖住（2026-10-06 真机截图抓到的 `当前` 压在 `10 next` 上）。 */
  padding-top: 26px;
}

/* 绝对定位的盒子默认宽度只从 `left:50%` 到父元素右边缘——结点宽的一半——所以 max-width 根本轮不到生效，
   两枚标记必然换行。`width: max-content` 让盒子按内容撑开，再靠 translateX(-50%) 居中。 */
.chain__marks {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: max-content;
  max-width: 240px;
  flex-wrap: nowrap;
}

.chain__body {
  display: flex;
  align-items: stretch;
  border: 1px double color-mix(in srgb, var(--stage-ink) 22%, transparent);
  border-radius: 10px;
  overflow: hidden;
  background: var(--stage-cell);
}

.chain__node--focus .chain__body {
  border-color: color-mix(in srgb, var(--stage-ink) 55%, transparent);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--stage-ink) 18%, transparent);
}

.chain__value {
  display: grid;
  place-items: center;
  min-width: 54px;
  min-height: 48px;
  padding: 0 6px;
  font-size: 19px;
  color: var(--stage-ink);
}

/* 结点框里的槽位：`prior` / `next`。两行——字段名在上、它此刻指向谁在下。
   只写目标值看不出哪格是哪个字段，只写字段名又看不出"next 指向谁"，所以两样都要。 */
.chain__slot {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 1px;
  min-width: 52px;
  padding: 2px 4px;
  border-left: 1px solid color-mix(in srgb, var(--stage-ink) 18%, transparent);
  background: color-mix(in srgb, var(--stage-ink) 6%, transparent);
}

.chain__slot-name {
  color: var(--stage-ink-soft);
  font-size: 12px;
  font-style: normal;
  line-height: 1.2;
}

.chain__slot-target {
  color: var(--stage-ink-soft);
  font-size: 15px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

/* 值左边的槽位（双向链表的 prior）：竖线画在右边。 */
.chain__slot--lead {
  border-left: none;
  border-right: 1px solid color-mix(in srgb, var(--stage-ink) 18%, transparent);
}

/* 这一帧正在改写的那一格（帧标题就是那行代码，比如 `p->next = B->next`）：
   点亮它，"断了哪根、接上哪根"就不用旁白。 */
.chain__slot--writing {
  background: color-mix(in srgb, var(--stage-focus) 18%, var(--stage-face));
  color: var(--stage-ink);
  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--stage-focus) 45%, transparent);
}

/* 结点之间的箭头区：正向一根；双向链表在下面再补一根反向的，两根都居中于结点框。 */
.chain__links {
  align-self: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chain__link {
  position: relative;
  width: 28px;
  height: 2px;
  margin: 0 3px;
  background: var(--stage-line);
}

/* 反向箭头直接镜像整根线（含箭头尖）：`scaleX(-1)` 一定指向左，比手算旋转角度可靠。 */
.chain__link--back { transform: scaleX(-1); }

.chain__links--wrap { flex-direction: row; align-items: center; gap: 6px; }

.chain__wrap {
  padding: 1px 7px;
  border: 1px dashed color-mix(in srgb, var(--stage-ink) 34%, transparent);
  border-radius: 999px;
  color: var(--stage-ink-soft);
  font-size: 14px;
  white-space: nowrap;
}

.chain__link::after {
  content: "";
  position: absolute;
  right: -1px;
  top: -4px;
  width: 9px;
  height: 9px;
  border-top: 2px solid var(--stage-line);
  border-right: 2px solid var(--stage-line);
  transform: rotate(45deg);
}

.cell__value {
  font-size: 19px;
  font-weight: 560;
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
}

.cell__index {
  color: var(--stage-ink-soft);
  font-size: 13px;
}

/* Rounded, separated cells read as a grid of tiles rather than a spreadsheet. */
.matrix { border-collapse: separate; border-spacing: 3px; font-variant-numeric: tabular-nums; }
.matrix th { padding: 2px 8px; color: var(--stage-ink-soft); font-size: 13px; font-weight: 600; text-align: right; }
.matrix td { min-width: 46px; padding: 8px 12px; border: 1px solid var(--stage-line); border-radius: 10px; text-align: center; font-size: 17px; }
.matrix td.cell--focus {
  border-color: transparent;
  background: color-mix(in srgb, var(--stage-focus) 16%, var(--stage-face));
  box-shadow: inset 0 0 0 2px var(--stage-focus);
  font-weight: 660;
}

.buckets { display: grid; gap: 8px; }
.bucket { display: flex; align-items: center; gap: 9px; }
.bucket__index { width: 28px; color: var(--stage-ink-soft); font-size: 13px; text-align: right; }
.bucket__empty { color: var(--stage-ink-soft); font-size: 14px; }
.bucket__entry {
  padding: 5px 12px;
  border: 1px solid var(--stage-line);
  border-radius: 999px;
  background: var(--stage-cell);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

/* 正在处理的那条同义词链：整行抬起来一点，桶号跟着变深，链上被比较的结点再单独上环。 */
.bucket--focus .bucket__index { color: var(--stage-ink); font-weight: 700; }
.bucket--focus .bucket__entry { border-color: color-mix(in srgb, var(--stage-focus) 40%, transparent); }
.bucket__entry--focus {
  border-color: transparent;
  background: color-mix(in srgb, var(--stage-focus) 16%, var(--stage-face));
  box-shadow: inset 0 0 0 2px var(--stage-focus);
  font-weight: 660;
  transform: translateY(-2px);
}

.records { display: grid; gap: 7px; margin: 0; }
.records__row { display: grid; grid-template-columns: minmax(72px, auto) minmax(0, 1fr); gap: 12px; align-items: baseline; }
.records dt { color: var(--stage-ink-soft); font-size: 15px; }
.records dd { margin: 0; font-size: 17px; overflow-wrap: anywhere; }
/* 记录式面板（多项式的系数项、编码表…）也跟着步骤高亮：没有它整条动画一行高亮都没有。 */
.records__row--focus {
  padding: 6px 10px;
  margin: -6px -10px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--stage-focus) 12%, var(--stage-face));
  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--stage-focus) 45%, transparent);
}
.records__row--focus dd { font-weight: 660; }

.canvas {
  display: block;
  width: 100%;
  max-height: 360px;
}

.edge { fill: none; stroke: var(--stage-line); stroke-width: 1.8; }
.edge--active { stroke: var(--stage-focus); stroke-width: 3.4; }
.edge__weight { fill: var(--stage-ink-soft); font-size: 13px; }

.node__box { fill: var(--stage-cell); stroke: var(--stage-line); stroke-width: 1.8; }
.node__label { fill: var(--stage-ink); font-size: 16px; font-weight: 600; font-variant-numeric: tabular-nums; }
.node__note { fill: var(--stage-ink-soft); font-size: 12px; }
/* Already-processed nodes recede: muted ink and a lighter label, so the eye goes to what is active. */
.node--done .node__box { fill: color-mix(in srgb, var(--stage-ink) 12%, var(--stage-face)); }
.node--done .node__label,
.node--done .node__note { opacity: .55; }
.node--active .node__box { fill: var(--stage-focus); stroke: var(--stage-focus); }
.node--active .node__label { fill: var(--stage-face); font-weight: 700; }

.arrow { fill: var(--stage-line); }
.arrow--active { fill: var(--stage-focus); }

.panel__chips { display: flex; flex-wrap: wrap; gap: 7px; }

/* One row for the frame's own metadata, above the panels it describes. */
.stage__meta { display: flex; flex-wrap: wrap; gap: 7px; }

/* 不变式条：现在这一步"什么成立"。它是学生要带到下一步去的那句话，不是又一个字段值，
   所以字不能小（≥19px）、独占一行，并用左侧竖线和大面积底色和胶囊区分开。 */
.stage__invariant {
  margin: 0 0 10px;
  padding: 10px 14px;
  border-left: 4px solid color-mix(in srgb, var(--stage-ink) 30%, transparent);
  border-radius: 10px;
  background: var(--stage-cell);
  color: var(--stage-ink);
  font-size: 19px;
  line-height: 1.5;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid color-mix(in srgb, var(--stage-ink) 15%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--stage-face) 82%, transparent);
  color: var(--stage-ink-soft);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.chip i { font-style: normal; opacity: .72; }
</style>
