/**
 * After package split, package-private constructors/methods/fields/compact ctors
 * become inaccessible. Promote non-private members to public across main+test java.
 * Skips members already marked public/protected/private.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const javaRoots = [
  path.join(root, 'backend', 'spring', 'src', 'main', 'java'),
  path.join(root, 'backend', 'spring', 'src', 'test', 'java'),
];

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) walk(full, out);
    else if (e.name.endsWith('.java')) out.push(full);
  }
  return out;
}

const MOD = '(?:(?:public|protected|private|static|final|synchronized|native|abstract|default|strictfp)\\s+)*';

let files = 0;
let edits = 0;

for (const f of javaRoots.flatMap((d) => walk(d))) {
  let text = fs.readFileSync(f, 'utf8');
  const name = path.basename(f, '.java');
  const before = text;

  // Compact record constructor: `Name {` or `Name\n{` at start of member — make public
  text = text.replace(
    new RegExp(`(\\n\\s*)(${name})(\\s*\\{)`, 'g'),
    (m, pre, n, brace, offset) => {
      // Look back for modifiers on previous tokens in same "statement"
      const ahead = text.slice(Math.max(0, offset - 40), offset);
      if (/\b(public|private|protected)\s*$/.test(ahead)) return m;
      // Avoid matching class/record declaration itself: `public record Name (`
      if (/record\s+$/.test(ahead) || /class\s+$/.test(ahead) || /enum\s+$/.test(ahead)) return m;
      edits++;
      return `${pre}public ${n}${brace}`;
    }
  );

  // Package-private methods and constructors:
  // line starts with optional whitespace, then type or name, then (
  // Not already having access modifier.
  text = text.replace(
    /^([ \t]+)(?!(?:public|protected|private|return|if|while|for|switch|catch|new|throw|else|case|default)\b)([\w.$]+(?:\s*<[^;{}]+?>)?(?:\s*\[\s*\])*)(\s+)([\w$]+)\s*\(/gm,
    (m, indent, typeOrVoid, sp, methodName) => {
      // Skip annotations-only lines handled elsewhere; skip assignments
      if (typeOrVoid === 'import' || typeOrVoid === 'package') return m;
      edits++;
      return `${indent}public ${typeOrVoid}${sp}${methodName}(`;
    }
  );

  // Constructors: `  Name(` without modifier
  text = text.replace(
    new RegExp(`^([ \\t]+)(${name})\\s*\\(`, 'gm'),
    (m, indent, n) => {
      edits++;
      return `${indent}public ${n}(`;
    }
  );

  // Package-private static final fields (constants)
  text = text.replace(
    /^([ \t]+)(static\s+final\s+)/gm,
    (m, indent, rest) => {
      // Check we didn't already have public on this line - the pattern starts at indent
      edits++;
      return `${indent}public ${rest}`;
    }
  );
  text = text.replace(
    /^([ \t]+)(final\s+static\s+)/gm,
    (m, indent, rest) => {
      edits++;
      return `${indent}public ${rest}`;
    }
  );

  // Fix double public
  text = text.replace(/\bpublic\s+public\s+/g, 'public ');
  // Fix public private / public protected mistakes
  text = text.replace(/\bpublic\s+private\s+/g, 'private ');
  text = text.replace(/\bpublic\s+protected\s+/g, 'protected ');

  if (text !== before) {
    fs.writeFileSync(f, text);
    files++;
  }
}

console.log(`Publicized members in ${files} files (~${edits} edits)`);
