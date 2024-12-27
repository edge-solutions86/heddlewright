import { DraftError } from '../util/errors.js';
import { array, integer, nonEmptyString, oneOf, positiveNumber } from '../util/check.js';
import { mod, roundTo, sum } from '../util/numeric.js';
import { createMatrix } from '../util/matrix.js';

/**
 * Twills.
 *
 * A twill is written as an alternating count of warp-up and warp-down threads,
 * "2/2" or "3/1" or "2/1/1/2", and the numbers add up to the repeat size. The
 * drawdown is drawn with the first pick at the bottom, so a Z twill, the
 * right-hand one, runs up to the right.
 */

export const HANDEDNESS = ['Z', 'S'];

/** Read "2/2" or "2 1 1 2" into a list of counts. */
export function parseTwillNotation(notation) {
  nonEmptyString(notation, 'notation');
  const parts = notation
    .trim()
    .split(/[/\s]+/)
    .filter((part) => part !== '');
  if (parts.length < 2) {
    throw new DraftError('a twill needs at least one up count and one down count', { notation });
  }
  const counts = parts.map((part, i) => {
    if (!/^\d+$/.test(part)) {
      throw new DraftError(`twill notation part "${part}" is not a count`, { notation, part });
    }
    return integer(Number.parseInt(part, 10), `notation[${i}]`, { min: 1 });
  });
  if (counts.length % 2 !== 0) {
    throw new DraftError('twill notation must alternate up and down, so it needs an even count', {
      notation,
      parts: counts.length,
    });
  }
  return counts;
}

/** Write a count list back as slash notation. */
export function formatTwillNotation(counts) {
  array(counts, 'counts', { min: 2 });
  return counts.join('/');
}

/** The repeat size of a twill is the sum of its counts. */
export function twillRepeatSize(counts) {
  return sum(counts);
}

/** The face pattern for one end of the repeat, starting with the warp up. */
export function twillPattern(counts) {
  array(counts, 'counts', { min: 2 });
  const pattern = [];
  for (const [i, count] of counts.entries()) {
    integer(count, `counts[${i}]`, { min: 1 });
    for (let n = 0; n < count; n += 1) pattern.push(i % 2 === 0 ? 1 : 0);
  }
  return pattern;
}

/** One repeat of a continuous twill as a picks-by-ends matrix. */
export function twillWeave(notation, { handedness = 'Z', step = 1, picks, ends } = {}) {
  const counts = Array.isArray(notation) ? notation : parseTwillNotation(notation);
  oneOf(handedness, 'handedness', HANDEDNESS);
  integer(step, 'step', { min: 1 });
  const pattern = twillPattern(counts);
  const size = pattern.length;
  const rows = picks ?? size;
  const cols = ends ?? size;
  integer(rows, 'picks', { min: 1 });
  integer(cols, 'ends', { min: 1 });
  const repeat = createMatrix(rows, cols);
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) {
      const shift = handedness === 'Z' ? e - p * step : e + p * step;
      repeat[p][e] = pattern[mod(shift, size)];
    }
  }
  return repeat;
}

/**
 * A broken twill: the drafting order runs straight for `breakAt` ends, then
 * back down for the next `breakAt`, so the diagonal never crosses the whole
 * repeat. It is a rearrangement of the ends, not a change of interlacement, so
 * every pick keeps the up and down counts the notation asked for.
 */
export function brokenTwill(notation, { breakAt, handedness = 'Z' } = {}) {
  const counts = Array.isArray(notation) ? notation : parseTwillNotation(notation);
  const pattern = twillPattern(counts);
  const size = pattern.length;
  const width = breakAt ?? size / 2;
  integer(width, 'breakAt', { min: 1 });
  oneOf(handedness, 'handedness', HANDEDNESS);
  const order = brokenDraftingOrder(size, width);
  const repeat = createMatrix(size, size);
  for (let p = 0; p < size; p += 1) {
    for (const [e, source] of order.entries()) {
      const shift = handedness === 'Z' ? source - p : source + p;
      repeat[p][e] = pattern[mod(shift, size)];
    }
  }
  return repeat;
}

/**
 * The end order a broken twill is drafted in: every second block of `width`
 * ends runs backwards. It is always a permutation of 0..size-1, which is what
 * keeps the interlacement counts intact.
 */
export function brokenDraftingOrder(size, width) {
  integer(size, 'size', { min: 1 });
  integer(width, 'width', { min: 1 });
  const order = [];
  for (let e = 0; e < size; e += 1) {
    const block = Math.floor(e / width);
    const within = e % width;
    const last = Math.min(width, size - block * width) - 1;
    order.push(block * width + (block % 2 === 0 ? within : last - within));
  }
  return order;
}

/**
 * A pointed twill turns the line back on itself, giving herringbone across the
 * cloth. `width` is how many ends run before the turn.
 */
export function pointedTwill(notation, { width, picks } = {}) {
  const counts = Array.isArray(notation) ? notation : parseTwillNotation(notation);
  const pattern = twillPattern(counts);
  const size = pattern.length;
  const half = width ?? size;
  integer(half, 'width', { min: 2 });
  const cols = half * 2 - 2;
  const rows = picks ?? size;
  const repeat = createMatrix(rows, cols);
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) {
      const folded = e < half ? e : cols - e;
      repeat[p][e] = pattern[mod(folded - p, size)];
    }
  }
  return repeat;
}

/**
 * The angle the twill line makes with the weft, in degrees. A step of one on a
 * square sett gives the familiar forty-five; a bigger step reclines it.
 */
export function twillAngle({ step = 1, endsPerInch, picksPerInch }) {
  integer(step, 'step', { min: 1 });
  positiveNumber(endsPerInch, 'endsPerInch');
  positiveNumber(picksPerInch, 'picksPerInch');
  const radians = Math.atan(endsPerInch / (step * picksPerInch));
  return roundTo((radians * 180) / Math.PI, 2);
}

/** How many of the repeat's threads are warp on the face, and how many weft. */
export function twillBalance(counts) {
  const list = Array.isArray(counts) ? counts : parseTwillNotation(counts);
  const up = list.filter((_, i) => i % 2 === 0);
  const down = list.filter((_, i) => i % 2 === 1);
  return { warp: sum(up), weft: sum(down), repeat: sum(list) };
}

/** True when a twill shows more warp than weft on the face. */
export function isWarpFaced(counts) {
  const { warp, weft } = twillBalance(counts);
  return warp > weft;
}

/** The twill that weaves the back of this one, by swapping every count pair. */
export function reverseTwill(counts) {
  const list = Array.isArray(counts) ? counts : parseTwillNotation(counts);
  const out = [];
  for (let i = 0; i < list.length; i += 2) {
    out.push(list[i + 1], list[i]);
  }
  return out;
}
