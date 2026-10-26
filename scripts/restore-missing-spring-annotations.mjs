import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

const ANNS = [
  {
    name: '@Repository',
    importLine: 'import org.springframework.stereotype.Repository;',
    classRe: /^(public class \w+)\b/m,
  },
  {
    name: '@Component',
    importLine: 'import org.springframework.stereotype.Component;',
    classRe: /^(public class \w+)\b/m,
  },
  {
    name: '@Configuration',
    importLine: 'import org.springframework.context.annotation.Configuration;',
    classRe: /^(public class \w+)\b/m,
  },
  {
    name: '@RestController',
    importLine: 'import org.springframework.web.bind.annotation.RestController;',
    classRe: /^(public class \w+Controller)\b/m,
  },
  {
    name: '@Service',
    importLine: 'import org.springframework.stereotype.Service;',
    classRe: /^(public class \w+Service)\b/m,
  },
];

let fixed = 0;
for (const file of walk('backend/spring/src/main/java')) {
  const rel = file.replace(/\\/g, '/');
  let head;
  try {
    head = execSync(`git show HEAD:${rel}`, { encoding: 'utf8' });
  } catch {
    continue;
  }
  let text = fs.readFileSync(file, 'utf8');
  let changed = false;
  for (const ann of ANNS) {
    if (!head.includes(ann.name)) continue;
    if (text.includes(ann.name)) continue;
    if (!text.includes(ann.importLine) && head.includes(ann.importLine.split(' ').pop().replace(';', ''))) {
      // ensure import if HEAD had that import package
    }
    if (!text.includes(ann.importLine)) {
      text = text.replace(/^(package .+;\s*\n)/m, `$1\n${ann.importLine}\n`);
    }
    if (ann.classRe.test(text)) {
      text = text.replace(ann.classRe, `${ann.name}\n$1`);
      changed = true;
      console.log(`restored ${ann.name}`, rel);
    }
  }
  if (changed) {
    fs.writeFileSync(file, text);
    fixed++;
  }
}
console.log('files fixed', fixed);
