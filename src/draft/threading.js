import { DraftError } from '../util/errors.js';
import { array, integer, positiveInteger, oneOf } from '../util/check.js';
import { mod } from '../util/numeric.js';

/**
 * Threading: which shaft each warp end is threaded through, read left to right
 * across the loom. Ends are 1-based in every message a weaver sees, and the
 * array itself is 0-based, so every function here converts at its own edge.
 */

export const DRAW_STYLES = ['straight', 'point', 'broken', 'undulating', 'divided'];

/** Check a threading against a shaft count and hand it back. */
export function assertThreading(threading, shafts) {
  array(threading, 'threading', { min: 1 });
  positiveInteger(shafts, 'shafts');
  for (const [i, shaft] of threading.entries()) {
    integer(shaft, `threading[${i}]`, { min: 1, max: shafts });
  }
  return threading;
}

/** A straight draw: 1, 2, 3 ... shafts, repeated until `ends` are threaded. */
export function straightDraw(shafts, ends = shafts, { start = 1, direction = 1 } = {}) {
  positiveInteger(shafts, 'shafts');
  positiveInteger(ends, 'ends');
  integer(start, 'start', { min: 1, max: shafts });
  oneOf(direction, 'direction', [1, -1]);
  const out = [];
  for (let i = 0; i < ends; i += 1) {
    out.push(mod(start - 1 + direction * i, shafts) + 1);
  }
  return out;
}

/**
 * A point draw runs up to the top shaft and back down without repeating the
 * turning shaft, so one full point over n shafts is 2n - 2 ends long.
 */
export function pointDraw(shafts, ends) {
  positiveInteger(shafts, 'shafts');
  // The shaft check comes first: on a single shaft the default end count works
  // out at zero, and reporting that as a bad end count hides the real problem.
  if (shafts < 2) {
    throw new DraftError('a point draw needs at least two shafts', { shafts });
  }
  const cycle = 2 * shafts - 2;
  const wanted = ends ?? cycle;
  positiveInteger(wanted, 'ends');
  const out = [];
  for (let i = 0; i < wanted; i += 1) {
    const at = mod(i, cycle);
    out.push(at < shafts ? at + 1 : cycle - at + 1);
  }
  return out;
}

/**
 * A broken draw is a straight draw whose blocks step across a break, the
 * threading behind a broken or herringbone twill. `blockSize` ends run
 * straight, then the start jumps by `stagger`.
 */
export function brokenDraw(shafts, ends, { blockSize = 4, stagger = 2 } = {}) {
  positiveInteger(shafts, 'shafts');
  positiveInteger(ends, 'ends');
  positiveInteger(blockSize, 'blockSize');
  integer(stagger, 'stagger');
  const out = [];
  for (let i = 0; i < ends; i += 1) {
    const block = Math.floor(i / blockSize);
    const within = i % blockSize;
    out.push(mod(block * stagger + within, shafts) + 1);
  }
  return out;
}

/**
 * An undulating draw holds each shaft for a run length taken from a repeating
 * list, which is how a wavy twill gets its varying stripe width.
 */
export function undulatingDraw(shafts, ends, runLengths) {
  positiveInteger(shafts, 'shafts');
  positiveInteger(ends, 'ends');
  array(runLengths, 'runLengths', { min: 1 });
  for (const [i, run] of runLengths.entries()) {
    positiveInteger(run, `runLengths[${i}]`);
  }
  const out = [];
  let shaft = 0;
  let step = 0;
  while (out.length < ends) {
    const run = runLengths[step % runLengths.length];
    for (let n = 0; n < run && out.length < ends; n += 1) {
      out.push(shaft + 1);
    }
    shaft = mod(shaft + 1, shafts);
    step += 1;
  }
  return out;
}

/**
 * A divided draw threads two shaft groups alternately, the classic ground and
 * pattern arrangement of an overshot or a summer and winter draft.
 */
export function dividedDraw(groundShafts, patternShafts, ends) {
  array(groundShafts, 'groundShafts', { min: 1 });
  array(patternShafts, 'patternShafts', { min: 1 });
  positiveInteger(ends, 'ends');
  const out = [];
  for (let i = 0; i < ends; i += 1) {
    const group = i % 2 === 0 ? groundShafts : patternShafts;
    const index = Math.floor(i / 2) % group.length;
    out.push(group[index]);
  }
  return out;
}

/** Build a threading by name, so a draft file can carry a style rather than a list. */
export function drawByStyle(style, options = {}) {
  oneOf(style, 'style', DRAW_STYLES);
  const { shafts, ends } = options;
  switch (style) {
    case 'straight':
      return straightDraw(shafts, ends ?? shafts, options);
    case 'point':
      return pointDraw(shafts, ends);
    case 'broken':
      return brokenDraw(shafts, ends ?? shafts * 2, options);
    case 'undulating':
      return undulatingDraw(shafts, ends ?? shafts * 2, options.runLengths ?? [1, 2]);
    default:
      return dividedDraw(options.groundShafts, options.patternShafts, ends ?? shafts * 2);
  }
}

/** How many ends sit on each shaft, 1-based, as a plain array. */
export function shaftLoad(threading, shafts) {
  assertThreading(threading, shafts);
  const load = new Array(shafts).fill(0);
  for (const shaft of threading) load[shaft - 1] += 1;
  return load;
}

/** Shafts with nothing threaded on them, which means heddles standing idle. */
export function idleShafts(threading, shafts) {
  return shaftLoad(threading, shafts)
    .map((count, index) => (count === 0 ? index + 1 : 0))
    .filter((shaft) => shaft !== 0);
}

/** The 1-based end positions threaded on a given shaft. */
export function endsOnShaft(threading, shaft) {
  positiveInteger(shaft, 'shaft');
  return threading
    .map((value, index) => (value === shaft ? index + 1 : 0))
    .filter((end) => end !== 0);
}

/** The lowest shaft count a threading could be rewritten onto. */
export function shaftsUsed(threading) {
  array(threading, 'threading', { min: 1 });
  return new Set(threading).size;
}
