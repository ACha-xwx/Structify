#!/usr/bin/env node
/**
 * Derives the DSVP contract's structure list and parameter vocabulary from the engine itself.
 *
 * The contract must describe what the engine can actually execute. Hand-maintaining a list of 167
 * capabilities is how a contract drifts, so the list is sampled from the registry and written to
 * stdout for the schema to be updated against. Read-only inspection tool.
 */
"use strict";

const path = require("node:path");
const engineDir = path.join(__dirname, "..", "engine");
const { normalizeOperationRequest } = require(path.join(engineDir, "dsvp-engine"));
const { ANIMATION_CAPABILITY_REGISTRY } = require(path.join(engineDir, "animation-capabilities"));

const structures = new Map();
const paramKeys = new Map();
const paramTypes = new Map();

for (const def of Object.values(ANIMATION_CAPABILITY_REGISTRY)) {
  if (!structures.has(def.structure)) structures.set(def.structure, new Set());
  structures.get(def.structure).add(def.operation);
  const request = normalizeOperationRequest(buildRequest(def, JSON.parse(JSON.stringify(def.demoArguments || {}))));
  for (const [key, value] of Object.entries(request.params || {})) {
    if (!paramKeys.has(key)) paramKeys.set(key, 0);
    paramKeys.set(key, paramKeys.get(key) + 1);
    const type = Array.isArray(value) ? "array" : value === null ? "null" : typeof value;
    if (!paramTypes.has(key)) paramTypes.set(key, new Set());
    paramTypes.get(key).add(type);
  }
}

console.log(`structures=${structures.size}`);
for (const [structure, operations] of [...structures].sort()) {
  console.log(`  ${structure.padEnd(26)} ${[...operations].sort().join(",")}`);
}
console.log(`\nparam keys=${paramKeys.size}`);
for (const [key, count] of [...paramKeys].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))) {
  console.log(`  ${key.padEnd(20)} used=${String(count).padStart(3)}  types=${[...paramTypes.get(key)].sort().join("|")}`);
}

function buildRequest(def, args) {
  const sourceRef = "contract-sample";
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
