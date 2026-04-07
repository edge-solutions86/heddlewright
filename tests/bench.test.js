import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { time, benchDraft, benchmarks, runBenchmarks } from '../scripts/bench.mjs';

describe('time', () => {
  it('runs the work the number of rounds it is given', () => {
    let seen = 0;
    const result = time(5, () => {
      seen += 1;
      return seen;
    });
    expect(seen).toBe(5);
    expect(result.rounds).toBe(5);
    expect(result.last).toBe(5);
  });

  it('reports a millisecond figure that is not negative', () => {
    expect(time(3, () => 1).milliseconds).toBeGreaterThanOrEqual(0);
  });

  it('passes the round number to the work', () => {
    const seen = [];
    time(3, (round) => seen.push(round));
    expect(seen).toEqual([0, 1, 2]);
  });
});

describe('benchDraft', () => {
  it('builds a draft of the size asked for', () => {
    const draft = benchDraft(64, 32);
    expect(draft.ends).toBe(64);
    expect(draft.picks).toBe(32);
    expect(draft.shafts).toBe(8);
  });
});

describe('benchmarks', () => {
  it('names every benchmark and gives it something to run', () => {
    for (const benchmark of benchmarks()) {
      expect(benchmark.name.length).toBeGreaterThan(0);
      expect(typeof benchmark.run).toBe('function');
    }
  });

  it('covers the drawdown, the analysis and the card deck', () => {
    const names = benchmarks().map((benchmark) => benchmark.name);
    expect(names.some((name) => name.includes('drawdown'))).toBe(true);
    expect(names.some((name) => name.includes('floats'))).toBe(true);
    expect(names.some((name) => name.includes('deck'))).toBe(true);
  });
});

describe('runBenchmarks', () => {
  // Run once and share the results. Each benchmark weaves a two hundred by two
  // hundred cloth several times over, so running the whole set once per test
  // put this file past the default timeout on a loaded machine.
  const results = runBenchmarks();

  it('runs every one and reports a timing', () => {
    expect(results.length).toBe(benchmarks().length);
    for (const result of results) {
      expect(result.rounds).toBeGreaterThan(0);
      expect(result.milliseconds).toBeGreaterThanOrEqual(0);
    }
  });

  it('does its work rather than reporting an empty round', () => {
    for (const result of results) {
      expect(result.last).toBeDefined();
    }
  });

  it('names every benchmark in its result', () => {
    expect(results.map((result) => result.name)).toEqual(
      benchmarks().map((benchmark) => benchmark.name),
    );
  });

  it('does not run itself on import', () => {
    const text = readFileSync(new URL('../scripts/bench.mjs', import.meta.url), 'utf8');
    expect(text).toContain("process.argv[1].endsWith('bench.mjs')");
  });
});
