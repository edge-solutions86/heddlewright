import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  DIRECTIONS,
  twistMultiplier,
  turnsForMultiplier,
  metricTwistFactor,
  turnsPerMetre,
  turnsPerInch,
  multiplierFor,
  twistClass,
  foldedDirection,
  foldingTurns,
  twillContrast,
} from '../../src/yarn/twist.js';

describe('twistMultiplier', () => {
  it('is the turns over the square root of the count', () => {
    expect(twistMultiplier({ turnsPerInch: 20, count: 25 })).toBe(4);
  });

  it('reads a finer yarn at fewer turns as a lower multiplier', () => {
    const coarse = twistMultiplier({ turnsPerInch: 20, count: 25 });
    const fine = twistMultiplier({ turnsPerInch: 20, count: 100 });
    expect(fine).toBeLessThan(coarse);
  });

  it('inverts through turnsForMultiplier', () => {
    const turns = turnsForMultiplier({ multiplier: 4, count: 25 });
    expect(turns).toBe(20);
    expect(twistMultiplier({ turnsPerInch: turns, count: 25 })).toBeCloseTo(4, 3);
  });

  it('reads a count given in another system', () => {
    const asNe = twistMultiplier({ turnsPerInch: 20, count: 25, system: 'ne' });
    const asTex = twistMultiplier({ turnsPerInch: 20, count: 590.5414 / 25, system: 'tex' });
    expect(asTex).toBeCloseTo(asNe, 3);
  });
});

describe('metricTwistFactor', () => {
  it('is the metric reading of the same idea', () => {
    expect(metricTwistFactor({ turnsPerMetre: 800, count: 25, system: 'tex' })).toBeGreaterThan(0);
  });

  it('rises with the turns', () => {
    const low = metricTwistFactor({ turnsPerMetre: 400, count: 25, system: 'tex' });
    const high = metricTwistFactor({ turnsPerMetre: 800, count: 25, system: 'tex' });
    expect(high).toBeCloseTo(low * 2, 3);
  });
});

describe('turnsPerMetre and turnsPerInch', () => {
  it('convert both ways', () => {
    expect(turnsPerMetre(20)).toBeCloseTo(787.4, 1);
    expect(turnsPerInch(turnsPerMetre(20))).toBeCloseTo(20, 3);
  });
});

describe('multiplierFor', () => {
  it('gives the working figure for each purpose', () => {
    expect(multiplierFor('warp')).toBe(4);
    expect(multiplierFor('weft')).toBe(3);
    expect(multiplierFor('voile')).toBeGreaterThan(multiplierFor('warp'));
  });

  it('names the purposes it knows when it does not know one', () => {
    try {
      multiplierFor('rope');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidArgument);
      expect(err.detail.known).toContain('warp');
    }
  });
});

describe('twistClass', () => {
  it('calls a yarn at its working multiplier normal', () => {
    expect(twistClass({ turnsPerInch: 20, count: 25, purpose: 'warp' }).reading).toBe('normal');
  });

  it('calls a lightly twisted warp yarn soft', () => {
    expect(twistClass({ turnsPerInch: 12, count: 25, purpose: 'warp' }).reading).toBe('soft');
  });

  it('calls a heavily twisted one hard', () => {
    expect(twistClass({ turnsPerInch: 30, count: 25, purpose: 'warp' }).reading).toBe('hard');
  });

  it('reads the same yarn differently for a different purpose', () => {
    const turns = { turnsPerInch: 20, count: 25 };
    expect(twistClass({ ...turns, purpose: 'warp' }).reading).toBe('normal');
    expect(twistClass({ ...turns, purpose: 'weft' }).reading).toBe('hard');
  });
});

describe('foldedDirection and foldingTurns', () => {
  it('folds against the single twist', () => {
    expect(DIRECTIONS).toEqual(['S', 'Z']);
    expect(foldedDirection('Z')).toBe('S');
    expect(foldedDirection('S')).toBe('Z');
  });

  it('refuses a direction it does not know', () => {
    expect(() => foldedDirection('X')).toThrow(InvalidArgument);
  });

  it('folds at a share of the single twist', () => {
    expect(foldingTurns({ singleTurnsPerInch: 20 })).toBe(14);
    expect(foldingTurns({ singleTurnsPerInch: 20, share: 1 })).toBe(20);
  });

  it('refuses a folding share nobody would spin', () => {
    expect(() => foldingTurns({ singleTurnsPerInch: 20, share: 0 })).toThrow(InvalidArgument);
  });
});

describe('twillContrast', () => {
  it('is pronounced when the two systems twist opposite ways', () => {
    expect(twillContrast('Z', 'S')).toBe('pronounced');
  });

  it('flattens when the twist runs the same way as the twill', () => {
    expect(twillContrast('Z', 'Z', { twillHand: 'Z' })).toBe('flattened');
  });

  it('stands out when the twist runs against the twill', () => {
    expect(twillContrast('S', 'S', { twillHand: 'Z' })).toBe('pronounced');
  });
});
