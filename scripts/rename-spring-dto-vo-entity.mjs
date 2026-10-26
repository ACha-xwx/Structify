#!/usr/bin/env node
/**
 * Batch-rename Spring types in ..dto.. / ..vo.. / ..entity.. per backend-standards:
 * - dto: *Request → *DTO; else append DTO
 * - vo:  *View → *VO; *Response → *VO; else append VO
 * - entity: append Entity if missing
 *
 * Updates file names, type declarations, and refs in main+test (.java) and mapper XML.
 * Does not change JSON field / record component names (only type identifiers).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPRING_ROOT = path.resolve(__dirname, '../backend/spring');
const SRC_ROOT = path.join(SPRING_ROOT, 'src');

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

function packageKind(filePath) {
  const norm = filePath.replace(/\\/g, '/');
  if (/\/dto\//.test(norm)) return 'dto';
  if (/\/vo\//.test(norm)) return 'vo';
  if (/\/entity\//.test(norm)) return 'entity';
  return null;
}

function targetName(kind, oldName) {
  if (kind === 'dto') {
    if (oldName.endsWith('DTO')) return oldName;
    if (oldName.endsWith('Request')) return `${oldName.slice(0, -'Request'.length)}DTO`;
    return `${oldName}DTO`;
  }
  if (kind === 'vo') {
    if (oldName.endsWith('VO')) return oldName;
    if (oldName.endsWith('View')) return `${oldName.slice(0, -'View'.length)}VO`;
    if (oldName.endsWith('Response')) return `${oldName.slice(0, -'Response'.length)}VO`;
    return `${oldName}VO`;
  }
  if (kind === 'entity') {
    if (oldName.endsWith('Entity')) return oldName;
    return `${oldName}Entity`;
  }
  return oldName;
}

function collectRenames() {
  const javaFiles = walk(SRC_ROOT).filter((f) => f.endsWith('.java'));
  const renames = [];
  const seenNew = new Map();

  for (const file of javaFiles) {
    const kind = packageKind(file);
    if (!kind) continue;
    const oldName = path.basename(file, '.java');
    const newName = targetName(kind, oldName);
    if (oldName === newName) continue;
    if (seenNew.has(newName)) {
      throw new Error(
        `Collision: ${oldName} and ${seenNew.get(newName)} both map to ${newName}`,
      );
    }
    seenNew.set(newName, oldName);
    renames.push({ kind, oldName, newName, file });
  }

  // Longest first so AdminAuditEventView is replaced before AdminAuditEvent, etc.
  renames.sort((a, b) => b.oldName.length - a.oldName.length || a.oldName.localeCompare(b.oldName));
  return renames;
}

function replaceTypeIdentifiers(content, renames) {
  let next = content;
  for (const { oldName, newName } of renames) {
    // Word-boundary style for Java identifiers: not preceded/followed by [A-Za-z0-9_]
    const re = new RegExp(`(?<![A-Za-z0-9_])${oldName}(?![A-Za-z0-9_])`, 'g');
    next = next.replace(re, newName);
  }
  return next;
}

function main() {
  const dryRun = process.argv.includes('--dry-run');
  const renames = collectRenames();

  if (renames.length === 0) {
    console.log('No renames needed.');
    return;
  }

  console.log(`Planned renames: ${renames.length}`);
  const byKind = { dto: 0, vo: 0, entity: 0 };
  for (const r of renames) {
    byKind[r.kind] += 1;
    console.log(`  [${r.kind}] ${r.oldName} → ${r.newName}`);
  }
  console.log(
    `Counts: dto=${byKind.dto} vo=${byKind.vo} entity=${byKind.entity} total=${renames.length}`,
  );

  if (dryRun) return;

  // 1) Rewrite content in all src files that may reference these types
  const rewriteExt = new Set(['.java', '.xml']);
  const allFiles = walk(SRC_ROOT).filter((f) => rewriteExt.has(path.extname(f)));

  let filesTouched = 0;
  for (const file of allFiles) {
    const original = fs.readFileSync(file, 'utf8');
    const updated = replaceTypeIdentifiers(original, renames);
    if (updated !== original) {
      fs.writeFileSync(file, updated, 'utf8');
      filesTouched += 1;
    }
  }
  console.log(`Updated content in ${filesTouched} files`);

  // 2) Rename files (after content rewrite so paths still find originals)
  for (const { file, newName } of renames) {
    const dir = path.dirname(file);
    const dest = path.join(dir, `${newName}.java`);
    if (fs.existsSync(dest)) {
      throw new Error(`Destination already exists: ${dest}`);
    }
    fs.renameSync(file, dest);
  }
  console.log(`Renamed ${renames.length} type files`);

  // Write a summary sidecar for humans
  const summaryPath = path.join(__dirname, 'rename-spring-dto-vo-entity.summary.json');
  fs.writeFileSync(
    summaryPath,
    JSON.stringify(
      {
        counts: byKind,
        total: renames.length,
        filesTouched,
        renames: renames.map(({ kind, oldName, newName }) => ({ kind, oldName, newName })),
      },
      null,
      2,
    ),
    'utf8',
  );
  console.log(`Wrote ${summaryPath}`);
}

main();
