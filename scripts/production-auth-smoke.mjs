#!/usr/bin/env node
/**
 * Anonymous per-route authorization smoke against a deployed environment.
 *
 * `docs/project/backend-api-matrix.md` states each route's authorization rule, but the matrix is a
 * promise about source code - nothing checked that the deployed proxy actually enforces it. This
 * script walks the same routes with **no credentials at all** and asserts the deployed answer.
 *
 * Two rules keep it safe to point at production:
 *   1. Protected checks send a body that is either invalid on purpose, or - for the routes where the
 *      handler refuses rather than the filter chain - well formed, because validation runs first and
 *      a malformed body would never reach the refusal. Those routes are also why the script asserts
 *      the documented error code and not just the status: a status alone cannot tell a filter
 *      refusal from a handler refusal. If one of them ever stopped refusing, the cost is a single
 *      model call - which is the incident this check exists to catch.
 *   2. Public routes are read-only, so nothing here mutates state or burns a sandbox slot.
 *
 * Usage:
 *   node scripts/production-auth-smoke.mjs                                  # production defaults
 *   SMOKE_SITE=http://127.0.0.1:8792 SMOKE_ADMIN_SITE=... node scripts/...
 *
 * Exit code is 0 only when every expectation held.
 */

const SITE = (process.env.SMOKE_SITE || "https://structify.cn").replace(/\/$/, "");
const ADMIN_SITE = (process.env.SMOKE_ADMIN_SITE || "https://admin.structify.cn").replace(/\/$/, "");
const UA = "structify-auth-smoke/1.0 (+ops; anonymous authorization check)";
const PER_REQUEST_TIMEOUT_MS = Number(process.env.SMOKE_TIMEOUT_MS || 15000);
/** Optional real account, used only for the checks that cannot be answered anonymously. */
const AUTH_TOKEN = process.env.SMOKE_TOKEN || "";

const checks = [];

/**
 * A protected route: the only acceptable answer to an anonymous caller is the listed refusal.
 * `body` is deliberately invalid so a misconfigured deployment yields a validation error instead of
 * performing the action.
 */
function protectedRoute(note, method, path, statuses = [401], body) {
  checks.push({ group: "protected", note, method, path, base: "site", statuses, body });
}

/**
 * A protected route that is reachable for anonymous callers - the handler decides, not the filter
 * chain - so the request has to be well formed to get past validation, and the refusal has to be
 * checked on its code rather than on the authorisation rule alone.
 */
function handlerGuardedRoute(note, method, path, statuses, body, accept, code) {
  checks.push({
    group: "protected",
    note,
    method,
    path,
    base: "site",
    statuses,
    body,
    headers: accept ? { accept } : {},
    verify: (result) => {
      const parsed = parseJson(result);
      return parsed?.code === code ? null : `expected code=${code}, got ${parsed?.code}`;
    }
  });
}

/** A public route: anonymous callers must genuinely be served. */
function publicRoute(note, method, path, statuses = [200], body, verify) {
  checks.push({ group: "public", note, method, path, base: "site", statuses, body, verify });
}

/** Node legacy `/api/*` routes. Anonymous behaviour differs per route, so it is spelled out. */
function legacyRoute(note, method, path, statuses, body, verify) {
  checks.push({ group: "legacy", note, method, path, base: "site", statuses, body, verify });
}

/** Requests aimed at the admin origin, where the same Spring routes are proxied under its domain. */
function adminOriginRoute(note, method, path, statuses = [401], body, verify) {
  checks.push({ group: "admin-origin", note, method, path, base: "admin", statuses, body, verify });
}

// ---------------------------------------------------------------------------------------------
// Spring /api/v1 - the primary backend. Everything outside chapters/resources/knowledge/readiness
// requires an active account, so an anonymous caller must be told 401 AUTH_REQUIRED.
// ---------------------------------------------------------------------------------------------
protectedRoute("current user", "GET", "/api/v1/users/me");
protectedRoute("chat history list", "GET", "/api/v1/chat/sessions");
protectedRoute("chat history item", "GET", "/api/v1/chat/sessions/1");
// These two are permitAll in the filter chain on purpose: the handler is what decides, so an
// anonymous caller only gets the refusal once the request is well formed. The documented code is
// asserted, not just the status - that is what tells the client to ask for a login.
handlerGuardedRoute("course Q&A", "POST", "/api/v1/chat", [401], { prompt: "什么是栈", chapterId: "03-stack-queue", history: [] },
  "application/json", "AI_QUOTA_AUTHENTICATION_REQUIRED");
