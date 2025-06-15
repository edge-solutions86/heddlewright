import { array, nonEmptyString, positiveInteger } from '../util/check.js';
import { lcm } from '../util/numeric.js';
import { assertMatrix, dimensions } from '../util/matrix.js';
import { twillWeave } from '../weave/twill.js';

/**
 * Colour and weave effects.
 *
 * The cloth you see is not the weave and it is not the colour order: it is what
 * happens when the two are laid over one another. A plain two by two twill in
 * four dark and four light ends and picks reads as houndstooth, and nothing in
 * either the weave or the colour order on its own predicts that.
 *
 * The rule is simple and the results are not: where the warp is on the face,
 * the cell takes the warp colour; where it is not, it takes the weft colour.
 */

/** The colour of every cell, from a drawdown and the two colour orders. */
export function colourGrid(drawdown, warpColours, weftColours) {
  assertMatrix(drawdown, 'drawdown');
  array(warpColours, 'warpColours', { min: 1 });
  array(weftColours, 'weftColours', { min: 1 });
  const { rows, cols } = dimensions(drawdown);
  return Array.from({ length: rows }, (_, p) =>
    Array.from({ length: cols }, (_, e) =>
      drawdown[p][e] === 1
        ? warpColours[e % warpColours.length]
        : weftColours[p % weftColours.length],
    ),
  );
}

/**
 * How large the visible repeat is once the weave and both colour orders are
 * taken together. It is the least common multiple, and it is usually much
 * bigger than any one of them.
 */
export function effectRepeat({ weavePicks, weaveEnds, warpOrder, weftOrder }) {
  positiveInteger(weavePicks, 'weavePicks');
  positiveInteger(weaveEnds, 'weaveEnds');
  positiveInteger(warpOrder, 'warpOrder');
  positiveInteger(weftOrder, 'weftOrder');
  return { picks: lcm(weavePicks, weftOrder), ends: lcm(weaveEnds, warpOrder) };
}

/** Expand a colour order written as stripes into one entry per thread. */
export function expandStripes(stripes) {
  array(stripes, 'stripes', { min: 1 });
  const out = [];
  for (const [index, stripe] of stripes.entries()) {
    const colour = nonEmptyString(stripe.colour, `stripes[${index}].colour`);
    const count = positiveInteger(stripe.count, `stripes[${index}].count`);
    for (let n = 0; n < count; n += 1) out.push(colour);
  }
  return out;
}

/** How many cells each colour covers, most first. */
export function colourCounts(grid) {
  array(grid, 'grid', { min: 1 });
  const counts = new Map();
  for (const row of grid) {
    for (const colour of row) counts.set(colour, (counts.get(colour) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([colour, cells]) => ({ colour, cells }))
    .sort((a, b) => b.cells - a.cells || a.colour.localeCompare(b.colour));
}

/** The grid printed with one character per colour. */
export function renderColourGrid(grid, symbols) {
  array(grid, 'grid', { min: 1 });
  const map = symbols ?? defaultSymbols(grid);
  return grid.map((row) => row.map((colour) => map[colour] ?? '?').join('')).join('\n');
}

function defaultSymbols(grid) {
  const alphabet = ['#', '.', 'o', '+', '*', '~'];
  const map = {};
  let next = 0;
  for (const { colour } of colourCounts(grid)) {
    map[colour] = alphabet[next % alphabet.length];
    next += 1;
  }
  return map;
}

/**
 * Houndstooth: a two by two twill in four dark and four light, both ways. The
 * broken check it produces is the standard demonstration that a colour effect
 * is not the weave and not the colour order.
 */
export function houndstooth({ dark = 'dark', light = 'light' } = {}) {
  const weave = twillWeave('2/2', { picks: 8, ends: 8 });
  const order = expandStripes([
    { colour: dark, count: 4 },
    { colour: light, count: 4 },
  ]);
  return colourGrid(weave, order, order);
}

/**
 * A step pattern, the other classic: the same twill in one dark and one light
 * end and pick, which reads as fine diagonal lines rather than a check.
 */
export function stepPattern({ dark = 'dark', light = 'light' } = {}) {
  const weave = twillWeave('2/2', { picks: 8, ends: 8 });
  const order = expandStripes([
    { colour: dark, count: 1 },
    { colour: light, count: 1 },
  ]);
  return colourGrid(weave, order, order);
}

/** True when the effect is balanced, which is what makes a check read square. */
export function isBalancedEffect(grid) {
  const counts = colourCounts(grid);
  if (counts.length !== 2) return false;
  return counts[0].cells === counts[1].cells;
}
