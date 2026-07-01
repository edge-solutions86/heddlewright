import { ConflictError, InvalidArgument, NotFound } from '../util/errors.js';
import { array, nonEmptyString, number, positiveNumber } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';
import { tally } from '../util/group.js';

/**
 * Yarn stock.
 *
 * A yarn store has three numbers for every line and they are never the same:
 * what is on the shelf, what is promised to jobs not yet started, and what is
 * left to promise. A store that keeps only the first will happily promise the
 * same cone to two warps, and nobody finds out until the second one is beamed.
 */

/** Open a stock line. */
export function createLine({ code, description = '', tex, kilograms }) {
  nonEmptyString(code, 'code');
  positiveNumber(tex, 'tex');
  number(kilograms, 'kilograms', { min: 0 });
  return Object.freeze({
    code,
    description,
    tex,
    kilograms: roundTo(kilograms, 4),
    reservations: [],
  });
}

/** What a line has promised out. */
export function reserved(line) {
  return roundTo(
    sumBy(line.reservations, (entry) => entry.kilograms, 'reservations'),
    4,
  );
}

/** What is left to promise. */
export function available(line) {
  return roundTo(line.kilograms - reserved(line), 4);
}

/** Promise stock to a job, which cannot promise what is not there. */
export function reserve(line, { order, kilograms }) {
  nonEmptyString(order, 'order');
  positiveNumber(kilograms, 'kilograms');
  if (kilograms > available(line)) {
    throw new ConflictError(`${line.code} has only ${available(line)} kg left to promise`, {
      code: line.code,
      wanted: kilograms,
      available: available(line),
    });
  }
  if (line.reservations.some((entry) => entry.order === order)) {
    throw new ConflictError(`${order} already has stock promised on ${line.code}`, {
      code: line.code,
      order,
    });
  }
  return Object.freeze({
    ...line,
    reservations: [...line.reservations, { order, kilograms: roundTo(kilograms, 4) }],
  });
}

/** Release a promise, which a cancelled order does. */
export function release(line, { order }) {
  nonEmptyString(order, 'order');
  if (!line.reservations.some((entry) => entry.order === order)) {
    throw new NotFound(`${order} has nothing promised on ${line.code}`, {
      code: line.code,
      order,
    });
  }
  return Object.freeze({
    ...line,
    reservations: line.reservations.filter((entry) => entry.order !== order),
  });
}

/**
 * Take stock off the shelf against a promise. The promise goes with it, because
 * yarn that has been issued is no longer promised: it is gone.
 */
export function issue(line, { order, kilograms }) {
  positiveNumber(kilograms, 'kilograms');
  const promise = line.reservations.find((entry) => entry.order === order);
  if (promise === undefined) {
    throw new NotFound(`${order} has nothing promised on ${line.code}`, { order });
  }
  if (kilograms > promise.kilograms) {
    throw new ConflictError('that is more than the order was promised', {
      order,
      promised: promise.kilograms,
      wanted: kilograms,
    });
  }
  const left = roundTo(promise.kilograms - kilograms, 4);
  const reservations = line.reservations
    .map((entry) => (entry.order === order ? { ...entry, kilograms: left } : entry))
    .filter((entry) => entry.kilograms > 0);
  return Object.freeze({
    ...line,
    kilograms: roundTo(line.kilograms - kilograms, 4),
    reservations,
  });
}

/** Put stock in, which a delivery does. */
export function receive(line, { kilograms }) {
  positiveNumber(kilograms, 'kilograms');
  return Object.freeze({ ...line, kilograms: roundTo(line.kilograms + kilograms, 4) });
}

/** Find a line by code. */
export function lineByCode(lines, code) {
  array(lines, 'lines');
  const found = lines.find((line) => line.code === code);
  if (found === undefined) {
    throw new NotFound(`no stock line called "${code}"`, { code });
  }
  return found;
}

/** Lines with nothing left to promise, which is what a buyer wants to see. */
export function fullyPromised(lines) {
  array(lines, 'lines');
  return lines.filter((line) => available(line) <= 0).map((line) => line.code);
}

/** What each order has promised across the whole store. */
export function byOrder(lines) {
  array(lines, 'lines');
  const claims = lines.flatMap((line) =>
    line.reservations.map((entry) => ({ ...entry, code: line.code })),
  );
  return tally(claims, {
    key: (claim) => claim.order,
    label: 'order',
    fields: { kilograms: (claim) => claim.kilograms },
    sortBy: 'kilograms',
  });
}

/** Refuse a store whose promises add up to more than it holds. */
export function assertStoreSound(lines) {
  array(lines, 'lines');
  const over = lines.filter((line) => reserved(line) > line.kilograms);
  if (over.length > 0) {
    throw new InvalidArgument('some lines have promised more than they hold', {
      lines: over.map((line) => line.code),
    });
  }
  return lines;
}
