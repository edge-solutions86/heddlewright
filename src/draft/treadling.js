import { DraftError } from '../util/errors.js';
import { array, integer, positiveInteger } from '../util/check.js';
import { mod } from '../util/numeric.js';
import { period } from '../util/cycle.js';
import { positionsWhere, positionsOf } from '../util/indexing.js';

/**
 * Treadling: which treadles are pressed for each pick, in weaving order.
 *
 * A pick is a list because two feet can be down at once on a countermarch
 * loom, and because a single-entry list keeps every consumer from having to
 * handle both a number and an array.
 */

/** Accept a number, or a list of numbers, per pick and return the list form. */
export function normaliseTreadling(treadling, treadles) {
  array(treadling, 'treadling', { min: 1 });
  positiveInteger(treadles, 'treadles');
  return treadling.map((pick, p) => {
    const entries = Array.isArray(pick) ? pick : [pick];
    const unique = [...new Set(entries)].sort((a, b) => a - b);
    for (const [i, treadle] of unique.entries()) {
      integer(treadle, `treadling[${p}][${i}]`, { min: 1, max: treadles });
    }
    return unique;
  });
}

/** A pick pressing no treadle leaves the weft lying loose across the warp. */
export function assertNoBlankPicks(treadling, treadles) {
  const normalised = normaliseTreadling(treadling, treadles);
  const blanks = positionsWhere(normalised, (pick) => pick.length === 0);
  if (blanks.length > 0) {
    throw new DraftError('a pick presses no treadle', { picks: blanks });
  }
  return normalised;
}

/** Treadle 1, 2, 3 ... and round again, the plain treadling of a straight draw. */
export function straightTreadling(treadles, picks = treadles, { start = 1, direction = 1 } = {}) {
  positiveInteger(treadles, 'treadles');
  positiveInteger(picks, 'picks');
  integer(start, 'start', { min: 1, max: treadles });
  const out = [];
  for (let i = 0; i < picks; i += 1) {
    out.push([mod(start - 1 + direction * i, treadles) + 1]);
  }
  return out;
}

/** Up and back down without repeating the turn, mirroring a point draw. */
export function pointTreadling(treadles, picks) {
  positiveInteger(treadles, 'treadles');
  if (treadles < 2) {
    throw new DraftError('a point treadling needs at least two treadles', { treadles });
  }
  const cycle = 2 * treadles - 2;
  const wanted = picks ?? cycle;
  positiveInteger(wanted, 'picks');
  const out = [];
  for (let i = 0; i < wanted; i += 1) {
    const at = mod(i, cycle);
    out.push([at < treadles ? at + 1 : cycle - at + 1]);
  }
  return out;
}

/**
 * Tromp as writ: treadle in the order the warp was threaded, which is what
 * turns a point threading into a diamond rather than a stripe.
 */
export function trompAsWrit(threading, { shaftToTreadle } = {}) {
  array(threading, 'threading', { min: 1 });
  const map = shaftToTreadle ?? ((shaft) => shaft);
  return threading.map((shaft, i) => {
    const treadle = map(shaft, i);
    integer(treadle, `treadle for end ${i + 1}`, { min: 1 });
    return [treadle];
  });
}

/**
 * An advancing twill moves the start of each block on by `advance` picks, the
 * device behind a rolling twill.
 */
export function advancingTreadling(treadles, picks, { blockSize = 4, advance = 1 } = {}) {
  positiveInteger(treadles, 'treadles');
  positiveInteger(picks, 'picks');
  positiveInteger(blockSize, 'blockSize');
  integer(advance, 'advance');
  const out = [];
  for (let i = 0; i < picks; i += 1) {
    const block = Math.floor(i / blockSize);
    const within = i % blockSize;
    out.push([mod(block * advance + within, treadles) + 1]);
  }
  return out;
}

/** Weave the sequence forwards then backwards, the way a boustrophedon runs. */
export function boustrophedon(treadling, { repeatTurn = false } = {}) {
  array(treadling, 'treadling', { min: 1 });
  const back = [...treadling].reverse();
  return [...treadling, ...(repeatTurn ? back : back.slice(1, -1))];
}

/** How many picks before the treadling comes back to where it started. */
export function treadlingRepeat(treadling, treadles) {
  const normalised = normaliseTreadling(treadling, treadles);
  return period(normalised.map((pick) => pick.join(',')));
}

/** How many picks each treadle carries, 1-based. */
export function treadleUsage(treadling, treadles) {
  const normalised = normaliseTreadling(treadling, treadles);
  const usage = new Array(treadles).fill(0);
  for (const pick of normalised) {
    for (const treadle of pick) usage[treadle - 1] += 1;
  }
  return usage;
}

/** Treadles the treadling never presses, so their cords could be freed. */
export function unusedTreadles(treadling, treadles) {
  return positionsOf(treadleUsage(treadling, treadles), 0);
}

/** Repeat a treadling until it reaches `picks`, cutting mid-repeat if it must. */
export function extendTreadling(treadling, picks) {
  array(treadling, 'treadling', { min: 1 });
  positiveInteger(picks, 'picks');
  const out = [];
  for (let i = 0; i < picks; i += 1) {
    const pick = treadling[i % treadling.length];
    out.push(Array.isArray(pick) ? [...pick] : [pick]);
  }
  return out;
}
