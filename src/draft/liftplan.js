import { CapacityError } from '../util/errors.js';
import { array, integer, positiveInteger } from '../util/check.js';
import { createMatrix, assertMatrix, dimensions } from '../util/matrix.js';
import { setPositions, labelDistinct } from '../util/indexing.js';
import { normaliseTieup } from './tieup.js';
import { normaliseTreadling } from './treadling.js';

/**
 * A liftplan is one row per pick and one column per shaft, which is what a
 * dobby actually reads. A treadle loom draft carries a tie-up and a treadling
 * instead, and the two forms convert both ways: forwards always, backwards
 * only when the number of distinct lifts fits the treadles available.
 */

/** Build the liftplan a tie-up and treadling produce on a rising shed. */
export function liftplanFromTreadling(tieup, treadling, { shafts, treadles }) {
  positiveInteger(shafts, 'shafts');
  positiveInteger(treadles, 'treadles');
  const ties = normaliseTieup(tieup, shafts);
  if (ties.length !== treadles) {
    throw new CapacityError('the tie-up does not cover every treadle', {
      treadles,
      tied: ties.length,
    });
  }
  const picks = normaliseTreadling(treadling, treadles);
  const plan = createMatrix(picks.length, shafts);
  for (const [p, pick] of picks.entries()) {
    for (const treadle of pick) {
      for (const shaft of ties[treadle - 1]) plan[p][shaft - 1] = 1;
    }
  }
  return plan;
}

/**
 * Split a liftplan back into a tie-up and a treadling. Each distinct row
 * becomes one treadle, in the order the rows are first seen, so a plan using
 * more distinct lifts than the loom has treadles is refused rather than
 * silently merged.
 */
export function treadlingFromLiftplan(liftplan, { treadles } = {}) {
  assertMatrix(liftplan, 'liftplan');
  const { labels, representatives, count } = labelDistinct(liftplan, (row) => row.join(''));
  if (treadles !== undefined && count > treadles) {
    throw new CapacityError('the liftplan needs more treadles than the loom has', {
      needed: count,
      treadles,
    });
  }
  return {
    tieup: representatives.map((row) => setPositions(row)),
    treadling: labels.map((label) => [label]),
    treadles: count,
  };
}

/** A liftplan straight from a shaft-per-pick list, for a direct-tie dobby. */
export function liftplanFromShaftLists(shaftLists, shafts) {
  array(shaftLists, 'shaftLists', { min: 1 });
  positiveInteger(shafts, 'shafts');
  const plan = createMatrix(shaftLists.length, shafts);
  for (const [p, list] of shaftLists.entries()) {
    const entries = Array.isArray(list) ? list : [list];
    for (const [i, shaft] of entries.entries()) {
      integer(shaft, `shaftLists[${p}][${i}]`, { min: 1, max: shafts });
      plan[p][shaft - 1] = 1;
    }
  }
  return plan;
}

/** The shafts lifted on each pick, as 1-based lists. */
export function shaftListsFromLiftplan(liftplan) {
  assertMatrix(liftplan, 'liftplan');
  return liftplan.map((row) => setPositions(row));
}

/** How many shafts each pick lifts, which sizes the dobby cylinder load. */
export function liftLoad(liftplan) {
  assertMatrix(liftplan, 'liftplan');
  return liftplan.map((row) => row.reduce((total, cell) => total + cell, 0));
}

/** Picks that lift nothing, or lift every shaft, so no shed opens. */
export function shedlessPicks(liftplan) {
  assertMatrix(liftplan, 'liftplan');
  const { cols } = dimensions(liftplan);
  const out = [];
  for (const [p, load] of liftLoad(liftplan).entries()) {
    if (load === 0) out.push({ pick: p + 1, reason: 'nothing lifted' });
    else if (load === cols) out.push({ pick: p + 1, reason: 'everything lifted' });
  }
  return out;
}

/** The distinct lifts a plan uses, which is the treadle count it needs. */
export function distinctLifts(liftplan) {
  assertMatrix(liftplan, 'liftplan');
  return new Set(liftplan.map((row) => row.join(''))).size;
}

/** Invert every lift, which is the same cloth woven face down. */
export function invertLiftplan(liftplan) {
  assertMatrix(liftplan, 'liftplan');
  return liftplan.map((row) => row.map((cell) => (cell === 1 ? 0 : 1)));
}
