import { NotFound, InvalidArgument } from '../util/errors.js';
import { array, integer, number, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { renumber } from './deck.js';

/**
 * Lacing, and the repairs that go with it.
 *
 * Cards are strung together through the lacing holes at each end, cord over and
 * cord under, and the last card is laced back to the first so the chain runs
 * round the cylinder without an end. Cards wear out, patterns get altered, and
 * a card is spliced in or cut out of a live chain far more often than a whole
 * deck is cut fresh, so those operations belong here.
 */

/** How much cord a chain needs, both sides, with a tail for the knots. */
export function lacingCordMetres(cards, { pitchMm = 8, sides = 2, allowanceMm = 400 } = {}) {
  positiveInteger(cards, 'cards');
  positiveNumber(pitchMm, 'pitchMm');
  positiveInteger(sides, 'sides');
  number(allowanceMm, 'allowanceMm', { min: 0 });
  // Each card takes two passes of cord per side: one down and one back.
  const perSide = cards * pitchMm * 2 + allowanceMm;
  return roundTo((perSide * sides) / 1000, 3);
}

/** The knots a lacing needs, counting the one that closes the loop. */
export function lacingKnots(cards, { sides = 2 } = {}) {
  positiveInteger(cards, 'cards');
  positiveInteger(sides, 'sides');
  return sides * 2;
}

/**
 * Where the cord passes, as a list of `{ from, to }` card pairs. The chain is a
 * loop, so the last pair joins the last card back to the first.
 */
export function lacingPlan(cards) {
  positiveInteger(cards, 'cards');
  const plan = [];
  for (let card = 1; card <= cards; card += 1) {
    plan.push({ from: card, to: (card % cards) + 1 });
  }
  return plan;
}

/** Splice cards into a live chain after the given card number. */
export function spliceAfter(deck, afterCard, cards) {
  array(deck, 'deck', { min: 1 });
  integer(afterCard, 'afterCard', { min: 0, max: deck.length });
  array(cards, 'cards', { min: 1 });
  return renumber([...deck.slice(0, afterCard), ...cards, ...deck.slice(afterCard)]);
}

/** Cut cards out of a chain by their 1-based numbers. */
export function cutOut(deck, numbers) {
  array(deck, 'deck', { min: 1 });
  array(numbers, 'numbers', { min: 1 });
  const drop = new Set();
  for (const [i, number] of numbers.entries()) {
    integer(number, `numbers[${i}]`, { min: 1, max: deck.length });
    drop.add(number);
  }
  if (drop.size === deck.length) {
    throw new InvalidArgument('that would cut the whole chain away', { cards: deck.length });
  }
  return renumber(deck.filter((_, index) => !drop.has(index + 1)));
}

/** Swap one card for another, keeping the rest of the chain as it is. */
export function replaceCard(deck, number, card) {
  array(deck, 'deck', { min: 1 });
  integer(number, 'number', { min: 1, max: deck.length });
  return renumber(deck.map((existing, index) => (index + 1 === number ? card : existing)));
}

/** Find a card by number, or say plainly that the chain has no such card. */
export function cardNumbered(deck, number) {
  array(deck, 'deck', { min: 1 });
  const found = deck.find((card) => card.number === number);
  if (found === undefined) {
    throw new NotFound(`the chain has no card ${number}`, { number, cards: deck.length });
  }
  return found;
}

/**
 * The join is where two lengths of chain meet. A chain laced from several
 * sections carries one join per section, and they are the first thing to check
 * when a chain jams on the cylinder.
 */
export function joinPositions(cards, sectionLength) {
  positiveInteger(cards, 'cards');
  positiveInteger(sectionLength, 'sectionLength');
  const joins = [];
  for (let at = sectionLength; at < cards; at += sectionLength) joins.push(at);
  joins.push(cards);
  return joins;
}

/** How many sections a chain of this length is laced from. */
export function sectionCount(cards, sectionLength) {
  positiveInteger(cards, 'cards');
  positiveInteger(sectionLength, 'sectionLength');
  return Math.ceil(cards / sectionLength);
}
