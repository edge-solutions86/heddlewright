import { DraftError } from '../util/errors.js';
import { array, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo, sum } from '../util/numeric.js';

/**
 * The reed and the denting plan.
 *
 * A reed is a comb of dents, counted per inch, and the warp is sleyed through
 * it so many ends to a dent. The reed sets the width and spaces the warp, and
 * an uneven or over-full denting shows in the cloth as a reed mark long after
 * the loom has forgotten about it.
 */

/** Reeds a shed is likely to have on the rack, in dents per inch. */
export const STOCK_REEDS = [5, 6, 8, 10, 12, 14, 15, 16, 18, 20, 24, 30];

/** The reed a sett needs, given how many ends go in each dent. */
export function reedFor(endsPerInch, endsPerDent = 2) {
  positiveNumber(endsPerInch, 'endsPerInch');
  positiveInteger(endsPerDent, 'endsPerDent');
  return roundTo(endsPerInch / endsPerDent, 4);
}

/** The stock reed closest to a wanted count, and how far off it is. */
export function nearestReed(dentsPerInch, { reeds = STOCK_REEDS } = {}) {
  positiveNumber(dentsPerInch, 'dentsPerInch');
  array(reeds, 'reeds', { min: 1 });
  let best = reeds[0];
  for (const reed of reeds) {
    if (Math.abs(reed - dentsPerInch) < Math.abs(best - dentsPerInch)) best = reed;
  }
  return { reed: best, off: roundTo(best - dentsPerInch, 4) };
}

/**
 * Every stock reed that reaches a sett with a whole number of ends per dent,
 * which is what a weaver actually wants to choose between.
 */
export function reedOptions(endsPerInch, { reeds = STOCK_REEDS, maxEndsPerDent = 4 } = {}) {
  positiveNumber(endsPerInch, 'endsPerInch');
  positiveInteger(maxEndsPerDent, 'maxEndsPerDent');
  const out = [];
  for (const reed of reeds) {
    for (let perDent = 1; perDent <= maxEndsPerDent; perDent += 1) {
      if (roundTo(reed * perDent, 6) === roundTo(endsPerInch, 6)) {
        out.push({ reed, endsPerDent: perDent });
      }
    }
  }
  return out;
}

/**
 * Spread `ends` across dents following a repeating pattern, so a sett of two
 * and a half to the dent is written as [3, 2] rather than as a fraction.
 */
export function dentingPlan(ends, pattern = [2]) {
  positiveInteger(ends, 'ends');
  array(pattern, 'pattern', { min: 1 });
  for (const [i, group] of pattern.entries()) positiveInteger(group, `pattern[${i}]`);
  const dents = [];
  let placed = 0;
  let step = 0;
  while (placed < ends) {
    const wanted = pattern[step % pattern.length];
    const take = Math.min(wanted, ends - placed);
    dents.push(take);
    placed += take;
    step += 1;
  }
  return dents;
}

/** The average ends per dent a repeating pattern gives. */
export function averageEndsPerDent(pattern) {
  array(pattern, 'pattern', { min: 1 });
  return roundTo(sum(pattern) / pattern.length, 4);
}

/** The width in the reed, in inches, for a denting plan at a given reed count. */
export function reedWidth(dents, dentsPerInch) {
  positiveInteger(dents, 'dents');
  positiveNumber(dentsPerInch, 'dentsPerInch');
  return roundTo(dents / dentsPerInch, 3);
}

/** The full sley: which dent each 1-based end falls in. */
export function sleyOrder(ends, pattern = [2]) {
  const dents = dentingPlan(ends, pattern);
  const order = [];
  for (const [index, group] of dents.entries()) {
    for (let n = 0; n < group; n += 1) order.push(index + 1);
  }
  return order;
}

/**
 * Warn about a denting that will show. Too many ends in a dent crowds them into
 * a stripe, and a plan whose last dent is short leaves a thin edge.
 */
export function dentingWarnings(dents, { maxEndsPerDent = 4 } = {}) {
  array(dents, 'dents', { min: 1 });
  const warnings = [];
  const crowded = dents
    .map((group, index) => (group > maxEndsPerDent ? index + 1 : 0))
    .filter((dent) => dent !== 0);
  if (crowded.length > 0) {
    warnings.push({
      code: 'crowded_dent',
      message: `${crowded.length} dents carry more than ${maxEndsPerDent} ends`,
      detail: { dents: crowded, maxEndsPerDent },
    });
  }
  const first = dents[0];
  const last = dents[dents.length - 1];
  if (dents.length > 1 && last < first) {
    warnings.push({
      code: 'short_last_dent',
      message: 'the last dent is not full, so the selvedge will pull in',
      detail: { first, last },
    });
  }
  return warnings;
}

/** Refuse a denting the reed cannot physically hold. */
export function assertDentingFits(dents, dentsAvailable) {
  array(dents, 'dents', { min: 1 });
  positiveInteger(dentsAvailable, 'dentsAvailable');
  if (dents.length > dentsAvailable) {
    throw new DraftError('the denting needs more dents than the reed has', {
      needed: dents.length,
      dentsAvailable,
    });
  }
  return dents;
}
