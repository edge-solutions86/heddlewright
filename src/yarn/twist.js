import { InvalidArgument } from '../util/errors.js';
import { number, oneOf, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { toTex } from './count.js';

/**
 * Twist.
 *
 * Twist is what holds a spun yarn together, and how much of it a yarn needs
 * depends on how thick the yarn is: a coarse yarn needs fewer turns an inch
 * than a fine one to be equally firm. The trade handles that with a twist
 * multiplier, which is turns per inch over the square root of the cotton count,
 * and quotes yarns by the multiplier rather than by the turns.
 *
 * Direction matters as much as amount. A warp and a weft twisted the same way
 * lie together and the cloth reads flat; twisted opposite ways they stand apart
 * and the twill line shows.
 */

export const DIRECTIONS = ['S', 'Z'];

/** The twist multiplier a yarn is running at. */
export function twistMultiplier({ turnsPerInch, count, system = 'ne' }) {
  positiveNumber(turnsPerInch, 'turnsPerInch');
  const ne = 590.5414 / toTex(count, system);
  return roundTo(turnsPerInch / Math.sqrt(ne), 4);
}

/** The turns an inch a wanted multiplier asks for. */
export function turnsForMultiplier({ multiplier, count, system = 'ne' }) {
  positiveNumber(multiplier, 'multiplier');
  const ne = 590.5414 / toTex(count, system);
  return roundTo(multiplier * Math.sqrt(ne), 2);
}

/** The same in metric: turns a metre over the square root of the metric count. */
export function metricTwistFactor({ turnsPerMetre, count, system = 'tex' }) {
  positiveNumber(turnsPerMetre, 'turnsPerMetre');
  const nm = 1000 / toTex(count, system);
  return roundTo(turnsPerMetre / Math.sqrt(nm), 4);
}

/** Turns an inch to turns a metre and back. */
export function turnsPerMetre(turnsPerInch) {
  positiveNumber(turnsPerInch, 'turnsPerInch');
  return roundTo(turnsPerInch / 0.0254, 2);
}

export function turnsPerInch(metreTurns) {
  positiveNumber(metreTurns, 'turnsPerMetre');
  return roundTo(metreTurns * 0.0254, 4);
}

/** The usual working multipliers, by what the yarn is for. */
export function multiplierFor(purpose) {
  const table = new Map([
    ['weft', 3.0],
    ['warp', 4.0],
    ['hosiery', 2.5],
    ['voile', 5.5],
  ]);
  const found = table.get(purpose);
  if (found === undefined) {
    throw new InvalidArgument(`no working multiplier for "${purpose}"`, {
      purpose,
      known: [...table.keys()],
    });
  }
  return found;
}

/** Whether a yarn is soft, normal or hard twisted for what it is meant to do. */
export function twistClass({ turnsPerInch: turns, count, system = 'ne', purpose = 'warp' }) {
  const running = twistMultiplier({ turnsPerInch: turns, count, system });
  const wanted = multiplierFor(purpose);
  const ratio = roundTo(running / wanted, 3);
  let reading = 'normal';
  if (ratio < 0.85) reading = 'soft';
  else if (ratio > 1.15) reading = 'hard';
  return { multiplier: running, wanted, ratio, reading };
}

/**
 * The resultant direction of a folded yarn. Folding twist runs against the
 * single twist, so a Z spun single folded with S twist gives an S resultant.
 */
export function foldedDirection(singleDirection) {
  oneOf(singleDirection, 'singleDirection', DIRECTIONS);
  return singleDirection === 'Z' ? 'S' : 'Z';
}

/**
 * How much twist a folded yarn should carry, as a share of the single twist.
 * Too little and the fold unwinds; too much and the yarn snarls.
 */
export function foldingTurns({ singleTurnsPerInch, share = 0.7 }) {
  positiveNumber(singleTurnsPerInch, 'singleTurnsPerInch');
  number(share, 'share', { min: 0.1, max: 1.5 });
  return roundTo(singleTurnsPerInch * share, 2);
}

/**
 * Whether a warp and a weft twisted this way will show the twill line or hide
 * it. Opposite twist stands the two systems apart and the line shows.
 */
export function twillContrast(warpDirection, weftDirection, { twillHand = 'Z' } = {}) {
  oneOf(warpDirection, 'warpDirection', DIRECTIONS);
  oneOf(weftDirection, 'weftDirection', DIRECTIONS);
  oneOf(twillHand, 'twillHand', DIRECTIONS);
  if (warpDirection !== weftDirection) return 'pronounced';
  return warpDirection === twillHand ? 'flattened' : 'pronounced';
}
