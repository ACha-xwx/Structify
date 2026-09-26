const assert = require("node:assert/strict");
const {
  DsvpValidationError,
  normalizeOperationRequest,
  simulateOperation,
  traceToPlayerData
} = require("../engine/dsvp-engine");

function request(structure, operation, data, value) {
  return {
    version: "1.0",
    structure,
    operation,
    params: value === undefined ? {} : { value },
    initial_state: { data, metadata: { capacity: 6 } },
    options: { language: "c", explain_level: "beginner" },
    source_ref: "textbook:test"
  };
}

const stackPush = simulateOperation(request("stack", "push", [2, 5], 7));
assert.equal(stackPush.structure, "stack");
assert.equal(stackPush.operation, "push");
assert.equal(stackPush.steps.length, 5);
assert.deepEqual(stackPush.steps.at(-1).state.items.map((item) => item.value), [2, 5, 7]);
assert.equal(stackPush.errors.length, 0);
assert.equal(stackPush.trace_id, simulateOperation(request("stack", "push", [2, 5], 7)).trace_id);

const stackPop = simulateOperation(request("stack", "pop", [2, 5]));
assert.deepEqual(stackPop.steps.at(-1).state.items.map((item) => item.value), [2]);

const queueEnqueue = simulateOperation(request("queue", "enqueue", [4, 7], 9));
assert.deepEqual(queueEnqueue.steps.at(-1).state.items.map((item) => item.value), [4, 7, 9]);

const queueDequeue = simulateOperation(request("queue", "dequeue", [4, 7]));
assert.deepEqual(queueDequeue.steps.at(-1).state.items.map((item) => item.value), [7]);

const mergeRequest = request("sequential_list", "merge", [[2, 2, 3], [1, 3, 3, 4]]);
mergeRequest.initial_state.metadata.capacity = 10;
const sequentialMerge = simulateOperation(mergeRequest);
assert.deepEqual(sequentialMerge.steps.at(-1).state.result, [1, 2, 2, 3, 3, 3, 4]);
assert.equal(sequentialMerge.steps[4].state.selected.source, "LA", "相等时应先取 LA");
const mergePlayer = traceToPlayerData(sequentialMerge);
assert.equal(mergePlayer.type, "sequential_list");
assert.deepEqual(mergePlayer.steps.at(-1).stateSnapshot[2].values, [1, 2, 2, 3, 3, 3, 4]);

const underflow = simulateOperation(request("stack", "pop", []));
assert.equal(underflow.errors[0].code, "STACK_UNDERFLOW");

const player = traceToPlayerData(stackPush);
assert.equal(player.protocol, "dsvp/1");
assert.deepEqual(player.initial, [2, 5]);
assert.deepEqual(player.steps.at(-1).stateSnapshot, [2, 5, 7]);

assert.throws(
  () => normalizeOperationRequest({ ...request("stack", "push", [], 1), unexpected: true }),
  (error) => error instanceof DsvpValidationError && error.code === "UNEXPECTED_FIELD"
);
const stackPeek = simulateOperation(request("stack", "peek", [2, 5, 7]));
assert.equal(stackPeek.operation, "peek");
assert.equal(stackPeek.steps.at(-1).state.view[1].peek, 7);
assert.throws(
  () => normalizeOperationRequest(request("stack", "rotate", [])),
  (error) => error instanceof DsvpValidationError && error.code === "UNSUPPORTED_OPERATION"
);

console.log("dsvp-engine-ok core-traces=6 extended-peek=1 strict-validation=2");
