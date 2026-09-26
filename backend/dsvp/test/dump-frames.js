#!/usr/bin/env node
// Prints the frames of a few named capabilities so the renderer is written against real shapes.
"use strict";

const path = require("node:path");
const fs = require("node:fs");
const engineDir = path.join(__dirname, "..", "engine");
const { simulateOperation, traceToPlayerData, normalizeOperationRequest } = require(path.join(engineDir, "dsvp-engine"));
const { ANIMATION_CAPABILITY_REGISTRY } = require(path.join(engineDir, "animation-capabilities"));

const wanted = process.argv.slice(2);
const out = [];

for (const name of wanted) {
  const def = ANIMATION_CAPABILITY_REGISTRY[name];
  if (!def) { out.push(`!! unknown capability ${name}`); continue; }
  const request = normalizeOperationRequest(buildRequest(def, JSON.parse(JSON.stringify(def.demoArguments || {}))));
  const player = traceToPlayerData(simulateOperation(request));
  out.push(`\n########## ${name}  steps=${player.steps.length} type=${player.type} title=${player.title}`);
  out.push(`initial=${JSON.stringify(player.initial)}`);
  player.steps.slice(0, 4).forEach((step, index) => {
    out.push(`--- step ${index} op=${step.op} label=${step.label} | ${step.note}`);
    out.push(`    state=${JSON.stringify(step.dsvpState)}`);
    out.push(`    highlights=${JSON.stringify(step.dsvpHighlights)}`);
  });
  const last = player.steps[player.steps.length - 1];
  out.push(`--- LAST op=${last.op} label=${last.label}`);
  out.push(`    state=${JSON.stringify(last.dsvpState)}`);
}

fs.writeFileSync(process.argv[1].replace(/dump-frames\.js$/, "frames.txt"), out.join("\n"), "utf8");

function buildRequest(def, args) {
  const sourceRef = "frame-dump";
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
