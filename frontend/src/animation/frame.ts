import type { AnimationDefinition, DsvpNode, DsvpPanel, DsvpState } from "../shared/types/animation";

/**
 * Turns one engine frame into something a renderer can draw.
 *
 * The engine emits 38 structure families and 74 panel roles, and the engine - not the frontend - owns
 * what a frame contains. Writing one renderer per family would mean rewriting them every time the
 * textbook grows, so every frame is normalized into the same small model: a list of labelled panels,
 * each classified into one of six drawable kinds, plus the engine's meta kept verbatim so a graph
 * renderer can colour `visited` nodes instead of merely printing the word.
 *
 * An unknown role or a brand-new family degrades to a labelled list of values, never a blank screen.
 */

export type FramePanelKind = "array" | "matrix" | "records" | "tree" | "graph" | "text" | "board";
export type FramePanelVariant = "plain" | "bucket";

export interface FrameChip {
  label: string;
  value: string;
}

/**
 * One named position on a panel - `top` on a stack, `mid` in a binary search, `i`/`j` while sorting.
 *
 * The engine has always reported these (they used to survive only as header text like "mid 3"), and a grid
 * with a single highlight cannot say "i is here and j is there". A learner reads a cursor's *name* on the
 * cell it stands on, so every pointer the engine reports for a panel is published, not just the first.
 */
export interface FrameCursor {
  key: string;
  label: string;
  index: number;
}

/**
 * 一个结点框该有哪些槽位、结点之间该有哪些箭头——**按结构定义来**，不是"链表都长一样"。
 *
 * 单链表每个结点只有后继，所以是 `[值 | next]`；双向链表每个结点**同时存前驱和后继**，
 * 框里必须有 `prior` 那一栏，结点之间也要有一根**反向**箭头——只画 next 就等于把双向链表画成了单链表
 * （2026-10-06 Rrd 拿截图问"双向链表这个箭头方向是不是也不对"，就是这里）。
 * 循环链表则要把尾结点的 next 绕回首结点，末结点的 `next` 栏写 `head` 而不是 `NULL`。
 */
export interface ChainShape {
  /** 值左边的槽位名（双向链表的 `prior`）；没有就是 null。 */
  lead: string | null;
  /** 值右边的槽位名（`next`；链式栈/队列同样是 next）。 */
  slots: string[];
  /** 结点之间还要画一根反向箭头（prior 指向前驱），画在正向箭头下面一行。 */
  back: boolean;
  /** 尾结点的 next 绕回首结点。 */
  circular: boolean;
}

/** 按 `kind`（结构定义本身）给形状；认不出的链表面板退回单链表画法。 */
const CHAIN_SHAPES: Record<string, ChainShape> = {
  linked_list: { lead: null, slots: ["next"], back: false, circular: false },
  doubly_linked_list: { lead: "prior", slots: ["next"], back: true, circular: false },
  circular_linked_list: { lead: null, slots: ["next"], back: false, circular: true },
  linked_stack: { lead: null, slots: ["next"], back: false, circular: false },
  linked_queue: { lead: null, slots: ["next"], back: false, circular: false },
};
const DEFAULT_CHAIN: ChainShape = { lead: null, slots: ["next"], back: false, circular: false };

export interface FramePanel {
  role: string;
  label: string;
  kind: FramePanelKind;
  variant: FramePanelVariant;
  /** Flat values for `array`/`text`; one entry per element for `matrix`; raw objects for `records`. */
  values: unknown[];
  /** Present for `matrix`: the row-major cells. */
  rows: unknown[][];
  /** Present for `tree`/`graph`, normalized to objects even when the engine lists bare labels. */
  nodes: DsvpNode[];
  edges: unknown[][];
  /** B-tree nodes hold several keys; the tree renderer then draws a key strip per node. */
  multiKey: boolean;
  /** Zero-based index inside this panel that the step is operating on, when the engine says so. */
  focus: number | null;
  /** Every named position the engine reports for this panel, drawn on the cells they stand on. */
  cursors: FrameCursor[];
  /** Present when the values are a chain of nodes: how a node is shaped and how nodes are wired. */
  chain: ChainShape | null;
  /**
   * 引擎按结构给的槽位文字：`next: ["1","3","5","head"]` 就是"第 i 个结点 next 栏写什么"。
   * 有就直接用它，前端不再自己反推——两处各推一份，迟早会推得不一样。
   */
  chainText: Record<string, string[]> | null;
  /**
   * 这一帧正在改写哪个结点的**哪个字段**（`{index, slot}`）——渲染器把那一格点亮，
   * 对应标题里那行代码（`p->next->prior = s` 改的是 `prior`，不是 `next`）。
   */
  chainWrite: { index: number; slot: string } | null;
  /** Zero-based [row, column] of the one cell a grid step is standing on (matrix panels only). */
  focusCell: [number, number] | null;
  /** Half-open index range a sort pass is working on (`low`..`high`). */
  range: [number, number] | null;
  /** Frame metadata belonging to this panel (front/rear/top/position/pivot/...). */
  chips: FrameChip[];
}

