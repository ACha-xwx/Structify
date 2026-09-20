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

const frame = computed<AnimationFrame>(() => normalizeFrame(props.state ?? props.step?.dsvpState ?? null));
const hasContent = computed(() => frame.value.panels.length > 0);
const treePanels = computed(() => frame.value.panels.filter((panel) => panel.kind === "tree" || panel.kind === "graph"));
const flatPanels = computed(() => frame.value.panels.filter((panel) => panel.kind !== "tree" && panel.kind !== "graph"));

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

/** The single node the step is standing on. */
function activeId(panel: FramePanel): string | null {
  const current = frame.value.raw.current;
  if (typeof current === "string" || typeof current === "number") {
    if (panel.nodes.some((node) => String(node.id) === String(current))) return String(current);
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

/** The edge a relaxation or traversal step just walked, drawn emphasised. */
const activeEdge = computed<[string, string] | null>(() => {
  const relax = frame.value.raw.relax;
  if (Array.isArray(relax) && relax.length >= 2) return [String(relax[0]), String(relax[1])];
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
  edges: { x1: number; y1: number; x2: number; y2: number; active: boolean }[];
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
  const edges = panel.edges
    .map((edge) => {
      if (!Array.isArray(edge) || edge.length < 2) return null;
      const from = positionOf.get(String(edge[0]));
      const to = positionOf.get(String(edge[1]));
      if (!from || !to) return null;
      const isActive = activeEdge.value !== null
        && ((activeEdge.value[0] === from.key && activeEdge.value[1] === to.key)
          || (activeEdge.value[0] === to.key && activeEdge.value[1] === from.key));
      return { x1: from.x, y1: from.y + NODE_HEIGHT / 2, x2: to.x, y2: to.y - NODE_HEIGHT / 2, active: isActive };
    })
    .filter((edge): edge is TreeLayout["edges"][number] => edge !== null);

  return {
    width: Math.max(320, cursor + PAD),
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
  edges: { x1: number; y1: number; x2: number; y2: number; label: string; active: boolean }[];
}

const GRAPH_SIZE = 300;
const GRAPH_RADIUS = 108;

/** A circle is the one layout that is deterministic, readable and needs no coordinates from the engine. */
function layoutGraph(panel: FramePanel): GraphLayout {
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
      return { x1: from.x, y1: from.y, x2: to.x, y2: to.y, label: weight === "1" ? "" : weight, active: isActive };
    })
    .filter((edge): edge is GraphLayout["edges"][number] => edge !== null);
  return { width: GRAPH_SIZE, height: GRAPH_SIZE, nodes: placed, edges };
}

const graphLayouts = computed(() => treePanels.value.map((panel) => (panel.kind === "graph" ? layoutGraph(panel) : null)));
</script>

<template>
  <div class="stage" :class="{ 'stage--empty': !hasContent }">
    <template v-if="hasContent">
      <div v-for="(panel, index) in flatPanels" :key="`flat-${index}-${panel.role}`" class="panel">
        <p class="panel__label">{{ panel.label }}</p>

        <ol v-if="panel.kind === 'array' || panel.kind === 'text'" class="cells">
          <li
            v-for="(value, cell) in panel.values"
            :key="`${cell}-${value}`"
            class="cell"
            :class="{ 'cell--focus': panel.focus === cell, 'cell--inRange': panel.range && cell >= panel.range[0] && cell < panel.range[1] }"
          >
            <span class="cell__value">{{ frameValueText(value) }}</span>
            <span class="cell__index">{{ cell }}</span>
          </li>
        </ol>

        <div v-else-if="panel.kind === 'matrix' && panel.variant === 'bucket'" class="buckets">
          <div v-for="(bucket, row) in panel.rows" :key="`bucket-${row}`" class="bucket">
            <span class="bucket__index">{{ row }}</span>
            <span v-if="!bucket.length" class="bucket__empty">{{ t("stage.emptyBucket") }}</span>
            <span v-for="(entry, cell) in bucket" :key="`bucket-${row}-${cell}`" class="bucket__entry">{{ frameValueText(entry) }}</span>
          </div>
        </div>

        <table v-else-if="panel.kind === 'matrix'" class="matrix">
          <tbody>
            <tr v-for="(row, rowIndex) in panel.rows" :key="`row-${rowIndex}`">
              <th scope="row">{{ rowIndex }}</th>
              <td v-for="(value, column) in row" :key="`cell-${rowIndex}-${column}`" :class="{ 'cell--focus': panel.focus === column }">{{ frameValueText(value) }}</td>
            </tr>
          </tbody>
        </table>

        <dl v-else-if="panel.kind === 'records'" class="records">
          <div v-for="(entry, rowIndex) in panel.values" :key="`record-${rowIndex}`" class="records__row">
            <dt>{{ frameValueText((entry as Record<string, unknown>).label ?? (entry as Record<string, unknown>).key ?? rowIndex) }}</dt>
            <dd>{{ frameValueText((entry as Record<string, unknown>).value ?? (entry as Record<string, unknown>).val ?? entry) }}</dd>
          </div>
        </dl>

        <div v-if="panel.chips.length" class="panel__chips">
          <span v-for="chip in panel.chips" :key="`${panel.role}-${chip.label}`" class="chip"><i>{{ chip.label }}</i>{{ chip.value }}</span>
        </div>
      </div>

      <div v-for="(panel, index) in treePanels" :key="`tree-${index}-${panel.role}`" class="panel panel--graph">
        <svg
          v-if="panel.kind === 'tree'"
          class="canvas"
          :viewBox="`0 0 ${treeLayouts[index].width} ${treeLayouts[index].height}`"
          preserveAspectRatio="xMidYMid meet"
          role="img"
          :aria-label="t('stage.panelLabel', { label: panel.label })"
        >
          <line
            v-for="(edge, edgeIndex) in treeLayouts[index].edges"
            :key="`edge-${edgeIndex}`"
            class="edge"
            :class="{ 'edge--active': edge.active }"
            :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
          />
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
          <g v-for="(edge, edgeIndex) in graphLayouts[index]!.edges" :key="`gedge-${edgeIndex}`">
            <line class="edge" :class="{ 'edge--active': edge.active }" :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" />
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
.stage {
  --stage-ink: var(--text, #1c1c1b);
  --stage-ink-soft: var(--text-muted, #6f6d69);
  --stage-line: var(--line, #d6d3cd);
  --stage-face: var(--surface, #fbfaf7);
  --stage-cell: color-mix(in srgb, var(--stage-ink) 6%, var(--stage-face));
  --stage-focus: var(--stage-ink);
  display: grid;
  gap: 16px;
  align-content: start;
  width: 100%;
  color: var(--stage-ink);
}

.stage--empty {
  place-items: center;
  min-height: 140px;
}

.stage__empty {
  margin: 0;
  color: var(--stage-ink-soft);
  font-size: 15px;
}

.panel {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.panel__label {
  margin: 0;
  color: var(--stage-ink-soft);
  font-size: 13px;
  letter-spacing: .02em;
}

.cells {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.cell {
  display: grid;
  min-width: 52px;
  justify-items: center;
  gap: 3px;
  padding: 9px 12px 5px;
  border: 1px solid var(--stage-line);
  border-radius: 12px;
  background: var(--stage-cell);
  transition: border-color .16s ease, background-color .16s ease, transform .16s ease;
}

.cell--focus {
  border-color: var(--stage-focus);
  background: color-mix(in srgb, var(--stage-focus) 16%, var(--stage-face));
  transform: translateY(-3px);
}

.cell--inRange { border-style: dashed; }

.cell__value {
  font-size: 17px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.cell__index {
  color: var(--stage-ink-soft);
  font-size: 11px;
}

.matrix { border-collapse: collapse; font-variant-numeric: tabular-nums; }
.matrix th { padding: 4px 8px; color: var(--stage-ink-soft); font-size: 11px; font-weight: 500; text-align: right; }
.matrix td { min-width: 38px; padding: 7px 10px; border: 1px solid var(--stage-line); text-align: center; }
.matrix td.cell--focus { border-color: var(--stage-focus); background: color-mix(in srgb, var(--stage-focus) 16%, var(--stage-face)); }

.buckets { display: grid; gap: 6px; }
.bucket { display: flex; align-items: center; gap: 7px; }
.bucket__index { width: 24px; color: var(--stage-ink-soft); font-size: 11px; text-align: right; }
.bucket__empty { color: var(--stage-ink-soft); font-size: 12px; }
.bucket__entry {
  padding: 4px 10px;
  border: 1px solid var(--stage-line);
  border-radius: 999px;
  background: var(--stage-cell);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.records { display: grid; gap: 4px; margin: 0; }
.records__row { display: grid; grid-template-columns: minmax(60px, auto) minmax(0, 1fr); gap: 10px; }
.records dt { color: var(--stage-ink-soft); font-size: 12px; }
.records dd { margin: 0; font-size: 14px; overflow-wrap: anywhere; }

.canvas {
  display: block;
  width: 100%;
  max-height: 340px;
}

.edge { stroke: var(--stage-line); stroke-width: 1.6; }
.edge--active { stroke: var(--stage-focus); stroke-width: 3; }
.edge__weight { fill: var(--stage-ink-soft); font-size: 11px; }

.node__box { fill: var(--stage-cell); stroke: var(--stage-line); stroke-width: 1.6; }
.node__label { fill: var(--stage-ink); font-size: 14px; font-variant-numeric: tabular-nums; }
.node__note { fill: var(--stage-ink-soft); font-size: 10px; }
.node--done .node__box { fill: color-mix(in srgb, var(--stage-ink) 12%, var(--stage-face)); }
.node--active .node__box { fill: var(--stage-focus); stroke: var(--stage-focus); }
.node--active .node__label { fill: var(--stage-face); }

.panel__chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid var(--stage-line);
  border-radius: 999px;
  color: var(--stage-ink-soft);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.chip i { font-style: normal; opacity: .75; }
</style>
