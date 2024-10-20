import { describe, it, expect } from 'vitest';
import {
  runs,
  cyclicRuns,
  longestRun,
  countRuns,
  runHistogram,
  runsOver,
  expandRuns,
  formatRuns,
} from '../../src/util/runs.js';

describe('runs', () => {
  it('splits a sequence into maximal blocks', () => {
    expect(runs([1, 1, 0, 0, 0, 1])).toEqual([
      { value: 1, start: 0, length: 2 },
      { value: 0, start: 2, length: 3 },
      { value: 1, start: 5, length: 1 },
    ]);
  });

  it('gives one run for a constant sequence and none for an empty one', () => {
    expect(runs([1, 1, 1])).toEqual([{ value: 1, start: 0, length: 3 }]);
    expect(runs([])).toEqual([]);
  });

  it('compares strictly, so 0 and false are different values', () => {
    expect(runs([0, false]).length).toBe(2);
  });
});

describe('cyclicRuns', () => {
  it('joins a run that wraps the end back to the start', () => {
    const found = cyclicRuns([1, 0, 0, 1, 1]);
    expect(found).toEqual([
      { value: 0, start: 1, length: 2, wraps: false },
      { value: 1, start: 3, length: 3, wraps: true },
    ]);
  });

  it('leaves the runs alone when the ends do not match', () => {
    expect(cyclicRuns([1, 1, 0])).toEqual([
      { value: 1, start: 0, length: 2, wraps: false },
      { value: 0, start: 2, length: 1, wraps: false },
    ]);
  });

  it('reports a constant sequence as a single non-wrapping run of the whole length', () => {
    expect(cyclicRuns([1, 1, 1, 1])).toEqual([{ value: 1, start: 0, length: 4, wraps: false }]);
  });

  it('is empty for an empty sequence', () => {
    expect(cyclicRuns([])).toEqual([]);
  });

  it('never reports a run longer than the sequence', () => {
    for (const seq of [
      [1, 0, 1],
      [1, 1, 0, 1],
      [0, 0, 0, 1, 0],
    ]) {
      for (const run of cyclicRuns(seq)) {
        expect(run.length).toBeLessThanOrEqual(seq.length);
      }
    }
  });
});

describe('longestRun', () => {
  it('counts the wrap by default, which is what a float in a repeat does', () => {
    expect(longestRun([1, 0, 0, 1, 1], 1)).toBe(3);
    expect(longestRun([1, 0, 0, 1, 1], 1, { cyclic: false })).toBe(2);
  });

  it('is zero when the value never appears', () => {
    expect(longestRun([0, 0, 0], 1)).toBe(0);
  });

  it('reads a plain weave column as a float of one', () => {
    expect(longestRun([1, 0, 1, 0, 1, 0], 1)).toBe(1);
    expect(longestRun([1, 0, 1, 0, 1, 0], 0)).toBe(1);
  });
});

describe('countRuns', () => {
  it('counts interlacements, merging the wrap', () => {
    expect(countRuns([1, 0, 0, 1, 1], 1)).toBe(1);
    expect(countRuns([1, 0, 0, 1, 1], 1, { cyclic: false })).toBe(2);
    expect(countRuns([1, 0, 1, 0], 1)).toBe(2);
  });
});

describe('runHistogram', () => {
  it('buckets runs by length', () => {
    const hist = runHistogram([1, 1, 0, 1, 0, 1, 1, 0], 1);
    expect(hist.get(2)).toBe(2);
    expect(hist.get(1)).toBe(1);
    expect([...hist.keys()].sort()).toEqual([1, 2]);
  });

  it('is empty when nothing matches', () => {
    expect(runHistogram([0, 0], 1).size).toBe(0);
  });
});

describe('runsOver', () => {
  it('finds floats longer than the limit and excludes the limit itself', () => {
    const long = runsOver([1, 1, 1, 0, 1, 1, 1, 1], 1, 3);
    expect(long.length).toBe(1);
    expect(long[0].length).toBe(7);
    expect(runsOver([1, 1, 1, 0], 1, 3)).toEqual([]);
  });
});

describe('expandRuns', () => {
  it('round-trips a sequence through its run form', () => {
    const seq = [1, 1, 0, 0, 0, 1, 0];
    expect(expandRuns(runs(seq))).toEqual(seq);
  });

  it('rejects a run with no length', () => {
    expect(() => expandRuns([{ value: 1, length: 0 }])).toThrow(/at least 1/);
  });
});

describe('formatRuns', () => {
  it('writes compact notation by default', () => {
    expect(formatRuns([1, 1, 0, 1])).toBe('1x2 0x1 1x1');
  });

  it('writes the long form when asked', () => {
    expect(formatRuns([1, 1, 0], { style: 'long' })).toBe('2 of 1, 1 of 0');
  });

  it('rejects an unknown style', () => {
    expect(() => formatRuns([1], { style: 'fancy' })).toThrow(/compact, long/);
  });
});
