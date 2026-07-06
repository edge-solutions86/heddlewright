import { CapacityError, InvalidArgument } from '../util/errors.js';
import { array, integer, positiveInteger, oneOf } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';
import { runs } from '../util/runs.js';
import { tally } from '../util/group.js';

/**
 * Drop box motion: weaving more than one weft on a shuttle loom.
 *
 * A shuttle loom picks from whatever box is lifted into line with the race, so
 * a second colour means a second box and a chain to lift it. Two rules govern
 * everything else here.
 *
 * The first is the shuttle's own: a shuttle sent from the left arrives on the
 * right, and it can only be picked again from the box it landed in. On a loom
 * boxed at one end only, every weft therefore has to be picked an even number
 * of times in a row, or its shuttle finishes on the plain side with no box to
 * hold it. A loom boxed at both ends has no such rule and can pick single
 * picks of anything.
 *
 * The second is timing: the box has to be lifted and settled between one pick
 * and the next, so a change costs part of a pick. A pattern that changes on
 * every pick runs measurably slower than one that changes every fourth, and
 * which colour sits in which box decides how far the box travels.
 */

/** Which ends of the sley carry boxes. */
export const BOXING = ['one', 'both'];

/**
 * Read a weft pattern into runs of one colour.
 *
 * Runs are read straight through rather than round the loop. A pattern is
 * repeated, so the last run and the first do join in the cloth, but the box
 * chain is cut and laced as written and the weaver reads it that way.
 */
export function weftRuns(pattern) {
  array(pattern, 'pattern', { min: 1 });
  for (const [i, colour] of pattern.entries()) {
    if (typeof colour !== 'string' || colour.length === 0) {
      throw new InvalidArgument(`pattern[${i}] must be a colour name`, { got: colour });
    }
  }
  return runs(pattern).map((run) => ({
    colour: run.value,
    start: run.start + 1,
    picks: run.length,
  }));
}

/** The distinct colours in a pattern, in the order they are first picked. */
export function coloursIn(pattern) {
  const seen = [];
  for (const colour of pattern) {
    if (!seen.includes(colour)) seen.push(colour);
  }
  return seen;
}

/**
 * Allot a box to each colour.
 *
 * The colour picked most goes in box one. Box one is the box the motion rests
 * in, so the commonest weft is the one that costs no lift, and the chain works
 * hardest for the colours that appear least. Ties fall back to the order the
 * colours first appear, so the allotment is stable for a given pattern.
 */
export function allotBoxes(pattern, { boxes } = {}) {
  const colours = coloursIn(pattern);
  positiveInteger(boxes, 'boxes');
  if (colours.length > boxes) {
    throw new CapacityError(`${colours.length} wefts will not go in ${boxes} boxes`, {
      colours,
      boxes,
    });
  }
  const order = coloursIn(pattern);
  const counted = colours.map((colour) => ({
    colour,
    picks: pattern.filter((entry) => entry === colour).length,
    first: order.indexOf(colour),
  }));
  counted.sort((a, b) => b.picks - a.picks || a.first - b.first);
  return counted.map((entry, index) => ({
    colour: entry.colour,
    box: index + 1,
    picks: entry.picks,
  }));
}

/** The box each pick is taken from, one entry per pick. */
export function boxChain(pattern, allotment) {
  array(allotment, 'allotment', { min: 1 });
  const byColour = new Map(allotment.map((entry) => [entry.colour, entry.box]));
  return [...pattern].map((colour, index) => {
    const box = byColour.get(colour);
    if (box === undefined) {
      throw new InvalidArgument(`pick ${index + 1} wants ${colour}, which has no box`, {
        pick: index + 1,
        colour,
      });
    }
    return box;
  });
}

/** How many times the box has to lift over one repeat of the chain. */
export function boxChanges(chain, { cyclic = true } = {}) {
  array(chain, 'chain', { min: 1 });
  let changes = 0;
  for (let i = 1; i < chain.length; i += 1) {
    if (chain[i] !== chain[i - 1]) changes += 1;
  }
  if (cyclic && chain.length > 1 && chain[0] !== chain[chain.length - 1]) changes += 1;
  return changes;
}

/** The furthest the box has to travel in one change, in box positions. */
export function longestLift(chain, { cyclic = true } = {}) {
  array(chain, 'chain', { min: 1 });
  let worst = 0;
  for (let i = 1; i < chain.length; i += 1) {
    worst = Math.max(worst, Math.abs(chain[i] - chain[i - 1]));
  }
  if (cyclic && chain.length > 1) {
    worst = Math.max(worst, Math.abs(chain[0] - chain[chain.length - 1]));
  }
  return worst;
}

