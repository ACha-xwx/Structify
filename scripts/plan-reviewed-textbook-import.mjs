// DEPRECATED: superseded by scripts/build-reviewed-textbook.mjs, which writes the page-level
// revision record into private/reviewed-textbook/ and emits the version 2 import manifest that
// KnowledgeCorpusLoader enforces. This file still only prints a plan and its manifest shape
// (sourceSha256 only, no pageRange) is no longer accepted by the loader.
//
// Extract explicit, previously recorded review metadata; never infer OCR quality.
// Prints an import plan only. Does not modify source material or write destination files.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const sourceRoot = path.resolve(process.argv[2]);
const files = [];
const manifest = { sourceRoot, policy: 'Only pages explicitly marked as checked against the source PDF; no new review claimed.', accepted: [], excluded: [] };
for (const filename of fs.readdirSync(path.join(sourceRoot, 'lessons')).sort()) {
  if (!filename.endsWith('.md') || filename.startsWith('00-')) continue;
  const source = fs.readFileSync(path.join(sourceRoot, 'lessons', filename), 'utf8');
  const lines = source.split('\n').map(line => line.endsWith('\r') ? line.slice(0, -1) : line);
  const titleLine = lines.find(line => line.startsWith('# 课时标题：'));
  if (!titleLine) throw new Error(`Missing title metadata: ${filename}`);
  const pages = [];
  for (let i = 0; i < lines.length; i++) {
    const header = lines[i];
    if (!header.startsWith('### 教材页 ')) continue;
    const separator = header.indexOf('（PDF页 ');
    const page = Number(header.slice('### 教材页 '.length, separator));
    const pdfPage = Number(header.slice(separator + '（PDF页 '.length, header.indexOf('）', separator)));
    if (!Number.isInteger(page) || !Number.isInteger(pdfPage)) throw new Error(`Invalid page metadata: ${filename} ${header}`);
    let end = i + 1;
    while (end < lines.length && !lines[end].startsWith('### 教材页 ') && !lines[end].startsWith('## ')) end++;
    const block = lines.slice(i, end);
    const label = block.find(line => line.startsWith('> OCR质量：')) ?? '';
    if (!label.startsWith('> OCR质量：已对照原始教材 PDF 核验（') || !label.endsWith('）')) {
      manifest.excluded.push({ filename, page, label });
    } else {
      pages.push({ page, pdfPage, label, text: block.join('\n').trim() });
    }
    i = end - 1;
  }
  // Keep consecutive verified pages together; never imply coverage of an excluded gap.
  const groups = [];
  for (const page of pages) {
    let group = groups.at(-1);
    if (!group || group.at(-1).page + 1 !== page.page) groups.push(group = []);
    group.push(page);
  }
  for (const group of groups) {
    const first = group[0], last = group.at(-1);
    const range = first.page === last.page ? `${first.page}` : `${first.page}–${last.page}`;
    const pdfRange = first.pdfPage === last.pdfPage ? `${first.pdfPage}` : `${first.pdfPage}–${last.pdfPage}`;
    const target = `lessons/${filename.slice(0, -3)}-已核验页${first.page}-${last.page}.md`;
    const content = `${titleLine}（已核验教材第 ${range} 页选段）\n\n- 教材页码：第 ${range} 页\n- 教材 PDF 页码：第 ${pdfRange} 页\n- 来源文件：${filename}\n- 接入说明：仅包含此前明确标注已对照 PDF 核验的页面；未包含的页面不得推断为已核验。\n\n${group.map(page => page.text).join('\n\n')}\n`;
    files.push({ path: target, content });
    manifest.accepted.push({ source: filename, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), target, pages: group.map(({page,pdfPage,label}) => ({page,pdfPage,label})) });
  }
}
if (!files.length) throw new Error('No explicitly reviewed textbook pages found');
files.push({ path: 'import-manifest.json', content: JSON.stringify(manifest, null, 2) + '\n' });
process.stdout.write(JSON.stringify({ files, pageCount: manifest.accepted.reduce((n, group) => n + group.pages.length, 0), lessonCount: manifest.accepted.length, excludedCount: manifest.excluded.length }));
