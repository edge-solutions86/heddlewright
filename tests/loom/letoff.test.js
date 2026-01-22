import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  LET_OFF_TYPES,
  tensionFromWeight,
  tensionDrift,
  weightForTension,
  tensionPerEnd,
  withinSafeTension,
  beamTurnsPerPick,
  picksPerBeamTurn,
  assertLetOffType,
} from '../../src/loom/letoff.js';

describe('tensionFromWeight', () => {
  it('rises with the weight and the lever', () => {
    const base = tensionFromWeight({ weightKg: 10, beamDiameterCm: 40 });
    // Loosely, because each reading is rounded to three places before it is
    // doubled or trebled here.
    expect(tensionFromWeight({ weightKg: 20, beamDiameterCm: 40 })).toBeCloseTo(base * 2, 2);
    expect(tensionFromWeight({ weightKg: 10, leverRatio: 3, beamDiameterCm: 40 })).toBeCloseTo(
      base * 3,
      2,
    );
  });

  it('rises as the beam empties, because the radius shrinks', () => {
    const full = tensionFromWeight({ weightKg: 10, beamDiameterCm: 60 });
    const empty = tensionFromWeight({ weightKg: 10, beamDiameterCm: 20 });
    expect(empty).toBeGreaterThan(full);
    expect(empty / full).toBeCloseTo(3, 3);
  });

  it('refuses a weight or a beam of nothing', () => {
    expect(() => tensionFromWeight({ weightKg: 0, beamDiameterCm: 40 })).toThrow(InvalidArgument);
    expect(() => tensionFromWeight({ weightKg: 10, beamDiameterCm: 0 })).toThrow(InvalidArgument);
  });
});

describe('tensionDrift', () => {
  it('reports how far the tension moves over a beam', () => {
    const drift = tensionDrift({ weightKg: 10, fullDiameterCm: 60, emptyDiameterCm: 20 });
    expect(drift.ratio).toBeCloseTo(3, 3);
    expect(drift.risePercent).toBeCloseTo(200, 1);
  });

  it('is one when the beam does not change', () => {
    const drift = tensionDrift({ weightKg: 10, fullDiameterCm: 60, emptyDiameterCm: 59.999 });
    expect(drift.ratio).toBeCloseTo(1, 3);
  });

  it('refuses an empty beam thicker than a full one', () => {
    expect(() => tensionDrift({ weightKg: 10, fullDiameterCm: 20, emptyDiameterCm: 60 })).toThrow(
      /thinner than a full one/,
    );
  });
});

describe('weightForTension', () => {
  it('inverts the tension calculation', () => {
    const weight = weightForTension({ tensionNewtons: 245, beamDiameterCm: 40 });
    expect(tensionFromWeight({ weightKg: weight, beamDiameterCm: 40 })).toBeCloseTo(245, 1);
  });

  it('needs less weight on a thicker beam', () => {
    const thin = weightForTension({ tensionNewtons: 245, beamDiameterCm: 20 });
    const thick = weightForTension({ tensionNewtons: 245, beamDiameterCm: 60 });
    expect(thick).toBeGreaterThan(thin);
  });
});

describe('tensionPerEnd and withinSafeTension', () => {
  it('divides the tension across the warp', () => {
    expect(tensionPerEnd({ tensionNewtons: 300, ends: 3000 })).toBe(0.1);
  });

  it('passes a warp held well below its breaking load', () => {
    const reading = withinSafeTension({ tensionNewtons: 300, ends: 3000, breakingLoadN: 4 });
    expect(reading.limit).toBe(0.6);
    expect(reading.safe).toBe(true);
    expect(reading.headroom).toBeCloseTo(0.5, 5);
  });

  it('fails one held near its breaking load', () => {
    const reading = withinSafeTension({ tensionNewtons: 3000, ends: 3000, breakingLoadN: 4 });
    expect(reading.safe).toBe(false);
    expect(reading.headroom).toBeLessThan(0);
  });

  it('takes a different working share', () => {
    const options = { tensionNewtons: 300, ends: 3000, breakingLoadN: 4 };
    expect(withinSafeTension({ ...options, share: 0.05 }).safe).toBe(true);
    expect(withinSafeTension({ ...options, share: 0.01 }).safe).toBe(false);
  });
});

describe('beamTurnsPerPick', () => {
  it('turns less per pick on a fatter beam', () => {
    const thin = beamTurnsPerPick({ picksPerCm: 20, beamDiameterCm: 20 });
    const fat = beamTurnsPerPick({ picksPerCm: 20, beamDiameterCm: 60 });
    expect(fat).toBeLessThan(thin);
  });

  it('pays out more picks per turn on a fatter beam', () => {
    expect(picksPerBeamTurn({ picksPerCm: 20, beamDiameterCm: 60 })).toBeGreaterThan(
      picksPerBeamTurn({ picksPerCm: 20, beamDiameterCm: 20 }),
    );
  });

  it('is the reciprocal of the turns per pick', () => {
    const options = { picksPerCm: 20, beamDiameterCm: 40 };
    expect(picksPerBeamTurn(options) * beamTurnsPerPick(options)).toBeCloseTo(1, 4);
  });
});

describe('assertLetOffType', () => {
  it('names the two kinds', () => {
    expect(LET_OFF_TYPES).toEqual(['negative', 'positive']);
    expect(assertLetOffType('positive')).toBe('positive');
    expect(() => assertLetOffType('semi')).toThrow(InvalidArgument);
  });
});
