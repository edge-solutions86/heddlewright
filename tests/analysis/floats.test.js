import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix } from '../../src/util/matrix.js';
import { plainWeave } from '../../src/weave/plain.js';
import { twillWeave } from '../../src/weave/twill.js';
import { satinWeave } from '../../src/weave/satin.js';
import {
  warpFloats,
  weftFloats,
  longestFloat,
  floatsOver,
  floatHistogram,
  meanFloatLength,
  floatLimitForSett,
  withinFloatLimit,
  floatOnEnd,
  floatOnPick,
} from '../../src/analysis/floats.js';

describe('warpFloats', () => {
  it('finds one float per end in a 2/2 twill, each two threads long', () => {
    const floats = warpFloats(twillWeave('2/2'));
    expect(floats.length).toBe(4);
    for (const float of floats) expect(float.length).toBe(2);
  });

  it('measures round the repeat, joining a float that wraps', () => {
    const floats = warpFloats(parseMatrix('X.\nX.\n..\nX.'));
    const onFirstEnd = floats.filter((float) => float.end === 1);
    expect(onFirstEnd.length).toBe(1);
    expect(onFirstEnd[0]).toMatchObject({ length: 3, wraps: true });
  });

  it('finds nothing on an end that is never raised', () => {
    expect(warpFloats(parseMatrix('.X\n.X')).filter((f) => f.end === 1)).toEqual([]);
  });

  it('reads a plain weave as floats of one', () => {
    for (const float of warpFloats(plainWeave())) expect(float.length).toBe(1);
  });
});

describe('weftFloats', () => {
  it('finds the weft floats of a 1/3 twill', () => {
    const floats = weftFloats(twillWeave('1/3'));
    expect(floats.length).toBe(4);
    for (const float of floats) expect(float.length).toBe(3);
  });

  it('reads the down cells, not the up ones', () => {
    const floats = weftFloats(parseMatrix('X...'));
    expect(floats).toEqual([{ pick: 1, start: 2, length: 3, wraps: false }]);
  });
});

describe('longestFloat', () => {
  it('reports each direction and which thread carries it', () => {
    const found = longestFloat(twillWeave('3/1'));
    expect(found.warp).toBe(3);
    expect(found.weft).toBe(1);
    expect(found.longest).toBe(3);
    expect(found.onEnd).toBeGreaterThan(0);
  });

  it('is one each way for plain weave', () => {
    expect(longestFloat(plainWeave())).toMatchObject({ warp: 1, weft: 1, longest: 1 });
  });

  it('measures a five thread satin at four', () => {
    expect(longestFloat(satinWeave(5, 2)).warp).toBe(4);
  });

  it('reports a null thread when a direction never floats', () => {
    const found = longestFloat(parseMatrix('XX\nXX'));
    expect(found.weft).toBe(0);
    expect(found.onPick).toBeNull();
  });
});

describe('floatsOver', () => {
  it('finds only floats past the limit and tags the direction', () => {
    const long = floatsOver(twillWeave('3/1'), 2);
    expect(long.length).toBe(4);
    for (const float of long) expect(float.direction).toBe('warp');
  });

  it('is empty when the cloth is inside the limit', () => {
    expect(floatsOver(plainWeave(), 1)).toEqual([]);
  });

  it('refuses a limit of zero', () => {
    expect(() => floatsOver(plainWeave(), 0)).toThrow(InvalidArgument);
  });
});

describe('floatHistogram', () => {
  it('buckets floats by length in each direction', () => {
    const { warp, weft } = floatHistogram(twillWeave('3/1'));
    expect(warp.get(3)).toBe(4);
    expect(weft.get(1)).toBe(4);
    expect(warp.has(1)).toBe(false);
  });
});

describe('meanFloatLength', () => {
  it('is one each way for plain weave', () => {
    expect(meanFloatLength(plainWeave())).toEqual({ warp: 1, weft: 1 });
  });

  it('shows the long warp float and the single binding point of a satin', () => {
    const satin = meanFloatLength(satinWeave(8, 3));
    expect(satin.warp).toBe(7);
    expect(satin.weft).toBe(1);
  });

  it('swaps the two readings when the satin is woven weft face up', () => {
    const sateen = meanFloatLength(satinWeave(8, 3, { face: 'weft' }));
    expect(sateen.warp).toBe(1);
    expect(sateen.weft).toBe(7);
  });

  it('is zero for a direction with no floats at all', () => {
    expect(meanFloatLength(parseMatrix('XX\nXX')).weft).toBe(0);
  });
});

describe('floatLimitForSett', () => {
  it('allows a longer float on a coarse sett than on a close one', () => {
    expect(floatLimitForSett(24)).toBe(2);
    expect(floatLimitForSett(60)).toBe(6);
    expect(floatLimitForSett(120)).toBe(12);
  });

  it('never falls below one thread', () => {
    expect(floatLimitForSett(4)).toBe(1);
  });

  it('takes a different span when the cloth wants one', () => {
    expect(floatLimitForSett(24, { spanInches: 0.25 })).toBe(6);
  });
});

describe('withinFloatLimit', () => {
  it('passes a cloth inside the limit and fails one past it', () => {
    expect(withinFloatLimit(twillWeave('2/2'), 2)).toBe(true);
    expect(withinFloatLimit(twillWeave('3/1'), 2)).toBe(false);
    expect(withinFloatLimit(satinWeave(8, 3), 7)).toBe(true);
  });
});

describe('floatOnEnd and floatOnPick', () => {
  const cloth = twillWeave('3/1');

  it('measures one named thread', () => {
    expect(floatOnEnd(cloth, 1)).toBe(3);
    expect(floatOnPick(cloth, 1)).toBe(1);
  });

  it('agrees with the whole-cloth reading', () => {
    const longest = longestFloat(cloth);
    expect(floatOnEnd(cloth, longest.onEnd)).toBe(longest.warp);
  });

  it('refuses a thread number of zero', () => {
    expect(() => floatOnEnd(cloth, 0)).toThrow(InvalidArgument);
  });
});
