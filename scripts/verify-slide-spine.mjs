/**
 * Live check of the courseware spine through the real classroom API.
 *
 * Prepares a lesson, then walks the whole classroom and verifies that step n really teaches courseware
 * page n, and that textbook-only steps keep the page already on screen.
 *
 * The preparation path depends on {@code app.classroom.slide-narration}: with {@code deterministic}
 * the spine is assembled locally and nothing is billed, with {@code model} (the default) the whole
 * lesson is one model call, so this script needs a working model configuration and spends quota.
 * The wait below is sized for the model path. A lesson interleaves question steps with its pages, and a
 * question blocks the walk until the model grades the answer correct, so each one is answered from its own
 * reviewed textbook evidence and the retries are counted (grading spends quota).
 *
 * Page order is checked against the courseware folder the user ships
 * ({@code presentation-materials/lesson-presentation-plans.json}), not against the backend's own page
 * list: comparing a plan with itself would pass even if the classroom invented its own order. Override
 * the folder with STRUCTIFY_PRESENTATION_DIR.
 *
 *   STRUCTIFY_USERNAME=... STRUCTIFY_PASSWORD=... node scripts/verify-slide-spine.mjs [lesson substring]
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const presentationDir = process.env.STRUCTIFY_PRESENTATION_DIR ?? path.resolve(repoRoot, "../../presentation-materials");

/**
 * The order the shipped courseware puts its pages in, straight from the source of truth, mirroring the
 * backend rule: the deck claiming the most pages for this lesson (via {@code lessonIds} on each slide of
 * slides.json) is the deck that teaches it, and its pages are used in their own order. Lessons no deck
 * claims fall back to the scene-ordered plans, re-sorted by deck order then page number.
 */
function sourceCoursewareOrder(lesson) {
  const plansFile = path.join(presentationDir, "lesson-presentation-plans.json");
  const slidesFile = path.join(presentationDir, "slides.json");
  if (!fs.existsSync(plansFile) || !fs.existsSync(slidesFile)) return null;
  const key = path.basename(String(lesson.source ?? "")).slice(0, 5);
  if (!/^\d\d-\d\d$/.test(key)) return null;
  const plansRoot = JSON.parse(fs.readFileSync(plansFile, "utf8")).lessons ?? {};
  const subLessons = Object.keys(plansRoot).filter(name => name === key || name.startsWith(key)).sort();
  const slides = JSON.parse(fs.readFileSync(slidesFile, "utf8")).slides ?? [];
  const claimed = new Map();
  for (const slide of slides) {
    if (!(slide.lessonIds ?? []).some(id => subLessons.includes(id))) continue;
    if (!claimed.has(slide.deckId)) claimed.set(slide.deckId, []);
    claimed.get(slide.deckId).push(slide);
  }
  let order = [];
  if (claimed.size === 0) {
    const byId = new Map(slides.map(slide => [slide.id, slide]));
    const deckRank = new Map();
    let rank = 0;
    for (const slide of slides) if (!deckRank.has(slide.deckId)) deckRank.set(slide.deckId, rank++);
    const planned = [];
    for (const name of subLessons) {
      for (const id of plansRoot[name]?.slideOrder ?? []) if (!planned.includes(id)) planned.push(id);
    }
    order = planned.map(id => byId.get(id)).filter(Boolean)
      .sort((a, b) => (deckRank.get(a.deckId) - deckRank.get(b.deckId)) || (a.slideNumber - b.slideNumber))
      .map(slide => slide.id);
  } else {
    let primary = null;
    let best = -1;
    for (const [deckId, list] of claimed) {
      if (list.length > best) { best = list.length; primary = deckId; }
    }
    order = (claimed.get(primary) ?? []).slice()
      .sort((a, b) => a.slideNumber - b.slideNumber)
      .map(slide => slide.id);
  }
  return order.length ? { key, order } : null;
}

const baseUrl = (process.env.STRUCTIFY_BASE_URL ?? "http://127.0.0.1:8794").replace(/\/$/, "");
const username = process.env.STRUCTIFY_USERNAME;
const password = process.env.STRUCTIFY_PASSWORD;
const wanted = process.argv[2] ?? process.env.STRUCTIFY_LESSON ?? "08-02";
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

const failures = [];
const login = await api("POST", "/api/v1/auth/login", { username, password });
if (login.status !== 200) {
  console.error("login failed", login.status, login.text.slice(0, 200));
  process.exit(2);
}

const lessons = await api("GET", "/api/v1/classroom/lessons");
const lesson = (lessons.json ?? []).find(item => item.source?.includes(wanted) || item.title?.includes(wanted));
if (!lesson) {
  console.error(`no lesson matching "${wanted}"`);
  process.exit(2);
}
console.log(`lesson ${lesson.title} | ${lesson.pages}`);

