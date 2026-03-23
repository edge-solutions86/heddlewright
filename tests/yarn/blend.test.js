import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  normaliseBlend,
  addsToWhole,
  labelShares,
  labelText,
  weightedProperty,
  batchWeights,
  conditionedWeight,
  blendRegain,
  withinTolerance,
} from '../../src/yarn/blend.js';

const POLYCOTTON = [
  { fibre: 'cotton', share: 65 },
  { fibre: 'polyester', share: 35 },
];

describe('normaliseBlend', () => {
  it('turns the shares into fractions that add to one', () => {
    const parts = normaliseBlend(POLYCOTTON);
    expect(parts[0].share).toBeCloseTo(0.65, 6);
    expect(parts.reduce((total, part) => total + part.share, 0)).toBeCloseTo(1, 10);
  });

  it('normalises shares that were never percentages', () => {
    const parts = normaliseBlend([
      { fibre: 'wool', share: 3 },
      { fibre: 'nylon', share: 1 },
    ]);
    expect(parts[0].share).toBe(0.75);
  });

  it('refuses the same fibre twice', () => {
    const doubled = [...POLYCOTTON, { fibre: 'cotton', share: 5 }];
    expect(() => normaliseBlend(doubled)).toThrow(/in the blend twice/);
  });

  it('refuses a share of nothing and an empty blend', () => {
    expect(() => normaliseBlend([{ fibre: 'cotton', share: 0 }])).toThrow(InvalidArgument);
    expect(() => normaliseBlend([])).toThrow(InvalidArgument);
  });
});

describe('addsToWhole', () => {
  it('is true when the shares are already percentages', () => {
    expect(addsToWhole(POLYCOTTON)).toBe(true);
  });

  it('is false when they are not', () => {
    expect(addsToWhole([{ fibre: 'wool', share: 3 }])).toBe(false);
  });
});

describe('labelShares and labelText', () => {
  it('writes whole percentages, largest first', () => {
    expect(labelShares(POLYCOTTON)).toEqual([
      { fibre: 'cotton', percent: 65 },
      { fibre: 'polyester', percent: 35 },
    ]);
  });

  it('always adds to a hundred, even when the rounding does not', () => {
    const thirds = [
      { fibre: 'a', share: 1 },
      { fibre: 'b', share: 1 },
      { fibre: 'c', share: 1 },
    ];
    const shares = labelShares(thirds);
    expect(shares.reduce((total, part) => total + part.percent, 0)).toBe(100);
  });

  it('gives the rounding remainder to the largest fibre', () => {
    const shares = labelShares([
      { fibre: 'a', share: 1 },
      { fibre: 'b', share: 1 },
      { fibre: 'c', share: 1 },
    ]);
    expect(shares[0].percent).toBe(34);
  });

  it('writes the ticket', () => {
    expect(labelText(POLYCOTTON)).toBe('65% cotton 35% polyester');
  });
});

describe('weightedProperty', () => {
  it('averages by weight, not by fibre count', () => {
    const regain = weightedProperty(POLYCOTTON, { cotton: 8.5, polyester: 0.4 });
    expect(regain).toBeCloseTo(0.65 * 8.5 + 0.35 * 0.4, 4);
  });

  it('refuses a fibre with no value against it', () => {
    expect(() => weightedProperty(POLYCOTTON, { cotton: 8.5 })).toThrow(/no value given for/);
  });

  it('is the value itself for a single fibre', () => {
    expect(weightedProperty([{ fibre: 'cotton', share: 100 }], { cotton: 8.5 })).toBe(8.5);
  });
});

describe('batchWeights', () => {
  it('splits a batch by the blend', () => {
    expect(batchWeights(POLYCOTTON, { totalKg: 1000 })).toEqual([
      { fibre: 'cotton', kilograms: 650 },
      { fibre: 'polyester', kilograms: 350 },
    ]);
  });

  it('adds back to the batch', () => {
    const weights = batchWeights(POLYCOTTON, { totalKg: 777 });
    expect(weights.reduce((total, part) => total + part.kilograms, 0)).toBeCloseTo(777, 3);
  });
});

describe('conditionedWeight and blendRegain', () => {
  it('adds the agreed regain to the dry weight', () => {
    expect(conditionedWeight({ ovenDryKg: 1000, regainPercent: 8.5 })).toBe(1085);
    expect(conditionedWeight({ ovenDryKg: 1000, regainPercent: 0 })).toBe(1000);
  });

  it('averages the regain across the blend', () => {
    const regain = blendRegain(POLYCOTTON, { cotton: 8.5, polyester: 0.4 });
    expect(regain).toBeLessThan(8.5);
    expect(regain).toBeGreaterThan(0.4);
  });

  it('refuses a negative regain', () => {
    expect(() => conditionedWeight({ ovenDryKg: 1000, regainPercent: -1 })).toThrow(
      InvalidArgument,
    );
  });
});

describe('withinTolerance', () => {
  it('passes a blend on target', () => {
    expect(withinTolerance(POLYCOTTON, POLYCOTTON).within).toBe(true);
  });

  it('passes one inside the allowance', () => {
    const drifted = [
      { fibre: 'cotton', share: 67 },
      { fibre: 'polyester', share: 33 },
    ];
    expect(withinTolerance(drifted, POLYCOTTON).within).toBe(true);
  });

  it('fails one outside it, naming the fibre and how far off', () => {
    const drifted = [
      { fibre: 'cotton', share: 55 },
      { fibre: 'polyester', share: 45 },
    ];
    const reading = withinTolerance(drifted, POLYCOTTON);
    expect(reading.within).toBe(false);
    expect(reading.findings[0]).toMatchObject({ fibre: 'cotton', wanted: 65, got: 55, off: -10 });
  });

  it('counts a fibre missing altogether as nothing', () => {
    const only = [{ fibre: 'cotton', share: 100 }];
    const reading = withinTolerance(only, POLYCOTTON);
    expect(reading.findings.some((finding) => finding.fibre === 'polyester')).toBe(true);
  });
});
