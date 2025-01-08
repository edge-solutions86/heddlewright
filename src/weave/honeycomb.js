import { DraftError } from '../util/errors.js';
import { integer, positiveInteger } from '../util/check.js';
import { createMatrix } from '../util/matrix.js';
import { pointDraw } from '../draft/threading.js';

/**
 * Honeycomb, the waffle weave.
 *
 * The cloth is a grid of square cells. Long floats ride the ridges between
 * cells and the interlacement tightens towards the middle of each well, which
 * is what makes the cloth stand up off the loom. The rule is geometric: an end
 * is on the face while its distance from the ridge, added to the pick's own
 * distance from the ridge, still fits inside the cell.
 */

/** The repeat size of an order-n honeycomb, which is 2n - 2 threads each way. */
export function honeycombRepeat(order) {
  positiveInteger(order, 'order');
  if (order < 3) {
    throw new DraftError('a honeycomb cell needs at least three shafts', { order });
  }
  return 2 * order - 2;
}

/** Distance from the ridge, measured the short way round a repeat of `size`. */
export function ridgeDistance(index, size) {
  integer(index, 'index', { min: 0 });
  positiveInteger(size, 'size');
  const at = index % size;
  return Math.min(at, size - at);
}

/** One repeat of an order-n honeycomb as a picks-by-ends matrix. */
export function honeycombWeave(order) {
  const size = honeycombRepeat(order);
  const limit = order - 2;
  const repeat = createMatrix(size, size);
  for (let p = 0; p < size; p += 1) {
    for (let e = 0; e < size; e += 1) {
      const inside = ridgeDistance(e, size) + ridgeDistance(p, size) <= limit;
      repeat[p][e] = inside ? 1 : 0;
    }
  }
  return repeat;
}

/**
 * The draft a loom is set up from: a point threading, a point treadling and a
 * staircase tie-up where treadle t lifts the first n - t shafts.
 */
export function honeycombDraft(order) {
  const size = honeycombRepeat(order);
  const tieup = Array.from({ length: order }, (_, t) =>
    Array.from({ length: order - 1 - t }, (_, s) => s + 1),
  );
  const threading = pointDraw(order, size);
  const treadling = pointDraw(order, size).map((shaft) => [shaft]);
  return { threading, tieup, treadling, shafts: order, treadles: order, shed: 'rising' };
}

/** The longest warp float a honeycomb of this order carries on its ridge. */
export function ridgeFloat(order) {
  const size = honeycombRepeat(order);
  return size - 1;
}

/**
 * How deep the well is, counted as the number of picks over which the
 * interlacement tightens from the ridge to the centre of the cell.
 */
export function cellDepth(order) {
  positiveInteger(order, 'order');
  return order - 1;
}

/** A honeycomb is square: every repeat has as many picks as ends. */
export function isSquareCell(repeat) {
  return repeat.length === repeat[0].length;
}

/**
 * Two honeycombs of the same order sitting side by side, offset by half a
 * repeat, which staggers the cells the way a brick course is staggered.
 */
export function staggeredHoneycomb(order) {
  const base = honeycombWeave(order);
  const size = base.length;
  const half = Math.floor(size / 2);
  const out = createMatrix(size, size * 2);
  for (let p = 0; p < size; p += 1) {
    for (let e = 0; e < size; e += 1) {
      out[p][e] = base[p][e];
      out[p][size + e] = base[(p + half) % size][e];
    }
  }
  return out;
}
