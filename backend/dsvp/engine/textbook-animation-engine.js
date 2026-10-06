const { AUX_SUPPORTED_PAIRS, simulateAuxiliaryOperation } = require("./textbook-animation-auxiliary");
const { SimulationInputError, ownOf, strictInt, normalizeParentArray, normalizeGraphSpec, requireVertex } = require("./textbook-validation");
const BASE_TEXTBOOK_SUPPORTED_PAIRS = Object.freeze({
  linked_list: new Set(["insert", "delete", "merge"]),
  doubly_linked_list: new Set(["insert", "delete"]),
  polynomial: new Set(["add"]),
  stack_app: new Set(["bracket_match", "expression_evaluate"]),
  recursion: new Set(["hanoi"]),
  circular_queue: new Set(["enqueue", "dequeue"]),
  string: new Set(["insert", "delete", "brute_force_match", "kmp_match"]),
  sparse_matrix: new Set(["transpose", "fast_transpose", "cross_list_build"]),
  generalized_list: new Set(["tail", "length", "depth", "atom_count", "copy"]),
  tree: new Set(["build", "preorder", "inorder", "postorder", "levelorder", "inorder_stack", "postorder_stack", "thread_inorder", "thread_predecessor", "thread_successor", "visit", "highlight"]),
  huffman: new Set(["build", "encode"]),
  union_find: new Set(["find", "union"]),
  graph: new Set(["dfs", "bfs", "path_search", "prim", "kruskal", "topological_sort", "critical_path", "dijkstra", "floyd", "visit", "highlight"]),
  search: new Set(["sequential", "binary", "block"]),
  bst: new Set(["search", "insert", "delete"]),
  avl: new Set(["insert"]),
  btree: new Set(["search", "insert", "delete"]),
  hash_table: new Set(["linear_probe_insert", "linear_probe_search", "quadratic_probe_insert", "random_probe_insert", "random_probe_search", "rehash_insert", "chaining_insert", "chaining_search"]),
  sort: new Set(["direct_insertion", "binary_insertion", "shell", "bubble", "quick", "simple_selection", "tournament_selection", "heap", "merge", "radix"]),
  external_sort: new Set(["two_way_merge", "replacement_selection", "multiway_merge"])
});
const TEXTBOOK_SUPPORTED_PAIRS = Object.freeze(Object.fromEntries(
  Array.from(new Set([...Object.keys(BASE_TEXTBOOK_SUPPORTED_PAIRS), ...Object.keys(AUX_SUPPORTED_PAIRS)]))
    .map((structure) => [
      structure,
      new Set([...(BASE_TEXTBOOK_SUPPORTED_PAIRS[structure] || []), ...(AUX_SUPPORTED_PAIRS[structure] || [])])
    ])
));

function deepClone(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

function sanitizeJson(value, depth = 0) {
  if (depth > 6) return null;
  if (value === null || ["string", "number", "boolean"].includes(typeof value)) {
    if (typeof value === "number" && !Number.isFinite(value)) return null;
    return value;
  }
  // 不再静默截断数组：超限在 normalizeTextbookRequest 中显式报错
  if (Array.isArray(value)) return value.map((item) => sanitizeJson(item, depth + 1));
  if (value && typeof value === "object") {
    const out = {};
    for (const [key, item] of Object.entries(value).slice(0, 80)) out[String(key).slice(0, 80)] = sanitizeJson(item, depth + 1);
    return out;
  }
  return null;
}

/** 递归检查数组规模：超过单次动画上限时显式报错，而不是悄悄截断前 120 个。 */
function assertArrayLimits(value, path, ValidationError, depth = 0) {
  if (depth > 8) return;
  if (Array.isArray(value)) {
    if (value.length > 120) {
      throw new ValidationError(
        "INPUT_TOO_LARGE",
        `${path} 包含 ${value.length} 个元素，超过单次动画 120 个元素的上限，请缩小示例规模`,
        path
      );
    }
    for (let i = 0; i < value.length; i++) assertArrayLimits(value[i], `${path}[${i}]`, ValidationError, depth + 1);
  } else if (value && typeof value === "object") {
    for (const [key, item] of Object.entries(value)) assertArrayLimits(item, `${path}.${key}`, ValidationError, depth + 1);
  }
}

function normalizeTextbookRequest(input, ValidationError, version) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new ValidationError("INVALID_REQUEST", "request 必须是对象", "request");
  }
  const unexpectedFields = Object.keys(input).filter((key) =>
    !["version", "structure", "operation", "params", "initial_state", "options", "source_ref"].includes(key)
  );
  if (unexpectedFields.length) {
    throw new ValidationError("UNEXPECTED_FIELD", `request 包含未定义字段：${unexpectedFields.join("、")}`, unexpectedFields.join(","));
  }
  const structure = String(input.structure || "").trim();
  const operation = String(input.operation || "").trim();
  if (!ownOf(TEXTBOOK_SUPPORTED_PAIRS, structure)?.has(operation)) {
    throw new ValidationError("UNSUPPORTED_OPERATION", `教材动画暂不支持 ${structure}/${operation}`, `${structure}/${operation}`);
  }
  const params = sanitizeJson(input.params && typeof input.params === "object" ? input.params : {}) || {};
  const rawState = input.initial_state && typeof input.initial_state === "object" ? input.initial_state : { data: [] };
  const data = sanitizeJson(rawState.data ?? []);
  const metadata = sanitizeJson(rawState.metadata && typeof rawState.metadata === "object" ? rawState.metadata : {}) || {};
  assertArrayLimits(params, "params", ValidationError);
  assertArrayLimits(data, "initial_state.data", ValidationError);
  const serialized = JSON.stringify({ params, data, metadata });
  if (serialized.length > 80_000) throw new ValidationError("INITIAL_STATE_TOO_LARGE", "教材动画输入过大，请缩小示例规模", String(serialized.length));
  return {
    version,
    structure,
    operation,
    params,
    initial_state: { data, metadata },
    options: {
      language: String(input.options?.language || "c").slice(0, 16),
      explain_level: String(input.options?.explain_level || "beginner").slice(0, 24)
    },
    source_ref: String(input.source_ref || "").slice(0, 160)
  };
}

function viewState(kind, rows, meta = {}) {
  return { kind, view: deepClone(rows), meta: deepClone(meta) };
}

function row(role, values, extra = {}) {
  return { role, values: deepClone(values), ...deepClone(extra) };
}

function textbookStateValues(state) {
  return Array.isArray(state?.view) ? deepClone(state.view) : [];
}

function numberArray(value, fallback = []) {
  if (!Array.isArray(value)) return [...fallback];
  // 空数组保持为空（由各模拟器决定空表语义）；非法元素显式报错，不再静默丢弃。
  return value.map((item, index) => {
    const n = Number(item);
    if (!Number.isFinite(n)) {
      throw new SimulationInputError("INVALID_ELEMENT", `第 ${index + 1} 个元素 ${JSON.stringify(item)} 不是有效数字`, `data[${index}]`);
    }
    return n;
  });
}

function scalarArray(value, fallback = []) {
  if (!Array.isArray(value)) return [...fallback];
  return value.map((item, index) => {
    if (item === null || ["string", "number", "boolean"].includes(typeof item)) return item;
    throw new SimulationInputError(
      "INVALID_ELEMENT",
      `第 ${index + 1} 个元素 ${JSON.stringify(item)} 类型不支持：只接受数字、字符串、布尔值或 null`,
      `data[${index}]`
    );
  });
}

function intParam(params, name, fallback, min = -1e9, max = 1e9) {
  // 提供了就严格校验（越界报错），未提供才用 fallback——不再静默夹取。
  return strictInt(params, name, fallback, min, max);
}

function makeHelpers(api) {
  return {
    makeStep: api.makeStep,
    makeTrace: api.makeTrace,
    action: api.action,
    emptyHighlights: api.emptyHighlights,
    makeRuntimeError: api.makeRuntimeError
  };
}

function simulateLinkedList(request, api) {
  const { makeStep, makeTrace, action, emptyHighlights } = makeHelpers(api);
  const op = request.operation;

  /* 单链表的"真相"是每个结点的 `next` 指向谁。按**代码级别**报帧：一帧 = 一行赋值，
     帧的标题就是那行代码；槽位里写**目标结点的序号**（`next: [2,3,null]`，写值遇到重复数据就分不清）；
     `pointers` 是**本面板自己**的具名指针；`write` 指出这一帧改的是哪个结点的指针。
     逐行帧标 `phase: "line"`，精简版把一段连续的 line 压成最后一帧（= 该阶段完成态）。 */
  const self = (values) => {
    const labels = values.map(String);
    return { labels, next: labels.map((_, i) => (i + 1 < labels.length ? i + 2 : null)) };
  };
  /* 结点插进显示序之后，**所有** next 都要按新的相邻关系重建（除开那个还没接上去的新结点）——
     只改两个结点的话，被挤到后面的老结点还指着自己（`next=[2,3,3,null]`）。 */
  const relink = (c, unlinked) => {
    c.next = c.labels.map((_, i) => (i === unlinked ? null : (i + 1 < c.labels.length ? i + 2 : null)));
  };
  const pan = (role, c, extra = {}) => row(role, c.labels, { next: [...c.next], ...extra });
  let sid = 1;
  const steps = [];
  const snap = (code, rows, meta, act, line) => {
    steps.push(makeStep(sid++, line ? "line" : "assign", code, code, viewState("linked_list", [...rows, row("meta", [], meta)]), act ? [act] : []));
  };

  if (op === "merge") {
    const raw = Array.isArray(request.initial_state.data) ? request.initial_state.data : [];
    const left = scalarArray(request.params.left ?? raw[0], [1, 3, 5]);
    const right = scalarArray(request.params.right ?? raw[1], [2, 4, 6]);
    const A = self(left), B = self(right), C = { labels: [], next: [] };
    const base = { operation: op, i: 0, j: 0 };
    steps.push(makeStep(sid++, "init", "p = A, q = B, r = C", "p、q 指向两个待合并链表，r 是结果链表的尾指针",
      viewState("linked_list", [pan("LA", A, { pointers: { p: 0 } }), pan("LB", B, { pointers: { q: 0 } }), pan("LC", C, { pointers: {} }), row("meta", [], base)])));
    let i = 0, j = 0;
    while (i < left.length || j < right.length) {
      const fromA = j >= right.length || (i < left.length && Number(left[i]) <= Number(right[j]));
      const pointerAt = (idx, len) => Math.min(idx, Math.max(0, len - 1));
      snap(`if (p->data <= q->data) → ${fromA ? "true" : "false"}`,
        [pan("LA", A, { pointers: { p: pointerAt(i, left.length) } }), pan("LB", B, { pointers: { q: pointerAt(j, right.length) } }), pan("LC", C, {})],
        { ...base, i, j }, action("compare", "比较两个链表的当前结点", {}));
      const value = fromA ? left[i++] : right[j++];
      C.labels.push(String(value));
      C.next = C.labels.map((_, k) => (k + 1 < C.labels.length ? k + 2 : null));
      snap(`r->next = ${fromA ? "p" : "q"}`,
        [pan("LA", A, { pointers: { p: pointerAt(i, left.length) } }), pan("LB", B, { pointers: { q: pointerAt(j, right.length) } }), pan("LC", C, { focusIndex: C.labels.length - 1, pointers: { r: C.labels.length - 1 } })],
        { ...base, i, j }, action("link", "结果链尾接到选中的结点", { value }), true);
      // 取完要**把被取的那一边的游标往前推**（取 A 才是 `p = p->next`）——写错过一次：两边都写成 p。
      snap(fromA ? "p = p->next" : "q = q->next",
        [pan("LA", A, { pointers: { p: pointerAt(i, left.length) } }), pan("LB", B, { pointers: { q: pointerAt(j, right.length) } }), pan("LC", C, { pointers: { r: C.labels.length - 1 } })],
        { ...base, i, j }, null, true);
    }
    return makeTrace(request, "有序单链表合并", `LA=[${left.join(",")}], LB=[${right.join(",")}]`, `LC=[${C.labels.join(",")}]`, steps);
  }

  const items = scalarArray(request.initial_state.data, [10, 20, 30]);
  if (op === "delete" && items.length === 0) {
    return api.makeRuntimeError(request, "单链表删除", "LIST_UNDERFLOW", "链表为空，没有可以删除的结点。", viewState("linked_list", [row("L", []), row("meta", [], { operation: op })]), "空链表");
  }
  const position = intParam(request.params, "position", op === "insert" ? Math.min(2, items.length + 1) : Math.min(2, items.length), 1, op === "insert" ? items.length + 1 : Math.max(1, items.length));
  const index = position - 1;
  const base = { operation: op, position };
  const cur = self(items);

  if (op === "insert") {
    const value = request.params.value ?? 15;
    snap("L", [pan("L", cur, { pointers: { L: 0 } })], { ...base }, action("inspect", "L 是头指针", {}));
    if (index > 0) {
      snap("p = L", [pan("L", cur, { pointers: { L: 0, p: 0 } })], { ...base }, null, true);
      for (let k = 1; k < index; k += 1) {
        snap("p = p->next", [pan("L", cur, { focusIndex: k, pointers: { L: 0, p: k } })], { ...base }, null, true);
      }
    }
    /* 新结点先以"next 还指着空"的样子出现，再一行一行接上去——教科书画的样子。 */
    cur.labels.splice(index, 0, "");
    relink(cur, index);
    snap("s = malloc()", [pan("L", cur, { pointers: { L: 0, s: index } })], { ...base, value }, null, true);
    cur.labels[index] = String(value);
    snap(`s->data = ${value}`, [pan("L", cur, { pointers: { L: 0, s: index } })], { ...base, value }, null, true);
    if (index === 0) {
cur.next[0] = 2;
      snap("s->next = L", [pan("L", cur, { pointers: { L: 1, s: 0 }, write: 0 })], { ...base, value }, action("link", "新结点 next 指向原首结点", { to: 2 }));
      snap("L = s", [pan("L", cur, { pointers: { L: 0, s: 0 } })], { ...base, value });
    } else {
const successor = index + 2 <= cur.labels.length ? index + 2 : null;
      cur.next[index] = successor;
      snap("s->next = p->next", [pan("L", cur, { pointers: { L: 0, p: index - 1, s: index }, write: index })], { ...base, value }, action("link", "新结点先接住 p 的后继", { to: successor }));
      cur.next[index - 1] = index + 1;
      snap("p->next = s", [pan("L", cur, { pointers: { L: 0, p: index - 1, s: index }, write: index - 1 })], { ...base, value }, action("link", "再让 p 指向新结点", { to: index + 1 }));
    }
    const result = [...items]; result.splice(index, 0, value);
    return makeTrace(request, "单链表插入", `L=[${items.join(",")}]`, `L=[${result.join(",")}]`, steps);
  }

  /* 删除：q 指向待删结点，让前驱直接指向 q 的后继，再释放 q。 */
  const removed = items[index];
  snap("L", [pan("L", cur, { pointers: { L: 0 } })], { ...base }, action("inspect", "L 是头指针", {}));
  if (index === 0) {
    snap("q = L", [pan("L", cur, { pointers: { L: 0, q: 0 } })], { ...base }, null, true);
    snap("L = L->next", [pan("L", cur, { pointers: { L: 1, q: 0 } })], { ...base }, null, true);
  } else {
    snap("p = L", [pan("L", cur, { pointers: { L: 0, p: 0 } })], { ...base }, null, true);
    for (let k = 1; k < index - 1; k += 1) {
      snap("p = p->next", [pan("L", cur, { focusIndex: k, pointers: { L: 0, p: k } })], { ...base }, null, true);
    }
    snap("q = p->next", [pan("L", cur, { focusIndex: index, pointers: { L: 0, p: index - 1, q: index } })], { ...base }, null, true);
    cur.next[index - 1] = cur.next[index];
    snap("p->next = q->next", [pan("L", cur, { pointers: { L: 0, p: index - 1, q: index }, write: index - 1 })], { ...base }, action("link", "前驱直接指向 q 的后继", { to: cur.next[index] }));
  }
  const freed = self(items.filter((_, k) => k !== index));
  snap("free(q)", [pan("L", freed, { pointers: { L: 0 } })], { ...base }, action("free", "释放待删结点", { value: removed }));
  const result = [...items]; result.splice(index, 1);
  return makeTrace(request, "单链表删除", `L=[${items.join(",")}]`, `L=[${result.join(",")}]`, steps);
}

