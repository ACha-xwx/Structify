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

let fixed = 0;
for (const file of walk('backend/spring/src/main/java').filter((f) => f.endsWith('Service.java'))) {
  const rel = file.replace(/\\/g, '/');
  let head;
  try {
    head = execSync(`git show HEAD:${rel}`, { encoding: 'utf8' });
  } catch {
    continue;
  }
  if (!/@Service/.test(head)) continue;
  let text = fs.readFileSync(file, 'utf8');
  if (/@Service/.test(text)) continue;
  if (!text.includes('import org.springframework.stereotype.Service;')) {
    text = text.replace(/^(package .+;\s*\n)/m, `$1\nimport org.springframework.stereotype.Service;\n`);
  }
  text = text.replace(/^(public class \w+Service\b)/m, '@Service\n$1');
  fs.writeFileSync(file, text);
  console.log('restored @Service', rel);
  fixed++;
}
console.log('fixed', fixed);