handlerGuardedRoute("course Q&A stream", "POST", "/api/v1/chat/stream", [401], { prompt: "什么是栈", chapterId: "03-stack-queue", history: [] },
  "text/event-stream", "AI_QUOTA_AUTHENTICATION_REQUIRED");
// A streaming endpoint cannot render its error for a client that refuses to accept a stream; that
// used to answer 500 INTERNAL_ERROR, i.e. an anonymous caller could fake a server fault.
handlerGuardedRoute("course Q&A stream, client cannot accept SSE", "POST", "/api/v1/chat/stream", [406],
  { prompt: "什么是栈", chapterId: "03-stack-queue", history: [] }, "application/json", "NOT_ACCEPTABLE");
protectedRoute("classroom scripts", "GET", "/api/v1/classroom/scripts");
protectedRoute("classroom session create", "POST", "/api/v1/classroom/sessions", [401], {});
protectedRoute("classroom session read", "GET", "/api/v1/classroom/sessions/1");
protectedRoute("classroom action", "POST", "/api/v1/classroom/sessions/1/actions", [401], {});
protectedRoute("dsvp simulate", "POST", "/api/v1/animations/simulate", [401], {});
handlerGuardedRoute("animation generate", "POST", "/api/v1/animations/generate", [401], { prompt: "栈的入栈出栈" },
  "application/json", "AI_QUOTA_AUTHENTICATION_REQUIRED");
protectedRoute("animation observation", "POST", "/api/v1/animations/1/observations", [401], {});
protectedRoute("code analysis", "POST", "/api/v1/code/analyze", [401], {});
protectedRoute("learning progress", "GET", "/api/v1/learning/progress");
protectedRoute("learning event", "POST", "/api/v1/learning/events", [401], {});
protectedRoute("ai readiness", "GET", "/api/v1/ai/readiness");
protectedRoute("admin capabilities", "GET", "/api/v1/admin/capabilities");
protectedRoute("admin users", "GET", "/api/v1/admin/users");
protectedRoute("admin user detail", "GET", "/api/v1/admin/users/1");
protectedRoute("admin user status", "PATCH", "/api/v1/admin/users/1/status", [401], { status: "ACTIVE" });
protectedRoute("admin user roles", "PATCH", "/api/v1/admin/users/1/roles", [401], { roles: ["STUDENT"] });
protectedRoute("admin audit log", "GET", "/api/v1/admin/audit-events");
protectedRoute("admin review queue", "GET", "/api/v1/admin/reviews");
protectedRoute("admin review detail", "GET", "/api/v1/admin/reviews/RESOURCE/1");
protectedRoute("admin review status", "PATCH", "/api/v1/admin/reviews/RESOURCE/1/status", [401], { status: "PUBLISHED" });
protectedRoute("admin review history", "GET", "/api/v1/admin/reviews/RESOURCE/1/history");
protectedRoute("admin background tasks", "GET", "/api/v1/admin/background-tasks");
protectedRoute("admin background task detail", "GET", "/api/v1/admin/background-tasks/1");
protectedRoute("admin task recover", "POST", "/api/v1/admin/background-tasks/recover-timeouts");
// A nonexistent id keeps the "what if it were open" case at 404 instead of an actual retry.
protectedRoute("admin task retry", "POST", "/api/v1/admin/background-tasks/999999999/retry");
protectedRoute("admin task cancel", "POST", "/api/v1/admin/background-tasks/999999999/cancel");
protectedRoute("admin model config read", "GET", "/api/v1/admin/model-config");
protectedRoute("admin model config write", "PUT", "/api/v1/admin/model-config", [401], {});
protectedRoute("admin model connection test", "POST", "/api/v1/admin/model-config/test", [401], {});

