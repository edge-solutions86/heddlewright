import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  required,
  isInteger,
  integer,
  positiveInteger,
  number,
  positiveNumber,
  nonEmptyString,
  oneOf,
  array,
  plainObject,
  describe as describeValue,
} from '../../src/util/check.js';

describe('required', () => {
  it('passes anything that is not null or undefined, including 0 and empty string', () => {
    expect(required(0, 'x')).toBe(0);
    expect(required('', 'x')).toBe('');
    expect(required(false, 'x')).toBe(false);
  });

  it('names the field and which of the two absences it saw', () => {
    expect(() => required(null, 'shafts')).toThrow(/shafts is required/);
    try {
      required(undefined, 'shafts');
    } catch (err) {
      expect(err.detail).toEqual({ field: 'shafts', got: 'undefined' });
    }
    try {
      required(null, 'shafts');
    } catch (err) {
      expect(err.detail.got).toBe('null');
    }
  });
});

describe('integer', () => {
  it('accepts integers and rejects everything else', () => {
    expect(integer(4, 'n')).toBe(4);
    expect(integer(-2, 'n')).toBe(-2);
    expect(() => integer(4.5, 'n')).toThrow(InvalidArgument);
    expect(() => integer('4', 'n')).toThrow(InvalidArgument);
    expect(() => integer(NaN, 'n')).toThrow(InvalidArgument);
    expect(() => integer(Infinity, 'n')).toThrow(InvalidArgument);
  });

  it('enforces min and max inclusively', () => {
    expect(integer(1, 'n', { min: 1, max: 8 })).toBe(1);
    expect(integer(8, 'n', { min: 1, max: 8 })).toBe(8);
    expect(() => integer(0, 'n', { min: 1 })).toThrow(/at least 1/);
    expect(() => integer(9, 'n', { max: 8 })).toThrow(/at most 8/);
  });

  it('reports the bound it broke in the detail', () => {
    try {
      integer(12, 'shafts', { max: 8 });
    } catch (err) {
      expect(err.detail).toEqual({ field: 'shafts', got: 12, max: 8 });
    }
  });
});

describe('isInteger', () => {
  it('is the predicate behind integer, without the throw', () => {
    expect(isInteger(3)).toBe(true);
    expect(isInteger(3.1)).toBe(false);
    expect(isInteger('3')).toBe(false);
    expect(isInteger(null)).toBe(false);
  });
});

describe('positiveInteger', () => {
  it('rejects zero and negatives', () => {
    expect(positiveInteger(1, 'n')).toBe(1);
    expect(() => positiveInteger(0, 'n')).toThrow(/at least 1/);
    expect(() => positiveInteger(-3, 'n')).toThrow(InvalidArgument);
  });
});

describe('number', () => {
  it('takes fractions but not non-finite values', () => {
    expect(number(2.75, 'x')).toBe(2.75);
    expect(() => number(Number.NaN, 'x')).toThrow(/finite number/);
    expect(() => number(Number.POSITIVE_INFINITY, 'x')).toThrow(/finite number/);
  });

  it('positiveNumber excludes zero where number allows it', () => {
    expect(number(0, 'x')).toBe(0);
    expect(() => positiveNumber(0, 'x')).toThrow(/greater than zero/);
    expect(positiveNumber(0.001, 'x')).toBe(0.001);
  });
});

describe('nonEmptyString', () => {
  it('rejects whitespace-only strings', () => {
    expect(nonEmptyString('twill', 'name')).toBe('twill');
    expect(() => nonEmptyString('   ', 'name')).toThrow(InvalidArgument);
    expect(() => nonEmptyString('', 'name')).toThrow(InvalidArgument);
    expect(() => nonEmptyString(7, 'name')).toThrow(InvalidArgument);
  });

  it('keeps surrounding whitespace rather than trimming for the caller', () => {
    expect(nonEmptyString(' twill ', 'name')).toBe(' twill ');
  });
});

describe('oneOf', () => {
  it('accepts a member and lists the alternatives when it does not', () => {
    expect(oneOf('rising', 'shed', ['rising', 'sinking'])).toBe('rising');
    expect(() => oneOf('floating', 'shed', ['rising', 'sinking'])).toThrow(/rising, sinking/);
  });

  it('works from a Set as well as an array', () => {
    expect(oneOf('S', 'hand', new Set(['S', 'Z']))).toBe('S');
    expect(() => oneOf('Q', 'hand', new Set(['S', 'Z']))).toThrow(InvalidArgument);
  });
});

describe('array', () => {
  it('checks the type before the length', () => {
    expect(() => array('abc', 'picks', { min: 1 })).toThrow(/must be an array/);
  });

  it('enforces min and max lengths', () => {
    expect(array([1, 2], 'picks', { min: 1, max: 4 })).toEqual([1, 2]);
    expect(() => array([], 'picks', { min: 1 })).toThrow(/at least 1 entries/);
    expect(() => array([1, 2, 3], 'picks', { max: 2 })).toThrow(/at most 2 entries/);
  });
});

describe('plainObject', () => {
  it('rejects arrays and null, which typeof calls objects', () => {
    expect(plainObject({ a: 1 }, 'o')).toEqual({ a: 1 });
    expect(() => plainObject([], 'o')).toThrow(/must be an object/);
    expect(() => plainObject(null, 'o')).toThrow(/must be an object/);
  });
});

describe('describe', () => {
  it('summarises without leaking a long value into an error message', () => {
    expect(describeValue(null)).toBe('null');
    expect(describeValue(undefined)).toBe('undefined');
    expect(describeValue([1, 2, 3])).toBe('array(3)');
    expect(describeValue('twill')).toBe('"twill"');
    expect(describeValue('x'.repeat(40))).toBe('string(40)');
    expect(describeValue(12)).toBe('12');
    expect(describeValue(true)).toBe('true');
    expect(describeValue({})).toBe('object');
    expect(describeValue(() => {})).toBe('function');
  });
});
