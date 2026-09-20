// Prints the OCR page body for authoring revision records, as a JSON-escaped string.
// Usage: node scripts/dump-ocr-page.mjs <lessonFile> <page> [<page> ...]
import fs from 'node:fs';
import path from 'node:path';

const sourceDir = 'F:/data-structure-agent/data-structure-agent/lesson-materials/lessons';
const [lessonFile, ...pages] = process.argv.slice(2);
const text = fs.readFileSync(path.join(sourceDir, lessonFile), 'utf8').replace(/\r\n?/g, '\n');
const lines = text.split('\n');
const blocks = new Map();
for (let i = 0; i < lines.length; i++) {
  const header = lines[i].trim().match(/^### 教材页 (\d+)（PDF页 (\d+)）$/);
  if (!header) continue;
  let end = i + 1;
  while (end < lines.length && !/^### 教材页 /.test(lines[end].trim()) && !lines[end].startsWith('## ')) end++;
  const body = lines.slice(i + 1, end)
    .filter(line => !line.trim().startsWith('> OCR质量：'))
    .join('\n').replace(/\n{3,}/g, '\n\n').trim();
  blocks.set(Number(header[1]), body);
  i = end - 1;
}
for (const page of pages) {
  const body = blocks.get(Number(page));
  console.log(`===== ${lessonFile} page ${page} =====`);
  console.log(JSON.stringify(body));
}