// ---------------------------------------------------------------------------------------------
// Spring /api/v1 - public surface. These must answer a stranger.
// ---------------------------------------------------------------------------------------------
publicRoute("published chapters", "GET", "/api/v1/chapters", [200], undefined, (result) => {
  const body = parseJson(result);
  return Array.isArray(body) || Array.isArray(body?.items) || Array.isArray(body?.chapters)
    ? null
    : "expected a chapter collection";
});
/**
 * Reviewed knowledge retrieval answers a guest with an empty list on purpose: every textbook chunk
 * is CLASSROOM_ONLY and the guest audience may only read PUBLIC scope. That makes "did the index
 * actually load" unanswerable from the outside, so when a real token is supplied the same query must
 * come back with hits - which is the only way to tell a working index from an empty one.
 */
publicRoute("reviewed knowledge search", "GET", "/api/v1/knowledge/search?q=%E6%A0%88&limit=2", [200], undefined, (result) => {
  const body = parseJson(result);
  if (!body || typeof body !== "object") return "expected a json object";
  return Array.isArray(body.results) ? null : "expected results[]";
});

if (AUTH_TOKEN) {
  checks.push({
    group: "authenticated",
    note: "reviewed knowledge search finds textbook chunks",
    method: "GET",
    path: "/api/v1/knowledge/search?q=%E6%A0%88&limit=2",
    base: "site",
    statuses: [200],
    token: AUTH_TOKEN,
    verify: (result) => {
      const body = parseJson(result);
      if (!Array.isArray(body?.results)) return "expected results[]";
      return body.results.length > 0 ? null : "the index answered nothing for 栈 with a real account";
    }
  });
}
// Guest code execution is a documented contract, so the route must validate rather than 401. An
// unsupported language fails validation before any sandbox slot or quota is used.
legacyRoute("guest code run (canonical path)", "POST", "/api/v1/code/runs", [400], { language: "brainfuck", code: "" });
legacyRoute("guest code run (deprecated alias)", "POST", "/api/v1/code/run", [400], { language: "brainfuck", code: "" });

// ---------------------------------------------------------------------------------------------
// Node legacy /api/* - the compatibility layer. Bearer-only routes must refuse a stranger.
// ---------------------------------------------------------------------------------------------
legacyRoute("node health", "GET", "/healthz", [200]);
legacyRoute("node current user", "GET", "/api/auth/me", [401]);
legacyRoute("node conversations", "GET", "/api/conversations", [401]);
legacyRoute("node chat threads", "GET", "/api/chat-threads", [401]);
legacyRoute("node learning snapshot", "GET", "/api/learning-snapshot", [401]);
legacyRoute("node assignments", "GET", "/api/assignments", [401]);
legacyRoute("node teacher overview", "GET", "/api/teacher/overview", [401]);
legacyRoute("node presentation plan", "GET", "/api/classroom/presentation-plan?lessonId=nope", [401]);
// Access is decided before existence, so a missing page and a real page are indistinguishable to a
// stranger - otherwise the status alone would tell them which courseware pages exist.
legacyRoute("node presentation asset, missing path", "GET", "/presentation/nope", [401]);
// A path that normalises out of /presentation/ never reaches the asset handler at all, so it is
// answered by the router; that reveals nothing about the courseware either.
legacyRoute("node presentation asset, traversal path", "GET", "/presentation/%2e%2e/%2e%2e/etc/passwd", [404]);
legacyRoute("node pdf", "GET", "/pdfs/nope.pdf", [401]);
legacyRoute("node sandbox", "POST", "/api/execute", [401], { language: "c", code: "int main(void){return 0;}" }, (result) => {
  const body = parseJson(result);
  // The refusal has to be followable in the logs, so it must carry the same trace id as the header.
  if (body?.code !== "AUTH_REQUIRED") return `expected code=AUTH_REQUIRED, got ${body?.code}`;
  if (!body?.requestId) return "expected requestId in the refusal body";
  if (result.headers.get("x-request-id") !== body.requestId) return "requestId does not match X-Request-Id";
  return null;
});
legacyRoute("node generic upload", "POST", "/api/upload", [401]);
legacyRoute("node pdf upload", "POST", "/api/upload-pdf", [401, 403]);
legacyRoute("node animation preview", "POST", "/api/animation/simulate", [401], {});
// Production forces the debug knowledge route off, so it must not merely be unauthenticated - it
// must be absent.
legacyRoute("node debug knowledge route", "GET", "/api/knowledge/search?q=stack", [404]);
// Anonymous plain Q&A is an intentional legacy behaviour; an empty prompt must still be rejected,
// which proves the route is reachable without spending a model call.
legacyRoute("node guest chat validation", "POST", "/api/chat", [400], { prompt: "", mode: "qa" });

