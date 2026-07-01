import { CapacityError, InvalidArgument } from '../util/errors.js';
import { nonEmptyString, number, oneOf, plainObject, positiveNumber } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';
import { toTex } from './count.js';

/**
 * Yarn packages.
 *
 * Yarn arrives on cones, cheeses, pirns or beams, and what matters about a
 * package is how much length is on it, because that is what decides when a loom
 * stops. Weight is what the yarn is bought by and length is what it is used by,
 * and the count is the only thing that connects them.
 */

export const PACKAGE_KINDS = ['cone', 'cheese', 'pirn', 'spool', 'beam'];

/** Describe a package. */
export function createPackage(input) {
  plainObject(input, 'package');
  const kind = oneOf(input.kind ?? 'cone', 'kind', PACKAGE_KINDS);
  const grossKg = positiveNumber(input.grossKg, 'grossKg');
  const tareKg = number(input.tareKg ?? 0, 'tareKg', { min: 0 });
  if (tareKg >= grossKg) {
    throw new InvalidArgument('the packing weighs as much as the package', { grossKg, tareKg });
  }
  const count = positiveNumber(input.count, 'count');
  const system = nonEmptyString(input.system ?? 'tex', 'system');
  const netKg = roundTo(grossKg - tareKg, 4);
  return Object.freeze({
    kind,
    grossKg,
    tareKg,
    netKg,
    count,
    system,
    metres: roundTo((netKg * 1000000) / toTex(count, system), 1),
  });
}

/** How long a package lasts on a loom, in minutes. */
export function runsForMinutes(pack, { metresPerMinute }) {
  positiveNumber(metresPerMinute, 'metresPerMinute');
  return roundTo(pack.metres / metresPerMinute, 2);
}

/** How many packages a length of yarn needs, and what is left on the last one. */
export function packagesFor(pack, metresNeeded) {
  positiveNumber(metresNeeded, 'metresNeeded');
  const whole = Math.ceil(metresNeeded / pack.metres);
  const used = metresNeeded - (whole - 1) * pack.metres;
  return {
    packages: whole,
    leftOverMetres: roundTo(pack.metres - used, 1),
    totalKg: roundTo(whole * pack.netKg, 4),
  };
}

/**
 * The tail a shed will not use. Yarn left below the doffing height is thrown
 * away with the tube, so the usable length is short of the whole one.
 */
export function usableMetres(pack, { wastePercent = 2 } = {}) {
  number(wastePercent, 'wastePercent', { min: 0, max: 100 });
  return roundTo(pack.metres * (1 - wastePercent / 100), 1);
}

/** Refuse a package a creel position cannot physically take. */
export function assertFitsCreel(pack, { maxKg, maxDiameterMm, diameterMm }) {
  positiveNumber(maxKg, 'maxKg');
  if (pack.grossKg > maxKg) {
    throw new CapacityError('the package is heavier than the creel peg takes', {
      grossKg: pack.grossKg,
      maxKg,
    });
  }
  if (maxDiameterMm !== undefined && diameterMm !== undefined && diameterMm > maxDiameterMm) {
    throw new CapacityError('the package is wider than the creel spacing', {
      diameterMm,
      maxDiameterMm,
    });
  }
  return pack;
}

/** What a delivery of packages comes to, in weight and in length. */
export function deliveryTotals(packages) {
  if (!Array.isArray(packages) || packages.length === 0) {
    throw new InvalidArgument('a delivery has at least one package', {});
  }
  return {
    packages: packages.length,
    grossKg: roundTo(
      sumBy(packages, (pack) => pack.grossKg, 'packages'),
      4,
    ),
    netKg: roundTo(
      sumBy(packages, (pack) => pack.netKg, 'packages'),
      4,
    ),
    metres: roundTo(
      sumBy(packages, (pack) => pack.metres, 'packages'),
      1,
    ),
  };
}

/** How much a delivery costs, priced on net weight. */
export function deliveryCost(packages, { pricePerKg }) {
  positiveNumber(pricePerKg, 'pricePerKg');
  return roundTo(deliveryTotals(packages).netKg * pricePerKg, 2);
}
