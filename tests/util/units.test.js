import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  dimensionOf,
  knownUnits,
  convert,
  comparable,
  perInchToPerCm,
  perCmToPerInch,
  gsmToOzYd2,
  ozYd2ToGsm,
  format,
} from '../../src/util/units.js';

describe('dimensionOf', () => {
  it('sorts the units into dimensions', () => {
    expect(dimensionOf('cm')).toBe('length');
    expect(dimensionOf('kg')).toBe('mass');
    expect(dimensionOf('yd2')).toBe('area');
  });

  it('names what it knows when it does not know one', () => {
    try {
      dimensionOf('furlong');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.detail.known).toContain('yd');
    }
  });

  it('lists its units sorted', () => {
    const units = knownUnits();
    expect(units).toEqual([...units].sort());
    expect(units).toContain('in');
  });
});

describe('convert', () => {
  it('converts within length exactly', () => {
    expect(convert(1, 'yd', 'm')).toBe(0.9144);
    expect(convert(1, 'in', 'cm')).toBe(2.54);
    expect(convert(100, 'cm', 'm')).toBe(1);
  });

  it('converts within mass exactly, given the places to show it in', () => {
    expect(convert(1, 'lb', 'kg', { places: 12 })).toBeCloseTo(0.45359237, 8);
    expect(convert(16, 'oz', 'lb', { places: 12 })).toBeCloseTo(1, 8);
  });

  it('converts within area', () => {
    expect(convert(1, 'yd2', 'm2', { places: 12 })).toBeCloseTo(0.83612736, 8);
  });

  it('rounds to six places by default, which is enough for a shed docket', () => {
    expect(convert(1, 'lb', 'kg')).toBe(0.453592);
  });

  it('round-trips without drifting', () => {
    for (const [from, to] of [
      ['yd', 'm'],
      ['lb', 'kg'],
      ['in2', 'm2'],
    ]) {
      const there = convert(7, from, to, { places: 12 });
      expect(convert(there, to, from, { places: 8 })).toBeCloseTo(7, 6);
    }
  });

  it('refuses two units that do not measure the same thing', () => {
    expect(() => convert(1, 'kg', 'm')).toThrow(/do not measure the same thing/);
    expect(comparable('kg', 'm')).toBe(false);
    expect(comparable('kg', 'lb')).toBe(true);
    expect(comparable('kg', 'furlong')).toBe(false);
  });
});

describe('per inch and per centimetre', () => {
  it('converts a sett both ways', () => {
    expect(perInchToPerCm(25.4)).toBe(10);
    expect(perCmToPerInch(10)).toBe(25.4);
  });

  it('round-trips', () => {
    expect(perCmToPerInch(perInchToPerCm(60))).toBeCloseTo(60, 3);
  });

  it('refuses a negative sett', () => {
    expect(() => perInchToPerCm(-1)).toThrow(InvalidArgument);
  });
});

describe('gsm and ounces a square yard', () => {
  it('converts a common cloth weight', () => {
    expect(gsmToOzYd2(339)).toBeCloseTo(10, 1);
  });

  it('round-trips', () => {
    expect(ozYd2ToGsm(gsmToOzYd2(250))).toBeCloseTo(250, 2);
  });

  it('is not a plain unit conversion, because both parts change', () => {
    expect(gsmToOzYd2(339)).not.toBeCloseTo(convert(339, 'g', 'oz'), 1);
  });
});

describe('format', () => {
  it('writes a value with its unit', () => {
    expect(format(1.23456, 'm')).toBe('1.23 m');
    expect(format(1.23456, 'm', { places: 4 })).toBe('1.2346 m');
  });

  it('refuses a unit it does not know', () => {
    expect(() => format(1, 'furlong')).toThrow(InvalidArgument);
  });
});
