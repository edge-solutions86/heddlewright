import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  createMatrix,
  assertBit,
  assertMatrix,
  dimensions,
  cloneMatrix,
  column,
  row,
  columns,
  matrixEquals,
  transpose,
  flipVertical,
  flipHorizontal,
  invert,
  rollMatrix,
  tile,
  countOnes,
  parseMatrix,
  formatMatrix,
} from '../../src/util/matrix.js';

const PLAIN = parseMatrix('X.\n.X');

describe('createMatrix', () => {
  it('builds a rectangle of the requested fill', () => {
    expect(createMatrix(2, 3)).toEqual([
      [0, 0, 0],
      [0, 0, 0],
    ]);
    expect(createMatrix(1, 2, 1)).toEqual([[1, 1]]);
  });

  it('gives every row its own array', () => {
    const m = createMatrix(2, 2);
    m[0][0] = 1;
    expect(m[1][0]).toBe(0);
  });

  it('refuses zero dimensions and a fill that is not a bit', () => {
    expect(() => createMatrix(0, 3)).toThrow(InvalidArgument);
    expect(() => createMatrix(2, 0)).toThrow(InvalidArgument);
    expect(() => createMatrix(2, 2, 2)).toThrow(/must be 0 or 1/);
  });
});

describe('assertBit', () => {
  it('takes only the two numbers, not their truthy neighbours', () => {
    expect(assertBit(0, 'c')).toBe(0);
    expect(assertBit(1, 'c')).toBe(1);
    expect(() => assertBit(true, 'c')).toThrow(InvalidArgument);
    expect(() => assertBit('1', 'c')).toThrow(InvalidArgument);
  });
});

describe('assertMatrix', () => {
  it('rejects a ragged matrix and says which row', () => {
    try {
      assertMatrix([[1, 0], [1]]);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.detail).toMatchObject({ row: 1, expected: 2, got: 1 });
    }
  });

  it('rejects an empty matrix and an empty row', () => {
    expect(() => assertMatrix([])).toThrow(InvalidArgument);
    expect(() => assertMatrix([[]])).toThrow(/must not be empty/);
  });

  it('rejects a non-bit cell naming its position', () => {
    expect(() => assertMatrix([[0, 2]])).toThrow(/matrix\[0\]\[1\]/);
  });
});

describe('shape readers', () => {
  it('reports dimensions rows first', () => {
    expect(dimensions(createMatrix(3, 5))).toEqual({ rows: 3, cols: 5 });
  });

  it('reads a column down and a row across', () => {
    const m = parseMatrix('XX.\n..X');
    expect(column(m, 0)).toEqual([1, 0]);
    expect(column(m, 2)).toEqual([0, 1]);
    expect(row(m, 1)).toEqual([0, 0, 1]);
  });

  it('columns lists every column left to right', () => {
    expect(columns(parseMatrix('X.\n.X'))).toEqual([
      [1, 0],
      [0, 1],
    ]);
  });

  it('bounds-checks the index', () => {
    expect(() => column(PLAIN, 2)).toThrow(InvalidArgument);
    expect(() => row(PLAIN, -1)).toThrow(InvalidArgument);
  });

  it('hands back copies, so editing the result leaves the matrix alone', () => {
    const m = cloneMatrix(PLAIN);
    row(m, 0)[0] = 0;
    column(m, 0)[0] = 0;
    expect(m[0][0]).toBe(1);
  });
});

describe('matrixEquals', () => {
  it('is true only for the same shape and the same cells', () => {
    expect(matrixEquals(PLAIN, parseMatrix('X.\n.X'))).toBe(true);
    expect(matrixEquals(PLAIN, parseMatrix('.X\nX.'))).toBe(false);
    expect(matrixEquals(PLAIN, parseMatrix('X.\n.X\nX.'))).toBe(false);
    expect(matrixEquals(PLAIN, parseMatrix('X..\n.X.'))).toBe(false);
  });
});

describe('transforms', () => {
  const m = parseMatrix('XX.\n..X');

  it('transposes rows into columns', () => {
    expect(formatMatrix(transpose(m))).toBe('X.\nX.\n.X');
    expect(matrixEquals(transpose(transpose(m)), m)).toBe(true);
  });

  it('flips vertically and horizontally', () => {
    expect(formatMatrix(flipVertical(m))).toBe('..X\nXX.');
    expect(formatMatrix(flipHorizontal(m))).toBe('.XX\nX..');
  });

  it('inverts every cell, which is the back of the cloth', () => {
    expect(formatMatrix(invert(m))).toBe('..X\nXX.');
    expect(matrixEquals(invert(invert(m)), m)).toBe(true);
  });

  it('flipping does not touch the original', () => {
    const original = formatMatrix(m);
    flipVertical(m);
    flipHorizontal(m);
    invert(m);
    expect(formatMatrix(m)).toBe(original);
  });
});

describe('rollMatrix', () => {
  const m = parseMatrix('X..\n.X.\n..X');

  it('moves rows down and columns right on a positive shift', () => {
    expect(formatMatrix(rollMatrix(m, 1, 0))).toBe('..X\nX..\n.X.');
    expect(formatMatrix(rollMatrix(m, 0, 1))).toBe('.X.\n..X\nX..');
  });

  it('wraps a negative shift the other way', () => {
    expect(matrixEquals(rollMatrix(m, -1, 0), rollMatrix(m, 2, 0))).toBe(true);
  });

  it('a shift of a whole repeat is the identity', () => {
    expect(matrixEquals(rollMatrix(m, 3, 3), m)).toBe(true);
    expect(matrixEquals(rollMatrix(m, 0, 0), m)).toBe(true);
  });
});

describe('tile', () => {
  it('repeats the block down and across', () => {
    const t = tile(PLAIN, 2, 3);
    expect(dimensions(t)).toEqual({ rows: 4, cols: 6 });
    expect(formatMatrix(t)).toBe('X.X.X.\n.X.X.X\nX.X.X.\n.X.X.X');
  });

  it('one by one gives an equal matrix', () => {
    expect(matrixEquals(tile(PLAIN, 1, 1), PLAIN)).toBe(true);
  });

  it('needs at least one repeat each way', () => {
    expect(() => tile(PLAIN, 0, 1)).toThrow(InvalidArgument);
  });
});

describe('countOnes', () => {
  it('counts raised cells over the whole matrix', () => {
    expect(countOnes(PLAIN)).toBe(2);
    expect(countOnes(createMatrix(3, 4, 1))).toBe(12);
    expect(countOnes(createMatrix(3, 4))).toBe(0);
  });
});

describe('parseMatrix and formatMatrix', () => {
  it('round-trips through the text form', () => {
    const text = 'XX..\n..XX\nX..X';
    expect(formatMatrix(parseMatrix(text))).toBe(text);
  });

  it('ignores blank lines and surrounding space', () => {
    expect(formatMatrix(parseMatrix('\n  X.  \n  .X\n\n'))).toBe('X.\n.X');
  });

  it('takes different symbols when asked', () => {
    const m = parseMatrix('10\n01', { on: '1', off: '0' });
    expect(matrixEquals(m, PLAIN)).toBe(true);
    expect(formatMatrix(m, { on: '#', off: ' ' })).toBe('# \n #');
  });

  it('names the character it could not read', () => {
    try {
      parseMatrix('X.\n.?');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.detail).toEqual({ row: 1, col: 1, char: '?' });
    }
  });

  it('refuses empty input and a non-string', () => {
    expect(() => parseMatrix('   \n\n')).toThrow(/found no rows/);
    expect(() => parseMatrix(null)).toThrow(/needs a string/);
  });
});
