import { InvalidArgument } from './errors.js';
import { array, integer, nonEmptyString, number } from './check.js';

/**
 * Dates, kept as plain day strings.
 *
 * A shed works in days and shifts, not in instants, and a Date carries a time
 * zone that turns a delivery date into the day before it somewhere. So a date
 * here is the ten characters of a calendar day and the arithmetic is done on
 * whole days, which cannot drift.
 */

const DAY_MS = 24 * 60 * 60 * 1000;
const PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Check a day string and hand it back. */
export function assertDay(day, field = 'day') {
  nonEmptyString(day, field);
  if (!PATTERN.test(day)) {
    throw new InvalidArgument(`${field} must be written as a calendar day`, { field, got: day });
  }
  const parsed = new Date(`${day}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== day) {
    throw new InvalidArgument(`${field} is not a real day`, { field, got: day });
  }
  return day;
}

/** The day a number of days after another. */
export function addDays(day, count) {
  assertDay(day);
  integer(count, 'count');
  const at = new Date(`${day}T00:00:00Z`).getTime() + count * DAY_MS;
  return new Date(at).toISOString().slice(0, 10);
}

/** How many days from one to another, which is negative when it is backwards. */
export function daysBetween(from, to) {
  assertDay(from, 'from');
  assertDay(to, 'to');
  const start = new Date(`${from}T00:00:00Z`).getTime();
  const end = new Date(`${to}T00:00:00Z`).getTime();
  return Math.round((end - start) / DAY_MS);
}

/** Which day of the week a date falls on, Monday first. */
export function weekday(day) {
  assertDay(day);
  const index = new Date(`${day}T00:00:00Z`).getUTCDay();
  return ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][index];
}

/** Whether a day is one the shed works, given a week and a holiday list. */
export function isWorkingDay(day, { week = WORKING_WEEK, holidays = [] } = {}) {
  array(week, 'week', { min: 1 });
  array(holidays, 'holidays');
  if (holidays.includes(assertDay(day))) return false;
  return week.includes(weekday(day));
}

/** The days a shed normally works. */
export const WORKING_WEEK = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];

/** The next working day on or after a date. */
export function nextWorkingDay(day, options = {}) {
  let at = assertDay(day);
  for (let guard = 0; guard < 30; guard += 1) {
    if (isWorkingDay(at, options)) return at;
    at = addDays(at, 1);
  }
  throw new InvalidArgument('no working day inside a month of that date', { day });
}

/** The working days between two dates, counting the first but not the last. */
export function workingDaysBetween(from, to, options = {}) {
  const span = daysBetween(from, to);
  if (span < 0) {
    throw new InvalidArgument('the second day comes before the first', { from, to });
  }
  let count = 0;
  for (let step = 0; step < span; step += 1) {
    if (isWorkingDay(addDays(from, step), options)) count += 1;
  }
  return count;
}

/** The day a run of working days lands on. */
export function addWorkingDays(day, count, options = {}) {
  integer(count, 'count', { min: 0 });
  let at = nextWorkingDay(day, options);
  for (let step = 0; step < count; step += 1) {
    at = nextWorkingDay(addDays(at, 1), options);
  }
  return at;
}

/**
 * When a job finishes, from the hours it takes and the hours a working day
 * holds. It is the calculation behind every delivery date a shed quotes.
 */
export function finishDay(start, { hours, hoursPerDay = 16, ...options }) {
  number(hours, 'hours', { min: 0 });
  number(hoursPerDay, 'hoursPerDay', { min: 0.1 });
  const days = Math.max(0, Math.ceil(hours / hoursPerDay) - 1);
  return addWorkingDays(start, days, options);
}
