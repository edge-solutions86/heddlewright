import { InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo, sum } from '../util/numeric.js';

/**
 * Loom efficiency and where it goes.
 *
 * A loom that never stopped would weave its quoted speed all day. Every stop
 * costs the time it takes plus the time before somebody reaches the loom, and
 * the two are not the same: a shed can lose more time waiting than mending.
 * Splitting the loss by cause is what tells a manager whether to buy better
 * yarn or hire another weaver.
 */

export const STOP_CAUSES = [
  'warp break',
  'weft break',
  'shuttle change',
  'beam change',
  'mechanical',
  'no weaver',
  'quality stop',
];

/** Check a stop record and hand back a normalised copy. */
export function normaliseStop(stop, index = 0) {
  plainObject(stop, `stops[${index}]`);
  const cause = nonEmptyString(stop.cause, `stops[${index}].cause`);
  const count = number(stop.count ?? 1, `stops[${index}].count`, { min: 0 });
  const minutesEach = number(stop.minutesEach ?? 0, `stops[${index}].minutesEach`, { min: 0 });
  const waitEach = number(stop.waitEach ?? 0, `stops[${index}].waitEach`, { min: 0 });
  return { cause, count, minutesEach, waitEach, minutesLost: count * (minutesEach + waitEach) };
}

/** The minutes a run of stops costs, split into mending and waiting. */
export function lostMinutes(stops) {
  array(stops, 'stops');
  const records = stops.map((stop, index) => normaliseStop(stop, index));
  return {
    mending: roundTo(sum(records.map((record) => record.count * record.minutesEach)), 3),
    waiting: roundTo(sum(records.map((record) => record.count * record.waitEach)), 3),
    total: roundTo(sum(records.map((record) => record.minutesLost)), 3),
  };
}

/** Efficiency over a shift, as a fraction between nothing and one. */
export function efficiency({ shiftMinutes, stops }) {
  positiveNumber(shiftMinutes, 'shiftMinutes');
  const lost = lostMinutes(stops).total;
  if (lost > shiftMinutes) {
    throw new InvalidArgument('the stops add up to more than the shift', {
      shiftMinutes,
      lostMinutes: lost,
    });
  }
  return roundTo((shiftMinutes - lost) / shiftMinutes, 4);
}

/** The losses broken down by cause, worst first. */
export function lossByCause(stops) {
  array(stops, 'stops');
  const totals = new Map();
  for (const [index, stop] of stops.entries()) {
    const record = normaliseStop(stop, index);
    const running = totals.get(record.cause) ?? { cause: record.cause, count: 0, minutesLost: 0 };
    running.count += record.count;
    running.minutesLost = roundTo(running.minutesLost + record.minutesLost, 3);
    totals.set(record.cause, running);
  }
  return [...totals.values()].sort(
    (a, b) => b.minutesLost - a.minutesLost || a.cause.localeCompare(b.cause),
  );
}

/**
 * Breaks per hundred thousand picks, the figure a spinner is judged on. A
 * warp break rate is quoted per hundred thousand picks so that looms of
 * different speeds and widths can be compared.
 */
export function breakRate({ breaks, picks, per = 100000 }) {
  number(breaks, 'breaks', { min: 0 });
  positiveNumber(picks, 'picks');
  positiveNumber(per, 'per');
  return roundTo((breaks / picks) * per, 3);
}

/**
 * The efficiency a stop rate implies, before anything is measured. Each stop
 * takes its own time plus the wait, and the rest of the hour is weaving.
 */
export function efficiencyFromStopRate({ stopsPerHour, minutesPerStop }) {
  number(stopsPerHour, 'stopsPerHour', { min: 0 });
  positiveNumber(minutesPerStop, 'minutesPerStop');
  const lost = stopsPerHour * minutesPerStop;
  if (lost >= 60) return 0;
  return roundTo((60 - lost) / 60, 4);
}

/** What one more or one fewer stop an hour is worth, in efficiency points. */
export function stopSensitivity({ minutesPerStop }) {
  positiveNumber(minutesPerStop, 'minutesPerStop');
  return roundTo(minutesPerStop / 60, 4);
}

/** A full report, ready to print for a shift handover. */
export function shiftReport({ shiftMinutes, stops, picksMinute }) {
  const lost = lostMinutes(stops);
  const running = efficiency({ shiftMinutes, stops });
  return {
    shiftMinutes,
    lostMinutes: lost,
    efficiency: running,
    efficiencyPercent: roundTo(running * 100, 2),
    picksWoven: picksMinute === undefined ? null : Math.round(shiftMinutes * running * picksMinute),
    byCause: lossByCause(stops),
  };
}