export interface AnimationFrame {
  kind: string;
  label: string;
  panels: FramePanel[];
  /** Every meta field, rendered for display. */
  chips: FrameChip[];
  /**
   * One sentence stating what is true of the structure right now ("[0, i) is sorted, [i, n) is not").
   *
   * The engine reports it as `meta.invariant`. It is drawn as a bar above the canvas rather than as one
   * more chip, because a chip next to "mid 3" reads as another value, while the invariant is the frame's
   * headline: it is what the learner is supposed to carry from one step to the next.
   */
  invariant: string;
  /** The engine's meta verbatim, for renderers that need structure rather than text (`visited`, `dist`). */
  raw: Record<string, unknown>;
}

const EMPTY_FRAME: AnimationFrame = { kind: "", label: "", panels: [], chips: [], invariant: "", raw: {} };

/**
 * Roles whose values are a list of lists. Everything else holding an array of scalars is drawn as one
 * row, which is what a stack, a hash table or a KMP `next` array actually is.
 */
const NESTED_ROLES = new Set([
  "matrix",
  "buckets",
  "blocks",
  "rows",
  "triples",
  "aligned",
  "cpot",
  "cross_nodes",
  "codes",
  "runs",
  "outputRuns",
  "records",
  "trees",
  "positions",
  "weights",
  "symbols",
  "usedIndices",
]);

/** Meta fields whose numeric value is a cursor a sibling panel should highlight. */const POINTER_KEYS = new Set([
  "index",
  "current",
  "top",
  "front",
  "rear",
  "i",
  "j",
  "k",
  "position",
  "targetIndex",
  "movingIndex",
  "pivotIndex",
  "mid",
  "column",
  // 双端栈：两个栈顶在**同一个共享数组**里从两端往中间长，所以两边的栈顶都要标出来。
  "topLeft",
  "topRight",
  // 简单选择排序：一边扫一边记"目前最小的在哪"——这两格不标出来，那几十帧比较帧看起来就是静止的。
  "selected",
  "scan",
]);

/**
 * 这几个游标说的是"**这个结构长什么样**"，不是"这一步在动哪一格"：栈顶、队首、队尾。
 * 所以它们和面板自带的 `focusIndex` 一起画，不会被顶掉（双端栈的左右栈顶就是这么丢过一次的）。
 */
const STRUCTURAL_CURSOR_KEYS = new Set(["top", "front", "rear"]);

const ROLE_LABELS: Record<string, string> = {
  A: "塔 A",
  B: "塔 B",
  C: "塔 C",
  L: "链表 L",
  LA: "表 A",
  LB: "表 B",
  LC: "结果链表",
  P: "多项式",
  PA: "多项式 A",
  PB: "多项式 B",
  PC: "结果多项式",
  R: "顺序表",
  active: "活跃结点",
  address: "地址",
  aligned: "分段",
  array: "数组",
  binaryRepresentation: "二叉树表示",
  blocks: "索引块",
  buckets: "同义词链",
  buffer: "缓冲区",
  call_stack: "调用栈",
  chars: "字符数组",
  codes: "编码",
  compressed: "压缩存储",
  copy: "复制结果",
  cpot: "cpot 定位",
  cross_nodes: "十字链表结点",
  current: "当前行",
  digits: "数字串",
  graph: "图",
  head: "表头",
  indegree: "入度",
  input: "输入串",
  inputA: "输入缓冲 A",
  inputB: "输入缓冲 B",
  left: "左段",
  list: "广义表",
  matrix: "矩阵",
  matrix_index: "下标",
  memory: "内存工作区",
  new: "新结点",
  next: "next 数组",
  node: "结点",
  nodes: "静态链表",
  num: "编号",
  operators: "运算符栈",
  output: "归并输出",
  outputBuffer: "输出缓冲",
  outputRuns: "输出顺串",
  parent: "parent 数组",
  pattern: "模式串",
  probe: "探测",
  promote: "提升关键字",
  queue: "队列",
  records: "记录",
  result: "结果",
  right: "右段",
  rows: "逐行结果",
  runs: "顺串",
  selected: "选中位",
  sequence: "序列",
  square_digits: "平方结果",
  stack: "栈",
  sum: "叠加和",
  table: "散列表",
  tail: "表尾",
  text: "主串",
  tokens: "token",
  tree: "树",
  trees: "树森林",
  triples: "三元组",
  values: "值",
  visited: "已访问",
  written: "已写回",
};

