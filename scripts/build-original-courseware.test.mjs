import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

test('OCR edits preserve registered page identity and prior IDs map by source and unchanged image', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'stable-courseware-'));
  try {
    fs.mkdirSync(path.join(root, '知识库'), { recursive: true });
    fs.mkdirSync(path.join(root, 'WebP', 'deck'), { recursive: true });
    fs.writeFileSync(path.join(root, 'WebP', 'deck', 'slide-001.webp'), Buffer.from('RIFF0000WEBPsynthetic'));
    fs.writeFileSync(path.join(root, '知识库', '转换清单.json'), JSON.stringify({
      slide_count: 1, ppt_count: 1, decks: [{ source: 'course/string.pptx', sourceAssetId: 'asset-string', nativeSlideIds: ['slide-256'], chapter: '第四章串', markdown: '知识库/string.md', webp_dir: 'WebP/deck', slides: 1 }],
    }));
    const build = (text) => {
      fs.writeFileSync(path.join(root, '知识库', 'string.md'), `## 第 1 页：串\n${text}\n`);
      const result = spawnSync(process.execPath, ['scripts/build-original-courseware.mjs', root], { encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      return JSON.parse(fs.readFileSync(path.join(root, 'WebP', 'slides.json'), 'utf8')).slides[0];
    };
    const before = build('OCR eror');
    const after = build('OCR error');
    assert.equal(before.id, after.id);
    assert.equal(after.sourceAssetId, 'asset-string');
    assert.equal(after.nativeSlideId, 'slide-256');
    assert.equal(after.imageHash, before.imageHash);
    assert.notEqual(after.documentRevision, before.documentRevision);
    assert.equal(after.semanticSummary, '', 'enhancement must not pretend truncated OCR is a summary');
    assert.ok(fs.existsSync(path.join(root, 'WebP', 'resource-identities.json')));
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

test('a provenance collision blocks a release before replacing published files', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'stable-courseware-'));
  try {
    fs.mkdirSync(path.join(root, '知识库'), { recursive: true });
    fs.mkdirSync(path.join(root, 'WebP', 'deck'), { recursive: true });
    fs.writeFileSync(path.join(root, 'WebP', 'deck', 'slide-001.webp'), Buffer.from('RIFF0000WEBPsynthetic'));
    fs.writeFileSync(path.join(root, '知识库', 'string.md'), '## 第 1 页：串\n串定义\n');
    const deck = { source: 'course/string.pptx', sourceAssetId: 'asset-string', nativeSlideIds: ['slide-256'], chapter: '第四章串', markdown: '知识库/string.md', webp_dir: 'WebP/deck', slides: 1 };
    fs.writeFileSync(path.join(root, '知识库', '转换清单.json'), JSON.stringify({ slide_count: 2, ppt_count: 2, decks: [deck, { ...deck, source: 'course/duplicate.pptx' }] }));
    const result = spawnSync(process.execPath, ['scripts/build-original-courseware.mjs', root], { encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.equal(fs.existsSync(path.join(root, 'WebP', 'slides.json')), false);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});
