import { CapacityError, InvalidArgument } from '../util/errors.js';
import { array, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { shaftLoad } from '../draft/threading.js';
import { shortfalls, worstShortfall } from '../util/limits.js';

/**
 * Heddles and drawing in.
 *
 * A heddle is a wire with an eye, and one end goes through one of them. The
 * loom carries a fixed number on each shaft, so a draft that wants more on one
 * shaft than the loom has cannot be drawn in however many spare heddles sit on
 * the shaft next to it. That asymmetry is the whole difficulty, and it is why
 * the check is per shaft rather than against a total.
 */

/** How many heddles each shaft needs for a threading. */
export function heddlesNeeded(threading, shafts) {
  return shaftLoad(threading, shafts);
}

/** Where a draft asks for more heddles than the shaft carries. */
export function heddleShortfalls(threading, { shafts, available }) {
  const needed = heddlesNeeded(threading, shafts);
  array(available, 'available', { min: shafts, max: shafts });
  for (const [index, count] of available.entries()) {
    positiveInteger(count, `available[${index}]`);
  }
  return shortfalls(needed, available, { label: 'shaft' }).map((entry) => ({
    shaft: entry.shaft,
    needed: entry.wanted,
    available: entry.available,
    short: entry.over,
  }));
}

/** Refuse a draft the loom has not the heddles for. */
export function assertHeddlesFit(threading, options) {
  const short = heddleShortfalls(threading, options);
  if (short.length > 0) {
    throw new CapacityError('there are not enough heddles on every shaft', {
      shafts: short.length,
      worst: worstShortfall(short.map((entry) => ({ ...entry, over: entry.short }))),
    });
  }
  return threading;
}

/**
 * The spare heddles on each shaft. They are not interchangeable between shafts
 * without moving them by hand, which is an hour's work a shaft, so the spares
 * are reported per shaft rather than added up.
 */
export function spareHeddles(threading, { shafts, available }) {
  const needed = heddlesNeeded(threading, shafts);
  return needed.map((count, index) => ({
    shaft: index + 1,
    spare: available[index] - count,
  }));
}

/**
 * How many heddles to move, and from where, to make a draft fit. Heddles come
 * off the shafts with the most spare, which is what a weaver would do.
 */
export function heddleMoves(threading, options) {
  const short = heddleShortfalls(threading, options);
  const spare = spareHeddles(threading, options)
    .filter((entry) => entry.spare > 0)
    .sort((a, b) => b.spare - a.spare);
  const moves = [];
  for (const need of short) {
    let outstanding = need.short;
    for (const source of spare) {
      if (outstanding === 0) break;
      const taken = Math.min(outstanding, source.spare);
      if (taken === 0) continue;
      moves.push({ from: source.shaft, to: need.shaft, heddles: taken });
      source.spare -= taken;
      outstanding -= taken;
    }
    if (outstanding > 0) {
      throw new CapacityError('the loom has not enough heddles anywhere to make it fit', {
        shaft: need.shaft,
        short: outstanding,
      });
    }
  }
  return moves;
}

/** How long drawing a warp in takes, at a rate an operator can hold. */
export function drawingInHours({ ends, endsPerHour = 400 }) {
  positiveInteger(ends, 'ends');
  positiveNumber(endsPerHour, 'endsPerHour');
  return roundTo(ends / endsPerHour, 3);
}

/**
 * Tying in instead: knotting the new warp to the old one end for end, which is
 * much faster but only works when the new threading is the same as the old.
 */
export function tyingInHours({ ends, endsPerHour = 2000 }) {
  positiveInteger(ends, 'ends');
  positiveNumber(endsPerHour, 'endsPerHour');
  return roundTo(ends / endsPerHour, 3);
}

/** Whether a new warp can be tied in, which needs the same threading. */
export function canTieIn(oldThreading, newThreading) {
  array(oldThreading, 'oldThreading', { min: 1 });
  array(newThreading, 'newThreading', { min: 1 });
  if (oldThreading.length !== newThreading.length) return false;
  return oldThreading.every((shaft, index) => shaft === newThreading[index]);
}

/** The hours a warp change takes, choosing the method that applies. */
export function changeoverHours(oldThreading, newThreading, options = {}) {
  const tie = canTieIn(oldThreading, newThreading);
  const ends = newThreading.length;
  if (ends === 0) {
    throw new InvalidArgument('a warp change needs a warp', {});
  }
  return {
    method: tie ? 'tie in' : 'draw in',
    hours: tie ? tyingInHours({ ends, ...options }) : drawingInHours({ ends, ...options }),
  };
}
