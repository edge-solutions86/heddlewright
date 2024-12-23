import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, dimensions } from '../../src/util/matrix.js';
import { longestRun } from '../../src/util/runs.js';
import { warpFaceRatio } from '../../src/draft/drawdown.js';
import {
  plainWeave,
  basketWeave,
  warpRib,
  weftRib,
  irregularWarpRib,
  plainOnShafts,
  isPlainDerived,
} from '../../src/weave/plain.js';

describe('plainWeave', () => {
  it('is the two by two chequer', () => {
    expect(formatMatrix(plainWeave())).toBe('X.\n.X');
  });

  it('is balanced', () => {
    expect(warpFaceRatio(plainWeave())).toBe(0.5);
  });

  it('has no float longer than one thread either way', () => {
    const repeat = plainWeave();
    expect(longestRun(repeat[0], 1)).toBe(1);
    expect(longestRun([repeat[0][0], repeat[1][0]], 1)).toBe(1);
  });
});

describe('basketWeave', () => {
  it('builds the two by two basket', () => {
    expect(formatMatrix(basketWeave(2))).toBe('XX..\nXX..\n..XX\n..XX');
  });

  it('builds a half basket with two ends and one pick', () => {
    expect(formatMatrix(basketWeave(2, 1))).toBe('XX..\n..XX');
  });

  it('a one by one basket is plain weave', () => {
    expect(formatMatrix(basketWeave(1, 1))).toBe(formatMatrix(plainWeave()));
  });

  it('stays balanced whatever the group size', () => {
    expect(warpFaceRatio(basketWeave(3))).toBe(0.5);
    expect(warpFaceRatio(basketWeave(4, 2))).toBe(0.5);
  });

  it('floats exactly as far as the group is wide', () => {
    const repeat = basketWeave(3);
    expect(longestRun(repeat[0], 1)).toBe(3);
    expect(dimensions(repeat)).toEqual({ rows: 6, cols: 6 });
  });

  it('refuses a group of no threads', () => {
    expect(() => basketWeave(0)).toThrow(InvalidArgument);
  });
});

describe('warpRib', () => {
  it('holds each shed for the run of picks', () => {
    expect(formatMatrix(warpRib(2))).toBe('X.\nX.\n.X\n.X');
    expect(formatMatrix(warpRib(3))).toBe('X.\nX.\nX.\n.X\n.X\n.X');
  });

  it('is plain weave when the run is one', () => {
    expect(formatMatrix(warpRib(1))).toBe(formatMatrix(plainWeave()));
  });

  it('keeps the repeat two ends wide however long the run', () => {
    expect(dimensions(warpRib(5))).toEqual({ rows: 10, cols: 2 });
  });
});

describe('weftRib', () => {
  it('groups the ends instead of the picks', () => {
    expect(formatMatrix(weftRib(2))).toBe('XX..\n..XX');
  });

  it('is the transpose of the matching warp rib', () => {
    const wefted = weftRib(3);
    const warped = warpRib(3);
    expect(dimensions(wefted)).toEqual({ rows: 2, cols: 6 });
    expect(dimensions(warped)).toEqual({ rows: 6, cols: 2 });
  });
});

describe('irregularWarpRib', () => {
  it('runs each shed for its own count', () => {
    expect(formatMatrix(irregularWarpRib([3, 1]))).toBe('X.\nX.\nX.\n.X');
  });

  it('alternates the shed on every run', () => {
    expect(formatMatrix(irregularWarpRib([1, 2, 2, 1]))).toBe('X.\n.X\n.X\nX.\nX.\n.X');
  });

  it('refuses an odd number of runs, which would not close the repeat', () => {
    expect(() => irregularWarpRib([2, 1, 3])).toThrow(DraftError);
  });

  it('refuses a run of nothing', () => {
    expect(() => irregularWarpRib([2, 0])).toThrow(InvalidArgument);
  });
});

describe('plainOnShafts', () => {
  it('splits the odd and even shafts onto two treadles', () => {
    expect(plainOnShafts(4)).toEqual({
      tieup: [
        [1, 3],
        [2, 4],
      ],
      treadling: [[1], [2]],
      shafts: 4,
    });
  });

  it('handles the plain two shaft case', () => {
    expect(plainOnShafts(2).tieup).toEqual([[1], [2]]);
  });

  it('refuses an odd shaft count', () => {
    expect(() => plainOnShafts(5)).toThrow(DraftError);
  });
});

describe('isPlainDerived', () => {
  it('accepts the family', () => {
    expect(isPlainDerived(plainWeave())).toBe(true);
    expect(isPlainDerived(basketWeave(2))).toBe(true);
    expect(isPlainDerived(warpRib(2))).toBe(true);
  });

  it('rejects a repeat with a row that never binds', () => {
    expect(
      isPlainDerived([
        [1, 1],
        [0, 1],
      ]),
    ).toBe(false);
  });
});