const KIND_LABELS: Record<string, string> = {
  btree: "B 树",
  circular_buffer: "键盘循环缓冲区",
  circular_linked_list: "循环单链表",
  circular_queue: "循环队列",
  double_stack: "双端顺序栈",
  doubly_linked_list: "双向链表",
  external_sort: "外部排序",
  forest: "树与森林",
  generalized_list: "广义表",
  graph: "图",
  hanoi: "汉诺塔",
  hash_function: "哈希函数构造",
  hash_table: "散列表",
  heap_string: "堆串",
  huffman: "哈夫曼树",
  linked_list: "单链表",
  linked_queue: "链队列",
  linked_stack: "链栈",
  polynomial: "一元多项式",
  queue: "顺序队列",
  queue_app: "队列应用",
  recursion: "递归",
  search: "查找",
  sequence: "顺序表查找",
  sequential_list: "顺序表",
  sequential_list_merge: "顺序表合并",
  sort: "排序",
  sparse_matrix: "稀疏矩阵",
  special_matrix: "特殊矩阵",
  stack: "顺序栈",
  stack_app: "栈的应用",
  static_linked_list: "静态链表",
  string: "顺序串",
  string_match: "模式匹配",
  tree: "二叉树",
  tree_compare: "二叉树比较",
  union_find: "并查集",
};

const META_LABELS: Record<string, string> = {
  operation: "操作",
  index: "下标",
  current: "当前",
  currentValue: "当前结点",
  currentIndex: "当前下标",
  top: "top",
  front: "front",
  rear: "rear",
  count: "个数",
  i: "i",
  j: "j",
  k: "k",
  low: "low",
  high: "high",
  position: "位置",
  targetIndex: "目标下标",
  movingIndex: "移动中",
  pivotIndex: "枢轴位置",
  mid: "中点",
  column: "列",
  attempt: "探测次数",
  mode: "探测方式",
  formula: "公式",
  stored: "已存储",
  kind: "类型",
  key: "关键字",
  n: "阶",
  capacity: "容量",
  positions: "选位",
  tableSize: "表长",
  circular: "循环",
  converted: "已转换",
  allocated: "分配下标",
  phase: "阶段",
  length: "长度",
  value: "值",
  depth: "深度",
  done: "已完成",
  adjusted: "已调整",
  visited: "已访问",
  settled: "已确定",
  topo: "拓扑序",
  frontier: "待扩展",
  dist: "距离",
  indegree: "入度",
  relax: "本步松弛",
  start: "起点",
  target: "目标",
  match: "匹配位置",
  ways: "归并路数",
  order: "阶",
  root: "根",
  element: "元素",
  edge: "边",
  source: "来源",
  parent: "parent",
  head: "头",
  totalWeight: "总权值",
  merged: "已合并",
  mismatch: "失配",
  result: "结果",
  peek: "读取值",
  removed: "移出",
  compareCount: "比较次数",
  swapCount: "交换次数",
  // 双端栈：两个栈顶在同一个共享数组里，还要看得见还剩几个单元。
  topLeft: "左栈顶",
  topRight: "右栈顶",
  free: "空位",
  found: "命中",
  digit: "数位",
  pass: "趟",
  bucket: "桶号",
  pivot: "枢轴",
  compare: "比较",
  next: "next",
  path: "路径",
};

export function frameKindLabel(kind: string): string {
  return KIND_LABELS[kind] ?? kind;
}

export function panelRoleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role;
}

export function chipLabel(key: string): string {
  return META_LABELS[key] ?? key;
}

/** Renders any engine value for display, including nested lists, polynomial terms and static nodes. */
export function frameValueText(value: unknown): string {
  if (value === null || value === undefined) return "∅";
  if (typeof value === "boolean") return value ? "true" : "false";
  if (typeof value === "number" || typeof value === "string") return String(value);
  if (Array.isArray(value)) return `[${value.map(frameValueText).join(", ")}]`;
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (typeof record.coef === "number" && typeof record.exp === "number") {
      return record.exp === 0 ? String(record.coef) : `${record.coef}x^${record.exp}`;
    }
    // A hash chain cell is `{key, val}`; with the (common) empty payload the key alone is the honest
    // rendering — "key=22 val=" reads like a bug because it is one.
    if ("key" in record) {
      const payload = record.val ?? record.value;
      return payload === "" || payload === null || payload === undefined
        ? frameValueText(record.key)
        : `${frameValueText(record.key)}：${frameValueText(payload)}`;
    }
    if ("data" in record || "cursor" in record) {
      return `${record.index ?? "?"}: ${frameValueText(record.data)} → ${frameValueText(record.cursor)}`;
    }
    return Object.entries(record)
      .map(([key, item]) => `${key}=${frameValueText(item)}`)
      .join(" ");
  }
  return String(value);
}

