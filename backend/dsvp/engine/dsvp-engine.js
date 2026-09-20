const crypto = require("node:crypto");
const {
  TEXTBOOK_SUPPORTED_PAIRS,
  normalizeTextbookRequest,
  simulateTextbookOperation,
  textbookStateValues
} = require("./textbook-animation-engine");

const DSVP_VERSION = "1.0";
const CORE_SUPPORTED_DEMO_PAIRS = Object.freeze({
  stack: new Set(["push", "pop"]),
  queue: new Set(["enqueue", "dequeue"]),
  sequential_list: new Set(["insert", "delete", "merge"])
});
const SUPPORTED_DEMO_PAIRS = Object.freeze(Object.fromEntries(
  Array.from(new Set([...Object.keys(CORE_SUPPORTED_DEMO_PAIRS), ...Object.keys(TEXTBOOK_SUPPORTED_PAIRS)]))
    .map((structure) => [
      structure,
      new Set([...(CORE_SUPPORTED_DEMO_PAIRS[structure] || []), ...(TEXTBOOK_SUPPORTED_PAIRS[structure] || [])])
    ])
));

class DsvpValidationError extends Error {
  constructor(code, message, detail = "") {
    super(message);
    this.name = "DsvpValidationError";
    this.code = code;
    this.detail = detail;
  }
}

function assertPlainObject(value, path) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new DsvpValidationError("INVALID_REQUEST", `${path} 必须是对象`, path);
  }
}

function assertAllowedKeys(value, allowed, path) {
  const unexpected = Object.keys(value).filter((key) => !allowed.has(key));
  if (unexpected.length) {
    throw new DsvpValidationError(
      "UNEXPECTED_FIELD",
      `${path} 包含未定义字段：${unexpected.join("、")}`,
      unexpected.join(",")
    );
  }
}

function normalizeScalar(value, path) {
  if (["string", "number", "boolean"].includes(typeof value) || value === null) return value;
  throw new DsvpValidationError("INVALID_VALUE", `${path} 只能使用字符串、数字、布尔值或 null`, path);
}

function normalizeCapacity(value, fallback = 10) {
  const number = Number(value ?? fallback);
  if (!Number.isInteger(number) || number < 1 || number > 100) {
    throw new DsvpValidationError("INVALID_CAPACITY", "capacity 必须是 1 到 100 之间的整数", String(value));
  }
  return number;
}

