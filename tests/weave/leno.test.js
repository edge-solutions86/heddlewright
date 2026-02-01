import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, dimensions } from '../../src/util/matrix.js';
import {
  mockLeno,
  holePositions,
  doupPlan,
  doupEnds,
  doupConflicts,
  assertDoupPlanSound,
  endsForLenoStripe,
  weavableOnShafts,
} from '../../src/weave/leno.js';

describe('mockLeno', () => {
  it('puts plain weave where a group meets itself and floats elsewhere', () => {
    expect(formatMatrix(mockLeno(2))).toBe('.XXX\nX.XX\nXX.X\nXXX.');
  });

  it('grows with the group size', () => {
    expect(dimensions(mockLeno(3))).toEqual({ rows: 6, cols: 6 });
  });

  it('interlaces inside a group and floats across', () => {
    const repeat = mockLeno(3);
    expect(repeat[0].slice(0, 3)).toEqual([0, 1, 0]);
    expect(repeat[0].slice(3)).toEqual([1, 1, 1]);
  });

  it('refuses a group of one, which is just plain weave', () => {
    expect(() => mockLeno(1)).toThrow(DraftError);
    expect(() => mockLeno(0)).toThrow(InvalidArgument);
  });

  it('is weavable on shafts, which is the whole point of it', () => {
    expect(weavableOnShafts(mockLeno(2))).toBe(true);
  });
});

describe('holePositions', () => {
  it('puts a hole at each corner where the groups meet', () => {
    expect(holePositions(2)).toEqual([
      { end: 2, pick: 2 },
      { end: 4, pick: 4 },
    ]);
  });

  it('moves with the group size', () => {
    expect(holePositions(3)[1]).toEqual({ end: 6, pick: 6 });
  });
});

describe('doupPlan', () => {
  it('records which end crosses and which way', () => {
    expect(doupPlan([{ doup: 1, standard: 2 }])).toEqual([{ doup: 1, standard: 2, side: 'left' }]);
    expect(doupPlan([{ doup: 4, standard: 3 }])[0].side).toBe('right');
  });

  it('refuses an end crossing over itself', () => {
    expect(() => doupPlan([{ doup: 1, standard: 1 }])).toThrow(/cannot cross over itself/);
  });

  it('refuses an end number of nothing', () => {
    expect(() => doupPlan([{ doup: 0, standard: 2 }])).toThrow(InvalidArgument);
  });

  it('lists the doup ends in order', () => {
    const plan = doupPlan([
      { doup: 5, standard: 6 },
      { doup: 1, standard: 2 },
    ]);
    expect(doupEnds(plan)).toEqual([1, 5]);
  });
});

describe('doupConflicts', () => {
  it('is quiet on a plan where every pair is its own', () => {
    const plan = doupPlan([
      { doup: 1, standard: 2 },
      { doup: 3, standard: 4 },
    ]);
    expect(doupConflicts(plan)).toEqual([]);
    expect(assertDoupPlanSound(plan)).toBe(plan);
  });

  it('catches two doups reaching for the same standard end', () => {
    const plan = doupPlan([
      { doup: 1, standard: 3 },
      { doup: 2, standard: 3 },
    ]);
    expect(doupConflicts(plan).map((finding) => finding.code)).toContain('shared_standard');
  });

  it('catches an end that is both a doup and a standard', () => {
    const plan = doupPlan([
      { doup: 1, standard: 2 },
      { doup: 2, standard: 3 },
    ]);
    expect(doupConflicts(plan).map((finding) => finding.code)).toContain('doup_as_standard');
  });

  it('refuses a plan that will not open', () => {
    const plan = doupPlan([
      { doup: 1, standard: 3 },
      { doup: 2, standard: 3 },
    ]);
    expect(() => assertDoupPlanSound(plan)).toThrow(DraftError);
  });
});

describe('endsForLenoStripe', () => {
  it('is two ends a pair, over the repeats', () => {
    expect(endsForLenoStripe({ pairs: 6 })).toBe(12);
    expect(endsForLenoStripe({ pairs: 6, repeats: 4 })).toBe(48);
  });

  it('refuses a stripe with no pairs', () => {
    expect(() => endsForLenoStripe({ pairs: 0 })).toThrow(InvalidArgument);
  });
});
