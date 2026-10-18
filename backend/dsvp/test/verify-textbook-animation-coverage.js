const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '..', 'engine');
const { ANIMATION_CAPABILITY_REGISTRY, resolveVisualizationIntent, animationCapabilityList } = require(path.join(root, 'animation-capabilities'));
const { simulateOperation, traceToPlayerData, SUPPORTED_DEMO_PAIRS } = require(path.join(root, 'dsvp-engine'));

const failures = [];
let totalSteps = 0;
for (const def of Object.values(ANIMATION_CAPABILITY_REGISTRY)) {
  const resolution = resolveVisualizationIntent({
    needed: true,
    confidence: 1,
    capability: def.capability,
    arguments: {},
    sourceChunkIds: []
  }, {
    allowDemoFallback: true,
    demoSourceRef: '教材覆盖自动测试'
  });
  if (resolution.status !== 'ready') {
    failures.push(`${def.capability}: resolver=${resolution.status} missing=${(resolution.missingArguments || []).join(',')} ${resolution.error || ''}`);
    continue;
  }
  try {
    const trace = simulateOperation(resolution.toolRequest.request);
    const player = traceToPlayerData(trace);
    assert.equal(trace.structure, def.structure, `${def.capability} structure`);
    assert.equal(trace.operation, def.operation, `${def.capability} operation`);
    assert.equal(player.protocol, 'dsvp/1');
    assert.ok(player.steps.length >= 1, `${def.capability} player steps`);
    assert.equal(player.type, def.structure);
    assert.equal(player.operation, def.operation);
    totalSteps += trace.steps.length;
  } catch (error) {
    failures.push(`${def.capability}: ${error.stack || error.message}`);
  }
}

assert.equal(failures.length, 0, `教材动画存在不可执行能力：\n${failures.join('\n')}`);
const listed = animationCapabilityList();
assert.equal(listed.length, Object.keys(ANIMATION_CAPABILITY_REGISTRY).length);
assert.ok(listed.length >= 80, `教材能力数量异常：${listed.length}`);
for (const def of Object.values(ANIMATION_CAPABILITY_REGISTRY)) {
  assert.ok(SUPPORTED_DEMO_PAIRS[def.structure]?.has(def.operation), `${def.capability} 未进入 DSVP 支持表`);
}
/* 空槽的四种写法必须等价：层次序列里的 `null` / `undefined` / 空串 / 纯空白都表示"这个位置没有结点"。
   2026-10-07 Rrd 截图：`["A",…,"G",""]` 让树上多出一个**空心结点框**（D 的左孩子），
   遍历还多"访问"它一次——「已访问」面板第一格是空的。渲染器本来就把空值画成空槽，
   引擎这边也必须同样处理，否则输入里一个手滑的空串就能把整条动画画歪。 */
for (const [blank, label] of [[null, "null"], [undefined, "undefined"], ["", "空串"], ["  ", "纯空白"]]) {
  const blankTrace = simulateOperation({
    structure: "tree",
    operation: "postorder",
    initial_state: { data: ["A", "B", "C", "D", "E", "F", "G", blank] },
    params: {}
  });
  const blankPlayer = traceToPlayerData(blankTrace);
  const firstView = blankPlayer.steps[0].dsvpState.view;
  assert.equal(
    firstView.find((row) => row.role === "tree").nodes.length, 7,
    `层次序列末尾是${label}时树上有 7 个结点、不该多出幻影结点`
  );
  assert.equal(
    blankPlayer.steps[0].dsvpState.view.find((row) => row.role === "visited").values.join(","), "D",
    `层次序列末尾是${label}时第一步只访问 D（不该先访问一个空结点）`
  );
  // 起点帧要看 `player.initial`（`traceToPlayerData` 把 trace 的第 0 帧摘出来当起点，不在 steps 里）。
  const initialVisited = (Array.isArray(blankPlayer.initial) ? blankPlayer.initial : []).find((row) => row.role === "visited");
  assert.equal(
    ((initialVisited && initialVisited.values) || []).join(","), "",
    `层次序列末尾是${label}时起点还没访问任何结点`
  );
  const lastVisited = blankPlayer.steps[blankPlayer.steps.length - 1].dsvpState.view.find((row) => row.role === "visited");
  assert.equal(
    lastVisited.values.join(","), "D,E,B,F,G,C,A",
    `层次序列末尾是${label}时后序访问序列不该混进空结点`
  );
}

/* 数值数组里的空串**不能悄悄变成 0**：`Number("") === 0`，输入框里多打一个逗号
   （`49,38,,97`）就会凭空多出一个 0，排序/图的动画会莫名其妙多一格。按非法元素报错才对。 */
assert.throws(
  () => simulateOperation({
    structure: "sort",
    operation: "bubble",
    initial_state: { data: [49, 38, "", 97] },
    params: {}
  }),
  /不是有效数字/,
  "数值数组里的空串必须显式报错，不能静默变成 0"
);

/* 结点/顶点**必须有名字**：空标签画出来就是一个没有名字的空框/空圈，和没写一样。
   树那边是"当成没有这个孩子"，图和森林这种"结点由列表给出"的结构则直接报错更清楚。 */
assert.throws(
  () => simulateOperation({
    structure: "graph",
    operation: "dfs",
    initial_state: { data: { nodes: ["A", "B", ""], edges: [["A", "B", 1]] } },
    params: { start: "A" }
  }),
  /顶点标签不能为空/,
  "图的顶点标签不能是空串"
);
assert.throws(
  () => simulateOperation({
    structure: "forest",
    operation: "to_binary_tree",
    params: { trees: [["A", "B"], [""]] }
  }),
  /没有名字/,
  "森林的每个结点都要有名字"
);

console.log(`Textbook animation coverage PASS: ${listed.length} capabilities, ${totalSteps} deterministic trace steps across canonical demos.`);