function normalizeOperationRequest(input) {
  assertPlainObject(input, "request");
  assertAllowedKeys(
    input,
    new Set(["version", "structure", "operation", "params", "initial_state", "options", "source_ref"]),
    "request"
  );

  const version = String(input.version || DSVP_VERSION).trim();
  if (version !== DSVP_VERSION) {
    throw new DsvpValidationError("UNSUPPORTED_VERSION", `暂不支持 DSVP ${version}`, version);
  }

  let structure = String(input.structure || "").trim();
  let operation = String(input.operation || "").trim();
  // 核心契约别名（contracts/dsvp.schema.json）：模型发的是 tree/traverse + params.order，
  // 而教材引擎只认识 preorder/inorder/postorder/levelorder 这些具体序。
  // 不做别名转换时请求会一路落空到 Java 兜底模拟器，产出没有视图面板的空帧。
  if (structure === "tree" && operation === "traverse") {
    const rawOrder = input.params && typeof input.params === "object" ? input.params.order : undefined;
    const order = String(rawOrder ?? "inorder").toLowerCase();
    if (!["preorder", "inorder", "postorder", "levelorder"].includes(order)) {
      throw new DsvpValidationError(
        "INVALID_PARAM",
        `tree/traverse 的 order=${JSON.stringify(rawOrder ?? "inorder")} 不支持，应为 preorder/inorder/postorder/levelorder`,
        "order"
      );
    }
    operation = order;
    input = { ...input, structure, operation };
  }

  // 只认自有属性，防止 structure="toString"/"constructor" 等原型链键命中继承成员后
  // 在 .has 上抛 TypeError（此前会以 INTERNAL_ERROR 泄露内部报错）。
  const supportedOperations = Object.hasOwn(SUPPORTED_DEMO_PAIRS, structure) ? SUPPORTED_DEMO_PAIRS[structure] : undefined;
  if (!supportedOperations?.has(operation)) {
    throw new DsvpValidationError(
      "UNSUPPORTED_OPERATION",
      `教材动画暂不支持 ${structure || "未知结构"}/${operation || "未知操作"}`,
      `${structure}/${operation}`
    );
  }

  // 教材扩展动画使用通用 JSON 参数模型；核心栈/队列/顺序表仍保留严格首版校验。
  if (!CORE_SUPPORTED_DEMO_PAIRS[structure]?.has(operation)) {
    return normalizeTextbookRequest(input, DsvpValidationError, version);
  }

  const params = input.params ?? {};
  assertPlainObject(params, "request.params");
  assertAllowedKeys(params, new Set(["value", "capacity", "position"]), "request.params");

  const initialState = input.initial_state;
  assertPlainObject(initialState, "request.initial_state");
  assertAllowedKeys(initialState, new Set(["data", "metadata"]), "request.initial_state");
  if (!Array.isArray(initialState.data)) {
    throw new DsvpValidationError("INVALID_INITIAL_STATE", "initial_state.data 必须是数组", "initial_state.data");
  }
  if (initialState.data.length > 100) {
    throw new DsvpValidationError("INITIAL_STATE_TOO_LARGE", "首版动画最多展示 100 个元素", String(initialState.data.length));
  }
  let data;
  if (structure === "sequential_list" && operation === "merge") {
    if (initialState.data.length !== 2 || initialState.data.some((items) => !Array.isArray(items))) {
      throw new DsvpValidationError("INVALID_INITIAL_STATE", "顺序表合并需要在 initial_state.data 中提供两个数组", "initial_state.data");
    }
    data = initialState.data.map((items, listIndex) => {
      if (items.length > 20) {
        throw new DsvpValidationError("INITIAL_STATE_TOO_LARGE", "单个顺序表最多演示 20 个元素", `initial_state.data[${listIndex}]`);
      }
      const values = items.map((item, index) => normalizeScalar(item, `initial_state.data[${listIndex}][${index}]`));
      if (values.some((item) => typeof item !== "number" || !Number.isFinite(item))) {
        throw new DsvpValidationError("INVALID_VALUE", "顺序表合并首版只接受有限数字", `initial_state.data[${listIndex}]`);
      }
      if (values.some((item, index) => index > 0 && values[index - 1] > item)) {
        throw new DsvpValidationError("UNSORTED_INPUT", "参与合并的两个顺序表必须按非递减顺序排列", `initial_state.data[${listIndex}]`);
      }
      return values;
    });
  } else {
    data = initialState.data.map((item, index) => normalizeScalar(item, `initial_state.data[${index}]`));
  }

  const metadata = initialState.metadata ?? {};
  assertPlainObject(metadata, "request.initial_state.metadata");
  assertAllowedKeys(metadata, new Set(["capacity"]), "request.initial_state.metadata");
  const elementCount = structure === "sequential_list" && operation === "merge" ? data[0].length + data[1].length : data.length;
  const capacity = normalizeCapacity(params.capacity ?? metadata.capacity, Math.max(10, elementCount + 1));
  if (elementCount > capacity) {
    throw new DsvpValidationError("INITIAL_STATE_OVERFLOW", "初始元素数量不能超过 capacity", `${elementCount}/${capacity}`);
  }

  const requiresValue = operation === "push" || operation === "enqueue" || operation === "insert";
  if (requiresValue && params.value === undefined) {
    throw new DsvpValidationError("MISSING_VALUE", `${operation} 缺少 params.value`, "params.value");
  }

  let position = null;
  if (structure === "sequential_list" && ["insert", "delete"].includes(operation)) {
    const rawPosition = Number(params.position);
    if (!Number.isInteger(rawPosition)) {
      throw new DsvpValidationError("MISSING_POSITION", `${operation} 缺少整数 params.position`, "params.position");
    }
    const maxPosition = operation === "insert" ? data.length + 1 : data.length;
    if (rawPosition < 1 || rawPosition > maxPosition) {
      throw new DsvpValidationError(
        "INVALID_POSITION",
        `${operation === "insert" ? "插入" : "删除"}位置必须在 1 到 ${maxPosition} 之间`,
        String(rawPosition)
      );
    }
    position = rawPosition;
  }

  const options = input.options ?? {};
  assertPlainObject(options, "request.options");
  assertAllowedKeys(options, new Set(["language", "explain_level"]), "request.options");

  return {
    version,
    structure,
    operation,
    params: {
      ...(requiresValue ? { value: normalizeScalar(params.value, "params.value") } : {}),
      ...(position !== null ? { position } : {}),
      capacity
    },
    initial_state: {
      data,
      metadata: { capacity }
    },
    options: {
      language: String(options.language || "c").slice(0, 16),
      explain_level: String(options.explain_level || "beginner").slice(0, 24)
    },
    source_ref: String(input.source_ref || "").slice(0, 160)
  };
}