/**
 * Normalizes one engine frame. A frame without `view` still renders: the four legacy shapes (`stack`,
 * `queue`, `sequential_list`, `sequential_list_merge`) carry their fields at the top level.
 */
export function normalizeFrame(state: DsvpState | null | undefined): AnimationFrame {
  if (!state || typeof state !== "object") return EMPTY_FRAME;
  const kind = typeof state.kind === "string" ? state.kind : "";
  const chips: FrameChip[] = [];
  const raw: Record<string, unknown> = {};
  const panels: FramePanel[] = [];
  const view = Array.isArray(state.view) ? (state.view as DsvpPanel[]) : [];

  if (view.length) {
    const pointers: Record<string, number> = {};
    for (const panel of view) {
      if (!panel || typeof panel !== "object") continue;
      if (panel.role === "meta" || panel.role === "probe" || panel.role === "matrix_index") {
        collectMeta(panel, chips, raw, pointers);
      }
    }
    for (const panel of view) {
      if (!panel || typeof panel !== "object") continue;
      if (panel.role === "meta" || panel.role === "probe" || panel.role === "matrix_index") continue;
      panels.push(panelFromView(panel, pointers, raw, kind));
    }
  } else {
    panels.push(...panelsFromLegacy(kind, state as Record<string, unknown>, chips, raw));
  }

  // A sort pass reports the interval it is working on; the renderer outlines it when it knows about it.
  const low = raw.low;
  const high = raw.high;
  if (typeof low === "number" && typeof high === "number") {
    const target = panels.find((panel) => panel.kind === "array" || panel.kind === "text");
    if (target) target.range = [low, high + 1];
  }

  return {
    kind,
    label: frameKindLabel(kind),
    panels: panels.filter((panel) => panel.kind !== "board"),
    chips,
    invariant: typeof raw.invariant === "string" ? raw.invariant : "",
    raw,
  };
}

function panelFromView(panel: DsvpPanel, pointers: Record<string, number>, raw: Record<string, unknown>, kind: string): FramePanel {
  const role = String(panel.role ?? "");
  const nodes = normalizeNodes(panel.nodes);
  const edges = Array.isArray(panel.edges) ? (panel.edges as unknown[][]) : [];
  const values = panel.values;

  const frame: FramePanel = {
    role,
    label: panelRoleLabel(role),
    kind: "board",
    variant: "plain",
    values: [],
    rows: [],
    nodes,
    edges,
    multiKey: false,
    focus: null,
    cursors: [],
    chain: null,
    chainText: null,
    chainWrite: null,
    focusCell: null,
    range: null,
    chips: [],
  };

  if (nodes.length) {
    frame.kind = role === "graph" ? "graph" : "tree";
    frame.multiKey = nodes.some((node) => Array.isArray(node.keys) && node.keys.length > 1);
    return frame;
  }

  if (typeof values === "string") {
    frame.kind = "text";
    frame.values = [values];
    return frame;
  }

  if (!Array.isArray(values)) return frame;

  const allArrays = values.length > 0 && values.every((item) => Array.isArray(item));

  if (allArrays) {
    frame.kind = "matrix";
    // A hash table's buckets render as labelled chain rows, whatever the entries look like
    // (objects `{key,val}` or the plain keys they now render as).
    frame.variant = role === "buckets" ? "bucket" : "plain";
    frame.rows = values as unknown[][];
    frame.values = values;
  } else if (values.length && values.every((item) => item !== null && typeof item === "object")) {
    frame.kind = "records";
    frame.values = values;
  } else {
    frame.kind = "array";
    frame.values = values;
    // 空结构不能画成一块空白画布。顺序栈、循环队列这类结构自己知道容量，就把 capacity 个空槽摆出来，
    // 「空」才有形状——2026-09-24 用户反馈：「初始化操作没什么可演示的，但你不能空空的啥也没有」。
    if (!values.length) {
      const capacity = typeof raw.capacity === "number" ? raw.capacity : null;
      if (capacity !== null && Number.isInteger(capacity) && capacity > 0 && capacity <= 64) {
        frame.values = new Array(capacity).fill(null);
      }
    }
  }

  frame.focusCell = focusCellOf(panel);
  // A panel may carry its own cursor (`focusIndex`); that beats the shared pointer pool, which several
  // panels read at once and which therefore cannot say "slot 7 of the packed array, not column 7".
  //
  // 但**结构性的标记不能因此被顶掉**：双端栈的左右两个栈顶、循环缓冲区的 front/rear 属于"这个结构长什么样"，
  // 每一帧都该看得见，而 focusIndex 说的是"这一步正在动哪一格"。两者同格时用结构性的名字（`top` 比"当前"
  // 说得清），不同格就都画出来。
  const ownFocus = typeof panel.focusIndex === "number" ? panel.focusIndex : null;
  frame.chain = frame.kind === "array" || frame.kind === "records" ? chainShapeOf(role, kind) : null;
  frame.chainText = chainTextOf(panel);
  frame.chainWrite = typeof panel.write === "number"
    ? { index: panel.write, slot: typeof panel.writeSlot === "string" ? panel.writeSlot : "next" }
    : null;
  if (ownFocus !== null) {
    const bounded = clamp(ownFocus, frame.values.length);
    const own = bounded === null ? [] : [{ key: "focus", label: cursorLabel("focus"), index: bounded }];
    const structural = cursorsFor(role, pointers, frame.values.length).filter((cursor) => STRUCTURAL_CURSOR_KEYS.has(cursor.key));
    const sameCell = structural.find((cursor) => cursor.index === bounded);
    frame.cursors = sameCell ? [sameCell, ...structural.filter((cursor) => cursor !== sameCell)] : [...own, ...structural];
  } else {
    frame.cursors = cursorsFor(role, pointers, frame.values.length);
  }
  // 面板自己的具名指针（`pointers: {p: 3, A: 0}`）排在最前：它们是**这个结构**的指针，
  // 比"整帧共用池"里推出来的更具体，也该是渲染器强调的那一个。
  const ownPointers = ownPointerCursors(panel, frame.values.length);
  if (ownPointers.length) {
    frame.cursors = [...ownPointers, ...frame.cursors.filter((cursor) => !ownPointers.some((own) => own.key === cursor.key))];
  }
  // Pattern matching: both string panels would stay unhighlighted because i/j are not in cursorsFor's
  // generic set. Each side rides its own pointer — i over the text, j over the pattern.
  if (ownFocus === null && frame.cursors.length === 0) {
    const pair = role === "pattern" && pointers.j !== undefined ? { key: "j", at: pointers.j }
      : role === "text" && pointers.i !== undefined && pointers.j !== undefined ? { key: "i", at: pointers.i }
        : null;
    const bounded = pair === null ? null : clamp(pair.at, frame.values.length);
    if (pair !== null && bounded !== null) {
      frame.cursors = [{ key: pair.key, label: cursorLabel(pair.key), index: bounded }];
    }
  }
  frame.focus = frame.cursors.length ? frame.cursors[0].index : null;
  return frame;
}

