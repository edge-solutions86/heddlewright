import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, dimensions } from '../../src/util/matrix.js';
import { warpFaceRatio, drawdownFromDraft } from '../../src/draft/drawdown.js';
import { longestRun } from '../../src/util/runs.js';
import { pointDraw } from '../../src/draft/threading.js';
import { twillTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import {
  HANDEDNESS,
  parseTwillNotation,
  formatTwillNotation,
  twillRepeatSize,
  twillPattern,
  twillWeave,
  brokenTwill,
  brokenDraftingOrder,
  pointedTwill,
  twillAngle,
  twillBalance,
  isWarpFaced,
  reverseTwill,
} from '../../src/weave/twill.js';

describe('parseTwillNotation', () => {
  it('reads slash notation and spaced notation the same way', () => {
    expect(parseTwillNotation('2/2')).toEqual([2, 2]);
    expect(parseTwillNotation('2 2')).toEqual([2, 2]);
    expect(parseTwillNotation(' 3/1 ')).toEqual([3, 1]);
  });

  it('reads a compound twill', () => {
    expect(parseTwillNotation('2/1/1/2')).toEqual([2, 1, 1, 2]);
  });

  it('refuses an odd number of parts, which would not alternate', () => {
    expect(() => parseTwillNotation('2/1/1')).toThrow(/alternate up and down/);
  });

  it('refuses a single part and a part that is not a count', () => {
    expect(() => parseTwillNotation('2')).toThrow(DraftError);
    expect(() => parseTwillNotation('2/x')).toThrow(/is not a count/);
    expect(() => parseTwillNotation('2/0')).toThrow(InvalidArgument);
  });

  it('round-trips through formatTwillNotation', () => {
    expect(formatTwillNotation(parseTwillNotation('2/1/1/2'))).toBe('2/1/1/2');
  });
});

describe('twillPattern and twillRepeatSize', () => {
  it('lays the counts out as face cells starting with the warp up', () => {
    expect(twillPattern([2, 2])).toEqual([1, 1, 0, 0]);
    expect(twillPattern([3, 1])).toEqual([1, 1, 1, 0]);
    expect(twillPattern([2, 1, 1, 2])).toEqual([1, 1, 0, 1, 0, 0]);
  });

  it('the repeat is the sum of the counts', () => {
    expect(twillRepeatSize([2, 2])).toBe(4);
    expect(twillRepeatSize([2, 1, 1, 2])).toBe(6);
  });
});

describe('twillWeave', () => {
  it('weaves the 2/2 twill, moving one end per pick', () => {
    expect(formatMatrix(twillWeave('2/2'))).toBe('XX..\n.XX.\n..XX\nX..X');
  });

  it('weaves the 3/1 warp-faced twill', () => {
    expect(formatMatrix(twillWeave('3/1'))).toBe('XXX.\n.XXX\nX.XX\nXX.X');
  });

  it('runs the line the other way for an S twill', () => {
    expect(formatMatrix(twillWeave('2/2', { handedness: 'S' }))).toBe('XX..\nX..X\n..XX\n.XX.');
  });

  it('reclines the line on a larger step', () => {
    expect(formatMatrix(twillWeave('2/2', { step: 2 }))).toBe('XX..\n..XX\nXX..\n..XX');
  });

  it('extends to a wider piece of cloth without changing the repeat', () => {
    const cloth = twillWeave('2/2', { picks: 8, ends: 12 });
    expect(dimensions(cloth)).toEqual({ rows: 8, cols: 12 });
    expect(formatMatrix(cloth).split('\n')[0]).toBe('XX..XX..XX..');
  });

  it('takes a parsed count list as well as a string', () => {
    expect(formatMatrix(twillWeave([2, 2]))).toBe(formatMatrix(twillWeave('2/2')));
  });

  it('floats exactly as far as the notation says', () => {
    const cloth = twillWeave('3/1', { picks: 8, ends: 8 });
    expect(longestRun(cloth[0], 1)).toBe(3);
    expect(longestRun(cloth[0], 0)).toBe(1);
  });

  it('rejects a handedness it does not know', () => {
    expect(() => twillWeave('2/2', { handedness: 'X' })).toThrow(InvalidArgument);
  });
});

describe('brokenDraftingOrder', () => {
  it('runs every second block backwards', () => {
    expect(brokenDraftingOrder(4, 2)).toEqual([0, 1, 3, 2]);
    expect(brokenDraftingOrder(8, 4)).toEqual([0, 1, 2, 3, 7, 6, 5, 4]);
  });

  it('is a permutation, so no end is drafted twice', () => {
    for (const width of [1, 2, 3, 4, 5]) {
      const order = brokenDraftingOrder(8, width);
      expect([...order].sort((a, b) => a - b)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
    }
  });

  it('leaves the order straight when the block covers everything', () => {
    expect(brokenDraftingOrder(4, 4)).toEqual([0, 1, 2, 3]);
  });
});

describe('brokenTwill', () => {
  it('breaks the line at the given width', () => {
    expect(formatMatrix(brokenTwill('2/2', { breakAt: 2 }))).toBe('XX..\n.X.X\n..XX\nX.X.');
  });

  it('is the plain twill when the break covers the whole repeat', () => {
    expect(formatMatrix(brokenTwill('2/2', { breakAt: 4 }))).toBe(formatMatrix(twillWeave('2/2')));
  });

  it('keeps the interlacement counts of the twill it came from', () => {
    const broken = brokenTwill('3/1', { breakAt: 2 });
    expect(warpFaceRatio(broken)).toBe(0.75);
    for (const row of broken) {
      expect(row.reduce((total, cell) => total + cell, 0)).toBe(3);
    }
  });
});

describe('pointedTwill', () => {
  it('turns the line back and makes a herringbone', () => {
    const cloth = pointedTwill('2/2', { width: 4 });
    expect(dimensions(cloth)).toEqual({ rows: 4, cols: 6 });
    expect(formatMatrix(cloth)).toBe('XX...X\n.XX.XX\n..XXX.\nX..X..');
  });

  it('agrees with weaving a point threading against a twill tie-up', () => {
    const woven = drawdownFromDraft({
      threading: pointDraw(4),
      tieup: twillTieup(4, 2),
      treadling: straightTreadling(4, 4),
      shafts: 4,
      treadles: 4,
    });
    expect(formatMatrix(pointedTwill('2/2', { width: 4 }))).toBe(formatMatrix(woven));
  });

  it('needs at least two ends before the turn', () => {
    expect(() => pointedTwill('2/2', { width: 1 })).toThrow(InvalidArgument);
  });
});

describe('twillAngle', () => {
  it('is forty five degrees on a square sett with a step of one', () => {
    expect(twillAngle({ endsPerInch: 24, picksPerInch: 24 })).toBe(45);
  });

  it('steepens when there are more ends than picks', () => {
    expect(twillAngle({ endsPerInch: 48, picksPerInch: 24 })).toBe(63.43);
  });

  it('reclines as the step grows', () => {
    expect(twillAngle({ step: 2, endsPerInch: 24, picksPerInch: 24 })).toBe(26.57);
  });

  it('needs both setts', () => {
    expect(() => twillAngle({ endsPerInch: 0, picksPerInch: 24 })).toThrow(InvalidArgument);
  });
});

describe('twillBalance', () => {
  it('adds the up counts and the down counts separately', () => {
    expect(twillBalance('2/2')).toEqual({ warp: 2, weft: 2, repeat: 4 });
    expect(twillBalance('3/1')).toEqual({ warp: 3, weft: 1, repeat: 4 });
    expect(twillBalance('2/1/1/2')).toEqual({ warp: 3, weft: 3, repeat: 6 });
  });

  it('agrees with the face ratio of the woven repeat', () => {
    const balance = twillBalance('3/1');
    expect(warpFaceRatio(twillWeave('3/1'))).toBe(balance.warp / balance.repeat);
  });

  it('calls a twill warp faced when it shows more warp', () => {
    expect(isWarpFaced('3/1')).toBe(true);
    expect(isWarpFaced('1/3')).toBe(false);
    expect(isWarpFaced('2/2')).toBe(false);
  });
});

describe('reverseTwill', () => {
  it('swaps each pair, which weaves the back of the cloth', () => {
    expect(reverseTwill('3/1')).toEqual([1, 3]);
    expect(reverseTwill('2/1/1/2')).toEqual([1, 2, 2, 1]);
  });

  it('is its own inverse', () => {
    expect(reverseTwill(reverseTwill('3/1'))).toEqual([3, 1]);
  });

  it('turns a warp-faced twill into a weft-faced one', () => {
    expect(isWarpFaced(reverseTwill('3/1'))).toBe(false);
  });
});

describe('HANDEDNESS', () => {
  it('names the two twill directions', () => {
    expect(HANDEDNESS).toEqual(['Z', 'S']);
  });
});