function stableTraceId(request) {
  const digest = crypto.createHash("sha256").update(JSON.stringify(request)).digest("hex").slice(0, 20);
  return `dsvp_${digest}`;
}

function emptyHighlights() {
  return { nodes: [], edges: [], cells: [], pointers: [] };
}

function action(type, description, fields = {}) {
  return {
    type,
    description,
    target: fields.target ?? null,
    from: fields.from ?? null,
    to: fields.to ?? null,
    value: fields.value ?? null
  };
}

function stackState(items, capacity, top = items.length - 1) {
  return {
    kind: "stack",
    items: items.map((value, index) => ({ index, value })),
    top,
    metadata: { capacity }
  };
}

function queueState(items, capacity) {
  return {
    kind: "queue",
    items: items.map((value, index) => ({ index, value })),
    front: items.length ? 0 : -1,
    rear: items.length ? items.length - 1 : -1,
    metadata: { capacity }
  };
}

function makeStep(stepId, phase, title, description, state, actions = [], highlights = emptyHighlights()) {
  return {
    step_id: stepId,
    phase,
    title,
    description,
    state,
    highlights,
    actions,
    code_refs: [],
    message: description
  };
}

function makeTrace(request, title, initialText, resultText, steps, errors = [], warnings = []) {
  return {
    version: DSVP_VERSION,
    trace_id: stableTraceId(request),
    title,
    structure: request.structure,
    operation: request.operation,
    source_ref: request.source_ref,
    summary: {
      initial: initialText,
      result: resultText,
      time_complexity: "",
      space_complexity: ""
    },
    steps,
    errors,
    warnings
  };
}

function makeRuntimeError(request, title, code, message, state, initialText) {
  return makeTrace(
    request,
    title,
    initialText,
    initialText,
    [makeStep(1, "error", "操作条件不满足", message, state, [action("check_condition", message, { target: request.structure })])],
    [{ code, message, detail: message, recoverable: true }]
  );
}

