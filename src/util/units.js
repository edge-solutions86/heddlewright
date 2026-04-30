import { InvalidArgument } from './errors.js';
import { nonEmptyString, number } from './check.js';
import { roundTo } from './numeric.js';

/**
 * Units.
 *
 * A weaving shed runs on both systems at once and will not be talked out of it:
 * the loom is quoted in centimetres, the cloth is sold in yards, the yarn is
 * bought in kilograms and the sett is set in ends an inch. Every conversion
 * here is exact by definition rather than rounded, and everything goes through
 * one base unit per dimension so a round trip cannot drift.
 */

const LENGTH = new Map([
  ['mm', 0.001],
  ['cm', 0.01],
  ['m', 1],
  ['km', 1000],
  ['in', 0.0254],
  ['ft', 0.3048],
  ['yd', 0.9144],
]);

const MASS = new Map([
  ['g', 0.001],
  ['kg', 1],
  ['oz', 0.028349523125],
  ['lb', 0.45359237],
]);

const AREA = new Map([
  ['m2', 1],
  ['cm2', 0.0001],
  ['in2', 0.0254 * 0.0254],
  ['yd2', 0.9144 * 0.9144],
]);

const DIMENSIONS = new Map([
  ['length', LENGTH],
  ['mass', MASS],
  ['area', AREA],
]);

/** Which dimension a unit belongs to. */
export function dimensionOf(unit) {
  nonEmptyString(unit, 'unit');
  for (const [dimension, table] of DIMENSIONS) {
    if (table.has(unit)) return dimension;
  }
  throw new InvalidArgument(`unknown unit "${unit}"`, { unit, known: knownUnits() });
}

/** Every unit the package converts between. */
export function knownUnits() {
  return [...DIMENSIONS.values()].flatMap((table) => [...table.keys()]).sort();
}

/** Convert between two units of the same dimension. */
export function convert(value, from, to, { places = 6 } = {}) {
  number(value, 'value');
  const dimension = dimensionOf(from);
  if (dimensionOf(to) !== dimension) {
    throw new InvalidArgument(`${from} and ${to} do not measure the same thing`, { from, to });
  }
  const table = DIMENSIONS.get(dimension);
  return roundTo((value * table.get(from)) / table.get(to), places);
}

/** True when two units can be converted between. */
export function comparable(from, to) {
  try {
    return dimensionOf(from) === dimensionOf(to);
  } catch {
    return false;
  }
}

/** Threads per inch to threads per centimetre and back. */
export function perInchToPerCm(value) {
  number(value, 'value', { min: 0 });
  return roundTo(value / 2.54, 4);
}

export function perCmToPerInch(value) {
  number(value, 'value', { min: 0 });
  return roundTo(value * 2.54, 4);
}

/**
 * A density conversion: grams a square metre to ounces a square yard. It is not
 * a plain unit conversion because both parts change, which is where it usually
 * goes wrong.
 */
export function gsmToOzYd2(value) {
  number(value, 'value', { min: 0 });
  return roundTo(convert(value, 'g', 'oz') / convert(1, 'm2', 'yd2'), 4);
}

export function ozYd2ToGsm(value) {
  number(value, 'value', { min: 0 });
  return roundTo(convert(value, 'oz', 'g') * convert(1, 'm2', 'yd2'), 4);
}

/** Format a value with its unit, for a docket. */
export function format(value, unit, { places = 2 } = {}) {
  dimensionOf(unit);
  number(value, 'value');
  return `${roundTo(value, places)} ${unit}`;
}
