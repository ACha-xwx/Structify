/**
 * Move *Controller / *Service into sibling controller/ and service/ packages
 * under each capability module, then rewrite imports/FQCN references.
 *
 * Usage (repo root): node scripts/repackage-controller-service.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoot = path.join(root, 'backend', 'spring', 'src');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

function packageOf(javaFile) {
  const text = fs.readFileSync(javaFile, 'utf8');
  const m = text.match(/^package\s+([\w.]+)\s*;/m);
  if (!m) throw new Error(`No package in ${javaFile}`);
  return m[1];
}

function rewritePackage(javaFile, newPkg) {
  let text = fs.readFileSync(javaFile, 'utf8');
  text = text.replace(/^package\s+[\w.]+\s*;/m, `package ${newPkg};`);
  fs.writeFileSync(javaFile, text);
}

const moves = []; // { oldFqcn, newFqcn, from, to }

for (const base of ['main/java', 'test/java']) {
  const baseDir = path.join(javaRoot, base, 'com', 'feng', 'dsagent');
  const files = walk(baseDir).filter((f) => f.endsWith('.java'));
  for (const file of files) {
    const name = path.basename(file, '.java');
    const isController = name.endsWith('Controller');
    const isService = name.endsWith('Service');
    if (!isController && !isService) continue;

    const oldPkg = packageOf(file);
    const leaf = isController ? 'controller' : 'service';
    // Already in target leaf package
    if (oldPkg.endsWith(`.${leaf}`)) continue;

    const newPkg = `${oldPkg}.${leaf}`;
    const oldFqcn = `${oldPkg}.${name}`;
    const newFqcn = `${newPkg}.${name}`;
    const destDir = path.join(path.dirname(file), leaf);
    const dest = path.join(destDir, `${name}.java`);
    fs.mkdirSync(destDir, { recursive: true });
    fs.renameSync(file, dest);
    rewritePackage(dest, newPkg);
    moves.push({ oldFqcn, newFqcn, oldPkg, newPkg, name, from: file, to: dest });
  }
}

// Second pass: rewrite references in all java/xml/md under spring + docs pointers optional
const rewriteRoots = [
  path.join(root, 'backend', 'spring'),
];
const exts = new Set(['.java', '.xml', '.yml', '.yaml', '.md', '.properties']);

function rewriteFile(file) {
  let text = fs.readFileSync(file, 'utf8');
  let changed = false;
  // Longest FQCN first to avoid partial collisions
  const sorted = [...moves].sort((a, b) => b.oldFqcn.length - a.oldFqcn.length);
  for (const m of sorted) {
    if (text.includes(m.oldFqcn)) {
      text = text.split(m.oldFqcn).join(m.newFqcn);
      changed = true;
    }
    // Same-package short imports: import com.feng...OldPkg.Name → new
    const oldImport = `import ${m.oldPkg}.${m.name};`;
    const newImport = `import ${m.newPkg}.${m.name};`;
    if (text.includes(oldImport)) {
      text = text.split(oldImport).join(newImport);
      changed = true;
    }
  }
  if (changed) fs.writeFileSync(file, text);
}

for (const r of rewriteRoots) {
  for (const file of walk(r)) {
    if (!exts.has(path.extname(file))) continue;
    rewriteFile(file);
  }
}

// Fix same-package references inside moved files: Controllers often referenced SiblingService
// without import when previously same package. After move they need imports.
for (const m of moves) {
  if (!m.name.endsWith('Controller')) continue;
  const text = fs.readFileSync(m.to, 'utf8');
  // Find simple Service type usages that lack import — heuristic via sibling moves
  const modulePkg = m.oldPkg;
  const siblingServices = moves.filter(
    (s) => s.name.endsWith('Service') && s.oldPkg === modulePkg
  );
  let next = text;
  for (const s of siblingServices) {
    const importLine = `import ${s.newFqcn};`;
    if (next.includes(s.name) && !next.includes(importLine) && !next.includes(`import ${s.oldFqcn}`)) {
      next = next.replace(/^(package .+;\r?\n)/m, `$1\n${importLine}\n`);
    }
  }
  // Controllers in same old module referencing each other — rare
  if (next !== text) fs.writeFileSync(m.to, next);
}

// Services referencing sibling services/controllers in same old package
for (const m of moves) {
  if (!m.name.endsWith('Service')) continue;
  let text = fs.readFileSync(m.to, 'utf8');
  const siblingServices = moves.filter(
    (s) => s.name.endsWith('Service') && s.oldPkg === m.oldPkg && s.name !== m.name
  );
  let next = text;
  for (const s of siblingServices) {
    const importLine = `import ${s.newFqcn};`;
    if (next.includes(s.name) && !next.includes(importLine)) {
      next = next.replace(/^(package .+;\r?\n)/m, `$1\n${importLine}\n`);
    }
  }
  if (next !== text) fs.writeFileSync(m.to, next);
}

// Unmoved types in the old package that referenced Controllers/Services by simple name
const byOldPkg = new Map();
for (const m of moves) {
  if (!byOldPkg.has(m.oldPkg)) byOldPkg.set(m.oldPkg, []);
  byOldPkg.get(m.oldPkg).push(m);
}
for (const file of walk(path.join(javaRoot, 'main/java')).concat(walk(path.join(javaRoot, 'test/java')))) {
  if (!file.endsWith('.java')) continue;
  if (moves.some((m) => m.to === file)) continue;
  let pkg;
  try {
    pkg = packageOf(file);
  } catch {
    continue;
  }
  const siblings = byOldPkg.get(pkg);
  if (!siblings?.length) continue;
  let text = fs.readFileSync(file, 'utf8');
  let next = text;
  for (const s of siblings) {
    const importLine = `import ${s.newFqcn};`;
    // word-boundary-ish: type name appears and no import yet
    const re = new RegExp(`\\b${s.name}\\b`);
    if (re.test(next) && !next.includes(importLine) && !next.includes(`import ${s.oldFqcn}`)) {
      next = next.replace(/^(package .+;\r?\n)/m, `$1\n${importLine}\n`);
    }
  }
  if (next !== text) fs.writeFileSync(file, next);
}

console.log(`Moved ${moves.length} types:`);
for (const m of moves) {
  console.log(`  ${m.oldFqcn} -> ${m.newFqcn}`);
}
