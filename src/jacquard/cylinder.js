import { InvalidArgument } from '../util/errors.js';
import { integer, oneOf, positiveInteger, positiveNumber } from '../util/check.js';
import { mod, roundTo } from '../util/numeric.js';
import { assertFits } from '../util/limits.js';

/**
 * The cylinder, and how the chain runs over it.
 *
 * The cylinder is a square prism that turns a quarter at a time, bringing the
 * next card against the needle board. Pegs on its faces drive the cards along,
 * and the chain hangs in a loop underneath. Because it turns rather than
 * slides, the chain can also be run backwards to pick a lost pick up again, and
 * getting that arithmetic right is what stops a mispick becoming a fault.
 */

export const CYLINDER_FACES = 4;
export const DIRECTIONS = ['forward', 'reverse'];

/** Which face of the cylinder card `number` lands on, counting from face one. */
export function faceForCard(number, { faces = CYLINDER_FACES } = {}) {
  positiveInteger(number, 'number');
  positiveInteger(faces, 'faces');
  return mod(number - 1, faces) + 1;
}

/** How far the cylinder has turned after a run of picks, in degrees. */
export function turnAngle(picks, { faces = CYLINDER_FACES } = {}) {
  integer(picks, 'picks', { min: 0 });
  positiveInteger(faces, 'faces');
  return mod((picks * 360) / faces, 360);
}

/**
 * Where the chain stands after weaving. `at` is the 1-based card next against
 * the board, and it wraps because the chain is a loop.
 */
export function advance(at, picks, cards, { direction = 'forward' } = {}) {
  positiveInteger(at, 'at');
  integer(picks, 'picks', { min: 0 });
  positiveInteger(cards, 'cards');
  oneOf(direction, 'direction', DIRECTIONS);
  if (at > cards) {
    throw new InvalidArgument('the chain has no such card', { at, cards });
  }
  const step = direction === 'forward' ? picks : -picks;
  return mod(at - 1 + step, cards) + 1;
}

/** How many picks it takes to get from one card to another going forwards. */
export function picksBetween(from, to, cards) {
  positiveInteger(from, 'from');
  positiveInteger(to, 'to');
  positiveInteger(cards, 'cards');
  return mod(to - from, cards);
}

/**
 * Backing the loom off after a broken pick: how far to reverse the chain so the
 * next card is the one that should have been woven.
 */
export function backOff(at, picksToUndo, cards) {
  return advance(at, picksToUndo, cards, { direction: 'reverse' });
}

/** How many full turns of the chain a length of cloth takes. */
export function chainRevolutions(picks, cards) {
  integer(picks, 'picks', { min: 0 });
  positiveInteger(cards, 'cards');
  return { whole: Math.floor(picks / cards), remainder: picks % cards };
}

/**
 * A cylinder can only carry so much chain before the loop drags on the floor.
 * The limit is set by how much card the machine's chain rail will hold.
 */
export function assertChainFits(cards, { railCapacity }) {
  positiveInteger(cards, 'cards');
  positiveInteger(railCapacity, 'railCapacity');
  assertFits(cards, railCapacity, {
    what: 'chain',
    has: 'the chain is longer than the rail will carry',
    detail: { cards, railCapacity },
  });
  return cards;
}

/** How long a pattern runs on the cloth before it repeats, in centimetres. */
export function patternLengthCm(cards, picksPerCm) {
  positiveInteger(cards, 'cards');
  positiveNumber(picksPerCm, 'picksPerCm');
  return roundTo(cards / picksPerCm, 3);
}

/** How many cards a length of cloth needs at a given pick density. */
export function cardsForLength(lengthCm, picksPerCm) {
  positiveNumber(lengthCm, 'lengthCm');
  positiveNumber(picksPerCm, 'picksPerCm');
  return Math.ceil(lengthCm * picksPerCm);
}
