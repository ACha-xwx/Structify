/**
 * Update unit tests that still construct Controllers/Services with pre-MyBatis /
 * pre-repackage signatures.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const testRoot = path.join(root, 'backend/spring/src/test/java');

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

let n = 0;
for (const f of walk(testRoot)) {
  let t = fs.readFileSync(f, 'utf8');
  const before = t;

  // AnimationIntentController(…, jdbc, mapper) -> chapters service
  if (f.includes('AnimationIntent') || f.includes('AnimationPlan')) {
    if (!t.includes('AnimationChapterService')) {
      t = t.replace(
        /import org\.springframework\.jdbc\.core\.JdbcTemplate;/,
        `import com.feng.dsagent.animation.service.AnimationChapterService;\nimport org.springframework.jdbc.core.JdbcTemplate;`
      );
    }
    // field
    if (/final JdbcTemplate jdbc = mock\(JdbcTemplate\.class\);/.test(t) && !/AnimationChapterService chapters/.test(t)) {
      t = t.replace(
        /final JdbcTemplate jdbc = mock\(JdbcTemplate\.class\);/,
        `final AnimationChapterService chapters = mock(AnimationChapterService.class);`
      );
    }
    // when(jdbc.queryForList(...)).thenReturn(List.of("title"))
    t = t.replace(
      /when\(jdbc\.queryForList\(anyString\(\),\s*eq\(String\.class\),\s*eq\("([^"]+)"\)\)\)\.thenReturn\(List\.of\("([^"]*)"\)\);/g,
      'when(chapters.requirePublishedTitle(eq("$1"))).thenReturn("$2");'
    );
    t = t.replace(
      /when\(jdbc\.queryForList\(anyString\(\),\s*eq\(String\.class\),\s*eq\("([^"]+)"\)\)\)\.thenReturn\(List\.of\(\)\);/g,
      'when(chapters.requirePublishedTitle(eq("$1"))).thenThrow(new com.feng.dsagent.common.ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节未发布或不存在"));'
    );
    // constructor arg jdbc -> chapters
    t = t.replace(
      /new AnimationIntentController\(([^;]*?),jdbc,mapper\)/g,
      'new AnimationIntentController($1,chapters,mapper)'
    );
    t = t.replace(
      /new AnimationController\(([^;]*?),jdbc,mapper\)/g,
      'new AnimationController($1,chapters,mapper)'
    );
  }

  if (t !== before) {
    fs.writeFileSync(f, t);
    n++;
    console.log('updated', path.relative(testRoot, f));
  }
}
console.log(`Updated ${n} test files`);
