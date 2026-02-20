import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument } from '../../src/util/errors.js';
import {
  readShade,
  shadeDistance,
  outliers,
  lotHolds,
  buildLots,
  lotCentre,
  lotSpread,
  assertLotHolds,
  lotOf,
  lotCount,
  byDyeLot,
} from '../../src/cloth/lot.js';

const A = readShade({ reference: 'P1', lightness: 60, red: 2, yellow: -1 });
const B = readShade({ reference: 'P2', lightness: 60.4, red: 2.1, yellow: -1.1 });
const C = readShade({ reference: 'P3', lightness: 64, red: 3, yellow: -2 });

describe('readShade', () => {
  it('takes a reading on the three axes', () => {
    expect(A).toEqual({ reference: 'P1', lightness: 60, red: 2, yellow: -1 });
  });

  it('refuses a reading with no reference or a missing axis', () => {
    expect(() => readShade({ lightness: 60, red: 2, yellow: 0 })).toThrow(InvalidArgument);
    expect(() => readShade({ reference: 'P1', lightness: 60, red: 2 })).toThrow(InvalidArgument);
  });
});

describe('shadeDistance', () => {
  it('is nothing between a reading and itself', () => {
    expect(shadeDistance(A, A)).toBe(0);
  });

  it('is the straight distance in colour space', () => {
    expect(shadeDistance(A, B)).toBeCloseTo(Math.sqrt(0.16 + 0.01 + 0.01), 4);
  });

  it('does not care which way round it is asked', () => {
    expect(shadeDistance(A, C)).toBe(shadeDistance(C, A));
  });
});

describe('outliers and lotHolds', () => {
  it('finds nothing in a tight lot', () => {
    expect(outliers([A, B], { tolerance: 1 })).toEqual([]);
    expect(lotHolds([A, B], { tolerance: 1 })).toBe(true);
  });

  it('finds the pair that is too far apart, worst first', () => {
    const found = outliers([A, B, C], { tolerance: 1 });
    expect(found.length).toBe(2);
    expect(found[0].distance).toBeGreaterThan(found[1].distance);
    expect(lotHolds([A, B, C], { tolerance: 1 })).toBe(false);
  });

  it('refuses a tolerance of nothing', () => {
    expect(() => outliers([A], { tolerance: 0 })).toThrow(InvalidArgument);
  });
});

describe('buildLots', () => {
  it('puts pieces that match into one lot', () => {
    const lots = buildLots([A, B], { tolerance: 1 });
    expect(lots.length).toBe(1);
    expect(lots[0].pieces).toEqual(['P1', 'P2']);
  });

  it('opens a second lot for a piece that does not match', () => {
    const lots = buildLots([A, B, C], { tolerance: 1 });
    expect(lots.length).toBe(2);
    expect(lots[1].pieces).toEqual(['P3']);
  });

  it('will not let a lot drift across the space one step at a time', () => {
    const middle = readShade({ reference: 'P4', lightness: 62, red: 2.5, yellow: -1.5 });
    const lots = buildLots([A, middle, C], { tolerance: 2.5 });
    expect(lots.length).toBeGreaterThan(1);
    for (const lot of lots) {
      expect(lot.spread).toBeLessThanOrEqual(2.5);
    }
  });

  it('reports the centre and the spread of each lot', () => {
    const lots = buildLots([A, B], { tolerance: 1 });
    expect(lots[0].centre.lightness).toBe(60.2);
    expect(lots[0].spread).toBe(shadeDistance(A, B));
  });

  it('counts the lots a delivery needs', () => {
    expect(lotCount([A, B, C], { tolerance: 1 })).toBe(2);
    expect(lotCount([A, B, C], { tolerance: 10 })).toBe(1);
  });
});

describe('lotCentre and lotSpread', () => {
  it('averages a single reading to itself', () => {
    expect(lotCentre([A])).toEqual({ lightness: 60, red: 2, yellow: -1 });
    expect(lotSpread([A])).toBe(0);
  });

  it('takes the widest gap, not the last one', () => {
    expect(lotSpread([A, B, C])).toBe(shadeDistance(A, C));
  });
});

describe('assertLotHolds', () => {
  it('passes a lot that holds', () => {
    expect(assertLotHolds([A, B], { tolerance: 1 })).toEqual([A, B]);
  });

  it('refuses one that does not, naming the widest pair', () => {
    try {
      assertLotHolds([A, C], { tolerance: 1 });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(ConflictError);
      expect(err.detail.widest.left).toBe('P1');
    }
  });
});

describe('lotOf', () => {
  it('finds which lot a piece went in', () => {
    const lots = buildLots([A, B, C], { tolerance: 1 });
    expect(lotOf(lots, 'P2')).toBe(1);
    expect(lotOf(lots, 'P3')).toBe(2);
  });

  it('refuses a piece nothing holds', () => {
    expect(() => lotOf(buildLots([A], { tolerance: 1 }), 'P9')).toThrow(InvalidArgument);
  });
});

describe('byDyeLot', () => {
  it('groups pieces on the lot they were dyed in', () => {
    const pieces = [
      { reference: 'P1', lot: 'D1' },
      { reference: 'P2', lot: 'D1' },
      { reference: 'P3', lot: 'D2' },
    ];
    expect(byDyeLot(pieces)).toEqual([
      { lot: 'D1', pieces: ['P1', 'P2'] },
      { lot: 'D2', pieces: ['P3'] },
    ]);
  });

  it('gathers the pieces nobody recorded a lot for', () => {
    expect(byDyeLot([{ reference: 'P1' }])).toEqual([{ lot: 'unrecorded', pieces: ['P1'] }]);
  });
});
