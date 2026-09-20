#!/usr/bin/env node
/**
 * Prints the distinct `dsvpState.kind` values and one sample snapshot per kind.
 *
 * The renderer is written against these shapes, so this file is the reference used when a new snapshot
 * shape shows up: run it, look at the sample, extend the renderer. It is a read-only inspection tool,
 * not part of the service.
 */
"use strict";

const path = require("node:path");
const engineDir = path.join(__dirname, "..", "engine");
const { simulateOperation, traceToPlayerData, normalizeOperationRequest } = require(path.join(engineDir, "dsvp-engine"));
const { ANIMATION_CAPABILITY_REGISTRY } = require(path.join(engineDir, "animation-capabilities"));

const only = process.argv[2] || null;
const samples = new Map();
const viewRoles = new Map();
let capabilityCount = 0;

for (const [name, def] of Object.entries(ANIMATION_CAPABILITY_REGISTRY)) {
  if (only && !name.includes(only)) continue;
  capabilityCount += 1;
  const args = JSON.parse(JSON.stringify(def.demoArguments || {}));
  const request = normalizeOperationRequest(buildRequest(def, args));
  const trace = simulateOperation(request);
  const player = traceToPlayerData(trace);
  for (const step of player.steps || []) {
    const kind = step.dsvpState?.kind || "(none)";
    if (!samples.has(kind)) samples.set(kind, { capability: name, step });
    for (const view of step.dsvpState?.view || []) {
      const role = view.role || "(none)";
      if (!viewRoles.has(role)) viewRoles.set(role, { capability: name, keys: Object.keys(view), sample: view });
    }
  }
}

console.log(`capabilities inspected: ${capabilityCount}`);
console.log(`distinct state kinds: ${samples.size}`);
for (const [kind, info] of [...samples].sort()) {
  console.log(`\n--- kind=${kind}  (from ${info.capability}) ---`);
  console.log(JSON.stringify(info.step.dsvpState ?? null, null, 1).slice(0, 1600));
}
console.log(`\n===== view roles (${viewRoles.size}) =====`);
for (const [role, info] of [...viewRoles].sort()) {
  console.log(`${role.padEnd(14)} keys=${info.keys.join(",")}  (from ${info.capability})`);
}
console.log("\n===== highlights / actions sample =====");
for (const [kind, info] of [...samples].sort().slice(0, 6)) {
  console.log(`kind=${kind} highlights=${JSON.stringify(info.step.dsvpHighlights)} actions=${JSON.stringify(info.step.dsvpActions)}`);
}

// Mirrors the argument->request mapping in animation-capabilities.js so demos can be replayed here.
function buildRequest(def, args) {
  const sourceRef = "shape-dump";
  if (def.capability === "sequential_list.merge") {
    const left = args.left || [];
    const right = args.right || [];
    return { version: "1.0", structure: def.structure, operation: def.operation, params: { capacity: Math.min(100, Math.max(10, left.length + right.length + 2)) }, initial_state: { data: [left, right], metadata: {} }, options: { language: "c", explain_level: "beginner" }, source_ref: sourceRef };
  }
  if (["stack", "queue", "sequential_list"].includes(def.structure)) {
    const data = Array.isArray(args.initialData) ? args.initialData : [];
    const capacity = args.capacity || Math.min(100, Math.max(10, data.length + 2));
    const params = { capacity };
    if (["push", "enqueue", "insert"].includes(def.operation)) params.value = args.value;
    if (["insert", "delete"].includes(def.operation)) params.position = args.position;
    if (def.operation === "pop" || def.operation === "dequeue") {
      // no extra params
    }
    return { version: "1.0", structure: def.structure, operation: def.operation, params, initial_state: { data, metadata: { capacity } }, options: { language: "c", explain_level: "beginner" }, source_ref: sourceRef };
  }
  const initialData = args.initialData ?? args.parent ?? args.runs ?? [];
  const params = {};
  for (const [key, value] of Object.entries(args)) {
    if (["initialData", "parent", "runs"].includes(key)) continue;
    params[key] = value;
  }
  return { version: "1.0", structure: def.structure, operation: def.operation, params, initial_state: { data: initialData, metadata: {} }, options: { language: "c", explain_level: "beginner" }, source_ref: sourceRef };
}
