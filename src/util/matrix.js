import { InvalidArgument } from './errors.js';
import { array, integer, positiveInteger } from './check.js';

/**
 * Binary matrices, stored row-major as arrays of 0/1 numbers.
 *
 * A drawdown, a liftplan, a tie-up and a punched card are all one of these, so
 * every rotation, mirror and comparison in the package lands here. Rows are
 * picks and columns are ends, unless a caller says otherwise.
 */

export function createMatrix(rows, cols, fill = 0) {
  positiveInteger(rows, 'rows');
  positiveInteger(cols, 'cols');
  assertBit(fill, 'fill');
  return Array.from({ length: rows }, () => new Array(cols).fill(fill));
}

export function assertBit(value, field) {
  if (value !== 0 && value !== 1) {
    throw new InvalidArgument(`${field} must be 0 or 1`, { field, got: value });
  }
  return value;
}

/** Check a value really is a rectangular matrix of bits and return it. */
export function assertMatrix(matrix, field = 'matrix') {
  array(matrix, field, { min: 1 });
  const cols = matrix[0].length;
  if (cols === 0) {
    throw new InvalidArgument(`${field} rows must not be empty`, { field });
  }
  for (const [r, row] of matrix.entries()) {
    array(row, `${field}[${r}]`);
    if (row.length !== cols) {
      throw new InvalidArgument(`${field} is ragged`, {
        field,
        row: r,
        expected: cols,
        got: row.length,
      });
    }
    for (const [c, cell] of row.entries()) {
      assertBit(cell, `${field}[${r}][${c}]`);
    }
  }
  return matrix;
}

export function dimensions(matrix) {
  assertMatrix(matrix);
  return { rows: matrix.length, cols: matrix[0].length };
}

export function cloneMatrix(matrix) {
  assertMatrix(matrix);
  return matrix.map((row) => [...row]);
}

export function column(matrix, index) {
  assertMatrix(matrix);
  integer(index, 'index', { min: 0, max: matrix[0].length - 1 });
  return matrix.map((row) => row[index]);
}

export function row(matrix, index) {
  assertMatrix(matrix);
  integer(index, 'index', { min: 0, max: matrix.length - 1 });
  return [...matrix[index]];
}

export function columns(matrix) {
  assertMatrix(matrix);
  return matrix[0].map((_, c) => matrix.map((r) => r[c]));
}

export function matrixEquals(a, b) {
  assertMatrix(a, 'a');
  assertMatrix(b, 'b');
  if (a.length !== b.length || a[0].length !== b[0].length) return false;
  return a.every((rowA, r) => rowA.every((cell, c) => cell === b[r][c]));
}

/** Swap rows for columns. A liftplan transposed is a shaft-by-pick view. */
export function transpose(matrix) {
  assertMatrix(matrix);
  return columns(matrix);
}

/** Reverse the order of the rows, which turns a drawdown upside down. */
export function flipVertical(matrix) {
  assertMatrix(matrix);
  return [...matrix].reverse().map((r) => [...r]);
}

/** Reverse the order of the columns, which mirrors a draft left to right. */
export function flipHorizontal(matrix) {
  assertMatrix(matrix);
  return matrix.map((r) => [...r].reverse());
}

/** Swap every 0 for a 1. On a drawdown this is the reverse of the cloth. */
export function invert(matrix) {
  assertMatrix(matrix);
  return matrix.map((r) => r.map((cell) => (cell === 1 ? 0 : 1)));
}

/** Move rows and columns by a signed offset, wrapping round. */
export function rollMatrix(matrix, rowShift = 0, colShift = 0) {
  assertMatrix(matrix);
  integer(rowShift, 'rowShift');
  integer(colShift, 'colShift');
  const rows = matrix.length;
  const cols = matrix[0].length;
  const out = createMatrix(rows, cols);
  for (let r = 0; r < rows; r += 1) {
    for (let c = 0; c < cols; c += 1) {
      const sr = (((r - rowShift) % rows) + rows) % rows;
      const sc = (((c - colShift) % cols) + cols) % cols;
      out[r][c] = matrix[sr][sc];
    }
  }
  return out;
}

/** Repeat a matrix across and down, the way a repeat covers a cloth width. */
export function tile(matrix, downRepeats, acrossRepeats) {
  assertMatrix(matrix);
  positiveInteger(downRepeats, 'downRepeats');
  positiveInteger(acrossRepeats, 'acrossRepeats');
  const out = [];
  for (let d = 0; d < downRepeats; d += 1) {
    for (const r of matrix) {
      const line = [];
      for (let a = 0; a < acrossRepeats; a += 1) line.push(...r);
      out.push(line);
    }
  }
  return out;
}

/** Count the set cells, which is the raised-end count over a whole repeat. */
export function countOnes(matrix) {
  assertMatrix(matrix);
  return matrix.reduce((total, r) => total + r.reduce((t, cell) => t + cell, 0), 0);
}

/** Read a matrix out of a string of rows, using `on` for a set cell. */
export function parseMatrix(text, { on = 'X', off = '.' } = {}) {
  if (typeof text !== 'string') {
    throw new InvalidArgument('parseMatrix needs a string', { got: typeof text });
  }
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '');
  if (lines.length === 0) {
    throw new InvalidArgument('parseMatrix found no rows', {});
  }
  return assertMatrix(
    lines.map((line, r) =>
      [...line].map((ch, c) => {
        if (ch === on) return 1;
        if (ch === off) return 0;
        throw new InvalidArgument(`unexpected character in row ${r}`, { row: r, col: c, char: ch });
      }),
    ),
  );
}

/** Render a matrix back to the string form `parseMatrix` reads. */
export function formatMatrix(matrix, { on = 'X', off = '.' } = {}) {
  assertMatrix(matrix);
  return matrix.map((r) => r.map((cell) => (cell === 1 ? on : off)).join('')).join('\n');
}
