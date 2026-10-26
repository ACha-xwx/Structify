/**
 * After repackage, add missing imports for moved types referenced by short name.
 * Also strip clearly unused polluted imports from simple record/enum files.
 *
 * Usage: node scripts/fix-moved-type-imports.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const moves = JSON.parse(
  fs.readFileSync(path.join(root, 'scripts', 'moved-dto-entity-config.json'), 'utf8'),
);
const javaRoot = path.join(root, 'backend', 'spring', 'src', 'main', 'java');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

function packageOf(text) {
  const m = text.match(/^package\s+([\w.]+)\s*;/m);
  return m ? m[1] : null;
}

function importsOf(text) {
  const set = new Set();
  for (const m of text.matchAll(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm)) {
    set.add(m[1]);
  }
  return set;
}

const bySimple = new Map();
for (const m of moves) {
  bySimple.set(m.newSimple, m);
  // also map old simple if renamed
  if (m.oldSimple !== m.newSimple) bySimple.set(m.oldSimple, m);
}

let filesFixed = 0;
for (const file of walk(javaRoot)) {
  let text = fs.readFileSync(file, 'utf8');
  const pkg = packageOf(text);
  if (!pkg) continue;
  const existing = importsOf(text);
  const needed = [];

  for (const m of moves) {
    const simple = m.newSimple;
    // Skip if this file IS the type
    if (pkg === m.newPkg && path.basename(file, '.java') === simple) continue;
    // Same package — no import needed
    if (pkg === m.newPkg) continue;
    // Already imported
    if (existing.has(m.newFqcn)) continue;
    // Referenced by short name?
    const re = new RegExp(`\\b${simple}\\b`);
    if (!re.test(text)) continue;
    needed.push(m.newFqcn);
  }

  if (needed.length === 0) continue;

  // Insert after package + existing imports
  const unique = [...new Set(needed)].sort();
  const importBlock = unique.map((fq) => `import ${fq};`).join('\n') + '\n';
  const pkgMatch = text.match(/^package\s+[\w.]+\s*;\s*\n/);
  if (!pkgMatch) continue;
  let insertAt = pkgMatch[0].length;
  // Skip blank lines after package
  while (text[insertAt] === '\n') insertAt++;
  // Find end of import section
  const importSection = text.slice(insertAt).match(/^(?:import\s+.+;\s*\n)+/);
  if (importSection) {
    insertAt += importSection[0].length;
  }
  text = text.slice(0, insertAt) + importBlock + text.slice(insertAt);
  fs.writeFileSync(file, text);
  filesFixed++;
}

console.log(`Added imports in ${filesFixed} files`);

// Clean polluted imports from simple type files (record/enum only, small bodies)
const pollution = [
  'org.springframework.stereotype.Service',
  'org.springframework.stereotype.Repository',
  'org.springframework.transaction.annotation.Transactional',
  'org.springframework.context.ApplicationEventPublisher',
  'org.springframework.jdbc.core.JdbcTemplate',
  'org.springframework.http.HttpStatus',
  'com.feng.dsagent.common.exception.ApiException',
  'com.feng.dsagent.knowledge.KnowledgeEligibilityChanged',
  'com.feng.dsagent.security.AuthenticatedUser',
  'com.feng.dsagent.review.ReviewRepository',
];

let cleaned = 0;
for (const file of walk(javaRoot)) {
  const base = path.basename(file);
  // Only clean vo/entity/dto/prompt/exception/config simple carriers + enums at module root that look polluted
  if (!/(\\|\/)(vo|entity|dto|prompt|exception)(\\|\/)/.test(file)
    && !/(ReviewType|UserStatus)\.java$/.test(base)) {
    continue;
  }
  let text = fs.readFileSync(file, 'utf8');
  const before = text;
  for (const fq of pollution) {
    text = text.replace(new RegExp(`^import\\s+${fq.replace(/\./g, '\\.')}\\s*;\\r?\\n`, 'gm'), '');
  }
  // Drop clearly unused java.util noise from tiny records if unused
  if (/^public record /.test(text.split('\n').find((l) => l.startsWith('public ')) || '')
    || /^public enum /.test(text.split('\n').find((l) => l.startsWith('public ')) || '')) {
    for (const unused of [
      'java.util.ArrayList',
      'java.util.Locale',
      'java.util.Optional',
      'java.sql.Timestamp',
      'java.util.LinkedHashSet',
      'java.util.List',
      'java.util.Set',
    ]) {
      if (!text.includes(unused.split('.').pop()) || !new RegExp(`\\b${unused.split('.').pop()}\\b`).test(
        text.replace(/^import .*$/gm, ''),
      )) {
        text = text.replace(new RegExp(`^import\\s+${unused.replace(/\./g, '\\.')}\\s*;\\r?\\n`, 'gm'), '');
      }
    }
  }
  if (text !== before) {
    fs.writeFileSync(file, text);
    cleaned++;
  }
}
console.log(`Cleaned imports in ${cleaned} simple type files`);
