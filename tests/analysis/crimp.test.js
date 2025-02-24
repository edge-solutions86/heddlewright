import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  crimpPercent,
  takeUpPercent,
  takeUpFromCrimp,
  crimpFromTakeUp,
  yarnForCloth,
  clothFromYarn,
  warpLength,
  splitCrimp,
  warpYarnMetres,
  weftYarnMetres,
} from '../../src/analysis/crimp.js';

describe('crimpPercent and takeUpPercent', () => {
  it('measure the same fact against different lengths', () => {
    expect(crimpPercent(110, 100)).toBe(10);
    expect(takeUpPercent(110, 100)).toBeCloseTo(9.0909, 3);
  });

  it('are both zero for a thread that lies straight', () => {
    expect(crimpPercent(100, 100)).toBe(0);
    expect(takeUpPercent(100, 100)).toBe(0);
  });

  it('refuse yarn shorter than the cloth, which cannot happen', () => {
    expect(() => crimpPercent(90, 100)).toThrow(InvalidArgument);
    expect(() => takeUpPercent(90, 100)).toThrow(InvalidArgument);
  });
});

describe('takeUpFromCrimp and crimpFromTakeUp', () => {
  it('agree with the direct measurements', () => {
    expect(takeUpFromCrimp(10)).toBeCloseTo(takeUpPercent(110, 100), 4);
    expect(crimpFromTakeUp(takeUpPercent(110, 100))).toBeCloseTo(10, 3);
  });

  it('are inverses of each other', () => {
    for (const crimp of [0, 5, 12.5, 40]) {
      expect(crimpFromTakeUp(takeUpFromCrimp(crimp))).toBeCloseTo(crimp, 3);
    }
  });

  it('take-up is always the smaller of the two', () => {
    for (const crimp of [1, 8, 25]) {
      expect(takeUpFromCrimp(crimp)).toBeLessThan(crimp);
    }
  });

  it('refuse a take-up of a hundred per cent, which has no crimp reading', () => {
    expect(() => crimpFromTakeUp(100)).toThrow(InvalidArgument);
  });
});

describe('yarnForCloth and clothFromYarn', () => {
  it('add and remove the crimp', () => {
    expect(yarnForCloth(100, 8)).toBe(108);
    expect(clothFromYarn(108, 8)).toBe(100);
  });

  it('leave the length alone at no crimp', () => {
    expect(yarnForCloth(50, 0)).toBe(50);
  });
});

describe('warpLength', () => {
  it('adds crimp, shrinkage and loom waste in that order', () => {
    const plan = warpLength({ clothLength: 10, crimp: 10, loomWaste: 1, shrinkage: 0 });
    expect(plan.finished).toBe(10);
    expect(plan.woven).toBe(11);
    expect(plan.total).toBe(12);
  });

  it('allows for finished shrinkage before the crimp', () => {
    const plan = warpLength({ clothLength: 9, crimp: 0, shrinkage: 10 });
    expect(plan.finished).toBe(10);
    expect(plan.woven).toBe(10);
  });

  it('needs nothing but a cloth length', () => {
    expect(warpLength({ clothLength: 5 })).toEqual({
      finished: 5,
      woven: 5,
      total: 5,
      loomWaste: 0,
    });
  });

  it('refuses a shrinkage of a hundred per cent', () => {
    expect(() => warpLength({ clothLength: 5, shrinkage: 100 })).toThrow(InvalidArgument);
  });
});

describe('splitCrimp', () => {
  it('splits evenly on a square sett', () => {
    expect(splitCrimp(10, { endsPerInch: 24, picksPerInch: 24 })).toEqual({ warp: 5, weft: 5 });
  });

  it('gives the crimp to the system with fewer threads holding it straight', () => {
    const split = splitCrimp(12, { endsPerInch: 60, picksPerInch: 20 });
    expect(split.weft).toBeGreaterThan(split.warp);
    expect(split.warp + split.weft).toBeCloseTo(12, 6);
  });
});

describe('warpYarnMetres', () => {
  it('multiplies the ends by the crimped length', () => {
    expect(warpYarnMetres({ ends: 500, lengthMetres: 10, crimp: 8 })).toBe(5400);
  });

  it('ignores crimp when there is none', () => {
    expect(warpYarnMetres({ ends: 100, lengthMetres: 5 })).toBe(500);
  });
});

describe('weftYarnMetres', () => {
  it('multiplies picks by the crimped width', () => {
    expect(weftYarnMetres({ picksPerMetre: 800, lengthMetres: 10, widthMetres: 1.5 })).toBe(12000);
  });

  it('adds the weft crimp to the width', () => {
    const straight = weftYarnMetres({ picksPerMetre: 100, lengthMetres: 1, widthMetres: 1 });
    const crimped = weftYarnMetres({
      picksPerMetre: 100,
      lengthMetres: 1,
      widthMetres: 1,
      crimp: 10,
    });
    expect(crimped).toBeCloseTo(straight * 1.1, 6);
  });
});
