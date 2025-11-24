import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { createDraft } from '../../src/draft/draft.js';
import { drawdownFromDraft } from '../../src/draft/drawdown.js';
import { longestFloat } from '../../src/analysis/floats.js';
import {
  TIE_SHAFTS,
  shaftsForBlocks,
  blocksForShafts,
  shaftForBlock,
  summerWinterThreading,
  summerWinterTieup,
  treadlesForBlock,
  tabbyTreadles,
  summerWinterTreadling,
  summerWinterDraft,
  maximumFloat,
  assertTiesThreaded,
  endsForBlocks,
} from '../../src/weave/summerwinter.js';

describe('shafts and blocks', () => {
  it('keeps two shafts back for the ties', () => {
    expect(TIE_SHAFTS).toEqual([1, 2]);
    expect(shaftsForBlocks(4)).toBe(6);
    expect(blocksForShafts(6)).toBe(4);
  });

  it('puts each block on the next shaft after the ties', () => {
    expect(shaftForBlock(1)).toBe(3);
    expect(shaftForBlock(4)).toBe(6);
  });

  it('refuses a loom with nothing left after the ties', () => {
    expect(() => blocksForShafts(2)).toThrow(DraftError);
    expect(() => blocksForShafts(0)).toThrow(InvalidArgument);
  });
});

describe('summerWinterThreading', () => {
  it('threads a unit as tie, pattern, tie, pattern', () => {
    expect(summerWinterThreading([1])).toEqual([1, 3, 2, 3]);
  });

  it('takes four ends per unit', () => {
    expect(summerWinterThreading([1], { units: 3 }).length).toBe(12);
    expect(endsForBlocks([1, 2], { units: 3 })).toBe(24);
  });

  it('changes only the pattern shaft between blocks', () => {
    expect(summerWinterThreading([1, 2])).toEqual([1, 3, 2, 3, 1, 4, 2, 4]);
  });

  it('always threads both ties', () => {
    expect(assertTiesThreaded(summerWinterThreading([1, 2, 3]))).toBeTruthy();
    expect(() => assertTiesThreaded([3, 4, 3, 4])).toThrow(/tie shaft 1/);
  });
});

describe('summerWinterTieup', () => {
  it('gives two treadles per block and two for the tabby', () => {
    expect(summerWinterTieup(2)).toEqual([
      [1, 3],
      [2, 3],
      [1, 4],
      [2, 4],
      [1, 2],
      [3, 4],
    ]);
  });

  it('numbers the treadles for a block', () => {
    expect(treadlesForBlock(1)).toEqual([1, 2]);
    expect(treadlesForBlock(3)).toEqual([5, 6]);
    expect(tabbyTreadles(3)).toEqual([7, 8]);
  });

  it('lifts every pattern shaft on the second tabby treadle', () => {
    expect(summerWinterTieup(4).at(-1)).toEqual([3, 4, 5, 6]);
  });
});

describe('summerWinterTreadling', () => {
  it('takes both treadles of a block in turn', () => {
    expect(summerWinterTreadling([1], 2)).toEqual([[1], [2]]);
    expect(summerWinterTreadling([2], 2)).toEqual([[3], [4]]);
  });

  it('repeats for each unit', () => {
    expect(summerWinterTreadling([1], 2, { units: 2 })).toEqual([[1], [2], [1], [2]]);
  });

  it('refuses a block the loom is not tied for', () => {
    expect(() => summerWinterTreadling([3], 2)).toThrow(/past the ones the loom is tied for/);
  });
});

describe('summerWinterDraft', () => {
  const draft = createDraft(summerWinterDraft([1, 2, 1], { units: 2 }));

  it('sizes the loom from the highest block used', () => {
    expect(draft.shafts).toBe(4);
    expect(draft.treadles).toBe(6);
  });

  it('threads four ends per unit and weaves two picks per unit', () => {
    expect(draft.ends).toBe(24);
    expect(draft.picks).toBe(12);
  });

  it('never floats the weft further than two ends', () => {
    const cloth = drawdownFromDraft(draft);
    expect(longestFloat(cloth).weft).toBeLessThanOrEqual(maximumFloat() + 1);
  });

  it('names itself after the block run', () => {
    expect(draft.name).toBe('summer and winter 121');
  });

  it('binds every end into the cloth', () => {
    const cloth = drawdownFromDraft(draft);
    for (const row of cloth) {
      expect(row.some((cell) => cell === 1)).toBe(true);
    }
  });
});
