import { readdirSync, statSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * The checks that are not tests.
 *
 * Each stage is exported so it can be run on its own, and nothing runs on
 * import: the file only does work when node was asked for it by name. A script
 * that acts at import time rewrites the very thing its tests are reading.
 */

const ROOT = fileURLToPath(new URL('..', import.meta.url));

function walk(directory, extensions) {
  const out = [];
  for (const entry of readdirSync(directory)) {
    if (entry === 'node_modules' || entry === 'coverage' || entry.startsWith('.')) continue;
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) {
      out.push(...walk(full, extensions));
      continue;
    }
    if (extensions.some((extension) => entry.endsWith(extension))) out.push(full);
  }
  return out.sort();
}

/** Every source file the checks read. */
export function sourceFiles() {
  return [
    ...walk(join(ROOT, 'src'), ['.js']),
    ...walk(join(ROOT, 'tests'), ['.js']),
    ...walk(join(ROOT, 'scripts'), ['.mjs']),
    ...walk(join(ROOT, 'bin'), ['.js']),
  ];
}

/** No file may carry a carriage return, a byte order mark or a tab. */
export function checkBytes() {
  const files = sourceFiles();
  if (files.length === 0) throw new Error('the byte check found no files to read');
  const problems = [];
  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    const name = relative(ROOT, file).split(sep).join('/');
    if (text.includes('\r')) problems.push(`${name} carries a carriage return`);
    if (text.charCodeAt(0) === 0xfeff) problems.push(`${name} starts with a byte order mark`);
    if (text.includes('\t')) problems.push(`${name} carries a tab`);
    if (!text.endsWith('\n')) problems.push(`${name} does not end with a newline`);
  }
  return { stage: 'bytes', files: files.length, problems };
}

/** Every source file must be importable on its own. */
export async function checkImports() {
  const files = walk(join(ROOT, 'src'), ['.js']);
  const problems = [];
  for (const file of files) {
    try {
      await import(`file://${file.split(sep).join('/')}`);
    } catch (err) {
      problems.push(`${relative(ROOT, file)} will not import: ${err.message}`);
    }
  }
  return { stage: 'imports', files: files.length, problems };
}

/** Nothing in src may import from tests, and nothing may import a bare path. */
export function checkDependencies() {
  const files = walk(join(ROOT, 'src'), ['.js']);
  const problems = [];
  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    const name = relative(ROOT, file).split(sep).join('/');
    for (const match of text.matchAll(/from '([^']+)'/g)) {
      const target = match[1];
      if (target.includes('/tests/')) problems.push(`${name} imports from the tests`);
      if (target.startsWith('.') && !target.endsWith('.js')) {
        problems.push(`${name} imports "${target}" without a file extension`);
      }
    }
  }
  return { stage: 'dependencies', files: files.length, problems };
}

/**
 * Every test file must sit beside something it tests. That is a file of the
 * same name under `src`, or a script of the same name under `scripts`, since
 * the scripts are tested the same way everything else is.
 */
export function checkTestsPaired() {
  const named = new Set([
    ...walk(join(ROOT, 'src'), ['.js']).map((file) =>
      relative(join(ROOT, 'src'), file).split(sep).join('/'),
    ),
    ...walk(join(ROOT, 'scripts'), ['.mjs']).map((file) =>
      relative(join(ROOT, 'scripts'), file).split(sep).join('/').replace('.mjs', '.js'),
    ),
  ]);
  const tests = walk(join(ROOT, 'tests'), ['.test.js']);
  const problems = [];
  for (const file of tests) {
    const key = relative(join(ROOT, 'tests'), file).split(sep).join('/').replace('.test.js', '.js');
    if (!named.has(key) && key !== 'sources.js') {
      problems.push(`${key} has a test but nothing it tests`);
    }
  }
  return { stage: 'pairing', files: tests.length, problems };
}

/** A script must not do its work at import time. */
export function checkScriptGuards() {
  const files = walk(join(ROOT, 'scripts'), ['.mjs']);
  const problems = [];
  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    const name = relative(ROOT, file).split(sep).join('/');
    if (!text.includes('process.argv[1]')) {
      problems.push(`${name} has no guard against running on import`);
    }
  }
  return { stage: 'guards', files: files.length, problems };
}

/** Run every stage and report them together. */
export async function verify() {
  const stages = [
    checkBytes(),
    await checkImports(),
    checkDependencies(),
    checkTestsPaired(),
    checkScriptGuards(),
  ];
  return { stages, problems: stages.flatMap((stage) => stage.problems) };
}

const invoked = process.argv[1] !== undefined && process.argv[1].endsWith('verify.mjs');
if (invoked) {
  const result = await verify();
  for (const stage of result.stages) {
    console.log(`${stage.stage}: ${stage.files} files, ${stage.problems.length} problems`);
  }
  for (const problem of result.problems) console.error(`  ${problem}`);
  process.exitCode = result.problems.length === 0 ? 0 : 1;
}