const started = await api("POST", "/api/v1/classroom/preparations", { lessonId: lesson.id });
if (started.status !== 200) {
  console.error("prepare failed", started.status, started.text.slice(0, 300));
  process.exit(2);
}
let status = started.json;
for (let attempt = 0; attempt < 300 && status.state === "preparing"; attempt++) {
  await new Promise(resolve => setTimeout(resolve, 1000));
  status = (await api("GET", `/api/v1/classroom/preparations/${status.id}`)).json;
}
console.log(`prepare state=${status.state} phase=${status.phase ?? "-"} ${status.error ? "error=" + status.error : ""}`);
if (status.state !== "ready") {
  failures.push(`preparation did not finish: ${status.state} ${status.error ?? ""}`);
}

const sessionId = status.session?.id;
// Printed so a browser check can open the very classroom this run prepared.
console.log(`session ${sessionId}`);
const deck = (await api("GET", `/api/v1/presentation/lessons/${encodeURIComponent(lesson.id)}/slides`)).json?.slides ?? [];
console.log(`courseware pages: ${deck.length}`);

// The shipped courseware is the reference. When it is readable the classroom has to agree with it page
// by page; the backend's own list is only a fallback for lessons whose key cannot be resolved.
const expected = sourceCoursewareOrder(lesson);
const reference = expected?.order ?? deck.map(slide => slide.id);
if (expected) {
  console.log(`shipped courseware order: ${expected.key} | ${expected.order.length} pages`);
  if (expected.order.length !== deck.length) {
    failures.push(`backend lists ${deck.length} pages but the shipped courseware has ${expected.order.length}`);
  }
  for (let index = 0; index < Math.min(expected.order.length, deck.length); index++) {
    if (deck[index].id !== expected.order[index]) {
      failures.push(`backend page ${index + 1} is ${deck[index].id} but the shipped courseware has ${expected.order[index]}`);
    }
  }
} else {
  console.log(`shipped courseware order: not found under ${presentationDir} - comparing against the backend page list`);
}

const observed = [];
const recorded = new Set();
const attempts = new Map();
const lastAction = new Map();
let questions = 0;
let retried = 0;
let view = await api("GET", `/api/v1/classroom/sessions/${sessionId}`);
/**
 * A question interleaved with the pages blocks the walk until the model grades the answer correct - exactly
 * as it blocks a learner, because a wrong answer no longer consumes the question. The first attempt quotes
 * the step's own reviewed textbook evidence; if the grader still wants a real answer (a "why" question is
 * not satisfied by reciting the page), the walk asks the teacher for the key points and answers with those.
 * Grading spends quota and every retry is counted, so a walk that cannot get past a question is reported
 * with the grader's own words instead of being silently shortened.
 * The cursor, not the reported state, decides when the walk is over: a summary-typed step is a teaching
 * beat that can be followed by the next sub-lesson, so stopping on that state would drop real pages.
 */
function evidenceOf(stage) {
  return String(stage.evidence ?? stage.content ?? "").replace(/\s+/g, " ").trim().slice(0, 600);
}
for (let guard = 0; guard < 400; guard++) {
  const stage = view.json?.stage ?? {};
  const stepIndex = Number(stage.stepIndex ?? -1);
  const stepCount = Number(stage.stepCount ?? 0);
  const response = stage.teacherResponse ?? null;
  const answered = response?.answered === true;
  if (stepIndex >= 0 && stepIndex < stepCount && !recorded.has(stepIndex)) {
    recorded.add(stepIndex);
    if (stage.type === "question") questions++;
    observed.push({
      stepIndex,
      type: stage.type ?? null,
      slideId: stage.slideRefs?.[0] ?? null,
      kind: stage.slideMatch?.kind ?? null,
      reason: stage.slideMatch?.reason ?? null,
      match: stage.textbookMatch ?? null,
      questionSource: stage.questionSource ?? null,
      pages: (stage.sourcePages ?? []).join("、"),
      content: String(stage.content ?? stage.prompt ?? "").replace(/\s+/g, " ").slice(0, 40),
    });
  }
  if (stepIndex >= stepCount) break;
  const waiting = view.json?.state === "WAITING" && !answered;
  const tried = attempts.get(stepIndex) ?? 0;
  let action = "CONTINUE";
  let content;
  if (waiting) {
    if (tried >= 3) {
      failures.push(`step${stepIndex} (提问: ${String(stage.prompt ?? "").slice(0, 60)}) was still graded wrong after ` +
        `${tried} attempts, so the walk is stuck on it: "${String(response?.feedback ?? "").slice(0, 300)}"`);
      break;
    }
    // Ask for the key points, then answer with them: reciting the page is not an answer to a why-question.
    const asking = tried > 0 && lastAction.get(stepIndex) !== "ASK";
    action = asking ? "ASK" : "ANSWER";
    content = asking
      ? "请直接给出这道题的参考答案要点，我要按它来作答。"
      : (tried === 0 ? `（验课作答）${evidenceOf(stage)}` : `（验课作答）${String(response?.feedback ?? "").replace(/\s+/g, " ").slice(0, 600)}`);
    lastAction.set(stepIndex, action);
    attempts.set(stepIndex, tried + 1);
    if (tried > 0) retried++;
  }
  const body = { action, expectedRevision: stage.revision ?? 0 };
  if (content) body.content = content;
  const next = await api("POST", `/api/v1/classroom/sessions/${sessionId}/actions`, body);
  if (next.status !== 200) {
    // Report the server's own reason: a silently truncated walk would otherwise be misread as a gap in the plan.
    failures.push(`${body.action} after step${stepIndex} failed: ${next.status} ${String(next.text ?? "").slice(0, 300)}`);
    break;
  }
  view = next;
}

