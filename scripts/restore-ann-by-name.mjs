import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

const IMPORTS = {
  '@Repository': 'import org.springframework.stereotype.Repository;',
  '@Component': 'import org.springframework.stereotype.Component;',
  '@Configuration': 'import org.springframework.context.annotation.Configuration;',
  '@RestController': 'import org.springframework.web.bind.annotation.RestController;',
  '@Service': 'import org.springframework.stereotype.Service;',
};

const byName = new Map();
for (const f of walk('backend/spring/src/main/java')) {
  byName.set(path.basename(f, '.java'), f);
}

const headFiles = execSync('git ls-tree -r --name-only HEAD backend/spring/src/main/java', {
  encoding: 'utf8',
})
  .trim()
  .split(/\n/);

let fixed = 0;
for (const hf of headFiles) {
  if (!hf.endsWith('.java')) continue;
  const simple = path.basename(hf, '.java');
  const cur = byName.get(simple);
  if (!cur) continue;
  const head = execSync(`git show HEAD:${hf}`, { encoding: 'utf8' });
  let text = fs.readFileSync(cur, 'utf8');
  let changed = false;
  for (const [ann, importLine] of Object.entries(IMPORTS)) {
    if (!head.includes(ann) || text.includes(ann)) continue;
    if (!text.includes(importLine)) {
      text = text.replace(/^(package .+;\s*\n)/m, `$1\n${importLine}\n`);
    }
    // Place annotation immediately before the top-level public type
    text = text.replace(
      /^(public (?:final )?class \w+\b)/m,
      `${ann}\n$1`,
    );
    changed = true;
    console.log('restored', ann, cur);
  }
  if (changed) {
    fs.writeFileSync(cur, text);
    fixed++;
  }
}
console.log('fixed files', fixed);
