import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  CYLINDER_FACES,
  DIRECTIONS,
  faceForCard,
  turnAngle,
  advance,
  picksBetween,
  backOff,
  chainRevolutions,
  assertChainFits,
  patternLengthCm,
  cardsForLength,
} from '../../src/jacquard/cylinder.js';

describe('faceForCard', () => {
  it('walks the four faces round', () => {
    expect([1, 2, 3, 4, 5, 8].map((n) => faceForCard(n))).toEqual([1, 2, 3, 4, 1, 4]);
  });

  it('names the face count it assumes', () => {
    expect(CYLINDER_FACES).toBe(4);
  });

  it('takes a cylinder with a different number of faces', () => {
    expect(faceForCard(7, { faces: 6 })).toBe(1);
  });
});

describe('turnAngle', () => {
  it('turns a quarter per pick on a four-faced cylinder', () => {
    expect(turnAngle(1)).toBe(90);
    expect(turnAngle(2)).toBe(180);
    expect(turnAngle(4)).toBe(0);
    expect(turnAngle(5)).toBe(90);
  });

  it('is zero before anything is woven', () => {
    expect(turnAngle(0)).toBe(0);
  });
});

describe('advance', () => {
  it('moves the chain on and wraps at the join', () => {
    expect(advance(1, 3, 10)).toBe(4);
    expect(advance(9, 3, 10)).toBe(2);
    expect(advance(1, 10, 10)).toBe(1);
  });

  it('runs the chain backwards when told to', () => {
    expect(advance(4, 3, 10, { direction: 'reverse' })).toBe(1);
    expect(advance(2, 3, 10, { direction: 'reverse' })).toBe(9);
  });

  it('names the two directions', () => {
    expect(DIRECTIONS).toEqual(['forward', 'reverse']);
    expect(() => advance(1, 1, 10, { direction: 'sideways' })).toThrow(InvalidArgument);
  });

  it('refuses a starting card the chain has not got', () => {
    expect(() => advance(11, 1, 10)).toThrow(/no such card/);
  });

  it('stays where it is for no picks', () => {
    expect(advance(5, 0, 10)).toBe(5);
  });
});

describe('picksBetween', () => {
  it('counts forward round the loop', () => {
    expect(picksBetween(1, 4, 10)).toBe(3);
    expect(picksBetween(9, 2, 10)).toBe(3);
    expect(picksBetween(4, 4, 10)).toBe(0);
  });

  it('agrees with advance', () => {
    for (const [from, to] of [
      [1, 7],
      [8, 3],
      [5, 5],
    ]) {
      expect(advance(from, picksBetween(from, to, 10), 10)).toBe(to);
    }
  });
});

describe('backOff', () => {
  it('undoes exactly the picks it is given', () => {
    const after = advance(1, 6, 20);
    expect(backOff(after, 6, 20)).toBe(1);
  });

  it('wraps back past the join', () => {
    expect(backOff(2, 5, 20)).toBe(17);
  });
});

describe('chainRevolutions', () => {
  it('splits the picks into whole turns and what is left', () => {
    expect(chainRevolutions(250, 100)).toEqual({ whole: 2, remainder: 50 });
    expect(chainRevolutions(100, 100)).toEqual({ whole: 1, remainder: 0 });
    expect(chainRevolutions(50, 100)).toEqual({ whole: 0, remainder: 50 });
  });
});

describe('assertChainFits', () => {
  it('passes a chain the rail can carry', () => {
    expect(assertChainFits(500, { railCapacity: 800 })).toBe(500);
  });

  it('says how much chain is over the limit', () => {
    try {
      assertChainFits(900, { railCapacity: 800 });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail.over).toBe(100);
    }
  });
});

describe('patternLengthCm and cardsForLength', () => {
  it('turns cards into cloth length at a pick density', () => {
    expect(patternLengthCm(240, 24)).toBe(10);
  });

  it('turns cloth length back into cards, rounding up to a whole pick', () => {
    expect(cardsForLength(10, 24)).toBe(240);
    expect(cardsForLength(10.01, 24)).toBe(241);
  });

  it('refuses a pick density of zero', () => {
    expect(() => patternLengthCm(240, 0)).toThrow(InvalidArgument);
  });
});
