import { describe, it, expect } from 'vitest';
import { parseMatrix } from '../../src/util/matrix.js';
import { plainWeave, basketWeave } from '../../src/weave/plain.js';
import { twillWeave } from '../../src/weave/twill.js';
import { satinWeave } from '../../src/weave/satin.js';
import {
  intersectionsPerEnd,
  intersectionsPerPick,
  interlacementFactor,
  averageFloatFromIntersections,
  ashenhurstDivisor,
  looseEnds,
  firmnessClass,
  settRatio,
} from '../../src/analysis/interlacement.js';

describe('intersectionsPerEnd', () => {
  it('is two per end for plain weave, one up and one down', () => {
    expect(intersectionsPerEnd(plainWeave())).toEqual([2, 2]);
  });

  it('is two per end for a satin, which binds once and floats once', () => {
    expect(intersectionsPerEnd(satinWeave(8, 3))).toEqual([2, 2, 2, 2, 2, 2, 2, 2]);
  });

  it('counts round the repeat, so a wrapping float is one run', () => {
    expect(intersectionsPerEnd(parseMatrix('X.\nX.\n..\nX.'))[0]).toBe(2);
  });

  it('counts a constant end as a single unbroken run', () => {
    expect(intersectionsPerEnd(parseMatrix('X.\nX.'))).toEqual([1, 1]);
  });
});

describe('intersectionsPerPick', () => {
  it('reads the same count across the picks of a twill', () => {
    expect(intersectionsPerPick(twillWeave('2/2'))).toEqual([2, 2, 2, 2]);
  });
});

describe('interlacementFactor', () => {
  it('is one for plain weave, the firmest interlacement there is', () => {
    expect(interlacementFactor(plainWeave())).toBe(1);
  });

  it('falls as the floats lengthen', () => {
    const plain = interlacementFactor(plainWeave());
    const twill = interlacementFactor(twillWeave('2/2'));
    const satin = interlacementFactor(satinWeave(8, 3));
    expect(twill).toBeLessThan(plain);
    expect(satin).toBeLessThan(twill);
  });

  it('reads a two by two basket at a half', () => {
    expect(interlacementFactor(basketWeave(2))).toBe(0.5);
  });

  it('is a quarter for the eight thread satin', () => {
    expect(interlacementFactor(satinWeave(8, 3))).toBe(0.25);
  });
});

describe('averageFloatFromIntersections', () => {
  it('is one each way for plain weave', () => {
    expect(averageFloatFromIntersections(plainWeave())).toEqual({ warp: 1, weft: 1 });
  });

  it('is two each way for a 2/2 twill', () => {
    expect(averageFloatFromIntersections(twillWeave('2/2'))).toEqual({ warp: 2, weft: 2 });
  });

  it('is four each way for an eight thread satin', () => {
    expect(averageFloatFromIntersections(satinWeave(8, 3))).toEqual({ warp: 4, weft: 4 });
  });
});

describe('ashenhurstDivisor', () => {
  it('is one for plain weave, which needs no allowance at all', () => {
    expect(ashenhurstDivisor(plainWeave())).toEqual({ warp: 1, weft: 1 });
  });

  it('rises with the float length, so the cloth may be set closer', () => {
    expect(ashenhurstDivisor(twillWeave('2/2'))).toEqual({ warp: 2, weft: 2 });
    expect(ashenhurstDivisor(satinWeave(5, 2))).toEqual({ warp: 2.5, weft: 2.5 });
  });

  it('reads a warp rib unevenly, because only one direction floats', () => {
    const divisor = ashenhurstDivisor(parseMatrix('X.\nX.\n.X\n.X'));
    expect(divisor.warp).toBe(2);
    expect(divisor.weft).toBe(1);
  });
});

describe('looseEnds', () => {
  it('finds an end that never crosses', () => {
    expect(looseEnds(parseMatrix('X..\n.X.'))).toEqual([3]);
  });

  it('catches an end that is always up as well as one always down', () => {
    expect(looseEnds(parseMatrix('XX.\n.X.'))).toEqual([2, 3]);
  });

  it('is empty for a sound cloth', () => {
    expect(looseEnds(twillWeave('2/2'))).toEqual([]);
  });
});

describe('firmnessClass', () => {
  it('calls plain weave firm and a satin loose', () => {
    expect(firmnessClass(plainWeave())).toBe('firm');
    expect(firmnessClass(twillWeave('2/2'))).toBe('medium');
    expect(firmnessClass(satinWeave(8, 3))).toBe('open');
    expect(firmnessClass(satinWeave(16, 7))).toBe('loose');
  });
});

describe('settRatio', () => {
  it('says how much closer a satin may be set than plain weave', () => {
    expect(settRatio(satinWeave(5, 2), plainWeave())).toEqual({ warp: 2.5, weft: 2.5 });
  });

  it('is one for a cloth against itself', () => {
    expect(settRatio(twillWeave('2/2'), twillWeave('2/2'))).toEqual({ warp: 1, weft: 1 });
  });
});