// ---------------------------------------------------------------------------------------------
// Admin origin - the same Spring routes behind the admin domain, plus the PATCH preflight that the
// admin console needs in order to change a user at all.
// ---------------------------------------------------------------------------------------------
adminOriginRoute("admin domain capabilities", "GET", "/api/v1/admin/capabilities", [401]);
adminOriginRoute("admin domain user list", "GET", "/api/v1/admin/users", [401]);

// ---------------------------------------------------------------------------------------------

function parseJson(result) {
  try {
    return JSON.parse(result.text);
  } catch {
    return null;
  }
}

async function call(check) {
  const base = check.base === "admin" ? ADMIN_SITE : SITE;
  const headers = {
    "user-agent": UA,
    accept: check.method === "GET" ? "application/json, text/plain, */*" : "application/json",
    ...(check.token ? { authorization: `Bearer ${check.token}` } : {}),
    ...(check.headers || {})
  };
  const init = { method: check.method, headers, redirect: "manual" };
  if (check.body !== undefined) {
    init.headers["content-type"] = "application/json";
    init.body = JSON.stringify(check.body);
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PER_REQUEST_TIMEOUT_MS);
  init.signal = controller.signal;
  let response;
  try {
    response = await fetch(`${base}${check.path}`, init);
  } catch (error) {
    clearTimeout(timer);
    return { status: 0, headers: new Headers(), text: "", error: error.message };
  }
  let text = "";
  try {
    text = await response.text();
  } catch {
    text = "";
  }
  clearTimeout(timer);
  return { status: response.status, headers: response.headers, text };
}

function describe(check) {
  return `${check.method} ${check.base === "admin" ? "admin-origin" : "site"}${check.path}`;
}

async function main() {
  const preflight = await fetch(`${ADMIN_SITE}/api/v1/admin/users/1/status`, {
    method: "OPTIONS",
    redirect: "manual",
    headers: {
      "user-agent": UA,
      origin: ADMIN_SITE,
      "access-control-request-method": "PATCH",
      "access-control-request-headers": "content-type,authorization"
    }
  }).catch((error) => ({ status: 0, headers: new Headers(), statusText: error.message }));

  let failures = 0;
  const rows = [];

  for (const check of checks) {
    const result = await call(check);
    let detail = "";
    let ok = check.statuses.includes(result.status);
    if (ok && check.verify) {
      const complaint = check.verify(result);
      if (complaint) {
        ok = false;
        detail = complaint;
      }
    }
    if (!ok && !detail) {
      detail = result.error ? result.error : `expected ${check.statuses.join("/")}`;
    }
    if (!ok) failures++;
    rows.push([ok ? "PASS" : "FAIL", describe(check), result.status, check.note, detail]);
  }

  const allowsPatch = (preflight.headers.get("access-control-allow-methods") || "").toUpperCase().includes("PATCH");
  const preflightOk = [200, 204].includes(preflight.status) && allowsPatch;
  if (!preflightOk) failures++;
  rows.push([
    preflightOk ? "PASS" : "FAIL",
    "OPTIONS admin-origin /api/v1/admin/users/1/status",
    preflight.status,
    "admin PATCH preflight",
    preflightOk ? "" : `allow-methods=${preflight.headers.get("access-control-allow-methods") || "none"}`
  ]);

  const width = Math.max(...rows.map((row) => row[1].length));
  for (const [verdict, target, status, note, detail] of rows) {
    console.log(`${verdict}  ${target.padEnd(width)}  ${String(status).padStart(3)}  ${note}${detail ? `  -- ${detail}` : ""}`);
  }

  const total = rows.length;
  console.log(`\nSUMMARY site=${SITE} admin=${ADMIN_SITE} checks=${total} failures=${failures}`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
