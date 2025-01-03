import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix } from '../../src/util/matrix.js';
import { warpFaceRatio } from '../../src/draft/drawdown.js';
import {
  SATIN_FACES,
  regularSatinMoves,
  hasRegularSatin,
  assertSatinMove,
  satinWeave,
  irregularSatin,
  fourThreadSatinMoves,
  sixThreadSatinMoves,
  bindingPoints,
  bindingPointsIsolated,
  reverseSatin,
} from '../../src/weave/satin.js';

describe('regularSatinMoves', () => {
  it('gives both moves for a five thread satin', () => {
    expect(regularSatinMoves(5)).toEqual([2, 3]);
  });

  it('gives the two odd moves for an eight thread satin', () => {
    expect(regularSatinMoves(8)).toEqual([3, 5]);
  });

  it('lists every coprime move on a seven thread repeat', () => {
    expect(regularSatinMoves(7)).toEqual([2, 3, 4, 5]);
  });

  it('is empty for four and six, which is why those satins are irregular', () => {
    expect(regularSatinMoves(4)).toEqual([]);
    expect(regularSatinMoves(6)).toEqual([]);
    expect(hasRegularSatin(4)).toBe(false);
    expect(hasRegularSatin(6)).toBe(false);
    expect(hasRegularSatin(5)).toBe(true);
    expect(hasRegularSatin(8)).toBe(true);
  });
});

describe('assertSatinMove', () => {
  it('accepts a legal move', () => {
    expect(assertSatinMove(5, 2)).toBe(2);
    expect(assertSatinMove(8, 3)).toBe(3);
  });

  it('refuses a move of one either way, which would draw a twill', () => {
    expect(() => assertSatinMove(5, 1)).toThrow(/twill line/);
    expect(() => assertSatinMove(5, 4)).toThrow(/twill line/);
  });

  it('refuses a move sharing a factor with the repeat and says which factor', () => {
    try {
      assertSatinMove(8, 2);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.commonFactor).toBe(2);
    }
  });

  it('refuses a repeat too small to hold a satin at all', () => {
    expect(() => assertSatinMove(4, 2)).toThrow(/at least five threads/);
  });

  it('refuses a move as large as the repeat', () => {
    expect(() => assertSatinMove(5, 5)).toThrow(DraftError);
  });
});

describe('satinWeave', () => {
  it('binds once per pick on a five thread satin with a move of two', () => {
    expect(formatMatrix(satinWeave(5, 2))).toBe('.XXXX\nXX.XX\nXXXX.\nX.XXX\nXXX.X');
  });

  it('shows four fifths warp on a five thread warp satin', () => {
    expect(warpFaceRatio(satinWeave(5, 2))).toBe(0.8);
  });

  it('a sateen is the same cloth with the face swapped', () => {
    const sateen = satinWeave(5, 2, { face: 'weft' });
    expect(warpFaceRatio(sateen)).toBe(0.2);
    expect(formatMatrix(sateen)).toBe(formatMatrix(reverseSatin(satinWeave(5, 2))));
  });

  it('shows seven eighths warp on an eight thread satin', () => {
    expect(warpFaceRatio(satinWeave(8, 3))).toBe(0.875);
  });

  it('names the two faces', () => {
    expect(SATIN_FACES).toEqual(['warp', 'weft']);
    expect(() => satinWeave(5, 2, { face: 'both' })).toThrow(InvalidArgument);
  });
});

describe('bindingPoints', () => {
  it('walks the ends by the move number', () => {
    expect(bindingPoints(satinWeave(5, 2))).toEqual([1, 3, 5, 2, 4]);
    expect(bindingPoints(satinWeave(5, 3))).toEqual([1, 4, 2, 5, 3]);
  });

  it('visits every end exactly once', () => {
    for (const move of regularSatinMoves(8)) {
      const points = bindingPoints(satinWeave(8, move));
      expect([...points].sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    }
  });

  it('reads the weft face from the other side', () => {
    expect(bindingPoints(satinWeave(5, 2, { face: 'weft' }), { face: 'weft' })).toEqual([
      1, 3, 5, 2, 4,
    ]);
  });
});

describe('bindingPointsIsolated', () => {
  it('is true for every regular satin', () => {
    for (const threads of [5, 7, 8, 9, 10]) {
      for (const move of regularSatinMoves(threads)) {
        expect(bindingPointsIsolated(satinWeave(threads, move))).toBe(true);
      }
    }
  });

  it('is false for a twill, where the points sit side by side', () => {
    const twillLike = [
      [0, 1, 1, 1, 1],
      [1, 0, 1, 1, 1],
      [1, 1, 0, 1, 1],
      [1, 1, 1, 0, 1],
      [1, 1, 1, 1, 0],
    ];
    expect(bindingPointsIsolated(twillLike)).toBe(false);
  });
});

describe('irregularSatin', () => {
  it('builds the four thread satin from its written move sequence', () => {
    const repeat = irregularSatin(4, fourThreadSatinMoves());
    expect(warpFaceRatio(repeat)).toBe(0.75);
    expect(bindingPoints(repeat)).toEqual([1, 3, 2, 4]);
  });

  it('builds the six thread satin and keeps its binding points apart', () => {
    const repeat = irregularSatin(6, sixThreadSatinMoves());
    expect(bindingPoints(repeat)).toEqual([1, 3, 5, 2, 6, 4]);
    expect(bindingPointsIsolated(repeat)).toBe(true);
    expect(warpFaceRatio(repeat)).toBeCloseTo(5 / 6, 10);
  });

  it('cannot keep the four thread points apart, which is the honest reading', () => {
    expect(bindingPointsIsolated(irregularSatin(4, fourThreadSatinMoves()))).toBe(false);
  });

  it('refuses a sequence that does not reach every thread', () => {
    expect(() => irregularSatin(6, [2, 2, 2, 2, 2, 2])).toThrow(/does not reach every thread/);
  });

  it('refuses a sequence of the wrong length', () => {
    expect(() => irregularSatin(6, [2, 3])).toThrow(/one move per thread/);
  });

  it('can build a regular satin too, given its constant move', () => {
    const written = irregularSatin(5, [2, 2, 2, 2, 2]);
    expect(formatMatrix(written)).toBe(formatMatrix(satinWeave(5, 2)));
  });
});

describe('reverseSatin', () => {
  it('is its own inverse', () => {
    const repeat = satinWeave(7, 3);
    expect(formatMatrix(reverseSatin(reverseSatin(repeat)))).toBe(formatMatrix(repeat));
  });
});
