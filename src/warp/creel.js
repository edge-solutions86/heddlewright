import { CapacityError, InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo, sum } from '../util/numeric.js';

/**
 * The colour order and the creel it is built from.
 *
 * A striped warp is written as a colour order: so many ends of this, so many of
 * that, repeated across the width. Turning that into a creel plan means
 * counting how many ends of each colour the whole warp needs, and then how many
 * packages of each colour have to hang in the creel to supply them.
 */

/** Check a colour order and hand back a normalised copy. */
export function normaliseColourOrder(order) {
  array(order, 'order', { min: 1 });
  return order.map((stripe, index) => ({
    colour: nonEmptyString(stripe.colour, `order[${index}].colour`),
    ends: positiveInteger(stripe.ends, `order[${index}].ends`),
  }));
}

/** How many ends one repeat of the order covers. */
export function orderRepeat(order) {
  return sum(normaliseColourOrder(order).map((stripe) => stripe.ends));
}

/** The colour of every end across the warp, repeating the order as needed. */
export function expandColourOrder(order, totalEnds) {
  const stripes = normaliseColourOrder(order);
  positiveInteger(totalEnds, 'totalEnds');
  const out = [];
  let at = 0;
  while (out.length < totalEnds) {
    const stripe = stripes[at % stripes.length];
    for (let n = 0; n < stripe.ends && out.length < totalEnds; n += 1) out.push(stripe.colour);
    at += 1;
  }
  return out;
}

/** How many ends of each colour a warp of this width needs. */
export function endsByColour(order, totalEnds) {
  const colours = new Map();
  for (const colour of expandColourOrder(order, totalEnds)) {
    colours.set(colour, (colours.get(colour) ?? 0) + 1);
  }
  return [...colours.entries()]
    .map(([colour, ends]) => ({ colour, ends }))
    .sort((a, b) => b.ends - a.ends || a.colour.localeCompare(b.colour));
}

/**
 * The creel plan: one package position per end of each colour in a single
 * warping pass, which is what the creel actually has to hold at once.
 */
export function creelPlan(order, { totalEnds, sections = 1 }) {
  positiveInteger(sections, 'sections');
  const byColour = endsByColour(order, totalEnds);
  const positions = byColour.map((entry) => ({
    colour: entry.colour,
    ends: entry.ends,
    packages: Math.ceil(entry.ends / sections),
  }));
  return {
    positions,
    packages: sum(positions.map((entry) => entry.packages)),
    colours: positions.length,
    sections,
  };
}

/** Refuse a plan the creel cannot hold. */
export function assertCreelFits(plan, capacity) {
  positiveInteger(capacity, 'capacity');
  if (plan.packages > capacity) {
    throw new CapacityError('the creel cannot hold that many packages at once', {
      packages: plan.packages,
      capacity,
      over: plan.packages - capacity,
    });
  }
  return plan;
}

/** The stripes as widths across the cloth, at a given sett. */
export function stripeWidths(order, endsPerInch) {
  positiveNumber(endsPerInch, 'endsPerInch');
  return normaliseColourOrder(order).map((stripe) => ({
    colour: stripe.colour,
    ends: stripe.ends,
    inches: roundTo(stripe.ends / endsPerInch, 3),
  }));
}

/**
 * Mirror a colour order about its last stripe, which is how a symmetric warp is
 * written from half of it. The turning stripe is not repeated.
 */
export function mirrorOrder(order) {
  const stripes = normaliseColourOrder(order);
  if (stripes.length < 2) {
    throw new InvalidArgument('a mirrored order needs at least two stripes', {
      stripes: stripes.length,
    });
  }
  return [...stripes, ...stripes.slice(0, -1).reverse()];
}

/** Where each stripe starts and ends across the warp, in 1-based end numbers. */
export function stripeSpans(order, totalEnds) {
  const colours = expandColourOrder(order, totalEnds);
  const spans = [];
  let start = 1;
  for (let end = 1; end <= colours.length; end += 1) {
    if (end === colours.length || colours[end] !== colours[end - 1]) {
      spans.push({ colour: colours[end - 1], from: start, to: end, ends: end - start + 1 });
      start = end + 1;
    }
  }
  return spans;
}

/** How much yarn each colour needs, in kilograms, at a given count in tex. */
export function yarnByColour(order, { totalEnds, warpLengthMetres, texByColour }) {
  positiveNumber(warpLengthMetres, 'warpLengthMetres');
  return endsByColour(order, totalEnds).map((entry) => {
    const tex = positiveNumber(texByColour[entry.colour], `texByColour.${entry.colour}`);
    const metres = entry.ends * warpLengthMetres;
    return {
      colour: entry.colour,
      ends: entry.ends,
      metres: roundTo(metres, 3),
      kilograms: roundTo((metres * tex) / 1000000, 4),
    };
  });
}
