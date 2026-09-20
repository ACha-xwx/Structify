#!/usr/bin/env node
/**
 * Local DSVP service — the "本地脚本" half of the animation labour split.
 *
 * The large model only ever produces an *intent* (which capability, which arguments). Every frame of
 * every trace is computed here, deterministically, by the simulators in ./engine. This process is a
 * line-delimited JSON service: one request per line on stdin, one response per line on stdout, so the
 * Java backend can hold a single long-lived child instead of paying Node startup per animation.
 *
 * Requests:
 *   {"id":1,"op":"health"}
 *   {"id":2,"op":"simulate","request":{DSVP request}}
 *   {"id":3,"op":"capabilities","lessonId":"08-02"}            -> chapter-scoped list + prompt text
 *   {"id":4,"op":"resolve","intent":{...},"options":{...}}     -> validated DSVP request from model intent
 *
 * Responses: {"id":<same>,"ok":true,...} or {"id":<same>,"ok":false,"error":{"code","message"}}
 */
"use strict";

const path = require("node:path");
const readline = require("node:readline");

const engineDir = path.join(__dirname, "engine");
const { simulateOperation, traceToPlayerData, normalizeOperationRequest } = require(path.join(engineDir, "dsvp-engine"));
const { ANIMATION_CAPABILITY_REGISTRY, animationCapabilityList, animationCapabilityPrompt, resolveVisualizationIntent } =
  require(path.join(engineDir, "animation-capabilities"));

const MAX_LINE = 512 * 1024;

function failure(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

/** Simulate one request and return both the raw DSVP trace and the flattened player payload. */
function simulate(raw) {
  const request = normalizeOperationRequest(raw);
  const trace = simulateOperation(request);
  return { request, trace, player: traceToPlayerData(trace) };
}

function capabilities(lessonId) {
  return {
    lessonId: lessonId || null,
    total: Object.keys(ANIMATION_CAPABILITY_REGISTRY).length,
    capabilities: animationCapabilityList().map((item) => ({
      ...item,
      demoArguments: ANIMATION_CAPABILITY_REGISTRY[item.capability]?.demoArguments ?? null
    })),
    prompt: animationCapabilityPrompt({ lessonId })
  };
}

function resolve(intent, options) {
  const resolution = resolveVisualizationIntent(intent, options || {});
  return {
    status: resolution.status,
    capability: resolution.capability || null,
    missingArguments: resolution.missingArguments || [],
    error: resolution.error || null,
    toolRequest: resolution.toolRequest || null
  };
}

function handle(message) {
  const op = String(message?.op || "").trim();
  if (op === "health") {
    return { engine: "dsvp-local", capabilities: Object.keys(ANIMATION_CAPABILITY_REGISTRY).length, node: process.version };
  }
  if (op === "simulate") {
    if (!message.request || typeof message.request !== "object") throw failure("INVALID_REQUEST", "simulate 需要 request 对象");
    return simulate(message.request);
  }
  if (op === "capabilities") return capabilities(message.lessonId);
  if (op === "resolve") return resolve(message.intent, message.options);
  throw failure("UNKNOWN_OPERATION", `不支持的操作：${op || "(空)"}`);
}

const input = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
input.on("line", (line) => {
  const text = line.trim();
  if (!text) return;
  let id = null;
  let response;
  try {
    if (text.length > MAX_LINE) throw failure("REQUEST_TOO_LARGE", "请求过大");
    const message = JSON.parse(text);
    id = message?.id ?? null;
    response = { id, ok: true, ...handle(message) };
  } catch (error) {
    response = {
      id,
      ok: false,
      error: {
        code: error?.code || (error?.name === "DsvpValidationError" ? error.code : "INTERNAL_ERROR") || "INTERNAL_ERROR",
        message: error?.message || String(error),
        detail: error?.detail || null
      }
    };
  }
  process.stdout.write(`${JSON.stringify(response)}\n`);
});

input.on("close", () => process.exit(0));
process.on("uncaughtException", (error) => {
  process.stdout.write(`${JSON.stringify({ id: null, ok: false, error: { code: "INTERNAL_ERROR", message: error.message } })}\n`);
});
