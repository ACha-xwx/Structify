// Builds the local annotation of every courseware page. Deterministic and offline: no model call.
// The annotations are what the preparation prompt and the deterministic aligner both read, so a page's
// section, role and terms come from one reviewed place instead of being guessed at each call site.
//
// Usage: node scripts/build-slide-annotations.mjs [--out <file>] [--presentation <dir>]
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) args.set(process.argv[i], process.argv[i + 1]);
const presentationDir = args.get("--presentation") ?? "F:/data-structure-agent/data-structure-agent/presentation-materials";
const outFile = args.get("--out") ?? "private/presentation-annotations/slides.annotations.json";

const slides = JSON.parse(fs.readFileSync(path.join(presentationDir, "slides.json"), "utf8"));
const plans = JSON.parse(fs.readFileSync(path.join(presentationDir, "lesson-presentation-plans.json"), "utf8")).lessons;

/** Slide -> sub-lesson/scene placement, taken from the pipeline's own plan. */
const scope = new Map();
for (const [lessonId, plan] of Object.entries(plans)) {
  for (const [scene, value] of Object.entries(plan.scenes ?? {})) {
    for (const slideId of value.slides ?? []) {
      const entry = scope.get(slideId) ?? { subLessons: [], scenes: [], planTitles: [] };
      if (!entry.subLessons.includes(lessonId)) entry.subLessons.push(lessonId);
      if (!entry.scenes.includes(scene)) entry.scenes.push(scene);
      const title = plan.title ?? "";
      if (title && !entry.planTitles.includes(title)) entry.planTitles.push(title);
      scope.set(slideId, entry);
    }
  }
}

const SECTION = /(?<![\d.\u56fe\u8868\u7b97\u6cd5\u4f8b\u5f0f])(\d{1,2}\.\d{1,2}(?:\.\d{1,2})?)(?![\d.])/g;
const ROLE_RULES = [
  [/(定义|概念|术语|基本概念)/, "定义"],
  [/(算法|实现|代码|伪码)/, "算法"],
  [/(示例|实例|例子|例\s*\d|例（)/, "示例"],
  [/(练习|习题|测试|自测|作业)/, "练习"],
  [/(性能|复杂度|分析|效率)/, "分析"],
  [/(小结|总结|回顾)/, "小结"],
  [/(构造|插入|删除|查找|遍历|排序|旋转)/, "操作"],
];
const STOP = new Set(["我们", "可以", "这个", "那个", "一个", "如果", "因此", "所以", "进行", "时候", "问题", "结构", "下列", "以下", "本节", "如下", "也称", "称为", "表示", "对于", "以及", "通过", "需要", "必须", "然后", "此时", "其中", "一个", "数据", "元素"]);

function section(text) {
  const matches = [...String(text ?? "").matchAll(SECTION)].map(match => match[1]);
  return matches.length ? matches[0] : "";
}

function role(text) {
  for (const [pattern, label] of ROLE_RULES) if (pattern.test(text)) return label;
  return "内容";
}

/**
 * Curated concepts first (the deck pipeline already wrote them), then the slide title, then terms that
 * really repeat in the page text. Sliding n-gram noise such as "的节点删" never reaches the prompt.
 */
function terms(slide, limit = 8) {
  const chosen = [];
  const push = (value) => {
    const token = String(value ?? "").trim();
    if (!token || STOP.has(token) || chosen.includes(token)) return;
    if (chosen.some(existing => existing.includes(token) || token.includes(existing))) return;
    chosen.push(token);
  };
  for (const concept of slide.concepts ?? []) push(concept);
  for (const match of String(slide.title ?? "").matchAll(/[\u4e00-\u9fff]{2,6}|[A-Za-z][A-Za-z0-9_]{1,}/g)) push(match[0]);
  const counts = new Map();
  const cjk = [...String(slide.rawText ?? "").replace(/[A-Za-z0-9_]+/g, " ")].filter(ch => /[\u4e00-\u9fff]/.test(ch)).join("");
  for (let size = 2; size <= 4; size++) {
    for (let index = 0; index + size <= cjk.length; index++) {
      const token = cjk.slice(index, index + size);
      if (STOP.has(token)) continue;
      counts.set(token, (counts.get(token) ?? 0) + 1);
    }
  }
  const repeated = [...counts.entries()].filter(([, count]) => count >= 2).sort((left, right) => right[1] - left[1]);
  for (const [token] of repeated) {
    if (chosen.length >= limit) break;
    push(token);
  }
  return chosen.slice(0, limit);
}

const annotations = {};
for (const slide of slides.slides ?? []) {
  const text = [slide.title, slide.semanticSummary, slide.teachingFocus, (slide.concepts ?? []).join(" "), slide.rawText].join(" ");
  const placement = scope.get(slide.id) ?? { subLessons: [], scenes: [], planTitles: [] };
  annotations[slide.id] = {
    section: section(slide.title) || section(slide.semanticSummary) || section(slide.rawText),
    role: role(text),
    terms: terms(slide),
    summary: (slide.semanticSummary || slide.title || "").slice(0, 120),
    subLessons: placement.subLessons,
    scenes: placement.scenes,
    planTitles: placement.planTitles,
  };
}

const payload = {
  version: 1,
  builtAt: new Date().toISOString(),
  source: path.join(presentationDir, "slides.json"),
  slideCount: Object.keys(annotations).length,
  slides: annotations,
};
fs.mkdirSync(path.dirname(path.resolve(outFile)), { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(payload, null, 1) + "\n", "utf8");

const withSection = Object.values(annotations).filter(item => item.section).length;
const roleCounts = {};
for (const item of Object.values(annotations)) roleCounts[item.role] = (roleCounts[item.role] ?? 0) + 1;
console.log(`annotated ${payload.slideCount} slides -> ${outFile}`);
console.log(`with section number: ${withSection}; by role: ${JSON.stringify(roleCounts)}`);
