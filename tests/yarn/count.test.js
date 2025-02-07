import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  COUNT_SYSTEMS,
  countSystem,
  toTex,
  fromTex,
  convertCount,
  isIndirect,
  foldedCount,
  formatFold,
  yarnDiameterInches,
  threadsPerInch,
  metresPerGram,
} from '../../src/yarn/count.js';

describe('COUNT_SYSTEMS', () => {
  it('carries the eight systems a mill quotes in', () => {
    expect([...COUNT_SYSTEMS.keys()]).toEqual([
      'tex',
      'dtex',
      'denier',
      'nm',
      'ne',
      'new',
      'nel',
      'nw',
    ]);
  });

  it('works the cotton constant out to the published 590.5', () => {
    expect(COUNT_SYSTEMS.get('ne').constant).toBeCloseTo(590.54, 2);
  });

  it('works the worsted, linen and woollen constants out too', () => {
    expect(COUNT_SYSTEMS.get('new').constant).toBeCloseTo(885.81, 2);
    expect(COUNT_SYSTEMS.get('nel').constant).toBeCloseTo(1653.52, 2);
    expect(COUNT_SYSTEMS.get('nw').constant).toBeCloseTo(1937.71, 2);
  });
});

describe('countSystem', () => {
  it('looks up whatever case the caller wrote', () => {
    expect(countSystem('Tex').kind).toBe('direct');
    expect(countSystem('NE').kind).toBe('indirect');
  });

  it('names the systems it does know when it refuses one', () => {
    try {
      countSystem('grist');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.detail.known).toContain('tex');
    }
  });
});

describe('toTex and fromTex', () => {
  it('leaves tex alone', () => {
    expect(toTex(25, 'tex')).toBe(25);
    expect(fromTex(25, 'tex')).toBe(25);
  });

  it('reads decitex as a tenth of a tex and denier as a ninth', () => {
    expect(toTex(250, 'dtex')).toBeCloseTo(25, 10);
    expect(toTex(225, 'denier')).toBeCloseTo(25, 10);
    expect(fromTex(25, 'denier')).toBeCloseTo(225, 10);
  });

  it('inverts an indirect count', () => {
    expect(toTex(20, 'ne')).toBeCloseTo(29.53, 2);
    expect(toTex(50, 'nm')).toBeCloseTo(20, 10);
  });

  it('round-trips every system', () => {
    for (const system of COUNT_SYSTEMS.keys()) {
      expect(fromTex(toTex(30, system), system)).toBeCloseTo(30, 8);
    }
  });

  it('refuses a count of zero, which no yarn has', () => {
    expect(() => toTex(0, 'ne')).toThrow(InvalidArgument);
  });
});

describe('convertCount', () => {
  it('turns 30s cotton into its metric and tex readings', () => {
    expect(convertCount(30, 'ne', 'tex', { places: 2 })).toBe(19.68);
    expect(convertCount(30, 'ne', 'nm', { places: 2 })).toBe(50.8);
  });

  it('turns 150 denier into decitex, which is the same yarn ten times over', () => {
    expect(convertCount(150, 'denier', 'dtex', { places: 2 })).toBe(166.67);
  });

  it('is its own inverse', () => {
    const there = convertCount(40, 'ne', 'nm', { places: 10 });
    expect(convertCount(there, 'nm', 'ne', { places: 6 })).toBeCloseTo(40, 5);
  });
});

describe('isIndirect', () => {
  it('separates the two kinds of system', () => {
    expect(isIndirect('ne')).toBe(true);
    expect(isIndirect('nm')).toBe(true);
    expect(isIndirect('tex')).toBe(false);
    expect(isIndirect('denier')).toBe(false);
  });
});

describe('foldedCount', () => {
  it('halves an indirect count when two equal ends are folded', () => {
    expect(foldedCount([40, 40], 'ne')).toBe(20);
    expect(foldedCount([60, 60, 60], 'nm')).toBe(20);
  });

  it('adds a direct count instead', () => {
    expect(foldedCount([25, 25], 'tex')).toBe(50);
  });

  it('handles unequal ends by reciprocals', () => {
    expect(foldedCount([30, 60], 'ne')).toBe(20);
  });

  it('leaves a single end alone', () => {
    expect(foldedCount([40], 'ne')).toBe(40);
  });

  it('refuses an empty fold', () => {
    expect(() => foldedCount([], 'ne')).toThrow(InvalidArgument);
  });
});

describe('formatFold', () => {
  it('writes the trade notation for equal ends', () => {
    expect(formatFold([40, 40], 'ne')).toBe('2/40s');
    expect(formatFold([25, 25], 'tex')).toBe('2/25 tex');
  });

  it('lists unequal ends instead', () => {
    expect(formatFold([30, 60], 'ne')).toBe('30 + 60');
  });
});

describe('yarnDiameterInches', () => {
  it('is one over twenty eight root Ne', () => {
    expect(yarnDiameterInches(16, 'ne')).toBeCloseTo(1 / (28 * 4), 10);
  });

  it('gets finer as the count rises', () => {
    expect(yarnDiameterInches(60, 'ne')).toBeLessThan(yarnDiameterInches(20, 'ne'));
  });

  it('reads a tex count by converting first', () => {
    const fromNe = yarnDiameterInches(30, 'ne');
    const asTex = yarnDiameterInches(convertCount(30, 'ne', 'tex', { places: 10 }), 'tex');
    expect(asTex).toBeCloseTo(fromNe, 8);
  });

  it('takes a different diameter constant for a different fibre', () => {
    expect(yarnDiameterInches(16, 'ne', { constant: 25 })).toBeCloseTo(1 / (25 * 4), 10);
  });
});

describe('threadsPerInch', () => {
  it('is the reciprocal of the diameter', () => {
    expect(threadsPerInch(16, 'ne')).toBe(112);
    expect(threadsPerInch(36, 'ne')).toBe(168);
  });
});

describe('metresPerGram', () => {
  it('is the metric count by definition', () => {
    expect(metresPerGram(50, 'nm')).toBe(50);
  });

  it('agrees with the tex reading', () => {
    expect(metresPerGram(20, 'tex')).toBe(50);
  });
});
