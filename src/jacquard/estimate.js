import { positiveInteger, positiveNumber, plainObject } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { deckLength, totalHoles, deckBulk } from './deck.js';

/**
 * Costing a card cutting job.
 *
 * Cutting a deck is priced on three things and they pull in different
 * directions: the cards themselves, the holes punched in them, and the lacing.
 * A large sparse design has many cards and few holes; a small dense one is the
 * other way round, and a shop that quotes on card count alone loses money on
 * the second kind.
 */

export const DEFAULT_RATES = Object.freeze({
  cardPence: 4.5,
  holesPerHour: 9000,
  lacingMetresPerHour: 220,
  labourPencePerHour: 1850,
  cordPencePerMetre: 3,
  wastage: 0.03,
});

/** Merge caller rates over the defaults, checking each one it is given. */
export function resolveRates(rates = {}) {
  plainObject(rates, 'rates');
  const merged = { ...DEFAULT_RATES, ...rates };
  positiveNumber(merged.cardPence, 'cardPence');
  positiveNumber(merged.holesPerHour, 'holesPerHour');
  positiveNumber(merged.lacingMetresPerHour, 'lacingMetresPerHour');
  positiveNumber(merged.labourPencePerHour, 'labourPencePerHour');
  positiveNumber(merged.cordPencePerMetre, 'cordPencePerMetre');
  return merged;
}

/** How many cards a piece needs, and how many spare to cut for wastage. */
export function cardsRequired({ picksPerRepeat, repeats = 1 }, rates = {}) {
  positiveInteger(picksPerRepeat, 'picksPerRepeat');
  positiveInteger(repeats, 'repeats');
  const merged = resolveRates(rates);
  const pattern = picksPerRepeat * repeats;
  const spare = Math.ceil(pattern * merged.wastage);
  return { pattern, spare, total: pattern + spare };
}

/** The hours a punch takes to cut a given number of holes. */
export function cuttingHours(holes, rates = {}) {
  positiveInteger(holes, 'holes');
  return roundTo(holes / resolveRates(rates).holesPerHour, 3);
}

/** The hours the lacing takes, from the cord it uses. */
export function lacingHours(cordMetres, rates = {}) {
  positiveNumber(cordMetres, 'cordMetres');
  return roundTo(cordMetres / resolveRates(rates).lacingMetresPerHour, 3);
}

/**
 * A whole quotation for a deck: card stock, punching, lacing, and the labour on
 * top, all in pence so nothing is lost rounding to a currency.
 */
export function quoteDeck(deck, { cordMetres, rates = {} } = {}) {
  const merged = resolveRates(rates);
  const cards = deckLength(deck);
  const holes = totalHoles(deck);
  const cord = cordMetres ?? cards * 0.032;
  const punchHours = cuttingHours(holes, merged);
  const laceHours = lacingHours(cord, merged);
  const cardCost = cards * merged.cardPence;
  const cordCost = cord * merged.cordPencePerMetre;
  const punchCost = punchHours * merged.labourPencePerHour;
  const laceCost = laceHours * merged.labourPencePerHour;
  return {
    cards,
    holes,
    cordMetres: roundTo(cord, 3),
    punchHours,
    laceHours,
    cardPence: roundTo(cardCost, 2),
    cordPence: roundTo(cordCost, 2),
    punchPence: roundTo(punchCost, 2),
    lacePence: roundTo(laceCost, 2),
    labourPence: roundTo(punchCost + laceCost, 2),
    totalPence: roundTo(cardCost + cordCost + punchCost + laceCost, 2),
  };
}

/** What the finished deck weighs and how much rail it takes up. */
export function deckLogistics(deck, options = {}) {
  const bulk = deckBulk(deck, options);
  return {
    ...bulk,
    metresOfRail: roundTo(bulk.thicknessMm / 1000, 3),
  };
}

/**
 * The share of the quotation that is punching rather than stock, which is the
 * number that says whether a design is dense or merely long.
 */
export function punchingShare(quote) {
  if (quote.totalPence === 0) return 0;
  return roundTo(quote.punchPence / quote.totalPence, 4);
}

/** Compare two decks on the same rates, for choosing between two designs. */
export function compareDecks(a, b, options = {}) {
  const left = quoteDeck(a, options);
  const right = quoteDeck(b, options);
  return {
    left,
    right,
    cheaper: left.totalPence <= right.totalPence ? 'left' : 'right',
    differencePence: roundTo(Math.abs(left.totalPence - right.totalPence), 2),
  };
}
