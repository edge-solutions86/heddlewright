import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  clearRuns,
  planCuts,
  cutPlanSummary,
  bestSingleLength,
  lengthsAvailable,
  cutRoll,
} from '../../src/cloth/cutplan.js';

describe('clearRuns', () => {
  it('is the whole roll when nothing is faulty', () => {
    expect(clearRuns([], 100)).toEqual([{ from: 0, to: 100, yards: 100 }]);
  });

  it('splits the roll either side of a fault, with clearance', () => {
    expect(clearRuns([50], 100)).toEqual([
      { from: 0, to: 49.75, yards: 49.75 },
      { from: 50.25, to: 100, yards: 49.75 },
    ]);
  });

  it('merges faults that fall within a clearance of each other', () => {
    const runs = clearRuns([50, 50.2], 100);
    expect(runs.length).toBe(2);
    expect(runs[1].from).toBe(50.45);
  });

  it('sorts the faults before working', () => {
    expect(clearRuns([70, 30], 100)).toEqual(clearRuns([30, 70], 100));
  });

  it('drops a run when a fault sits at the very start', () => {
    const runs = clearRuns([0], 100);
    expect(runs.length).toBe(1);
    expect(runs[0].from).toBe(0.25);
  });

  it('takes a wider clearance', () => {
    expect(clearRuns([50], 100, { clearance: 1 })[0].to).toBe(49);
  });

  it('refuses a fault at a negative position', () => {
    expect(() => clearRuns([-1], 100)).toThrow(InvalidArgument);
  });
});

describe('planCuts', () => {
  const RUNS = clearRuns([50], 100);

  it('fits the longest wanted length first', () => {
    const plan = planCuts(RUNS, [10, 40]);
    expect(plan.cuts.map((cut) => cut.yards)).toEqual([40, 10]);
    expect(plan.unmet).toEqual([]);
  });

  it('reports a length no run can hold', () => {
    const plan = planCuts(RUNS, [60]);
    expect(plan.cuts).toEqual([]);
    expect(plan.unmet).toEqual([60]);
  });

  it('leaves what is left of each run as an offcut', () => {
    const plan = planCuts(RUNS, [40]);
    const offcutYards = plan.offcuts.reduce((total, entry) => total + entry.yards, 0);
    expect(offcutYards).toBeCloseTo(49.75 - 40 + 49.75, 3);
  });

  it('never cuts past the end of a run', () => {
    const plan = planCuts(RUNS, [40, 9, 40, 9]);
    for (const cut of plan.cuts) {
      const run = RUNS.find((candidate) => cut.from >= candidate.from && cut.to <= candidate.to);
      expect(run).toBeDefined();
    }
  });

  it('refuses a wanted length of nothing', () => {
    expect(() => planCuts(RUNS, [0])).toThrow(InvalidArgument);
  });
});

describe('cutPlanSummary', () => {
  it('adds the cuts, the offcuts and the waste back to the roll', () => {
    const runs = clearRuns([50], 100);
    const plan = planCuts(runs, [40, 40]);
    const summary = cutPlanSummary(plan, 100);
    expect(summary.cutYards).toBe(80);
    expect(summary.cutYards + summary.offcutYards + summary.wasteYards).toBeCloseTo(100, 3);
  });

  it('reads the yield as the share actually cut', () => {
    const plan = planCuts(clearRuns([], 100), [50]);
    expect(cutPlanSummary(plan, 100).yield).toBe(0.5);
  });

  it('counts the lengths it could not meet', () => {
    const plan = planCuts(clearRuns([50], 100), [60, 60]);
    expect(cutPlanSummary(plan, 100).unmet).toBe(2);
  });
});

describe('bestSingleLength', () => {
  it('is the longest clear run', () => {
    expect(bestSingleLength(clearRuns([20], 100))).toBe(79.75);
    expect(bestSingleLength(clearRuns([], 100))).toBe(100);
  });

  it('is nothing when there are no runs at all', () => {
    expect(bestSingleLength([])).toBe(0);
  });
});

describe('lengthsAvailable', () => {
  it('counts how many of one size the roll gives', () => {
    expect(lengthsAvailable(clearRuns([], 100), 30)).toBe(3);
    expect(lengthsAvailable(clearRuns([50], 100), 30)).toBe(2);
  });

  it('is nothing when the length does not fit anywhere', () => {
    expect(lengthsAvailable(clearRuns([50], 100), 60)).toBe(0);
  });
});

describe('cutRoll', () => {
  it('does the whole job in one call', () => {
    const job = cutRoll({ rollYards: 100, defectYards: [50], wantedLengths: [40, 40] });
    expect(job.runs.length).toBe(2);
    expect(job.cuts.length).toBe(2);
    expect(job.summary.yield).toBe(0.8);
  });

  it('a roll with a fault in the middle yields less than a clean one', () => {
    const clean = cutRoll({ rollYards: 100, defectYards: [], wantedLengths: [60] });
    const faulty = cutRoll({ rollYards: 100, defectYards: [50], wantedLengths: [60] });
    expect(clean.summary.yield).toBe(0.6);
    expect(faulty.summary.yield).toBe(0);
    expect(faulty.unmet).toEqual([60]);
  });
});
