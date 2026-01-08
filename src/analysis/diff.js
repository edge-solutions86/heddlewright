import { InvalidArgument } from '../util/errors.js';
import { number } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { assertMatrix, dimensions, createMatrix } from '../util/matrix.js';

/**
 * Comparing two cloths.
 *
 * A designer changes one thing in a draft and wants to know what it did. The
 * useful answer is not "they differ": it is which ends and which picks moved,
 * and by how much, because a change that shows on two ends is a correction and
 * a change that shows on every end is a different cloth.
 */

/** Refuse two cloths that cannot be compared cell for cell. */
export function assertSameShape(left, right) {
  const a = dimensions(assertMatrix(left, 'left'));
  const b = dimensions(assertMatrix(right, 'right'));
  if (a.rows !== b.rows || a.cols !== b.cols) {
    throw new InvalidArgument('those two cloths are different sizes', { left: a, right: b });
  }
  return a;
}

/** A matrix with a set cell wherever the two cloths disagree. */
export function differenceMask(left, right) {
  const { rows, cols } = assertSameShape(left, right);
  const mask = createMatrix(rows, cols);
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) {
      mask[p][e] = left[p][e] === right[p][e] ? 0 : 1;
    }
  }
  return mask;
}

/** How many cells differ, and what share of the cloth that is. */
export function differenceCount(left, right) {
  const { rows, cols } = assertSameShape(left, right);
  const mask = differenceMask(left, right);
  const cells = mask.reduce((total, row) => total + row.reduce((sum, cell) => sum + cell, 0), 0);
  return { cells, total: rows * cols, share: roundTo(cells / (rows * cols), 4) };
}

/** The 1-based ends that changed, and how many cells changed on each. */
export function endsChanged(left, right) {
  const { rows, cols } = assertSameShape(left, right);
  const mask = differenceMask(left, right);
  const out = [];
  for (let e = 0; e < cols; e += 1) {
    let cells = 0;
    for (let p = 0; p < rows; p += 1) cells += mask[p][e];
    if (cells > 0) out.push({ end: e + 1, cells });
  }
  return out;
}

/** The 1-based picks that changed, and how many cells changed on each. */
export function picksChanged(left, right) {
  assertSameShape(left, right);
  return differenceMask(left, right)
    .map((row, index) => ({
      pick: index + 1,
      cells: row.reduce((total, cell) => total + cell, 0),
    }))
    .filter((entry) => entry.cells > 0);
}

/** True when the two cloths are the same everywhere. */
export function sameCloth(left, right) {
  return differenceCount(left, right).cells === 0;
}

/**
 * A reading a designer can act on: whether the change is local, meaning it
 * touched a handful of threads, or wholesale.
 */
export function describeChange(left, right, { localShare = 0.1 } = {}) {
  number(localShare, 'localShare', { min: 0, max: 1 });
  const count = differenceCount(left, right);
  const ends = endsChanged(left, right);
  const picks = picksChanged(left, right);
  let kind = 'none';
  if (count.cells > 0) kind = count.share <= localShare ? 'local' : 'wholesale';
  return {
    ...count,
    kind,
    endsChanged: ends.length,
    picksChanged: picks.length,
    firstEnd: ends.length === 0 ? null : ends[0].end,
    firstPick: picks.length === 0 ? null : picks[0].pick,
  };
}

/** The two cloths side by side, for a report that has to show its working. */
export function renderSideBySide(left, right, { gap = '   ' } = {}) {
  const { rows } = assertSameShape(left, right);
  const lines = [];
  for (let p = 0; p < rows; p += 1) {
    const a = left[p].map((cell) => (cell === 1 ? 'X' : '.')).join('');
    const b = right[p].map((cell) => (cell === 1 ? 'X' : '.')).join('');
    const marks = left[p].map((cell, e) => (cell === right[p][e] ? ' ' : '!')).join('');
    lines.push(`${a}${gap}${b}${gap}${marks}`);
  }
  return lines.join('\n');
}
