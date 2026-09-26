// End-to-end check of the courseware surface through the real HTTP API.
//   STRUCTIFY_BASE_URL=http://127.0.0.1:8794 STRUCTIFY_USERNAME=... STRUCTIFY_PASSWORD=... node scripts/verify-courseware.mjs
import process from 'node:process';

const baseUrl = (process.env.STRUCTIFY_BASE_URL ?? 'http://127.0.0.1:8794').replace(/\/$/, '');
const username = process.env.STRUCTIFY_USERNAME;
const password = process.env.STRUCTIFY_PASSWORD;
if (!username || !password) {
  console.error('STRUCTIFY_USERNAME and STRUCTIFY_PASSWORD are required');
  process.exit(2);
}

let cookie = '';
async function api(method, pathname, body, raw = false) {
  const response = await fetch(baseUrl + pathname, {
    method,
    headers: { 'content-type': 'application/json', ...(cookie ? { cookie } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const setCookie = response.headers.getSetCookie?.() ?? [];
  if (setCookie.length) cookie = setCookie.map(value => value.split(';')[0]).join('; ');
  if (raw) return { status: response.status, type: response.headers.get('content-type'), bytes: (await response.arrayBuffer()).byteLength };
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
console.log(`login ok as ${login.json?.user?.username ?? username}`);

const meta = await api('GET', '/api/v1/presentation/meta');
console.log(`meta: ready=${meta.json?.ready} slides=${meta.json?.slideCount} decks=${meta.json?.deckCount} plannedLessons=${meta.json?.lessonCount}`);
if (!meta.json?.ready) failures.push('courseware index is not ready');

const decks = await api('GET', '/api/v1/presentation/decks');
console.log(`decks: ${decks.json?.length}`);
if (!Array.isArray(decks.json) || decks.json.length === 0) failures.push('no decks are exposed');

const firstDeck = decks.json?.[0];
if (firstDeck) {
  const slides = await api('GET', `/api/v1/presentation/decks/${encodeURIComponent(firstDeck.deckId)}/slides`);
  console.log(`deck ${firstDeck.deckId}: ${slides.json?.length} slides, first=${slides.json?.[0]?.title ?? ''}`);
  const image = await api('GET', `/api/v1/presentation/slides/${encodeURIComponent(slides.json[0].id)}/image`, undefined, true);
  console.log(`deck image: status=${image.status} type=${image.type} bytes=${image.bytes}`);
  if (image.status !== 200 || !String(image.type).startsWith('image/')) failures.push('deck image is not served');
}

const lessons = await api('GET', '/api/v1/classroom/lessons');
const withCourseware = [];
const withoutCourseware = [];
for (const lesson of lessons.json ?? []) {
  const courseware = await api('GET', `/api/v1/presentation/lessons/${encodeURIComponent(lesson.id)}/slides`);
  const count = courseware.json?.slides?.length ?? 0;
  (count ? withCourseware : withoutCourseware).push(`${courseware.json?.coursewareKey || '?'} ${lesson.title.slice(0, 24)} (${count})`);
  if (count) {
    const primary = courseware.json.slides[0];
    const image = await api('GET', `/api/v1/presentation/slides/${encodeURIComponent(primary.id)}/image`, undefined, true);
    if (image.status !== 200) failures.push(`${lesson.title}: primary slide image missing`);
  }
}
console.log(`lessons with courseware: ${withCourseware.length}`);
for (const row of withCourseware.slice(0, 12)) console.log(`  ${row}`);
console.log(`lessons without courseware: ${withoutCourseware.length}`);
for (const row of withoutCourseware) console.log(`  ${row}`);

if (failures.length) {
  console.log('\nFAILURES:');
  for (const failure of failures) console.log(`  - ${failure}`);
  process.exit(1);
}
console.log('\nOK');
