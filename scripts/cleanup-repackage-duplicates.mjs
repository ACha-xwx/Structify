/**
 * Prefer *.controller / *.service copies; delete restored same-named types in parent package.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const javaRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../backend/spring/src/main/java/com/feng/dsagent'
);

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function pkg(file) {
  const m = fs.readFileSync(file, 'utf8').match(/^package\s+([\w.]+)\s*;/m);
  return m?.[1];
}

const files = walk(javaRoot);
const byName = new Map();
for (const f of files) {
  const name = path.basename(f, '.java');
  if (!name.endsWith('Controller') && !name.endsWith('Service')) continue;
  if (!byName.has(name)) byName.set(name, []);
  byName.get(name).push(f);
}

let removed = 0;
for (const [name, list] of byName) {
  if (list.length < 2) continue;
  const preferred = list.filter((f) => {
    const p = pkg(f) || '';
    return p.endsWith('.controller') || p.endsWith('.service');
  });
  const obsolete = list.filter((f) => !preferred.includes(f));
  if (!preferred.length) continue;
  for (const f of obsolete) {
    fs.unlinkSync(f);
    removed++;
    console.log('removed', f);
  }
}

// Prefer common ModelFeatureRateLimiter
const modelLimiter = path.join(javaRoot, 'model', 'ModelFeatureRateLimiter.java');
const commonLimiter = path.join(javaRoot, 'common', 'ModelFeatureRateLimiter.java');
if (fs.existsSync(modelLimiter) && fs.existsSync(commonLimiter)) {
  fs.unlinkSync(modelLimiter);
  removed++;
  console.log('removed', modelLimiter);
}

console.log(`Removed ${removed} duplicate files`);
