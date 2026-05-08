import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  TEMPLE_KINDS,
  pullIn,
  clothWidth,
  reedWidth,
  estimatePullIn,
  templeGrip,
  templeFits,
  assertTempleFits,
  pullInWarning,
  endsUnderTemple,
  assertTempleKind,
  ringsNeeded,
} from '../../src/loom/temple.js';

describe('pullIn', () => {
  it('is the share the cloth lost against the reed', () => {
    expect(pullIn({ reedWidthCm: 200, clothWidthCm: 190 })).toBe(0.05);
  });

  it('is nothing when the cloth comes out the width it went in', () => {
    expect(pullIn({ reedWidthCm: 200, clothWidthCm: 200 })).toBe(0);
  });

  it('refuses cloth wider than the reed', () => {
    expect(() => pullIn({ reedWidthCm: 200, clothWidthCm: 210 })).toThrow(/cannot be wider/);
  });
});

describe('clothWidth and reedWidth', () => {
  it('are inverses of each other', () => {
    expect(clothWidth({ reedWidthCm: 200, pullInShare: 0.05 })).toBe(190);
    expect(reedWidth({ clothWidthCm: 190, pullInShare: 0.05 })).toBe(200);
  });

  it('leave the width alone at no pull-in', () => {
    expect(clothWidth({ reedWidthCm: 200, pullInShare: 0 })).toBe(200);
  });

  it('refuse a pull-in nobody would weave', () => {
    expect(() => clothWidth({ reedWidthCm: 200, pullInShare: 0.9 })).toThrow(InvalidArgument);
  });
});

describe('estimatePullIn', () => {
  it('rises with the weft crimp', () => {
    const low = estimatePullIn({ weftCrimpPercent: 4, coverFactor: 14 });
    const high = estimatePullIn({ weftCrimpPercent: 10, coverFactor: 14 });
    expect(high).toBeGreaterThan(low);
  });

  it('falls as the cloth is set more closely', () => {
    const open = estimatePullIn({ weftCrimpPercent: 8, coverFactor: 8 });
    const close = estimatePullIn({ weftCrimpPercent: 8, coverFactor: 22 });
    expect(close).toBeLessThan(open);
  });

  it('is nothing when the weft does not bend at all', () => {
    expect(estimatePullIn({ weftCrimpPercent: 0, coverFactor: 14 })).toBe(0);
  });

  it('never reads past a quarter, whatever it is given', () => {
    expect(estimatePullIn({ weftCrimpPercent: 500, coverFactor: 1 })).toBe(0.25);
  });
});

describe('templeGrip and templeFits', () => {
  it('holds half the loss at each side', () => {
    expect(templeGrip({ reedWidthCm: 200, clothWidthCm: 190 })).toBe(5);
  });

  it('fits when the temple reaches', () => {
    const reading = templeFits({ reedWidthCm: 200, clothWidthCm: 190, maxGripCm: 6 });
    expect(reading.fits).toBe(true);
    expect(assertTempleFits({ reedWidthCm: 200, clothWidthCm: 190, maxGripCm: 6 }).fits).toBe(true);
  });

  it('refuses a grip past what the temple holds', () => {
    const options = { reedWidthCm: 200, clothWidthCm: 170, maxGripCm: 6 };
    expect(templeFits(options).fits).toBe(false);
    expect(() => assertTempleFits(options)).toThrow(CapacityError);
  });
});

describe('pullInWarning', () => {
  it('is quiet inside the working limit', () => {
    expect(pullInWarning(0.05)).toBeNull();
    expect(pullInWarning(0.1)).toBeNull();
  });

  it('warns past it', () => {
    expect(pullInWarning(0.15).code).toBe('excessive_pull_in');
  });

  it('takes a different limit', () => {
    expect(pullInWarning(0.06, { limit: 0.05 })).not.toBeNull();
  });
});

describe('endsUnderTemple and ringsNeeded', () => {
  it('counts the selvedge ends a temple covers', () => {
    expect(endsUnderTemple({ gripCm: 5, endsPerCm: 24 })).toBe(120);
  });

  it('rounds an end up rather than losing it', () => {
    expect(endsUnderTemple({ gripCm: 0.1, endsPerCm: 24 })).toBe(3);
  });

  it('needs at least two rings whatever the width', () => {
    expect(ringsNeeded({ clothWidthCm: 1 })).toBe(2);
    expect(ringsNeeded({ clothWidthCm: 100 })).toBe(20);
    expect(ringsNeeded({ clothWidthCm: 100, ringWidthCm: 10 })).toBe(10);
  });
});

describe('assertTempleKind', () => {
  it('names the kinds a shed fits', () => {
    expect(TEMPLE_KINDS).toEqual(['ring', 'roller', 'full width']);
    expect(assertTempleKind('roller')).toBe('roller');
    expect(() => assertTempleKind('clamp')).toThrow(InvalidArgument);
  });
});
