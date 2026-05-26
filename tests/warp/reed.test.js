import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  STOCK_REEDS,
  reedFor,
  nearestReed,
  reedOptions,
  dentingPlan,
  averageEndsPerDent,
  reedWidth,
  sleyOrder,
  dentingWarnings,
  assertDentingFits,
} from '../../src/warp/reed.js';

describe('reedFor', () => {
  it('divides the sett by the ends in each dent', () => {
    expect(reedFor(24, 2)).toBe(12);
    expect(reedFor(24, 3)).toBe(8);
    expect(reedFor(24, 1)).toBe(24);
  });

  it('does not hide a sett that needs a fractional reed', () => {
    expect(reedFor(25, 2)).toBe(12.5);
  });
});

describe('nearestReed', () => {
  it('picks the closest reed on the rack and says how far off it is', () => {
    expect(nearestReed(12.5)).toEqual({ reed: 12, off: -0.5 });
    expect(nearestReed(13)).toEqual({ reed: 12, off: -1 });
    expect(nearestReed(17)).toEqual({ reed: 16, off: -1 });
  });

  it('is exact when the wanted count is on the rack', () => {
    expect(nearestReed(15)).toEqual({ reed: 15, off: 0 });
  });

  it('takes a different rack', () => {
    expect(nearestReed(13, { reeds: [11, 13, 17] })).toEqual({ reed: 13, off: 0 });
  });

  it('lists the reeds it knows about', () => {
    expect(STOCK_REEDS).toContain(12);
    expect(STOCK_REEDS).toContain(30);
  });
});

describe('reedOptions', () => {
  it('finds every whole-number way to reach a sett', () => {
    expect(reedOptions(24)).toEqual([
      { reed: 6, endsPerDent: 4 },
      { reed: 8, endsPerDent: 3 },
      { reed: 12, endsPerDent: 2 },
      { reed: 24, endsPerDent: 1 },
    ]);
  });

  it('respects a lower limit on how many ends may share a dent', () => {
    expect(reedOptions(24, { maxEndsPerDent: 2 })).toEqual([
      { reed: 12, endsPerDent: 2 },
      { reed: 24, endsPerDent: 1 },
    ]);
  });

  it('is empty when no stock reed divides the sett', () => {
    expect(reedOptions(23)).toEqual([]);
  });
});

describe('dentingPlan', () => {
  it('fills dents evenly for a plain pattern', () => {
    expect(dentingPlan(8, [2])).toEqual([2, 2, 2, 2]);
  });

  it('alternates an uneven pattern, which is how a half sett is sleyed', () => {
    expect(dentingPlan(10, [3, 2])).toEqual([3, 2, 3, 2]);
  });

  it('cuts the last dent short rather than overfilling it', () => {
    expect(dentingPlan(7, [2])).toEqual([2, 2, 2, 1]);
  });

  it('refuses a group of no ends, which would never finish', () => {
    expect(() => dentingPlan(8, [0])).toThrow(InvalidArgument);
  });

  it('places exactly the ends it was given', () => {
    for (const pattern of [[2], [3, 2], [1, 2, 3]]) {
      const dents = dentingPlan(17, pattern);
      expect(dents.reduce((total, group) => total + group, 0)).toBe(17);
    }
  });
});

describe('averageEndsPerDent', () => {
  it('averages a repeating pattern', () => {
    expect(averageEndsPerDent([2])).toBe(2);
    expect(averageEndsPerDent([3, 2])).toBe(2.5);
    expect(averageEndsPerDent([1, 2, 3])).toBe(2);
  });
});

describe('reedWidth', () => {
  it('divides the dents by the reed count', () => {
    expect(reedWidth(240, 12)).toBe(20);
    expect(reedWidth(100, 8)).toBe(12.5);
  });
});

describe('sleyOrder', () => {
  it('says which dent each end falls in', () => {
    expect(sleyOrder(6, [2])).toEqual([1, 1, 2, 2, 3, 3]);
    expect(sleyOrder(5, [3, 2])).toEqual([1, 1, 1, 2, 2]);
  });

  it('has one entry per end', () => {
    expect(sleyOrder(17, [3, 2]).length).toBe(17);
  });
});

describe('dentingWarnings', () => {
  it('is quiet on an even plan', () => {
    expect(dentingWarnings([2, 2, 2, 2])).toEqual([]);
  });

  it('flags a crowded dent and names which ones', () => {
    const warnings = dentingWarnings([2, 6, 2, 5]);
    expect(warnings[0].code).toBe('crowded_dent');
    expect(warnings[0].detail.dents).toEqual([2, 4]);
  });

  it('flags a short last dent', () => {
    const warnings = dentingWarnings([2, 2, 1]);
    expect(warnings.map((warning) => warning.code)).toContain('short_last_dent');
  });

  it('takes a different crowding limit', () => {
    expect(dentingWarnings([3, 3], { maxEndsPerDent: 2 })[0].code).toBe('crowded_dent');
    expect(dentingWarnings([3, 3], { maxEndsPerDent: 4 })).toEqual([]);
  });
});

describe('assertDentingFits', () => {
  it('passes a plan the reed can hold', () => {
    expect(assertDentingFits([2, 2], 10)).toEqual([2, 2]);
  });

  it('refuses one it cannot, naming both counts', () => {
    try {
      assertDentingFits([2, 2, 2], 2);
      throw new Error('should have thrown');
    } catch (err) {
      // A reed that is too small is a capacity problem rather than a fault in
      // the draft, and it reports the shortfall the same way every other
      // capacity check does.
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail).toMatchObject({ needed: 3, dentsAvailable: 2, over: 1 });
    }
  });
});
