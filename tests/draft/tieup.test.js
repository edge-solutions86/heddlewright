import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix } from '../../src/util/matrix.js';
import {
  SHED_TYPES,
  normaliseTieup,
  assertTieupUsable,
  tieupMatrix,
  tieupFromMatrix,
  shaftMatrix,
  convertShed,
  untiedTreadles,
  duplicateTreadles,
  treadleLoad,
  twillTieup,
  directTieup,
} from '../../src/draft/tieup.js';

const TWILL_2_2 = [
  [1, 2],
  [2, 3],
  [3, 4],
  [1, 4],
];

describe('normaliseTieup', () => {
  it('sorts and deduplicates each treadle', () => {
    expect(normaliseTieup([[3, 1, 3]], 4)).toEqual([[1, 3]]);
  });

  it('keeps an untied treadle as an empty list', () => {
    expect(normaliseTieup([[], [1]], 4)).toEqual([[], [1]]);
  });

  it('rejects a shaft off the loom, naming the treadle', () => {
    expect(() => normaliseTieup([[1], [5]], 4)).toThrow(/tieup\[1\]\[0\]/);
  });

  it('does not touch the caller list', () => {
    const source = [[3, 1]];
    normaliseTieup(source, 4);
    expect(source).toEqual([[3, 1]]);
  });
});

describe('assertTieupUsable', () => {
  it('passes a tie-up with at least one tied treadle and reports the untied ones', () => {
    const result = assertTieupUsable([[1, 2], [], [3]], 4);
    expect(result.untied).toEqual([2]);
    expect(result.tieup[0]).toEqual([1, 2]);
  });

  it('refuses a tie-up where nothing is tied at all', () => {
    expect(() => assertTieupUsable([[], []], 4)).toThrow(DraftError);
  });
});

describe('tieupMatrix and tieupFromMatrix', () => {
  it('sets a cell for every tied shaft', () => {
    expect(formatMatrix(tieupMatrix(TWILL_2_2, 4))).toBe('XX..\n.XX.\n..XX\nX..X');
  });

  it('round-trips through the matrix form', () => {
    expect(tieupFromMatrix(tieupMatrix(TWILL_2_2, 4))).toEqual(TWILL_2_2);
  });

  it('keeps an untied treadle as an empty row', () => {
    expect(formatMatrix(tieupMatrix([[], [2]], 3))).toBe('...\n.X.');
    expect(tieupFromMatrix(tieupMatrix([[], [2]], 3))).toEqual([[], [2]]);
  });
});

describe('shaftMatrix', () => {
  it('is the transpose, one row per shaft', () => {
    expect(formatMatrix(shaftMatrix(TWILL_2_2, 4))).toBe('X..X\nXX..\n.XX.\n..XX');
  });
});

describe('convertShed', () => {
  it('names the two shed types', () => {
    expect(SHED_TYPES).toEqual(['rising', 'sinking']);
  });

  it('takes the complement when the loom changes', () => {
    expect(convertShed(TWILL_2_2, 4, 'rising', 'sinking')).toEqual([
      [3, 4],
      [1, 4],
      [1, 2],
      [2, 3],
    ]);
  });

  it('is its own inverse', () => {
    const sunk = convertShed(TWILL_2_2, 4, 'rising', 'sinking');
    expect(convertShed(sunk, 4, 'sinking', 'rising')).toEqual(TWILL_2_2);
  });

  it('only normalises when the shed does not change', () => {
    expect(convertShed([[2, 1]], 4, 'rising', 'rising')).toEqual([[1, 2]]);
  });

  it('turns an untied treadle into one that lifts everything', () => {
    expect(convertShed([[]], 3, 'rising', 'sinking')).toEqual([[1, 2, 3]]);
  });

  it('rejects a shed type it does not know', () => {
    expect(() => convertShed(TWILL_2_2, 4, 'rising', 'floating')).toThrow(InvalidArgument);
  });
});

describe('treadle reports', () => {
  it('finds untied treadles by number', () => {
    expect(untiedTreadles([[1], [], [2], []], 4)).toEqual([2, 4]);
    expect(untiedTreadles(TWILL_2_2, 4)).toEqual([]);
  });

  it('groups treadles tied the same way', () => {
    const groups = duplicateTreadles([[1, 2], [3], [2, 1], [3]], 4);
    expect(groups).toEqual([
      [1, 3],
      [2, 4],
    ]);
  });

  it('reports nothing when every treadle differs', () => {
    expect(duplicateTreadles(TWILL_2_2, 4)).toEqual([]);
  });

  it('counts the shafts each treadle lifts', () => {
    expect(treadleLoad(TWILL_2_2, 4)).toEqual([2, 2, 2, 2]);
    expect(treadleLoad([[1, 2, 3], []], 4)).toEqual([3, 0]);
  });
});

describe('twillTieup', () => {
  it('builds the 2/2 twill tie-up on four shafts', () => {
    expect(twillTieup(4, 2)).toEqual(TWILL_2_2);
  });

  it('builds a 3/1 twill by raising three', () => {
    expect(twillTieup(4, 3)).toEqual([
      [1, 2, 3],
      [2, 3, 4],
      [1, 3, 4],
      [1, 2, 4],
    ]);
  });

  it('builds the 1/3 tie-up on a single raise', () => {
    expect(twillTieup(4, 1)).toEqual([[1], [2], [3], [4]]);
  });

  it('refuses a raise that would lift every shaft', () => {
    expect(() => twillTieup(4, 4)).toThrow(InvalidArgument);
    expect(() => twillTieup(4, 0)).toThrow(InvalidArgument);
  });
});

describe('directTieup', () => {
  it('ties each treadle to its own shaft', () => {
    expect(directTieup(3)).toEqual([[1], [2], [3]]);
  });

  it('is the identity matrix', () => {
    expect(formatMatrix(tieupMatrix(directTieup(3), 3))).toBe('X..\n.X.\n..X');
  });

  it('matches a one-raise twill tie-up', () => {
    expect(directTieup(4)).toEqual(twillTieup(4, 1));
  });
});
