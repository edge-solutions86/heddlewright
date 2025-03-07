import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import { defineMachine, machineByName } from '../../src/jacquard/machine.js';
import {
  PEG_HOLES,
  LACING_HOLES,
  blankCard,
  punchCard,
  cardHooks,
  holeCount,
  cutAwayRatio,
  assertCardFitsMachine,
  cardSizeMm,
  sameCard,
  isBlank,
  isFullyPunched,
  renderCard,
} from '../../src/jacquard/card.js';

const SMALL = defineMachine({ name: 'bench', rows: 2, columns: 4 });

describe('blankCard', () => {
  it('is the size of the needle board with nothing punched', () => {
    const card = blankCard(SMALL);
    expect(card.rows).toBe(2);
    expect(card.columns).toBe(4);
    expect(holeCount(card)).toBe(0);
    expect(isBlank(card)).toBe(true);
  });

  it('carries its number and the machine it was cut for', () => {
    const card = blankCard(SMALL, { number: 7 });
    expect(card.number).toBe(7);
    expect(card.machine).toBe('bench');
  });

  it('names how many peg and lacing holes a card carries', () => {
    expect(PEG_HOLES).toBe(2);
    expect(LACING_HOLES).toBe(2);
  });
});

describe('punchCard', () => {
  it('punches a hole at each named hook, reading along the rows', () => {
    expect(renderCard(punchCard(SMALL, [1, 4, 5]))).toBe('O..O\nO...');
  });

  it('punches nothing for an empty hook list', () => {
    expect(isBlank(punchCard(SMALL, []))).toBe(true);
  });

  it('ignores a hook named twice', () => {
    expect(holeCount(punchCard(SMALL, [3, 3]))).toBe(1);
  });

  it('refuses a hook past the machine', () => {
    expect(() => punchCard(SMALL, [9])).toThrow(CapacityError);
    expect(() => punchCard(SMALL, [0])).toThrow(InvalidArgument);
  });
});

describe('cardHooks', () => {
  it('reads the punched hooks back in machine order', () => {
    expect(cardHooks(punchCard(SMALL, [5, 1, 4]), SMALL)).toEqual([1, 4, 5]);
  });

  it('round-trips any hook set', () => {
    for (const hooks of [[1], [2, 6], [1, 2, 3, 4, 5, 6, 7, 8], []]) {
      expect(cardHooks(punchCard(SMALL, hooks), SMALL)).toEqual([...hooks].sort((a, b) => a - b));
    }
  });

  it('reads the other way for a machine where solid card lifts', () => {
    const card = punchCard(SMALL, [1]);
    expect(cardHooks(card, SMALL, { holeMeansLift: false })).toEqual([2, 3, 4, 5, 6, 7, 8]);
  });

  it('refuses a card cut for a different machine', () => {
    expect(() => cardHooks(blankCard(SMALL), machineByName('400 hook'))).toThrow(InvalidArgument);
  });
});

describe('cutAwayRatio', () => {
  it('is the share of the card that has been removed', () => {
    expect(cutAwayRatio(punchCard(SMALL, [1, 2]))).toBe(0.25);
    expect(cutAwayRatio(blankCard(SMALL))).toBe(0);
  });

  it('is one on a card punched right through', () => {
    const everything = punchCard(SMALL, [1, 2, 3, 4, 5, 6, 7, 8]);
    expect(cutAwayRatio(everything)).toBe(1);
    expect(isFullyPunched(everything)).toBe(true);
  });
});

describe('assertCardFitsMachine', () => {
  it('passes a card cut for the machine', () => {
    const card = blankCard(SMALL);
    expect(assertCardFitsMachine(card, SMALL)).toBe(card);
  });

  it('catches a card whose stated size disagrees with its own grid', () => {
    const card = blankCard(SMALL);
    card.columns = 5;
    expect(() => assertCardFitsMachine(card, SMALL)).toThrow(/does not fit that machine/);
  });

  it('catches a grid that has been resized behind the card record', () => {
    const card = blankCard(SMALL);
    card.holes = [[0, 0, 0, 0]];
    expect(() => assertCardFitsMachine(card, SMALL)).toThrow(/does not match its own stated size/);
  });
});

describe('cardSizeMm', () => {
  it('works the card out from the needle pitch plus its margins', () => {
    const size = cardSizeMm(machineByName('400 hook'));
    expect(size.width).toBe(50 * 8 + 24);
    expect(size.height).toBe(8 * 8 + 24);
    expect(size.pitchMm).toBe(8);
  });

  it('takes a different margin', () => {
    expect(cardSizeMm(SMALL, { marginMm: 0 }).width).toBe(32);
  });
});

describe('sameCard', () => {
  it('is true for two cards punched the same way', () => {
    expect(sameCard(punchCard(SMALL, [1, 5]), punchCard(SMALL, [5, 1]))).toBe(true);
  });

  it('ignores the card number, which is bookkeeping rather than pattern', () => {
    expect(
      sameCard(punchCard(SMALL, [1], { number: 1 }), punchCard(SMALL, [1], { number: 9 })),
    ).toBe(true);
  });

  it('is false once a hole moves', () => {
    expect(sameCard(punchCard(SMALL, [1]), punchCard(SMALL, [2]))).toBe(false);
  });
});
