import { DraftError } from '../util/errors.js';
import { array, integer, positiveInteger } from '../util/check.js';
import { createMatrix } from '../util/matrix.js';

/**
 * Leno and its imitation.
 *
 * True leno crosses one warp end over another between picks, and no
 * arrangement of shafts can do that on its own: it needs a doup, a half heddle
 * that carries the crossing end over its neighbour. What a shaft loom can do is
 * mock leno, which opens holes by putting tight interlacement next to floats so
 * that the threads gather away from each other. It is not gauze and it does not
 * hold like gauze, but it looks like it and it weaves on four shafts.
 */

/**
 * One repeat of mock leno. Ends and picks are grouped, and a group that meets
 * itself interlaces plainly while a group meeting another floats, so the
 * threads pull together into bundles and leave a hole at each corner.
 */
export function mockLeno(unit = 2) {
  positiveInteger(unit, 'unit');
  if (unit < 2) {
    throw new DraftError('a mock leno group has to be at least two threads', { unit });
  }
  const size = unit * 2;
  const repeat = createMatrix(size, size);
  for (let p = 0; p < size; p += 1) {
    for (let e = 0; e < size; e += 1) {
      const sameGroup = Math.floor(p / unit) === Math.floor(e / unit);
      repeat[p][e] = sameGroup ? (p + e) % 2 : 1;
    }
  }
  return repeat;
}

/** Where the holes fall, as the 1-based end and pick of each corner. */
export function holePositions(unit = 2) {
  positiveInteger(unit, 'unit');
  const size = unit * 2;
  return [
    { end: unit, pick: unit },
    { end: size, pick: size },
  ];
}

/**
 * A doup plan for true leno: which end crosses and which it crosses over. The
 * crossing end is called the doup end and the other the standard end.
 */
export function doupPlan(pairs) {
  array(pairs, 'pairs', { min: 1 });
  return pairs.map((pair, index) => {
    const doup = integer(pair.doup, `pairs[${index}].doup`, { min: 1 });
    const standard = integer(pair.standard, `pairs[${index}].standard`, { min: 1 });
    if (doup === standard) {
      throw new DraftError('an end cannot cross over itself', { at: index + 1, end: doup });
    }
    return { doup, standard, side: doup < standard ? 'left' : 'right' };
  });
}

/** The ends a doup plan needs threaded through the half heddles. */
export function doupEnds(plan) {
  return plan.map((pair) => pair.doup).sort((a, b) => a - b);
}

/**
 * A doup end may only cross over one standard end, and only if nothing else
 * claims it. Two doups sharing a standard end lock the shed shut.
 */
export function doupConflicts(plan) {
  const claimed = new Map();
  const findings = [];
  for (const [index, pair] of plan.entries()) {
    if (claimed.has(pair.standard)) {
      findings.push({
        code: 'shared_standard',
        at: index + 1,
        end: pair.standard,
        message: `end ${pair.standard} is crossed by two doups`,
      });
    }
    claimed.set(pair.standard, index + 1);
  }
  const doups = new Set(doupEnds(plan));
  for (const pair of plan) {
    if (doups.has(pair.standard)) {
      findings.push({
        code: 'doup_as_standard',
        end: pair.standard,
        message: `end ${pair.standard} is both a doup and a standard end`,
      });
    }
  }
  return findings;
}

/** Refuse a doup plan that will not open. */
export function assertDoupPlanSound(plan) {
  const findings = doupConflicts(plan);
  if (findings.length > 0) {
    throw new DraftError('that doup plan will not open a shed', { findings });
  }
  return plan;
}

/** How many ends a leno stripe takes, doup ends included. */
export function endsForLenoStripe({ pairs, repeats = 1 }) {
  positiveInteger(pairs, 'pairs');
  positiveInteger(repeats, 'repeats');
  return pairs * 2 * repeats;
}

/** True when a repeat could be woven on shafts alone, without a doup. */
export function weavableOnShafts(repeat) {
  array(repeat, 'repeat', { min: 1 });
  return repeat.every((row) => row.some((cell) => cell === 1) || row.every((cell) => cell === 0));
}
