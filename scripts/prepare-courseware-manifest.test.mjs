import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {prepareManifest} from './prepare-courseware-manifest.mjs';

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'structify-courseware-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  const markdown = path.join(root, '知识库/课件/第四章串');
  const images = path.join(root, 'WebP/第四章串/第四章-串');
  fs.mkdirSync(markdown, {recursive:true});fs.mkdirSync(images, {recursive:true});
  fs.writeFileSync(path.join(markdown, '第四章-串-统一风格版.md'), '# 串\n共 1 页。\n## 第 1 页：串\n字符序列\n');
  fs.writeFileSync(path.join(images, 'slide-001.webp'), 'synthetic');
  return {root, images};
}
test('matches a renamed markdown deck to its unique image directory', t => {
  const {root} = fixture(t);const manifest = prepareManifest(root);
  assert.equal(manifest.slide_count, 1);
  assert.equal(manifest.decks[0].webp_dir, 'WebP/第四章串/第四章-串');
});
test('rejects missing images before writing a release manifest', t => {
  const {root, images} = fixture(t);fs.unlinkSync(path.join(images, 'slide-001.webp'));
  assert.throws(() => prepareManifest(root), /Image count mismatch/);
  assert.equal(fs.existsSync(path.join(root, '知识库/转换清单.json')), false);
});
