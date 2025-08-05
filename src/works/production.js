import { ConflictError, InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo, sum } from '../util/numeric.js';

/**
 * What the floor actually made.
 *
 * A plan says what should happen; production says what did. The two are kept
 * apart on purpose, because the useful number is the difference between them,
 * and a system that quietly overwrites the plan with the outturn can no longer
 * tell anybody why a delivery slipped.
 */

/** Book a shift's output against a loom and an order. */
export function recordOutturn(entry, index = 0) {
  plainObject(entry, `outturn[${index}]`);
  return {
    order: nonEmptyString(entry.order, `outturn[${index}].order`),
    loom: nonEmptyString(entry.loom, `outturn[${index}].loom`),
    shift: nonEmptyString(entry.shift ?? 'day', `outturn[${index}].shift`),
    metres: positiveNumber(entry.metres, `outturn[${index}].metres`),
    hours: positiveNumber(entry.hours, `outturn[${index}].hours`),
    stops: number(entry.stops ?? 0, `outturn[${index}].stops`, { min: 0 }),
    at: entry.at ?? null,
  };
}

/** Book a whole book of outturn at once. */
export function recordBook(entries) {
  array(entries, 'entries');
  return entries.map((entry, index) => recordOutturn(entry, index));
}

/** What one order has produced so far. */
export function producedFor(book, order) {
  nonEmptyString(order, 'order');
  const mine = book.filter((entry) => entry.order === order);
  return {
    order,
    entries: mine.length,
    metres: roundTo(sum(mine.map((entry) => entry.metres)), 3),
    hours: roundTo(sum(mine.map((entry) => entry.hours)), 3),
    stops: sum(mine.map((entry) => entry.stops)),
  };
}

/** How an order stands against what was ordered. */
export function orderProgress(book, order, orderedMetres) {
  positiveNumber(orderedMetres, 'orderedMetres');
  const made = producedFor(book, order);
  const remaining = Math.max(0, roundTo(orderedMetres - made.metres, 3));
  return {
    ...made,
    orderedMetres,
    remainingMetres: remaining,
    complete: made.metres >= orderedMetres,
    share: roundTo(Math.min(1, made.metres / orderedMetres), 4),
    overrunMetres: roundTo(Math.max(0, made.metres - orderedMetres), 3),
  };
}

/** Metres an hour, by loom, best first. */
export function loomRates(book) {
  array(book, 'book');
  const totals = new Map();
  for (const entry of book) {
    const running = totals.get(entry.loom) ?? { loom: entry.loom, metres: 0, hours: 0, stops: 0 };
    running.metres = roundTo(running.metres + entry.metres, 3);
    running.hours = roundTo(running.hours + entry.hours, 3);
    running.stops += entry.stops;
    totals.set(entry.loom, running);
  }
  return [...totals.values()]
    .map((entry) => ({
      ...entry,
      metresPerHour: roundTo(entry.metres / entry.hours, 3),
      stopsPerHour: roundTo(entry.stops / entry.hours, 3),
    }))
    .sort((a, b) => b.metresPerHour - a.metresPerHour || a.loom.localeCompare(b.loom));
}

/** The same reading by shift, which is where a night crew shows up. */
export function shiftRates(book) {
  array(book, 'book');
  const totals = new Map();
  for (const entry of book) {
    const running = totals.get(entry.shift) ?? { shift: entry.shift, metres: 0, hours: 0 };
    running.metres = roundTo(running.metres + entry.metres, 3);
    running.hours = roundTo(running.hours + entry.hours, 3);
    totals.set(entry.shift, running);
  }
  return [...totals.values()]
    .map((entry) => ({ ...entry, metresPerHour: roundTo(entry.metres / entry.hours, 3) }))
    .sort((a, b) => b.metresPerHour - a.metresPerHour || a.shift.localeCompare(b.shift));
}

/**
 * Production against plan. A plan entry that produced nothing is reported as
 * such rather than dropped, because an order nobody started is exactly the one
 * a report has to name.
 */
export function againstPlan(plan, book, orderedMetres) {
  plainObject(plan, 'plan');
  plainObject(orderedMetres, 'orderedMetres');
  return plan.placed.map((entry) => {
    const ordered = orderedMetres[entry.order];
    if (ordered === undefined) {
      throw new InvalidArgument(`no ordered length for ${entry.order}`, { order: entry.order });
    }
    const progress = orderProgress(book, entry.order, ordered);
    return {
      order: entry.order,
      loom: entry.loom,
      plannedHours: entry.hours,
      actualHours: progress.hours,
      metres: progress.metres,
      orderedMetres: ordered,
      share: progress.share,
      hoursOver: roundTo(progress.hours - entry.hours, 3),
    };
  });
}

/** Refuse an outturn booked against a loom the plan never gave it. */
export function assertBookMatchesPlan(plan, book) {
  const planned = new Map(plan.placed.map((entry) => [entry.order, entry.loom]));
  for (const entry of book) {
    const loom = planned.get(entry.order);
    if (loom === undefined) {
      throw new ConflictError(`${entry.order} was never planned`, { order: entry.order });
    }
    if (loom !== entry.loom) {
      throw new ConflictError(`${entry.order} was planned for ${loom}`, {
        order: entry.order,
        planned: loom,
        booked: entry.loom,
      });
    }
  }
  return book;
}
