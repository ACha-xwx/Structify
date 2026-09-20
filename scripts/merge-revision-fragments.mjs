// Merges per-lesson fragment records (revisions/fragments/*.json) into page-revisions.json.
//
// Each fragment is written by an independent review pass and looks like:
//   { "source": "03-03-栈与队列-队列定义和表示实现.md",
//     "pages": [ { "page": 97, "pdfPage": 108, "textFile": "pages/03-03-p097.md", "note": "..." } ] }
//
// The script validates the page->PDF offset, the existence of every textFile, replaces the
// lesson entry wholesale (so re-running a review pass is idempotent), and stamps resolvedOn
// on quarantined entries whose source has been properly re-recorded.
import fs from 'node:fs';
import path from 'node:path';

const root = 'private/reviewed-textbook/revisions';
const revisionsPath = path.join(root, 'page-revisions.json');
const fragmentsDir = path.join(root, 'fragments');
const OFFSET = 11;
const resolvedOn = new Date().toISOString().slice(0, 10);

const revisions = JSON.parse(fs.readFileSync(revisionsPath, 'utf8'));
const bySource = new Map(revisions.lessons.map(lesson => [lesson.source, lesson]));

const fragments = fs.readdirSync(fragmentsDir).filter(name => name.endsWith('.json')).sort();
let added = 0;
let replaced = 0;
for (const name of fragments) {
  const fragment = JSON.parse(fs.readFileSync(path.join(fragmentsDir, name), 'utf8'));
  if (!fragment.source || !Array.isArray(fragment.pages) || fragment.pages.length === 0) {
    throw new Error(`${name}: fragment must carry source and a non-empty pages array`);
  }
  const pages = fragment.pages.map(page => {
    if (page.pdfPage !== page.page + OFFSET) {
      throw new Error(`${name}: page ${page.page} must map to PDF page ${page.page + OFFSET}, got ${page.pdfPage}`);
    }
    const file = path.resolve(root, page.textFile);
    if (!fs.existsSync(file)) throw new Error(`${name}: missing textFile ${page.textFile}`);
    if (!fs.readFileSync(file, 'utf8').trim()) throw new Error(`${name}: empty textFile ${page.textFile}`);
    return {
      page: page.page,
      pdfPage: page.pdfPage,
      textFile: page.textFile,
      ...(page.note ? { note: page.note } : {}),
    };
  }).sort((left, right) => left.page - right.page);

  const pagesSeen = new Set(pages.map(page => page.page));
  if (pagesSeen.size !== pages.length) throw new Error(`${name}: repeated page in fragment`);
  if (bySource.has(fragment.source)) replaced++; else added++;
  bySource.set(fragment.source, { source: fragment.source, pages });
}

revisions.lessons = [...bySource.values()].sort((left, right) => {
  const [lc, lp] = left.source.split('-');
  const [rc, rp] = right.source.split('-');
  return Number(lc) - Number(rc) || Number(lp) - Number(rp);
});

// A quarantined source that now carries a full verified revision is resolved.
for (const entry of revisions.quarantined ?? []) {
  if (bySource.has(entry.source) && !entry.resolvedOn) entry.resolvedOn = resolvedOn;
}

revisions.reviewedOn = resolvedOn;
fs.writeFileSync(revisionsPath, JSON.stringify(revisions, null, 2) + '\n', 'utf8');
console.log(`merged ${fragments.length} fragments: ${added} added, ${replaced} replaced; lessons total ${revisions.lessons.length}`);
for (const lesson of revisions.lessons) {
  console.log(`  ${lesson.source}  pages ${lesson.pages[0].page}-${lesson.pages.at(-1).page} (${lesson.pages.length})`);
}
