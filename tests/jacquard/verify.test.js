import { describe, it, expect } from 'vitest';
import { DraftError } from '../../src/util/errors.js';
import { defineMachine } from '../../src/jacquard/machine.js';
import { blankCard, punchCard } from '../../src/jacquard/card.js';
import { deckFromHookLists } from '../../src/jacquard/deck.js';
import {
  blankCards,
  fullCards,
  weakCards,
  misfitCards,
  idleHooks,
  wastedCards,
  verifyDeck,
  assertDeckSound,
  savingFromRepeat,
} from '../../src/jacquard/verify.js';

const MACHINE = defineMachine({ name: 'bench', rows: 2, columns: 4 });
const OTHER = defineMachine({ name: 'wide bench', rows: 2, columns: 6 });
const GOOD = deckFromHookLists(MACHINE, [
  [1, 2],
  [3, 4],
  [5, 6],
  [7, 8],
]);

describe('blankCards', () => {
  it('finds a card that lifts nothing', () => {
    const deck = [...GOOD, blankCard(MACHINE)];
    expect(blankCards(deck)).toEqual([
      { code: 'blank_card', card: 5, message: 'card 5 lifts nothing' },
    ]);
  });

  it('is quiet on a deck where every card lifts something', () => {
    expect(blankCards(GOOD)).toEqual([]);
  });
});

describe('fullCards', () => {
  it('finds a card cut right through', () => {
    const deck = [...GOOD, punchCard(MACHINE, [1, 2, 3, 4, 5, 6, 7, 8])];
    expect(fullCards(deck).map((finding) => finding.card)).toEqual([5]);
  });

  it('is quiet otherwise', () => {
    expect(fullCards(GOOD)).toEqual([]);
  });
});

describe('weakCards', () => {
  it('finds a card with too much cut away, without counting a full one twice', () => {
    const deck = [punchCard(MACHINE, [1, 2, 3, 4, 5, 6, 7])];
    expect(weakCards(deck).map((finding) => finding.card)).toEqual([1]);
    expect(fullCards(deck)).toEqual([]);
  });

  it('takes a different limit', () => {
    const deck = [punchCard(MACHINE, [1, 2, 3, 4, 5])];
    expect(weakCards(deck)).toEqual([]);
    expect(weakCards(deck, { maxCutAway: 0.5 }).length).toBe(1);
  });
});

describe('misfitCards', () => {
  it('finds a card cut for a different machine', () => {
    const deck = [...GOOD, blankCard(OTHER)];
    const findings = misfitCards(deck, MACHINE);
    expect(findings.length).toBe(1);
    expect(findings[0].card).toBe(5);
  });

  it('is quiet on a deck cut for the machine it is going on', () => {
    expect(misfitCards(GOOD, MACHINE)).toEqual([]);
  });
});

describe('idleHooks', () => {
  it('names hooks no card ever lifts', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2]]);
    expect(idleHooks(deck, MACHINE)).toEqual([3, 4, 5, 6, 7, 8]);
  });

  it('is empty when the deck reaches every hook', () => {
    expect(idleHooks(GOOD, MACHINE)).toEqual([]);
  });
});

describe('wastedCards', () => {
  it('finds a deck cut longer than its own repeat', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [1], [2]]);
    const findings = wastedCards(deck);
    expect(findings.length).toBe(1);
    expect(findings[0].message).toContain('repeats after 2 cards');
  });

  it('is quiet on a deck that is its own repeat', () => {
    expect(wastedCards(GOOD)).toEqual([]);
  });
});

describe('verifyDeck', () => {
  it('reads a sound deck as sound', () => {
    const report = verifyDeck(GOOD, MACHINE);
    expect(report.sound).toBe(true);
    expect(report.cards).toBe(4);
    expect(report.idleHooks).toEqual([]);
  });

  it('gathers every finding in card order', () => {
    const deck = [...GOOD, blankCard(MACHINE), punchCard(MACHINE, [1, 2, 3, 4, 5, 6, 7, 8])];
    const report = verifyDeck(deck, MACHINE);
    expect(report.sound).toBe(false);
    expect(report.findings.map((finding) => finding.card)).toEqual([5, 6]);
  });

  it('counts the cards a cutter punched twice', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [1]]);
    expect(verifyDeck(deck, MACHINE).repeatedCards).toBe(1);
  });
});

describe('assertDeckSound', () => {
  it('returns the report for a sound deck', () => {
    expect(assertDeckSound(GOOD, MACHINE).sound).toBe(true);
  });

  it('throws with every finding attached', () => {
    const deck = [...GOOD, blankCard(MACHINE)];
    try {
      assertDeckSound(deck, MACHINE);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.findings.length).toBe(1);
    }
  });
});

describe('savingFromRepeat', () => {
  it('prices the cards a shorter deck would save', () => {
    const deck = deckFromHookLists(MACHINE, [[1], [2], [1], [2]]);
    expect(savingFromRepeat(deck)).toEqual({ spareCards: 2, pence: 9 });
  });

  it('saves nothing on a deck that is already its own repeat', () => {
    expect(savingFromRepeat(GOOD)).toEqual({ spareCards: 0, pence: 0 });
  });
});
