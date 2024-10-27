import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  canonicalStringify,
  deepFreeze,
  canonicalClone,
  canonicalEquals,
  parseJson,
} from '../../src/util/json.js';

describe('canonicalStringify', () => {
  it('sorts object keys so key order cannot change an id', () => {
    expect(canonicalStringify({ b: 1, a: 2 })).toBe('{"a":2,"b":1}');
    expect(canonicalStringify({ a: 2, b: 1 })).toBe('{"a":2,"b":1}');
  });

  it('sorts nested keys too', () => {
    expect(canonicalStringify({ z: { y: 1, x: 2 } })).toBe('{"z":{"x":2,"y":1}}');
  });

  it('keeps array order, because a treadling is not a set', () => {
    expect(canonicalStringify([3, 1, 2])).toBe('[3,1,2]');
  });

  it('drops undefined properties but writes undefined array holes as null', () => {
    expect(canonicalStringify({ a: 1, b: undefined })).toBe('{"a":1}');
    expect(canonicalStringify([1, undefined, 2])).toBe('[1,null,2]');
  });

  it('folds negative zero so a rounded figure cannot fork an id', () => {
    expect(canonicalStringify(-0)).toBe('0');
    expect(canonicalStringify({ v: -0 })).toBe('{"v":0}');
  });

  it('writes a Date as its ISO string', () => {
    expect(canonicalStringify(new Date(Date.UTC(2025, 0, 2, 3, 4, 5)))).toBe(
      '"2025-01-02T03:04:05.000Z"',
    );
  });

  it('writes a Map like an object and a Set like an array', () => {
    expect(
      canonicalStringify(
        new Map([
          ['b', 1],
          ['a', 2],
        ]),
      ),
    ).toBe('{"a":2,"b":1}');
    expect(canonicalStringify(new Set([2, 1]))).toBe('[2,1]');
  });

  it('refuses what JSON cannot carry honestly', () => {
    expect(() => canonicalStringify(Number.NaN)).toThrow(/non-finite/);
    expect(() => canonicalStringify(Number.POSITIVE_INFINITY)).toThrow(/non-finite/);
    expect(() => canonicalStringify(10n)).toThrow(/bigint/);
    expect(() => canonicalStringify(undefined)).toThrow(/undefined/);
    expect(() => canonicalStringify(() => {})).toThrow(/function/);
    expect(() => canonicalStringify(Symbol('s'))).toThrow(/symbol/);
  });

  it('detects a cycle instead of overflowing the stack', () => {
    const a = { name: 'a' };
    a.self = a;
    expect(() => canonicalStringify(a)).toThrow(/cycle/);
  });

  it('allows the same object twice when it is not a cycle', () => {
    const shared = { n: 1 };
    expect(canonicalStringify({ x: shared, y: shared })).toBe('{"x":{"n":1},"y":{"n":1}}');
  });
});

describe('deepFreeze', () => {
  it('freezes nested objects and arrays', () => {
    const frozen = deepFreeze({ a: { b: [1, 2] } });
    expect(Object.isFrozen(frozen)).toBe(true);
    expect(Object.isFrozen(frozen.a)).toBe(true);
    expect(Object.isFrozen(frozen.a.b)).toBe(true);
  });

  it('returns primitives untouched', () => {
    expect(deepFreeze(4)).toBe(4);
    expect(deepFreeze(null)).toBeNull();
  });

  it('survives a cycle', () => {
    const a = { name: 'a' };
    a.self = a;
    expect(Object.isFrozen(deepFreeze(a))).toBe(true);
  });
});

describe('canonicalClone', () => {
  it('produces an equal but separate structure', () => {
    const source = { b: [1, { c: 2 }], a: 'x' };
    const copy = canonicalClone(source);
    expect(copy).toEqual(source);
    copy.b[1].c = 99;
    expect(source.b[1].c).toBe(2);
  });

  it('rejects what canonicalStringify rejects', () => {
    expect(() => canonicalClone({ n: Number.NaN })).toThrow(InvalidArgument);
  });
});

describe('canonicalEquals', () => {
  it('ignores key order', () => {
    expect(canonicalEquals({ a: 1, b: 2 }, { b: 2, a: 1 })).toBe(true);
  });

  it('does not ignore array order', () => {
    expect(canonicalEquals([1, 2], [2, 1])).toBe(false);
  });

  it('separates 1 from "1"', () => {
    expect(canonicalEquals({ v: 1 }, { v: '1' })).toBe(false);
  });
});

describe('parseJson', () => {
  it('parses valid JSON', () => {
    expect(parseJson('{"a":1}')).toEqual({ a: 1 });
  });

  it('turns a syntax error into InvalidArgument naming the field', () => {
    try {
      parseJson('{oops', 'deck');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.message).toBe('deck is not valid JSON');
      expect(err.detail.field).toBe('deck');
      expect(typeof err.detail.reason).toBe('string');
    }
  });

  it('refuses a non-string', () => {
    expect(() => parseJson(null)).toThrow(/body must be a string/);
  });
});
