import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  gcd,
  lcm,
  areCoprime,
  coprimesBelow,
  mod,
  clamp,
  roundTo,
  sum,
  sumBy,
  mean,
  meanBy,
  stdev,
  percentile,
} from '../../src/util/numeric.js';

describe('gcd and lcm', () => {
  it('agrees with the worked cases a satin repeat needs', () => {
    expect(gcd(8, 12)).toBe(4);
    expect(gcd(5, 2)).toBe(1);
    expect(gcd(6, 3)).toBe(3);
    expect(gcd(0, 7)).toBe(7);
    expect(gcd(7, 0)).toBe(7);
    expect(gcd(0, 0)).toBe(0);
  });

  it('ignores sign', () => {
    expect(gcd(-8, 12)).toBe(4);
    expect(gcd(8, -12)).toBe(4);
    expect(gcd(-8, -12)).toBe(4);
  });

  it('lcm is the repeat length of two cycles run together', () => {
    expect(lcm(4, 6)).toBe(12);
    expect(lcm(5, 8)).toBe(40);
    expect(lcm(3, 3)).toBe(3);
    expect(lcm(0, 5)).toBe(0);
  });

  it('refuses a non-integer', () => {
    expect(() => gcd(4.5, 2)).toThrow(InvalidArgument);
  });
});

describe('coprimality', () => {
  it('areCoprime is gcd of one', () => {
    expect(areCoprime(5, 2)).toBe(true);
    expect(areCoprime(6, 4)).toBe(false);
    expect(areCoprime(1, 1)).toBe(true);
  });

  it('coprimesBelow gives the candidate move numbers for an n-end satin', () => {
    expect(coprimesBelow(5)).toEqual([1, 2, 3, 4]);
    expect(coprimesBelow(8)).toEqual([1, 3, 5, 7]);
    expect(coprimesBelow(6)).toEqual([1, 5]);
    expect(coprimesBelow(1)).toEqual([]);
  });

  it('six has no move number left once 1 and n-1 are barred, which is why there is no regular six-end satin', () => {
    const usable = coprimesBelow(6).filter((m) => m > 1 && m < 5);
    expect(usable).toEqual([]);
    const forFive = coprimesBelow(5).filter((m) => m > 1 && m < 4);
    expect(forFive).toEqual([2, 3]);
  });
});

describe('mod', () => {
  it('never returns a negative, unlike the operator', () => {
    expect(-1 % 8).toBe(-1);
    expect(mod(-1, 8)).toBe(7);
    expect(mod(9, 8)).toBe(1);
    expect(mod(-16, 8)).toBe(0);
  });

  it('needs a positive modulus', () => {
    expect(() => mod(3, 0)).toThrow(InvalidArgument);
    expect(() => mod(3, -4)).toThrow(InvalidArgument);
  });
});

describe('clamp', () => {
  it('holds a value inside the range', () => {
    expect(clamp(5, 1, 8)).toBe(5);
    expect(clamp(-2, 1, 8)).toBe(1);
    expect(clamp(99, 1, 8)).toBe(8);
  });

  it('refuses an inverted range rather than silently swapping it', () => {
    expect(() => clamp(5, 8, 1)).toThrow(/min <= max/);
  });
});

describe('roundTo', () => {
  it('rounds to the requested places', () => {
    expect(roundTo(3.14159, 2)).toBe(3.14);
    expect(roundTo(3.14159, 4)).toBe(3.1416);
    expect(roundTo(2.5, 0)).toBe(3);
    expect(roundTo(12.345, 2)).toBe(12.35);
  });

  it('takes a tie away from zero on both signs', () => {
    expect(roundTo(-2.5, 0)).toBe(-3);
    expect(roundTo(-0.125, 2)).toBe(-0.13);
  });

  it('defaults to two places', () => {
    expect(roundTo(1.23456)).toBe(1.23);
  });
});

describe('summary statistics', () => {
  it('sums and averages', () => {
    expect(sum([1, 2, 3, 4])).toBe(10);
    expect(sum([])).toBe(0);
    expect(mean([2, 4, 6])).toBe(4);
  });

  it('mean refuses an empty list rather than returning NaN', () => {
    expect(() => mean([])).toThrow(InvalidArgument);
  });

  it('stdev is the population figure, not the sample one', () => {
    expect(stdev([2, 4, 4, 4, 5, 5, 7, 9])).toBe(2);
    expect(stdev([5, 5, 5])).toBe(0);
  });

  it('percentile interpolates between neighbours', () => {
    const values = [1, 2, 3, 4];
    expect(percentile(values, 0)).toBe(1);
    expect(percentile(values, 100)).toBe(4);
    expect(percentile(values, 50)).toBe(2.5);
    expect(percentile([7], 42)).toBe(7);
  });

  it('percentile does not disturb the caller list', () => {
    const values = [9, 1, 5];
    percentile(values, 50);
    expect(values).toEqual([9, 1, 5]);
  });

  it('rejects a percentile outside 0 to 100', () => {
    expect(() => percentile([1, 2], 101)).toThrow(InvalidArgument);
  });
});

describe('sumBy', () => {
  const pieces = [{ yards: 40 }, { yards: 55.5 }, { yards: 4.5 }];

  it('adds a field up over records', () => {
    expect(sumBy(pieces, (piece) => piece.yards)).toBe(100);
  });

  it('is zero over nothing', () => {
    expect(sumBy([], (piece) => piece.yards)).toBe(0);
  });

  it('hands the index to the reader', () => {
    expect(sumBy(pieces, (piece, index) => index)).toBe(3);
  });

  it('names the record whose field is missing rather than giving back NaN', () => {
    const withHole = [{ yards: 1 }, {}, { yards: 3 }];
    expect(() => sumBy(withHole, (piece) => piece.yards)).toThrow(InvalidArgument);
    expect(() => sumBy(withHole, (piece) => piece.yards)).toThrow(/items\[1\]/);
  });

  it('takes the field name the caller uses for its own records', () => {
    expect(() => sumBy([{}], (piece) => piece.yards, 'pieces')).toThrow(/pieces\[0\]/);
  });

  it('refuses something that is not a list', () => {
    expect(() => sumBy(null, (piece) => piece)).toThrow(InvalidArgument);
  });
});

describe('meanBy', () => {
  it('averages a field over records', () => {
    expect(meanBy([{ n: 2 }, { n: 4 }, { n: 9 }], (entry) => entry.n)).toBe(5);
  });

  it('reads an empty list as zero rather than throwing, unlike mean', () => {
    // A run with no readings has no average, and every caller here wants a
    // number back to put in a report rather than an error to handle.
    expect(meanBy([], (entry) => entry.n)).toBe(0);
    expect(() => mean([])).toThrow(InvalidArgument);
  });

  it('names the record at fault the same way', () => {
    expect(() => meanBy([{ n: 1 }, {}], (entry) => entry.n, 'readings')).toThrow(/readings\[1\]/);
  });
});
