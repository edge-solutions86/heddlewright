import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { sourceFiles } from '../scripts/verify.mjs';

/**
 * House rules, checked against the bytes of every file.
 *
 * Every rule here is a predicate over one file's text. A predicate that has
 * quietly stopped matching anything reports a clean sweep, which is
 * indistinguishable from a clean repository, so each one is also run against a
 * planted string that it must reject. A rule with no positive control is a rule
 * nobody can tell is still working.
 */

const ROOT = fileURLToPath(new URL('..', import.meta.url));

/**
 * This file is the one exemption, and it has to be. Every rule below carries a
 * planted string that the rule must reject, so this file contains a carriage
 * return, a tab, a TODO and an extensionless import on purpose. Sweeping it
 * would report its own controls as violations.
 */
const EXEMPT = 'tests/sources.test.js';

const FILES = sourceFiles()
  .map((file) => ({
    name: relative(ROOT, file).split(sep).join('/'),
    text: readFileSync(file, 'utf8'),
  }))
  .filter((file) => file.name !== EXEMPT);

const SOURCE = FILES.filter((file) => file.name.startsWith('src/'));

function offenders(files, predicate) {
  return files.filter((file) => predicate(file.text, file.name)).map((file) => file.name);
}

describe('the file list itself', () => {
  it('is not empty, which would make every rule below pass for free', () => {
    expect(FILES.length).toBeGreaterThan(80);
    expect(SOURCE.length).toBeGreaterThan(40);
  });

  it('covers every part of the package', () => {
    for (const area of ['src/draft/', 'src/weave/', 'src/jacquard/', 'src/loom/', 'tests/']) {
      expect(FILES.some((file) => file.name.startsWith(area))).toBe(true);
    }
  });

  it('leaves exactly one file out, and that file is this one', () => {
    const swept = new Set(FILES.map((file) => file.name));
    const all = sourceFiles().map((file) => relative(ROOT, file).split(sep).join('/'));
    const missing = all.filter((name) => !swept.has(name));
    expect(missing).toEqual([EXEMPT]);
  });
});

describe('no carriage returns anywhere', () => {
  const rule = (text) => text.includes('\r');

  it('holds across the repository', () => {
    expect(offenders(FILES, rule)).toEqual([]);
  });

  it('would catch one if it were there', () => {
    expect(rule('a\r\nb\n')).toBe(true);
    expect(rule('a\nb\n')).toBe(false);
  });
});

describe('no tabs in the indentation', () => {
  const rule = (text) => /^\t/m.test(text);

  it('holds across the repository', () => {
    expect(offenders(FILES, rule)).toEqual([]);
  });

  it('would catch one if it were there', () => {
    expect(rule('function f() {\n\treturn 1;\n}\n')).toBe(true);
    expect(rule('function f() {\n  return 1;\n}\n')).toBe(false);
  });
});

describe('every file ends with exactly one newline', () => {
  const rule = (text) => !text.endsWith('\n') || text.endsWith('\n\n');

  it('holds across the repository', () => {
    expect(offenders(FILES, rule)).toEqual([]);
  });

  it('would catch a missing or a doubled one', () => {
    expect(rule('a')).toBe(true);
    expect(rule('a\n\n')).toBe(true);
    expect(rule('a\n')).toBe(false);
  });
});

describe('nothing in src writes to the console', () => {
  const rule = (text) => /\bconsole\.(log|error|warn)\(/.test(text);

  it('holds across the source, which is a library', () => {
    expect(offenders(SOURCE, rule)).toEqual([]);
  });

  it('would catch one if it were there', () => {
    expect(rule('console.log("hello");\n')).toBe(true);
    expect(rule('const logged = false;\n')).toBe(false);
  });

  it('allows it in the scripts and the binary, which are meant to print', () => {
    const printing = FILES.filter((file) => rule(file.text)).map((file) => file.name);
    expect(printing.length).toBeGreaterThan(0);
    for (const name of printing) {
      expect(name.startsWith('scripts/') || name.startsWith('bin/')).toBe(true);
    }
  });
});

describe('no unfinished work is left lying about', () => {
  // The usual third marker, XXX, is not in this list. Half the tests in this
  // package print cloth as strings of X, so a rule looking for it reports every
  // drawdown wider than two ends as unfinished work.
  const rule = (text) => /\b(TODO|FIXME)\b/.test(text);

  it('holds across the repository', () => {
    expect(offenders(FILES, rule)).toEqual([]);
  });

  it('would catch either marker', () => {
    expect(rule('// TODO: later\n')).toBe(true);
    expect(rule('// FIXME\n')).toBe(true);
    expect(rule('// done\n')).toBe(false);
  });

  it('does not read a drawdown as a marker, which is why XXX is left out', () => {
    expect(rule("expect(cloth).toBe('XXX.');\n")).toBe(false);
  });
});

describe('every source file opens with a comment saying what it is for', () => {
  const rule = (text) => !text.includes('/**');

  it('holds across the source', () => {
    expect(offenders(SOURCE, rule)).toEqual([]);
  });

  it('would catch a file with no doc comment at all', () => {
    expect(rule('export const x = 1;\n')).toBe(true);
    expect(rule('/** what it is for */\nexport const x = 1;\n')).toBe(false);
  });
});

describe('no source file has grown past what one sitting can read', () => {
  const rule = (text) => text.split('\n').length > 400;

  it('holds across the source', () => {
    expect(offenders(SOURCE, rule)).toEqual([]);
  });

  it('would catch one that had', () => {
    expect(rule('x\n'.repeat(401))).toBe(true);
    expect(rule('x\n'.repeat(399))).toBe(false);
  });
});

describe('imports name a file rather than a directory', () => {
  const rule = (text) =>
    [...text.matchAll(/from '(\.[^']*)'/g)].some((match) => !/\.m?js$/.test(match[1]));

  it('holds across the repository', () => {
    expect(offenders(FILES, rule)).toEqual([]);
  });

  it('would catch an extensionless import', () => {
    expect(rule("import { x } from './util/check';\n")).toBe(true);
    expect(rule("import { x } from './util/check.js';\n")).toBe(false);
  });

  it('takes a script import too, which is written with the other extension', () => {
    expect(rule("import { x } from '../scripts/verify.mjs';\n")).toBe(false);
  });
});
