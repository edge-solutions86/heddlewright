import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, dimensions } from '../../src/util/matrix.js';
import { longestRun } from '../../src/util/runs.js';
import { drawdownFromDraft } from '../../src/draft/drawdown.js';
import {
  honeycombRepeat,
  ridgeDistance,
  honeycombWeave,
  honeycombDraft,
  ridgeFloat,
  cellDepth,
  isSquareCell,
  staggeredHoneycomb,
} from '../../src/weave/honeycomb.js';

describe('honeycombRepeat', () => {
  it('is twice the order less two', () => {
    expect(honeycombRepeat(5)).toBe(8);
    expect(honeycombRepeat(3)).toBe(4);
    expect(honeycombRepeat(9)).toBe(16);
  });

  it('refuses an order too small to make a cell', () => {
    expect(() => honeycombRepeat(2)).toThrow(DraftError);
    expect(() => honeycombRepeat(0)).toThrow(InvalidArgument);
  });
});

describe('ridgeDistance', () => {
  it('counts the short way round the repeat', () => {
    expect(ridgeDistance(0, 8)).toBe(0);
    expect(ridgeDistance(3, 8)).toBe(3);
    expect(ridgeDistance(4, 8)).toBe(4);
    expect(ridgeDistance(5, 8)).toBe(3);
    expect(ridgeDistance(7, 8)).toBe(1);
  });

  it('wraps an index past the repeat', () => {
    expect(ridgeDistance(9, 8)).toBe(1);
  });

  it('is symmetric about the far side', () => {
    for (let i = 0; i < 8; i += 1) {
      expect(ridgeDistance(i, 8)).toBe(ridgeDistance(8 - i, 8));
    }
  });
});

describe('honeycombWeave', () => {
  it('builds the nested cell of a five shaft waffle', () => {
    expect(formatMatrix(honeycombWeave(5))).toBe(
      [
        'XXXX.XXX',
        'XXX...XX',
        'XX.....X',
        'X.......',
        '........',
        'X.......',
        'XX.....X',
        'XXX...XX',
      ].join('\n'),
    );
  });

  it('is square and symmetric between warp and weft', () => {
    for (const order of [3, 5, 7]) {
      const repeat = honeycombWeave(order);
      expect(isSquareCell(repeat)).toBe(true);
      for (let p = 0; p < repeat.length; p += 1) {
        for (let e = 0; e < repeat.length; e += 1) {
          expect(repeat[p][e]).toBe(repeat[e][p]);
        }
      }
    }
  });

  it('leaves one pick with no warp at all, the floor of the cell', () => {
    const repeat = honeycombWeave(5);
    const bare = repeat.filter((row) => row.every((cell) => cell === 0));
    expect(bare.length).toBe(1);
  });

  it('rides a long ridge float down the first end', () => {
    const repeat = honeycombWeave(5);
    const firstEnd = repeat.map((row) => row[0]);
    expect(longestRun(firstEnd, 1)).toBe(ridgeFloat(5));
    expect(ridgeFloat(5)).toBe(7);
  });

  it('shrinks to a small cell on three shafts', () => {
    expect(formatMatrix(honeycombWeave(3))).toBe('XX.X\nX...\n....\nX...');
  });
});

describe('honeycombDraft', () => {
  it('weaves exactly the honeycomb the geometric rule describes', () => {
    for (const order of [3, 5, 7]) {
      const woven = drawdownFromDraft(honeycombDraft(order));
      expect(formatMatrix(woven)).toBe(formatMatrix(honeycombWeave(order)));
    }
  });

  it('uses a staircase tie-up whose last treadle lifts nothing', () => {
    const draft = honeycombDraft(5);
    expect(draft.tieup).toEqual([[1, 2, 3, 4], [1, 2, 3], [1, 2], [1], []]);
    expect(draft.shafts).toBe(5);
    expect(draft.treadles).toBe(5);
  });

  it('threads and treadles on the same point draw', () => {
    const draft = honeycombDraft(5);
    expect(draft.threading).toEqual([1, 2, 3, 4, 5, 4, 3, 2]);
    expect(draft.treadling.map((pick) => pick[0])).toEqual(draft.threading);
  });
});

describe('cellDepth', () => {
  it('is one less than the order', () => {
    expect(cellDepth(5)).toBe(4);
    expect(cellDepth(9)).toBe(8);
  });
});

describe('staggeredHoneycomb', () => {
  it('lays a second cell alongside, offset by half a repeat', () => {
    const staggered = staggeredHoneycomb(5);
    expect(dimensions(staggered)).toEqual({ rows: 8, cols: 16 });
    const base = honeycombWeave(5);
    for (let p = 0; p < 8; p += 1) {
      expect(staggered[p].slice(0, 8)).toEqual(base[p]);
      expect(staggered[p].slice(8)).toEqual(base[(p + 4) % 8]);
    }
  });
});
