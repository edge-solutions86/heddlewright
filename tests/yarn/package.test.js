import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  PACKAGE_KINDS,
  createPackage,
  runsForMinutes,
  packagesFor,
  usableMetres,
  assertFitsCreel,
  deliveryTotals,
  deliveryCost,
} from '../../src/yarn/package.js';

const CONE = createPackage({ grossKg: 2.05, tareKg: 0.05, count: 20, system: 'tex' });

describe('createPackage', () => {
  it('works the net weight and the length out from the count', () => {
    expect(CONE.netKg).toBe(2);
    expect(CONE.metres).toBe(100000);
    expect(CONE.kind).toBe('cone');
  });

  it('names the kinds it knows', () => {
    expect(PACKAGE_KINDS).toEqual(['cone', 'cheese', 'pirn', 'spool', 'beam']);
    expect(() => createPackage({ ...CONE, kind: 'sack' })).toThrow(InvalidArgument);
  });

  it('reads a count in another system', () => {
    const inNe = createPackage({ grossKg: 2, count: 30, system: 'ne' });
    expect(inNe.metres).toBeGreaterThan(CONE.metres);
  });

  it('refuses packing that weighs as much as the yarn', () => {
    expect(() => createPackage({ grossKg: 1, tareKg: 1, count: 20 })).toThrow(/packing weighs/);
  });

  it('freezes the record', () => {
    expect(Object.isFrozen(CONE)).toBe(true);
  });
});

describe('runsForMinutes', () => {
  it('divides the length by the rate it is used at', () => {
    expect(runsForMinutes(CONE, { metresPerMinute: 500 })).toBe(200);
  });

  it('refuses a rate of nothing', () => {
    expect(() => runsForMinutes(CONE, { metresPerMinute: 0 })).toThrow(InvalidArgument);
  });
});

describe('packagesFor', () => {
  it('rounds up to a whole package and says what is left on the last', () => {
    const needed = packagesFor(CONE, 250000);
    expect(needed.packages).toBe(3);
    expect(needed.leftOverMetres).toBe(50000);
    expect(needed.totalKg).toBe(6);
  });

  it('needs one package for anything up to its length', () => {
    expect(packagesFor(CONE, 100000).packages).toBe(1);
    expect(packagesFor(CONE, 100001).packages).toBe(2);
  });

  it('leaves nothing over when the length divides exactly', () => {
    expect(packagesFor(CONE, 200000).leftOverMetres).toBe(0);
  });
});

describe('usableMetres', () => {
  it('takes the tail off', () => {
    expect(usableMetres(CONE)).toBe(98000);
    expect(usableMetres(CONE, { wastePercent: 0 })).toBe(100000);
  });

  it('refuses a waste share over everything', () => {
    expect(() => usableMetres(CONE, { wastePercent: 101 })).toThrow(InvalidArgument);
  });
});

describe('assertFitsCreel', () => {
  it('passes a package the peg takes', () => {
    expect(assertFitsCreel(CONE, { maxKg: 3 })).toBe(CONE);
  });

  it('refuses one that is too heavy', () => {
    expect(() => assertFitsCreel(CONE, { maxKg: 1 })).toThrow(CapacityError);
  });

  it('refuses one that is too wide, when a width is given', () => {
    const options = { maxKg: 3, maxDiameterMm: 150, diameterMm: 200 };
    expect(() => assertFitsCreel(CONE, options)).toThrow(/wider than the creel/);
  });

  it('ignores the width when nobody measured it', () => {
    expect(assertFitsCreel(CONE, { maxKg: 3, maxDiameterMm: 150 })).toBe(CONE);
  });
});

describe('deliveryTotals and deliveryCost', () => {
  const delivery = [CONE, CONE, createPackage({ grossKg: 1.05, tareKg: 0.05, count: 20 })];

  it('adds the weights and the lengths', () => {
    const totals = deliveryTotals(delivery);
    expect(totals.packages).toBe(3);
    expect(totals.netKg).toBe(5);
    expect(totals.metres).toBe(250000);
  });

  it('prices on the net weight, not the gross', () => {
    expect(deliveryCost(delivery, { pricePerKg: 4 })).toBe(20);
  });

  it('refuses an empty delivery', () => {
    expect(() => deliveryTotals([])).toThrow(InvalidArgument);
  });
});