function simulateDoublyList(request, api) {
  const { makeStep, makeTrace, action, emptyHighlights } = makeHelpers(api);
  const items = scalarArray(request.initial_state.data, [10,20,30]);
  const op = request.operation;
  if (op === "delete" && items.length === 0) {
    return api.makeRuntimeError(request, "双向链表删除", "LIST_UNDERFLOW", "链表为空，没有可以删除的结点。", viewState("doubly_linked_list", [row("L", []), row("meta", [], { operation: op })]), "空链表");
  }
  const maxPos = op === "insert" ? items.length + 1 : items.length;
  const position = intParam(request.params, "position", Math.min(2,maxPos), 1, Math.max(1,maxPos));
  const index = position - 1;

  /* 双向链表的"真相"是每个结点的**两个**指针：`prior` 指向前驱、`next` 指向后继。
     按**代码级别**报帧：一帧 = 一行赋值（`s->prior = p;` → `s->next = p->next;` → `p->next->prior = s;`
     → `p->next = s;`），每格槽位写"它此刻指向谁"（`prior` 那格和 `next` 那格各写各的），
     这一帧在改哪一格就把哪一格点亮——"next/pre 到底怎么改的"就是画面本身，不需要旁白。
     新结点先以"两个指针都空"的样子出现，再一行一行接上去，正是教科书画的样子。 */
  const self = (labels) => ({
    labels,
    prior: labels.map((_, i) => (i === 0 ? null : i)),
    next: labels.map((_, i) => (i + 1 < labels.length ? i + 2 : null)),
  });
  const pan = (c, extra = {}) => row("L", c.labels, { prior: [...c.prior], next: [...c.next], ...extra });
  const steps = [];
  let sid = 1;
  const snap = (code, cur, extra, act, line = false) => {
    steps.push(makeStep(sid++, line ? "line" : "assign", code, code,
      viewState("doubly_linked_list", [pan(cur, extra), row("meta", [], { operation: op, position, ...(extra?.meta || {}) })]),
      act ? [act] : []));
  };

  if (op === "insert") {
    const value = request.params.value ?? 15;
    const base = self(items.map(String));
    /* 先把新结点插进显示序（它最终就落在 position 这个位置），但**两个指针都还空着**——
       旁边两个结点也还互相指着，这就是"还没接上去"的样子。 */
    const cur = { labels: [...base.labels], prior: [...base.prior], next: [...base.next] };
    /* 第 0 帧必须是**操作之前**的样子：播放器的"起点"显示的就是它。
       少了这一帧，起点上那个"还没申请的新结点"（∅）就已经在表里了（Rrd 2026-10-06 一眼看出来的）。 */
    snap("L", cur, { pointers: { L: 0 } }, action("inspect", "L 是头指针", {}));
    cur.labels.splice(index, 0, "");
    cur.prior.splice(index, 0, null);
    cur.next.splice(index, 0, null);
    snap("s = malloc()", cur, { pointers: { L: 0, s: index } }, null, true);
    cur.labels[index] = String(value);
    snap(`s->data = ${value}`, cur, { pointers: { L: 0, s: index } }, null, true);
    /* 改完再报帧：每个画面显示的是这一行**执行之后**的状态——学生点一次看到一行代码的效果。 */
    if (index === 0) {
      cur.next[index] = 2;
      snap("s->next = L", cur, { pointers: { L: 0, s: index }, write: index }, action("link", "新结点 next 指向原首结点", { to: cur.labels[1] }));
      cur.prior[1] = 1;
      snap("L->prior = s", cur, { pointers: { L: 1, s: index }, write: 1, writeSlot: "prior" }, action("link", "原首结点 prior 指向新结点", { to: value }), true);
      snap("L = s", cur, { pointers: { L: 0, s: index } }, null, true);
    } else {
      snap("p = L", cur, { pointers: { L: 0, p: 0 } });
      for (let i = 1; i < index; i += 1) {
        snap("p = p->next", cur, { focusIndex: i, pointers: { L: 0, p: i, s: index } }, null, true);
      }
      cur.prior[index] = index;
      snap("s->prior = p", cur, { pointers: { L: 0, p: index - 1, s: index }, write: index, writeSlot: "prior" }, action("link", "新结点 prior 指向 p", { to: cur.labels[index - 1] }), true);
      cur.next[index] = cur.next[index - 1];
      snap("s->next = p->next", cur, { pointers: { L: 0, p: index - 1, s: index }, write: index }, action("link", "新结点 next 指向 p 的后继", { to: cur.next[index - 1] }), true);
      cur.prior[index + 1] = index + 1;
      snap("p->next->prior = s", cur, { pointers: { L: 0, p: index - 1, s: index }, write: index + 1, writeSlot: "prior" }, action("link", "后继的 prior 改指新结点", { to: value }), true);
      cur.next[index - 1] = index + 1;
      snap("p->next = s", cur, { pointers: { L: 0, p: index - 1, s: index }, write: index - 1 }, action("link", "p 的 next 改指新结点", { to: value }), true);
    }
    const result = [...items]; result.splice(index, 0, value);
    return makeTrace(request, "双向链表插入", `L=[${items.join(",")}]`, `L=[${result.join(",")}]`, steps);
  }

  /* 删除：先 q 指向待删结点，再让前后两个结点互相指对方，最后释放 q。 */
  const removed = items[index];
  const cur = self(items.map(String));
  snap("q = L", cur, { pointers: { L: 0, q: 0 } }, null, true);
  if (index === 0) {
    snap("L = L->next", cur, { pointers: { L: 1, q: 0 } }, null, true);
    cur.prior[1] = null;
    snap("L->prior = NULL", cur, { pointers: { L: 1, q: 0 }, write: 1, writeSlot: "prior" }, action("link", "新首结点没有前驱", { to: "NULL" }), true);
  } else {
    snap("p = L", cur, { pointers: { L: 0, p: 0, q: index } });
    for (let i = 1; i < index; i += 1) {
      snap("p = p->next", cur, { focusIndex: i, pointers: { L: 0, p: i, q: index } }, null, true);
    }
    snap("q = p->next", cur, { pointers: { L: 0, p: index - 1, q: index }, focusIndex: index });
    const successor = cur.next[index];
    cur.next[index - 1] = successor;
    snap("p->next = q->next", cur, { pointers: { L: 0, p: index - 1, q: index }, write: index - 1 }, action("link", "p 直接指向 q 的后继", { to: successor }), true);
    if (index + 1 < cur.labels.length) {
      cur.prior[index + 1] = index;
      snap("q->next->prior = p", cur, { pointers: { L: 0, p: index - 1, q: index }, write: index + 1, writeSlot: "prior" }, action("link", "后继的 prior 改指 p", { to: cur.labels[index - 1] }), true);
    }
  }
  const freed = self(items.filter((_, i) => i !== index).map(String));
  snap("free(q)", freed, { pointers: { L: 0 } }, action("free", "释放待删结点", { value: removed }));
  const result = [...items]; result.splice(index, 1);
  return makeTrace(request, "双向链表删除", `L=[${items.join(",")}]`, `L=[${result.join(",")}]`, steps);
}

function normalizeTerms(value, fallback) {
  const src = Array.isArray(value) ? value : fallback;
  return src.map((t, i) => {
    if (!t || typeof t !== "object" || Array.isArray(t)) {
      throw new SimulationInputError(
        "INVALID_TERM",
        `第 ${i + 1} 项 ${JSON.stringify(t)} 格式不正确：多项式项应为 { "coef": 数字, "exp": 数字 }（如 { "coef": 3, "exp": 4 }）`,
        `terms[${i}]`
      );
    }
    const coef = Number(t.coef ?? t.coefficient);
    const exp = Number(t.exp ?? t.exponent);
    if (!Number.isFinite(coef)) {
      throw new SimulationInputError("INVALID_TERM", `第 ${i + 1} 项的系数必须是有限数字（当前 ${JSON.stringify(t.coef ?? t.coefficient)}）`, `terms[${i}]`);
    }
    if (!Number.isInteger(exp) || exp < 0) {
      throw new SimulationInputError("INVALID_TERM", `第 ${i + 1} 项的指数必须是非负整数（当前 ${JSON.stringify(t.exp ?? t.exponent)}）`, `terms[${i}]`);
    }
    return { coef, exp };
  }).sort((a, b) => b.exp - a.exp);
}

function simulatePolynomial(request, api) {
  const { makeStep, makeTrace, action } = makeHelpers(api);
  const left=normalizeTerms(request.params.left ?? request.initial_state.data?.[0],[{coef:3,exp:3},{coef:2,exp:1}]);
  const right=normalizeTerms(request.params.right ?? request.initial_state.data?.[1],[{coef:4,exp:2},{coef:-2,exp:1},{coef:5,exp:0}]);
  const result=[]; let i=0,j=0,sid=1;
  const steps=[makeStep(sid++,"init","准备两个多项式链表","按指数从高到低比较当前项。",viewState("polynomial",[row("PA",left),row("PB",right),row("PC",result),row("meta",[],{i,j})]))];
  while(i<left.length||j<right.length){
    if(j>=right.length || (i<left.length&&left[i].exp>right[j].exp)){ result.push({...left[i]}); i++; }
    else if(i>=left.length || right[j].exp>left[i].exp){ result.push({...right[j]}); j++; }
    else { const coef=left[i].coef+right[j].coef; const exp=left[i].exp; i++; j++; if(coef!==0) result.push({coef,exp}); }
    steps.push(makeStep(sid++,"merge","比较指数并处理当前项",`当前结果：${result.map(t=>`${t.coef}x^${t.exp}`).join(" + ")||"0"}`,viewState("polynomial",[row("PA",left),row("PB",right),row("PC",result),row("meta",[],{i,j})]),[action("compare","比较两个当前结点指数",{target:"exp"})]));
  }
  return makeTrace(request,"一元多项式相加","两个多项式链表","得到合并后的多项式",steps);
}

function simulateBracketMatch(request, api) {
  const { makeStep, makeTrace, action } = makeHelpers(api);
  const text=String(request.params.text ?? request.initial_state.data ?? "{[()]}").slice(0,80);
  const stack=[]; const steps=[makeStep(1,"init","准备空栈",`从左到右扫描 ${text}`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:-1})]))];
  const pairs={")":"(","]":"[","}":"{"}; let ok=true,sid=2;
  for(let i=0;i<text.length;i++){
    const ch=text[i];
    if("([{<".includes(ch)){ stack.push(ch); steps.push(makeStep(sid++,"push","左括号入栈",`${ch} 入栈。`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:i,current:ch})]),[action("push","左括号入栈",{value:ch})])); }
    else if(")]}>".includes(ch)){
      const expected=pairs[ch] ?? ({">":"<"})[ch]; const top=stack[stack.length-1];
      if(top===expected){ stack.pop(); steps.push(makeStep(sid++,"pop","匹配并出栈",`${top} 与 ${ch} 匹配，栈顶出栈。`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:i,current:ch})]),[action("pop","匹配括号出栈",{value:top})])); }
      else { ok=false; steps.push(makeStep(sid++,"error","发现不匹配",`当前位置 ${ch} 与栈顶 ${String(top??"空")} 不匹配。`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:i,current:ch,error:true})]))); break; }
    }
  }
  if(stack.length) ok=false;
  steps.push(makeStep(sid,"done","匹配结束",ok?"扫描结束且栈空，括号匹配。":"扫描结束后仍有不匹配括号。",viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{done:true,ok})])));
  return makeTrace(request,"括号匹配",text,ok?"匹配成功":"匹配失败",steps);
}

function tokenizeExpression(expr){
  const tokens=String(expr).replace(/\s+/g,"").match(/\d+(?:\.\d+)?|[()+\-*/]/g)||[];
  return tokens.slice(0,80);
}
function precedence(op){ return op==="+"||op==="-"?1:op==="*"||op==="/"?2:0; }
function applyOp(a,b,op){ if(op==="+")return a+b;if(op==="-")return a-b;if(op==="*")return a*b;if(op==="/")return b===0?NaN:a/b;return NaN; }
function simulateExpression(request, api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const rawExpr=String(request.params.expression??request.initial_state.data??"3+5*2");
  if (rawExpr.length > 120) throw new SimulationInputError("INPUT_TOO_LONG", `表达式长度 ${rawExpr.length} 超过 120 字符上限`, "expression");
  if (!/^[\d+\-*/().\s]*$/.test(rawExpr)) throw new SimulationInputError("INVALID_EXPRESSION", "表达式含不支持的字符：只接受数字、+ - * / ( ) 和空白");
  if (!/\d/.test(rawExpr)) throw new SimulationInputError("INVALID_EXPRESSION", "表达式为空或不含操作数");
  const expr=rawExpr; const tokens=tokenizeExpression(expr);
  const vals=[],ops=[]; let sid=1; const steps=[makeStep(sid++,"init","准备两个栈","操作数栈和运算符栈均为空。",viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:-1})]))];
  const reduce=()=>{ const op=ops.pop(),b=vals.pop(),a=vals.pop(),r=applyOp(a,b,op); vals.push(r); return {op,a,b,r}; };
  for(let i=0;i<tokens.length;i++){
    const t=tokens[i];
    if(/^\d/.test(t)){ vals.push(Number(t)); steps.push(makeStep(sid++,"push","操作数入栈",`${t} 入操作数栈。`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})]),[action("push","操作数入栈",{value:Number(t)})])); continue; }
    if(t==="("){ops.push(t);continue;}
    if(t===")"){ while(ops.length&&ops[ops.length-1]!=="("){const x=reduce();steps.push(makeStep(sid++,"compute","计算栈顶运算",`${x.a}${x.op}${x.b}=${x.r}`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})])));} ops.pop(); continue; }
    while(ops.length&&precedence(ops[ops.length-1])>=precedence(t)){const x=reduce();steps.push(makeStep(sid++,"compute","先计算高优先级运算",`${x.a}${x.op}${x.b}=${x.r}`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})])));} ops.push(t);
    steps.push(makeStep(sid++,"push","运算符入栈",`${t} 入运算符栈。`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})])));
  }
  while(ops.length){const x=reduce();steps.push(makeStep(sid++,"compute","完成剩余运算",`${x.a}${x.op}${x.b}=${x.r}`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{done:false})])));}
  const result=vals[0]; steps.push(makeStep(sid,"done","表达式求值完成",`结果为 ${String(result)}。`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{done:true,result})])));
  return makeTrace(request,"无括号算术表达式求值",expr,String(result),steps);
}

function simulateHanoi(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  // 注册表声明的是 n；diskCount 是历史参数名，保留兼容。两个名字共用 1~6 的范围（盘数超过 6 步数会超出演示上限）。
  const n=intParam(request.params,"n",intParam(request.params,"diskCount",3,1,6),1,6); const towers={A:Array.from({length:n},(_,i)=>n-i),B:[],C:[]}; let sid=1;
  const steps=[makeStep(sid++,"init","汉诺塔初始状态",`${n} 个盘片位于 A 柱。`,viewState("hanoi",[row("A",towers.A),row("B",towers.B),row("C",towers.C)]))];
  function move(k,from,aux,to){if(k===0)return;move(k-1,from,to,aux);const disk=towers[from].pop();towers[to].push(disk);steps.push(makeStep(sid++,"move","移动盘片",`将盘片 ${disk} 从 ${from} 移到 ${to}。`,viewState("hanoi",[row("A",towers.A),row("B",towers.B),row("C",towers.C),row("meta",[],{disk,from,to})]),[action("move","移动盘片",{from,to,value:disk})]));move(k-1,aux,from,to);} move(n,"A","B","C");
  return makeTrace(request,"汉诺塔递归过程",`${n} 个盘片在 A`,`全部移动到 C`,steps);
}

function circularQueueState(buffer,front,rear,count,capacity,extra={}){return viewState("circular_queue",[row("buffer",buffer),row("meta",[],{front,rear,count,capacity,...extra})]);}
function simulateCircularQueue(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api); const capacity=intParam(request.params,"capacity",5,2,20); const initial=scalarArray(request.initial_state.data,[4,7]);
  if (initial.length > capacity-1) throw new SimulationInputError("INITIAL_STATE_OVERFLOW", `初始元素 ${initial.length} 个超过循环队列可容纳上限 ${capacity-1}（需保留一个空单元区分队空与队满），请增大 capacity 或减少初始元素`, "initialData");
  let front=0,count=initial.length,rear=count%capacity; const buffer=Array(capacity).fill(null); initial.forEach((v,i)=>buffer[i]=v); const op=request.operation;
  const steps=[makeStep(1,"init","循环队列初始状态",`front=${front}, rear=${rear}。`,circularQueueState(buffer,front,rear,count,capacity))];
  if(op==="enqueue"){
    if(count>=capacity-1)return api.makeRuntimeError(request,"循环队列入队","QUEUE_OVERFLOW","保留一个空单元时队列已满。",circularQueueState(buffer,front,rear,count,capacity),`count=${count}`);
    const value=request.params.value??9; buffer[rear]=value; steps.push(makeStep(2,"write","写入 rear 位置",`将 ${value} 写入下标 ${rear}。`,circularQueueState(buffer,front,rear,count,capacity,{current:rear}),[action("enqueue","写入队尾",{target:rear,value})])); rear=(rear+1)%capacity; count++; steps.push(makeStep(3,"move","rear 循环后移",`rear=(rear+1)%${capacity}=${rear}。`,circularQueueState(buffer,front,rear,count,capacity),[action("move","rear 取模后移",{target:"rear",value:rear})]));
    return makeTrace(request,"循环队列入队","初始循环队列",`front=${front},rear=${rear}`,steps);
  }
  if(count===0)return api.makeRuntimeError(request,"循环队列出队","QUEUE_UNDERFLOW","当前循环队列为空。",circularQueueState(buffer,front,rear,count,capacity),"空队列");
  const removed=buffer[front]; buffer[front]=null; steps.push(makeStep(2,"read","取出 front 元素",`读取并删除 ${removed}。`,circularQueueState(buffer,front,rear,count,capacity,{current:front,removed}),[action("dequeue","删除队首",{target:front,value:removed})])); front=(front+1)%capacity; count--; steps.push(makeStep(3,"move","front 循环后移",`front=(front+1)%${capacity}=${front}。`,circularQueueState(buffer,front,rear,count,capacity),[action("move","front 取模后移",{target:"front",value:front})]));
  return makeTrace(request,"循环队列出队","初始循环队列",`front=${front},rear=${rear}`,steps);
}

function simulateString(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api); const op=request.operation; const text=String(request.params.text??request.initial_state.data??"DATASTRUCTURE").slice(0,80); const chars=[...text];
  if(op==="insert"||op==="delete"){
    const position=intParam(request.params,"position",Math.min(3,chars.length+1),1,op==="insert"?chars.length+1:Math.max(1,chars.length)); const idx=position-1; const steps=[makeStep(1,"init","原字符串",text,viewState("string",[row("text",chars),row("meta",[],{position,operation:op})]))];
    if(op==="insert"){const value=String(request.params.value??"AI");steps.push(makeStep(2,"locate","定位插入点","第 position 个字符前是插入位置，该字符及其后内容都要后移。",viewState("string",[row("text",chars),row("meta",[],{position,operation:op,value})]),[action("locate","定位插入位置",{target:idx,value})]));const result=[...chars];result.splice(idx,0,...value);steps.push(makeStep(3,"shift","为新串腾出位置",`第 ${position} 个字符及其后内容整体后移，写入 ${value}。`,viewState("string",[row("text",result),row("meta",[],{position,operation:op,value})]),[action("move","后移字符",{target:idx})]));return makeTrace(request,"顺序串插入",text,result.join(""),steps);}
    const count=intParam(request.params,"count",1,1,chars.length-idx);steps.push(makeStep(2,"locate","定位待删区间",`从第 ${position} 个字符起共 ${count} 个字符将被删除。`,viewState("string",[row("text",chars),row("meta",[],{position,count,operation:op})]),[action("locate","定位删除区间",{target:idx,value:count})]));const result=[...chars];const removed=result.splice(idx,count).join("");steps.push(makeStep(3,"shift","删除并前移",`删除 ${removed}，后续字符前移。`,viewState("string",[row("text",result),row("meta",[],{position,count,operation:op,removed})]),[action("delete","删除字符区间",{target:idx,value:removed})]));return makeTrace(request,"顺序串删除",text,result.join(""),steps);
  }
  const rawPattern=String(request.params.pattern??"STRUCT"); if (rawPattern.length>40) throw new SimulationInputError("INPUT_TOO_LONG",`模式串长度 ${rawPattern.length} 超过 40 字符上限`,"pattern"); if (!rawPattern.length) throw new SimulationInputError("EMPTY_PATTERN","模式串不能为空"); const pattern=rawPattern; const p=[...pattern]; const steps=[makeStep(1,"init","准备模式匹配",`主串=${text}，模式串=${pattern}`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:0,j:0,operation:op})]))]; let sid=2;
  if(op==="brute_force_match"){
    for(let start=0;start<=chars.length-p.length;start++){
      let j=0; while(j<p.length&&chars[start+j]===p[j]){steps.push(makeStep(sid++,"compare","字符相等",`${chars[start+j]} = ${p[j]}`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:start+j,j,start,operation:op})]),[action("compare","比较当前字符",{from:start+j,to:j})]));j++;}
      if(j===p.length){steps.push(makeStep(sid,"done","匹配成功",`模式串首次出现在位置 ${start+1}。`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:start,j,match:start,operation:op})])));return makeTrace(request,"Brute-Force 模式匹配",text,`位置 ${start+1}`,steps);}
      steps.push(makeStep(sid++,"shift","模式串后移一位",`在起点 ${start+1} 处失配，下一轮从 ${start+2} 开始。`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:start+j,j,start,operation:op,mismatch:true})])));
    }
    return makeTrace(request,"Brute-Force 模式匹配",text,"未找到",steps);
  }
  const next=Array(p.length).fill(0); for(let i=1,j=0;i<p.length;i++){while(j>0&&p[i]!==p[j])j=next[j-1];if(p[i]===p[j])j++;next[i]=j;}
  let i=0,j=0; while(i<chars.length){ if(chars[i]===p[j]){steps.push(makeStep(sid++,"compare","字符相等",`${chars[i]} = ${p[j]}`,viewState("string_match",[row("text",chars),row("pattern",p),row("next",next),row("meta",[],{i,j,operation:op})])));i++;j++;if(j===p.length){const pos=i-j;steps.push(makeStep(sid,"done","匹配成功",`KMP 找到位置 ${pos+1}。`,viewState("string_match",[row("text",chars),row("pattern",p),row("next",next),row("meta",[],{i,j,match:pos,operation:op})])));return makeTrace(request,"KMP 模式匹配",text,`位置 ${pos+1}`,steps);}} else if(j>0){const old=j;j=next[j-1];steps.push(makeStep(sid++,"fallback","利用 next 回退模式指针",`j 从 ${old} 回退到 ${j}，主串指针 i 不回退。`,viewState("string_match",[row("text",chars),row("pattern",p),row("next",next),row("meta",[],{i,j,operation:op})])));} else i++; }
  return makeTrace(request,"KMP 模式匹配",text,"未找到",steps);
}