function simulateStack(request) {
  const items = [...request.initial_state.data];
  const capacity = request.initial_state.metadata.capacity;
  const initialText = items.length ? `栈底 [${items.join(", ")}] 栈顶` : "空栈";
  if (request.operation === "push") {
    if (items.length >= capacity) {
      return makeRuntimeError(request, "栈入栈演示", "STACK_OVERFLOW", "当前栈已满，不能继续入栈。", stackState(items, capacity), initialText);
    }
    const value = request.params.value;
    const newTop = items.length;
    const result = [...items, value];
    return makeTrace(request, "栈入栈演示", initialText, `栈底 [${result.join(", ")}] 栈顶`, [
      makeStep(1, "init", "初始状态", `当前栈为${initialText === "空栈" ? "空栈" : ` ${initialText}`}。`, stackState(items, capacity)),
      makeStep(2, "check", "检查是否栈满", "当前栈未满，可以执行入栈。", stackState(items, capacity), [action("check_condition", "检查 top < capacity - 1。", { target: "top" })]),
      makeStep(3, "move", "top 后移", `top 移动到位置 ${newTop}。`, stackState(items, capacity, newTop), [action("move", "top = top + 1。", { target: "top", value: newTop })], { ...emptyHighlights(), pointers: [{ role: "changed", name: "top" }] }),
      makeStep(4, "push", "写入栈顶元素", `将 ${String(value)} 写入新的栈顶位置。`, stackState(result, capacity), [action("push", "在 top 位置写入新元素。", { target: "top", value })], { ...emptyHighlights(), cells: [{ role: "new", index: newTop }] }),
      makeStep(5, "done", "入栈完成", `结果为栈底 [${result.join(", ")}] 栈顶。`, stackState(result, capacity), [], { ...emptyHighlights(), cells: [{ role: "success", index: newTop }] })
    ]);
  }

  if (!items.length) {
    return makeRuntimeError(request, "栈出栈演示", "STACK_UNDERFLOW", "当前栈为空，不能执行出栈。", stackState(items, capacity), initialText);
  }
  const removed = items[items.length - 1];
  const result = items.slice(0, -1);
  const resultText = result.length ? `栈底 [${result.join(", ")}] 栈顶` : "空栈";
  return makeTrace(request, "栈出栈演示", initialText, resultText, [
    makeStep(1, "init", "初始状态", `当前栈为 ${initialText}。`, stackState(items, capacity)),
    makeStep(2, "check", "检查是否栈空", "当前栈非空，可以执行出栈。", stackState(items, capacity), [action("check_condition", "检查 top >= 0。", { target: "top" })]),
    makeStep(3, "read", "读取栈顶元素", `先读取栈顶元素 ${String(removed)}。`, stackState(items, capacity), [action("assign", "读取当前栈顶值。", { target: "value", value: removed })], { ...emptyHighlights(), cells: [{ role: "target", index: items.length - 1 }] }),
    makeStep(4, "pop", "移除栈顶元素", `${String(removed)} 离开栈，top 前移。`, stackState(result, capacity), [action("pop", "移除当前栈顶元素。", { target: "top", value: removed }), action("move", "top = top - 1。", { target: "top", value: result.length - 1 })], { ...emptyHighlights(), pointers: [{ role: "changed", name: "top" }] }),
    makeStep(5, "done", "出栈完成", `结果为${resultText === "空栈" ? "空栈" : ` ${resultText}`}。`, stackState(result, capacity))
  ]);
}

