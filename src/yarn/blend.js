import { InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, number, positiveNumber } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';

/**
 * Fibre blends.
 *
 * A blend is stated by weight, and every property of the yarn that follows from
 * it is a weighted average by weight rather than by anything else. The label
 * rules are the part people get wrong: the shares have to add to a hundred, and
 * they are rounded to whole numbers on the label, so a blend that adds to a
 * hundred exactly can still round to ninety nine.
 */

/** Check a blend and hand back a normalised copy, shares as fractions. */
export function normaliseBlend(parts) {
  array(parts, 'parts', { min: 1 });
  const normalised = parts.map((part, index) => ({
    fibre: nonEmptyString(part.fibre, `parts[${index}].fibre`),
    share: positiveNumber(part.share, `parts[${index}].share`),
  }));
  const named = new Set();
  for (const part of normalised) {
    if (named.has(part.fibre)) {
      throw new InvalidArgument(`${part.fibre} is in the blend twice`, { fibre: part.fibre });
    }
    named.add(part.fibre);
  }
  const total = sumBy(normalised, (part) => part.share, 'parts');
  return normalised.map((part) => ({ ...part, share: part.share / total }));
}

/** Whether the shares as given already add to a hundred per cent. */
export function addsToWhole(parts, { tolerance = 0.001 } = {}) {
  array(parts, 'parts', { min: 1 });
  number(tolerance, 'tolerance', { min: 0 });
  const total = sumBy(parts, (part) => part.share, 'parts');
  return Math.abs(total - 100) <= tolerance;
}

/**
 * The blend as it goes on a label: whole percentages that add to a hundred.
 * The rounding is done largest first and the remainder goes to the largest
 * fibre, because a label that adds to ninety nine is a label nobody will sign.
 */
export function labelShares(parts) {
  const normalised = normaliseBlend(parts);
  const rounded = normalised
    .map((part) => ({ fibre: part.fibre, exact: part.share * 100 }))
    .sort((a, b) => b.exact - a.exact)
    .map((part) => ({ ...part, percent: Math.round(part.exact) }));
  const total = sumBy(rounded, (part) => part.percent, 'parts');
  if (total !== 100 && rounded.length > 0) rounded[0].percent += 100 - total;
  return rounded.map((part) => ({ fibre: part.fibre, percent: part.percent }));
}

/** The label itself, written the way a ticket reads. */
export function labelText(parts) {
  return labelShares(parts)
    .map((part) => `${part.percent}% ${part.fibre}`)
    .join(' ');
}

/**
 * A property of the blend, averaged by weight. Moisture regain, density and
 * fibre strength all work this way and none of them averages by fibre count.
 */
export function weightedProperty(parts, values) {
  const normalised = normaliseBlend(parts);
  let total = 0;
  for (const part of normalised) {
    const value = values[part.fibre];
    if (value === undefined) {
      throw new InvalidArgument(`no value given for ${part.fibre}`, { fibre: part.fibre });
    }
    total += number(value, `values.${part.fibre}`) * part.share;
  }
  return roundTo(total, 4);
}

/** The weight each fibre contributes to a batch. */
export function batchWeights(parts, { totalKg }) {
  positiveNumber(totalKg, 'totalKg');
  return normaliseBlend(parts).map((part) => ({
    fibre: part.fibre,
    kilograms: roundTo(part.share * totalKg, 4),
  }));
}

/**
 * The conditioned weight of a batch, which is what it is bought and sold by.
 * Fibre holds water, and the trade weighs it at an agreed regain rather than
 * bone dry, so the invoice weight is above the oven dry one.
 */
export function conditionedWeight({ ovenDryKg, regainPercent }) {
  positiveNumber(ovenDryKg, 'ovenDryKg');
  number(regainPercent, 'regainPercent', { min: 0 });
  return roundTo(ovenDryKg * (1 + regainPercent / 100), 4);
}

/** The regain a blend is conditioned at, averaged by weight. */
export function blendRegain(parts, regains) {
  return weightedProperty(parts, regains);
}

/** Whether a blend is inside a stated tolerance of its target. */
export function withinTolerance(parts, target, { tolerancePercent = 3 } = {}) {
  const found = new Map(labelShares(parts).map((part) => [part.fibre, part.percent]));
  const wanted = new Map(labelShares(target).map((part) => [part.fibre, part.percent]));
  const findings = [];
  for (const [fibre, percent] of wanted) {
    const got = found.get(fibre) ?? 0;
    if (Math.abs(got - percent) > tolerancePercent) {
      findings.push({ fibre, wanted: percent, got, off: got - percent });
    }
  }
  return { within: findings.length === 0, findings };
}
