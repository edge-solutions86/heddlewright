import { DraftError } from '../util/errors.js';
import { integer, positiveInteger } from '../util/check.js';
import { assertMatrix, createMatrix, dimensions } from '../util/matrix.js';
import { longestRun } from '../util/runs.js';
import { mod } from '../util/numeric.js';
import { overlayWeaves } from './combine.js';
import { satinWeave, regularSatinMoves } from './satin.js';
import { twillWeave } from './twill.js';

/**
 * Crepe.
 *
 * A crepe has no line in it: no twill running across, no check, nothing the eye
 * can follow. That is harder to build than a pattern, because almost any rule
 * regular enough to write down leaves a regularity behind. The two devices that
 * work are laying a satin over a ground, which scatters the binding points, and
 * rearranging the threads of a known weave by a satin order, which breaks the
 * line without changing the interlacement counts.
 */

/**
 * Lay a satin over a twill ground, taking the cells where exactly one of them
 * raises. Union would give a cloth that is nearly all warp and intersection one
 * that is nearly all weft; it is the exclusive reading that scatters the
 * binding points and leaves the cloth balanced.
 */
export function satinCrepe({ threads = 8, move, ground } = {}) {
  const moves = regularSatinMoves(threads);
  if (moves.length === 0) {
    throw new DraftError('no regular satin exists on that many threads', { threads });
  }
  const chosen = move ?? moves[0];
  const satin = satinWeave(threads, chosen);
  const base = ground ?? twillWeave('2/2', { picks: threads, ends: threads });
  return overlayWeaves(satin, base, 'either');
}

/**
 * Rearrange the threads of a weave by a satin order. The interlacement of every
 * thread is untouched, so the cloth is as firm as it was; only the order they
 * lie in changes, and the line goes.
 */
export function rearrangedCrepe(weave, { move } = {}) {
  assertMatrix(weave, 'weave');
  const { rows, cols } = dimensions(weave);
  const moves = regularSatinMoves(cols);
  if (moves.length === 0) {
    throw new DraftError('no satin order exists for a repeat that size', { ends: cols });
  }
  const step = move ?? moves[0];
  integer(step, 'move');
  const out = createMatrix(rows, cols);
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) {
      out[p][e] = weave[p][mod(e * step, cols)];
    }
  }
  return out;
}

/**
 * The longest run of anything in one direction, which is the reading that says
 * whether a cloth has a line in it. A crepe should be low both ways.
 */
export function longestLine(repeat) {
  assertMatrix(repeat, 'repeat');
  const { cols } = dimensions(repeat);
  let warp = 0;
  let weft = 0;
  for (let e = 0; e < cols; e += 1) {
    const column = repeat.map((row) => row[e]);
    warp = Math.max(warp, longestRun(column, 1), longestRun(column, 0));
  }
  for (const row of repeat) {
    weft = Math.max(weft, longestRun(row, 1), longestRun(row, 0));
  }
  return { warp, weft, longest: Math.max(warp, weft) };
}

/**
 * How much of a diagonal a repeat carries: the share of cells that continue the
 * cell up and to the right of them. A twill scores one, because every cell does;
 * a scatter with no line in it sits nearer a half, and anything above about
 * four fifths still has a diagonal somebody will see.
 */
export function diagonalScore(repeat) {
  assertMatrix(repeat, 'repeat');
  const { rows, cols } = dimensions(repeat);
  let continued = 0;
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) {
      if (repeat[p][e] === repeat[(p + 1) % rows][(e + 1) % cols]) continued += 1;
    }
  }
  return continued / (rows * cols);
}

/**
 * A crepe reads as a crepe when no line runs far and no diagonal carries. The
 * limits are what a designer looks for rather than laws, so they come in.
 */
export function readsAsCrepe(repeat, { maxLine = 3, maxDiagonal = 0.8 } = {}) {
  positiveInteger(maxLine, 'maxLine');
  const line = longestLine(repeat);
  const diagonal = diagonalScore(repeat);
  const findings = [];
  if (line.longest > maxLine) {
    findings.push({ code: 'long_line', got: line.longest, limit: maxLine });
  }
  if (diagonal > maxDiagonal) {
    findings.push({ code: 'diagonal', got: diagonal, limit: maxDiagonal });
  }
  return { reads: findings.length === 0, line, diagonal, findings };
}
