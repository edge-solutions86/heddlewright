import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  windingVolumeCm3,
  beamCapacityKg,
  beamCapacityMetres,
  buildUpCm,
  woundDiameterCm,
  assertFitsBeam,
  fillShare,
  isWorthBeaming,
} from '../../src/warp/beam.js';

const BEAM = { barrelDiameterCm: 20, flangeDiameterCm: 60, widthCm: 180 };
const WARP = { ends: 3000, tex: 30 };

describe('windingVolumeCm3', () => {
  it('is the ring between the barrel and the flange, times the width', () => {
    const wanted = Math.PI * (30 ** 2 - 10 ** 2) * 180;
    expect(windingVolumeCm3(BEAM)).toBeCloseTo(wanted, 2);
  });

  it('grows with the flange', () => {
    expect(windingVolumeCm3({ ...BEAM, flangeDiameterCm: 80 })).toBeGreaterThan(
      windingVolumeCm3(BEAM),
    );
  });

  it('refuses a flange no wider than the barrel', () => {
    expect(() => windingVolumeCm3({ ...BEAM, flangeDiameterCm: 20 })).toThrow(InvalidArgument);
  });
});

describe('beamCapacityKg', () => {
  it('is the volume at the packing density', () => {
    expect(beamCapacityKg(BEAM)).toBe(roundThree((windingVolumeCm3(BEAM) * 0.5) / 1000));
  });

  it('scales with the density a shed measures', () => {
    // Compared loosely on purpose: both readings are rounded to three places,
    // so doubling one is not the same number as rounding the doubled one.
    expect(beamCapacityKg(BEAM, { packingDensity: 1 })).toBeCloseTo(beamCapacityKg(BEAM) * 2, 2);
  });
});

describe('beamCapacityMetres', () => {
  it('turns the weight into a warp length', () => {
    const metres = beamCapacityMetres(BEAM, WARP);
    expect(metres).toBeGreaterThan(0);
    const grams = (WARP.ends * WARP.tex * metres) / 1000;
    expect(grams / 1000).toBeCloseTo(beamCapacityKg(BEAM), 0);
  });

  it('holds less of a coarser yarn', () => {
    expect(beamCapacityMetres(BEAM, { ...WARP, tex: 60 })).toBeLessThan(
      beamCapacityMetres(BEAM, WARP),
    );
  });

  it('holds less of a wider warp', () => {
    expect(beamCapacityMetres(BEAM, { ...WARP, ends: 6000 })).toBeLessThan(
      beamCapacityMetres(BEAM, WARP),
    );
  });
});

describe('buildUpCm and woundDiameterCm', () => {
  it('is nothing on an empty beam and grows with the warp', () => {
    const short = buildUpCm(BEAM, { ...WARP, metres: 100 });
    const long = buildUpCm(BEAM, { ...WARP, metres: 1000 });
    expect(short).toBeGreaterThan(0);
    expect(long).toBeGreaterThan(short);
  });

  it('adds the build-up to the barrel', () => {
    const options = { ...WARP, metres: 500 };
    expect(woundDiameterCm(BEAM, options)).toBeCloseTo(20 + buildUpCm(BEAM, options), 4);
  });

  it('reaches the flange at about the capacity the weight calculation gives', () => {
    const metres = beamCapacityMetres(BEAM, WARP);
    expect(woundDiameterCm(BEAM, { ...WARP, metres })).toBeCloseTo(60, 1);
  });
});

describe('assertFitsBeam', () => {
  it('passes a warp that fits', () => {
    expect(assertFitsBeam(BEAM, { ...WARP, metres: 1000 })).toBeLessThan(60);
  });

  it('refuses one that would wind past the flanges, and says by how much', () => {
    try {
      assertFitsBeam(BEAM, { ...WARP, metres: 100000 });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail.over).toBeGreaterThan(0);
    }
  });
});

describe('fillShare and isWorthBeaming', () => {
  it('reads an empty beam near nothing and a full one near one', () => {
    expect(fillShare(BEAM, { ...WARP, metres: 10 })).toBeLessThan(0.05);
    const full = beamCapacityMetres(BEAM, WARP);
    expect(fillShare(BEAM, { ...WARP, metres: full })).toBeCloseTo(1, 1);
  });

  it('calls a thin beam not worth the change-over', () => {
    expect(isWorthBeaming(BEAM, { ...WARP, metres: 100 })).toBe(false);
    const full = beamCapacityMetres(BEAM, WARP);
    expect(isWorthBeaming(BEAM, { ...WARP, metres: full })).toBe(true);
  });

  it('takes a different minimum', () => {
    const options = { ...WARP, metres: 2000 };
    expect(isWorthBeaming(BEAM, options, { minimumShare: 0 })).toBe(true);
    expect(isWorthBeaming(BEAM, options, { minimumShare: 1 })).toBe(false);
  });
});

function roundThree(value) {
  return Math.round(value * 1000) / 1000;
}
