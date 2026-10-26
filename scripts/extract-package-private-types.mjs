/**
 * Extract package-private top-level types that share a file with another type
 * into their own public *.java files (needed after controller/service split).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../backend/spring/src/main/java'
);

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function extractTopLevelTypes(text) {
  // Split on lines that start a top-level type (no indent)
  const lines = text.split(/\n/);
  const pkg = (text.match(/^package\s+([\w.]+)\s*;/m) || [])[1];
  const imports = [...text.matchAll(/^import\s+.+;$/gm)].map((m) => m[0]);
  const chunks = [];
  let current = null;
  let brace = 0;
  let started = false;
  for (const line of lines) {
    const typeStart = /^(?:public\s+)?(?:final\s+)?(class|interface|enum|record)\s+(\w+)/.exec(line);
    if (typeStart && brace === 0 && (!current || started)) {
      if (current) chunks.push(current);
      current = { kind: typeStart[1], name: typeStart[2], lines: [line], public: /^public\s/.test(line) };
      started = true;
      brace += (line.match(/\{/g) || []).length;
      brace -= (line.match(/\}/g) || []).length;
      continue;
    }
    if (!current) continue;
    current.lines.push(line);
    brace += (line.match(/\{/g) || []).length;
    brace -= (line.match(/\}/g) || []).length;
  }
  if (current) chunks.push(current);
  return { pkg, imports, chunks };
}

let created = 0;
for (const file of walk(root)) {
  const text = fs.readFileSync(file, 'utf8');
  const { pkg, imports, chunks } = extractTopLevelTypes(text);
  if (!pkg || chunks.length < 2) continue;
  // Keep first chunk in original file; extract the rest if not public or always extract non-matching filename
  const primaryName = path.basename(file, '.java');
  const primary = chunks.find((c) => c.name === primaryName) || chunks[0];
  const extras = chunks.filter((c) => c !== primary);
  if (!extras.length) continue;

  // Rebuild primary file
  const header = [`package ${pkg};`, '', ...imports, ''].join('\n');
  const primaryBody = primary.lines.join('\n').replace(/^(?:public\s+)?/, 'public ');
  fs.writeFileSync(file, `${header}\n${primaryBody}\n`);

  for (const extra of extras) {
    const dest = path.join(path.dirname(file), `${extra.name}.java`);
    if (fs.existsSync(dest) && path.resolve(dest) !== path.resolve(file)) {
      // already exists — skip overwrite of unrelated
      console.warn('skip existing', dest);
      continue;
    }
    const body = extra.lines.join('\n').replace(/^(?:public\s+)?/, 'public ');
    fs.writeFileSync(dest, `${header}\n${body}\n`);
    created++;
    console.log('extracted', dest);
  }
}
console.log(`Created ${created} type files`);
