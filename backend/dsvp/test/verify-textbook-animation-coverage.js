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
console.log(`Textbook animation coverage PASS: ${listed.length} capabilities, ${totalSteps} deterministic trace steps across canonical demos.`);
