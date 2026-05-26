import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  over,
  room,
  fits,
  assertFits,
  assertAtLeast,
  shortfalls,
  worstShortfall,
} from '../../src/util/limits.js';

describe('over and room', () => {
  it('measure the two sides of a limit', () => {
    expect(over(12, 10)).toBe(2);
    expect(room(12, 10)).toBe(0);
    expect(over(8, 10)).toBe(0);
    expect(room(8, 10)).toBe(2);
  });

  it('are both nothing exactly on the limit', () => {
    expect(over(10, 10)).toBe(0);
    expect(room(10, 10)).toBe(0);
  });

  it('never go negative, which is the mistake they exist to stop', () => {
    expect(over(1, 100)).toBe(0);
    expect(room(100, 1)).toBe(0);
  });

  it('refuse something that is not a number', () => {
    expect(() => over('twelve', 10)).toThrow(InvalidArgument);
  });
});

describe('fits', () => {
  it('is true up to and including the limit', () => {
    expect(fits(9, 10)).toBe(true);
    expect(fits(10, 10)).toBe(true);
    expect(fits(11, 10)).toBe(false);
  });
});

describe('assertFits', () => {
  it('hands the value back when it fits', () => {
    expect(assertFits(9, 10, { what: 'chain', has: 'the rail holds ten' })).toBe(9);
  });

  it('throws with the shortfall the right way round', () => {
    try {
      assertFits(14, 10, { what: 'chain', has: 'the rail holds ten' });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.message).toBe('the rail holds ten');
      expect(err.detail).toMatchObject({ what: 'chain', wanted: 14, available: 10, over: 4 });
    }
  });

  it('carries whatever detail the caller adds', () => {
    try {
      assertFits(14, 10, { what: 'chain', has: 'too long', detail: { cards: 14 } });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.detail.cards).toBe(14);
    }
  });

  it('needs a name and a message', () => {
    expect(() => assertFits(1, 10, { what: '', has: 'x' })).toThrow(InvalidArgument);
    expect(() => assertFits(1, 10, { what: 'x' })).toThrow(InvalidArgument);
  });
});

describe('assertAtLeast', () => {
  it('passes a value on or above the floor', () => {
    expect(assertAtLeast(10, 10, { what: 'warp', needs: 'ten ends' })).toBe(10);
  });

  it('throws with how far short it is', () => {
    try {
      assertAtLeast(4, 10, { what: 'warp', needs: 'ten ends' });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.detail).toMatchObject({ wanted: 4, floor: 10, short: 6 });
    }
  });
});

describe('shortfalls', () => {
  it('reports every position that is over, and only those', () => {
    expect(shortfalls([5, 12, 3], [10, 10, 10], { label: 'shaft' })).toEqual([
      { shaft: 2, wanted: 12, available: 10, over: 2 },
    ]);
  });

  it('is empty when everything fits', () => {
    expect(shortfalls([1, 2], [10, 10])).toEqual([]);
  });

  it('labels the positions from one', () => {
    expect(shortfalls([12], [10])[0].index).toBe(1);
  });

  it('refuses two lists of different lengths', () => {
    expect(() => shortfalls([1, 2], [10])).toThrow(/different lengths/);
    expect(() => shortfalls([1], 'ten')).toThrow(InvalidArgument);
  });
});

describe('worstShortfall', () => {
  it('finds the one furthest over', () => {
    const found = shortfalls([12, 20, 3], [10, 10, 10]);
    expect(worstShortfall(found).over).toBe(10);
  });

  it('is null when nothing is over', () => {
    expect(worstShortfall([])).toBeNull();
    expect(worstShortfall(null)).toBeNull();
  });
});
