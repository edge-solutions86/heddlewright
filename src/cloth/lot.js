import { ConflictError, InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo, meanBy } from '../util/numeric.js';
import { groupBy } from '../util/group.js';

/**
 * Shade lots.
 *
 * Two pieces dyed in different vessels are never quite the same colour, and a
 * garment cut across the join shows it. So pieces are gathered into lots by how
 * far apart their shade readings are, and a lot is only usable if every piece
 * in it sits inside the tolerance of every other, not merely of the first one.
 * That is the part a simple grouping gets wrong.
 */

/** Book a shade reading against a piece, on the three colour axes. */
export function readShade(entry) {
  plainObject(entry, 'reading');
  return {
    reference: nonEmptyString(entry.reference, 'reference'),
    lightness: number(entry.lightness, 'lightness'),
    red: number(entry.red, 'red'),
    yellow: number(entry.yellow, 'yellow'),
  };
}

/** How far apart two readings are, as a straight distance in colour space. */
export function shadeDistance(left, right) {
  const dl = left.lightness - right.lightness;
  const da = left.red - right.red;
  const db = left.yellow - right.yellow;
  return roundTo(Math.sqrt(dl * dl + da * da + db * db), 4);
}

/** Every pair in a lot that sits further apart than the tolerance. */
export function outliers(readings, { tolerance }) {
  array(readings, 'readings', { min: 1 });
  positiveNumber(tolerance, 'tolerance');
  const found = [];
  for (let i = 0; i < readings.length; i += 1) {
    for (let j = i + 1; j < readings.length; j += 1) {
      const distance = shadeDistance(readings[i], readings[j]);
      if (distance > tolerance) {
        found.push({
          left: readings[i].reference,
          right: readings[j].reference,
          distance,
        });
      }
    }
  }
  return found.sort((a, b) => b.distance - a.distance);
}

/** A lot holds together when no two pieces in it are further apart than the tolerance. */
export function lotHolds(readings, options) {
  return outliers(readings, options).length === 0;
}

/**
 * Gather readings into lots. A piece joins a lot only when it sits inside the
 * tolerance of every piece already in it, so a lot cannot drift across the
 * whole colour space one small step at a time.
 */
export function buildLots(readings, { tolerance }) {
  array(readings, 'readings', { min: 1 });
  positiveNumber(tolerance, 'tolerance');
  const lots = [];
  for (const reading of readings) {
    const home = lots.find((lot) =>
      lot.every((member) => shadeDistance(member, reading) <= tolerance),
    );
    if (home === undefined) {
      lots.push([reading]);
      continue;
    }
    home.push(reading);
  }
  return lots.map((members, index) => ({
    lot: index + 1,
    pieces: members.map((member) => member.reference),
    centre: lotCentre(members),
    spread: lotSpread(members),
  }));
}

/** The middle of a lot, which is what a shade card records. */
export function lotCentre(readings) {
  array(readings, 'readings', { min: 1 });
  const mean = (read) => meanBy(readings, read, 'readings');
  return {
    lightness: roundTo(
      mean((entry) => entry.lightness),
      4,
    ),
    red: roundTo(
      mean((entry) => entry.red),
      4,
    ),
    yellow: roundTo(
      mean((entry) => entry.yellow),
      4,
    ),
  };
}

/** The widest gap inside a lot, which is what a customer would see. */
export function lotSpread(readings) {
  array(readings, 'readings', { min: 1 });
  let widest = 0;
  for (let i = 0; i < readings.length; i += 1) {
    for (let j = i + 1; j < readings.length; j += 1) {
      widest = Math.max(widest, shadeDistance(readings[i], readings[j]));
    }
  }
  return roundTo(widest, 4);
}

/** Refuse a lot that would not hold together. */
export function assertLotHolds(readings, options) {
  const found = outliers(readings, options);
  if (found.length > 0) {
    throw new ConflictError('those pieces are not one shade lot', {
      pairs: found.length,
      widest: found[0],
    });
  }
  return readings;
}

/** Which lot a piece was put in, by reference. */
export function lotOf(lots, reference) {
  nonEmptyString(reference, 'reference');
  const found = lots.find((lot) => lot.pieces.includes(reference));
  if (found === undefined) {
    throw new InvalidArgument(`no lot holds ${reference}`, { reference });
  }
  return found.lot;
}

/** How many lots a delivery would need at a given tolerance. */
export function lotCount(readings, options) {
  return buildLots(readings, options).length;
}

/** Pieces grouped by the lot they were dyed in, rather than by shade. */
export function byDyeLot(pieces) {
  return [...groupBy(pieces, (piece) => piece.lot ?? 'unrecorded').entries()].map(
    ([lot, members]) => ({ lot, pieces: members.map((piece) => piece.reference) }),
  );
}
