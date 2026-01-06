import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix } from '../../src/util/matrix.js';
import { createDraft } from '../../src/draft/draft.js';
import { drawdownFromDraft } from '../../src/draft/drawdown.js';
import { straightDraw } from '../../src/draft/threading.js';
import { twillTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import {
  DIRECTIONS,
  mirrorThreading,
  mirrorTreadling,
  shiftThreading,
  shiftTreadling,
  turnDraft,
  foldBack,
  foldDraft,
  extendDraft,
  compactShafts,
} from '../../src/draft/transform.js';

const TWILL = {
  name: 'twill',
  shafts: 4,
  treadles: 4,
  threading: straightDraw(4, 8),
  tieup: twillTieup(4, 2),
  treadling: straightTreadling(4, 8),
};

describe('mirroring', () => {
  it('reverses the threading', () => {
    expect(mirrorThreading(TWILL).threading).toEqual([4, 3, 2, 1, 4, 3, 2, 1]);
  });

  it('reverses the treadling', () => {
    expect(mirrorTreadling(TWILL).treadling[0]).toEqual([4]);
  });

  it('is its own inverse', () => {
    expect(mirrorThreading(mirrorThreading(TWILL)).threading).toEqual(TWILL.threading);
  });

  it('leaves the rest of the draft alone', () => {
    expect(mirrorThreading(TWILL).tieup).toEqual(TWILL.tieup);
  });
});

describe('shifting', () => {
  it('moves the threading along and wraps', () => {
    expect(shiftThreading(TWILL, 1).threading).toEqual([2, 3, 4, 1, 2, 3, 4, 1]);
    expect(shiftThreading(TWILL, -1).threading[0]).toBe(4);
  });

  it('moves the treadling along', () => {
    expect(shiftTreadling(TWILL, 2).treadling[0]).toEqual([3]);
  });

  it('a shift of the whole length changes nothing', () => {
    expect(shiftThreading(TWILL, 8).threading).toEqual(TWILL.threading);
  });
});

describe('turnDraft', () => {
  const turned = turnDraft(TWILL);

  it('swaps the shafts for the treadles', () => {
    expect(turned.shafts).toBe(TWILL.treadles);
    expect(turned.treadles).toBe(TWILL.shafts);
  });

  it('makes the old treadling the new threading', () => {
    expect(turned.threading).toEqual(TWILL.treadling.map((pick) => pick[0]));
  });

  it('transposes the tie-up', () => {
    expect(turned.tieup).toEqual([
      [1, 4],
      [1, 2],
      [2, 3],
      [3, 4],
    ]);
  });

  it('weaves the same cloth read the other way round', () => {
    const original = drawdownFromDraft(createDraft(TWILL));
    const other = drawdownFromDraft(createDraft(turned));
    const flipped = original[0].map((_, e) => original.map((row) => row[e]));
    expect(formatMatrix(other)).toBe(formatMatrix(flipped));
  });

  it('refuses a draft with a doubled pick, which has nowhere to go', () => {
    const doubled = { ...TWILL, treadling: [[1, 2], [3], [4], [1], [2], [3], [4], [1]] };
    expect(() => turnDraft(doubled)).toThrow(DraftError);
  });
});

describe('foldBack and foldDraft', () => {
  it('folds without repeating the turning thread', () => {
    expect(foldBack([1, 2, 3, 4])).toEqual([1, 2, 3, 4, 3, 2]);
  });

  it('needs at least two threads to fold', () => {
    expect(() => foldBack([1])).toThrow(InvalidArgument);
  });

  it('folds the warp, the weft or both', () => {
    expect(DIRECTIONS).toEqual(['warp', 'weft']);
    expect(foldDraft(TWILL, 'warp').threading.length).toBe(14);
    expect(foldDraft(TWILL, 'weft').treadling.length).toBe(14);
    const both = foldDraft(TWILL, 'both');
    expect(both.threading.length).toBe(14);
    expect(both.treadling.length).toBe(14);
  });

  it('refuses a direction it does not know', () => {
    expect(() => foldDraft(TWILL, 'sideways')).toThrow(InvalidArgument);
  });
});

describe('extendDraft', () => {
  it('repeats to the size asked for', () => {
    const wide = extendDraft(TWILL, { ends: 20, picks: 12 });
    expect(wide.threading.length).toBe(20);
    expect(wide.treadling.length).toBe(12);
    expect(wide.threading[19]).toBe(TWILL.threading[19 % 8]);
  });

  it('gives each pick its own array', () => {
    const wide = extendDraft(TWILL, { ends: 8, picks: 16 });
    wide.treadling[0].push(2);
    expect(wide.treadling[8]).toEqual([1]);
  });
});

describe('compactShafts', () => {
  it('renumbers a sparse threading onto the shafts it uses', () => {
    const sparse = { ...TWILL, shafts: 8, threading: [1, 4, 6, 4, 1, 6], tieup: twillTieup(8, 2) };
    const tight = compactShafts(sparse);
    expect(tight.shafts).toBe(3);
    expect(tight.threading).toEqual([1, 2, 3, 2, 1, 3]);
  });

  it('carries the tie-up across, dropping the shafts nothing was on', () => {
    const sparse = {
      ...TWILL,
      shafts: 8,
      threading: [1, 4],
      tieup: [
        [1, 4],
        [2, 3],
      ],
    };
    expect(compactShafts(sparse).tieup).toEqual([[1, 2], []]);
  });

  it('leaves a draft that already uses every shaft alone', () => {
    const tight = compactShafts(TWILL);
    expect(tight.shafts).toBe(4);
    expect(tight.threading).toEqual(TWILL.threading);
  });
});
