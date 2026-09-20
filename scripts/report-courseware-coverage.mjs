// Where does the courseware actually cover the reviewed textbook, and where does it not?
// Writes a markdown report so the gaps are explicit instead of showing up as a wrong slide in class.
//
// Usage: node scripts/report-courseware-coverage.mjs [--out output/courseware-coverage.md]
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) args.set(process.argv[i], process.argv[i + 1]);
const presentationDir = args.get("--presentation") ?? "F:/data-structure-agent/data-structure-agent/presentation-materials";
const annotationsFile = args.get("--annotations") ?? "private/presentation-annotations/slides.annotations.json";
const lessonsDir = args.get("--lessons") ?? "private/reviewed-textbook/lessons";
const outFile = args.get("--out") ?? "output/courseware-coverage.md";

const slides = JSON.parse(fs.readFileSync(path.join(presentationDir, "slides.json"), "utf8")).slides;
const plans = JSON.parse(fs.readFileSync(path.join(presentationDir, "lesson-presentation-plans.json"), "utf8")).lessons;
const annotations = JSON.parse(fs.readFileSync(annotationsFile, "utf8")).slides;
const byId = new Map(slides.map(slide => [slide.id, slide]));

const CJK = /[\u3400-\u9fff]/;
function tokens(text) {
  const clean = String(text ?? "").toLowerCase().replace(/\s+/g, " ");
  const out = new Set(clean.match(/[a-z0-9_]{2,}/g) ?? []);
  const cjk = [...clean].filter(ch => CJK.test(ch)).join("");
  for (let i = 0; i < cjk.length - 1; i++) out.add(cjk.slice(i, i + 2));
  return out;
}
function cosine(a, b) {
  if (!a.size || !b.size) return 0;
  let hit = 0;
  for (const token of a) if (b.has(token)) hit++;
  return hit / Math.sqrt(a.size * b.size);
}

/** Planned sub-lessons of a lesson number, with their scene pages annotated. */
function deckOf(key) {
  const planIds = Object.keys(plans).filter(id => id.startsWith(key)).sort();
  const sceneTokens = [];
  const subLessons = [];
  for (const planId of planIds) {
    const plan = plans[planId];
    const scenes = [];
    for (const [scene, value] of Object.entries(plan.scenes ?? {})) {
      const pages = (value.slides ?? []).map(id => byId.get(id)).filter(Boolean);
      if (!pages.length) continue;
      scenes.push({ scene, coverage: value.coverage, pages });
      for (const page of pages) sceneTokens.push(tokens([page.title, page.semanticSummary, (page.concepts ?? []).join(" "), (annotations[page.id]?.terms ?? []).join(" ")].join(" ")));
    }
    subLessons.push({ planId, title: plan.title, scenes });
  }
  return { planIds, subLessons, sceneTokens, pageCount: new Set(subLessons.flatMap(item => item.scenes.flatMap(scene => scene.pages.map(page => page.id)))).size };
}

/** Teaching units of the reviewed lesson text (paragraph level, code blocks skipped). */
function unitsOf(markdown) {
  const result = [];
  let page = 0, code = false, buffer = [];
  const flush = () => { const text = buffer.join("\n").trim(); buffer = []; if (text.length > 50) result.push({ page, text }); };
  for (const line of markdown.split(/\r?\n/)) {
    const header = line.match(/^### 教材页 (\d+)（PDF页 (\d+)）$/);
    if (header) { flush(); page = Number(header[1]); continue; }
    if (line.startsWith("```")) { code = !code; flush(); continue; }
    if (code || line.startsWith("> ") || line.startsWith("<!--")) continue;
    if (!line.trim()) flush(); else buffer.push(line);
  }
  flush();
  return result;
}

const FLOOR = 0.10;
const rows = [];
for (const file of fs.readdirSync(lessonsDir).filter(name => name.includes("已核验")).sort()) {
  const key = file.slice(0, 5);
  if (!/^\d{2}-\d{2}$/.test(key)) continue;
  if (rows.some(row => row.key === key)) continue;
  const deck = deckOf(key);
  const units = unitsOf(fs.readFileSync(path.join(lessonsDir, file), "utf8")).map(unit => ({ ...unit, tokens: tokens(unit.text) }));
  const gaps = units.filter(unit => {
    const best = Math.max(0, ...deck.sceneTokens.map(signal => cosine(unit.tokens, signal)));
    return best < FLOOR;
  });
  rows.push({
    key,
    file,
    pageCount: deck.pageCount,
    planIds: deck.planIds,
    subLessons: deck.subLessons,
    units: units.length,
    gaps: gaps.map(gap => ({ page: gap.page, preview: gap.text.replace(/\n/g, " ").slice(0, 60) })),
  });
}

const lines = [];
lines.push("# 课件覆盖报告", "", `生成时间：${new Date().toISOString()}`, "");
lines.push("对每个已核验课时，列出课件里的细分课时与场景页数，以及教材中**找不到对应课件页**的段落（判定阈值 0.10）。", "");
let totalUnits = 0, totalGaps = 0;
for (const row of rows) {
  if (!row.subLessons.length) {
    lines.push(`## ${row.key} — 无课件`, "", `- 教材段落 ${row.units} 段，全部没有课件。`, "");
    totalUnits += row.units;
    totalGaps += row.units;
    continue;
  }
  totalUnits += row.units;
  totalGaps += row.gaps.length;
  lines.push(`## ${row.key} — 课件 ${row.pageCount} 页`, "");
  for (const subLesson of row.subLessons) {
    lines.push(`- 细分课时 ${subLesson.planId} ${subLesson.title}`);
    for (const scene of subLesson.scenes) {
      lines.push(`  - 场景 ${scene.scene}（${scene.coverage}）：${scene.pages.map(page => `${page.slideNumber}.${page.title || page.semanticSummary}`.slice(0, 34)).join("；").slice(0, 160)}`);
    }
  }
  lines.push("", `- 教材段落 ${row.units} 段，其中 ${row.gaps.length} 段在课件里没有对应页：`);
  for (const gap of row.gaps.slice(0, 12)) lines.push(`  - 教材第 ${gap.page} 页：${gap.preview}`);
  if (row.gaps.length > 12) lines.push(`  - 其余 ${row.gaps.length - 12} 段略`);
  lines.push("");
}
lines.splice(4, 0, `合计：${rows.length} 个课时，教材段落 ${totalUnits} 段，其中 ${totalGaps} 段（${(100 * totalGaps / Math.max(1, totalUnits)).toFixed(0)}%）在课件里没有对应页。`, "");
fs.mkdirSync(path.dirname(path.resolve(outFile)), { recursive: true });
fs.writeFileSync(outFile, lines.join("\n") + "\n", "utf8");
console.log(`coverage report -> ${outFile}`);
console.log(`lessons=${rows.length} units=${totalUnits} gaps=${totalGaps} (${(100 * totalGaps / Math.max(1, totalUnits)).toFixed(0)}%)`);
for (const row of rows.slice(0, 8)) console.log(`  ${row.key}: pages=${row.pageCount} subLessons=${row.subLessons.length} units=${row.units} gaps=${row.gaps.length}`);
