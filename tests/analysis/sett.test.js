import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { plainWeave } from '../../src/weave/plain.js';
import { twillWeave } from '../../src/weave/twill.js';
import { satinWeave } from '../../src/weave/satin.js';
import {
  maximumSett,
  ashenhurstSett,
  settFactor,
  workingSett,
  coverFactor,
  isJammed,
  endsForWidth,
  widthForEnds,
} from '../../src/analysis/sett.js';

describe('maximumSett', () => {
  it('is twenty eight root Ne, the diameters that fit in an inch', () => {
    expect(maximumSett(16, 'ne')).toBe(112);
    expect(maximumSett(4, 'ne')).toBe(56);
  });
});

describe('settFactor', () => {
  it('is a half for plain weave, which crosses on every thread', () => {
    expect(settFactor(plainWeave())).toEqual({ warp: 0.5, weft: 0.5 });
  });

  it('rises through twill to satin as the crossings thin out', () => {
    const plain = settFactor(plainWeave()).warp;
    const twill = settFactor(twillWeave('2/2')).warp;
    const satin = settFactor(satinWeave(8, 3)).warp;
    expect(twill).toBeGreaterThan(plain);
    expect(satin).toBeGreaterThan(twill);
  });

  it('reads two thirds for a 2/2 twill and four fifths for an eight thread satin', () => {
    expect(settFactor(twillWeave('2/2')).warp).toBeCloseTo(4 / 6, 3);
    expect(settFactor(satinWeave(8, 3)).warp).toBeCloseTo(8 / 10, 3);
  });
});

describe('ashenhurstSett', () => {
  it('sets plain weave at half the diameters', () => {
    const sett = ashenhurstSett(plainWeave(), 16, {});
    expect(sett.diameters).toBe(112);
    expect(sett.warp).toBe(56);
    expect(sett.weft).toBe(56);
  });

  it('sets a satin closer than a twill in the same yarn', () => {
    const twill = ashenhurstSett(twillWeave('2/2'), 30).warp;
    const satin = ashenhurstSett(satinWeave(8, 3), 30).warp;
    expect(satin).toBeGreaterThan(twill);
  });

  it('is the sett factor times the diameters', () => {
    const cloth = twillWeave('3/1');
    const sett = ashenhurstSett(cloth, 24);
    const factor = settFactor(cloth);
    expect(sett.warp).toBeCloseTo(factor.warp * sett.diameters, 1);
  });

  it('takes a different diameter constant', () => {
    const wide = ashenhurstSett(plainWeave(), 16, { constant: 25 });
    expect(wide.diameters).toBe(100);
    expect(wide.warp).toBe(50);
  });
});

describe('workingSett', () => {
  it('takes two thirds of the theoretical figure by default', () => {
    expect(workingSett(60)).toBe(40.2);
  });

  it('takes whatever share the designer asks for', () => {
    expect(workingSett(60, { share: 0.5 })).toBe(30);
    expect(workingSett(60, { share: 1 })).toBe(60);
  });

  it('refuses a share of zero', () => {
    expect(() => workingSett(60, { share: 0 })).toThrow(InvalidArgument);
  });
});

describe('coverFactor', () => {
  it('reads each direction as threads over root count', () => {
    const cover = coverFactor({
      endsPerInch: 60,
      picksPerInch: 60,
      warpCount: 36,
      weftCount: 36,
    });
    expect(cover.warp).toBe(10);
    expect(cover.weft).toBe(10);
  });

  it('takes off the overlap when it adds the two', () => {
    const cover = coverFactor({
      endsPerInch: 60,
      picksPerInch: 60,
      warpCount: 36,
      weftCount: 36,
    });
    expect(cover.cloth).toBe(roundToTwo(10 + 10 - (10 * 10) / 28));
  });

  it('rises with a closer sett', () => {
    const open = coverFactor({ endsPerInch: 40, picksPerInch: 40, warpCount: 36, weftCount: 36 });
    const close = coverFactor({ endsPerInch: 80, picksPerInch: 80, warpCount: 36, weftCount: 36 });
    expect(close.cloth).toBeGreaterThan(open.cloth);
  });

  it('refuses a count of zero', () => {
    expect(() =>
      coverFactor({ endsPerInch: 60, picksPerInch: 60, warpCount: 0, weftCount: 36 }),
    ).toThrow(InvalidArgument);
  });
});

describe('isJammed', () => {
  it('is true once a direction reaches twenty eight', () => {
    expect(isJammed({ warp: 28, weft: 10 })).toBe(true);
    expect(isJammed({ warp: 10, weft: 30 })).toBe(true);
    expect(isJammed({ warp: 10, weft: 12 })).toBe(false);
  });
});

describe('endsForWidth and widthForEnds', () => {
  it('multiplies and divides by the sett', () => {
    expect(endsForWidth(20, 24)).toBe(480);
    expect(widthForEnds(480, 24)).toBe(20);
  });

  it('rounds an end count to a whole thread', () => {
    expect(endsForWidth(20.5, 24)).toBe(492);
    expect(endsForWidth(10.02, 24)).toBe(240);
  });

  it('refuses a sett of zero', () => {
    expect(() => endsForWidth(20, 0)).toThrow(InvalidArgument);
  });
});

function roundToTwo(value) {
  return Math.round(value * 100) / 100;
}
