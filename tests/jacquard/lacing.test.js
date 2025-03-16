import { describe, it, expect } from 'vitest';
import { NotFound, InvalidArgument } from '../../src/util/errors.js';
import { defineMachine } from '../../src/jacquard/machine.js';
import { punchCard, renderCard } from '../../src/jacquard/card.js';
import { deckFromHookLists, deckHooks } from '../../src/jacquard/deck.js';
import {
  lacingCordMetres,
  lacingKnots,
  lacingPlan,
  spliceAfter,
  cutOut,
  replaceCard,
  cardNumbered,
  joinPositions,
  sectionCount,
} from '../../src/jacquard/lacing.js';

const MACHINE = defineMachine({ name: 'bench', rows: 2, columns: 4 });
const DECK = deckFromHookLists(MACHINE, [[1], [2], [3], [4]]);

describe('lacingCordMetres', () => {
  it('measures both sides and adds a tail for the knots', () => {
    expect(lacingCordMetres(100)).toBe(4);
  });

  it('grows with the chain', () => {
    expect(lacingCordMetres(200)).toBeGreaterThan(lacingCordMetres(100));
  });

  it('takes a single-sided lacing', () => {
    expect(lacingCordMetres(100, { sides: 1 })).toBe(2);
  });

  it('takes a different card pitch', () => {
    expect(lacingCordMetres(100, { pitchMm: 4, allowanceMm: 0 })).toBe(1.6);
  });
});

describe('lacingKnots', () => {
  it('is two knots a side, whatever the length', () => {
    expect(lacingKnots(50)).toBe(4);
    expect(lacingKnots(5000)).toBe(4);
    expect(lacingKnots(50, { sides: 1 })).toBe(2);
  });
});

describe('lacingPlan', () => {
  it('joins each card to the next and closes the loop', () => {
    expect(lacingPlan(3)).toEqual([
      { from: 1, to: 2 },
      { from: 2, to: 3 },
      { from: 3, to: 1 },
    ]);
  });

  it('has one pass per card', () => {
    expect(lacingPlan(120).length).toBe(120);
  });

  it('laces a single card back to itself', () => {
    expect(lacingPlan(1)).toEqual([{ from: 1, to: 1 }]);
  });
});

describe('spliceAfter', () => {
  it('puts new cards in and renumbers what follows', () => {
    const extra = punchCard(MACHINE, [8]);
    const spliced = spliceAfter(DECK, 2, [extra]);
    expect(deckHooks(spliced, MACHINE)).toEqual([[1], [2], [8], [3], [4]]);
    expect(spliced.map((card) => card.number)).toEqual([1, 2, 3, 4, 5]);
  });

  it('splices at the head when told to go after nothing', () => {
    const extra = punchCard(MACHINE, [8]);
    expect(deckHooks(spliceAfter(DECK, 0, [extra]), MACHINE)[0]).toEqual([8]);
  });

  it('splices at the tail', () => {
    const extra = punchCard(MACHINE, [8]);
    const spliced = spliceAfter(DECK, 4, [extra]);
    expect(deckHooks(spliced, MACHINE).at(-1)).toEqual([8]);
  });

  it('refuses a position past the chain', () => {
    expect(() => spliceAfter(DECK, 5, [punchCard(MACHINE, [1])])).toThrow(InvalidArgument);
  });
});

describe('cutOut', () => {
  it('removes the named cards and renumbers', () => {
    const cut = cutOut(DECK, [2, 3]);
    expect(deckHooks(cut, MACHINE)).toEqual([[1], [4]]);
    expect(cut.map((card) => card.number)).toEqual([1, 2]);
  });

  it('ignores a card named twice', () => {
    expect(cutOut(DECK, [2, 2]).length).toBe(3);
  });

  it('refuses to cut the whole chain away', () => {
    expect(() => cutOut(DECK, [1, 2, 3, 4])).toThrow(/whole chain/);
  });

  it('refuses a card the chain has not got', () => {
    expect(() => cutOut(DECK, [9])).toThrow(InvalidArgument);
  });
});

describe('replaceCard', () => {
  it('swaps one card and leaves the rest alone', () => {
    const swapped = replaceCard(DECK, 2, punchCard(MACHINE, [8]));
    expect(deckHooks(swapped, MACHINE)).toEqual([[1], [8], [3], [4]]);
  });

  it('keeps the chain the same length', () => {
    expect(replaceCard(DECK, 1, punchCard(MACHINE, [8])).length).toBe(DECK.length);
  });

  it('renumbers the replacement into its place', () => {
    const swapped = replaceCard(DECK, 3, punchCard(MACHINE, [8], { number: 99 }));
    expect(swapped[2].number).toBe(3);
  });
});

describe('cardNumbered', () => {
  it('finds a card by its number', () => {
    expect(renderCard(cardNumbered(DECK, 2))).toBe(renderCard(DECK[1]));
  });

  it('says plainly when there is no such card', () => {
    expect(() => cardNumbered(DECK, 9)).toThrow(NotFound);
  });
});

describe('joinPositions and sectionCount', () => {
  it('marks a join at the end of every section and at the end of the chain', () => {
    expect(joinPositions(1000, 250)).toEqual([250, 500, 750, 1000]);
  });

  it('leaves a short last section joined where the chain ends', () => {
    expect(joinPositions(900, 400)).toEqual([400, 800, 900]);
    expect(sectionCount(900, 400)).toBe(3);
  });

  it('has one join on a chain shorter than a section', () => {
    expect(joinPositions(100, 250)).toEqual([100]);
    expect(sectionCount(100, 250)).toBe(1);
  });
});
