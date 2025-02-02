import { DraftError } from '../util/errors.js';
import { array, integer, positiveInteger, oneOf } from '../util/check.js';
import { createMatrix, assertMatrix, transpose } from '../util/matrix.js';
import { positionsWhere, setPositions, duplicateGroups } from '../util/indexing.js';

/**
 * The tie-up: which shafts each treadle lifts. Held as an array of sorted,
 * deduplicated shaft numbers per treadle, because that is how a weaver writes
 * it on paper, with a matrix view available for the drawdown to consume.
 */

export const SHED_TYPES = ['rising', 'sinking'];

/** Check a tie-up against a shaft count, returning a normalised copy. */
export function normaliseTieup(tieup, shafts) {
  array(tieup, 'tieup', { min: 1 });
  positiveInteger(shafts, 'shafts');
  return tieup.map((entry, t) => {
    array(entry, `tieup[${t}]`);
    const unique = [...new Set(entry)].sort((a, b) => a - b);
    for (const [i, shaft] of unique.entries()) {
      integer(shaft, `tieup[${t}][${i}]`, { min: 1, max: shafts });
    }
    return unique;
  });
}

/** A tie-up with no shaft tied to any treadle would open no shed at all. */
export function assertTieupUsable(tieup, shafts) {
  const normalised = normaliseTieup(tieup, shafts);
  const empty = normalised
    .map((entry, index) => (entry.length === 0 ? index + 1 : 0))
    .filter((treadle) => treadle !== 0);
  if (empty.length === normalised.length) {
    throw new DraftError('every treadle is untied, so no shed can open', {
      treadles: normalised.length,
    });
  }
  return { tieup: normalised, untied: empty };
}

/** The tie-up as a treadle-by-shaft binary matrix. */
export function tieupMatrix(tieup, shafts) {
  const normalised = normaliseTieup(tieup, shafts);
  const matrix = createMatrix(normalised.length, shafts);
  for (const [t, entry] of normalised.entries()) {
    for (const shaft of entry) matrix[t][shaft - 1] = 1;
  }
  return matrix;
}

/** Read a treadle-by-shaft matrix back into the list form. */
export function tieupFromMatrix(matrix) {
  assertMatrix(matrix, 'tieup matrix');
  return matrix.map((row) => setPositions(row));
}

/** The shaft-by-treadle view, which is how a countermarch loom is corded. */
export function shaftMatrix(tieup, shafts) {
  return transpose(tieupMatrix(tieup, shafts));
}

/**
 * Convert between rising and sinking sheds. A jack loom lifts what is tied; a
 * counterbalance loom sinks it, so the same cloth needs the complementary
 * tie-up on the other kind of loom.
 */
export function convertShed(tieup, shafts, from, to) {
  oneOf(from, 'from', SHED_TYPES);
  oneOf(to, 'to', SHED_TYPES);
  const normalised = normaliseTieup(tieup, shafts);
  if (from === to) return normalised;
  const all = Array.from({ length: shafts }, (_, i) => i + 1);
  return normalised.map((entry) => all.filter((shaft) => !entry.includes(shaft)));
}

/** Treadles tied to nothing, which a weaver has left spare on the lamms. */
export function untiedTreadles(tieup, shafts) {
  return positionsWhere(normaliseTieup(tieup, shafts), (entry) => entry.length === 0);
}

/** Treadles tied identically, which are interchangeable at the loom. */
export function duplicateTreadles(tieup, shafts) {
  return duplicateGroups(normaliseTieup(tieup, shafts), (entry) => entry.join(','));
}

/** How many shafts each treadle lifts, which is the weight on the foot. */
export function treadleLoad(tieup, shafts) {
  return normaliseTieup(tieup, shafts).map((entry) => entry.length);
}

/**
 * A standard twill tie-up: treadle t lifts `raise` consecutive shafts starting
 * at t. The 2/2 twill tie-up over four shafts is the canonical case.
 */
export function twillTieup(shafts, raise) {
  positiveInteger(shafts, 'shafts');
  integer(raise, 'raise', { min: 1, max: shafts - 1 });
  return Array.from({ length: shafts }, (_, t) =>
    Array.from({ length: raise }, (_, r) => ((t + r) % shafts) + 1).sort((a, b) => a - b),
  );
}

/** The tie-up in which treadle t lifts only shaft t, the direct tie of a dobby. */
export function directTieup(shafts) {
  positiveInteger(shafts, 'shafts');
  return Array.from({ length: shafts }, (_, t) => [t + 1]);
}
