import { DraftError } from '../util/errors.js';
import { array, oneOf, positiveInteger } from '../util/check.js';
import { lcm } from '../util/numeric.js';
import { assertMatrix, createMatrix, dimensions } from '../util/matrix.js';

/**
 * Weave arithmetic.
 *
 * New cloths are built by putting known ones together: two weaves laid over one
 * another make a crepe, alternate ends taken from two weaves make a double
 * cloth face, and enlarging a weave thread for thread makes it coarser without
 * changing its structure. Every operation here takes and returns a repeat.
 */

export const OVERLAY_MODES = ['over', 'under', 'either'];

/** Enlarge a weave by repeating each pick and each end. */
export function expandWeave(repeat, downFactor = 2, acrossFactor = downFactor) {
  assertMatrix(repeat, 'repeat');
  positiveInteger(downFactor, 'downFactor');
  positiveInteger(acrossFactor, 'acrossFactor');
  const out = [];
  for (const row of repeat) {
    const wide = [];
    for (const cell of row) {
      for (let a = 0; a < acrossFactor; a += 1) wide.push(cell);
    }
    for (let d = 0; d < downFactor; d += 1) out.push([...wide]);
  }
  return out;
}

/** Grow two weaves to a common repeat, the smallest that holds both whole. */
export function alignWeaves(a, b) {
  assertMatrix(a, 'a');
  assertMatrix(b, 'b');
  const picks = lcm(a.length, b.length);
  const ends = lcm(a[0].length, b[0].length);
  return {
    picks,
    ends,
    a: stretchTo(a, picks, ends),
    b: stretchTo(b, picks, ends),
  };
}

function stretchTo(repeat, picks, ends) {
  const out = createMatrix(picks, ends);
  for (let p = 0; p < picks; p += 1) {
    for (let e = 0; e < ends; e += 1) {
      out[p][e] = repeat[p % repeat.length][e % repeat[0].length];
    }
  }
  return out;
}

/**
 * Lay one weave over another. `over` keeps a cell raised when either weave
 * raises it, `under` only when both do, and `either` raises it when exactly one
 * does, which is how a crepe is drawn from two plain grounds.
 */
export function overlayWeaves(a, b, mode = 'over') {
  oneOf(mode, 'mode', OVERLAY_MODES);
  const { picks, ends, a: left, b: right } = alignWeaves(a, b);
  const out = createMatrix(picks, ends);
  for (let p = 0; p < picks; p += 1) {
    for (let e = 0; e < ends; e += 1) {
      const x = left[p][e];
      const y = right[p][e];
      if (mode === 'over') out[p][e] = x === 1 || y === 1 ? 1 : 0;
      else if (mode === 'under') out[p][e] = x === 1 && y === 1 ? 1 : 0;
      else out[p][e] = x !== y ? 1 : 0;
    }
  }
  return out;
}

/**
 * Take ends alternately from two weaves in the order given, which is how the
 * face and back cloths of a double weave share a warp. The order is a list of
 * 0s and 1s naming which weave the next end comes from.
 */
export function interleaveEnds(a, b, order = [0, 1]) {
  const { picks, ends, a: left, b: right } = alignWeaves(a, b);
  array(order, 'order', { min: 1 });
  for (const [i, choice] of order.entries()) {
    oneOf(choice, `order[${i}]`, [0, 1]);
  }
  const width = ends * order.length;
  const out = createMatrix(picks, width);
  for (let p = 0; p < picks; p += 1) {
    for (let e = 0; e < width; e += 1) {
      const source = order[e % order.length] === 0 ? left : right;
      const column = Math.floor(e / order.length) % ends;
      out[p][e] = source[p][column];
    }
  }
  return out;
}

/** The same, taken pick by pick, which is how two cloths share a shuttle box. */
export function interleavePicks(a, b, order = [0, 1]) {
  const { picks, ends, a: left, b: right } = alignWeaves(a, b);
  array(order, 'order', { min: 1 });
  const height = picks * order.length;
  const out = createMatrix(height, ends);
  for (let p = 0; p < height; p += 1) {
    const source = order[p % order.length] === 0 ? left : right;
    const row = Math.floor(p / order.length) % picks;
    for (let e = 0; e < ends; e += 1) out[p][e] = source[row][e];
  }
  return out;
}

/**
 * Add stitching points to a weave: cells named by a second weave are forced
 * down, which is how a backing cloth is tied to its face.
 */
export function stitchWeave(repeat, stitches, { raise = false } = {}) {
  const { picks, ends, a: cloth, b: points } = alignWeaves(repeat, stitches);
  const out = createMatrix(picks, ends);
  for (let p = 0; p < picks; p += 1) {
    for (let e = 0; e < ends; e += 1) {
      out[p][e] = points[p][e] === 1 ? (raise ? 1 : 0) : cloth[p][e];
    }
  }
  return out;
}

/** Cut a piece out of a repeat, wrapping past its edges. */
export function cutWeave(repeat, { fromPick = 0, fromEnd = 0, picks, ends }) {
  assertMatrix(repeat, 'repeat');
  const size = dimensions(repeat);
  const rows = picks ?? size.rows;
  const cols = ends ?? size.cols;
  positiveInteger(rows, 'picks');
  positiveInteger(cols, 'ends');
  const out = createMatrix(rows, cols);
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) {
      out[p][e] = repeat[(fromPick + p) % size.rows][(fromEnd + e) % size.cols];
    }
  }
  return out;
}

/** Refuse a weave that has no interlacement at all, in either direction. */
export function assertInterlaces(repeat, label = 'repeat') {
  assertMatrix(repeat, label);
  const flat = repeat.flat();
  if (flat.every((cell) => cell === 1) || flat.every((cell) => cell === 0)) {
    throw new DraftError(`${label} does not interlace anywhere`, { label });
  }
  return repeat;
}
