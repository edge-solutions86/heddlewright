import { DraftError } from '../util/errors.js';
import { array, nonEmptyString, plainObject, positiveInteger } from '../util/check.js';
import { createMatrix } from '../util/matrix.js';
import { assertInterlaces } from './combine.js';

/**
 * Profile drafts and block substitution.
 *
 * A profile says nothing about interlacement. It names blocks across the warp
 * and down the weft, and a block that meets itself is figure while a block that
 * meets a different one is ground. Substituting a weave for each of those two
 * states turns the profile into cloth, which is how damask, overshot and summer
 * and winter are all drafted from the same small diagram.
 */

/** Check a profile order and return the distinct block names in first-seen order. */
export function profileBlocks(order) {
  array(order, 'order', { min: 1 });
  const seen = [];
  for (const [i, block] of order.entries()) {
    nonEmptyString(block, `order[${i}]`);
    if (!seen.includes(block)) seen.push(block);
  }
  return seen;
}

/**
 * How many threads each block takes. A number applies to every block; an object
 * gives each block its own size, and any block it does not name is refused
 * rather than defaulted.
 */
export function blockSizes(order, units) {
  const blocks = profileBlocks(order);
  if (typeof units === 'number') {
    positiveInteger(units, 'units');
    return new Map(blocks.map((block) => [block, units]));
  }
  plainObject(units, 'units');
  const sizes = new Map();
  for (const block of blocks) {
    const size = units[block];
    if (size === undefined) {
      throw new DraftError(`block ${block} has no thread count`, { block, blocks });
    }
    sizes.set(block, positiveInteger(size, `units.${block}`));
  }
  return sizes;
}

/** Expand a block order into one entry per thread. */
export function expandProfileOrder(order, units) {
  const sizes = blockSizes(order, units);
  const out = [];
  for (const block of order) {
    for (let n = 0; n < sizes.get(block); n += 1) out.push(block);
  }
  return out;
}

/**
 * Turn a profile into cloth. Every intersection where the warp block equals the
 * weft block takes the figure weave; everywhere else takes the ground.
 */
export function substituteProfile(profileThreading, profileTreadling, options) {
  plainObject(options, 'options');
  const { figure, ground, warpUnits = 1, weftUnits = warpUnits } = options;
  assertInterlaces(figure, 'figure');
  assertInterlaces(ground, 'ground');
  const ends = expandProfileOrder(profileThreading, warpUnits);
  const picks = expandProfileOrder(profileTreadling, weftUnits);
  const cloth = createMatrix(picks.length, ends.length);
  for (const [p, weftBlock] of picks.entries()) {
    for (const [e, warpBlock] of ends.entries()) {
      const weave = warpBlock === weftBlock ? figure : ground;
      cloth[p][e] = weave[p % weave.length][e % weave[0].length];
    }
  }
  return cloth;
}

/** The block a given thread belongs to, 1-based, for reading a report back. */
export function blockAt(order, units, thread) {
  positiveInteger(thread, 'thread');
  const expanded = expandProfileOrder(order, units);
  if (thread > expanded.length) {
    throw new DraftError('that thread is past the end of the profile', {
      thread,
      threads: expanded.length,
    });
  }
  return expanded[thread - 1];
}

/** The 1-based thread span each block occupies, in profile order. */
export function blockSpans(order, units) {
  const sizes = blockSizes(order, units);
  const spans = [];
  let at = 1;
  for (const block of order) {
    const size = sizes.get(block);
    spans.push({ block, from: at, to: at + size - 1, threads: size });
    at += size;
  }
  return spans;
}

/**
 * A profile is a tie-down profile when no block sits next to itself, which is
 * what keeps a figure from running into the next one.
 */
export function hasAdjacentRepeats(order) {
  array(order, 'order', { min: 1 });
  return order.some((block, i) => i > 0 && block === order[i - 1]);
}

/** The block diagram itself, as a matrix of figure and ground cells. */
export function profileMatrix(profileThreading, profileTreadling) {
  array(profileThreading, 'profileThreading', { min: 1 });
  array(profileTreadling, 'profileTreadling', { min: 1 });
  return profileTreadling.map((weftBlock) =>
    profileThreading.map((warpBlock) => (warpBlock === weftBlock ? 1 : 0)),
  );
}