console.log(`\nsteps observed: ${observed.length}: ${observed.filter(step => step.type !== "question").length} teaching steps, ` +
  `${questions} question steps (${retried} retried answers), courseware pages ${deck.length}`);
for (const step of observed) {
  const type = { explain: "讲解", summary: "小结", question: "提问" }[step.type] ?? step.type ?? "-";
  console.log(`  step${String(step.stepIndex).padStart(2)} ${type} ${step.kind ?? "-"} ${step.reason ?? "-"} ${step.match ?? "-"} ` +
    `${step.questionSource ? "[" + step.questionSource + "] " : ""}${step.pages || "-"} ` +
    `${step.slideId === null ? "(no page)" : step.slideId} | ${step.content}`);
}

// The spine's promise: page n is taught by the steps that follow it, in the shipped courseware's order.
// A question step interrogates the page already on screen, so it repeats that page instead of advancing;
// the page sequence is therefore compared after collapsing consecutive repeats. Textbook-only steps are
// interleaved as well and deliberately carry no page of their own.
const pageSteps = [];
for (const step of observed) {
  if (step.reason === "scope-only") continue;
  const previous = pageSteps.at(-1);
  if (!previous || previous.slideId !== step.slideId) pageSteps.push(step);
}
for (let index = 0; index < Math.min(reference.length, pageSteps.length); index++) {
  const step = pageSteps[index];
  if (step.slideId !== reference[index]) {
    failures.push(`page step ${index} shows ${step.slideId} but courseware page ${index + 1} is ${reference[index]}`);
  }
  if (step.kind !== "DIRECT" || step.reason !== "script-refs") {
    failures.push(`page step ${index} was not anchored to its own page (kind=${step.kind} reason=${step.reason})`);
  }
}
if (pageSteps.length !== reference.length) {
  failures.push(`${pageSteps.length} page steps for ${reference.length} courseware pages`);
}
if (expected) {
  const shown = pageSteps.map(step => step.slideId);
  console.log(`\nshipped courseware order preserved: ${shown.every((id, index) => id === reference[index]) ? "yes" : "no"}` +
    ` (${shown.length}/${reference.length} pages)`);
}

// A question asks about the page under discussion: it must leave the screen exactly where it was.
for (const step of observed.filter(step => step.type === "question")) {
  const before = observed.filter(item => item.stepIndex < step.stepIndex).at(-1);
  if (step.slideId === null) failures.push(`question step${step.stepIndex} showed no page: a question must stay on the page it asks about`);
  else if (before && before.slideId !== step.slideId) {
    failures.push(`question step${step.stepIndex} moved the screen from ${before.slideId} to ${step.slideId}`);
  }
}

// Textbook-only steps must leave the screen where it is: they carry no page of their own (slideId null)
// or, at most, repeat the page already on screen.
const extensions = observed.filter(step => step.reason === "scope-only");
for (const step of extensions) {
  const before = observed.filter(item => item.stepIndex < step.stepIndex).at(-1);
  if (!before || (step.slideId !== null && step.slideId !== before.slideId)) {
    failures.push(`step${step.stepIndex} is a textbook extension but moved the screen`);
  }
  if (!step.pages) failures.push(`step${step.stepIndex} is a textbook extension without a textbook page`);
}

const anchored = observed.filter(step => step.match === "exact").length;
const widened = observed.filter(step => step.match === "parent" || step.match === "chapter").length;
const withoutEvidence = observed.filter(step => !step.pages).length;
console.log(`\nanchored to the page's own section: ${anchored} | widened section: ${widened} | no textbook page: ${withoutEvidence}`);
console.log(`textbook-only extension steps: ${extensions.length}`);

if (failures.length) {
  console.log("\nFAILURES:");
  for (const failure of failures) console.log(`  - ${failure}`);
  process.exit(1);
}
console.log("\nOK");
