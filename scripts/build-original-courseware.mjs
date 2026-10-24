import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const workspace = process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const knowledgeRoot = path.join(workspace, '知识库');
const imageRoot = path.join(workspace, 'WebP');
const manifest = JSON.parse(fs.readFileSync(path.join(knowledgeRoot, '转换清单.json'), 'utf8'));
const chapterIds = new Map([
  ['第一章绪论', '01'], ['第二章线性表', '02'], ['第三章限定性线性表', '03'],
  ['第四章串', '04'],
  ['第五章数组和广义表', '05'], ['第六章树和二叉树', '06'], ['第七章图', '07'],
  ['第八章查找', '08'], ['第九章排序', '09'],
]);
const slides = [], lessons = {}, numbers = new Map();
const builtAt = new Date().toISOString();
for (const deck of [...manifest.decks].sort((a, b) => a.source < b.source ? -1 : a.source > b.source ? 1 : 0)) {
  const chapter = chapterIds.get(deck.chapter);
  if (!chapter) throw new Error(`Unknown chapter: ${deck.chapter}`);
  const number = (numbers.get(chapter) ?? 0) + 1;
  numbers.set(chapter, number);
  const key = `${chapter}-${String(number).padStart(2, '0')}`;
  const markdownFile = path.resolve(workspace, deck.markdown);
  if (!markdownFile.startsWith(knowledgeRoot + path.sep)) throw new Error('Markdown escapes knowledge root');
  const markdown = fs.readFileSync(markdownFile, 'utf8').replace(/\r\n/g, '\n');
  const pages = [...markdown.matchAll(/^## 第 (\d+) 页：([^\n]*)\n/gm)];
  if (pages.length !== deck.slides || pages.some((page, i) => Number(page[1]) !== i + 1)) {
    throw new Error(`Page count/order mismatch: ${deck.markdown}`);
  }
  const title = path.basename(deck.source, '.pptx');
  // Include source text and every image in the revision so immutable URLs always identify the bytes.
  const hash = crypto.createHash('sha256').update(markdown);
  const images = pages.map((page) => {
    const directory = deck.webp_dir ? deck.webp_dir.replace(/^WebP\//, '') : `${deck.chapter}/${title}`;
    const relative = `${directory}/slide-${String(page[1]).padStart(3, '0')}.webp`;
    const file = path.resolve(imageRoot, relative);
    if (!file.startsWith(imageRoot + path.sep)) throw new Error('Image escapes WebP root');
    const bytes = fs.readFileSync(file);
    if (bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WEBP') {
      throw new Error(`Invalid WebP: ${relative}`);
    }
    hash.update(bytes);
    return relative;
  });
  const deckId = `ch${chapter}-deck${String(number).padStart(2, '0')}-${hash.digest('hex').slice(0, 16)}`;
  const slideOrder = [];
  pages.forEach((page, i) => {
    const body = markdown.slice(page.index + page[0].length, pages[i + 1]?.index ?? markdown.length)
      .replace(/^<a id="slide-[^\n]*\n?/gm, '')
      .replace(/^!?\[(?:查看 WebP 原图|第 \d+ 页)\]\([^\n]*\)\s*$/gm, '').trim();
    const id = `${deckId}-s${String(i + 1).padStart(3, '0')}`;
    slideOrder.push(id);
    slides.push({ id, deckId, deckTitle: title, slideNumber: i + 1, chapter,
      title: page[2].trim(), rawText: body, speakerNotes: '', semanticSummary: body.slice(0, 400),
      teachingRole: '', teachingFocus: '', concepts: [], visualAnchors: [], shouldShow: true,
      lessonIds: [key], imagePath: images[i], section: page[2].match(/\d+\.\d+(?:\.\d+)*/)?.[0] ?? '',
      role: '', terms: [] });
  });
  lessons[key] = { title, slideOrder };
}
if (slides.length !== manifest.slide_count || Object.keys(lessons).length !== manifest.ppt_count) {
  throw new Error('Release totals do not match conversion manifest');
}
// Validate the full release before replacing either index. Original Markdown and WebP files stay intact.
fs.writeFileSync(path.join(imageRoot, 'slides.json'), JSON.stringify({ version: 1, builtAt, slides }, null, 2) + '\n');
fs.writeFileSync(path.join(imageRoot, 'lesson-presentation-plans.json'), JSON.stringify({ version: 1, builtAt, lessons }, null, 2) + '\n');
console.log(`Indexed ${slides.length} original WebP pages across ${Object.keys(lessons).length} decks.`);