function simulateQueue(request) {
  const items = [...request.initial_state.data];
  const capacity = request.initial_state.metadata.capacity;
  const initialText = items.length ? `队首 ${items.join(" → ")} 队尾` : "空队列";
  if (request.operation === "enqueue") {
    if (items.length >= capacity) {
      return makeRuntimeError(request, "队列入队演示", "QUEUE_OVERFLOW", "当前队列已满，不能继续入队。", queueState(items, capacity), initialText);
    }
    const value = request.params.value;
    const result = [...items, value];
    return makeTrace(request, "队列入队演示", initialText, `队首 ${result.join(" → ")} 队尾`, [
      makeStep(1, "init", "初始状态", `当前队列为 ${initialText}。`, queueState(items, capacity)),
      makeStep(2, "check", "检查队列容量", "当前队列未满，可以入队。", queueState(items, capacity), [action("check_condition", "检查队列是否已满。", { target: "rear" })]),
      makeStep(3, "move", "rear 后移", `rear 移动到位置 ${items.length}。`, queueState(items, capacity), [action("move", "rear 指向新的队尾位置。", { target: "rear", value: items.length })], { ...emptyHighlights(), pointers: [{ role: "changed", name: "rear" }] }),
      makeStep(4, "enqueue", "元素进入队尾", `${String(value)} 从队尾进入队列。`, queueState(result, capacity), [action("enqueue", "在队尾写入新元素。", { target: "rear", value })], { ...emptyHighlights(), cells: [{ role: "new", index: result.length - 1 }] }),
      makeStep(5, "done", "入队完成", `结果为队首 ${result.join(" → ")} 队尾。`, queueState(result, capacity))
    ]);
  }

  if (!items.length) {
    return makeRuntimeError(request, "队列出队演示", "QUEUE_UNDERFLOW", "当前队列为空，不能执行出队。", queueState(items, capacity), initialText);
  }
  const removed = items[0];
  const result = items.slice(1);
  const resultText = result.length ? `队首 ${result.join(" → ")} 队尾` : "空队列";
  return makeTrace(request, "队列出队演示", initialText, resultText, [
    makeStep(1, "init", "初始状态", `当前队列为 ${initialText}。`, queueState(items, capacity)),
    makeStep(2, "check", "检查队列是否为空", "当前队列非空，可以出队。", queueState(items, capacity), [action("check_condition", "检查队列是否为空。", { target: "front" })]),
    makeStep(3, "read", "读取队首元素", `读取即将出队的元素 ${String(removed)}。`, queueState(items, capacity), [action("assign", "读取队首元素。", { target: "value", value: removed })], { ...emptyHighlights(), cells: [{ role: "target", index: 0 }] }),
    makeStep(4, "dequeue", "队首元素离开", `${String(removed)} 从队首离开，其余元素依次成为新的队列。`, queueState(result, capacity), [action("dequeue", "移除队首元素。", { target: "front", value: removed }), action("move", "front 指向新的队首。", { target: "front", value: result.length ? 0 : -1 })], { ...emptyHighlights(), pointers: [{ role: "changed", name: "front" }] }),
    makeStep(5, "done", "出队完成", `结果为${resultText === "空队列" ? "空队列" : ` ${resultText}`}。`, queueState(result, capacity))
  ]);
}

function sequentialListMergeState(left, right, result, i, j, selected = null, comparison = "", capacity = null) {
  return {
    kind: "sequential_list_merge",
    left: [...left],
    right: [...right],
    result: [...result],
    i,
    j,
    k: result.length,
    selected,
    comparison,
    metadata: { left_length: left.length, right_length: right.length, ...(capacity === null ? {} : { capacity }) }
  };
}


function sequentialListState(items, capacity, details = {}) {
  return {
    kind: "sequential_list",
    items: items.map((value, index) => ({ index, value })),
    length: Number.isInteger(details.length) ? details.length : items.length,
    position: Number.isInteger(details.position) ? details.position : -1,
    targetIndex: Number.isInteger(details.targetIndex) ? details.targetIndex : -1,
    movingIndex: Number.isInteger(details.movingIndex) ? details.movingIndex : -1,
    value: details.value ?? null,
    operation: String(details.operation || ""),
    metadata: { capacity }
  };
}

