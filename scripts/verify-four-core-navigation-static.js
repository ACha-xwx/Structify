const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..", "frontend");
// Only the legacy page is checked here. frontend/index.html stopped being a mirror of it when the entry
// page was split out for the Vue build - it is now the built application, which has its own navigation
// covered by the frontend test job - while prototype.html is still served at /prototype.html and still
// needs the legacy four-module navigation.
const entries = ["prototype.html"];
const expectedViews = ["home", "coach", "mainline", "presentation", "knowledge"];

for (const entry of entries) {
  const html = fs.readFileSync(path.join(root, entry), "utf8");
  const nav = html.match(/<nav class="rail-nav" id="viewNav"[\s\S]*?<\/nav>/)?.[0] || "";
  assert.ok(nav, `${entry} must expose the primary navigation`);

  const views = Array.from(nav.matchAll(/<button\b[^>]*\bdata-view="([^"]+)"/g), (match) => match[1]);
  assert.deepEqual(views, expectedViews, `${entry} primary navigation must contain the overview and four meeting modules in order`);
  assert.match(nav, />问答<\/button>/, `${entry} must label the coach entry as 问答`);
  assert.match(nav, />主线学习<\/button>/, `${entry} must label the textbook path explicitly`);
  assert.match(nav, />PPT 学习<\/button>/, `${entry} must expose a dedicated PPT entry`);
  assert.match(nav, />知识库<\/button>/, `${entry} must expose a dedicated knowledge entry`);

  for (const toolView of ["classroom", "animation", "compiler", "materials"]) {
    assert.ok(!views.includes(toolView), `${entry} must keep ${toolView} out of primary navigation`);
  }

  assert.match(html, /const appViews = \[[^\]]*"mainline"[^\]]*"presentation"[^\]]*"knowledge"[^\]]*\]/, `${entry} must route all four modules`);
  assert.match(html, /if \(view === "materials"[^\n]*return "mainline";/, `${entry} must retain the legacy materials route as a mainline alias`);
}

console.log("four-core-navigation-static-ok entries=1 modules=4");
