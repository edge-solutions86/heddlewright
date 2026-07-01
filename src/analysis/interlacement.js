import { positiveNumber } from '../util/check.js';
import { roundTo, mean, sum } from '../util/numeric.js';
import { assertMatrix, columns, dimensions } from '../util/matrix.js';
import { countRuns } from '../util/runs.js';
import { positionsWhere } from '../util/indexing.js';

/**
 * Interlacement.
 *
 * How often a thread changes from face to back decides how firm the cloth is
 * and how closely it can be set. Plain weave changes on every intersection and
 * is the firmest cloth two threads can make; a satin changes once per repeat
 * and can be set far closer for the same yarn.
 */

/** The number of times each end crosses from face to back, round the repeat. */
export function intersectionsPerEnd(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  return columns(drawdown).map((column) => countRuns(column, 1) + countRuns(column, 0));
}

/** The same reading taken across each pick. */
export function intersectionsPerPick(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  return drawdown.map((row) => countRuns(row, 1) + countRuns(row, 0));
}

/**
 * The interlacement factor: intersections counted over the whole repeat as a
 * share of the most a repeat that size could have. Plain weave reads 1.
 */
export function interlacementFactor(drawdown) {
  const { rows, cols } = dimensions(drawdown);
  const warp = sum(intersectionsPerEnd(drawdown));
  const weft = sum(intersectionsPerPick(drawdown));
  const most = rows * cols * 2;
  return roundTo((warp + weft) / most, 4);
}

/** The mean float length implied by the intersection count, in each direction. */
export function averageFloatFromIntersections(drawdown) {
  const { rows, cols } = dimensions(drawdown);
  // A thread that never crosses still counts as one run, so the divisor is
  // never zero and a constant end reports a float of the whole repeat.
  const warp = intersectionsPerEnd(drawdown).map((count) => rows / count);
  const weft = intersectionsPerPick(drawdown).map((count) => cols / count);
  return { warp: roundTo(mean(warp), 4), weft: roundTo(mean(weft), 4) };
}

/**
 * Ashenhurst's intersection count for a repeat: the number of threads in the
 * repeat divided by the number of intersections one thread makes, which is the
 * multiplier his sett rule works from.
 */
export function ashenhurstDivisor(drawdown) {
  const { rows, cols } = dimensions(drawdown);
  const warpIntersections = mean(intersectionsPerEnd(drawdown));
  const weftIntersections = mean(intersectionsPerPick(drawdown));
  return {
    warp: roundTo(rows / warpIntersections, 4),
    weft: roundTo(cols / weftIntersections, 4),
  };
}

/**
 * Ends that are lying on the cloth rather than woven into it. A woven end
 * changes state at least twice round the repeat, once each way; anything under
 * that is a single unbroken run and is not bound in at all.
 */
export function looseEnds(drawdown) {
  return positionsWhere(intersectionsPerEnd(drawdown), (count) => count < 2);
}

/**
 * A coarse reading of how firm a cloth is, for a report that has to say
 * something in one word.
 */
export function firmnessClass(drawdown) {
  const factor = interlacementFactor(drawdown);
  if (factor >= 0.9) return 'firm';
  if (factor >= 0.5) return 'medium';
  if (factor >= 0.25) return 'open';
  return 'loose';
}

/**
 * How much closer one cloth can be set than another for the same yarn, taken
 * as the ratio of their Ashenhurst divisors.
 */
export function settRatio(loose, firm) {
  const a = ashenhurstDivisor(loose);
  const b = ashenhurstDivisor(firm);
  positiveNumber(b.warp, 'firm warp divisor');
  return { warp: roundTo(a.warp / b.warp, 4), weft: roundTo(a.weft / b.weft, 4) };
}
