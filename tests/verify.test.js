import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import {
  sourceFiles,
  checkBytes,
  checkImports,
  checkDependencies,
  checkTestsPaired,
  checkScriptGuards,
  verify,
} from '../scripts/verify.mjs';

describe('sourceFiles', () => {
  it('finds the source, the tests, the scripts and the binary', () => {
    const files = sourceFiles().map((file) => file.replace(/\\/g, '/'));
    expect(files.some((file) => file.includes('/src/'))).toBe(true);
    expect(files.some((file) => file.includes('/tests/'))).toBe(true);
    expect(files.some((file) => file.includes('/scripts/'))).toBe(true);
    expect(files.some((file) => file.includes('/bin/'))).toBe(true);
  });

  it('leaves the installed packages alone', () => {
    expect(sourceFiles().some((file) => file.includes('node_modules'))).toBe(false);
  });
});

describe('checkBytes', () => {
  it('reads every file and finds nothing wrong', () => {
    const result = checkBytes();
    expect(result.files).toBe(sourceFiles().length);
    expect(result.problems).toEqual([]);
  });

  it('would notice a carriage return, a tab and a missing newline', () => {
    const cases = [
      ['a\r\nb\n', /carriage return/],
      ['a\tb\n', /tab/],
      ['ab', /does not end with a newline/],
      ['﻿ab\n', /byte order mark/],
    ];
    for (const [text, pattern] of cases) {
      const problems = [];
      if (text.includes('\r')) problems.push('carriage return');
      if (text.charCodeAt(0) === 0xfeff) problems.push('byte order mark');
      if (text.includes('\t')) problems.push('tab');
      if (!text.endsWith('\n')) problems.push('does not end with a newline');
      expect(problems.join(' ')).toMatch(pattern);
    }
  });

  it('reads the same rules the repository is actually written to', () => {
    const attributes = readFileSync(new URL('../.gitattributes', import.meta.url), 'utf8');
    expect(attributes).toContain('text=auto eol=lf');
  });
});

describe('checkImports', () => {
  it('imports every source file on its own', async () => {
    const result = await checkImports();
    expect(result.problems).toEqual([]);
    expect(result.files).toBeGreaterThan(40);
  });
});

describe('checkDependencies', () => {
  it('finds no source file reaching into the tests', () => {
    expect(checkDependencies().problems).toEqual([]);
  });

  it('reads every source file', () => {
    expect(checkDependencies().files).toBeGreaterThan(40);
  });
});

describe('checkTestsPaired', () => {
  it('finds a source file behind every test', () => {
    expect(checkTestsPaired().problems).toEqual([]);
  });

  it('counts the test files it walked', () => {
    expect(checkTestsPaired().files).toBeGreaterThan(40);
  });
});

describe('checkScriptGuards', () => {
  it('finds a guard in every script', () => {
    const result = checkScriptGuards();
    expect(result.problems).toEqual([]);
    expect(result.files).toBeGreaterThan(0);
  });

  it('sees the guard in the bytes of this very script', () => {
    const text = readFileSync(new URL('../scripts/verify.mjs', import.meta.url), 'utf8');
    expect(text).toContain("process.argv[1].endsWith('verify.mjs')");
  });
});

describe('verify', () => {
  it('runs every stage and reports them together', async () => {
    const result = await verify();
    expect(result.stages.map((stage) => stage.stage)).toEqual([
      'bytes',
      'imports',
      'dependencies',
      'pairing',
      'guards',
    ]);
    expect(result.problems).toEqual([]);
  });

  it('reports a file count for every stage', async () => {
    for (const stage of (await verify()).stages) {
      expect(stage.files).toBeGreaterThan(0);
    }
  });
});
