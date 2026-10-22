import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { commandsFor, runChecks } from './verify-studyroom.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));

test('stops on failure and preserves the child exit code', () => {
  const result = runChecks([
    { name: 'failed', command: process.execPath, args: ['-e', 'process.exit(7)'] },
    { name: 'never', command: process.execPath, args: ['-e', 'process.exit(0)'] },
  ]);
  assert.equal(result.exitCode, 7);
  assert.deepEqual(result.completed, ['failed']);
});

test('returns nonzero when a command cannot start', () => {
  assert.notEqual(runChecks([{ name: 'missing', command: 'structify-command-does-not-exist', args: [] }]).exitCode, 0);
});

test('selects platform-specific wrappers and working directories', () => {
  assert.equal(commandsFor('backend', { root, platform: 'win32' })[0].command, 'mvnw.cmd');
  assert.equal(commandsFor('backend', { root, platform: 'linux' })[0].command, './mvnw');
  assert.equal(commandsFor('frontend', { root, platform: 'win32' })[0].command, 'npm.cmd');
  assert.match(commandsFor('frontend', { root, platform: 'linux' })[0].cwd, /frontend/);
});

test('architecture and e2e are real commands, never silently skipped', () => {
  assert.ok(commandsFor('architecture', { root }).length);
  assert.deepEqual(commandsFor('e2e', { root })[0].args, ['run', 'test:e2e']);
  assert.ok(commandsFor('all', { root }).length > commandsFor('frontend', { root }).length);
});

test('usage and invalid scopes have distinct outcomes', () => {
  const script = fileURLToPath(new URL('./verify-studyroom.mjs', import.meta.url));
  assert.equal(spawnSync(process.execPath, [script], { encoding: 'utf8' }).status, 0);
  assert.equal(spawnSync(process.execPath, [script, '--scope', 'typo'], { encoding: 'utf8' }).status, 2);
});

test('importing the runner does not execute checks', () => {
  const result = spawnSync(process.execPath, ['--input-type=module', '-e',
    `await import(${JSON.stringify(new URL('./verify-studyroom.mjs', import.meta.url).href)});`], { encoding: 'utf8' });
  assert.equal(result.status, 0);
  assert.equal(result.stdout, '');
});
