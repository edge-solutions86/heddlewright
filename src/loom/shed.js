import { DraftError } from '../util/errors.js';
import { number, positiveNumber } from '../util/check.js';
import { mod, roundTo } from '../util/numeric.js';

/**
 * Shed timing.
 *
 * Everything on a loom is timed against the crank, which turns once per pick.
 * Beat-up is at nought degrees, the shed crosses somewhere on the way round,
 * and it has to be open wide enough, for long enough, for the shuttle to fly
 * through without touching a warp end. Getting that window wrong does not stop
 * the loom: it just cuts ends, one at a time, all day.
 */

export const BEAT_UP = 0;

/** Put an angle in the 0 to 360 range. */
export function normaliseAngle(angle) {
  number(angle, 'angle');
  return mod(Math.round(angle), 360);
}

/** True when `angle` falls in the arc from `from` to `to`, going forwards. */
export function angleWithin(angle, from, to) {
  const a = normaliseAngle(angle);
  const start = normaliseAngle(from);
  const end = normaliseAngle(to);
  if (start <= end) return a >= start && a <= end;
  return a >= start || a <= end;
}

/**
 * The shed cycle. The shed crosses at `crossAngle`, opens to full a quarter of
 * the way on, holds open for the dwell, and closes again to cross on the next
 * pick.
 */
export function shedCycle({ crossAngle = 300, dwellDegrees = 140, openDegrees = 60 } = {}) {
  const cross = normaliseAngle(crossAngle);
  positiveNumber(dwellDegrees, 'dwellDegrees');
  positiveNumber(openDegrees, 'openDegrees');
  if (dwellDegrees + openDegrees * 2 > 360) {
    throw new DraftError('the shed cannot open, dwell and close inside one turn', {
      dwellDegrees,
      openDegrees,
    });
  }
  const fullyOpen = normaliseAngle(cross + openDegrees);
  const startClosing = normaliseAngle(fullyOpen + dwellDegrees);
  return {
    crossAngle: cross,
    fullyOpen,
    startClosing,
    dwellDegrees,
    openDegrees,
  };
}

/** True when the shed is at its full opening at that crank angle. */
export function isShedOpenAt(cycle, angle) {
  return angleWithin(angle, cycle.fullyOpen, cycle.startClosing);
}

/** The window a shuttle occupies, from launch to arrival in the far box. */
export function shuttleWindow({ pickingAngle = 10, flightDegrees = 120 } = {}) {
  const launch = normaliseAngle(pickingAngle);
  positiveNumber(flightDegrees, 'flightDegrees');
  if (flightDegrees >= 360) {
    throw new DraftError('the shuttle cannot take a whole turn to cross', { flightDegrees });
  }
  return {
    launch,
    arrival: normaliseAngle(launch + flightDegrees),
    flightDegrees,
  };
}

/**
 * Whether the shuttle flies entirely inside the open shed, and by how much it
 * clears at each end. A negative margin is warp being cut.
 */
export function shuttleClearance(cycle, window) {
  const dwell = mod(cycle.startClosing - cycle.fullyOpen, 360);
  // Both margins are measured forwards from the moment the shed is fully open,
  // so the exit margin goes negative exactly when the shuttle is still in the
  // shed as it starts to close. That sign is the whole reading.
  const entryMargin = mod(window.launch - cycle.fullyOpen, 360);
  const exitMargin = roundTo(dwell - entryMargin - window.flightDegrees, 2);
  return {
    clears: entryMargin <= dwell && exitMargin >= 0,
    entryMargin,
    exitMargin,
    dwellDegrees: dwell,
  };
}

/** Refuse a timing that would fly the shuttle into a closing shed. */
export function assertShuttleClears(cycle, window) {
  const clearance = shuttleClearance(cycle, window);
  if (!clearance.clears) {
    throw new DraftError('the shuttle does not clear the shed', {
      flightDegrees: window.flightDegrees,
      dwellDegrees: clearance.dwellDegrees,
      launch: window.launch,
      arrival: window.arrival,
    });
  }
  return clearance;
}

/**
 * How early the shed crosses relative to beat-up. Early shedding closes the
 * warp before the reed arrives and packs the pick harder; late shedding leaves
 * it open and gives a softer cloth.
 */
export function sheddingLead(cycle) {
  const lead = mod(BEAT_UP - cycle.crossAngle, 360);
  let timing = 'level';
  if (lead > 0 && lead <= 180) timing = 'early';
  else if (lead > 180) timing = 'late';
  return { leadDegrees: lead, timing };
}

/** The whole pick as a list of events in crank order, for a timing chart. */
export function timingChart(cycle, window) {
  const events = [
    { at: BEAT_UP, event: 'beat-up' },
    { at: cycle.crossAngle, event: 'shed crosses' },
    { at: cycle.fullyOpen, event: 'shed fully open' },
    { at: window.launch, event: 'shuttle launched' },
    { at: window.arrival, event: 'shuttle boxed' },
    { at: cycle.startClosing, event: 'shed starts closing' },
  ];
  return events.sort((a, b) => a.at - b.at || a.event.localeCompare(b.event));
}