function simulateSequentialListEdit(request) {
  const items = [...request.initial_state.data];
  const capacity = request.initial_state.metadata.capacity;
  const position = request.params.position;
  const targetIndex = position - 1;
  const operation = request.operation;
  const initialText = `L=[${items.join(", ")}]，length=${items.length}`;

  if (operation === "insert") {
    const value = request.params.value;
    const initialState = sequentialListState(items, capacity, {
      length: items.length,
      position,
      targetIndex,
      value,
      operation
    });
    if (items.length >= capacity) {
      return makeRuntimeError(
        request,
        "顺序表插入演示",
        "LIST_OVERFLOW",
        "当前顺序表已满，不能继续插入。",
        initialState,
        initialText
      );
    }

    const steps = [
      makeStep(
        1,
        "init",
        "初始顺序表",
        `当前 ${initialText}，准备在第 ${position} 个位置插入 ${String(value)}。`,
        initialState
      ),
      makeStep(
        2,
        "check",
        "检查插入位置与容量",
        `第 ${position} 个位置合法，且顺序表仍有空余容量。`,
        initialState,
        [action("check_condition", "检查位置范围与 length < capacity。", { target: "position,capacity" })],
        { ...emptyHighlights(), cells: [{ role: "target", index: targetIndex }] }
      )
    ];

    const work = [...items, items.length ? items[items.length - 1] : value];
    let stepId = 3;
    for (let from = items.length - 1; from >= targetIndex; from -= 1) {
      work[from + 1] = work[from];
      steps.push(makeStep(
        stepId++,
        "shift",
        "元素后移",
        `将 L[${from}] 的 ${String(work[from])} 后移到 L[${from + 1}]。`,
        sequentialListState(work, capacity, {
          length: items.length,
          position,
          targetIndex,
          movingIndex: from + 1,
          value,
          operation
        }),
        [action("move", "为插入位置腾出空间。", { from: `L[${from}]`, to: `L[${from + 1}]`, value: work[from] })],
        { ...emptyHighlights(), cells: [{ role: "source", index: from }, { role: "moved", index: from + 1 }] }
      ));
    }

    work[targetIndex] = value;
    steps.push(makeStep(
      stepId++,
      "write",
      "写入新元素",
      `把 ${String(value)} 写入第 ${position} 个位置 L[${targetIndex}]。`,
      sequentialListState(work, capacity, {
        length: items.length + 1,
        position,
        targetIndex,
        movingIndex: targetIndex,
        value,
        operation
      }),
      [action("insert", "在目标位置写入新元素。", { target: `L[${targetIndex}]`, value })],
      { ...emptyHighlights(), cells: [{ role: "new", index: targetIndex }] }
    ));
    steps.push(makeStep(
      stepId,
      "done",
      "插入完成",
      `表长加 1，得到 L=[${work.join(", ")}]，length=${items.length + 1}。`,
      sequentialListState(work, capacity, {
        length: items.length + 1,
        position,
        targetIndex,
        value,
        operation
      }),
      [action("assign", "length = length + 1。", { target: "length", value: items.length + 1 })]
    ));
    return makeTrace(
      request,
      "顺序表插入演示",
      initialText,
      `L=[${work.join(", ")}]，length=${items.length + 1}`,
      steps
    );
  }

  const removed = items[targetIndex];
  const initialState = sequentialListState(items, capacity, {
    length: items.length,
    position,
    targetIndex,
    value: removed,
    operation
  });
  const steps = [
    makeStep(
      1,
      "init",
      "初始顺序表",
      `当前 ${initialText}，准备删除第 ${position} 个位置的元素 ${String(removed)}。`,
      initialState
    ),
    makeStep(
      2,
      "target",
      "定位删除元素",
      `目标是 L[${targetIndex}] = ${String(removed)}。`,
      initialState,
      [action("check_condition", "确认删除位置合法。", { target: `L[${targetIndex}]` })],
      { ...emptyHighlights(), cells: [{ role: "target", index: targetIndex }] }
    )
  ];

  const work = [...items];
  let stepId = 3;
  for (let from = targetIndex + 1; from < items.length; from += 1) {
    work[from - 1] = work[from];
    steps.push(makeStep(
      stepId++,
      "shift",
      "元素前移",
      `将 L[${from}] 的 ${String(work[from])} 前移到 L[${from - 1}]。`,
      sequentialListState(work, capacity, {
        length: items.length,
        position,
        targetIndex,
        movingIndex: from - 1,
        value: removed,
        operation
      }),
      [action("move", "覆盖前一个位置，填补删除产生的空位。", { from: `L[${from}]`, to: `L[${from - 1}]`, value: work[from] })],
      { ...emptyHighlights(), cells: [{ role: "source", index: from }, { role: "moved", index: from - 1 }] }
    ));
  }

  const result = work.slice(0, -1);
  steps.push(makeStep(
    stepId,
    "done",
    "删除完成",
    `后续元素前移完成，表长减 1，得到 L=[${result.join(", ")}]，length=${result.length}。`,
    sequentialListState(result, capacity, {
      length: result.length,
      position,
      targetIndex: Math.min(targetIndex, Math.max(0, result.length - 1)),
      value: removed,
      operation
    }),
    [action("delete", "删除目标元素并令 length = length - 1。", { target: `L[${targetIndex}]`, value: removed })]
  ));
  return makeTrace(
    request,
    "顺序表删除演示",
    initialText,
    `L=[${result.join(", ")}]，length=${result.length}`,
    steps
  );
}

