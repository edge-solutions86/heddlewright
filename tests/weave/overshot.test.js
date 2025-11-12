import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { createDraft } from '../../src/draft/draft.js';
import { drawdownFromDraft, warpFaceRatio } from '../../src/draft/drawdown.js';
import { longestFloat } from '../../src/analysis/floats.js';
import {
  BLOCKS,
  blockShafts,
  blocksAdjoin,
  overshotThreading,
  overshotTieup,
  treadleForBlock,
  tabbyTreadles,
  overshotTreadling,
  overshotDraft,
  patternFloat,
  assertFloatWithin,
  blocksAfter,
} from '../../src/weave/overshot.js';

describe('the blocks', () => {
  it('names four, each on an overlapping pair of shafts', () => {
    expect(BLOCKS).toEqual(['A', 'B', 'C', 'D']);
    expect(blockShafts('A')).toEqual([1, 2]);
    expect(blockShafts('D')).toEqual([4, 1]);
  });

  it('has every block sharing a shaft with the next one round', () => {
    expect(blocksAdjoin('A', 'B')).toBe(true);
    expect(blocksAdjoin('D', 'A')).toBe(true);
    expect(blocksAdjoin('A', 'C')).toBe(false);
  });

  it('names what can follow a block', () => {
    expect(blocksAfter('A')).toEqual(['B', 'D']);
    expect(blocksAfter('B')).toEqual(['A', 'C']);
  });

  it('refuses a block it does not have', () => {
    expect(() => blockShafts('E')).toThrow(InvalidArgument);
  });
});

describe('overshotThreading', () => {
  it('threads each block on its pair, for as many units as it is given', () => {
    expect(overshotThreading(['A'], { units: 2 })).toEqual([1, 2, 1, 2]);
    expect(overshotThreading(['A', 'B'], { units: 1 })).toEqual([1, 2, 2, 3]);
  });

  it('refuses two blocks that do not share a shaft', () => {
    try {
      overshotThreading(['A', 'C']);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail).toMatchObject({ from: 'A', to: 'C' });
    }
  });

  it('threads two ends per unit', () => {
    expect(overshotThreading(['A', 'B', 'C'], { units: 3 }).length).toBe(3 * 3 * 2);
  });
});

describe('overshotTieup', () => {
  it('gives four pattern treadles and two tabby ones', () => {
    expect(overshotTieup()).toEqual([
      [1, 2],
      [2, 3],
      [3, 4],
      [4, 1],
      [1, 3],
      [2, 4],
    ]);
  });

  it('numbers the pattern treadles in block order', () => {
    expect(treadleForBlock('A')).toBe(1);
    expect(treadleForBlock('D')).toBe(4);
    expect(tabbyTreadles()).toEqual([5, 6]);
  });
});

describe('overshotTreadling', () => {
  it('puts a tabby pick after every pattern pick', () => {
    expect(overshotTreadling(['A'], { units: 2 })).toEqual([[1], [5], [1], [6]]);
  });

  it('alternates the tabby across a block change', () => {
    const picks = overshotTreadling(['A', 'B'], { units: 1 });
    expect(picks).toEqual([[1], [5], [2], [6]]);
  });

  it('starts on the other tabby when asked', () => {
    expect(overshotTreadling(['A'], { units: 1, startTabby: 1 })).toEqual([[1], [6]]);
  });

  it('weaves twice as many picks as there are pattern picks', () => {
    expect(overshotTreadling(['A', 'B', 'C'], { units: 2 }).length).toBe(3 * 2 * 2);
  });

  it('refuses a tabby start that is neither of the two', () => {
    expect(() => overshotTreadling(['A'], { startTabby: 2 })).toThrow(InvalidArgument);
  });
});

describe('overshotDraft', () => {
  const draft = createDraft(overshotDraft(['A', 'B', 'C', 'D'], { units: 2 }));

  it('is a four shaft draft on six treadles', () => {
    expect(draft.shafts).toBe(4);
    expect(draft.treadles).toBe(6);
    expect(draft.ends).toBe(16);
    expect(draft.picks).toBe(16);
  });

  it('weaves a cloth with more warp down than up, because the pattern floats', () => {
    const cloth = drawdownFromDraft(draft);
    expect(warpFaceRatio(cloth)).toBeGreaterThan(0.4);
    expect(warpFaceRatio(cloth)).toBeLessThan(0.6);
  });

  it('floats the pattern weft over the block it is weaving', () => {
    const cloth = drawdownFromDraft(draft);
    expect(longestFloat(cloth).weft).toBeGreaterThanOrEqual(4);
  });

  it('names itself after the block run', () => {
    expect(draft.name).toBe('overshot ABCD');
  });
});

describe('patternFloat', () => {
  it('is two ends per unit', () => {
    expect(patternFloat({ units: 2 })).toBe(4);
    expect(patternFloat({ units: 5 })).toBe(10);
  });

  it('passes a float inside the limit and refuses one past it', () => {
    expect(assertFloatWithin(6, { units: 3 })).toBe(6);
    expect(() => assertFloatWithin(5, { units: 3 })).toThrow(/longer than the cloth/);
  });
});
