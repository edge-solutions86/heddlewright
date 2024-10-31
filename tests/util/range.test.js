import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  parseRange,
  formatRange,
  rangeSize,
  rangeIncludes,
  inclusive,
} from '../../src/util/range.js';

describe('parseRange', () => {
  it('reads single values and spans', () => {
    expect(parseRange('3')).toEqual([3]);
    expect(parseRange('1-4')).toEqual([1, 2, 3, 4]);
    expect(parseRange('1-3,7,9-10')).toEqual([1, 2, 3, 7, 9, 10]);
  });

  it('sorts and deduplicates overlapping parts', () => {
    expect(parseRange('5,1-3,2')).toEqual([1, 2, 3, 5]);
    expect(parseRange('4-6,5-7')).toEqual([4, 5, 6, 7]);
  });

  it('tolerates spaces around parts and hyphens', () => {
    expect(parseRange(' 1 - 3 , 8 ')).toEqual([1, 2, 3, 8]);
  });

  it('reads negative values, which a defect offset can be', () => {
    expect(parseRange('-2--1')).toEqual([-2, -1]);
    expect(parseRange('-1,2')).toEqual([-1, 2]);
  });

  it('rejects a backwards span rather than silently reversing it', () => {
    expect(() => parseRange('7-3')).toThrow(/counts backwards/);
  });

  it('rejects empty parts and unreadable text', () => {
    expect(() => parseRange('1,,3')).toThrow(/empty part/);
    expect(() => parseRange('1-')).toThrow(/not a number or a span/);
    expect(() => parseRange('a-b')).toThrow(InvalidArgument);
    expect(() => parseRange('')).toThrow(InvalidArgument);
  });

  it('enforces the bounds it is given', () => {
    expect(parseRange('1-4', { min: 1, max: 8 })).toEqual([1, 2, 3, 4]);
    expect(() => parseRange('0-4', { min: 1 })).toThrow(/below 1/);
    expect(() => parseRange('1-9', { max: 8 })).toThrow(/above 8/);
  });
});

describe('formatRange', () => {
  it('folds consecutive runs into spans', () => {
    expect(formatRange([1, 2, 3, 7, 9, 10])).toBe('1-3,7,9-10');
    expect(formatRange([4])).toBe('4');
    expect(formatRange([])).toBe('');
  });

  it('sorts and deduplicates first', () => {
    expect(formatRange([3, 1, 2, 2])).toBe('1-3');
  });

  it('does not fold a pair into a span it cannot shorten', () => {
    expect(formatRange([1, 2])).toBe('1-2');
    expect(formatRange([1, 3])).toBe('1,3');
  });

  it('round-trips with parseRange', () => {
    for (const text of ['1-3,7,9-10', '2', '1-2,4-5']) {
      expect(formatRange(parseRange(text))).toBe(text);
    }
  });

  it('refuses a non-integer member', () => {
    expect(() => formatRange([1, 2.5])).toThrow(InvalidArgument);
  });
});

describe('rangeSize and rangeIncludes', () => {
  it('counts the values a range covers', () => {
    expect(rangeSize('1-4,9')).toBe(5);
    expect(rangeSize('3')).toBe(1);
  });

  it('answers membership', () => {
    expect(rangeIncludes('1-4,9', 3)).toBe(true);
    expect(rangeIncludes('1-4,9', 5)).toBe(false);
    expect(rangeIncludes('1-4,9', 9)).toBe(true);
  });
});

describe('inclusive', () => {
  it('counts up and down', () => {
    expect(inclusive(1, 4)).toEqual([1, 2, 3, 4]);
    expect(inclusive(4, 1)).toEqual([4, 3, 2, 1]);
    expect(inclusive(3, 3)).toEqual([3]);
  });
});
