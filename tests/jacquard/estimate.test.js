import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { defineMachine } from '../../src/jacquard/machine.js';
import { deckFromHookLists, extendDeck } from '../../src/jacquard/deck.js';
import {
  DEFAULT_RATES,
  resolveRates,
  cardsRequired,
  cuttingHours,
  lacingHours,
  quoteDeck,
  deckLogistics,
  punchingShare,
  compareDecks,
} from '../../src/jacquard/estimate.js';

const MACHINE = defineMachine({ name: 'bench', rows: 4, columns: 8 });
const SPARSE = extendDeck(deckFromHookLists(MACHINE, [[1], [2]]), 100);
const DENSE = extendDeck(
  deckFromHookLists(MACHINE, [
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
    [11, 12, 13, 14, 15, 16, 17, 18, 19, 20],
  ]),
  100,
);

describe('resolveRates', () => {
  it('fills in the shop defaults', () => {
    expect(resolveRates()).toEqual(DEFAULT_RATES);
  });

  it('takes an override without losing the rest', () => {
    const rates = resolveRates({ cardPence: 6 });
    expect(rates.cardPence).toBe(6);
    expect(rates.holesPerHour).toBe(DEFAULT_RATES.holesPerHour);
  });

  it('refuses a rate of zero', () => {
    expect(() => resolveRates({ holesPerHour: 0 })).toThrow(InvalidArgument);
  });

  it('leaves the defaults frozen', () => {
    expect(Object.isFrozen(DEFAULT_RATES)).toBe(true);
  });
});

describe('cardsRequired', () => {
  it('multiplies the repeat out and adds wastage', () => {
    expect(cardsRequired({ picksPerRepeat: 400, repeats: 2 })).toEqual({
      pattern: 800,
      spare: 24,
      total: 824,
    });
  });

  it('rounds a fractional spare up to a whole card', () => {
    expect(cardsRequired({ picksPerRepeat: 10 }).spare).toBe(1);
  });

  it('takes a different wastage allowance', () => {
    expect(cardsRequired({ picksPerRepeat: 100 }, { wastage: 0.1 }).spare).toBe(10);
  });
});

describe('cuttingHours and lacingHours', () => {
  it('divide the work by the rate', () => {
    expect(cuttingHours(9000)).toBe(1);
    expect(cuttingHours(4500)).toBe(0.5);
    expect(lacingHours(220)).toBe(1);
  });

  it('follow an overridden rate', () => {
    expect(cuttingHours(9000, { holesPerHour: 4500 })).toBe(2);
  });
});

describe('quoteDeck', () => {
  it('prices stock, cord and labour separately and adds them up', () => {
    const quote = quoteDeck(SPARSE);
    expect(quote.cards).toBe(100);
    expect(quote.holes).toBe(100);
    expect(quote.cardPence).toBe(450);
    expect(quote.totalPence).toBe(
      Math.round((quote.cardPence + quote.cordPence + quote.labourPence) * 100) / 100,
    );
  });

  it('splits the labour into punching and lacing', () => {
    const quote = quoteDeck(SPARSE);
    expect(quote.punchPence + quote.lacePence).toBeCloseTo(quote.labourPence, 2);
  });

  it('takes a cord length rather than guessing one', () => {
    const guessed = quoteDeck(SPARSE);
    const given = quoteDeck(SPARSE, { cordMetres: 20 });
    expect(given.cordMetres).toBe(20);
    expect(given.totalPence).not.toBe(guessed.totalPence);
  });

  it('charges more for a dense deck than a sparse one of the same length', () => {
    expect(quoteDeck(DENSE).totalPence).toBeGreaterThan(quoteDeck(SPARSE).totalPence);
    expect(quoteDeck(DENSE).cards).toBe(quoteDeck(SPARSE).cards);
  });
});

describe('punchingShare', () => {
  it('is a bigger share of a dense deck than a sparse one', () => {
    expect(punchingShare(quoteDeck(DENSE))).toBeGreaterThan(punchingShare(quoteDeck(SPARSE)));
  });

  it('is between nothing and everything', () => {
    const share = punchingShare(quoteDeck(DENSE));
    expect(share).toBeGreaterThan(0);
    expect(share).toBeLessThan(1);
  });
});

describe('deckLogistics', () => {
  it('reports the rail the chain takes up', () => {
    const logistics = deckLogistics(SPARSE);
    expect(logistics.cards).toBe(100);
    expect(logistics.metresOfRail).toBe(0.06);
  });
});

describe('compareDecks', () => {
  it('says which of two decks is cheaper and by how much', () => {
    const comparison = compareDecks(SPARSE, DENSE);
    expect(comparison.cheaper).toBe('left');
    expect(comparison.differencePence).toBeCloseTo(
      comparison.right.totalPence - comparison.left.totalPence,
      2,
    );
  });

  it('calls the left one cheaper on a tie', () => {
    expect(compareDecks(SPARSE, SPARSE).cheaper).toBe('left');
    expect(compareDecks(SPARSE, SPARSE).differencePence).toBe(0);
  });
});
