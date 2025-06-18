import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  warpWeightGsm,
  weftWeightGsm,
  clothWeightGsm,
  gsmToOuncesPerSquareYard,
  ouncesPerSquareYardToGsm,
  pieceWeightKg,
  gramsPerLinearMetre,
  yarnCostPerSquareMetre,
  picksForWeight,
} from '../../src/analysis/weight.js';

const SHIRTING = {
  endsPerCm: 24,
  picksPerCm: 20,
  warpCount: 20,
  weftCount: 20,
  system: 'tex',
};

describe('warpWeightGsm and weftWeightGsm', () => {
  it('are a tenth of the threads per cm times the tex', () => {
    expect(warpWeightGsm(SHIRTING)).toBe(48);
    expect(weftWeightGsm(SHIRTING)).toBe(40);
  });

  it('rise with the crimp', () => {
    expect(warpWeightGsm({ ...SHIRTING, warpCrimp: 10 })).toBe(52.8);
    expect(weftWeightGsm({ ...SHIRTING, weftCrimp: 5 })).toBe(42);
  });

  it('read a count in another system by converting first', () => {
    const asTex = warpWeightGsm({ ...SHIRTING, warpCount: 29.527, system: 'tex' });
    const asNe = warpWeightGsm({ ...SHIRTING, warpCount: 20, system: 'ne' });
    expect(asNe).toBeCloseTo(asTex, 1);
  });

  it('refuse a sett of nothing', () => {
    expect(() => warpWeightGsm({ ...SHIRTING, endsPerCm: 0 })).toThrow(InvalidArgument);
  });
});

describe('clothWeightGsm', () => {
  it('adds the two systems and says which carries the weight', () => {
    const weight = clothWeightGsm(SHIRTING);
    expect(weight.total).toBe(88);
    expect(weight.warpShare).toBeCloseTo(48 / 88, 4);
  });

  it('shifts the share when one system is set closer', () => {
    const warpHeavy = clothWeightGsm({ ...SHIRTING, endsPerCm: 40 });
    expect(warpHeavy.warpShare).toBeGreaterThan(clothWeightGsm(SHIRTING).warpShare);
  });
});

describe('gsm and ounces', () => {
  it('converts a common shirting weight', () => {
    expect(gsmToOuncesPerSquareYard(339)).toBeCloseTo(10, 1);
  });

  it('round-trips both ways', () => {
    expect(ouncesPerSquareYardToGsm(gsmToOuncesPerSquareYard(250))).toBeCloseTo(250, 1);
  });

  it('refuses a weight of nothing', () => {
    expect(() => gsmToOuncesPerSquareYard(0)).toThrow(InvalidArgument);
  });
});

describe('pieceWeightKg', () => {
  it('multiplies the area by the weight', () => {
    expect(pieceWeightKg({ gsm: 200, metres: 50, widthMetres: 1.5 })).toBe(15);
  });

  it('scales with every term', () => {
    const base = pieceWeightKg({ gsm: 200, metres: 50, widthMetres: 1.5 });
    expect(pieceWeightKg({ gsm: 400, metres: 50, widthMetres: 1.5 })).toBe(base * 2);
    expect(pieceWeightKg({ gsm: 200, metres: 100, widthMetres: 1.5 })).toBe(base * 2);
  });
});

describe('gramsPerLinearMetre', () => {
  it('is the running weight at the cloth width', () => {
    expect(gramsPerLinearMetre({ gsm: 200, widthMetres: 1.5 })).toBe(300);
  });
});

describe('yarnCostPerSquareMetre', () => {
  it('follows the weight split', () => {
    const cost = yarnCostPerSquareMetre(SHIRTING, { warpPricePerKg: 5, weftPricePerKg: 5 });
    expect(cost.warp).toBe(0.24);
    expect(cost.weft).toBe(0.2);
    expect(cost.total).toBe(0.44);
  });

  it('rises when the warp yarn is the dear one on a warp-heavy cloth', () => {
    const cheapWarp = yarnCostPerSquareMetre(SHIRTING, { warpPricePerKg: 4, weftPricePerKg: 8 });
    const dearWarp = yarnCostPerSquareMetre(SHIRTING, { warpPricePerKg: 8, weftPricePerKg: 4 });
    expect(dearWarp.total).toBeGreaterThan(cheapWarp.total);
  });
});

describe('picksForWeight', () => {
  it('works out the picks a target weight needs', () => {
    const picks = picksForWeight({ targetGsm: 88, ...SHIRTING });
    expect(picks).toBe(20);
  });

  it('agrees with the weight calculation it inverts', () => {
    const picks = picksForWeight({ targetGsm: 120, ...SHIRTING });
    const weight = clothWeightGsm({ ...SHIRTING, picksPerCm: picks });
    expect(weight.total).toBeCloseTo(120, 1);
  });

  it('is nothing when the warp alone already weighs more than the target', () => {
    expect(picksForWeight({ targetGsm: 10, ...SHIRTING })).toBe(0);
  });
});
