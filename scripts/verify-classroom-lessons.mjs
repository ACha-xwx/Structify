// Verifies the classroom lesson surface through the real HTTP API with a logged-in account.
// Credentials come from the environment only; nothing is read from or written to the repository.
//
//   STRUCTIFY_BASE_URL=http://127.0.0.1:8794 \
//   STRUCTIFY_USERNAME=... STRUCTIFY_PASSWORD=... \
//   node scripts/verify-classroom-lessons.mjs [--expect N] [--prepare]
import process from 'node:process';

const baseUrl = (process.env.STRUCTIFY_BASE_URL ?? 'http://127.0.0.1:8794').replace(/\/$/, '');
const username = process.env.STRUCTIFY_USERNAME;
const password = process.env.STRUCTIFY_PASSWORD;
if (!username || !password) {
  console.error('STRUCTIFY_USERNAME and STRUCTIFY_PASSWORD are required');
  process.exit(2);
}

const expectIndex = process.argv.indexOf('--expect');
const expected = expectIndex >= 0 ? Number(process.argv[expectIndex + 1]) : null;
const prepare = process.argv.includes('--prepare');
const onlyIndex = process.argv.indexOf('--only');
const only = onlyIndex >= 0 ? process.argv[onlyIndex + 1] : null;
// --match filters by lesson title substring, so a single new lesson can be prepared without re-running the whole chapter.
const matchIndex = process.argv.indexOf('--match');
const match = matchIndex >= 0 ? process.argv[matchIndex + 1] : null;

let cookie = '';
async function api(method, pathname, body) {
  const response = await fetch(baseUrl + pathname, {
    method,
    headers: { 'content-type': 'application/json', ...(cookie ? { cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const setCookie = response.headers.getSetCookie?.() ?? [];
  if (setCookie.length) cookie = setCookie.map(value => value.split(';')[0]).join('; ');
  const text = await response.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* keep raw text */ }
  return { status: response.status, json, text };
}

const failures = [];

const login = await api('POST', '/api/v1/auth/login', { username, password });
if (login.status !== 200) {
  console.error('login failed', login.status, login.text.slice(0, 200));
  process.exit(2);
}
console.log(`login ok as ${login.json?.user?.username ?? username} role=${login.json?.user?.role ?? '?'}`);

const lessons = await api('GET', '/api/v1/classroom/lessons');
if (lessons.status !== 200) {
  console.error('lessons failed', lessons.status, lessons.text.slice(0, 200));
  process.exit(2);
}
const list = Array.isArray(lessons.json) ? lessons.json : [];
console.log(`lessons: ${list.length}`);
for (const lesson of list) {
  console.log(`  ${lesson.chapterId} | ${lesson.pages} | ${lesson.title}`);
}
if (expected !== null && list.length !== expected) {
  failures.push(`expected ${expected} lessons but the API returned ${list.length}`);
}

const chapters = [...new Set(list.map(lesson => lesson.chapterId))].sort();
console.log(`chapters: ${chapters.join(', ')}`);

if (prepare) {
  for (const lesson of list.filter(item => (!only || item.chapterId === only) && (!match || (item.title ?? '').includes(match)))) {
    const started = await api('POST', '/api/v1/classroom/preparations', { lessonId: lesson.id });
    if (started.status !== 200) {
      failures.push(`${lesson.chapterId}: preparation rejected with ${started.status} ${started.text.slice(0, 160)}`);
      console.log(`  prepare ${lesson.chapterId}: START FAILED ${started.status}`);
      continue;
    }
    let status = started.json;
    const deadline = Date.now() + 180_000;
    while (status?.state === 'preparing' && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 3000));
      const polled = await api('GET', `/api/v1/classroom/preparations/${status.id}`);
      status = polled.json;
    }
    const steps = status?.session?.stage ? 'session-ready' : 'no-session';
    console.log(`  prepare ${lesson.chapterId}: ${status?.state} (${steps}) ${status?.error ?? ''}`);
    if (status?.state !== 'ready') {
      failures.push(`${lesson.chapterId}: preparation ended in state ${status?.state} — ${status?.error ?? 'no error message'}`);
    }
  }
}

if (failures.length) {
  console.log('\nFAILURES:');
  for (const failure of failures) console.log(`  - ${failure}`);
  process.exit(1);
}
console.log('\nOK');
