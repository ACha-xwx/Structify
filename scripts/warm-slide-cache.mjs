#!/usr/bin/env node
/**
 * Warm the slide-image cache from wherever this runs.
 *
 * The classroom pulls one slide image per page plus a few neighbours, and every image is served from a
 * signed, permanently cacheable URL. Cloudflare caches per colo, so a colo that has never served an
 * image pays a full round trip to the origin in China - seconds per image. This walks the catalogue
 * through the public hostname so the colos that matter already have the bytes.
 *
 * Run this from a NEUTRAL network (your laptop, the office, a phone hotspot) - never from the origin
 * server: there the downloads and Cloudflare's own origin pulls share one uplink, and warming makes the
 * live site slower instead of faster. Two requests at a time is deliberate for the same reason.
 *
 * Usage:
 *   STRUCTIFY_USER=you@example.com STRUCTIFY_PASSWORD=… node scripts/warm-slide-cache.mjs
 *   … --site=https://structify.cn --concurrency=2 --minutes=20
 *
 * Exit code is 0 for a completed sweep, 1 when it could not even start (no credentials, no catalogue).
 */

const options = parseArguments(process.argv.slice(2));
const SITE = (options.site ?? process.env.STRUCTIFY_SITE ?? "https://structify.cn").replace(/\/$/, "");
const CONCURRENCY = Number(options.concurrency ?? 2);
const MINUTES = Number(options.minutes ?? 20);
const USER = options.user ?? process.env.STRUCTIFY_USER ?? "";
const PASSWORD = options.password ?? process.env.STRUCTIFY_PASSWORD ?? "";

function parseArguments(argv) {
  const parsed = {};
  for (const argument of argv) {
    const match = /^--([^=]+)=?(.*)$/.exec(argument);
    if (match) parsed[match[1]] = match[2] || "true";
  }
  return parsed;
}

async function login() {
  if (!USER || !PASSWORD) {
    throw new Error("需要 STRUCTIFY_USER / STRUCTIFY_PASSWORD（或 --user= / --password=）");
  }
  const response = await fetch(`${SITE}/api/v1/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ username: USER, password: PASSWORD }),
  });
  if (!response.ok) throw new Error(`登录失败：${response.status} ${response.statusText}`);
  const payload = await response.json();
  const token = payload.token ?? payload.accessToken;
  if (!token) throw new Error("登录响应里没有 token，接口可能变了");
  return token;
}

async function getJson(path, token) {
  const response = await fetch(`${SITE}/api/v1${path}`, { headers: { authorization: `Bearer ${token}` } });
  if (!response.ok) throw new Error(`${path} → ${response.status}`);
  return response.json();
}

async function collectImageUrls(token) {
  const decks = await getJson("/presentation/decks", token);
  const urls = new Set();
  for (const deck of decks) {
    const id = deck.deckId ?? deck.id;
    if (!id) continue;
    try {
      for (const slide of await getJson(`/presentation/decks/${encodeURIComponent(id)}/slides`, token)) {
        if (slide.imageUrl) urls.add(slide.imageUrl);
      }
    } catch (error) {
      console.warn(`  跳过 ${id}：${error.message}`);
    }
  }
  return [...urls];
}

async function main() {
  const token = await login();
  const urls = await collectImageUrls(token);
  if (!urls.length) {
    console.error("没有取到任何图片 URL，检查账号权限或接口路径");
    process.exit(1);
  }
  console.log(`${SITE}：${urls.length} 张图，并发 ${CONCURRENCY}，最长 ${MINUTES} 分钟`);
  console.log("（从现在开始算的是公网域名，不是你到服务器的距离）");

  const deadline = Date.now() + MINUTES * 60_000;
  const tally = { HIT: 0, MISS: 0, EXPIRED: 0, REVALIDATED: 0, other: 0, failed: 0, bytes: 0 };
  let cursor = 0;
  let done = 0;

  async function worker() {
    while (cursor < urls.length && Date.now() < deadline) {
      const url = urls[cursor++];
      try {
        const response = await fetch(SITE + url, { redirect: "follow" });
        const body = await response.arrayBuffer();
        const state = response.headers.get("cf-cache-status") ?? `http-${response.status}`;
        if (state in tally) tally[state] += 1;
        else tally.other += 1;
        tally.bytes += body.byteLength;
      } catch {
        tally.failed += 1;
      }
      done += 1;
      if (done % 100 === 0) {
        console.log(`  ${done}/${urls.length}  HIT=${tally.HIT} MISS=${tally.MISS} 失败=${tally.failed} ${(tally.bytes / 1048576).toFixed(1)}MB`);
      }
    }
  }

  await Promise.all(Array.from({ length: Math.max(1, CONCURRENCY) }, worker));
  console.log(`完成 ${done}/${urls.length}：HIT=${tally.HIT} MISS=${tally.MISS} REVALIDATED=${tally.REVALIDATED} ` +
    `其它=${tally.other} 失败=${tally.failed}，共 ${(tally.bytes / 1048576).toFixed(1)}MB`);
  if (done < urls.length) console.log("（到时间预算了，再跑一次会接着暖剩下的）");
}

main().catch((error) => {
  console.error(`失败：${error.message}`);
  process.exit(1);
});