function simulateSequentialListMerge(request) {
  const [left, right] = request.initial_state.data.map((items) => [...items]);
  const capacity = request.initial_state.metadata.capacity;
  const mergeState = (l, r, res, i2, j2, selected = null, comparison = "") => sequentialListMergeState(l, r, res, i2, j2, selected, comparison, capacity);
  const initialText = `LA=[${left.join(", ")}], LB=[${right.join(", ")}]`;
  const initialState = mergeState(left, right, [], 0, 0);
  if (left.length + right.length > capacity) {
    return makeRuntimeError(request, "顺序表有序合并", "RESULT_OVERFLOW", "LC 容量不足，无法容纳两个顺序表的全部元素。", initialState, initialText);
  }

  const result = [];
  const steps = [makeStep(1, "init", "准备三个顺序表", "i、j 分别指向 LA、LB 的首元素，k 指向 LC 的待写位置。", initialState)];
  let i = 0;
  let j = 0;
  let stepId = 2;
  while (i < left.length && j < right.length) {
    const takeLeft = left[i] <= right[j];
    const selected = takeLeft
      ? { source: "LA", index: i, value: left[i] }
      : { source: "LB", index: j, value: right[j] };
    const comparison = `${left[i]} ${takeLeft ? "≤" : ">"} ${right[j]}`;
    result.push(selected.value);
    if (takeLeft) i += 1;
    else j += 1;
    steps.push(makeStep(
      stepId++,
      "merge",
      `比较并取 ${selected.source}`,
      `${comparison}，把 ${selected.value} 写入 LC[${result.length - 1}]，${selected.source === "LA" ? "i" : "j"} 后移。`,
      mergeState(left, right, result, i, j, selected, comparison),
      [
        action("compare", `比较 LA[${Math.max(0, i - (takeLeft ? 1 : 0))}] 与 LB[${Math.max(0, j - (takeLeft ? 0 : 1))}]`, { target: "i,j" }),
        action("copy", `从 ${selected.source} 复制到 LC`, { from: `${selected.source}[${selected.index}]`, to: `LC[${result.length - 1}]`, value: selected.value })
      ],
      { ...emptyHighlights(), cells: [{ role: "source", list: selected.source, index: selected.index }, { role: "new", list: "LC", index: result.length - 1 }], pointers: [{ role: "current", name: "i", index: i }, { role: "current", name: "j", index: j }] }
    ));
  }
  while (i < left.length) {
    const selected = { source: "LA", index: i, value: left[i] };
    result.push(left[i]);
    i += 1;
    steps.push(makeStep(stepId++, "remainder", "追加 LA 剩余元素", `LB 已扫描完，把 ${selected.value} 追加到 LC。`, mergeState(left, right, result, i, j, selected, "LB 已结束"), [action("copy", "复制 LA 剩余元素", { from: `LA[${selected.index}]`, to: `LC[${result.length - 1}]`, value: selected.value })]));
  }
  while (j < right.length) {
    const selected = { source: "LB", index: j, value: right[j] };
    result.push(right[j]);
    j += 1;
    steps.push(makeStep(stepId++, "remainder", "追加 LB 剩余元素", `LA 已扫描完，把 ${selected.value} 追加到 LC。`, mergeState(left, right, result, i, j, selected, "LA 已结束"), [action("copy", "复制 LB 剩余元素", { from: `LB[${selected.index}]`, to: `LC[${result.length - 1}]`, value: selected.value })]));
  }
  steps.push(makeStep(stepId, "done", "合并完成", `LC=[${result.join(", ")}]，两个输入表均已扫描完成。`, mergeState(left, right, result, i, j, null, "完成")));
  return makeTrace(request, "顺序表双指针合并", initialText, `LC=[${result.join(", ")}]`, steps);
}

