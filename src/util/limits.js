import { CapacityError, InvalidArgument } from './errors.js';
import { nonEmptyString, number, plainObject } from './check.js';
import { roundTo } from './numeric.js';

/**
 * Capacity checks.
 *
 * Six modules were each writing the same four lines: compare a wanted figure
 * against what a machine has, throw if it is over, and put the shortfall in the
 * detail. They differed only in the names, and the shortfall was worked out
 * three different ways, so two of them reported it the wrong way round.
 *
 * The shortfall is always what is over the limit, never what is under it.
 */

/** How far past a limit a figure sits, which is zero when it is inside. */
export function over(value, limit) {
  number(value, 'value');
  number(limit, 'limit');
  return roundTo(Math.max(0, value - limit), 6);
}

/** How much room is left under a limit, which is zero when it is over. */
export function room(value, limit) {
  number(value, 'value');
  number(limit, 'limit');
  return roundTo(Math.max(0, limit - value), 6);
}

/** Whether a figure fits inside a limit. */
export function fits(value, limit) {
  return over(value, limit) === 0;
}

/**
 * Refuse a figure past a limit. The message says what the machine has rather
 * than what was asked for, because the machine is the fact and the ask is the
 * thing that has to change.
 */
export function assertFits(value, limit, { what, has, detail = {} } = {}) {
  nonEmptyString(what, 'what');
  nonEmptyString(has, 'has');
  plainObject(detail, 'detail');
  if (fits(value, limit)) return value;
  throw new CapacityError(`${has}`, {
    ...detail,
    what,
    wanted: value,
    available: limit,
    over: over(value, limit),
  });
}

/** Refuse a figure below a floor, for the checks that run the other way. */
export function assertAtLeast(value, floor, { what, needs, detail = {} } = {}) {
  nonEmptyString(what, 'what');
  nonEmptyString(needs, 'needs');
  if (value >= floor) return value;
  throw new CapacityError(`${needs}`, {
    ...detail,
    what,
    wanted: value,
    floor,
    short: roundTo(floor - value, 6),
  });
}

/**
 * The same reading for a list of figures against a list of limits, which is
 * what a per-shaft or per-section check wants: every one of them, not the
 * first, and not a total that hides which one failed.
 */
export function shortfalls(values, limits, { label = 'index' } = {}) {
  if (!Array.isArray(values) || !Array.isArray(limits)) {
    throw new InvalidArgument('shortfalls compares two lists', {});
  }
  if (values.length !== limits.length) {
    throw new InvalidArgument('the two lists are different lengths', {
      values: values.length,
      limits: limits.length,
    });
  }
  return values
    .map((value, index) => ({
      [label]: index + 1,
      wanted: value,
      available: limits[index],
      over: over(value, limits[index]),
    }))
    .filter((entry) => entry.over > 0);
}

/** The worst of a list of shortfalls, or null when there are none. */
export function worstShortfall(found) {
  if (!Array.isArray(found) || found.length === 0) return null;
  return found.reduce((best, entry) => (entry.over > best.over ? entry : best), found[0]);
}
