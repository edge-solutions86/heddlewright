import { InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * Yarn count systems.
 *
 * A direct system states mass for a fixed length, so a bigger number is a
 * thicker yarn: tex is grams per thousand metres, denier grams per nine
 * thousand. An indirect system states length for a fixed mass, so a bigger
 * number is a finer yarn: cotton counts hanks of 840 yards to the pound.
 *
 * Every constant here is worked out from the pound and the yard rather than
 * copied from a table, so the familiar 590.5 for cotton falls out rather than
 * being asserted.
 */

const GRAMS_PER_POUND = 453.59237;
const METRES_PER_YARD = 0.9144;

/** Grams per 1000 m for one unit of an indirect system of `hankYards` per lb. */
function indirectConstant(hankYards) {
  return (GRAMS_PER_POUND * 1000) / (hankYards * METRES_PER_YARD);
}

export const COUNT_SYSTEMS = new Map([
  ['tex', { kind: 'direct', factor: 1, label: 'grams per 1000 m' }],
  ['dtex', { kind: 'direct', factor: 0.1, label: 'grams per 10000 m' }],
  ['denier', { kind: 'direct', factor: 1 / 9, label: 'grams per 9000 m' }],
  ['nm', { kind: 'indirect', constant: 1000, label: 'metres per gram' }],
  ['ne', { kind: 'indirect', constant: indirectConstant(840), label: 'cotton hanks per pound' }],
  ['new', { kind: 'indirect', constant: indirectConstant(560), label: 'worsted hanks per pound' }],
  ['nel', { kind: 'indirect', constant: indirectConstant(300), label: 'linen leas per pound' }],
  ['nw', { kind: 'indirect', constant: indirectConstant(256), label: 'woollen skeins per pound' }],
]);

/** Look a system up by name, case-insensitively. */
export function countSystem(name) {
  nonEmptyString(name, 'system');
  const system = COUNT_SYSTEMS.get(name.toLowerCase());
  if (system === undefined) {
    throw new InvalidArgument(`unknown count system "${name}"`, {
      system: name,
      known: [...COUNT_SYSTEMS.keys()],
    });
  }
  return system;
}

/** Convert a count in any system to tex. */
export function toTex(value, system) {
  positiveNumber(value, 'value');
  const spec = countSystem(system);
  return spec.kind === 'direct' ? value * spec.factor : spec.constant / value;
}

/** Convert tex to any system. */
export function fromTex(tex, system) {
  positiveNumber(tex, 'tex');
  const spec = countSystem(system);
  return spec.kind === 'direct' ? tex / spec.factor : spec.constant / tex;
}

/** Convert straight between two systems. */
export function convertCount(value, from, to, { places = 4 } = {}) {
  return roundTo(fromTex(toTex(value, from), to), places);
}

/** True when a bigger number in this system means a finer yarn. */
export function isIndirect(system) {
  return countSystem(system).kind === 'indirect';
}

/**
 * The resultant count of a folded yarn. Direct counts add; indirect counts add
 * as reciprocals, so two ends of 40s cotton folded together read as 20s.
 */
export function foldedCount(counts, system) {
  array(counts, 'counts', { min: 1 });
  const spec = countSystem(system);
  for (const [i, count] of counts.entries()) positiveNumber(count, `counts[${i}]`);
  if (spec.kind === 'direct') {
    return roundTo(
      counts.reduce((total, count) => total + count, 0),
      4,
    );
  }
  const reciprocal = counts.reduce((total, count) => total + 1 / count, 0);
  return roundTo(1 / reciprocal, 4);
}

/** The same fold written as a plied notation, "2/40s" for two ends of forties. */
export function formatFold(counts, system) {
  array(counts, 'counts', { min: 1 });
  countSystem(system);
  const unique = new Set(counts);
  const name = system.toLowerCase();
  if (unique.size === 1) {
    return `${counts.length}/${counts[0]}${name === 'ne' ? 's' : ` ${name}`}`;
  }
  return counts.join(' + ');
}

/**
 * Ashenhurst's yarn diameter in inches: a cotton yarn of count Ne measures one
 * over twenty eight times the square root of the count. It is the figure every
 * maximum-sett rule is built on.
 */
export function yarnDiameterInches(count, system = 'ne', { constant = 28 } = {}) {
  const ne = fromTex(toTex(count, system), 'ne');
  positiveNumber(constant, 'constant');
  return 1 / (constant * Math.sqrt(ne));
}

/** How many threads of this yarn lie side by side in an inch, touching. */
export function threadsPerInch(count, system = 'ne', options = {}) {
  return roundTo(1 / yarnDiameterInches(count, system, options), 2);
}

/** The length of one gram of a yarn, in metres, whatever system it is stated in. */
export function metresPerGram(count, system) {
  return roundTo(1000 / toTex(count, system), 4);
}