/**
 * 面板自带的具名指针：`pointers: {p: 3, A: 0}`（名字 → 第几个结点）。
 *
 * 与整帧共用的指针池不同，这些指针**只属于这个面板**——两个循环表各有自己的 p、q 时，
 * 一份共用的池会把 LB 的 q 标到 LA 上。所以引擎按面板发，这里按面板收。
 */
function ownPointerCursors(panel: DsvpPanel, length: number): FrameCursor[] {
  const raw = panel.pointers;
  if (!isRecord(raw)) return [];
  const cursors: FrameCursor[] = [];
  for (const [key, value] of Object.entries(raw)) {
    const index = clamp(Number(value), length);
    if (index === null) continue;
    cursors.push({ key, label: cursorLabel(key), index });
  }
  return cursors;
}

/**
 * 引擎按结构给的槽位文字：`next: ["1","3","5","head"]` —— 第 i 个结点 next 栏此刻该写什么。
 * 有就不在前端反推（`NULL`/`head`/相邻结点各推一份，迟早推得不一样）。
 */
function chainTextOf(panel: DsvpPanel): Record<string, string[]> | null {
  const text: Record<string, string[]> = {};
  for (const key of ["next", "prior"] as const) {
    const value = panel[key];
    if (!Array.isArray(value)) continue;
    text[key] = value.map((item) => (item === null || item === undefined ? "NULL" : String(item)));
  }
  return Object.keys(text).length ? text : null;
}

/** The one cell a grid step stands on, when the engine names it as `focusCell: [row, column]`. */function focusCellOf(panel: DsvpPanel): [number, number] | null {
  const value = panel.focusCell;
  if (!Array.isArray(value) || value.length < 2) return null;
  const row = Number(value[0]);
  const column = Number(value[1]);
  return Number.isInteger(row) && Number.isInteger(column) && row >= 0 && column >= 0 ? [row, column] : null;
}

