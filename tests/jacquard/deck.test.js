import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix } from '../../src/util/matrix.js';
import { defineMachine } from '../../src/jacquard/machine.js';
import { blankCard, renderCard } from '../../src/jacquard/card.js';
import {
  deckFromLiftplan,
  deckFromHookLists,
  deckLength,
  deckHooks,
  deckId,
  deckRepeat,
  minimalDeck,
  rotateDeck,
  sameDeck,
  repeatedCards,
  renumber,
  assertDeckFitsMachine,
  deckBulk,
  totalHoles,
  extendDeck,
} from '../../src/jacquard/deck.js';

const MACHINE = defineMachine({ name: 'bench', rows: 2, columns: 4 });
const OTHER = defineMachine({ name: 'wide bench', rows: 2, columns: 6 });
const PLAN = parseMatrix('X.X.X.X.\n.X.X.X.X');

describe('deckFromLiftplan', () => {
  it('cuts one card per pick and numbers them in weaving order', () => {
    const deck = deckFromLiftplan(MACHINE, PLAN);
    expect(deckLength(deck)).toBe(2);
    expect(deck[0].number).toBe(1);
    expect(deck[1].number).toBe(2);
  });

  it('punches the hooks the plan lifts', () => {
    const deck = deckFromLiftplan(MACHINE, PLAN);
    expect(deckHooks(deck, MACHINE)).toEqual([
      [1, 3, 5, 7],
      [2, 4, 6, 8],
    ]);
  });

  it('refuses a plan wider than the machine', () => {
    expect(() => deckFromLiftplan(MACHINE, parseMatrix('X.X.X.X.X.'))).toThrow(DraftError);
  });
});

describe('deckFromHookLists', () => {
  it('takes the lifted hooks directly', () => {
    const deck = deckFromHookLists(MACHINE, [[1, 2], [3]]);
    expect(deckHooks(deck, MACHINE)).toEqual([[1, 2], [3]]);
  });

  it('takes a bare number for a single hook', () => {
    expect(deckHooks(deckFromHookLists(MACHINE, [1, 2]), MACHINE)).toEqual([[1], [2]]);
  });
});

describe('deckRepeat and minimalDeck', () => {
  it('finds the shortest chain that weaves the same cloth', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [1], [2]]);
    expect(deckRepeat(deck)).toBe(2);
    expect(deckLength(minimalDeck(deck))).toBe(2);
  });

  it('is the whole deck when nothing repeats', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [3]]);
    expect(deckRepeat(deck)).toBe(3);
  });

  it('renumbers the cut deck from one', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [1], [2]]);
    expect(minimalDeck(deck).map((card) => card.number)).toEqual([1, 2]);
  });
});

describe('rotateDeck', () => {
  it('starts the chain elsewhere and renumbers', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [3]]);
    const moved = rotateDeck(deck, 1);
    expect(deckHooks(moved, MACHINE)).toEqual([[2], [3], [1]]);
    expect(moved.map((card) => card.number)).toEqual([1, 2, 3]);
  });

  it('does not change what the chain weaves', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [3]]);
    expect(sameDeck(deck, rotateDeck(deck, 2))).toBe(true);
  });
});

describe('deckId', () => {
  it('is the same wherever the chain was cut', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [3], [4]]);
    for (const by of [0, 1, 2, 3]) {
      expect(deckId(rotateDeck(deck, by))).toBe(deckId(deck));
    }
  });

  it('separates chains that weave different cloth', () => {
    const a = deckFromHookLists(MACHINE, [[1], [2]]);
    const b = deckFromHookLists(MACHINE, [[1], [3]]);
    expect(deckId(a)).not.toBe(deckId(b));
  });

  it('reads as a deck prefix and a hash', () => {
    expect(deckId(deckFromHookLists(MACHINE, [[1]]))).toMatch(/^deck-[0-9a-f]{8}$/);
  });

  it('is not fooled by a reversed chain', () => {
    const a = deckFromHookLists(MACHINE, [[1], [2], [3]]);
    const b = deckFromHookLists(MACHINE, [[3], [2], [1]]);
    expect(sameDeck(a, b)).toBe(false);
  });
});

describe('repeatedCards', () => {
  it('groups cards a cutter punched the same way', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [1], [3]]);
    expect(repeatedCards(deck)).toEqual([[1, 3]]);
  });

  it('is empty when every card differs', () => {
    expect(repeatedCards(deckFromHookLists(MACHINE, [[1], [2]]))).toEqual([]);
  });
});

describe('renumber', () => {
  it('numbers from one without touching the holes', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2]]);
    const shuffled = renumber([deck[1], deck[0]]);
    expect(shuffled.map((card) => card.number)).toEqual([1, 2]);
    expect(renderCard(shuffled[0])).toBe(renderCard(deck[1]));
  });
});

describe('assertDeckFitsMachine', () => {
  it('passes a deck cut for the machine', () => {
    const deck = deckFromHookLists(MACHINE, [[1]]);
    expect(assertDeckFitsMachine(deck, MACHINE)).toBe(deck);
  });

  it('names the card that does not fit', () => {
    const deck = [...deckFromHookLists(MACHINE, [[1], [2]]), blankCard(OTHER)];
    try {
      assertDeckFitsMachine(deck, MACHINE);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.card).toBe(3);
    }
  });
});

describe('deckBulk', () => {
  it('measures the hanging chain', () => {
    const deck = extendDeck(deckFromHookLists(MACHINE, [[1], [2]]), 500);
    expect(deckBulk(deck)).toEqual({ cards: 500, thicknessMm: 300, kilograms: 9 });
  });

  it('takes a different card stock', () => {
    const deck = extendDeck(deckFromHookLists(MACHINE, [[1]]), 100);
    expect(deckBulk(deck, { cardThicknessMm: 1, cardGrams: 25 })).toEqual({
      cards: 100,
      thicknessMm: 100,
      kilograms: 2.5,
    });
  });
});

describe('totalHoles', () => {
  it('adds the holes across every card, which is the punching job', () => {
    expect(totalHoles(deckFromHookLists(MACHINE, [[1, 2], [3]]))).toBe(3);
  });
});

describe('extendDeck', () => {
  it('repeats the chain to the picks wanted', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2]]);
    expect(deckHooks(extendDeck(deck, 5), MACHINE)).toEqual([[1], [2], [1], [2], [1]]);
  });

  it('renumbers the extended chain', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2]]);
    expect(extendDeck(deck, 3).map((card) => card.number)).toEqual([1, 2, 3]);
  });

  it('refuses a pick count of zero', () => {
    expect(() => extendDeck(deckFromHookLists(MACHINE, [[1]]), 0)).toThrow(InvalidArgument);
  });
});