function denseMatrix(value){ if(!Array.isArray(value)||value.length===0) throw new SimulationInputError("INVALID_MATRIX","matrix 必须是非空二维数组（如 [[0,5,0],[2,0,3],[0,0,4]]）","matrix"); if(value.length>12) throw new SimulationInputError("INPUT_TOO_LARGE",`矩阵动画最多演示 12×12，当前 ${value.length} 行`,"matrix"); return value.map((r,i)=>{ if(!Array.isArray(r)) throw new SimulationInputError("INVALID_MATRIX",`第 ${i+1} 行不是数组`,"matrix"); if(r.length>12) throw new SimulationInputError("INPUT_TOO_LARGE",`矩阵动画最多演示 12×12，第 ${i+1} 行有 ${r.length} 列`,"matrix"); return r.map((x,j)=>{ const n=Number(x); if(!Number.isFinite(n)) throw new SimulationInputError("INVALID_MATRIX",`matrix[${i}][${j}]=${JSON.stringify(x)} 不是有效数字`,"matrix"); return n; }); }); }
function triplesFromMatrix(m){const out=[];for(let r=0;r<m.length;r++)for(let c=0;c<(m[r]||[]).length;c++)if(m[r][c]!==0)out.push([r,c,m[r][c]]);return out;}
function simulateSparseMatrix(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const m=denseMatrix(request.params.matrix??request.initial_state.data);const triples=triplesFromMatrix(m);const rows=m.length,cols=Math.max(0,...m.map(r=>r.length));const result=[];let sid=1;
  const steps=[makeStep(sid++,"init","三元组表",`非零元共有 ${triples.length} 个。`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",result),row("meta",[],{rows,cols,operation:request.operation})]))];
  if(request.operation==="cross_list_build"){
    const crossNodes=[];
    for(const [r,c,v] of triples){
      crossNodes.push({row:r,col:c,value:v});
      steps.push(makeStep(sid++,"link","建立十字链表结点",`为 a[${r}][${c}]=${v} 建结点，并分别接入第 ${r} 行链和第 ${c} 列链。`,viewState("sparse_matrix",[row("matrix",m,{focusCell:[r,c]}),row("cross_nodes",crossNodes),row("meta",[],{rows,cols,current:[r,c],operation:request.operation})]),[action("link","同时连接 right/down 指针",{target:`(${r},${c})`,value:v})]));
    }
    return makeTrace(request,"稀疏矩阵十字链表建立",`非零元=${triples.length}`,`结点数=${crossNodes.length}`,steps);
  }
  if(request.operation==="transpose"){
    for(let c=0;c<cols;c++)for(const [r,cc,v] of triples)if(cc===c){result.push([cc,r,v]);steps.push(makeStep(sid++,"copy","按列扫描转置",`(${r},${cc},${v}) → (${cc},${r},${v})`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",result),row("meta",[],{column:c,operation:request.operation})]),[action("copy","交换行列下标",{value:v})]));}
  }else{
    const count=Array(cols).fill(0);triples.forEach(t=>count[t[1]]++);const start=Array(cols).fill(0);for(let i=1;i<cols;i++)start[i]=start[i-1]+count[i-1];const pos=[...start];const out=Array(triples.length);
    steps.push(makeStep(sid++,"count","统计各列非零元个数",`num=[${count.join(",")}]，cpot=[${start.join(",")}]。`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",[]),row("num",count),row("cpot",start),row("meta",[],{operation:request.operation})])));
    for(const [r,c,v] of triples){const k=pos[c]++;out[k]=[c,r,v];steps.push(makeStep(sid++,"place","一次定位写入",`原三元组 (${r},${c},${v}) 直接写入转置表位置 ${k}。`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",out.filter(Boolean)),row("num",count),row("cpot",start),row("meta",[],{current:k,operation:request.operation})]),[action("copy","按 cpot 定位",{target:k,value:v})]));} result.push(...out);
  }
  return makeTrace(request,request.operation==="fast_transpose"?"稀疏矩阵快速转置":"稀疏矩阵转置",`三元组数=${triples.length}`,`转置三元组数=${result.length}`,steps);
}

function generalizedDepth(x){if(!Array.isArray(x))return 0;if(x.length===0)return 1;return 1+Math.max(...x.map(generalizedDepth));}
function generalizedAtomCount(x){if(!Array.isArray(x))return 1;return x.reduce((sum,item)=>sum+generalizedAtomCount(item),0);}
function simulateGeneralizedList(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const data=Array.isArray(request.initial_state.data)?request.initial_state.data:["a",["b","c"],["d",["e"]]];
  const op=request.operation;
  if(op==="tail"&&data.length===0){return api.makeRuntimeError(request,"求广义表表尾","EMPTY_LIST","空表没有表尾：广义表至少要有一个元素。",viewState("generalized_list",[row("list",[])]),"空广义表");}
  const steps=[makeStep(1,"init","广义表结构","按表头/表尾和子表层次观察广义表。",viewState("generalized_list",[row("list",data),row("meta",[],{operation:op})]))];
  if(op==="tail"){const tail=data.slice(1);steps.push(makeStep(2,"split","认出表头","第一个表元素是表头，去掉它剩下的就是表尾。",viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,current:0})]),[action("locate","指向第一个表元素",{target:0})]));steps.push(makeStep(3,"split","取表尾","去掉第一个表元素，剩余元素构成表尾。",viewState("generalized_list",[row("list",data),row("tail",tail),row("meta",[],{operation:op})]),[action("slice","去掉表头",{target:0})]));return makeTrace(request,"求广义表表尾","广义表",`tail=${JSON.stringify(tail)}`,steps);}
  if(op==="length"){const len=data.length;for(let i=0;i<len;i++)steps.push(makeStep(2+i,"count",`数第 ${i+1} 个表元素`,`${JSON.stringify(data[i])} 是一个表元素，当前计数 ${i+1}。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,current:i,count:i+1})]),[action("count","表元素计数加 1",{value:i+1})]));steps.push(makeStep(2+len,"count","统计完成",`最外层共有 ${len} 个表元素。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,length:len})])));return makeTrace(request,"求广义表长度","广义表",`length=${len}`,steps);}
  if(op==="depth"){const d=generalizedDepth(data);for(let i=0;i<data.length;i++){const inner=Array.isArray(data[i]);const di=inner?generalizedDepth(data[i]):0;steps.push(makeStep(2+i,"recurse",inner?"进入子表看深度":"原子不产生层次",inner?`子表 ${JSON.stringify(data[i])} 的深度是 ${di}。`:`${JSON.stringify(data[i])} 是原子，深度记 0。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,current:i,elementDepth:di})])));}steps.push(makeStep(2+data.length,"recurse","取最大深度加 1","最深层子表的深度加 1 得到整个表的深度。",viewState("generalized_list",[row("list",data),row("meta",[],{depth:d,operation:op})])));return makeTrace(request,"求广义表深度","广义表",`depth=${d}`,steps);}
  if(op==="atom_count"){const count=generalizedAtomCount(data);let seen=0;for(let i=0;i<data.length;i++){const add=Array.isArray(data[i])?generalizedAtomCount(data[i]):1;seen+=add;steps.push(makeStep(2+i,"recurse",Array.isArray(data[i])?"进入子表累计原子":"数到一个原子",Array.isArray(data[i])?`子表 ${JSON.stringify(data[i])} 里有 ${add} 个原子。`:`原子 ${JSON.stringify(data[i])} 计 1 个。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,current:i,atomCount:seen})]),[action("count","累计原子个数",{value:seen})]));}steps.push(makeStep(2+data.length,"recurse","统计完成",`逐层累计共 ${count} 个原子。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,atomCount:count})])));return makeTrace(request,"统计广义表原子个数","广义表",`atoms=${count}`,steps);}
  {const copy=deepClone(data);for(let i=0;i<data.length;i++){copy[i]=deepClone(data[i]);steps.push(makeStep(2+i,"copy",`复制第 ${i+1} 个表元素`,`为 ${JSON.stringify(data[i])} 创建对应的新结点（原子照抄、子表递归）。`,viewState("generalized_list",[row("list",data),row("copy",deepClone(copy.slice(0,i+1))),row("meta",[],{operation:op,current:i})]),[action("copy","递归复制结点",{target:i})]));}steps.push(makeStep(2+data.length,"copy","复制完成","新表与原表结构完全一致。",viewState("generalized_list",[row("list",data),row("copy",copy),row("meta",[],{operation:op})])));return makeTrace(request,"复制广义表","原广义表","复制完成",steps);}
}

function normalizeTreeArray(value){const arr=Array.isArray(value)&&value.length?value:["A","B","C","D","E","F","G"];return arr.slice(0,31).map(v=>v===undefined?null:v);}
function treeChildren(arr,i){const l=2*i+1,r=2*i+2;return [l<arr.length&&arr[l]!==null?l:-1,r<arr.length&&arr[r]!==null?r:-1];}
function treeView(arr,visited=[],current=-1,extra={}){const nodes=arr.map((v,i)=>v===null?null:{id:i,label:String(v),index:i}).filter(Boolean);const edges=[];nodes.forEach(n=>{const [l,r]=treeChildren(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);});return viewState("tree",[row("tree",[],{nodes,edges}),row("visited",visited.map(i=>arr[i])),row("meta",[],{currentValue:current>=0?arr[current]:null,currentIndex:current,...extra})]);}
function traversalOrder(arr,mode){const out=[];function rec(i){if(i<0||i>=arr.length||arr[i]===null)return;const[l,r]=treeChildren(arr,i);if(mode==="pre")out.push(i);rec(l);if(mode==="in")out.push(i);rec(r);if(mode==="post")out.push(i);}if(mode==="level"){const q=[0];while(q.length){const i=q.shift();if(i<0||i>=arr.length||arr[i]===null)continue;out.push(i);const[l,r]=treeChildren(arr,i);if(l>=0)q.push(l);if(r>=0)q.push(r);}}else rec(0);return out;}
/* 线索二叉树：线索画成虚线（树面板的 `threads`），`ltag`/`rtag` 落在结点上——
   空孩子指针改指前驱/后继，正是线索树区别于普通二叉树的那两个特征位，
   所以它们必须出现在画面上，而不是只写在旁白里。 */
function threadAll(arr){const threads=[],tags={};let pre=-1;for(const idx of traversalOrder(arr,"in")){if(treeChildren(arr,idx)[0]<0){if(pre>=0)threads.push([idx,pre,"L"]);tags[idx]={...(tags[idx]||{}),ltag:1};}if(pre>=0&&treeChildren(arr,pre)[1]<0){threads.push([pre,idx,"R"]);tags[pre]={...(tags[pre]||{}),rtag:1};}pre=idx;}return {threads,tags};}
function threadView(arr,visited,current,threads,tags,extra={}){
  const nodes=arr.map((v,i)=>v===null?null:{id:i,label:String(v),index:i,...(tags[i]||{})}).filter(Boolean);
  const edges=[];
  for(const n of nodes){const[l,r]=treeChildren(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);}
  const treeRow=row("tree",[],{nodes,edges});
  treeRow.threads=threads.map((t)=>[t[0],t[1],t[2]]);
  const rows=[treeRow];
  if(visited.length)rows.push(row("visited",visited.map((i)=>arr[i])));
  rows.push(row("meta",[],{currentValue:current>=0?arr[current]:null,currentIndex:current,...extra}));
  return viewState("tree",rows);
}

/* 树面板 + 几块辅助行（栈/队列/已访问）：非递归遍历与层序遍历要把"容器里现在有什么"画出来。 */
function treeViewExtra(arr,visited,current,rows,extra={}){
  const nodes=arr.map((v,i)=>v===null?null:{id:i,label:String(v),index:i}).filter(Boolean);
  const edges=[];
  for(const n of nodes){const[l,r]=treeChildren(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);}
  const all=[row("tree",[],{nodes,edges})];
  if(visited.length)all.push(row("visited",visited.map((i)=>arr[i])));
  all.push(...rows);
  all.push(row("meta",[],{currentValue:current>=0?arr[current]:null,currentIndex:current,...extra}));
  return viewState("tree",all);
}

function simulateTree(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const arr=normalizeTreeArray(request.initial_state.data);const op=request.operation;
  if(op==="build"){
    /* 按层次序列建树：`s = malloc(); s->data = 'B'` 一行、接到父结点上再一行。 */
    const partial=Array(arr.length).fill(null);
    let sid=1;const steps=[makeStep(sid++,"init","空二叉树",`按层次序列 ${arr.map((x)=>x===null?"#":x).join("")} 逐个建立非空结点。`,treeView(partial,[],-1,{operation:op}))];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"build",code,code,treeView(partial,[],current,{operation:op,...extra}),act?[act]:[]));
    for(let i=0;i<arr.length;i++){
      if(arr[i]===null)continue;
      const label=String(arr[i]);
      step(`s = malloc(); s->data = '${label}'`,-1,{s:label,i},action("allocate","申请一个结点",{value:label}),true);
      partial[i]=arr[i];
      step(i===0?"root = s":`p->${i%2?"rchild":"lchild"} = s`,i,{i,parent:i?Math.floor((i-1)/2):null},action("link",i===0?"作为根结点":"接到父结点上",{target:i,value:label}));
    }
    return makeTrace(request,"建立二叉树","层次序列",`结点数=${arr.filter((x)=>x!==null).length}`,steps);
  }
  // 核心契约的 tree visit/highlight（params.node 为层次下标）：单步定位并高亮一个结点。
  if(op==="visit"||op==="highlight"){
    const rawNode=request.params.node;
    const idx=intParam(request.params,"node",0,0,arr.length-1);
    if(arr[idx]===null)throw new SimulationInputError("EMPTY_NODE",`下标 ${idx} 处是空结点，无法访问（非空结点：${arr.map((x,i)=>x===null?null:i).filter(x=>x!==null).map(i=>`${i}:${arr[i]}`).join("、")}）`,"node");
    return makeTrace(request,op==="visit"?"访问指定结点":"高亮指定结点",`node=${rawNode===undefined?idx:rawNode}`,`访问 ${arr[idx]}`,[
      makeStep(1,"init","二叉树初始状态","观察当前树结构。",treeView(arr,[],-1,{operation:op})),
      makeStep(2,op,`${op==="visit"?"访问":"高亮"}结点 ${arr[idx]}`,`定位到层次下标 ${idx}。`,treeView(arr,[idx],idx,{operation:op}),[action("visit",op==="visit"?"访问结点":"高亮结点",{value:arr[idx]})])
    ]);
  }
  let order=[];
  if(op==="preorder")order=traversalOrder(arr,"pre");else if(["inorder","inorder_stack","thread_inorder","thread_predecessor","thread_successor"].includes(op))order=traversalOrder(arr,"in");else if(op==="postorder"||op==="postorder_stack")order=traversalOrder(arr,"post");else order=traversalOrder(arr,"level");
  const visited=[];let sid=1;const steps=[makeStep(sid++,"init","二叉树初始状态","准备按教材规定的次序访问结点。",treeView(arr,visited,-1,{operation:op}))];
  if(op==="inorder_stack"){
    /* 非递归中序：`s.push(p)` 一行、`p = p->lchild` 一行、`p = s.pop()` 一行、`p = p->rchild` 一行。 */
    const stack=[];let cur=0;const visited=[];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"push",code,code,treeViewExtra(arr,visited,current,[row("stack",stack.map((i)=>arr[i]))],{operation:op,...extra}),act?[act]:[]));
    step("p = root",0,{s:""},null,true);
    while(cur>=0||stack.length){
      const down=cur>=0&&arr[cur]!==null;
      step(`while (p != NULL) → ${down}`,cur,{s:stack.length?stack.map((i)=>arr[i]).join(","):"(空)"},null,true);
      if(down){
        stack.push(cur);
        step("s.push(p)",cur,{s:stack.map((i)=>arr[i]).join(",")},action("push",`${arr[cur]} 进栈`,{value:arr[cur]}),true);
        cur=treeChildren(arr,cur)[0];
        step("p = p->lchild",cur,{s:stack.length?stack.map((i)=>arr[i]).join(","):"(空)",p:cur>=0?arr[cur]:"NULL"},null,true);
        continue;
      }
      const out=stack.pop();visited.push(out);
      cur=treeChildren(arr,out)[1];
      step("p = s.pop(); visit(p)",out,{s:stack.map((i)=>arr[i]).join(","),visit:arr[out]},action("visit",`退栈并访问 ${arr[out]}`,{value:arr[out]}));
      step("p = p->rchild",cur,{s:stack.length?stack.map((i)=>arr[i]).join(","):"(空)",p:cur>=0?arr[cur]:"NULL"},null,true);
    }
    return makeTrace(request,"非递归中序遍历","二叉树",`访问序列=${visited.map((i)=>arr[i]).join(" ")}`,steps);
  } else if(op==="postorder_stack"){
    /* 非递归后序（结点带"已展开"标志）：第一次退栈把它和两个孩子压回去，第二次退栈才访问。 */
    const stack=[[0,false]];const visited=[];
    const label=()=>stack.map((pair)=>`${arr[pair[0]]}${pair[1]?"*":""}`).join(",");
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"push",code,code,treeViewExtra(arr,visited,current,[row("stack",stack.map((pair)=>`${arr[pair[0]]}${pair[1]?"*":""}`))],{operation:op,...extra}),act?[act]:[]));
    while(stack.length){
      const [idx,expanded]=stack.pop();
      if(idx<0||idx>=arr.length||arr[idx]===null){
        step("if (p == NULL) continue",-1,{s:label()},null,true);
        continue;
      }
      if(expanded){
        visited.push(idx);
        step("visit(p)",idx,{s:label(),visit:arr[idx]},action("visit",`第二次退栈，访问 ${arr[idx]}`,{value:arr[idx]}));
        continue;
      }
      step("if (p 已展开) → false",idx,{s:label()},null,true);
      const [l,r]=treeChildren(arr,idx);
      stack.push([idx,true]);
      if(r>=0)stack.push([r,false]);
      if(l>=0)stack.push([l,false]);
      step("s.push(p, 已展开); s.push(p->rchild); s.push(p->lchild)",idx,{s:label(),pushed:arr[idx]},action("push",`${arr[idx]} 与两个孩子重新入栈`,{value:arr[idx]}),true);
    }
    return makeTrace(request,"非递归后序遍历","二叉树",`访问序列=${visited.map((i)=>arr[i]).join(" ")}`,steps);
  } else if(op==="levelorder"){
    /* 层序：队列进出一个顶点一行，它的两个孩子入队各一行。 */
    const q=[0];const visited=[];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"queue",code,code,treeViewExtra(arr,visited,current,[row("queue",q.map((i)=>arr[i]))],{operation:op,...extra}),act?[act]:[]));
    step("q.push(root)",0,{q:arr[0]},action("push",`根 ${arr[0]} 入队`,{value:arr[0]}),true);
    while(q.length){
      const v=q.shift();visited.push(v);
      step("v = q.pop(); visit(v)",v,{q:q.map((i)=>arr[i]).join(",")},action("visit",`访问 ${arr[v]}`,{value:arr[v]}));
      const [l,r]=treeChildren(arr,v);
      if(l>=0){q.push(l);step("q.push(v->lchild)",v,{q:q.map((i)=>arr[i]).join(","),pushed:arr[l]},action("push",`左孩子 ${arr[l]} 入队`,{value:arr[l]}),true);}
      if(r>=0){q.push(r);step("q.push(v->rchild)",v,{q:q.map((i)=>arr[i]).join(","),pushed:arr[r]},action("push",`右孩子 ${arr[r]} 入队`,{value:arr[r]}),true);}
    }
    step("while (!q.empty()) → false",-1,{q:"(空)"});
    return makeTrace(request,"层序遍历","二叉树",`访问序列=${visited.map((i)=>arr[i]).join(" ")}`,steps);
} else if(op==="thread_inorder"){
    /* 中序线索化：`if (p->lchild == NULL)` → `p->lchild = pre; p->ltag = 1` →
       `if (pre && pre->rchild == NULL)` → `pre->rchild = p; pre->rtag = 1` → `pre = p`，**一行一帧**。
       空孩子指针改成前驱/后继线索，画面上就多一根虚线（指不出去的就是一个吊在结点下方的 NULL 线头）。 */
    const threads=[],tags={};let pre=-1;
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"thread",code,code,threadView(arr,visited,current,threads,tags,{operation:op,...extra}),act?[act]:[]));
    for(const idx of order){
      visited.push(idx);
      const noLeft=treeChildren(arr,idx)[0]<0;
      step(`if (p->lchild == NULL) → ${noLeft}`,idx,null,null,true);
      if(noLeft){
        if(pre>=0)threads.push([idx,pre,"L"]);
        tags[idx]={...(tags[idx]||{}),ltag:1};
        step("p->lchild = pre; p->ltag = 1",idx,{},action("link","左指针改作前驱线索",{value:pre>=0?arr[pre]:null}));
      }
      const preNoRight=pre>=0&&treeChildren(arr,pre)[1]<0;
      step(`if (pre && pre->rchild == NULL) → ${preNoRight}`,pre>=0?pre:idx,{pre:pre>=0?arr[pre]:"NULL"},null,true);
      if(preNoRight){
        threads.push([pre,idx,"R"]);
        tags[pre]={...(tags[pre]||{}),rtag:1};
        step("pre->rchild = p; pre->rtag = 1",pre,{pre:arr[pre]},action("link","前驱的右指针改作后继线索",{value:arr[idx]}));
      }
      step("pre = p",idx,{pre:arr[idx]},null,true);
      pre=idx;
    }
    return makeTrace(request,"中序线索化","二叉树",`线索 ${threads.length} 条`,steps);
  } else if(op==="thread_predecessor"||op==="thread_successor"){
    /* 求前驱/后继：**先看 tag**——标记为线索就直接沿线索走一步；不是线索才走子树，
       找"左子树的最右"（前驱）或"右子树的最左"（后继）。 */
    const {threads,tags}=threadAll(arr);
    const target=String(request.params.target??arr[order[Math.floor(order.length/2)]]);
    const pos=order.findIndex(i=>String(arr[i])===target);
    if(pos<0)return api.makeRuntimeError(request,"中序线索树求前驱/后继","TARGET_NOT_FOUND",`目标 ${target} 不在这棵二叉树中（结点：${arr.filter(x=>x!==null).join("、")}），请检查 target 参数。`,threadView(arr,[],-1,threads,tags,{operation:op,target}),`target=${target}`);
    const idx=order[pos];
    const successor=op==="thread_successor";
    const tagName=successor?"rtag":"ltag";
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"thread",code,code,threadView(arr,[],current,threads,tags,{operation:op,target,...extra}),act?[act]:[]));
    /* 起点就是**已经线索化**的那棵树：线索本来就是这个结构的一部分。 */
    steps[0]=makeStep(1,"init","线索二叉树",`${successor?"求后继":"求前驱"}：先看 ${tagName}，是线索就直接沿它走。`,threadView(arr,[],-1,threads,tags,{operation:op,target}));
    step(`p = ${target}`,idx,null,null,true);
    const threaded=Number((tags[idx]||{})[tagName])===1;
    step(`if (p->${tagName} == 1) → ${threaded}`,idx,{[tagName]:threaded?1:0},null,true);
    if(threaded){
      /* 那根指针就是线索：一步走到前驱/后继。 */
      const t=threads.find(x=>x[0]===idx&&x[2]===(successor?"R":"L"));
      const answer=t?t[1]:-1;
      step(`q = p->${successor?"rchild":"lchild"}`,answer,{q:answer>=0?arr[answer]:"NULL"},action("move",`沿${successor?"后继":"前驱"}线索直接走到 ${answer>=0?arr[answer]:"NULL"}`,{value:answer>=0?arr[answer]:null}));
      step("return q",idx,{q:answer>=0?arr[answer]:"NULL"});
      return makeTrace(request,successor?"中序线索树求后继":"中序线索树求前驱",`target=${target}`,answer>=0?String(arr[answer]):"NULL",steps);
    }
    const first=treeChildren(arr,idx)[successor?1:0];
    step(`q = p->${successor?"rchild":"lchild"}`,first,{},action("move","不是线索，先走进子树",{value:first>=0?arr[first]:null}),true);
    if(first<0){
      step("if (q == NULL) return NULL",idx,{q:"NULL"});
      return makeTrace(request,successor?"中序线索树求后继":"中序线索树求前驱",`target=${target}`,"NULL",steps);
    }
    let q=first;
    /* 后继：一路**向左**走到底；前驱：一路**向右**走到底。走到 tag=1 就停——那一步就是答案。 */
    for(;;){
      const stop=Number((tags[q]||{})[tagName])===1;
      step(`if (q->${tagName} == 0) → ${!stop}`,q,{[tagName]:stop?1:0},null,true);
      if(stop)break;
      const gone=treeChildren(arr,q)[successor?0:1];
      step(`q = q->${successor?"lchild":"rchild"}`,gone,{},action("move",`沿${successor?"左":"右"}链走到 ${gone>=0?arr[gone]:"NULL"}`,{value:gone>=0?arr[gone]:null}),true);
      if(gone<0)break;
      q=gone;
    }
    step("return q",idx,{q:arr[q]});
    return makeTrace(request,successor?"中序线索树求后继":"中序线索树求前驱",`target=${target}`,String(arr[q]),steps);
  } else {
    /* 递归遍历的"一行"：`visit(p)` 一格，**再走到下一个结点一格**。
       下一个结点是当前结点的左/右孩子，那一行就是 `p = p->left` / `p = p->right`；
       否则说明这一支走完了要**回溯**（`return`），此时 p 停在"下一个结点的父亲"上——
       两帧的高亮因此总是不同的，不会出现"点一下什么都没动"。 */
    for(let k=0;k<order.length;k++){
      const idx=order[k];visited.push(idx);
      steps.push(makeStep(sid++,"assign","visit(p)","visit(p)",treeView(arr,visited,idx,{operation:op}),[action("visit","按遍历次序访问",{value:arr[idx]})]));
      const next=k+1<order.length?order[k+1]:-1;
      if(next<0)continue;
      const l=2*idx+1,r=2*idx+2;
      const isChild=next===l||next===r;
      const code=next===l?"p = p->left":next===r?"p = p->right":"return";
      const at=isChild?next:Math.floor((next-1)/2);
      steps.push(makeStep(sid++,"line",code,code,treeView(arr,visited,at,{operation:op}),[]));
    }
  }
  return makeTrace(request,{preorder:"先序遍历",inorder:"中序遍历",postorder:"后序遍历",levelorder:"层序遍历",inorder_stack:"非递归中序遍历",postorder_stack:"非递归后序遍历",thread_inorder:"中序线索化"}[op]||"二叉树遍历","二叉树",`访问序列=${order.map(i=>arr[i]).join(" ")}`,steps);
}

function simulateHuffman(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const weights=numberArray(request.params.weights??request.initial_state.data,[2,3,4,7]);
  if(weights.length===0) throw new SimulationInputError("EMPTY_WEIGHTS","权值列表为空：哈夫曼树至少需要一个权值","weights");
  if(weights.length>12) throw new SimulationInputError("INPUT_TOO_LARGE",`哈夫曼动画最多演示 12 个权值，当前 ${weights.length} 个`,"weights");
  if(Array.isArray(request.params.symbols)&&request.params.symbols.length!==weights.length) throw new SimulationInputError("SYMBOL_MISMATCH",`symbols 数量（${request.params.symbols.length}）必须与 weights 数量（${weights.length}）一致`,"symbols");
  const symbols=Array.isArray(request.params.symbols)&&request.params.symbols.length===weights.length?request.params.symbols.map(String):weights.map((_,i)=>String.fromCharCode(65+i));
  let nodes=weights.map((w,i)=>({id:i,label:symbols[i],weight:w,left:null,right:null,parent:null}));
  let active=nodes.map(n=>n.id),nextId=nodes.length,sid=1;
  const find=(id)=>nodes.find((n)=>n.id===id);
  const treeRow=()=>row("tree",[],{nodes:deepClone(nodes),edges:nodes.flatMap((n)=>[[n.id,n.left],[n.id,n.right]].filter((e)=>e[1]!==null))});
  const base=(extra={})=>viewState("huffman",[treeRow(),row("active",active.map((id)=>find(id).weight)),row("meta",[],{operation:request.operation,...extra})]);
  const steps=[makeStep(sid++,"init","权值集合",`每个权值先看成一棵只有根结点的树：${weights.map((w,i)=>`${symbols[i]}=${w}`).join("、")}。`,base())];
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"merge",code,code,base(extra),act?[act]:[]));
  /* 合并：`a = 最小的树` → `b = 次小的树` → `s = malloc(); s->weight = …` → `s->left = a; s->right = b` → 放回森林。
     取走的两棵、新结点、新边、森林的变化，各报一帧。 */
  while(active.length>1){
    active.sort((a,b)=>find(a).weight-find(b).weight);
    const a=active.shift(),wa=find(a).weight;
    snap(`a = 最小的一棵（${find(a).label} = ${wa}）`,{current:String(a),a:wa},action("select","取权值最小的树",{value:wa}),true);
    const b=active.shift(),wb=find(b).weight;
    snap(`b = 次小的一棵（${find(b).label} = ${wb}）`,{current:String(b),a:wa,b:wb},action("select","取次小的树",{value:wb}),true);
    const parent={id:nextId++,label:String(wa+wb),weight:wa+wb,left:null,right:null,parent:null};
    find(a).parent=parent.id;find(b).parent=parent.id;
    nodes.push(parent);
    snap(`s = malloc(); s->weight = ${wa} + ${wb} = ${wa+wb}`,{current:String(parent.id),sum:wa+wb},action("merge","新建父结点",{value:wa+wb}));
    parent.left=a;parent.right=b;
    snap("s->left = a; s->right = b",{current:String(parent.id),sum:wa+wb},action("link","两棵最小的树挂到 s 下面",{value:wa+wb}),true);
    active.push(parent.id);
    snap("把 s 放回森林",{current:String(parent.id),n:active.length},action("insert","新树放回森林",{value:wa+wb}),true);
  }
  if(request.operation!=="encode"){
    const root=active[0];
    snap("return root",{current:String(root),root:find(root).weight},action("visit",`森林里只剩一棵树，根权值 ${find(root).weight}`,{value:find(root).weight}));
    return makeTrace(request,"构造哈夫曼树","权值集合",`根权值=${find(root).weight}`,steps);
  }
  /* encode：从根往下递归，`左 0 右 1`；每到一个叶子就定下一个编码。 */
  const root=active[0];
  const codes=[];
  const at=(extra={},withCodes=true)=>viewState("huffman",[treeRow(),...(withCodes&&codes.length?[row("codes",codes.map((c)=>({label:c.label,value:c.value})))]:[]),row("meta",[],{operation:request.operation,...extra})]);
  const enc=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"encode",code,code,at(extra),act?[act]:[]));
  steps[0]=makeStep(1,"init","哈夫曼树",`树建好了：从根往下走，向左收 0、向右收 1。`,at());
  const visit=(id,prefix,parentId)=>{
    const node=find(id);
    const leaf=node.left===null&&node.right===null;
    enc(`if (p 是叶子) → ${leaf}`,{current:String(id),code:prefix||'""'},null,true);
    if(leaf){
      codes.push({label:node.label,value:prefix||"0"});
      enc(`codes[${node.label}] = "${prefix||"0"}"`,{current:String(id),code:prefix||"0"},action("visit",`${node.label} 的编码是 ${prefix||"0"}`,{value:prefix||"0"}));
      if(parentId!==null)enc("return",{current:String(parentId),code:prefix||"0"},null,true);
      return;
    }
    enc(`Encode(p->left, code + "0")`,{current:String(node.left),code:prefix+"0",call:/left/},action("move","向左走一步（收 0）",{value:prefix+"0"}),true);
    visit(node.left,prefix+"0",id);
    enc(`Encode(p->right, code + "1")`,{current:String(node.right),code:prefix+"1",call:/right/},action("move","向右走一步（收 1）",{value:prefix+"1"}),true);
    visit(node.right,prefix+"1",id);
    if(parentId!==null)enc("return",{current:String(parentId),code:prefix||'""'},null,true);
  };
  visit(root,"",null);
  enc("return codes",{current:String(root),root:find(root).weight},action("visit",`得到 ${codes.length} 个编码`,{value:codes.length}));
  const map={};for(const c of codes)map[c.label]=c.value;
  return makeTrace(request,"哈夫曼编码","权值集合",JSON.stringify(map),steps);
}

function simulateUnionFind(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  // 用户显式传入的 parent 优先（注册表明明声明了这个参数，此前却被静默替换成 size 个单点集）。
  // normalizeParentArray 负责结构校验：整数、无自指、无环——保证 find 一定能走到根，杜绝死循环。
  // 直连 /simulate 的调用方可能把 parent 放在 params 或 initial_state.data，两处都要认。
  const provided=normalizeParentArray(request.params.parent)||normalizeParentArray(request.initial_state.data);
  let parent,n;
  if(provided){parent=provided;n=parent.length;}
  else{n=intParam(request.params,"size",6,2,20);parent=Array(n).fill(-1);}
  let sid=2;
  const at=(extra={})=>viewState("union_find",[row("parent",parent),row("meta",[],{operation:request.operation,...extra})]);
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":request.operation,code,code,at(extra),act?[act]:[]));
  const steps=[makeStep(1,"init","parent[]",`parent=[${parent.join(",")}]（负值 = 根，其绝对值为集合大小）。`,at())];
  /* 沿 parent 一路走到根：条件一行、上移一行，走到的每一步都看得见。 */
  const walk=(name,start)=>{
    snap(`${name} = ${start}`,{current:start,[name]:start},action("assign",`先假定 ${start} 自己是根`,{value:start}),true);
    let cur=start;
    for(;;){
      const up=parent[cur]>=0;
      snap(`while (parent[${name}] >= 0) → ${up}`,{current:cur,[name]:cur,[`parent[${name}]`]:parent[cur]},null,true);
      if(!up)break;
      const next=parent[cur];
      snap(`${name} = parent[${name}]`,{current:next,[name]:next},action("move",`${name} 上移到 ${next}`,{from:cur,to:next}),true);
      cur=next;
    }
    return cur;
  };
  if(request.operation==="find"){
    const x=intParam(request.params,"element",Math.min(2,Math.max(0,n-1)),0,n-1);
    const root=walk("x",x);
    snap("return x",{current:root,x:root,root},action("visit","x 就是代表元",{value:root}));
    return makeTrace(request,"并查集查找",`element=${x}`,`root=${root}`,steps);
  }
  const a=intParam(request.params,"a",0,0,n-1),b=intParam(request.params,"b",Math.min(1,n-1),0,n-1);
  const ra=walk("ra",a),rb=walk("rb",b);
  const same=ra===rb;
  snap(`if (ra == rb) → ${same}`,{current:ra,ra,rb},null,true);
  if(same){
    snap("return 0",{current:ra,ra,rb,"return":0},action("skip","两元素已在同一集合，parent 不变",{value:0}));
    return makeTrace(request,"并查集合并",`a=${a},b=${b}`,"已在同一集合，parent 未改变",steps);
  }
  /* 两棵树都是负数：`parent[ra] > parent[rb]` 的意思是"ra 那棵更小"，把小的挂到大的上。 */
  const hookBToA=parent[ra]>parent[rb];
  snap(`if (parent[ra] > parent[rb]) → ${hookBToA}`,{current:ra,ra,rb,"parent[ra]":parent[ra],"parent[rb]":parent[rb]},null,true);
  let line;
  if(hookBToA){parent[rb]+=parent[ra];parent[ra]=rb;line="parent[rb] += parent[ra]; parent[ra] = rb";}
  else{parent[ra]+=parent[rb];parent[rb]=ra;line="parent[ra] += parent[rb]; parent[rb] = ra";}
  snap(line,{current:ra,ra,rb},action("link",`${hookBToA?"ra（较小的树）挂到 rb":"rb（较小的树）挂到 ra"}`,{from:hookBToA?ra:rb,to:hookBToA?rb:ra}));
  snap("return 1",{current:ra,ra,rb,"return":1});
  return makeTrace(request,"并查集合并",`a=${a},b=${b}`,`parent=[${parent.join(",")}]`,steps);
}

function graphInput(request,defaults={}){return normalizeGraphSpec(request,defaults);}
function graphRows(g,extra={}){return [row("graph",[],{nodes:g.nodes,edges:g.edges}),row("meta",[],{directed:g.directed,...extra})];}
function adjMap(g,directed=false){const m=new Map(g.nodes.map(n=>[n,[]]));for(const [a,b,w]of g.edges){m.get(a)?.push([b,w]);if(!directed)m.get(b)?.push([a,w]);}return m;}
function simulateGraph(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const op=request.operation;
  // 教材约定：拓扑排序/关键路径/Dijkstra 天然按有向图处理；Prim/Kruskal 按无向图处理；
  // 其余遍历/路径/建图操作由 directed 参数决定（缺省 false）。directed 全程写入 meta，界面上可见。
  const directedByDefault=new Set(["topological_sort","critical_path","dijkstra","floyd"]);
  const undirectedByDefault=new Set(["prim","kruskal"]);
  const g=graphInput(request,{directed:directedByDefault.has(op)});
  const directed=g.directed;const adj=adjMap(g,directed);
  // 起点支持两种写法：教材习惯的 params.start（顶点标签）与核心契约的 params.node（顶点下标）。
  let start;
  if(request.params.start!==undefined){start=String(request.params.start);requireVertex(g,start,"起点 start");}
  else if(request.params.node!==undefined){
    const ni=Number(request.params.node);
    if(!Number.isInteger(ni)||ni<0||ni>=g.nodes.length)throw new SimulationInputError("PARAM_OUT_OF_RANGE",`起点下标 node=${JSON.stringify(request.params.node)} 超出顶点范围 [0, ${g.nodes.length-1}]`,"node");
    start=g.nodes[ni];
  }
  else start=g.nodes[0];
  if(op==="path_search"){const target=String(request.params.target??g.nodes[g.nodes.length-1]);requireVertex(g,target,"终点 target");}
  if(op==="dijkstra"){for(const [a,b,w] of g.edges){if(w<0) throw new SimulationInputError("NEGATIVE_WEIGHT",`Dijkstra 不支持负权边：边 ${a}→${b} 的权值为 ${w}。带负权时请改用 Floyd 等算法`,"edges");}}
  let sid=1;
  /* 代码级：每一步都是那行代码执行**之后**的图 —— `visited[v] = 1`、`q.push(邻居)`、
     `dist[v] = dist[u] + w`、`indegree[w] = indegree[w] - 1`……一行一帧。
     辅助结构（栈/队列/入度/距离/矩阵）各占一块面板，写得动的那一帧它就会变。 */
  const gRow=(extra={})=>row("graph",[],{nodes:g.nodes,edges:g.edges,...extra});
  /* `visited` 要放进 **meta** 行：渲染器的"已处理"淡显只从 meta 面板里读（放在图面板上没人读）。 */
  const at=(extraRows,extra={},visited=[])=>viewState("graph",[gRow(),...extraRows,row("meta",[],{operation:op,directed,...extra,...(visited&&visited.length?{visited}:{})})]);
  const steps=[makeStep(sid++,"init","图的初始状态",`顶点 ${g.nodes.join(", ")}；${g.edges.length} 条边；${directed?"有向图":"无向图"}${undirectedByDefault.has(op)?"（最小生成树按无向图处理）":""}。`,at([],{}))];
  const snap=(code,extraRows,extra,act,line,visited)=>steps.push(makeStep(sid++,line?"line":"visit",code,code,at(extraRows,extra,visited),act?[act]:[]));
  const graphRowWith=(extra={})=>gRow(extra);

  // 核心契约的 graph visit/highlight：单步定位并高亮起点顶点。
  if(op==="visit"||op==="highlight"){
    snap(`${op==="visit"?"visit":"highlight"}(${start})`,[],{current:start},action("visit",`${op==="visit"?"访问":"高亮"}顶点 ${start}`,{value:start}),false,[start]);
    return makeTrace(request,op==="visit"?"访问指定顶点":"高亮指定顶点",`start=${start}`,start,steps);
  }

  if(op==="dfs"){
    /* 递归 DFS：`visited[v] = 1` → 逐个邻居 `if (!visited[w])` → `DFS(w)`。 */
    const seen=new Set(),order=[];
    const walk=(v)=>{
      seen.add(v);order.push(v);
      snap(`visited[${v}] = 1`,[],{current:v,v},action("visit",`访问 ${v}`,{value:v}),false,[...order]);
      for(const [w] of adj.get(v)||[]){
        const skip=seen.has(w);
        snap(`if (!visited[${w}]) → ${!skip}`,[],{current:v,w,visited:skip?1:0},null,true,[...order]);
        if(skip)continue;
        snap(`DFS(${w})`,[],{current:w,w,from:v},action("move",`从 ${v} 深入 ${w}`,{from:v,to:w}),true,[...order]);
        walk(w);
      }
    };
    walk(start);
    snap("return",[],{current:start,done:"遍历结束"},null,false,[...order]);
    return makeTrace(request,"图的深度优先遍历",`start=${start}`,order.join("→"),steps);
  }

  if(op==="bfs"){
    /* 队列 BFS：`q.push(start); visited[start] = 1` → `v = q.pop()` → 邻居 `visited[w] = 1; q.push(w)`。 */
    const seen=new Set([start]),order=[];
    const q=[start];
    snap(`q.push(${start}); visited[${start}] = 1`,[row("queue",[...q])],{v:start,q:q.join(",")},action("push","起点入队并标记",{value:start}),false,[start]);
    while(q.length){
      snap("while (!q.empty()) → true",[row("queue",[...q])],{q:q.join(",")},null,true,[...order]);
      const v=q.shift();order.push(v);
      snap("v = q.pop()",[row("queue",[...q])],{current:v,v},action("visit",`访问 ${v}`,{value:v}),false,[...order]);
      for(const [w] of adj.get(v)||[]){
        const skip=seen.has(w);
        snap(`if (!visited[${w}]) → ${!skip}`,[row("queue",[...q])],{current:v,w,visited:skip?1:0},null,true,[...order]);
        if(skip)continue;
        seen.add(w);q.push(w);
        snap(`visited[${w}] = 1`,[row("queue",[...q])],{current:w,w},action("visit",`${w} 第一次被访问`,{value:w}),true,[...order]);
        snap(`q.push(${w})`,[row("queue",[...q])],{current:w,q:q.join(","),pushed:w},action("push",`${w} 入队`,{value:w}),true,[...order]);
      }
    }
    snap("while (!q.empty()) → false",[row("queue",[])],{q:"(空)"},null,false,[...order]);
    return makeTrace(request,"图的广度优先遍历",`start=${start}`,order.join("→"),steps);
  }

  if(op==="path_search"){
    /* 带父指针的 DFS：`parent[w] = v` 记下是怎么走到 w 的，找到 target 后顺着 parent 倒推路径。 */
    const target=String(request.params.target??g.nodes[g.nodes.length-1]);
    const seen=new Set(),parent={},order=[];
    let found=false;
    const walk=(v)=>{
      seen.add(v);order.push(v);
      snap(`visited[${v}] = 1`,[],{current:v,v},action("visit",`访问 ${v}`,{value:v}),false,[...order]);
      if(v===target){found=true;return;}
      for(const [w] of adj.get(v)||[]){
        const skip=seen.has(w);
        snap(`if (!visited[${w}]) → ${!skip}`,[],{current:v,w,visited:skip?1:0},null,true,[...order]);
        if(skip)continue;
        parent[w]=v;
        snap(`parent[${w}] = ${v}`,[],{current:v,w,parent:v},action("link",`记下 ${w} 是从 ${v} 来的`,{from:v,to:w}),true,[...order]);
        walk(w);
        if(found)return;
      }
    };
    walk(start);
    if(!found){
      snap("return NULL",[],{current:start,found:"NULL"},action("miss",`从 ${start} 出发到不了 ${target}`,{from:start,to:target}),false,[...order]);
      return makeTrace(request,"图中简单路径搜索",`start=${start},target=${target}`,"不可达",steps);
    }
    const path=[target];
    let x=target;
    while(x!==start){
      x=parent[x];
      if(x===undefined)break;
      path.push(x);
      snap(`p = parent[p]`, [], {current:x,p:x}, action("move",`沿 parent 退到 ${x}`,{value:x}), true, [...order]);
    }
    const route=path.reverse();
    snap("return 路径",[],{current:start,path:route.join("→")},action("visit",`路径 ${route.join("→")}`,{value:route.length}),false,[...order]);
    return makeTrace(request,"图中简单路径搜索",`start=${start},target=${target}`,route.join("→"),steps);
  }

  if(op==="prim"){
    /* Prim：每一轮扫一遍所有边，挑"一端已在 U 里、另一端不在"的最小边。 */
    const inU=new Set([start]);const mst=[];let total=0;
    const inURow=()=>row("inU",[...inU]);
    while(inU.size<g.nodes.length){
      let best=null;
      for(const e of g.edges){
        const [u,v,w]=e;
        const crosses=(inU.has(u)&&!inU.has(v))||(inU.has(v)&&!inU.has(u));
        const better=crosses&&(best===null||w<best[2]);
        snap(`if (一端在 U 内、另一端不在 且 w < min) → ${better}`,[inURow(),...(mst.length?[row("chosen",mst.map((m)=>`${m[0]}-${m[1]}`))]:[])],{edge:`${u}-${v}`,w,min:best?best[2]:null},null,true);
        if(better)best=e;
      }
      if(!best){
        const unreached=g.nodes.filter((x)=>!inU.has(x));
        snap("没找到跨边 → 图不连通",[inURow()],{left:unreached.join("、")},null,false);
        return makeTrace(request,"Prim 最小生成树",`start=${start}`,"图不连通，无法生成最小生成树",steps,[{code:"DISCONNECTED_GRAPH",message:`图不连通：只覆盖了 ${inU.size}/${g.nodes.length} 个顶点`,detail:unreached.join(","),recoverable:false}]);
      }
      const [a,b,w]=best;
      const add=inU.has(a)?b:a;
      inU.add(add);mst.push(best);total+=w;
      snap(`U = U ∪ {${add}}`,[inURow(),row("chosen",mst.map((m)=>`${m[0]}-${m[1]}`))],{current:add,edge:`${a}-${b}`,total},action("select",`把 ${add} 并进 U（边 ${a}-${b} 权 ${w}）`,{from:a,to:b,value:w}));
    }
    return makeTrace(request,"Prim 最小生成树",`start=${start}`,`总权值=${total}`,steps);
  }

  if(op==="kruskal"){
    /* Kruskal：先把边按权排序，然后一条条看它会不会成环（并查集判环）。 */
    const parent=Object.fromEntries(g.nodes.map((n)=>[n,n]));
    const find=(x)=>{let r=x;while(parent[r]!==r)r=parent[r];return r;};
    const mst=[];let total=0;
    const sorted=[...g.edges].sort((a,b)=>a[2]-b[2]);
    for(const e of sorted){
      const [a,b,w]=e,ra=find(a),rb=find(b);
      const ok=ra!==rb;
      snap(`if (Find(${a}) != Find(${b})) → ${ok}`,[row("chosen",mst.map((m)=>`${m[0]}-${m[1]}`))],{edge:`${a}-${b}`,w,ra,rb},null,true);
      if(ok){
        parent[rb]=ra;mst.push(e);total+=w;
        snap(`Union(${a}, ${b})：${a}-${b} 加入`,[row("chosen",mst.map((m)=>`${m[0]}-${m[1]}`))],{chain:`${a}-${b}`,total},action("select","不构成环，加入",{from:a,to:b,value:w}));
      }else{
        snap(`${a}-${b} 已经在同一棵树上 → 跳过`,[row("chosen",mst.map((m)=>`${m[0]}-${m[1]}`))],{chain:`${a}-${b}`,skipped:true},action("skip","会构成环，跳过",{from:a,to:b,value:w}));
      }
    }
    if(mst.length<g.nodes.length-1){
      snap("边数不足以连通 → 生成森林",[row("chosen",mst.map((m)=>`${m[0]}-${m[1]}`))],{forest:true},null,false);
      return makeTrace(request,"Kruskal 最小生成树","边按权排序",`图不连通：生成森林，总权值=${total}`,steps,[],[{message:"图不连通，无法得到最小生成树，所选边构成生成森林"}]);
    }
    return makeTrace(request,"Kruskal 最小生成树","边按权排序",`总权值=${total}`,steps);
  }

  if(op==="topological_sort"||op==="critical_path"){
    /* 拓扑排序：`indegree[w] = indegree[w] - 1` 一行、`if (indegree[w] == 0) q.push(w)` 一行。 */
    const indeg=Object.fromEntries(g.nodes.map((n)=>[n,0]));
    for(const [,b] of g.edges)indeg[b]++;
    const indegRow=()=>row("indegree",g.nodes.map((x)=>({vertex:x,value:indeg[x]})));
    const q=g.nodes.filter((n)=>indeg[n]===0);
    const topo=[];const ve=Object.fromEntries(g.nodes.map((n)=>[n,0]));
    while(q.length){
      const v=q.shift();topo.push(v);
      snap(`v = q.pop() → 输出 ${v}`,[indegRow(),row("topo",[...topo])],{current:v,indegree:indeg[v]},action("output",`输出 ${v}（入度已为 0）`,{value:v}));
      for(const [to,w] of adj.get(v)||[]){
        indeg[to]-=1;
        ve[to]=Math.max(ve[to],ve[v]+w);
        snap(`indegree[${to}] = indegree[${to}] - 1`,[indegRow(),row("topo",[...topo])],{current:to,indegree:indeg[to]},action("move",`删掉 ${v}→${to}，入度减一`,{from:v,to}),true);
        const zero=indeg[to]===0;
        snap(`if (indegree[${to}] == 0) → ${zero}`,[indegRow(),row("topo",[...topo])],{current:to,indegree:indeg[to],zero:zero?"入队":"继续"},null,true);
        if(zero)q.push(to);
      }
    }
    if(topo.length<g.nodes.length){
      const remaining=g.nodes.filter((x)=>!topo.includes(x));
      snap("剩余顶点入度都不为 0 → 有环",[indegRow(),row("topo",[...topo])],{current:null,left:remaining.join("、")},null,false);
      return makeTrace(request,"拓扑排序","AOV 网","存在回路，无法拓扑排序",steps,[{code:"CYCLE_DETECTED",message:`图中存在回路（卡住的顶点：${remaining.join("、")}）`,detail:remaining.join(","),recoverable:false}]);
    }
    if(op==="topological_sort")return makeTrace(request,"拓扑排序","AOV 网",topo.join("→"),steps);
    /* 关键路径：按拓扑序正推 ve（最早发生时间），再逆推 vl（最迟发生时间），ve == vl 的活动就是关键活动。 */
    const maxTime=Math.max(...Object.values(ve));
    const veRow=()=>row("ve",g.nodes.map((x)=>({vertex:x,value:ve[x]})));
    snap(`ve = 拓扑序正推（工期 ${maxTime}）`,[veRow(),row("topo",[...topo])],{maxTime},action("compute","最早发生时间正推完成",{value:maxTime}));
    const vl=Object.fromEntries(g.nodes.map((n)=>[n,maxTime]));
    const vlRow=()=>row("vl",g.nodes.map((x)=>({vertex:x,value:vl[x]})));
    for(let k=topo.length-1;k>=0;k--){
      const v=topo[k];
      for(const [to,w] of adj.get(v)||[]){
        if(vl[to]-w<vl[v]){
          vl[v]=vl[to]-w;
          snap(`vl[${v}] = min(vl[${v}], vl[${to}] - ${w})`,[vlRow(),veRow()],{current:v,vl:vl[v]},action("compute","最迟发生时间逆推",{value:vl[v]}),true);
        }
      }
    }
    const critical=[];
    for(const [a,b,w] of g.edges){
      const isCritical=ve[a]===vl[b]-w;
      snap(`if (ve[${a}] == vl[${b}] - ${w}) → ${isCritical}`,[vlRow(),veRow()],{edge:`${a}-${b}`,e:ve[a],l:vl[b]-w},null,true);
      if(isCritical){critical.push([a,b,w]);snap(`${a}→${b} 是关键活动`,[vlRow(),veRow(),...(critical.length?[row("critical",critical.map((c)=>`${c[0]}→${c[1]}`))]:[])],{edge:`${a}-${b}`,critical:critical.length},action("mark","关键活动",{from:a,to:b}),false);}
    }
    snap("return 关键路径",[vlRow(),veRow(),row("critical",critical.map((c)=>`${c[0]}→${c[1]}`))],{maxTime,count:critical.length},action("visit",`工期 ${maxTime}`,{value:maxTime}),false);
    return makeTrace(request,"关键路径","AOE 网",`工期=${maxTime}`,steps);
  }

  if(op==="dijkstra"){
    /* Dijkstra：`u = 未确定里 dist 最小的` → 对每条出边 `if (dist[u] + w < dist[v]) → dist[v] = dist[u] + w`。 */
    const dist=Object.fromEntries(g.nodes.map((n)=>[n,Infinity]));dist[start]=0;
    const done=new Set();const prev={};
    const distRow=()=>row("dist",g.nodes.map((x)=>({vertex:x,value:Number.isFinite(dist[x])?dist[x]:"∞"})));
    while(done.size<g.nodes.length){
      let u=null;let best=Infinity;
      for(const n of g.nodes)if(!done.has(n)&&dist[n]<best){best=dist[n];u=n;}
      if(u===null)break;
      snap(`u = 未确定中 dist 最小的（min = ${Number.isFinite(best)?best:"∞"}）`,[distRow()],{current:u,min:Number.isFinite(best)?best:"∞"},action("select",`确定 ${u} 的最短距离`,{value:best}),true);
      done.add(u);
      snap(`S = S ∪ {${u}}`,[distRow()],{current:u,settled:done.size},action("select","加入已确定集合",{value:u}));
      for(const [v,w] of adj.get(u)||[]){
        const via=dist[u]+w;
        const better=via<dist[v];
        snap(`if (dist[${u}] + ${w} < dist[${v}]) → ${better}`,[distRow()],{current:u,edge:`${u}-${v}`,dist_u:dist[u],dist_v:Number.isFinite(dist[v])?dist[v]:"∞"},null,true);
        if(!better)continue;
        dist[v]=via;prev[v]=u;
        snap(`dist[${v}] = dist[${u}] + ${w} = ${via}`,[distRow()],{current:v,relax:[u,v,w],dist_v:via},action("relax",`${u}→${v} 松弛到 ${via}`,{from:u,to:v,value:via}),true);
      }
    }
    snap("return dist[]",[distRow()],{start},action("visit","求各顶点最短距离完成",{value:dist[start]}));
    return makeTrace(request,"Dijkstra 单源最短路径",`start=${start}`,JSON.stringify(dist),steps);
  }

  /* Floyd：三层循环，每一对 (i,j) 都真的比较一次，能缩短就写进矩阵。 */
  const n=g.nodes.length;const idx=Object.fromEntries(g.nodes.map((x,i)=>[x,i]));
  const d=Array.from({length:n},(_,i)=>Array.from({length:n},(_,j)=>i===j?0:Infinity));
  for(const [a,b,w] of g.edges){d[idx[a]][idx[b]]=Math.min(d[idx[a]][idx[b]],w);if(!directed)d[idx[b]][idx[a]]=Math.min(d[idx[b]][idx[a]],w);}
  const cells=()=>d.map((r)=>r.map((x)=>Number.isFinite(x)?x:"∞"));
  for(let k=0;k<n;k++){
    snap(`k = ${k}：允许 ${g.nodes[k]} 作中间点`,[row("matrix",cells(),{focusIndex:k})],{current:g.nodes[k]},action("visit","允许中间顶点",{value:g.nodes[k]}),true);
    for(let i=0;i<n;i++)for(let j=0;j<n;j++){
      if(i===k||j===k||i===j)continue;
      const via=d[i][k]+d[k][j];
      const better=via<d[i][j];
      snap(`if (d[${i}][${k}] + d[${k}][${j}] < d[${i}][${j}]) → ${better}`,[row("matrix",cells(),{focusCell:[i,j]})],{current:g.nodes[k],pair:`${g.nodes[i]}→${g.nodes[j]}`},null,true);
      if(!better)continue;
      const before=d[i][j];d[i][j]=via;
      snap(`d[${i}][${j}] = ${via}`,[row("matrix",cells(),{focusCell:[i,j]})],{current:g.nodes[k],relax:[g.nodes[i],g.nodes[j]],before:Number.isFinite(before)?before:"∞"},action("relax","更新距离矩阵",{from:g.nodes[i],to:g.nodes[j],value:via}));
    }
  }
  snap("return d[][]",[row("matrix",cells())],{done:"各对顶点最短距离"},null,false);
  return makeTrace(request,"Floyd 各对顶点最短路径","初始距离矩阵","最终距离矩阵",steps);
}

function simulateSearch(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const arr=numberArray(request.initial_state.data,[7,13,18,24,31,42,55]);if(request.params.key!==undefined&&!Number.isFinite(Number(request.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");const key=Number(request.params.key??24);let sid=1;const steps=[makeStep(sid++,"init","准备查找",`查找关键字 ${key}。`,viewState("sequence",[row("array",arr),row("meta",[],{operation:request.operation,key})]))];if(request.operation==="sequential"){if(!arr.length){steps.push(makeStep(sid,"done","查找表为空","查找表中没有任何元素，顺序查找结束。",viewState("sequence",[row("array",[]),row("meta",[],{operation:request.operation,key})])));return makeTrace(request,"顺序查找",`key=${key}`,"未找到",steps);}for(let i=0;i<arr.length;i++){steps.push(makeStep(sid++,"compare","逐个比较",`${key} ${arr[i]===key?"=":"≠"} ${arr[i]}`,viewState("sequence",[row("array",arr),row("meta",[],{operation:request.operation,key,current:i})]),[action("compare","比较关键字",{target:i,value:arr[i]})]));if(arr[i]===key)return makeTrace(request,"顺序查找",`key=${key}`,`位置=${i+1}`,steps);}return makeTrace(request,"顺序查找",`key=${key}`,"未找到",steps);}if(request.operation==="binary"){if(!arr.length)return api.makeRuntimeError(request,"折半查找","EMPTY_TABLE","查找表为空：折半查找需要至少一个元素。",viewState("sequence",[row("array",[]),row("meta",[],{operation:request.operation,key})]),`key=${key}`);for(let i=1;i<arr.length;i++){if(arr[i-1]>arr[i])return api.makeRuntimeError(request,"折半查找","UNSORTED_INPUT",`折半查找要求有序表：a[${i-1}]=${arr[i-1]} > a[${i}]=${arr[i]}，当前输入不是非递减序列。请先排序，或改用顺序查找。`,viewState("sequence",[row("array",arr),row("meta",[],{operation:request.operation,key,violated:i})]),`key=${key}`);}const a=arr;let low=0,high=a.length-1;while(low<=high){const mid=Math.floor((low+high)/2);steps.push(makeStep(sid++,"compare","比较中间记录",`low=${low}, mid=${mid}, high=${high}，比较 ${key} 与 ${a[mid]}。`,viewState("sequence",[row("array",a),row("meta",[],{operation:request.operation,key,low,mid,high,current:mid})]),[action("compare","比较中间关键字",{target:mid,value:a[mid]})]));if(a[mid]===key)return makeTrace(request,"折半查找",`key=${key}`,`位置=${mid+1}`,steps);if(key<a[mid])high=mid-1;else low=mid+1;}return makeTrace(request,"折半查找",`key=${key}`,"未找到",steps);}if(!arr.length)return api.makeRuntimeError(request,"分块查找","EMPTY_TABLE","查找表为空：分块查找需要至少一个元素。",viewState("sequence",[row("array",[]),row("meta",[],{operation:request.operation,key})]),`key=${key}`);const blockSize=intParam(request.params,"blockSize",3,1,10);const blocks=[];for(let i=0;i<arr.length;i+=blockSize){const part=arr.slice(i,i+blockSize);blocks.push({start:i,end:i+part.length-1,max:Math.max(...part)});}for(let b=1;b<blocks.length;b++){const curMin=Math.min(...arr.slice(blocks[b].start,blocks[b].end+1));if(blocks[b-1].max>curMin)return api.makeRuntimeError(request,"分块查找","UNBLOCKED_ORDER",`分块查找要求“分块有序”：第 ${b} 块的最小值 ${curMin} 小于第 ${b} 块前一块的最大值 ${blocks[b-1].max}。请先排序或调整块划分。`,viewState("sequence",[row("array",arr),row("blocks",blocks),row("meta",[],{operation:request.operation,key,block:b})]),`key=${key}`);}let b=blocks.findIndex(x=>key<=x.max);steps.push(makeStep(sid++,"block","先查索引表",b>=0?`关键字应落在第 ${b+1} 块。`:"索引表中没有候选块。",viewState("sequence",[row("array",arr),row("blocks",blocks),row("meta",[],{operation:request.operation,key,block:b})])));if(b>=0)for(let i=blocks[b].start;i<=blocks[b].end;i++){steps.push(makeStep(sid++,"compare","块内顺序查找",`比较 ${key} 与 ${arr[i]}。`,viewState("sequence",[row("array",arr),row("blocks",blocks),row("meta",[],{operation:request.operation,key,block:b,current:i})])));if(arr[i]===key)return makeTrace(request,"分块查找",`key=${key}`,`位置=${i+1}`,steps);}return makeTrace(request,"分块查找",`key=${key}`,"未找到",steps);}

function bstBuild(values){let root=null;function insert(node,key){if(!node)return{key,left:null,right:null};if(key<node.key)node.left=insert(node.left,key);else if(key>node.key)node.right=insert(node.right,key);return node;}for(const k of values)root=insert(root,k);return root;}function bstRows(root,extra={}){const nodes=[],edges=[];let id=0;function walk(n,parent=null){if(!n)return null;const my=id++;nodes.push({id:my,label:String(n.key),key:n.key});if(parent!==null)edges.push([parent,my]);const l=walk(n.left,my),r=walk(n.right,my);return my;}walk(root);return [row("tree",[],{nodes,edges}),row("meta",[],extra)];}
function simulateBST(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const values=numberArray(request.initial_state.data,[45,24,53,12,28,90]);
  if(request.params.key!==undefined&&!Number.isFinite(Number(request.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");
  const key=Number(request.params.key??request.params.value??35);
  let root=bstBuild(values);let sid=1;
  /* 代码级：**比较一行、下沉一行**。一次比较报一帧（`if (p->key == key) → true/false`），
     转左/右子树再报一帧（`p = p->left`）——查找/插入/删除的走向在画面上就是这一对一对的箭头。 */
  const steps=[makeStep(sid++,"init","p = root","从根开始",viewState("tree",bstRows(root,{operation:request.operation,key})))];
  const treeStep=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"assign",code,code,viewState("tree",bstRows(root,{operation:request.operation,key,...extra})),act?[act]:[]));
  /** 走到 key 该在的位置：返回 {node, parent}。一路报"比较一行 + 下沉一行"。 */
  const walk=(stopOnHit)=>{
    let n=root,q=null;
    while(n){
      const hit=key===n.key;
      treeStep(`if (p->key == ${key}) → ${hit}`,{current:n.key},action("compare","比较关键字",{value:n.key}));
      if(hit&&stopOnHit)return {node:n,parent:q,hit:true};
      if(hit)return {node:n,parent:q,hit:true};
      const dir=key<n.key?"left":"right";
      const next=dir==="left"?n.left:n.right;
      treeStep(`p = p->${dir}`,{current:next?next.key:null},null,true);
      q=n;n=next;
    }
    return {node:null,parent:q,hit:false};
  };
  if(request.operation==="search"){
    const {node}=walk(true);
    return makeTrace(request,"二叉排序树查找",`key=${key}`,node?"查找成功":"未找到",steps);
  }
  if(request.operation==="insert"){
    const {node,parent}=walk(true);
    if(node){treeStep("return",{current:node.key},action("skip","关键字已存在，不插入",{value:key}));return makeTrace(request,"二叉排序树插入",`key=${key}`,"未插入：关键字已存在",steps);}
    const dir=parent===null?null:(key<parent.key?"left":"right");
    treeStep("s = malloc()",{current:parent?parent.key:null},action("allocate","申请新结点 s",{value:key}),true);
    function ins(n){if(!n)return{key,left:null,right:null};if(key<n.key)n.left=ins(n.left);else if(key>n.key)n.right=ins(n.right);return n;}
    root=ins(root);
    treeStep(parent===null?"root = s":`q->${dir} = s`,{current:key},action("link","把 s 接到空位置",{value:key}));
    return makeTrace(request,"二叉排序树插入",`key=${key}`,"插入完成",steps);
  }
  const {node,parent}=walk(true);
  if(!node){treeStep("p == NULL",{},action("miss","树里没有这个关键字",{value:key}));return makeTrace(request,"二叉排序树删除",`key=${key}`,"未找到关键字，未删除",steps);}
  const dir=parent===null?null:(key<parent.key?"left":"right");
  function del(n,k){if(!n)return null;if(k<n.key)n.left=del(n.left,k);else if(k>n.key)n.right=del(n.right,k);else if(!n.left)return n.right;else if(!n.right)return n.left;else{let t=n.right;while(t.left)t=t.left;n.key=t.key;n.right=del(n.right,t.key);}return n;}
  const child=node.left?"left":node.right?"right":null;
  if(child===null){
    root=del(root,key);
    treeStep(parent===null?"root = NULL":`q->${dir} = NULL`,{},action("delete","叶子结点直接摘掉",{value:key}));
  } else if(!(node.left&&node.right)){
    root=del(root,key);
    treeStep(parent===null?`root = p->${child}`:`q->${dir} = p->${child}`,{},action("delete","唯一的孩子顶替被删结点",{value:key}));
  } else {
    let t=node.right;while(t.left)t=t.left;
    treeStep("s = p->right; while (s->left) s = s->left",{current:t.key},null,true);
    root=del(root,key);
    treeStep("p->key = s->key; free(s)",{current:t.key},action("delete","用中序后继替代后删掉它",{value:key}));
  }
  return makeTrace(request,"二叉排序树删除",`key=${key}`,"删除完成",steps);
}

function h(n){return n?1+Math.max(h(n.left),h(n.right)):0;}
/* AVL 的树形行：结点 id 用**关键字本身**、边带 "L"/"R"——旋转后结点会换位置，只有 id 稳定才看得出"谁上去了"。
   这里**按当前真实存在的指针**画（不是从根遍历）：旋转中途会出现"两个父结点都指着同一个结点"
   的瞬间（`q->right = p` 之后、父结点还没改指之前），那一刻画面上就该是两根箭头——
   紧跟着的 `parent->left = q` 再把旧的那根撤掉。从根遍历会把整支摘掉的子树画没。 */
function avlRowsOf(nodes,extra={}){const list=[],edges=[];for(const n of nodes){list.push({id:String(n.key),label:String(n.key),key:n.key});if(n.left)edges.push([String(n.key),String(n.left.key),"L"]);if(n.right)edges.push([String(n.key),String(n.right.key),"R"]);}return [row("tree",[],{nodes:list,edges}),row("meta",[],extra)];}

function simulateAVL(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const values=numberArray(request.initial_state.data,[30,20,40,10]);
  const key=Number(request.params.key??request.params.value??5);
  if(!Number.isFinite(key))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");
  /* 建树（同时记下所有结点：画树要按"当前指针"画，不能只从根遍历）。 */
  const all=[];let root=null;
  for(const v of values){
    const node={key:v,left:null,right:null};
    if(all.some(x=>x.key===v))continue;
    all.push(node);
    if(root===null){root=node;continue;}
    for(let x=root;;){if(v<x.key){if(x.left===null){x.left=node;break;}x=x.left;}else if(v>x.key){if(x.right===null){x.right=node;break;}x=x.right;}else break;}
  }
  const tree={root};
  let sid=1;
  /* 代码级：**比较一行、下沉一行**找插入位置，插入后**回看平衡因子**，谁失衡就转谁——
     每一帧都是那行代码执行**之后**的树。`bf` 作为胶囊显示出来，平衡因子不再只是旁白。 */
  const at=(extra={})=>viewState("tree",avlRowsOf(all,{operation:"avl_insert",key,...extra}));
  const steps=[makeStep(sid++,"init","p = root","从根开始按 BST 规则找插入位置；插完再回头检查哪一步失衡。",at())];
  const snap=(phase,text,extra,act)=>steps.push(makeStep(sid++,phase,text,text,at(extra),act?[act]:[]));
  const line=(text,extra,act)=>snap("line",text,extra,act);
  const hold=(text,extra,act)=>snap("insert",text,extra,act);

  const path=[];let n=tree.root,parent=null;
  while(n){
    const hit=key===n.key;
    snap("assign",`if (p->key == ${key}) → ${hit}`,{current:String(n.key)},action("compare","比较关键字",{value:n.key}));
    if(hit){snap("insert","return",{current:String(n.key)},action("skip","关键字已存在，不插入",{value:key}));return makeTrace(request,"AVL 树插入与平衡调整",`key=${key}`,"未插入：关键字已存在",steps);}
    const dir=key<n.key?"left":"right";
    const next=dir==="left"?n.left:n.right;
    path.push({node:n,dir});
    parent=n;
    line(`p = p->${dir}`,{current:next?String(next.key):null});
    n=next;
  }
  line("s = malloc()",{current:parent?String(parent.key):null},action("allocate","申请新结点 s",{value:key}));
  const leaf={key,left:null,right:null};
  all.push(leaf);
  const side=parent===null?null:(key<parent.key?"left":"right");
  if(parent===null)tree.root=leaf;else parent[side]=leaf;
  hold(parent===null?"root = s":`q->${side} = s`,{current:String(key)},action("link","把 s 接到空位置",{value:key}));

  for(let i=path.length-1;i>=0;i--){
    const p=path[i].node;
    const bf=h(p.left)-h(p.right);
    const grand=i>0?path[i-1].node:null;
    const grandDir=i>0?path[i-1].dir:null;
    const relink=(newRoot)=>{if(grand===null)tree.root=newRoot;else grand[grandDir]=newRoot;};
    const parentLine=(name)=>grand===null?`root = ${name}`:`parent->${grandDir} = ${name}`;
    /* 沿插入路径**自下而上**逐层复核平衡因子：每一层都报一行，不失衡就继续往上。 */
    snap("assign","bf = h(p->left) - h(p->right)",{current:String(p.key),bf},action("check_condition",`结点 ${p.key} 的平衡因子是 ${bf}`,{value:bf}));
    if(Math.abs(bf)<=1)continue;
    if(bf>1){
      const q=p.left;
      line("q = p->left",{current:String(q.key),bf},action("move","q 指向 p 的左孩子",{value:q.key}));
      if(key<q.key){
        /* LL：对 p 做一次右旋 */
        p.left=q.right;
        hold("p->left = q->right",{current:String(p.key),bf},action("link","p 的左子树改挂 q 的右子树",{value:q.right?q.right.key:null}));
        q.right=p;
        hold("q->right = p",{current:String(q.key),bf},action("rotate","p 降为 q 的右孩子",{value:p.key}));
        relink(q);
        line(parentLine("q"),{current:String(q.key)},action("move","子树的新根是 q",{value:q.key}));
      }else{
        /* LR：先对 q 左旋，再对 p 右旋。挂回父结点在挂 q 之前——中途不会出现"一个结点两个父亲"。 */
        const r=q.right;
        line("r = q->right",{current:String(r.key),bf},action("move","r 指向 q 的右孩子",{value:r.key}));
        q.right=r.left;
        hold("q->right = r->left",{current:String(q.key),bf},action("link","q 的右子树改挂 r 的左子树",{value:r.left?r.left.key:null}));
        p.left=r;
        hold("p->left = r",{current:String(p.key),bf},action("link","p 的左孩子换成 r",{value:r.key}));
        r.left=q;
        hold("r->left = q",{current:String(q.key),bf},action("link","q 降为 r 的左孩子",{value:q.key}));
        p.left=r.right;
        hold("p->left = r->right",{current:String(p.key),bf},action("link","p 的左子树改挂 r 的右子树",{value:r.right?r.right.key:null}));
        r.right=p;
        hold("r->right = p",{current:String(p.key),bf},action("rotate","p 降为 r 的右孩子",{value:p.key}));
        relink(r);
        line(parentLine("r"),{current:String(r.key)},action("move","子树的新根是 r",{value:r.key}));
      }
    }else{
      const q=p.right;
      line("q = p->right",{current:String(q.key),bf},action("move","q 指向 p 的右孩子",{value:q.key}));
      if(key>q.key){
        /* RR：对 p 做一次左旋 */
        p.right=q.left;
        hold("p->right = q->left",{current:String(p.key),bf},action("link","p 的右子树改挂 q 的左子树",{value:q.left?q.left.key:null}));
        q.left=p;
        hold("q->left = p",{current:String(q.key),bf},action("rotate","p 降为 q 的左孩子",{value:p.key}));
        relink(q);
        line(parentLine("q"),{current:String(q.key)},action("move","子树的新根是 q",{value:q.key}));
      }else{
        /* RL：先对 q 右旋，再对 p 左旋 */
        const r=q.left;
        line("r = q->left",{current:String(r.key),bf},action("move","r 指向 q 的左孩子",{value:r.key}));
        q.left=r.right;
        hold("q->left = r->right",{current:String(q.key),bf},action("link","q 的左子树改挂 r 的右子树",{value:r.right?r.right.key:null}));
        p.right=r;
        hold("p->right = r",{current:String(p.key),bf},action("link","p 的右孩子换成 r",{value:r.key}));
        r.right=q;
        hold("r->right = q",{current:String(q.key),bf},action("link","q 降为 r 的右孩子",{value:q.key}));
        p.right=r.left;
        hold("p->right = r->left",{current:String(p.key),bf},action("link","p 的右子树改挂 r 的左子树",{value:r.left?r.left.key:null}));
        r.left=p;
        hold("r->left = p",{current:String(p.key),bf},action("rotate","p 降为 r 的左孩子",{value:p.key}));
        relink(r);
        line(parentLine("r"),{current:String(r.key)},action("move","子树的新根是 r",{value:r.key}));
      }
    }
    break;
  }
  return makeTrace(request,"AVL 树插入与平衡调整",`key=${key}`,"保持平衡",steps);
}

/* B 树：先按插入顺序建成**真正的** m 阶 B 树。
   （原来那个 simpleBTree 是"把关键字每 m-1 个切一刀"的示意形状：m=3 时根有 2 个关键字、
   孩子里却含分隔关键字本身，孩子区间与分隔符对不上。真正的 B 树在这里，查找/插入/删除才是对的。） */
/* B 树：先按插入顺序建成**真正的** m 阶 B 树。
   （原来那个 simpleBTree 是"把关键字每 m-1 个切一刀"的示意形状：m=3 时根有 2 个关键字、
   孩子里却含分隔关键字本身，孩子区间与分隔符对不上。真正的 B 树在这里，查找/插入/删除才是对的。） */
/* B 树：先按插入顺序建成**真正的** m 阶 B 树。
   （原来那个 simpleBTree 是"把关键字每 m-1 个切一刀"的示意形状：m=3 时根有 2 个关键字、
   孩子里却含分隔关键字本身，孩子区间与分隔符对不上。真正的 B 树在这里，查找/插入/删除才是对的。） */
function btreeNode(keys,children){return {keys:[...keys],children:children?[...children]:[]};}
function btreeSplitInPlace(parent,i,maxKeys){
  const child=parent.children[i];
  const mid=Math.floor(child.keys.length/2);
  const promote=child.keys[mid];
  const left=btreeNode(child.keys.slice(0,mid),child.children.slice(0,mid+1));
  const right=btreeNode(child.keys.slice(mid+1),child.children.slice(mid+1));
  parent.keys.splice(i,0,promote);
  parent.children.splice(i,1,left,right);
  return promote;
}
function btreeInsertKey(node,k,maxKeys){
  let i=0;while(i<node.keys.length&&k>node.keys[i])i++;
  if(node.keys[i]===k)return;
  if(!node.children.length){node.keys.splice(i,0,k);return;}
  btreeInsertKey(node.children[i],k,maxKeys);
  if(node.children[i].keys.length>maxKeys)btreeSplitInPlace(node,i,maxKeys);
}
function btreeBuildAll(keys,order){
  const maxKeys=order-1;
  let root=btreeNode([],[]);
  for(const k of keys){
    btreeInsertKey(root,k,maxKeys);
    /* 根也会满：每插一个都要看一眼，满了就把根劈成两个孩子、长高一层。 */
    if(root.keys.length>maxKeys){
      const holder=btreeNode([],[root]);
      btreeSplitInPlace(holder,0,maxKeys);
      root=holder;
    }
  }
  return root;
}
function btreeRows(tree,extra={}){const nodes=[],edges=[];let id=0;function walk(n,parent=null,depth=0){const my=id++;nodes.push({id:my,label:(n.keys||[]).join(" | "),keys:n.keys||[],depth});if(parent!==null)edges.push([parent,my]);for(const c of n.children||[])walk(c,my,depth+1);}walk(tree);return[row("tree",[],{nodes,edges,multiKey:true}),row("meta",[],extra)];}

function simulateBTree(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const keys=numberArray(request.initial_state.data,[10,20,30,40,50,60]);
  const key=Number(request.params.key??45);
  if(!Number.isFinite(key))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");
  const order=intParam(request.params,"order",3,3,6);
  const maxKeys=order-1,minKeys=Math.max(1,Math.ceil(order/2)-1);
  const op=request.operation;
  if(op!=="insert"&&keys.length===0)return api.makeRuntimeError(request,`B 树${op==="search"?"查找":"删除"}`,"EMPTY_TREE","B 树为空：请先在 initialData 中提供关键字。",viewState("btree",btreeRows(btreeNode([],[]),{operation:op,key,order})),`key=${key}`);
  const tree={root:btreeBuildAll(keys,order)};
  let sid=1;
  /* 一行一帧：`i++`、`p = p->child[i]`、`mid = n / 2`、`parent->key[i] = s`……
     当前结点用 `current`（结点内关键字组）点亮，当前下标/当前值走胶囊。 */
  const at=(extra={})=>viewState("btree",btreeRows(tree.root,{operation:op,key,order,...extra}));
  const steps=[makeStep(sid++,"init","B 树",`${order} 阶 B 树：每个结点最多 ${maxKeys} 个关键字（根最少 1 个，其余最少 ${minKeys} 个）。`,at())];
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"compare",code,code,at(extra),act?[act]:[]));
  const path=[]; /* 下降时记下每一层：{parent, pos}，pos 是"从父结点第几个孩子下来的"。 */
  const descend=()=>{
    let p=tree.root;
    snap("p = root",{current:p.keys},null,true);
    for(;;){
      snap("i = 0",{current:p.keys,i:0},null,true);
      let i=0;
      for(;;){
        const cont=i<p.keys.length&&key>p.keys[i];
        snap(`while (i < n && k > key[i]) → ${cont}`,{current:p.keys,i,"key[i]":i<p.keys.length?p.keys[i]:null,n:p.keys.length},null,true);
        if(!cont)break;
        i++;
        snap("i++",{current:p.keys,i},null,true);
      }
      const hit=i<p.keys.length&&key===p.keys[i];
      snap(`if (i < n && k == key[i]) → ${hit}`,{current:p.keys,i,k:key},null,true);
      if(hit)return {node:p,pos:i,found:true};
      if(!p.children.length)return {node:p,pos:i,found:false};
      path.push({parent:p,pos:i});
      const child=p.children[i];
      snap("p = p->child[i]",{current:child.keys,i,child:child.keys.join(",")},action("move","沿第 i 个孩子下降",{value:child.keys.join(",")}),true);
      p=child;
    }
  };

  if(op==="search"){
    const {node,pos,found}=descend();
    if(found){
      snap("return p->key[i]",{current:node.keys,i:pos,found:key},action("visit",`在第 ${pos+1} 个位置找到 ${key}`,{value:key}));
      return makeTrace(request,"B 树查找",`key=${key}`,"查找成功",steps);
    }
    snap("return NULL",{current:node.keys,i:pos,found:"NULL"},action("miss","到达叶结点仍未命中，查找失败",{value:null}));
    return makeTrace(request,"B 树查找",`key=${key}`,"未找到",steps);
  }

  if(op==="insert"){
    const {node,pos,found}=descend();
    if(found){
      snap("return 0",{current:node.keys,i:pos,"return":0},action("skip","关键字已存在，不再插入",{value:0}));
      return makeTrace(request,"B 树插入",`key=${key}`,"未插入：关键字已存在",steps);
    }
    node.keys.splice(pos,0,key);
    snap("p->key[i] = k; p->n = p->n + 1",{current:node.keys,i:pos},action("insert","关键字写进叶结点",{value:key}));
    snap(`if (p->n > m-1) → ${node.keys.length>maxKeys}`,{current:node.keys,i:pos,n:node.keys.length},null,true);
    /* 溢出就分裂：中间关键字提升给父结点，父结点也可能跟着溢出，一层层往上，直到根。 */
    let cur=node,li=path.length-1;
    while(cur.keys.length>maxKeys){
      const parent=li>=0?path[li].parent:null;
      const idx=li>=0?path[li].pos:0;
      const mid=Math.floor(cur.keys.length/2);
      const promote=cur.keys[mid];
      snap("mid = n / 2",{current:cur.keys,mid,n:cur.keys.length},null,true);
      snap("s = p->key[mid]",{current:cur.keys,mid,s:promote},action("read","中间那个关键字要提升",{value:promote}),true);
      const left=btreeNode(cur.keys.slice(0,mid),cur.children.slice(0,mid+1));
      const right=btreeNode(cur.keys.slice(mid+1),cur.children.slice(mid+1));
      if(parent===null){
        tree.root=btreeNode([promote],[left,right]);
        snap("r = malloc(); r->key[0] = s; r->child[0] = p; r->child[1] = q; root = r",{current:tree.root.keys,s:promote},action("split","根结点分裂：树长高一层",{value:promote}));
        break;
      }
      parent.keys.splice(idx,0,promote);
      parent.children.splice(idx,1,left,right);
      snap("parent->key[i] = s; parent->child[i+1] = q",{current:parent.keys,i:idx,s:promote},action("split",`${promote} 提升到父结点，${left.keys.join(",")||"空"} 与 ${right.keys.join(",")||"空"} 分成两个孩子`,{value:promote}));
      snap(`if (parent->n > m-1) → ${parent.keys.length>maxKeys}`,{current:parent.keys,n:parent.keys.length},null,true);
      cur=parent;li--;
    }
    return makeTrace(request,"B 树插入",`key=${key}`,"插入完成",steps);
  }

  /* delete：先找到关键字并删掉；关键字数掉到下限以下时，先找兄弟借一个，借不到就与兄弟合并。 */
  const {node,pos,found}=descend();
  if(!found){
    snap("return 0",{current:node.keys,i:pos,"return":0},action("miss","树里没有这个关键字",{value:0}));
    return makeTrace(request,"B 树删除",`key=${key}`,"未找到关键字，未删除",steps);
  }
  const parent=path.length?path[path.length-1].parent:null;
  const idx=path.length?path[path.length-1].pos:0;
  for(let j=pos;j<node.keys.length-1;j++){
    node.keys[j]=node.keys[j+1];
    snap(`key[${j}] = key[${j+1}]`,{current:node.keys,i:j},null,true);
  }
  node.keys.pop();
  snap("p->n = p->n - 1",{current:node.keys,i:Math.min(pos,node.keys.length-1),n:node.keys.length},action("delete",`从结点里删掉 ${key}`,{value:key}));
  snap(`if (p->n < ${minKeys}) → ${node.keys.length<minKeys}`,{current:node.keys,n:node.keys.length},null,true);
  let cur=node,li=path.length-1;
  while(cur.keys.length<minKeys&&li>=0){
    const par=path[li].parent,ci=path[li].pos;
    const left=ci>0?par.children[ci-1]:null;
    const right=ci+1<par.children.length?par.children[ci+1]:null;
    snap(`if (brother->n > ${minKeys}) → ${Boolean((left&&left.keys.length>minKeys)||(right&&right.keys.length>minKeys))}`,{current:par.keys,n:cur.keys.length},null,true);
    if(left&&left.keys.length>minKeys){
      cur.keys.unshift(par.keys[ci-1]);
      par.keys[ci-1]=left.keys.pop();
      if(left.children.length)cur.children.unshift(left.children.pop());
      snap("p->key[0] = parent->key[i-1]",{current:cur.keys,from:"left"},action("move","左兄弟最大的关键字借过来（分隔关键字先下来）",{value:par.keys[ci-1]}));
      snap("parent->key[i-1] = brother->key[n-1]",{current:par.keys,from:"left"},action("move","分隔关键字被兄弟最大关键字顶替",{value:par.keys[ci-1]}));
      break;
    }
    if(right&&right.keys.length>minKeys){
      cur.keys.push(par.keys[ci]);
      par.keys[ci]=right.keys.shift();
      if(right.children.length)cur.children.push(right.children.shift());
      snap("p->key[p->n] = parent->key[i]",{current:cur.keys,from:"right"},action("move","右兄弟最小的关键字借过来（分隔关键字先下来）",{value:par.keys[ci]}));
      snap("parent->key[i] = brother->key[0]",{current:par.keys,from:"right"},action("move","分隔关键字被兄弟最小关键字顶替",{value:par.keys[ci]}));
      break;
    }
    /* 两边兄弟都只剩下限：把关键字并到一起，父结点少一个关键字，再往上看父结点够不够。 */
    const target=left||right;
    if(!target){
      cur=par;li--;continue;
    }
    const down=left?par.keys[ci-1]:par.keys[ci];
    cur.keys.unshift(down);
    cur.keys.push(...target.keys);
    cur.children.push(...target.children);
    par.keys.splice(left?ci-1:ci,1);
    par.children.splice(left?ci-1:ci+1,1);
    snap(left?"q = p; 与左兄弟合并，分隔关键字 parent->key[i-1] 下来":"q = p; 与右兄弟合并，分隔关键字 parent->key[i] 下来",{current:cur.keys,merged:true},action("split","兄弟不足，合并成一个结点",{value:down}));
    cur=par;li--;
  }
  if(li<0&&tree.root.keys.length===0&&tree.root.children.length)tree.root=tree.root.children[0];
  snap("return 1",{current:tree.root.keys,"return":1});
  return makeTrace(request,"B 树删除",`key=${key}`,"删除完成",steps);
}

function hashIndex(key,m){const n=typeof key==="number"?key:[...String(key)].reduce((h,c)=>h*31+c.charCodeAt(0),0);return Math.abs(n)%m;}
function simulateHash(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const m=intParam(request.params,"tableSize",11,5,29);const existing=scalarArray(request.initial_state.data,[18,41,22,44,59]);const key=request.params.key??69;const op=request.operation;let sid=1;const table=Array(m).fill(null);const chains=Array.from({length:m},()=>[]);
  const offsets=(kind)=>{if(kind==="quadratic")return Array.from({length:m},(_,i)=>i===0?0:Math.ceil(i/2)**2*(i%2?1:-1));if(kind==="random"){let x=7;const a=[0];for(let i=1;i<m;i++){x=(x*17+11)%m;a.push(x);}return [...new Set(a)];}return Array.from({length:m},(_,i)=>i);};
  function probeSequence(k,kind){const h=hashIndex(k,m);if(kind==="rehash"){const step=1+(hashIndex(k,m-2)%(m-2));return Array.from({length:m},(_,i)=>(h+i*step)%m);}return offsets(kind).map(d=>(h+d+m)%m);}
  function probeInsert(k,kind="linear"){for(const idx of probeSequence(k,kind)){if(table[idx]===null){table[idx]=k;return idx;}}return-1;}
  const mode=op.startsWith("quadratic")?"quadratic":op.startsWith("random")?"random":op.startsWith("rehash")?"rehash":"linear";
  for(const k of existing){if(op.startsWith("chaining"))chains[hashIndex(k,m)].push(k);else probeInsert(k,mode);}
  // 链地址法要在 m 条同义词链里指出"正在看哪一条"：整条链用 focusIndex，链内某个结点用
  // focusCell=[桶号, 链内序号]。此前只把桶号塞进 meta（连 index 都会被共享指针池误当成桶号），
  // 界面上一条链都不亮。
  const rows=(mark)=>op.startsWith("chaining")?[row("buckets",chains.map((c)=>c.map(x=>String(x))),mark||{}),row("meta",[],{operation:op,key,tableSize:m})]:[row("table",table),row("meta",[],{operation:op,key,tableSize:m})];
  const steps=[makeStep(sid++,"init","哈希表初始状态",`表长 ${m}。`,viewState("hash_table",rows()))];
  if(op==="chaining_insert"){const h=hashIndex(key,m);steps.push(makeStep(sid++,"hash","计算散列地址",`H(${key}) = ${key} mod ${m} = ${h}，冲突的同义词存进同一条链。`,viewState("hash_table",[...rows({focusIndex:h}),row("probe",[],{bucket:h})]),[action("hash","计算散列地址",{target:h,value:key})]));chains[h].push(key);steps.push(makeStep(sid++,"insert","链地址法插入",`把关键字 ${key} 接入第 ${h} 个同义词链。`,viewState("hash_table",rows({focusIndex:h,focusCell:[h,chains[h].length-1]})),[action("insert","接入同义词链",{target:h,value:key})]));return makeTrace(request,"链地址法处理冲突",`key=${key}`,`bucket=${h}`,steps);}
  if(op==="chaining_search"){const h=hashIndex(key,m);steps.push(makeStep(sid++,"hash","计算散列地址",`H(${key}) = ${key} mod ${m} = ${h}，只需在第 ${h} 条同义词链里找。`,viewState("hash_table",[...rows({focusIndex:h}),row("probe",[],{bucket:h})]),[action("hash","计算散列地址",{target:h,value:key})]));for(let i=0;i<chains[h].length;i++){steps.push(makeStep(sid++,"compare","桶内比较",`比较 ${key} 与 ${chains[h][i]}。`,viewState("hash_table",[...rows({focusIndex:h,focusCell:[h,i]}),row("probe",[],{bucket:h,index:i})])));if(chains[h][i]===key)return makeTrace(request,"链地址哈希查找",`key=${key}`,"查找成功",steps);}steps.push(makeStep(sid,"done","同义词链走完","链上没有该关键字，查找失败。",viewState("hash_table",[...rows({focusIndex:h}),row("probe",[],{bucket:h})])));return makeTrace(request,"链地址哈希查找",`key=${key}`,"未找到",steps);}
  const search=op.endsWith("_search");const seq=probeSequence(key,mode);const h0=seq[0];/* 这一帧只算地址，**不指任何格子**：它是算术（H(69)=3），而"第 1 次探测"才把指针放到那个格子上。
     两帧都指 3 号格时，界面上一模一样——学生点「下一步」什么都没发生。 */steps.push(makeStep(sid++,"hash","计算散列地址",`H(${key}) = ${h0}${search?"，从散列地址开始逐个比较":"，被占用时按规则探测下一地址"}。`,viewState("hash_table",[...rows(),row("probe",[],{mode})]),[action("hash","计算散列地址",{target:h0,value:key})]));
for(let t=0;t<seq.length;t++){const idx=seq[t];steps.push(makeStep(sid++,"probe","探测哈希地址",`第 ${t+1} 次探测地址 ${idx}。`,viewState("hash_table",[...rows(),row("probe",[],{index:idx,attempt:t,mode})]),[action("probe","按冲突处理规则计算下一地址",{target:idx})]));if(search){if(table[idx]===key)return makeTrace(request,`${mode} 探测哈希查找`,`key=${key}`,"查找成功",steps);if(table[idx]===null)return makeTrace(request,`${mode} 探测哈希查找`,`key=${key}`,"未找到",steps);}else if(table[idx]===null){table[idx]=key;/* 写入帧也要带上 probe 面板：只发 rows() 时表格没有任何指针，新关键字落在哪一格看不出来。 */steps.push(makeStep(sid++,"insert","写入空单元",`${key} 写入地址 ${idx}。`,viewState("hash_table",[...rows(),row("probe",[],{index:idx,attempt:t,mode,stored:key})]),[action("insert","写入哈希表",{target:idx,value:key})]));const title={linear:"线性探测再散列",quadratic:"二次探测再散列",random:"伪随机探测再散列",rehash:"再哈希法"}[mode];return makeTrace(request,title,`key=${key}`,`address=${idx}`,steps);}}
  return makeTrace(request,"哈希探测",`key=${key}`,"探测结束",steps);
}

function sequenceRows(a,extra={}){return[row("array",a),row("meta",[],extra)];}
/* 不变式里"已排好的区间"在开头必须是**空**的，不能写成倒序区间：第一趟的 end 就等于 a.length-1，
   直接拼 `下标 ${end+1} 到 ${a.length-1}` 会得到"下标 4 到 3 已经排好"——一句读不通的话。 */
function sortedTail(a,end){return end>=a.length-1?"还没有任何元素就位":`下标 ${end+1} 到 ${a.length-1} 已经排好`;}
function sortedHead(i){return i<=0?"还没有任何元素就位":`下标 0 到 ${i-1} 已经排好`;}
function simulateSort(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const a=numberArray(request.initial_state.data,[49,38,65,97,76,13,27,49]);const initialText=`[${a.join(",")}]`;const op=request.operation;let sid=1;let invariant="";const steps=[makeStep(sid++,"init","待排序序列",`[${a.join(", ")}]`,viewState("sort",sequenceRows(a,{operation:op,invariant:"整个序列都还是待排序区，没有任何记录就位"})))];if(!a.length){steps.push(makeStep(sid,"done","序列为空","没有待排序记录，排序结束。",viewState("sort",sequenceRows(a,{operation:op,done:true}))));return makeTrace(request,`教材排序演示：${op}`,initialText,"[]",steps);}function snap(title,note,extra={},act=null,line=false){const acts=Array.isArray(act)?act:(act?[act]:[]);const meta={operation:op,...(invariant?{invariant}:{}),...extra};steps.push(makeStep(sid++,line?"line":(extra.phase||"sort"),title,note,viewState("sort",sequenceRows(a,meta)),acts.map(t=>action(t,t==="compare"?"比较关键字":"排序操作",extra))));}
  /* 交换的三步**逐行**报帧：`temp = a[i]` → `a[i] = a[j]` → `a[j] = temp`。
     "为什么需要一个临时变量"就画在中间那一帧上（同一个值出现两份，学生自己看得见）；
     `temp` 本身在胶囊里显示，所以第一步（数组没变）也不会和上一帧长得一样。
     这三帧都标 line ⇒ **精简版只留最后一步**，于是精简版正好是"比较一帧、交换一帧"。
     代码帧的约定：**title 与 note 都是那行代码**（解释性帧两者不同，前端靠这个决定用不用等宽字体）。 */
  const swapFrames=(x,y,extra={})=>{const tx=a[x],ty=a[y];
    snap(`temp = a[${x}]`,`temp = a[${x}]`,{...extra,temp:tx},"temp",true);
    a[x]=ty;
    snap(`a[${x}] = a[${y}]`,`a[${x}] = a[${y}]`,{...extra,temp:tx},"move",true);
    a[y]=tx;
    snap(`a[${y}] = temp`,`a[${y}] = temp`,{...extra,temp:tx},"swap",true);
  };
  /* 一次比较报一帧，是「比较次数」这个计数器的唯一来源：计数器只数引擎真正报出来的动作，
     所以只要比较没有自己的帧，冒泡排序就会显示「比较 0 次、交换 5 次」——比不显示更误导。
     每帧只讲这一步在比较谁、结果如何；真的发生交换时把 swap 动作挂在同一帧上，帧数就等于比较次数，
     不会因为"先比较再交换"而翻倍。 */
  if(op==="direct_insertion"||op==="binary_insertion"){
    for(let i=1;i<a.length;i++){const key=a[i];let pos=i;invariant=`下标 0 到 ${i-1} 是已排好的有序区；现在把 a[${i}]=${key} 插进有序区`;if(op==="binary_insertion"){let l=0,r=i-1;while(l<=r){const m=Math.floor((l+r)/2);snap(`if (key < a[${m}]) → ${key<a[m]?"true":"false"}`,`if (key < a[${m}]) → ${key<a[m]?"true":"false"}`,{current:m,low:l,high:r},"compare");if(key<a[m])r=m-1;else l=m+1;}pos=l;}else{while(pos>0){if(!(a[pos-1]>key)){snap(`if (a[${pos-1}] > key) → false`,`if (a[${pos-1}] > key) → false`,{current:pos-1,sortedEnd:i},"compare");break;}const before=a[pos-1];pos--;snap(`if (a[${pos}] > key) → true`,`if (a[${pos}] > key) → true`,{current:pos,sortedEnd:i},"compare");}}
      for(let j=i;j>pos;j--){a[j]=a[j-1];snap(`a[${j}] = a[${j-1}]`,`a[${j}] = a[${j-1}]`,{current:j,sortedEnd:i},"move",true);}a[pos]=key;snap("插入到有序区",`把 ${key} 插入位置 ${pos}。`,{current:pos,sortedEnd:i},"insert");}
  } else if(op==="shell"){
    /* 每个子序列插入各报一帧（以前整趟只有一帧，画面既没有过程也没有高亮）。 */
    const gaps=Array.isArray(request.params.gaps)?request.params.gaps.map(Number).filter(x=>Number.isInteger(x)&&x>0):[Math.floor(a.length/2),1];for(const gap0 of gaps){const gap=Math.min(gap0,a.length-1);if(gap<1)continue;invariant=`当前增量 gap=${gap}：每隔 ${gap} 个位置取一条子序列，子序列内部做插入排序`;for(let i=gap;i<a.length;i++){const temp=a[i];let j=i;let shifted=false;while(j>=gap){if(!(a[j-gap]>temp)){snap(`if (a[${j-gap}] > temp) → false`,`if (a[${j-gap}] > temp) → false`,{gap,i,j,phase:"group_sort"},shifted?"compare":["compare","insert"]);break;}const before=a[j-gap];a[j]=before;j-=gap;shifted=true;snap(`a[${j}] = a[${j-gap}]`,`a[${j}] = a[${j-gap}]`,{gap,i,j,phase:"group_sort"},["compare","move"]);}a[j]=temp;/* 一步都没后移时插入是空操作，再报一帧只会让画面和上一帧一模一样。 */if(shifted)snap("子序列插入",`gap=${gap}：把 ${temp} 插到位置 ${j}。`,{gap,i,j,phase:"group_sort"},"insert");}}
  } else if(op==="bubble"){
    for(let end=a.length-1;end>0;end--){let swapped=false;invariant=`${sortedTail(a,end)}；本趟把 [0, ${end}] 里的最大值冒到下标 ${end}`;for(let i=0;i<end;i++){const left=a[i],right=a[i+1];const verdict=left>right?"true":"false";snap(`if (a[${i}] > a[${i+1}]) → ${verdict}`,`if (a[${i}] > a[${i+1}]) → ${verdict}`,{i,j:i+1},"compare");if(left>right){swapFrames(i,i+1,{i,j:i+1});swapped=true;}}snap("一趟冒泡结束",`最大元素已到位置 ${end}。`,{sortedStart:end});if(!swapped)break;}
  } else if(op==="quick"){
    function q(l,r){if(l>=r)return;const pivot=a[l];invariant=`正在划分区间 [${l}, ${r}]：枢轴是 a[${l}]=${pivot}，比它小的放左边、大的放右边`;let i=l,j=r;while(i<j){while(i<j){const hit=a[j]>=pivot;snap(`if (a[${j}] >= pivot) → ${hit?"true":"false"}`,`if (a[${j}] >= pivot) → ${hit?"true":"false"}`,{low:l,high:r,pivotIndex:i,current:j},"compare");if(!hit)break;j--;}if(i<j){a[i]=a[j];snap(`a[${i}] = a[${j}]`,`a[${i}] = a[${j}]`,{low:l,high:r,pivotIndex:i,current:i},"move",true);i++;}while(i<j){const hit=a[i]<=pivot;snap(`if (a[${i}] <= pivot) → ${hit?"true":"false"}`,`if (a[${i}] <= pivot) → ${hit?"true":"false"}`,{low:l,high:r,pivotIndex:i,current:i},"compare");if(!hit)break;i++;}if(i<j){a[j]=a[i];snap(`a[${j}] = a[${i}]`,`a[${j}] = a[${i}]`,{low:l,high:r,pivotIndex:j,current:j},"move",true);j--;} }a[i]=pivot;snap("完成一次划分",`枢轴 ${pivot} 落在位置 ${i}。`,{low:l,high:r,pivotIndex:i},"partition");q(l,i-1);q(i+1,r);}q(0,a.length-1);
  } else if(op==="simple_selection"){
    for(let i=0;i<a.length-1;i++){let min=i;invariant=`${sortedHead(i)}${i>0?`（这 ${i} 个是全局最小的）`:""}；现在从下标 ${i} 到 ${a.length-1} 里挑最小值`;for(let j=i+1;j<a.length;j++){const hit=a[j]<a[min];const verdict=hit?"true":"false";snap(`if (a[${j}] < a[${min}]) → ${verdict}`,`if (a[${j}] < a[${min}]) → ${verdict}`,{current:i,selected:min,scan:j},"compare");if(hit)min=j;}if(min!==i){swapFrames(i,min,{current:i,selected:min});}else{snap("无需交换",`位置 ${i} 已是剩余记录中的最小值，第 ${i+1} 个位置确定为 ${a[i]}。`,{current:i,selected:min},"select");}}
  } else if(op==="tournament_selection"){
    const remain=a.map((v,i)=>({v,i})),out=[];while(remain.length){remain.sort((x,y)=>x.v-y.v);const win=remain.shift();out.push(win.v);invariant=`已经输出 ${out.length} 个最小记录；剩下 ${remain.length} 个还在锦标赛里`;/* current 指向胜者在原序列里的下标，画面才知道高亮谁 */snap("锦标赛选出当前最小值",`${win.v} 胜出并输出。`,{winner:win.v,current:win.i,output:[...out]},"select");}a.splice(0,a.length,...out);
  } else if(op==="heap"){
    function down(n,i){invariant=`堆区是 [0, ${n-1}]，正在从结点 ${i} 向下调整：让父结点不小于它的孩子`;while(true){let largest=i;const l=2*i+1,r=2*i+2;if(l<n){const hit=a[l]>a[largest];snap(`if (a[${l}] > a[${largest}]) → ${hit?"true":"false"}`,`if (a[${l}] > a[${largest}]) → ${hit?"true":"false"}`,{i,j:largest,heapSize:n,current:l},"compare");if(hit)largest=l;}if(r<n){const hit=a[r]>a[largest];snap(`if (a[${r}] > a[${largest}]) → ${hit?"true":"false"}`,`if (a[${r}] > a[${largest}]) → ${hit?"true":"false"}`,{i,j:largest,heapSize:n,current:r},"compare");if(hit)largest=r;}if(largest===i)break;swapFrames(i,largest,{i,j:largest,heapSize:n});i=largest;}}
    for(let i=Math.floor(a.length/2)-1;i>=0;i--)down(a.length,i);snap("建立初始大根堆","从最后一个非叶结点向前调整。",{heapSize:a.length},"heapify");for(let end=a.length-1;end>0;end--){invariant=`${sortedTail(a,end)}；堆区是 [0, ${end}]，堆顶 ${a[0]} 是当前最大值`;swapFrames(0,end,{i:0,j:end,heapSize:end});down(end,0);}
  } else if(op==="merge"){
    /* 逐记录归并：以前整段只报一帧、且只带 left/mid/right 三个数字，
       画面既没有过程也没有高亮（2026-09-24：「你的高亮，不行，整个卡住」）。
       现在每写回一条记录报一帧：数组面板上 current 走到刚写好的位置、range 框住当前归并段，
       左段/右段两个面板各自跟着正在比较的那条记录——和「合并相邻两个有序子序列」同一套画法。 */
    for(let width=1;width<a.length;width*=2){
      for(let l=0;l<a.length;l+=2*width){
        const m=Math.min(l+width,a.length),r=Math.min(l+2*width,a.length);
        if(m>=r)continue;invariant=`正在合并 [${l}, ${m}) 与 [${m}, ${r})：两段各自已经有序，谁小先取谁`;                                   // 只剩一段，本身有序，无需归并
        const seg=a.slice(l,r),half=m-l,leftSeg=seg.slice(0,half),rightSeg=seg.slice(half);
        let i=0,j=half,k=l;
        const show=()=>viewState("sort",[
          row("array",a),
          row("left",leftSeg,{focusIndex:Math.max(0,Math.min(i,leftSeg.length-1))}),
          row("right",rightSeg,{focusIndex:Math.max(0,Math.min(j-half,rightSeg.length-1))}),
          row("meta",[],{operation:op,current:k,low:l,high:r-1,mid:m,...(invariant?{invariant}:{})}),
        ]);
        const emit=(title,note,compared=false)=>steps.push(makeStep(sid++,"merge",title,note,show(),compared?[action("compare","比较两段当前记录",{to:k}),action("merge","归并当前记录",{to:k})]:[action("merge","归并当前记录",{to:k})]));
        emit("开始归并有序段",`合并 [${l},${m}) 与 [${m},${r})。`);
        while(i<half&&j<seg.length){
          const fromLeft=seg[i]<=seg[j];
          const taken=fromLeft?seg[i]:seg[j];
          a[k]=taken;
          emit(fromLeft?"取前半段较小记录":"取后半段较小记录",`比较左段 ${seg[i]} 与右段 ${seg[j]}，取 ${taken} 写入位置 ${k}。`,true);
          if(fromLeft)i++;else j++;
          k++;
        }
        while(i<half){a[k]=seg[i];emit("前半段剩余记录就位",`${a[k]} 写入位置 ${k}。`);i++;k++;}
        while(j<seg.length){a[k]=seg[j];emit("后半段剩余记录就位",`${a[k]} 写入位置 ${k}。`);j++;k++;}
        k=r-1;
        emit("本段归并完成",`[${l},${r}) 已有序：${a.slice(l,r).join(", ")}。`);
      }
    }
  } else if(op==="radix"){
    /* 逐个元素分桶（以前整趟只有一帧）：每帧高亮正在分派的那个元素。 */
    let exp=1,max=Math.max(...a.map(Math.abs));while(Math.floor(max/exp)>0){const unit=exp===1?"个位":exp===10?"十位":`10^${Math.log10(exp)}位`;invariant=`第 ${Math.round(Math.log10(exp))+1} 趟：按${unit}把每条记录分进 0~9 号桶，再按桶号收回来`;const buckets=Array.from({length:10},()=>[]);a.forEach((v,idx)=>{const d=Math.floor(Math.abs(v)/exp)%10;buckets[d].push(v);snap("按当前位分配",`${v} 的第 ${exp===1?"个位":exp===10?"十位":`10^${Math.log10(exp)}位`}是 ${d}，进 ${d} 号队列。`,{exp,digit:d,current:idx,phase:"distribute"},"distribute");});a.splice(0,a.length,...buckets.flat());snap("按桶序收集","依次收集 0~9 号队列。",{exp,phase:"collect"},"collect");exp*=10;}
  }
  invariant="排序结束：整个序列已经有序";snap("排序完成",`[${a.join(", ")}]`,{done:true},"done");return makeTrace(request,`教材排序演示：${op}`,initialText,`[${a.join(",")}]`,steps);}

function simulateExternalSort(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const rawRuns=request.params.runs!==undefined?request.params.runs:request.initial_state.data;const runs=Array.isArray(rawRuns)&&rawRuns.every(Array.isArray)?rawRuns.map(r=>numberArray(r,[])):[[1,7,13],[2,8,12],[3,6,15]];if(request.operation!=="replacement_selection"&&runs.length===0)throw new SimulationInputError("EMPTY_RUNS","归并段列表为空：请提供至少一个初始归并段","runs");let sid=1;const steps=[makeStep(sid++,"init","初始归并段",`${runs.length} 个归并段。`,viewState("external_sort",[row("runs",runs),row("meta",[],{operation:request.operation})]))];if(request.operation==="replacement_selection"){const input=numberArray(request.params.input??(Array.isArray(request.initial_state.data)&&!request.initial_state.data.every(Array.isArray)?request.initial_state.data:undefined),[12,7,18,3,15,9,20,4]);if(request.params.input!==undefined&&!input.length)throw new SimulationInputError("EMPTY_INPUT","输入记录为空：置换选择至少需要一条记录","input");const memSize=intParam(request.params,"memorySize",3,2,8);const pool=input.slice(0,memSize),rest=input.slice(memSize),out=[];let last=-Infinity;while(pool.length){pool.sort((a,b)=>a-b);let idx=pool.findIndex(x=>x>=last);if(idx<0){steps.push(makeStep(sid++,"new_run","开始新的初始归并段","内存中剩余记录均小于当前输出下界。",viewState("external_sort",[row("memory",pool),row("output",out),row("input",rest),row("meta",[],{operation:request.operation})])));last=-Infinity;idx=0;}const v=pool.splice(idx,1)[0];out.push(v);last=v;if(rest.length)pool.push(rest.shift());steps.push(makeStep(sid++,"output","输出当前可选最小记录",`输出 ${v}，并读入下一条记录。`,viewState("external_sort",[row("memory",pool),row("output",out,{focusIndex:out.length-1}),row("input",rest),row("meta",[],{operation:request.operation,last})]),[action("output","生成初始归并段",{value:v})]));}return makeTrace(request,"置换选择生成初始归并段","输入文件",`输出=${out.join(",")}`,steps);}let current=runs.map(r=>[...r]);const k=request.operation==="multiway_merge"?Math.max(2,intParam(request.params,"ways",3,2,8)):2;while(current.length>1){const next=[];for(let i=0;i<current.length;i+=k){const group=current.slice(i,i+k),pos=Array(group.length).fill(0),merged=[];next.push(merged);
/* 逐记录归并：整组只报一帧时，输出顺串是"瞬间长出来的"，看不出选了谁（2026-09-24 用户反馈）。
   现在每选出一条记录报一帧：runs 面板框住被取走的那条记录，outputRuns 框住刚写入的那一格。 */
const show=(hit,written)=>viewState("external_sort",[row("runs",current,hit?{focusCell:[i+hit[0],hit[1]]}:{}),row("outputRuns",next,{focusCell:[next.length-1,Math.max(0,written)]}),row("meta",[],{operation:request.operation,ways:k})]);
if(group.length===1){merged.push(...group[0]);steps.push(makeStep(sid++,"merge","单顺串直接作为输出",`第 ${i+1} 个顺串无需归并。`,show(null,merged.length-1)));}else{while(true){let best=-1,bv=Infinity;for(let g=0;g<group.length;g++)if(pos[g]<group[g].length&&group[g][pos[g]]<bv){bv=group[g][pos[g]];best=g;}if(best<0)break;const at=pos[best],v=group[best][pos[best]++];merged.push(v);steps.push(makeStep(sid++,"merge","选出当前最小记录",`${v} 来自第 ${i+best+1} 个顺串，写入输出顺串。`,show([best,at],merged.length-1),[action("merge","多路选择当前最小记录",{value:v})]));}}
steps.push(makeStep(sid++,"merge","一组顺串归并完毕",`${group.length} 路归并得到 [${merged.join(",")}]。`,show(null,merged.length-1),[action("merge","归并一组顺串",{value:merged.length})]));}current=next;}return makeTrace(request,request.operation==="multiway_merge"?"多路归并外排序":"二路归并外排序","初始归并段",`最终顺串=[${current[0]?.join(",")||""}]`,steps);}

function simulateTextbookOperation(request, api) {
  if (ownOf(AUX_SUPPORTED_PAIRS, request.structure)?.has(request.operation)) return simulateAuxiliaryOperation(request, api);
  if (request.structure === "linked_list") return simulateLinkedList(request, api);
  if (request.structure === "doubly_linked_list") return simulateDoublyList(request, api);
  if (request.structure === "polynomial") return simulatePolynomial(request, api);
  if (request.structure === "stack_app" && request.operation === "bracket_match") return simulateBracketMatch(request, api);
  if (request.structure === "stack_app") return simulateExpression(request, api);
  if (request.structure === "recursion") return simulateHanoi(request, api);
  if (request.structure === "circular_queue") return simulateCircularQueue(request, api);
  if (request.structure === "string") return simulateString(request, api);
  if (request.structure === "sparse_matrix") return simulateSparseMatrix(request, api);
  if (request.structure === "generalized_list") return simulateGeneralizedList(request, api);
  if (request.structure === "tree") return simulateTree(request, api);
  if (request.structure === "huffman") return simulateHuffman(request, api);
  if (request.structure === "union_find") return simulateUnionFind(request, api);
  if (request.structure === "graph") return simulateGraph(request, api);
  if (request.structure === "search") return simulateSearch(request, api);
  if (request.structure === "bst") return simulateBST(request, api);
  if (request.structure === "avl") return simulateAVL(request, api);
  if (request.structure === "btree") return simulateBTree(request, api);
  if (request.structure === "hash_table") return simulateHash(request, api);
  if (request.structure === "sort") return simulateSort(request, api);
  if (request.structure === "external_sort") return simulateExternalSort(request, api);
  throw new Error(`未实现教材动画 ${request.structure}/${request.operation}`);
}

module.exports = {
  TEXTBOOK_SUPPORTED_PAIRS,
  normalizeTextbookRequest,
  simulateTextbookOperation,
  textbookStateValues
};