function simulateOperation(input) {
  const request = normalizeOperationRequest(input);
  if (request.structure === "stack" && CORE_SUPPORTED_DEMO_PAIRS.stack.has(request.operation)) return simulateStack(request);
  if (request.structure === "queue" && CORE_SUPPORTED_DEMO_PAIRS.queue.has(request.operation)) return simulateQueue(request);
  if (request.structure === "sequential_list" && request.operation === "merge") return simulateSequentialListMerge(request);
  if (request.structure === "sequential_list" && CORE_SUPPORTED_DEMO_PAIRS.sequential_list.has(request.operation)) return simulateSequentialListEdit(request);
  return simulateTextbookOperation(request, {
    makeStep,
    makeTrace,
    action,
    emptyHighlights,
    makeRuntimeError
  });
}

function stateValues(state) {
  if (Array.isArray(state?.view)) return textbookStateValues(state);
  if (state?.kind === "sequential_list") {
    return [
      { role: "L", values: Array.isArray(state.items) ? state.items.map((item) => item?.value) : [] },
      {
        role: "meta",
        length: Number(state.length) || 0,
        capacity: Number(state?.metadata?.capacity) || 0,
        position: Number(state.position) || 0,
        targetIndex: Number.isInteger(state.targetIndex) ? state.targetIndex : -1,
        movingIndex: Number.isInteger(state.movingIndex) ? state.movingIndex : -1,
        value: state.value ?? null,
        operation: String(state.operation || "")
      }
    ];
  }
  if (state?.kind === "sequential_list_merge") {
    return [
      { role: "LA", values: [...(state.left || [])] },
      { role: "LB", values: [...(state.right || [])] },
      { role: "LC", values: [...(state.result || [])] },
      { role: "pointers", i: state.i, j: state.j, k: state.k, selected: state.selected, comparison: state.comparison }
    ];
  }
  return Array.isArray(state?.items) ? state.items.map((item) => item?.value) : [];
}

function traceToPlayerData(trace) {
  if (!trace || typeof trace !== "object" || !Array.isArray(trace.steps) || !trace.steps.length) {
    throw new DsvpValidationError("INVALID_TRACE", "VisualizationTrace 缺少可播放步骤", "trace.steps");
  }
  const firstState = trace.steps[0].state || {};
  const initial = stateValues(firstState);
  const steps = trace.steps.slice(1).map((step) => {
    const primaryAction = Array.isArray(step.actions) && step.actions.length ? step.actions[0] : null;
    return {
      op: primaryAction?.type || "inspect",
      label: String(step.title || `步骤 ${step.step_id}`),
      note: String(step.description || step.message || ""),
      stateSnapshot: stateValues(step.state),
      dsvpPhase: String(step.phase || ""),
      dsvpState: step.state || {},
      dsvpActions: Array.isArray(step.actions) ? step.actions : [],
      dsvpHighlights: step.highlights || emptyHighlights()
    };
  });
  return {
    animation: true,
    protocol: "dsvp/1",
    traceId: trace.trace_id,
    type: trace.structure,
    operation: String(trace.operation || ""),
    title: trace.title,
    description: trace.errors?.length
      ? trace.errors[0].message
      : `${trace.summary.initial} → ${trace.summary.result}`,
    initial,
    steps: steps.length ? steps : [{ op: "inspect", label: "查看结果", note: trace.summary.result, stateSnapshot: initial }],
    dsvpTrace: trace
  };
}

module.exports = {
  DSVP_VERSION,
  SUPPORTED_DEMO_PAIRS,
  DsvpValidationError,
  normalizeOperationRequest,
  simulateOperation,
  traceToPlayerData
};
