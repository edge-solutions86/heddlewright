import { positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo, mean } from '../util/numeric.js';
import { dimensions } from '../util/matrix.js';
import { threadsPerInch } from '../yarn/count.js';
import { intersectionsPerEnd, intersectionsPerPick } from './interlacement.js';

/**
 * Sett: how many threads to the inch a cloth can carry.
 *
 * Ashenhurst's rule starts from the number of yarn diameters that fit in an
 * inch and takes off an allowance for the crossings, because every crossing
 * lifts one thread over another and uses up room. The more a weave interlaces,
 * the more room it needs, so plain weave sets the most openly and a satin the
 * closest for the same yarn.
 *
 *   sett = threads in repeat / (threads in repeat + intersections) x diameters
 */

/** The most threads of this yarn that fit in an inch lying side by side. */
export function maximumSett(count, system = 'ne', options = {}) {
  return threadsPerInch(count, system, options);
}

/** Ashenhurst's sett for a cloth, read off its own drawdown. */
export function ashenhurstSett(drawdown, count, { system = 'ne', constant = 28 } = {}) {
  const { rows, cols } = dimensions(drawdown);
  const diameters = threadsPerInch(count, system, { constant });
  const warpIntersections = mean(intersectionsPerEnd(drawdown));
  const weftIntersections = mean(intersectionsPerPick(drawdown));
  return {
    diameters,
    warp: roundTo((cols / (cols + weftIntersections)) * diameters, 2),
    weft: roundTo((rows / (rows + warpIntersections)) * diameters, 2),
  };
}

/**
 * The share of the maximum a weave may be set at, which is the part of the rule
 * that depends only on the interlacement and not on the yarn.
 */
export function settFactor(drawdown) {
  const { rows, cols } = dimensions(drawdown);
  const warpIntersections = mean(intersectionsPerEnd(drawdown));
  const weftIntersections = mean(intersectionsPerPick(drawdown));
  return {
    warp: roundTo(cols / (cols + weftIntersections), 4),
    weft: roundTo(rows / (rows + warpIntersections), 4),
  };
}

/**
 * A working sett, which a designer takes as a share of the theoretical figure.
 * Two thirds is the usual starting point for a cloth meant to drape.
 */
export function workingSett(theoretical, { share = 0.67 } = {}) {
  positiveNumber(theoretical, 'theoretical');
  positiveNumber(share, 'share');
  return roundTo(theoretical * share, 1);
}

/**
 * Peirce's cloth cover factor. Each direction is threads per inch over the
 * square root of the cotton count, and the cloth figure takes off the overlap
 * where warp and weft cover the same ground.
 */
export function coverFactor({ endsPerInch, picksPerInch, warpCount, weftCount }) {
  positiveNumber(endsPerInch, 'endsPerInch');
  positiveNumber(picksPerInch, 'picksPerInch');
  positiveNumber(warpCount, 'warpCount');
  positiveNumber(weftCount, 'weftCount');
  const warp = endsPerInch / Math.sqrt(warpCount);
  const weft = picksPerInch / Math.sqrt(weftCount);
  return {
    warp: roundTo(warp, 2),
    weft: roundTo(weft, 2),
    cloth: roundTo(warp + weft - (warp * weft) / 28, 2),
  };
}

/** A cover factor at or above 28 in one direction means the cloth is jammed. */
export function isJammed(cover) {
  return cover.warp >= 28 || cover.weft >= 28;
}

/** How the ends divide across the cloth width at a given sett. */
export function endsForWidth(widthInches, endsPerInch) {
  positiveNumber(widthInches, 'widthInches');
  positiveNumber(endsPerInch, 'endsPerInch');
  return Math.round(widthInches * endsPerInch);
}

/** The width a given end count occupies at a sett, in inches. */
export function widthForEnds(ends, endsPerInch) {
  positiveInteger(ends, 'ends');
  positiveNumber(endsPerInch, 'endsPerInch');
  return roundTo(ends / endsPerInch, 3);
}
