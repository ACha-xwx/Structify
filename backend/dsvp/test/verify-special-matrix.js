const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '..', 'engine');
const { resolveVisualizationIntent } = require(path.join(root, 'animation-capabilities'));
const { simulateOperation, normalizeOperationRequest, traceToPlayerData } = require(path.join(root, 'dsvp-engine'));

/**
 * 特殊矩阵压缩存储的回归：这一条能力曾经只产出两帧空面板（values 全是 []），界面上是两个空框、
 * 播放器只有 1 步。现在每个下标都要能核对「矩阵格子里显示的值 == 压缩数组 B[k] 里的值」，并且
 * 播放器必须有真正的过程可播。
 *
 * 运行：node backend/dsvp/test/verify-special-matrix.js
 */

const CAPABILITY = 'special_matrix.compress_map';
const KINDS = ['lower_triangular', 'upper_triangular', 'symmetric', 'tridiagonal'];

function run(arguments_) {
  const resolution = resolveVisualizationIntent(
    { needed: true, confidence: 1, capability: CAPABILITY, arguments: arguments_ },
    { allowDemoFallback: false, minimumConfidence: 0 },
  );
  assert.equal(resolution.status, 'ready', `${CAPABILITY} ${JSON.stringify(arguments_)} resolver=${resolution.status} ${resolution.error || ''}`);
  const trace = simulateOperation(normalizeOperationRequest(resolution.toolRequest.request));
  return { trace, player: traceToPlayerData(trace) };
}

function panelOf(step, role) {
  return (step.state.view || []).find((panel) => panel.role === role);
}

const failures = [];
let checked = 0;
let playerStepTotal = 0;
for (const kind of KINDS) {
  for (let n = 2; n <= 8; n++) {
    for (let i = 1; i <= n; i++) {
      for (let j = 1; j <= n; j++) {
        const where = `${kind} n=${n} A[${i},${j}]`;
        try {
          const { trace, player } = run({ n, kind, i, j });
          checked += 1;
          playerStepTotal += player.steps.length;
          // 每一帧都要有真正的矩阵内容，而不是空面板。
          for (const step of trace.steps) {
            const matrix = panelOf(step, 'matrix');
            assert.ok(matrix, `${where} 帧 ${step.step_id} 没有矩阵面板`);
            assert.equal(matrix.values.length, n, `${where} 帧 ${step.step_id} 矩阵行数`);
            assert.ok(matrix.values.every((row) => row.length === n), `${where} 帧 ${step.step_id} 矩阵列数`);
            assert.deepEqual(matrix.focusCell, [i - 1, j - 1], `${where} 帧 ${step.step_id} focusCell`);
          }
          const last = trace.steps[trace.steps.length - 1];
          const matrix = panelOf(last, 'matrix');
          const packed = panelOf(last, 'compressed');
          assert.ok(packed, `${where} 末帧缺压缩数组面板`);
          const cell = matrix.values[i - 1][j - 1];
          const storedInArray = trace.summary.result.startsWith('B[');
          if (storedInArray) {
            // 教材公式算出来的 k，必须正好是画面上那一个格子；这也是「A 里的值 = B[k] 的值」的依据。
            assert.ok(Number.isInteger(packed.focusIndex) && packed.focusIndex >= 0 && packed.focusIndex < packed.values.length,
              `${where} focusIndex=${packed.focusIndex} 越出 B[0..${packed.values.length - 1}]`);
            assert.equal(packed.values[packed.focusIndex], cell, `${where} A[${i},${j}]=${cell} 与 B[${packed.focusIndex}]=${packed.values[packed.focusIndex]} 不一致`);
            assert.equal(trace.summary.result, `B[${packed.focusIndex}]=${cell}`, `${where} summary`);
          } else if (kind === 'tridiagonal') {
            assert.equal(cell, 0, `${where} 带外元素应为常量 0`);
            assert.equal(packed.focusIndex, null, `${where} 带外元素不该高亮压缩数组`);
            assert.equal(trace.summary.result, '不存储（带外为 0）', `${where} summary`);
          } else {
            assert.equal(cell, 0, `${where} 常量区应为常量 0`);
            assert.equal(packed.focusIndex, packed.values.length - 1, `${where} 常量位置`);
            assert.equal(packed.values[packed.focusIndex], 0, `${where} 常量位应为 0`);
          }
          // 空动画的判据：播放器只有 1 步就说明整段没有过程。
          assert.ok(player.steps.length >= 2, `${where} 播放器只有 ${player.steps.length} 步，没有过程可播`);
        } catch (error) {
          failures.push(`${where}: ${error.message}`);
        }
      }
    }
  }
}
assert.equal(failures.length, 0, `特殊矩阵压缩映射存在问题：\n${failures.slice(0, 12).join('\n')}`);

// 参数校验：kind 是自由文本，写错必须显式报错而不是悄悄当成下三角。
const invalid = [
  [{ n: 5, kind: 'foobar', i: 4, j: 2 }, 'INVALID_PARAM'],
  [{ n: 5, kind: 'lower_triangular', i: 0, j: 2 }, 'PARAM_OUT_OF_RANGE'],
  [{ n: 5, kind: 'lower_triangular', i: 2.5, j: 2 }, 'INVALID_PARAM'],
  [{ n: 40, kind: 'lower_triangular', i: 2, j: 1 }, 'PARAM_OUT_OF_RANGE'],
];
for (const [arguments_, code] of invalid) {
  try {
    run(arguments_);
    failures.push(`${JSON.stringify(arguments_)} 被接受了，应该报 ${code}`);
  } catch (error) {
    assert.equal(error.code, code, `${JSON.stringify(arguments_)} 报的是 ${error.code}：${error.message}`);
  }
}
assert.equal(failures.length, 0, failures.join('\n'));
// 大小写与连字符容错
assert.equal(run({ n: 5, kind: 'Lower-Triangular', i: 4, j: 2 }).trace.summary.result, 'B[7]=8');

console.log(`Special-matrix compression PASS: ${checked} index combinations across ${KINDS.length} kinds, ${playerStepTotal} playable steps, invalid kinds rejected with explicit errors.`);
