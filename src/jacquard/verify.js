import { DraftError } from '../util/errors.js';
import { array, integer, plainObject } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { holeCount, cardHooks, cutAwayRatio, assertCardFitsMachine } from './card.js';
import { deckLength, repeatedCards, deckRepeat } from './deck.js';

/**
 * Checking a deck before it goes on the machine.
 *
 * Cards are cut once and hang for years, so every fault worth catching is worth
 * catching before the deck is laced. Each check returns findings rather than
 * throwing, because a deck usually has several things slightly wrong with it
 * and a card cutter wants the whole list, not the first one.
 */

/** A card that lifts nothing leaves the shed shut on that pick. */
export function blankCards(deck) {
  array(deck, 'deck', { min: 1 });
  return deck
    .map((card, index) => ({ card: index + 1, holes: holeCount(card) }))
    .filter((entry) => entry.holes === 0)
    .map((entry) => ({
      code: 'blank_card',
      card: entry.card,
      message: `card ${entry.card} lifts nothing`,
    }));
}

/** A card cut right through lifts everything, which also opens no shed. */
export function fullCards(deck) {
  array(deck, 'deck', { min: 1 });
  return deck
    .map((card, index) => ({ card: index + 1, ratio: cutAwayRatio(card) }))
    .filter((entry) => entry.ratio === 1)
    .map((entry) => ({
      code: 'full_card',
      card: entry.card,
      message: `card ${entry.card} lifts every hook`,
    }));
}

/**
 * A card with too much cut away tears at the lacing. The share is a shed
 * setting rather than a law, so it comes in as an option.
 */
export function weakCards(deck, { maxCutAway = 0.85 } = {}) {
  array(deck, 'deck', { min: 1 });
  return deck
    .map((card, index) => ({ card: index + 1, ratio: cutAwayRatio(card) }))
    .filter((entry) => entry.ratio > maxCutAway && entry.ratio < 1)
    .map((entry) => ({
      code: 'weak_card',
      card: entry.card,
      message: `card ${entry.card} has ${roundTo(entry.ratio * 100, 1)} per cent cut away`,
    }));
}

/** Cards cut for a different machine, which will not feed at all. */
export function misfitCards(deck, machine) {
  plainObject(machine, 'machine');
  const findings = [];
  for (const [index, card] of deck.entries()) {
    try {
      assertCardFitsMachine(card, machine);
    } catch (err) {
      findings.push({
        code: 'misfit_card',
        card: index + 1,
        message: `card ${index + 1} does not fit ${machine.name}: ${err.message}`,
      });
    }
  }
  return findings;
}

/**
 * A hook that no card in the deck ever lifts. Its cords could come out of the
 * comber board, and if the design meant to use it, something is wrong with the
 * cutting rather than with the machine.
 */
export function idleHooks(deck, machine) {
  const lifted = new Set();
  for (const card of deck) {
    for (const hook of cardHooks(card, machine)) lifted.add(hook);
  }
  const out = [];
  for (let hook = 1; hook <= machine.hooks; hook += 1) {
    if (!lifted.has(hook)) out.push(hook);
  }
  return out;
}

/** A deck that repeats sooner than it is long has cards nobody needed to cut. */
export function wastedCards(deck) {
  const repeat = deckRepeat(deck);
  const length = deckLength(deck);
  if (repeat === length) return [];
  return [
    {
      code: 'wasted_cards',
      card: repeat + 1,
      message: `the pattern repeats after ${repeat} cards but the deck is ${length} long`,
    },
  ];
}

/** Every check, gathered. */
export function verifyDeck(deck, machine, options = {}) {
  const findings = [
    ...misfitCards(deck, machine),
    ...blankCards(deck),
    ...fullCards(deck),
    ...weakCards(deck, options),
    ...wastedCards(deck),
  ];
  return {
    cards: deckLength(deck),
    findings: findings.sort((a, b) => a.card - b.card || a.code.localeCompare(b.code)),
    idleHooks: idleHooks(deck, machine),
    repeatedCards: repeatedCards(deck).length,
    sound: findings.length === 0,
  };
}

/** Refuse a deck that will not run, naming everything wrong with it. */
export function assertDeckSound(deck, machine, options = {}) {
  const report = verifyDeck(deck, machine, options);
  if (!report.sound) {
    throw new DraftError('the deck will not run as it stands', {
      cards: report.cards,
      findings: report.findings,
    });
  }
  return report;
}

/** How much card stock a deck would save if its repeat were cut instead. */
export function savingFromRepeat(deck, { cardPence = 4.5 } = {}) {
  integer(deckLength(deck), 'cards', { min: 1 });
  const spare = deckLength(deck) - deckRepeat(deck);
  return { spareCards: spare, pence: roundTo(spare * cardPence, 2) };
}
