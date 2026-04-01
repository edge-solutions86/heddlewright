import { InvalidArgument } from '../util/errors.js';
import { positiveInteger } from '../util/check.js';
import { assertMatrix, dimensions, formatMatrix } from '../util/matrix.js';

/**
 * Finding the repeat in a piece of cloth.
 *
 * A drawdown taken off a loom is usually several repeats wide and several deep,
 * and the useful thing to know is the smallest block that would rebuild it. It
 * is not enough to find a block that matches the first repeat: the block has to
 * rebuild every cell of the cloth, which is what this checks.
 */

/** True when a block of this size, tiled, rebuilds the whole cloth. */
export function tilesCloth(cloth, { picks, ends }) {
  assertMatrix(cloth, 'cloth');
  positiveInteger(picks, 'picks');
  positiveInteger(ends, 'ends');
  const size = dimensions(cloth);
  for (let p = 0; p < size.rows; p += 1) {
    for (let e = 0; e < size.cols; e += 1) {
      if (cloth[p][e] !== cloth[p % picks][e % ends]) return false;
    }
  }
  return true;
}

/** The smallest number of picks the cloth repeats on. */
export function pickRepeat(cloth) {
  const { rows, cols } = dimensions(cloth);
  for (let picks = 1; picks <= rows; picks += 1) {
    if (tilesCloth(cloth, { picks, ends: cols })) return picks;
  }
  return rows;
}

/** The smallest number of ends the cloth repeats on. */
export function endRepeat(cloth) {
  const { rows, cols } = dimensions(cloth);
  for (let ends = 1; ends <= cols; ends += 1) {
    if (tilesCloth(cloth, { picks: rows, ends })) return ends;
  }
  return cols;
}

/** The smallest block that rebuilds the cloth, with its size. */
export function motif(cloth) {
  const picks = pickRepeat(cloth);
  const ends = endRepeat(cloth);
  return {
    picks,
    ends,
    block: cloth.slice(0, picks).map((row) => row.slice(0, ends)),
  };
}

/** How many times the motif covers the cloth, each way. */
export function repeatsAcross(cloth) {
  const { rows, cols } = dimensions(cloth);
  const found = motif(cloth);
  return {
    down: rows / found.picks,
    across: cols / found.ends,
    whole: rows % found.picks === 0 && cols % found.ends === 0,
  };
}

/**
 * The cloth cut down to one motif, which is what should be stored. Anything
 * larger is the same information written out several times.
 */
export function reduceToMotif(cloth) {
  return motif(cloth).block;
}

/** How much smaller the motif is than the cloth it came from. */
export function compression(cloth) {
  const { rows, cols } = dimensions(cloth);
  const found = motif(cloth);
  return {
    cells: rows * cols,
    motifCells: found.picks * found.ends,
    ratio: (found.picks * found.ends) / (rows * cols),
  };
}

/** Whether two cloths are built from the same motif. */
export function sameMotif(left, right) {
  return formatMatrix(reduceToMotif(left)) === formatMatrix(reduceToMotif(right));
}

/**
 * Refuse a cloth whose motif does not divide it evenly. A drawdown that is nine
 * ends wide on a four end repeat is a drawdown somebody cut in the wrong place,
 * and everything downstream will be a quarter of a repeat out.
 */
export function assertWholeRepeats(cloth) {
  const found = repeatsAcross(cloth);
  if (!found.whole) {
    throw new InvalidArgument('the cloth is not a whole number of repeats', {
      down: found.down,
      across: found.across,
    });
  }
  return found;
}
