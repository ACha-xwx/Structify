// Composes private reviewed-textbook lessons and the page-level import manifest.
//
// Input  : a page revision record (private, git-ignored) that quotes the textbook text and
//          records, per textbook page, how the OCR was checked against the scanned PDF.
// Output : private/reviewed-textbook/lessons/*.md  (what the knowledge loader reads)
//          private/reviewed-textbook/import-manifest.json (the allow list the loader enforces)
//
// The script never invents textbook text: every page must either be marked `verbatim`
// (OCR confirmed against the PDF) or carry explicit `replacements` / a full `text` override
// produced by reading the page. A replacement that does not match exactly once fails the build.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) args.set(process.argv[i], process.argv[i + 1]);

const revisionsPath = args.get('--revisions') ?? 'private/reviewed-textbook/revisions/page-revisions.json';
const sourceDir = args.get('--source') ?? 'F:/data-structure-agent/data-structure-agent/lesson-materials/lessons';
const outputDir = args.get('--out') ?? 'private/reviewed-textbook';
const dryRun = process.argv.includes('--dry-run');

const REVIEWED_LABEL_PREFIX = '> OCR质量：已对照原始教材 PDF 核验（';
const PAGE_HEADER = /^### 教材页 (\d+)（PDF页 (\d+)）$/;

const revisions = JSON.parse(fs.readFileSync(revisionsPath, 'utf8'));
const reviewedOn = revisions.reviewedOn;
if (!reviewedOn) throw new Error('revision record must declare reviewedOn');
const revisionsDir = path.dirname(revisionsPath);
// Textbook page N maps to PDF page N + offset; every page must satisfy this so a typo cannot slip a wrong page picture into the material.
const offset = Number.isInteger(revisions.pdf?.textbookPageOffset) ? revisions.pdf.textbookPageOffset : null;

const OCR_BASELINE_PATH = revisions.ocrBaseline?.file
  ?? 'F:/data-structure-agent/data-structure-agent/lesson-materials/raw/ocr_pages.json';
const baseline = JSON.parse(fs.readFileSync(OCR_BASELINE_PATH, 'utf8'));

function sha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

