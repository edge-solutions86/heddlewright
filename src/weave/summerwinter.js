import { DraftError } from '../util/errors.js';
import { array, integer, positiveInteger } from '../util/check.js';

/**
 * Summer and winter.
 *
 * Two shafts do nothing but tie the pattern weft down; every other shaft
 * carries one block. A unit of a block is threaded tie, pattern, tie, pattern,
 * so a block is four ends wide and no float can run further than the tie-downs
 * allow. That is the whole point of the weave: a coverlet in summer and winter
 * can be woven in as many blocks as the loom has shafts to spare, and none of
 * them floats long enough to catch.
 */

export const TIE_SHAFTS = [1, 2];

/** How many shafts a block count needs, which is the blocks plus the two ties. */
export function shaftsForBlocks(blocks) {
  positiveInteger(blocks, 'blocks');
  return blocks + TIE_SHAFTS.length;
}

/** How many blocks a loom of this many shafts can carry. */
export function blocksForShafts(shafts) {
  positiveInteger(shafts, 'shafts');
  if (shafts <= TIE_SHAFTS.length) {
    throw new DraftError('summer and winter needs more than the two tie shafts', { shafts });
  }
  return shafts - TIE_SHAFTS.length;
}

/** The pattern shaft a block sits on, 1-based block numbers. */
export function shaftForBlock(block) {
  positiveInteger(block, 'block');
  return block + TIE_SHAFTS.length;
}

/** Thread a run of blocks, four ends to a unit. */
export function summerWinterThreading(blocks, { units = 1 } = {}) {
  array(blocks, 'blocks', { min: 1 });
  positiveInteger(units, 'units');
  const out = [];
  for (const block of blocks) {
    const pattern = shaftForBlock(block);
    for (let unit = 0; unit < units; unit += 1) {
      out.push(TIE_SHAFTS[0], pattern, TIE_SHAFTS[1], pattern);
    }
  }
  return out;
}

/**
 * The tie-up: two treadles per block, one against each tie shaft, then the two
 * tabby treadles. The tabby lifts the ties against everything else, which is
 * what makes the ground plain weave whatever the pattern is doing.
 */
export function summerWinterTieup(blockCount) {
  positiveInteger(blockCount, 'blockCount');
  const patternShafts = Array.from({ length: blockCount }, (_, index) => shaftForBlock(index + 1));
  const treadles = [];
  for (const shaft of patternShafts) {
    treadles.push([TIE_SHAFTS[0], shaft]);
    treadles.push([TIE_SHAFTS[1], shaft]);
  }
  treadles.push([...TIE_SHAFTS]);
  treadles.push([...patternShafts]);
  return treadles;
}

/** The two treadles that weave a block, 1-based into that tie-up. */
export function treadlesForBlock(block) {
  positiveInteger(block, 'block');
  return [block * 2 - 1, block * 2];
}

/** The two tabby treadles for a given block count. */
export function tabbyTreadles(blockCount) {
  positiveInteger(blockCount, 'blockCount');
  return [blockCount * 2 + 1, blockCount * 2 + 2];
}

/**
 * Treadle a run of blocks. Each unit takes both of the block's treadles in
 * turn, which is what alternates the tie-down and stops the pattern weft
 * floating over the same tie twice running.
 */
export function summerWinterTreadling(blocks, blockCount, { units = 1 } = {}) {
  array(blocks, 'blocks', { min: 1 });
  positiveInteger(blockCount, 'blockCount');
  positiveInteger(units, 'units');
  const out = [];
  for (const block of blocks) {
    if (block > blockCount) {
      throw new DraftError('that block is past the ones the loom is tied for', {
        block,
        blockCount,
      });
    }
    const [first, second] = treadlesForBlock(block);
    for (let unit = 0; unit < units; unit += 1) out.push([first], [second]);
  }
  return out;
}

/** The whole draft for a run of blocks. */
export function summerWinterDraft(blocks, options = {}) {
  const blockCount = Math.max(...blocks);
  return {
    name: `summer and winter ${blocks.join('')}`,
    shafts: shaftsForBlocks(blockCount),
    treadles: blockCount * 2 + 2,
    shed: 'rising',
    threading: summerWinterThreading(blocks, options),
    tieup: summerWinterTieup(blockCount),
    treadling: summerWinterTreadling(blocks, blockCount, options),
  };
}

/**
 * The longest the pattern weft can float, which is two ends whatever the block
 * size, because a tie-down sits between every pair of pattern ends.
 */
export function maximumFloat() {
  return 2;
}

/** Refuse a threading that is missing one of its tie shafts. */
export function assertTiesThreaded(threading) {
  array(threading, 'threading', { min: 1 });
  for (const shaft of TIE_SHAFTS) {
    if (!threading.includes(shaft)) {
      throw new DraftError(`tie shaft ${shaft} carries no ends`, { shaft });
    }
  }
  return threading;
}

/** How many ends a run of blocks takes. */
export function endsForBlocks(blocks, { units = 1 } = {}) {
  array(blocks, 'blocks', { min: 1 });
  integer(units, 'units', { min: 1 });
  return blocks.length * units * 4;
}
