#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = fileURLToPath(new URL('../', import.meta.url));
const scopes = ['backend', 'frontend', 'contracts', 'architecture', 'e2e'];

export function commandsFor(scope, { root = repoRoot, platform = process.platform } = {}) {
  const maven = platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  const npm = platform === 'win32' ? 'npm.cmd' : 'npm';
  const backend = path.join(root, 'backend', 'spring');
  const frontend = path.join(root, 'frontend');
  const commands = {
    backend: [{ name: 'backend tests', command: maven, args: ['-B', 'test'], cwd: backend }],
    frontend: [
      { name: 'frontend tests', command: npm, args: ['test'], cwd: frontend },
      { name: 'frontend build and types', command: npm, args: ['run', 'build'], cwd: frontend },
    ],
    contracts: [{ name: 'API contracts', command: process.execPath, args: ['scripts/verify-api-contract-fixtures.js'], cwd: root }],
    architecture: [{ name: 'execution boundaries', command: maven,
      args: ['-B', '-Dtest=StudyroomArchitectureTest,FragmentContractTest', 'test'], cwd: backend }],
    e2e: [{ name: 'browser regression', command: npm, args: ['run', 'test:e2e'], cwd: frontend }],
  };
  if (scope === 'all') return scopes.flatMap(name => commands[name]);
  if (!commands[scope]) throw new RangeError(`Unknown scope: ${scope}`);
  return commands[scope];
}

export function runChecks(checks) {
  const completed = [];
  for (const check of checks) {
    process.stdout.write(`Running ${check.name}\n`);
    // Only repository-defined commands/arguments reach .cmd wrappers; no user shell text.
    const result = spawnSync(check.command, check.args, {
      cwd: check.cwd ?? repoRoot,
      stdio: 'inherit',
      windowsHide: true,
      shell: process.platform === 'win32' && check.command.endsWith('.cmd'),
    });
    completed.push(check.name);
    if (result.error) process.stderr.write(`${result.error.message}\n`);
    if (result.error || result.signal || result.status !== 0) {
      return { completed, exitCode: result.status > 0 ? result.status : 1 };
    }
  }
  return { completed, exitCode: 0 };
}

function main(args) {
  const usage = 'Usage: node scripts/verify-studyroom.mjs --scope backend|frontend|contracts|architecture|e2e|all\n';
  if (args.length === 0 || (args.length === 1 && args[0] === '--help')) {
    process.stdout.write(usage);
    return 0;
  }
  if (args.length !== 2 || args[0] !== '--scope' || ![...scopes, 'all'].includes(args[1])) {
    process.stderr.write(usage);
    return 2;
  }
  return runChecks(commandsFor(args[1])).exitCode;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv.slice(2));
}