/** The four legacy snapshot shapes carry their fields at the top level instead of inside `view`. */
function panelsFromLegacy(kind: string, state: Record<string, unknown>, chips: FrameChip[], raw: Record<string, unknown>): FramePanel[] {
  const base = (role: string, values: unknown[]): FramePanel => ({
    role,
    label: panelRoleLabel(role),
    kind: "array",
    variant: "plain",
    values,
    rows: [],
    nodes: [],
    edges: [],
    multiKey: false,
    focus: null,
    cursors: [],
    chain: chainShapeOf(role, kind),
    chainText: null,
    chainWrite: null,
    focusCell: null,
    range: null,
    chips: [],
  });

  if (kind === "stack" || kind === "queue") {
    const values = asArray(state.items).map((item) => (isRecord(item) ? item.value : item));
    const panel = base(kind, values);
    const pointer = kind === "stack" ? state.top : state.front;
    const cursors = cursorsFor(kind, {
      ...(typeof state.top === "number" ? { top: state.top } : {}),
      ...(typeof state.front === "number" ? { front: state.front } : {}),
      ...(typeof state.rear === "number" ? { rear: state.rear } : {}),
      ...(typeof state.current === "number" ? { current: state.current } : {}),
    }, values.length);
    panel.cursors = cursors;
    if (typeof pointer === "number") {
      panel.chips.push({ label: kind === "stack" ? "top" : "front", value: String(pointer) });
    }
    // 队列/栈在读元素、写入元素的帧会给 current（这一步正踩着的下标），它比 front/top 更具体。
    if (typeof state.current === "number" && !cursors.length) {
      const bounded = clamp(state.current, values.length);
      if (bounded !== null) panel.cursors = [{ key: "current", label: cursorLabel("current"), index: bounded }];
    }
    panel.focus = panel.cursors.length ? panel.cursors[0].index : null;
    if (kind === "queue" && typeof state.rear === "number") panel.chips.push({ label: "rear", value: String(state.rear) });
    // 走了多少步：引擎在每帧上累加比较/交换次数，它是"看起来很随机"和"选择排序比较得多、交换得少"之间的差别。
    for (const key of ["compareCount", "swapCount"]) {
      pushChip(panel.chips, chipLabel(key), (state as Record<string, unknown>)[key]);
    }
    const metadata = isRecord(state.metadata) ? state.metadata : {};
    if (typeof metadata.capacity === "number") panel.chips.push({ label: "容量", value: String(metadata.capacity) });
    return [panel];
  }

  if (kind === "sequential_list") {
    const values = asArray(state.items).map((item) => (isRecord(item) ? item.value : item));
    const panel = base("array", values);
    // 移动中的那个元素优先于插入/删除位置：后移/前移帧的高亮要跟着元素走，
    // 否则整条动画都钉在目标位置上，看起来就是"高亮卡住不动"。
    const marker = typeof state.movingIndex === "number" && state.movingIndex >= 0
      ? { key: "movingIndex", at: state.movingIndex }
      : typeof state.targetIndex === "number" && state.targetIndex >= 0
        ? { key: "targetIndex", at: state.targetIndex }
        : null;
    const bounded = marker === null ? null : clamp(marker.at, values.length);
    panel.cursors = marker !== null && bounded !== null ? [{ key: marker.key, label: cursorLabel(marker.key), index: bounded }] : [];
    panel.focus = panel.cursors.length ? panel.cursors[0].index : null;
    pushChip(panel.chips, "表长", state.length);
    pushChip(panel.chips, "插入位置", state.position);
    pushChip(panel.chips, "新值", state.value);
    const metadata = isRecord(state.metadata) ? state.metadata : {};
    pushChip(panel.chips, "容量", metadata.capacity);
    pushChip(chips, "操作", state.operation);
    return [panel];
  }

  if (kind === "sequential_list_merge") {
    const left = base("LA", asArray(state.left));
    const right = base("LB", asArray(state.right));
    const result = base("LC", asArray(state.result));
    // Each segment rides its own pointer: i over the left, j over the right, k over what has been built.
    const mark = (panel: FramePanel, key: string, at: number | undefined): void => {
      const bounded = at === undefined ? null : clamp(at, panel.values.length);
      panel.cursors = bounded === null ? [] : [{ key, label: cursorLabel(key), index: bounded }];
      panel.focus = panel.cursors.length ? panel.cursors[0].index : null;
    };
    mark(left, "i", typeof state.i === "number" ? state.i - 1 : undefined);
    mark(right, "j", typeof state.j === "number" ? state.j - 1 : undefined);
    mark(result, "k", typeof state.k === "number" ? state.k - 1 : undefined);
    pushChip(chips, "i", state.i);
    pushChip(chips, "j", state.j);
    pushChip(chips, "k", state.k);
    pushChip(chips, "比较", state.comparison);
    const selected = isRecord(state.selected) ? state.selected : null;
    if (selected) pushChip(chips, "本步选中", `${frameValueText(selected.value)}（来自 ${frameValueText(selected.source)}）`);
    return [left, right, result];
  }

  // A frame with a kind but neither view nor legacy fields: print whatever scalars it does carry.
  const scalars = Object.entries(state).filter(([key, value]) => key !== "kind" && key !== "meta" && !isRecord(value) && !Array.isArray(value));
  if (!scalars.length) return [];
  const panel = base("values", scalars.map(([, value]) => value));
  panel.kind = "records";
  panel.values = scalars.map(([key, value]) => ({ label: key, value: frameValueText(value) }));
  return [panel];
}

