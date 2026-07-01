import { DraftError } from '../util/errors.js';
import { array, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';
import { contentId } from '../util/ids.js';
import { assertMatrix } from '../util/matrix.js';
import { period, rotate, canonicalRotation } from '../util/cycle.js';
import { duplicateGroups } from '../util/indexing.js';
import { setPositions } from '../util/indexing.js';
import { punchCard, cardHooks, holeCount, renderCard, assertCardFitsMachine } from './card.js';

/**
 * The card deck, which the shed calls the chain.
 *
 * Cards are laced end to end in weaving order and hang in a loop over the
 * cylinder, so the deck has no first card in any real sense: it has a starting
 * point, and two decks that differ only in where they were cut are the same
 * pattern. Everything here treats the chain as a loop.
 */

/** Cut a deck from a liftplan, one card per pick. */
export function deckFromLiftplan(machine, liftplan) {
  assertMatrix(liftplan, 'liftplan');
  if (liftplan[0].length > machine.hooks) {
    throw new DraftError('the liftplan is wider than the machine', {
      hooks: machine.hooks,
      width: liftplan[0].length,
    });
  }
  return liftplan.map((row, index) => punchCard(machine, setPositions(row), { number: index + 1 }));
}

/** Cut a deck straight from a list of lifted hooks per pick. */
export function deckFromHookLists(machine, hookLists) {
  array(hookLists, 'hookLists', { min: 1 });
  return hookLists.map((hooks, index) =>
    punchCard(machine, Array.isArray(hooks) ? hooks : [hooks], { number: index + 1 }),
  );
}

/** How many cards the deck holds. */
export function deckLength(deck) {
  array(deck, 'deck', { min: 1 });
  return deck.length;
}

/** The hook lists the deck weaves, in order. */
export function deckHooks(deck, machine, options = {}) {
  array(deck, 'deck', { min: 1 });
  return deck.map((card) => cardHooks(card, machine, options));
}

/** A stable id for the pattern a deck weaves, ignoring where it was cut. */
export function deckId(deck) {
  array(deck, 'deck', { min: 1 });
  return contentId('deck', canonicalRotation(deck.map((card) => renderCard(card))).join('|'));
}

/** How many picks before the chain comes back round to its own beginning. */
export function deckRepeat(deck) {
  array(deck, 'deck', { min: 1 });
  return period(deck.map((card) => renderCard(card)));
}

/** The deck cut down to a single repeat, renumbered from one. */
export function minimalDeck(deck) {
  const cards = deck.slice(0, deckRepeat(deck));
  return renumber(cards);
}

/** Start the chain at a different card, which weaves the same cloth. */
export function rotateDeck(deck, by) {
  array(deck, 'deck', { min: 1 });
  return renumber(rotate(deck, by));
}

/** Two decks weave the same cloth when their patterns match round the loop. */
export function sameDeck(a, b) {
  return deckId(a) === deckId(b);
}

/** Cards punched identically, which a card cutter could have shared. */
export function repeatedCards(deck) {
  array(deck, 'deck', { min: 1 });
  return duplicateGroups(deck, (card) => renderCard(card));
}

/** Number every card in the deck from one, in place order. */
export function renumber(deck) {
  array(deck, 'deck', { min: 1 });
  return deck.map((card, index) => ({ ...card, number: index + 1 }));
}

/** Refuse a deck that a machine could not read. */
export function assertDeckFitsMachine(deck, machine) {
  array(deck, 'deck', { min: 1 });
  for (const [index, card] of deck.entries()) {
    try {
      assertCardFitsMachine(card, machine);
    } catch (err) {
      throw new DraftError(`card ${index + 1} does not fit the machine`, {
        card: index + 1,
        reason: err.message,
      });
    }
  }
  return deck;
}

/** How thick the deck is when it is hanging, and what it weighs. */
export function deckBulk(deck, { cardThicknessMm = 0.6, cardGrams = 18 } = {}) {
  positiveNumber(cardThicknessMm, 'cardThicknessMm');
  positiveNumber(cardGrams, 'cardGrams');
  const cards = deckLength(deck);
  return {
    cards,
    thicknessMm: roundTo(cards * cardThicknessMm, 2),
    kilograms: roundTo((cards * cardGrams) / 1000, 3),
  };
}

/** The total holes cut across the whole deck, which sizes the punching job. */
export function totalHoles(deck) {
  array(deck, 'deck', { min: 1 });
  return sumBy(deck, (card) => holeCount(card), 'deck');
}

/** Extend a deck by repeating it until it covers the picks wanted. */
export function extendDeck(deck, picks) {
  array(deck, 'deck', { min: 1 });
  positiveInteger(picks, 'picks');
  const out = [];
  for (let i = 0; i < picks; i += 1) out.push(deck[i % deck.length]);
  return renumber(out);
}
