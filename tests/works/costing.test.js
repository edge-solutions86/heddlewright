import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  yarnCostPerMetre,
  loomCostPerMetre,
  withWaste,
  costPerMetre,
  priceForMargin,
  marginAtPrice,
  compare,
} from '../../src/works/costing.js';

const CLOTH = {
  endsPerCm: 24,
  picksPerCm: 20,
  warpCount: 20,
  weftCount: 20,
  system: 'tex',
};

const PRICES = { warpPricePerKg: 5, weftPricePerKg: 4 };
const LOOM = { picksMinute: 200, picksPerCm: 20, ratePerHour: 18 };

const JOB = {
  construction: CLOTH,
  prices: PRICES,
  loom: LOOM,
  widthMetres: 1.5,
};

describe('yarnCostPerMetre', () => {
  it('costs the warp and the weft separately', () => {
    const cost = yarnCostPerMetre(CLOTH, PRICES, { widthMetres: 1.5 });
    expect(cost.warp).toBe(0.36);
    expect(cost.weft).toBe(0.24);
    expect(cost.total).toBe(0.6);
    expect(cost.gsm).toBe(88);
  });

  it('scales with the width', () => {
    const narrow = yarnCostPerMetre(CLOTH, PRICES, { widthMetres: 1 });
    const wide = yarnCostPerMetre(CLOTH, PRICES, { widthMetres: 2 });
    expect(wide.total).toBeCloseTo(narrow.total * 2, 4);
  });

  it('refuses a price of nothing', () => {
    const bad = { warpPricePerKg: 0, weftPricePerKg: 4 };
    expect(() => yarnCostPerMetre(CLOTH, bad, { widthMetres: 1 })).toThrow(InvalidArgument);
  });
});

describe('loomCostPerMetre', () => {
  it('is the hourly rate over the metres an hour', () => {
    expect(loomCostPerMetre(LOOM)).toBe(roundFour(18 / (((200 * 60) / 20 / 100) * 0.85)));
  });

  it('costs more at a closer sett, because the loom makes less cloth', () => {
    expect(loomCostPerMetre({ ...LOOM, picksPerCm: 40 })).toBeCloseTo(
      loomCostPerMetre(LOOM) * 2,
      3,
    );
  });

  it('costs more at a lower efficiency', () => {
    expect(loomCostPerMetre({ ...LOOM, efficiency: 0.5 })).toBeGreaterThan(loomCostPerMetre(LOOM));
  });

  it('refuses an efficiency of nothing, which never finishes', () => {
    expect(() => loomCostPerMetre({ ...LOOM, efficiency: 0 })).toThrow(InvalidArgument);
  });
});

describe('withWaste', () => {
  it('adds the shed allowance', () => {
    expect(withWaste(1)).toBe(1.04);
    expect(withWaste(1, { wasteShare: 0.1 })).toBe(1.1);
    expect(withWaste(1, { wasteShare: 0 })).toBe(1);
  });
});

describe('costPerMetre', () => {
  it('adds the yarn, the weaving and the overheads', () => {
    const cost = costPerMetre({ ...JOB, overheads: 0.2 });
    expect(cost.total).toBeCloseTo(cost.yarnWithWaste + cost.weaving + 0.2, 4);
    expect(cost.overheads).toBe(0.2);
  });

  it('reads the loom as most of the cost at a close sett, not the yarn', () => {
    // Twenty picks a centimetre at two hundred picks a minute is about five
    // metres an hour, so an hour of loom time buys very little cloth.
    expect(costPerMetre(JOB).yarnShare).toBeLessThan(0.25);
  });

  it('hands the cost back to the yarn once the loom time is cheap', () => {
    const cheapLoom = { ...JOB, loom: { ...LOOM, ratePerHour: 1 } };
    expect(costPerMetre(cheapLoom).yarnShare).toBeGreaterThan(0.7);
  });

  it('carries the cloth weight through, so it can be checked', () => {
    expect(costPerMetre(JOB).gsm).toBe(88);
  });
});

describe('priceForMargin and marginAtPrice', () => {
  it('are inverses of each other', () => {
    const price = priceForMargin(1, { margin: 0.25 });
    expect(price).toBeCloseTo(1.3333, 3);
    expect(marginAtPrice(1, price)).toBeCloseTo(0.25, 3);
  });

  it('a margin of nothing prices at cost', () => {
    expect(priceForMargin(2, { margin: 0 })).toBe(2);
    expect(marginAtPrice(2, 2)).toBe(0);
  });

  it('refuses a price below the cost', () => {
    expect(() => marginAtPrice(2, 1)).toThrow(/below the cost/);
  });
});

describe('compare', () => {
  it('says which term a change moved', () => {
    const dearerYarn = {
      ...JOB,
      prices: { warpPricePerKg: 10, weftPricePerKg: 4 },
    };
    const comparison = compare(JOB, dearerYarn);
    expect(comparison.movedBy).toBe('yarn');
    expect(comparison.difference).toBeGreaterThan(0);
  });

  it('says weaving when the loom time moved instead', () => {
    const slower = { ...JOB, loom: { ...LOOM, picksMinute: 60 } };
    expect(compare(JOB, slower).movedBy).toBe('weaving');
  });

  it('reports no difference for the same job twice', () => {
    expect(compare(JOB, JOB).difference).toBe(0);
  });
});

function roundFour(value) {
  return Math.round(value * 10000) / 10000;
}