/**
 * Every meta field becomes a readable chip, and numeric cursors are also published so a sibling panel
 * can highlight the index the step is standing on. Nothing is filtered out: when the engine starts
 * reporting a new field, it shows up in the header without a frontend change.
 */
/**
 * Meta fields that never deserve a chip: the operation is already the player's headline, and a
 * graph's directedness is drawn as arrowheads, not spelled out.
 */
const HIDDEN_META_CHIPS = new Set(["operation", "directed", "invariant"]);

function collectMeta(panel: DsvpPanel, chips: FrameChip[], raw: Record<string, unknown>, pointers: Record<string, number>): void {
  const isMeta = panel.role === "meta" || panel.role === "probe" || panel.role === "matrix_index";
  for (const [key, value] of Object.entries(panel)) {
    if (key === "role" || key === "values" || key === "nodes" || key === "edges") continue;
    if (isMeta) raw[key] = value;
    if (typeof value === "number" && POINTER_KEYS.has(key)) pointers[key] = value;
    if ((isMeta || POINTER_KEYS.has(key)) && !HIDDEN_META_CHIPS.has(key)) {
      if (value === null || value === undefined || value === "") continue;
      // true/false 胶囊（"循环 true"、"headSelfLoop true"）对学生没有信息量——
      // 这些性质要么已经画在画面上（自环、箭头），要么根本没画出来，念一遍值毫无意义。
      if (typeof value === "boolean") continue;
      chips.push({ label: chipLabel(key), value: frameValueText(value) });
    }
  }
}

/**
 * How a cursor is written on the cell it stands on. Textbook names stay as they are (`i`, `mid`, `front`):
 * a student meets those in the book and in code, and translating them to "中点" would break the link.
 */
const CURSOR_LABELS: Record<string, string> = {
  index: "下标",
  current: "当前",
  top: "top",
  front: "front",
  rear: "rear",
  i: "i",
  j: "j",
  k: "k",
  position: "位置",
  targetIndex: "目标",
  movingIndex: "移动中",
  pivotIndex: "枢轴",
  // 简单选择排序的两个位置：正在扫的那一格，和目前最小的那一格。
  selected: "最小",
  scan: "扫描",
  mid: "mid",
  column: "列",
  focus: "当前",
  latest: "最新",
};

export function cursorLabel(key: string): string {
  return CURSOR_LABELS[key] ?? key;
}

/**
 * Cursors any panel may show, in the order the single focus used to pick from them.
 *
 * `front`/`rear` 在这里是有意的：队列的**两端**都要标出来——只标 front 的话，"队尾在哪、还剩几个空位"
 * 就得靠胶囊去读（2026-10-06 把队列和循环缓冲区一并过了一遍才发现）。
 */
const GENERIC_CURSOR_KEYS = ["current", "pivotIndex", "j", "i", "mid", "index", "position", "front", "rear", "selected", "scan"] as const;

/** Panels whose values are a chain of nodes: a list's links live in the gaps between the values. */
const CHAIN_ROLES = new Set(["L", "LA", "LB", "LC", "head", "new"]);
const CHAIN_KINDS = new Set([
  "linked_list",
  "doubly_linked_list",
  "circular_linked_list",
  "linked_stack",
  "linked_queue",
]);

/** 这个面板是不是"结点链"，是的话结点该长什么样——形状由结构定义（`kind`）决定。 */
function chainShapeOf(role: string, kind: string): ChainShape | null {
  if (!CHAIN_ROLES.has(role) && !CHAIN_KINDS.has(kind)) return null;
  return CHAIN_SHAPES[kind] ?? DEFAULT_CHAIN;
}

/**
 * Every named position the engine reports for one panel, in the order the focus picks from them.
 *
 * The role-specific rules come first and stay exclusive on purpose: a merge's LA rides `i` and LB rides
 * `j`, and letting the generic rules also fire there drew `j` on both segments, so both jumped together.
 * A `table`, `stack` or `queue` keeps its own marker *and* the generic ones, because "top 2" and "reading
 * slot 2" are both true and both worth seeing. An out-of-range pointer yields nothing, which is what the
 * single focus did before.
 */
