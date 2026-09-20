const assert = require('node:assert');
const {
  animationCapabilityList,
  normalizeVisualizationIntent,
  resolveVisualizationIntent
} = require('../engine/animation-capabilities');

const names = animationCapabilityList().map((item) => item.capability);
for (const required of [
  'stack.push', 'stack.pop', 'queue.enqueue', 'queue.dequeue',
  'sequential_list.insert', 'sequential_list.delete', 'sequential_list.merge',
  'linked_list.insert', 'tree.preorder', 'graph.dijkstra', 'sort.quick'
]) {
  assert.ok(names.includes(required), `missing capability ${required}`);
}
assert.ok(names.length >= 80, `expected textbook-wide registry, got ${names.length}`);

const push = resolveVisualizationIntent({
  needed: true,
  confidence: 0.95,
  capability: 'stack.push',
  purpose: '观察 top 与栈元素状态变化',
  arguments: { initialData: [2, 5], value: 7 }
}, { sourceRef: 'test' });
assert.strictEqual(push.status, 'ready');
assert.strictEqual(push.toolRequest.request.structure, 'stack');
assert.strictEqual(push.toolRequest.request.operation, 'push');
assert.deepStrictEqual(push.toolRequest.request.initial_state.data, [2, 5]);
assert.strictEqual(push.toolRequest.request.params.value, 7);

const popEmpty = resolveVisualizationIntent({
  needed: true,
  confidence: 0.95,
  capability: 'stack.pop',
  arguments: { initialData: [] }
});
// 显式提供的空数组视为“已提供”：不再当缺参触发演示回填，
// 而是交给引擎产出 STACK_UNDERFLOW 的明确错误轨迹。
assert.strictEqual(popEmpty.status, 'ready');
assert.strictEqual(popEmpty.toolRequest.demoFallback, false);
assert.deepStrictEqual(popEmpty.toolRequest.request.initial_state.data, []);

const popMissing = resolveVisualizationIntent({
  needed: true,
  confidence: 0.95,
  capability: 'stack.pop',
  arguments: {}
});
assert.strictEqual(popMissing.status, 'missing-arguments');
assert.deepStrictEqual(popMissing.missingArguments, ['initialData']);


const insert = resolveVisualizationIntent({
  needed: true,
  confidence: 0.99,
  capability: 'sequential_list.insert',
  arguments: { initialData: [10, 20, 30], position: 2, value: 15 }
});
assert.strictEqual(insert.status, 'ready');
assert.strictEqual(insert.toolRequest.request.operation, 'insert');
assert.strictEqual(insert.toolRequest.request.params.position, 2);
assert.strictEqual(insert.toolRequest.request.params.value, 15);

const deleteDemo = resolveVisualizationIntent({
  needed: true,
  confidence: 0.99,
  capability: 'sequential_list.delete',
  arguments: {}
}, { allowDemoFallback: true });
assert.strictEqual(deleteDemo.status, 'ready');
assert.strictEqual(deleteDemo.toolRequest.demoFallback, true);
assert.deepStrictEqual(deleteDemo.toolRequest.request.initial_state.data, [10, 20, 30, 40]);
assert.strictEqual(deleteDemo.toolRequest.request.params.position, 2);

const deletion = resolveVisualizationIntent({
  needed: true,
  confidence: 0.99,
  capability: 'sequential_list.delete',
  arguments: { initialData: [10, 20, 30, 40], position: 2 }
});
assert.strictEqual(deletion.status, 'ready');
assert.strictEqual(deletion.toolRequest.request.operation, 'delete');
assert.strictEqual(deletion.toolRequest.request.params.position, 2);

const merge = resolveVisualizationIntent({
  needed: true,
  confidence: 0.99,
  capability: 'sequential_list.merge',
  arguments: { left: [1, 3, 5], right: [2, 4, 6] }
});
assert.strictEqual(merge.status, 'ready');
assert.deepStrictEqual(merge.toolRequest.request.initial_state.data, [[1, 3, 5], [2, 4, 6]]);

const hallucinated = resolveVisualizationIntent({
  needed: true,
  confidence: 0.99,
  capability: 'tree.rotate',
  arguments: {}
});
assert.strictEqual(hallucinated.status, 'unsupported');

const lowConfidence = resolveVisualizationIntent({
  needed: true,
  confidence: 0.4,
  capability: 'stack.push',
  arguments: { value: 1 }
});
assert.strictEqual(lowConfidence.status, 'low-confidence');

const normalized = normalizeVisualizationIntent({
  recommended: true,
  confidence: 2,
  capability: 'queue.enqueue',
  arguments: { initialData: [1], value: 'x', capacity: 8 }
});
assert.strictEqual(normalized.needed, true);
assert.strictEqual(normalized.confidence, 1);
assert.strictEqual(normalized.arguments.value, 'x');
assert.strictEqual(normalized.arguments.capacity, 8);

console.log('animation capability router verification passed');
