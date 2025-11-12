import { DraftError } from '../util/errors.js';
import { array, integer, oneOf, positiveInteger } from '../util/check.js';

/**
 * Overshot.
 *
 * Four shafts, worked in overlapping pairs: shafts one and two make block A,
 * two and three block B, three and four block C, four and one block D. Every
 * block shares a shaft with the two next to it, which is what ties the figure
 * together and what limits a four shaft overshot to those four blocks and no
 * others.
 *
 * The cloth is two wefts. A pattern pick floats over its block; a tabby pick
 * binds the ground underneath it. They alternate, so a coverlet woven this way
 * has twice as many picks as it has pattern picks.
 */

export const BLOCKS = ['A', 'B', 'C', 'D'];

const BLOCK_SHAFTS = new Map([
  ['A', [1, 2]],
  ['B', [2, 3]],
  ['C', [3, 4]],
  ['D', [4, 1]],
]);

/** The two shafts a block is threaded on. */
export function blockShafts(block) {
  oneOf(block, 'block', BLOCKS);
  return [...BLOCK_SHAFTS.get(block)];
}

/** True when two blocks share a shaft, which is what lets them run together. */
export function blocksAdjoin(left, right) {
  const a = blockShafts(left);
  const b = blockShafts(right);
  return a.some((shaft) => b.includes(shaft));
}

/**
 * Thread a run of blocks. Each block is threaded on its two shafts, repeated
 * for the number of units the block is given, and the threading is checked so
 * that neighbouring blocks in the list actually adjoin.
 */
export function overshotThreading(blocks, { units = 2 } = {}) {
  array(blocks, 'blocks', { min: 1 });
  positiveInteger(units, 'units');
  const out = [];
  for (const [index, block] of blocks.entries()) {
    const shafts = blockShafts(block);
    if (index > 0 && !blocksAdjoin(blocks[index - 1], block)) {
      throw new DraftError('those two blocks do not share a shaft', {
        at: index + 1,
        from: blocks[index - 1],
        to: block,
      });
    }
    for (let unit = 0; unit < units; unit += 1) out.push(shafts[0], shafts[1]);
  }
  return out;
}

/**
 * The tie-up a four shaft overshot loom is corded for: four pattern treadles,
 * one per block, then the two tabby treadles.
 */
export function overshotTieup() {
  return [...BLOCKS.map((block) => blockShafts(block)), [1, 3], [2, 4]];
}

/** Which treadle weaves a block, 1-based into the tie-up above. */
export function treadleForBlock(block) {
  return BLOCKS.indexOf(oneOf(block, 'block', BLOCKS)) + 1;
}

/** The two tabby treadles, which never change. */
export function tabbyTreadles() {
  return [BLOCKS.length + 1, BLOCKS.length + 2];
}

/**
 * Treadle a run of blocks with a tabby pick after every pattern pick. The tabby
 * alternates on its own account, so the ground stays plain weave however the
 * pattern is ordered.
 */
export function overshotTreadling(blocks, { units = 2, startTabby = 0 } = {}) {
  array(blocks, 'blocks', { min: 1 });
  positiveInteger(units, 'units');
  integer(startTabby, 'startTabby', { min: 0, max: 1 });
  const tabby = tabbyTreadles();
  const out = [];
  let which = startTabby;
  for (const block of blocks) {
    const treadle = treadleForBlock(block);
    for (let unit = 0; unit < units; unit += 1) {
      out.push([treadle]);
      out.push([tabby[which]]);
      which = 1 - which;
    }
  }
  return out;
}

/** The whole draft, treadled in the order it was threaded. */
export function overshotDraft(blocks, options = {}) {
  return {
    name: `overshot ${blocks.join('')}`,
    shafts: 4,
    treadles: 6,
    shed: 'rising',
    threading: overshotThreading(blocks, options),
    tieup: overshotTieup(),
    treadling: overshotTreadling(blocks, options),
  };
}

/**
 * How long the pattern weft floats over a block, in ends. It is twice the
 * units in the block, because each unit is two ends on the same pair of shafts.
 */
export function patternFloat({ units = 2 } = {}) {
  positiveInteger(units, 'units');
  return units * 2;
}

/** Refuse a block run whose float would be too long to wear. */
export function assertFloatWithin(limit, options = {}) {
  positiveInteger(limit, 'limit');
  const float = patternFloat(options);
  if (float > limit) {
    throw new DraftError('the pattern float is longer than the cloth will carry', {
      float,
      limit,
    });
  }
  return float;
}

/** Every block run that can follow a given block on four shafts. */
export function blocksAfter(block) {
  return BLOCKS.filter((candidate) => candidate !== block && blocksAdjoin(block, candidate));
}