// Review evidence lives in `<!-- 核验记录 … -->` blocks next to the text it justifies.
// The classroom material must contain textbook content only, so the blocks are dropped here.
function stripAuditComments(text) {
  return text
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map(line => line.replace(/[ \t]+$/, ''))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function readPageBlocks(markdown) {
  const lines = markdown.replace(/\r\n?/g, '\n').split('\n');
  const blocks = new Map();
  for (let i = 0; i < lines.length; i++) {
    const header = lines[i].trim().match(PAGE_HEADER);
    if (!header) continue;
    let end = i + 1;
    while (end < lines.length && !PAGE_HEADER.test(lines[end].trim()) && !lines[end].startsWith('## ')) end++;
    const block = lines.slice(i + 1, end);
    const body = block
      .filter(line => !line.trim().startsWith('> OCR质量：'))
      .join('\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
    blocks.set(Number(header[1]), { page: Number(header[1]), pdfPage: Number(header[2]), body });
    i = end - 1;
  }
  return blocks;
}

const CJK = '\\u3000-\\u303f\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff';
const DESPACE_RULES = [
  // The page-level OCR separates every Chinese word; rejoin it. ASCII, digits and code stay untouched.
  [new RegExp(`([${CJK}])\\s+([${CJK}])`, 'g'), '$1$2'],
  [new RegExp(`([${CJK}])\\s+([，。；：、！？（）《》“”‘’])`, 'g'), '$1$2'],
  [new RegExp(`([，。；：、！？（）《》“”‘’])\\s+([${CJK}])`, 'g'), '$1$2'],
  [/[ \t]+/g, ' '],
];

function despace(text) {
  let result = text.replace(/\r\n?/g, '\n');
  // Repeat because adjacent pairs overlap (a b c -> a b c -> abc).
  for (let pass = 0; pass < 6; pass++) {
    const before = result;
    for (const [pattern, replacement] of DESPACE_RULES) result = result.replace(pattern, replacement);
    if (result === before) break;
  }
  return result
    .split('\n')
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/**
 * Builds a page body from the page-level OCR baseline. The de-spacing is mechanical and
 * deterministic; every remaining difference from the book must be listed as a replacement,
 * which the caller has checked against the rendered page image.
 */
function fromOcrBaseline(revision, page, baseline) {
  const raw = baseline[String(page)];
  if (typeof raw !== 'string' || !raw.trim()) {
    throw new Error(`OCR baseline has no text for textbook page ${page}`);
  }
  return revision.despace === false ? raw.trim() : despace(raw);
}

function applyRevision(source, page, revision, ocrBody, baseDir, baseline) {  if (revision.textFile) {
    if (revision.mode && revision.mode !== 'file') {
      throw new Error(`${source} page ${page}: textFile and mode ${revision.mode} are mutually exclusive`);
    }
    const file = path.resolve(baseDir, revision.textFile);
    if (!fs.existsSync(file)) throw new Error(`${source} page ${page}: missing revised text ${revision.textFile}`);
    // Page files keep their audit trail inline as HTML comments (`<!-- 核验记录 … -->`).
    // Comments are review evidence, never classroom text, so they are stripped here.
    const text = stripAuditComments(fs.readFileSync(file, 'utf8'));
    if (!text) throw new Error(`${source} page ${page}: revised text ${revision.textFile} is empty`);
    return text;
  }
  if (revision.mode === 'verbatim') {
    if (revision.text) throw new Error(`${source} page ${page}: verbatim pages must not carry text`);
    return ocrBody;
  }
  if (revision.ocrSource === 'raw' || revision.mode === 'replacements') {
    let text = revision.ocrSource === 'raw' ? fromOcrBaseline(revision, page, baseline) : ocrBody;
    const applied = [];
    for (const replacement of revision.replacements ?? []) {
      const occurrences = text.split(replacement.from).length - 1;
      if (occurrences !== 1) {
        throw new Error(
          `${source} page ${page}: replacement matched ${occurrences} times, expected exactly 1 -> ${JSON.stringify(replacement.from)}`
        );
      }
      text = text.replace(replacement.from, replacement.to);
      applied.push(replacement.to);
    }
    if (applied.length === 0 && revision.ocrSource !== 'raw') {
      throw new Error(`${source} page ${page}: replacements mode needs at least one replacement`);
    }
    return text.trim();
  }
  throw new Error(`${source} page ${page}: page needs textFile, mode "verbatim", mode "replacements" or ocrSource "raw"`);
}

const lessonDir = path.join(outputDir, 'lessons');
fs.mkdirSync(lessonDir, { recursive: true });

// --preview-ocr 12 13 prints the mechanically de-spaced baseline for those textbook pages,
// so the reviewer can compare it with the rendered page before listing corrections.
const previewIndex = process.argv.indexOf('--preview-ocr');
if (previewIndex >= 0) {
  for (const value of process.argv.slice(previewIndex + 1)) {
    const page = Number(value);
    console.log(`===== textbook page ${page} =====`);
    console.log(despace(String(baseline[String(page)] ?? '')));
  }
  process.exit(0);
}

const accepted = [];
const summary = [];
for (const lesson of revisions.lessons) {
  const sourcePath = path.join(sourceDir, lesson.source);
  const sourceText = fs.readFileSync(sourcePath, 'utf8');
  const title = (sourceText.match(/^#\s*课时标题[：:]\s*(.+?)\s*$/m) ?? [])[1];
  if (!title) throw new Error(`missing lesson title: ${lesson.source}`);
  const ocrBlocks = readPageBlocks(sourceText);

  const pages = lesson.pages
    .map(page => {
      const block = ocrBlocks.get(page.page);
      if (!block && page.ocrSource !== 'raw' && !page.textFile) {
        throw new Error(`${lesson.source}: no OCR block for textbook page ${page.page}`);
      }
      if (block && page.pdfPage && block.pdfPage !== page.pdfPage) {
        throw new Error(`${lesson.source}: page ${page.page} declares PDF page ${block.pdfPage}, revision says ${page.pdfPage}`);
      }
      const pdfPage = block ? block.pdfPage : page.pdfPage;
      if (!Number.isInteger(pdfPage) || pdfPage <= 0) {
        throw new Error(`${lesson.source}: page ${page.page} has no PDF page; declare pdfPage in the revision`);
      }
      if (Number.isInteger(offset) && pdfPage !== page.page + offset) {
        throw new Error(`${lesson.source}: page ${page.page} maps to PDF page ${page.page + offset}, but the revision says ${pdfPage}`);
      }
      const body = applyRevision(lesson.source, page.page, page, block?.body ?? '', revisionsDir, baseline);
      if (!body.trim()) throw new Error(`${lesson.source}: page ${page.page} produced empty text`);
      return { page: page.page, pdfPage, body, note: page.note ?? '' };
    })
    .sort((left, right) => left.page - right.page);

  const first = pages[0];
  const last = pages.at(-1);
  const pageRange = first.page === last.page ? `第 ${first.page} 页` : `第 ${first.page}–${last.page} 页`;
  const pdfRange = first.pdfPage === last.pdfPage ? `${first.pdfPage}` : `${first.pdfPage}–${last.pdfPage}`;
  const baseName = lesson.source.slice(0, -3);
  const targetName = `${baseName}-已核验页${first.page}-${last.page}.md`;

  const content = [
    `# 课时标题：${title}（已核验教材${pageRange}选段）`,
    '',
    `- 教材页码：${pageRange}`,
    `- 教材 PDF 页码：第 ${pdfRange} 页`,
    `- 来源文件：${lesson.source}`,
    `- 原教材 PDF SHA-256：${revisions.pdf?.sha256 ?? '未记录'}`,
    `- 核验日期：${reviewedOn}`,
    '- 接入说明：仅包含对照原书扫描 PDF 逐页核验的页面；未包含的页面不得推断为已核验。',
    '',
    pages
      .map(page => [
        `### 教材页 ${page.page}（PDF页 ${page.pdfPage}）`,
        '',
        `${REVIEWED_LABEL_PREFIX}${reviewedOn}）`,
        '',
        page.body,
      ].join('\n'))
      .join('\n\n'),
    '',
  ].join('\n');

  const targetPath = path.join(lessonDir, targetName);
  if (!dryRun) fs.writeFileSync(targetPath, content, 'utf8');

  accepted.push({
    source: lesson.source,
    sourceSha256: sha256(fs.readFileSync(sourcePath)),
    target: `lessons/${targetName}`,
    targetSha256: sha256(Buffer.from(content, 'utf8')),
    pageRange,
    pages: pages.map(page => ({ page: page.page, pdfPage: page.pdfPage, note: page.note })),
  });
  summary.push({
    lesson: baseName,
    target: targetName,
    pageRange,
    pdfRange,
    pages: pages.length,
    verbatim: lesson.pages.filter(page => page.mode === 'verbatim').length,
    revised: lesson.pages.filter(page => page.mode !== 'verbatim').length,
  });
}

const manifest = {
  version: 2,
  policy: 'Only pages listed here may become classroom material; each page was compared with the scanned textbook PDF. The loader refuses anything missing, re-hashed or page-mismatched.',
  reviewedOn,
  pdf: revisions.pdf ?? {},
  sharedPageAttribution: revisions.sharedPageAttribution ?? {},
  accepted,
  quarantined: revisions.quarantined ?? [],
};
if (!dryRun) {
  fs.writeFileSync(path.join(outputDir, 'import-manifest.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');
}

const totalPages = accepted.reduce((sum, entry) => sum + entry.pages.length, 0);
const verbatim = summary.reduce((sum, row) => sum + row.verbatim, 0);
console.log(`${dryRun ? '[dry-run] ' : ''}lessons=${accepted.length} pages=${totalPages} verbatim=${verbatim} revised=${totalPages - verbatim}`);
for (const row of summary) {
  console.log(`  ${row.lesson} -> ${row.target} | ${row.pageRange} (PDF ${row.pdfRange}) | pages=${row.pages} verbatim=${row.verbatim} revised=${row.revised}`);
}