export function cursorsFor(role: string, pointers: Record<string, number>, length: number): FrameCursor[] {
  const specific =
    role === "table" && pointers.index !== undefined ? { key: "index", at: pointers.index }
      : role === "stack" && pointers.top !== undefined ? { key: "top", at: pointers.top }
        : role === "queue" && pointers.front !== undefined ? { key: "front", at: pointers.front }
          // 双端栈：每一侧自己的栈顶都在那一侧数组的**最后一个元素**上（引擎给的是共享数组里的全局下标，
          // 直接拿来标本侧会越界），所以两侧都取 length-1，标签统一写 top。
          : role === "left" && pointers.topLeft !== undefined ? { key: "top", at: length - 1 }
            : role === "right" && pointers.topRight !== undefined ? { key: "top", at: length - 1 }
              : /A$/.test(role) && pointers.i !== undefined ? { key: "i", at: pointers.i - 1 }
                : /B$/.test(role) && pointers.j !== undefined ? { key: "j", at: pointers.j - 1 }
                  : /C$/.test(role) && length > 0 ? { key: "latest", at: length - 1 }
                    : role === "result" && length > 0 ? { key: "latest", at: length - 1 }
                      : role === "left" && pointers.i !== undefined ? { key: "i", at: pointers.i }
                        : role === "right" && pointers.j !== undefined ? { key: "j", at: pointers.j }
                          : null;

  if (specific) {
    const index = clamp(specific.at, length);
    if (index === null) return [];
    const first: FrameCursor = { key: specific.key, label: cursorLabel(specific.key), index };
    const shared = role === "table" || role === "stack" || role === "queue" ? genericCursors(pointers, length, first.key) : [];
    return [first, ...shared];
  }
  return genericCursors(pointers, length, "");
}

function genericCursors(pointers: Record<string, number>, length: number, skip: string): FrameCursor[] {
  const cursors: FrameCursor[] = [];
  for (const key of GENERIC_CURSOR_KEYS) {
    if (key === skip || pointers[key] === undefined) continue;
    // A 1-based position (insert at the 3rd slot) is drawn on the cell it names.
    const index = clamp(key === "position" ? pointers[key] - 1 : pointers[key], length);
    if (index !== null && !cursors.some((cursor) => cursor.key === key)) {
      cursors.push({ key, label: cursorLabel(key), index });
    }
  }
  return cursors;
}


/** The engine sometimes lists bare labels ("A", "B") and sometimes full objects; unify them. */
function normalizeNodes(value: unknown): DsvpNode[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === "string" || typeof item === "number") {
        const id = item;
        return { id, label: String(item) } as DsvpNode;
      }
      if (isRecord(item)) {
        const id = (item.id ?? item.label ?? "") as string | number;
        return { ...item, id, label: String(item.label ?? item.id ?? "") } as DsvpNode;
      }
      return null;
    })
    .filter((node): node is DsvpNode => node !== null);
}

function pushChip(chips: FrameChip[], label: string, value: unknown): void {
  if (value === null || value === undefined || value === "") return;
  chips.push({ label, value: frameValueText(value) });
}

function clamp(value: number, length: number): number | null {
  if (!Number.isFinite(value) || value < 0 || value >= length) return null;
  return value;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

/**
 * The frame shown before the first step.
 *
 * A learner has to see the input before watching what happens to it, and the initial state arrives in
 * three different shapes depending on where the trace came from: the local engine puts the whole frame in
 * `trace.steps[0].state`, its flattened `animationData.initial` is a list of view panels, and the older
 * in-process simulator puts a flat list of scalars there. All three are accepted here so the player never
 * opens on a blank canvas.
 */
export function initialFrame(
  definition: AnimationDefinition | null,
  trace?: Record<string, unknown> | null,
): DsvpState | null {
  const traced = firstTraceState(trace);
  if (traced) return traced;
  if (!definition) return null;
  const initial = Array.isArray(definition.initial) ? definition.initial : [];
  if (!initial.length) return null;
  const panels = initial.filter(
    (entry): entry is DsvpPanel => isRecord(entry) && typeof (entry as Record<string, unknown>).role === "string",
  );
  if (panels.length) return { kind: "", view: panels };
  return { kind: "", view: [{ role: "array", values: initial }] };
}

function firstTraceState(trace?: Record<string, unknown> | null): DsvpState | null {
  if (!trace) return null;
  const steps = trace.steps;
  if (!Array.isArray(steps) || !steps.length) return null;
  const first = steps[0];
  if (!isRecord(first)) return null;
  const state = first.state;
  if (!isRecord(state)) return null;
  if (Array.isArray(state.view) || typeof state.kind === "string") return state as DsvpState;
  return null;
}
