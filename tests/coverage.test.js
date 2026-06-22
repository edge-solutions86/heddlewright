import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { hasReport, parseLcov, worstCovered, totals, readReport } from '../scripts/coverage.mjs';

const SAMPLE = [
  'TN:',
  'SF:C:\\repo\\src\\a.js',
  'FNF:2',
  'FNH:2',
  'BRF:10',
  'BRH:8',
  'LF:100',
  'LH:95',
  'end_of_record',
  'TN:',
  'SF:C:\\repo\\src\\b.js',
  'BRF:4',
  'BRH:4',
  'LF:50',
  'LH:20',
  'end_of_record',
  'TN:',
  'SF:C:\\repo\\src\\c.js',
  'LF:10',
  'LH:10',
  'end_of_record',
  '',
].join('\n');

describe('parseLcov', () => {
  const files = parseLcov(SAMPLE);

  it('reads one record per file', () => {
    expect(files.length).toBe(3);
    expect(files.map((entry) => entry.file)).toEqual([
      'C:/repo/src/a.js',
      'C:/repo/src/b.js',
      'C:/repo/src/c.js',
    ]);
  });

  it('works the shares and the misses out', () => {
    expect(files[0].lineShare).toBe(0.95);
    expect(files[0].branchShare).toBe(0.8);
    expect(files[1].missed).toBe(30);
  });

  it('reads a file with no branches as fully covered on branches', () => {
    expect(files[2].branchShare).toBe(1);
  });

  it('is empty for an empty report', () => {
    expect(parseLcov('')).toEqual([]);
  });

  it('ignores lines outside a record', () => {
    expect(parseLcov('LF:100\nLH:1\n')).toEqual([]);
  });
});

describe('worstCovered', () => {
  const files = parseLcov(SAMPLE);

  it('ranks by lines missed rather than by share', () => {
    expect(worstCovered(files).map((entry) => entry.file)).toEqual([
      'C:/repo/src/b.js',
      'C:/repo/src/a.js',
    ]);
  });

  it('leaves out the files with nothing missing', () => {
    expect(worstCovered(files).some((entry) => entry.file.endsWith('c.js'))).toBe(false);
  });

  it('takes a shorter list', () => {
    expect(worstCovered(files, { limit: 1 }).length).toBe(1);
  });
});

describe('totals', () => {
  it('adds the per-file figures up', () => {
    const summary = totals(parseLcov(SAMPLE));
    expect(summary.files).toBe(3);
    expect(summary.lines).toBe(160);
    expect(summary.hit).toBe(125);
    expect(summary.lineShare).toBeCloseTo(125 / 160, 6);
  });

  it('reads an empty report as fully covered rather than as nothing', () => {
    expect(totals([])).toMatchObject({ files: 0, lineShare: 1, branchShare: 1 });
  });
});

describe('the real report', () => {
  it('reads back when one has been written', () => {
    if (!hasReport()) return;
    const files = readReport();
    expect(files.length).toBeGreaterThan(0);
    expect(totals(files).lineShare).toBeGreaterThan(0.9);
  });

  it('says plainly when there is none', () => {
    expect(readReport('nowhere/lcov.info')).toBeNull();
    expect(hasReport('nowhere/lcov.info')).toBe(false);
  });

  it('does not run itself on import', () => {
    const text = readFileSync(new URL('../scripts/coverage.mjs', import.meta.url), 'utf8');
    expect(text).toContain("process.argv[1].endsWith('coverage.mjs')");
  });
});

describe('the thresholds', () => {
  it('are declared in the runner config rather than only in a script', () => {
    const config = readFileSync(new URL('../vitest.config.js', import.meta.url), 'utf8');
    expect(config).toContain('thresholds');
    expect(config).toMatch(/statements:\s*\d+/);
    expect(config).toMatch(/branches:\s*\d+/);
  });
});
