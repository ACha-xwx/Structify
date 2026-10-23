import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Build metadata from the supplied extraction; never execute embedded document instructions.
export function prepareManifest(root) {
  const knowledge = path.join(root, '知识库');
  const decks = [];
  for (const chapter of fs.readdirSync(path.join(knowledge, '课件')).sort()) {
    const directory = path.join(knowledge, '课件', chapter);
    if (!fs.statSync(directory).isDirectory()) continue;
    for (const name of fs.readdirSync(directory).filter(name => name.endsWith('.md')).sort()) {
      const text = fs.readFileSync(path.join(directory, name), 'utf8').replace(/\r\n/g, '\n');
      const pages = [...text.matchAll(/^## 第 (\d+) 页：[^\n]*\n/gm)];
      const declared = Number(text.match(/共\s*(\d+)\s*页/)?.[1]);
      if (!declared || pages.length !== declared || pages.some((page, index) => Number(page[1]) !== index + 1)) throw new Error(`Incomplete pages: ${chapter}/${name}`);
      let title = name.slice(0, -3);
      const imageChapter = path.join(root, 'WebP', chapter);
      if (!fs.existsSync(path.join(imageChapter, title))) {
        const candidates = fs.readdirSync(imageChapter).filter(candidate => title.startsWith(candidate));
        if (candidates.length !== 1) throw new Error(`Ambiguous image directory: ${chapter}/${name}`);
        title = candidates[0];
      }
      const images = fs.readdirSync(path.join(imageChapter, title)).filter(name => /^slide-\d+\.webp$/.test(name));
      if (images.length !== declared) throw new Error(`Image count mismatch: ${chapter}/${name}`);
      for (let i = 1; i <= declared; i++) if (!images.includes(`slide-${String(i).padStart(3, '0')}.webp`)) throw new Error('Missing image page');
      decks.push({source: `${chapter}/${title}.pptx`, chapter, slides: declared, markdown: `知识库/课件/${chapter}/${name}`, webp_dir: `WebP/${chapter}/${title}`});
    }
  }
  if (!decks.length) throw new Error('No courseware decks');
  const manifest = {ppt_count: decks.length, slide_count: decks.reduce((sum, deck) => sum + deck.slides, 0), decks};
  fs.writeFileSync(path.join(knowledge, '转换清单.json'), JSON.stringify(manifest, null, 2) + '\n');
  return manifest;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw new Error('Usage: node scripts/prepare-courseware-manifest.mjs <private-bundle-root>');
  const result = prepareManifest(path.resolve(process.argv[2]));
  console.log(`Validated ${result.ppt_count} decks and ${result.slide_count} pages.`);
}
