import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  beamWarpingPlan,
  balanceEnds,
  sectionalPlan,
  sectionSpread,
  assertSectionsEven,
  warpingYarnMetres,
  warpingHours,
} from '../../src/warp/warping.js';

describe('balanceEnds', () => {
  it('divides evenly when it can', () => {
    expect(balanceEnds(600, 3)).toEqual([200, 200, 200]);
  });

  it('gives the remainder to the earliest groups', () => {
    expect(balanceEnds(602, 3)).toEqual([201, 201, 200]);
    expect(balanceEnds(601, 3)).toEqual([201, 200, 200]);
  });

  it('always adds back to the total', () => {
    for (const [total, groups] of [
      [1000, 7],
      [4321, 9],
      [5, 5],
    ]) {
      expect(balanceEnds(total, groups).reduce((a, b) => a + b, 0)).toBe(total);
    }
  });

  it('never lets two groups differ by more than one end', () => {
    const groups = balanceEnds(4321, 9);
    expect(Math.max(...groups) - Math.min(...groups)).toBeLessThanOrEqual(1);
  });

  it('refuses more groups than there are ends', () => {
    expect(() => balanceEnds(3, 4)).toThrow(/fewer ends than groups/);
  });
});

describe('beamWarpingPlan', () => {
  it('splits a wide warp over as many back beams as the creel needs', () => {
    const plan = beamWarpingPlan({ totalEnds: 2400, creelCapacity: 600, warpLengthMetres: 1000 });
    expect(plan.beams).toBe(4);
    expect(plan.endsPerBeam).toEqual([600, 600, 600, 600]);
  });

  it('spreads an awkward end count rather than leaving a thin beam', () => {
    const plan = beamWarpingPlan({ totalEnds: 2402, creelCapacity: 600, warpLengthMetres: 1000 });
    expect(plan.beams).toBe(5);
    expect(Math.max(...plan.endsPerBeam) - Math.min(...plan.endsPerBeam)).toBeLessThanOrEqual(1);
  });

  it('needs one beam when the creel holds the whole warp', () => {
    expect(
      beamWarpingPlan({ totalEnds: 500, creelCapacity: 600, warpLengthMetres: 100 }).beams,
    ).toBe(1);
  });
});

describe('sectionalPlan', () => {
  it('works the sections and their widths out from the sett', () => {
    const plan = sectionalPlan({
      totalEnds: 2400,
      creelCapacity: 400,
      endsPerInch: 40,
      warpLengthMetres: 500,
    });
    expect(plan.sections).toBe(6);
    expect(plan.endsPerSection).toEqual([400, 400, 400, 400, 400, 400]);
    expect(plan.widthPerSectionInches[0]).toBe(10);
    expect(plan.totalWidthInches).toBe(60);
    expect(plan.evenSections).toBe(true);
  });

  it('reports uneven sections rather than hiding them', () => {
    const plan = sectionalPlan({
      totalEnds: 2402,
      creelCapacity: 400,
      endsPerInch: 40,
      warpLengthMetres: 500,
    });
    expect(plan.sections).toBe(7);
    expect(plan.evenSections).toBe(false);
  });

  it('adds the section widths back to the total width', () => {
    const plan = sectionalPlan({
      totalEnds: 2402,
      creelCapacity: 400,
      endsPerInch: 40,
      warpLengthMetres: 500,
    });
    const sum = plan.widthPerSectionInches.reduce((a, b) => a + b, 0);
    expect(sum).toBeCloseTo(plan.totalWidthInches, 2);
  });
});

describe('sectionSpread and assertSectionsEven', () => {
  const even = sectionalPlan({
    totalEnds: 2400,
    creelCapacity: 400,
    endsPerInch: 40,
    warpLengthMetres: 500,
  });
  const uneven = sectionalPlan({
    totalEnds: 2402,
    creelCapacity: 400,
    endsPerInch: 40,
    warpLengthMetres: 500,
  });

  it('measures the spread across the sections', () => {
    expect(sectionSpread(even)).toEqual({ smallest: 400, largest: 400, spread: 0 });
    expect(sectionSpread(uneven).spread).toBe(1);
  });

  it('accepts a spread of one end by default', () => {
    expect(assertSectionsEven(even)).toBe(even);
    expect(assertSectionsEven(uneven)).toBe(uneven);
  });

  it('refuses one wider than the shed allows', () => {
    expect(() => assertSectionsEven(uneven, { maxSpread: 0 })).toThrow(/too uneven/);
  });
});

describe('warpingYarnMetres', () => {
  it('adds the creel waste to the useful length', () => {
    const yarn = warpingYarnMetres({ totalEnds: 600, warpLengthMetres: 1000 });
    expect(yarn.useful).toBe(600000);
    expect(yarn.waste).toBe(1200);
    expect(yarn.total).toBe(601200);
  });

  it('charges the waste once per section', () => {
    const one = warpingYarnMetres({ totalEnds: 600, warpLengthMetres: 1000, sections: 1 });
    const six = warpingYarnMetres({ totalEnds: 600, warpLengthMetres: 1000, sections: 6 });
    expect(six.waste).toBe(one.waste * 6);
  });

  it('reads the waste share as a small fraction on a long warp', () => {
    const yarn = warpingYarnMetres({ totalEnds: 600, warpLengthMetres: 1000 });
    expect(yarn.wasteShare).toBeLessThan(0.01);
  });
});

describe('warpingHours', () => {
  it('adds the section changes to the running time', () => {
    const time = warpingHours({
      warpLengthMetres: 500,
      sections: 6,
      metresPerMinute: 300,
      changeMinutes: 8,
    });
    expect(time.runningMinutes).toBe(10);
    expect(time.changeMinutes).toBe(40);
    expect(time.hours).toBeCloseTo(50 / 60, 3);
  });

  it('charges no change time on a single section', () => {
    expect(
      warpingHours({ warpLengthMetres: 500, sections: 1, metresPerMinute: 300 }).changeMinutes,
    ).toBe(0);
  });

  it('refuses a warping speed of nothing', () => {
    expect(() => warpingHours({ warpLengthMetres: 500, sections: 1, metresPerMinute: 0 })).toThrow(
      InvalidArgument,
    );
  });
});
