import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  stockChangeWheels,
  takeUpDividend,
  picksPerInch,
  clothPerPick,
  exactChangeWheel,
  bestChangeWheel,
  settingCard,
  settRange,
  assertSettReachable,
} from '../../src/loom/takeup.js';

const TRAIN = {
  ratchetTeeth: 50,
  carrierTeeth: 89,
  studTeeth: 15,
  rollerWheelTeeth: 90,
  rollerDiameterInches: 15 / Math.PI,
};

describe('takeUpDividend', () => {
  it('collapses the whole train into one number', () => {
    expect(takeUpDividend(TRAIN)).toBe(1780);
  });

  it('rises with the ratchet and falls with the stud wheel', () => {
    const base = takeUpDividend(TRAIN);
    expect(takeUpDividend({ ...TRAIN, ratchetTeeth: 100 })).toBeGreaterThan(base);
    expect(takeUpDividend({ ...TRAIN, studTeeth: 30 })).toBeLessThan(base);
  });

  it('falls as the roller gets fatter', () => {
    const base = takeUpDividend(TRAIN);
    expect(takeUpDividend({ ...TRAIN, rollerDiameterInches: 10 })).toBeLessThan(base);
  });

  it('refuses a wheel with no teeth', () => {
    expect(() => takeUpDividend({ ...TRAIN, studTeeth: 0 })).toThrow(InvalidArgument);
  });
});

describe('picksPerInch', () => {
  it('is the dividend over the change wheel', () => {
    expect(picksPerInch(1780, 40)).toBe(44.5);
    expect(picksPerInch(1780, 89)).toBe(20);
  });

  it('gives a closer sett on a smaller wheel', () => {
    expect(picksPerInch(1780, 30)).toBeGreaterThan(picksPerInch(1780, 60));
  });

  it('refuses a wheel with a fractional tooth count', () => {
    expect(() => picksPerInch(1780, 40.5)).toThrow(InvalidArgument);
  });
});

describe('clothPerPick', () => {
  it('is the reciprocal of the sett', () => {
    expect(clothPerPick(1780, 89)).toBe(0.05);
  });
});

describe('exactChangeWheel', () => {
  it('is the wheel a wanted sett would need if teeth were continuous', () => {
    expect(exactChangeWheel(1780, 40)).toBe(44.5);
    expect(exactChangeWheel(1780, 44.5)).toBe(40);
  });
});

describe('bestChangeWheel', () => {
  it('picks the wheel that lands nearest and says how far off it is', () => {
    const best = bestChangeWheel(1780, 44.5);
    expect(best.teeth).toBe(40);
    expect(best.achieved).toBe(44.5);
    expect(best.error).toBe(0);
  });

  it('reports the error when no wheel lands exactly', () => {
    const best = bestChangeWheel(1780, 43);
    expect(best.exact).toBeCloseTo(41.395, 2);
    expect([40, 42]).toContain(best.teeth);
    expect(best.error).toBeGreaterThan(0);
    expect(best.errorPercent).toBeCloseTo((best.error / 43) * 100, 3);
  });

  it('takes a different drawer of wheels', () => {
    const best = bestChangeWheel(1780, 43, { wheels: [41] });
    expect(best.teeth).toBe(41);
  });

  it('lists the stock drawer in ascending order', () => {
    const wheels = stockChangeWheels();
    expect(wheels).toEqual([...wheels].sort((a, b) => a - b));
  });
});

describe('settingCard', () => {
  it('gives a sett for every wheel in the drawer', () => {
    const card = settingCard(1780);
    expect(card.length).toBe(stockChangeWheels().length);
    expect(card[0]).toEqual({ teeth: 24, picksPerInch: picksPerInch(1780, 24) });
  });

  it('falls as the wheels get bigger', () => {
    const card = settingCard(1780);
    for (let i = 1; i < card.length; i += 1) {
      expect(card[i].picksPerInch).toBeLessThan(card[i - 1].picksPerInch);
    }
  });
});

describe('settRange', () => {
  it('spans the drawer', () => {
    const range = settRange(1780);
    expect(range.highest).toBe(picksPerInch(1780, 24));
    expect(range.lowest).toBe(picksPerInch(1780, 60));
  });
});

describe('assertSettReachable', () => {
  it('passes a sett a wheel reaches', () => {
    expect(assertSettReachable(1780, 44.5).teeth).toBe(40);
  });

  it('refuses one nothing in the drawer comes near', () => {
    try {
      assertSettReachable(1780, 200);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.detail.wantedPicksPerInch).toBe(200);
      expect(err.detail.teeth).toBe(24);
    }
  });

  it('takes a wider tolerance', () => {
    expect(assertSettReachable(1780, 43, { tolerance: 2 }).teeth).toBeGreaterThan(0);
  });
});
