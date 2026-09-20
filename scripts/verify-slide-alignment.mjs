// Live check of step-level courseware alignment through the real classroom API.
//   STRUCTIFY_USERNAME=... STRUCTIFY_PASSWORD=... node scripts/verify-slide-alignment.mjs
import process from "node:process";

const baseUrl = (process.env.STRUCTIFY_BASE_URL ?? "http://127.0.0.1:8794").replace(/\/$/, "");
const username = process.env.STRUCTIFY_USERNAME;
const password = process.env.STRUCTIFY_PASSWORD;
const scriptId = process.env.STRUCTIFY_SLIDE_SCRIPT ?? "verify-slide-alignment";
if (!username || !password) {
  console.error("STRUCTIFY_USERNAME and STRUCTIFY_PASSWORD are required");
  process.exit(2);
}

let cookie = "";
async function api(method, pathname, body) {
  const response = await fetch(baseUrl + pathname, {
    method,
    headers: { "content-type": "application/json", ...(cookie ? { cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const setCookie = response.headers.getSetCookie?.() ?? [];
  if (setCookie.length) cookie = setCookie.map(value => value.split(";")[0]).join("; ");
  const text = await response.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* keep raw */ }
  return { status: response.status, json, text };
}

const login = await api("POST", "/api/v1/auth/login", { username, password });
if (login.status !== 200) {
  console.error("login failed", login.status, login.text.slice(0, 200));
  process.exit(2);
}
const session = await api("POST", "/api/v1/classroom/sessions", { scriptId });
if (session.status !== 200) {
  console.error("session failed", session.status, session.text.slice(0, 300));
  process.exit(2);
}
const id = session.json.id;
console.log(`session ${id} state=${session.json.state}`);
const failures = [];

function report(view, label) {
  const stage = view.stage ?? {};
  const match = stage.slideMatch ?? null;
  const refs = stage.slideRefs ?? [];
  console.log(`  ${label}: step=${stage.stepIndex} slide=${refs[0] ?? "-"} kind=${match?.kind ?? "-"} source=${match?.source ?? "-"} score=${(match?.score ?? 0).toFixed(3)} | ${(match?.slideTitle ?? "").slice(0, 30)}`);
  return match;
}

await api("POST", `/api/v1/classroom/sessions/${id}/actions`, { action: "CONTINUE", expectedRevision: 0 });
let view = await api("GET", `/api/v1/classroom/sessions/${id}`);
let kinds = [];
for (let step = 0; step < 4; step++) {
  const match = report(view.json, `step${step}`);
  if (!match) failures.push(`step${step} has no slide decision at all`);
  else kinds.push(match.kind);
  const next = await api("POST", `/api/v1/classroom/sessions/${id}/actions`, { action: "CONTINUE", expectedRevision: view.json.stage.revision });
  view = next.status === 200 ? next : view;
  if (next.status !== 200) break;
}

// A human decision must win over the automatic alignment, and must survive a reload.
const deck = await api("GET", `/api/v1/presentation/lessons/${encodeURIComponent(view.json.stage.lessonId)}/slides`);
const slides = deck.json?.slides ?? [];
console.log(`  lesson deck: ${slides.length} pages`);
if (slides.length < 3) failures.push("lesson has no usable courseware");
const currentStep = view.json.stage.stepIndex;
const target = slides.find(slide => slide.id !== view.json.stage.slideRefs?.[0]) ?? slides[0];
const pinned = await api("PUT", `/api/v1/classroom/sessions/${id}/slides`, { stepIndex: currentStep, slideId: target.id });
if (pinned.status !== 200) failures.push(`pin failed: ${pinned.status} ${pinned.text.slice(0, 120)}`);
else {
  const reload = await api("GET", `/api/v1/classroom/sessions/${id}`);
  const stage = reload.json.stage;
  const match = stage.slideMatch ?? {};
  console.log(`  pinned step${currentStep} -> ${stage.slideRefs?.[0]} source=${match.source} kind=${match.kind}`);
  if (stage.slideRefs?.[0] !== target.id || match.source !== "override") failures.push("pinned page was not applied");
}
const rejected = await api("PUT", `/api/v1/classroom/sessions/${id}/slides`, { stepIndex: currentStep, slideId: "not-a-slide-of-this-lesson" });
console.log(`  unrelated page rejected: ${rejected.status === 400 || rejected.status === 404}`);
if (rejected.status !== 400 && rejected.status !== 404) failures.push("an unrelated slide id was accepted");

console.log(`  kinds observed: ${kinds.join(",")}`);
if (failures.length) {
  console.log("\nFAILURES:");
  for (const failure of failures) console.log(`  - ${failure}`);
  process.exit(1);
}
console.log("\nOK");