/**
 * The runs a loom boxed at one end cannot weave.
 *
 * An odd run leaves its shuttle on the far side. The first run is exempt when
 * the pattern is read as a repeat and the run before it carries the shuttle
 * back, but that is a judgement about the whole chain and not about one run, so
 * every odd run is reported and the caller decides.
 */
export function oddRuns(pattern) {
  return weftRuns(pattern).filter((run) => run.picks % 2 === 1);
}

/** Whether a pattern can be woven on a loom boxed the way it is boxed. */
export function shuttlesReturn(pattern, { boxedAt = 'both' } = {}) {
  oneOf(boxedAt, 'boxedAt', BOXING);
  if (boxedAt === 'both') return true;
  return oddRuns(pattern).length === 0;
}

/** Refuse a pattern the loom's boxing cannot pick, naming the runs at fault. */
export function assertShuttlesReturn(pattern, { boxedAt = 'both' } = {}) {
  if (shuttlesReturn(pattern, { boxedAt })) return pattern;
  const odd = oddRuns(pattern);
  throw new CapacityError('boxed at one end, so every weft must be picked in pairs', {
    boxedAt,
    runs: odd.map((run) => ({ colour: run.colour, start: run.start, picks: run.picks })),
  });
}

/**
 * The picks a repeat loses to box changing.
 *
 * A change is quoted as a fraction of a pick because the motion works in the
 * same crank revolution as the pick it precedes. A tenth is the usual figure
 * for a well set drop box; a worn one costs a good deal more.
 */
export function picksLostToChanging(chain, { picksPerChange = 0.1 } = {}) {
  if (typeof picksPerChange !== 'number' || !(picksPerChange >= 0)) {
    throw new InvalidArgument('picksPerChange must be zero or more', { got: picksPerChange });
  }
  return roundTo(boxChanges(chain) * picksPerChange, 3);
}

/** The running speed a chain leaves, given the loom's quoted speed. */
export function speedWithChanging(chain, { picksMinute, picksPerChange = 0.1 } = {}) {
  positiveInteger(picksMinute, 'picksMinute');
  const lost = picksLostToChanging(chain, { picksPerChange });
  const perRepeat = chain.length;
  return roundTo((picksMinute * perRepeat) / (perRepeat + lost), 1);
}

/** How the picks of a repeat divide between the boxes. */
export function pickShare(allotment) {
  array(allotment, 'allotment', { min: 1 });
  const total = sumBy(allotment, (entry) => entry.picks, 'allotment');
  return tally(allotment, {
    key: (entry) => `box ${entry.box}`,
    label: 'box',
    fields: { picks: (entry) => entry.picks },
  }).map((row) => ({ ...row, share: roundTo(row.picks / total, 4) }));
}

/**
 * Everything a weaver needs to gait a box pattern up: the allotment, the chain
 * to lace, what it costs in speed, and whether the loom can pick it at all.
 */
export function boxMotionPlan({ pattern, boxes, boxedAt = 'both', picksMinute, ...rest }) {
  const unknown = Object.keys(rest);
  if (unknown.length > 0) {
    throw new InvalidArgument(`boxMotionPlan does not take ${unknown.join(', ')}`, { unknown });
  }
  const allotment = allotBoxes(pattern, { boxes });
  assertShuttlesReturn(pattern, { boxedAt });
  const chain = boxChain(pattern, allotment);
  const plan = {
    picks: chain.length,
    colours: allotment.length,
    boxes,
    boxedAt,
    allotment,
    chain,
    changes: boxChanges(chain),
    longestLift: longestLift(chain),
    runs: weftRuns(pattern),
    share: pickShare(allotment),
  };
  if (picksMinute === undefined) return plan;
  return {
    ...plan,
    picksLost: picksLostToChanging(chain),
    picksMinute: speedWithChanging(chain, { picksMinute }),
  };
}

/**
 * Turn a box chain back into the pattern it picks, for reading a chain off a
 * loom that is already gaited and checking it against the order.
 */
export function patternFromChain(chain, allotment) {
  array(chain, 'chain', { min: 1 });
  const byBox = new Map(allotment.map((entry) => [entry.box, entry.colour]));
  return chain.map((box, index) => {
    integer(box, `chain[${index}]`, { min: 1 });
    const colour = byBox.get(box);
    if (colour === undefined) {
      throw new InvalidArgument(`chain[${index}] names box ${box}, which is empty`, { box });
    }
    return colour;
  });
}
