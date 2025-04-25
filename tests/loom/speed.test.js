import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  picksPerMinute,
  weftInsertionRate,
  clothPerHourMetres,
  picksForCloth,
  runHours,
  shiftProduction,
  weftPackageMinutes,
  loomsPerWeaver,
  compareLooms,
} from '../../src/loom/speed.js';

describe('picksPerMinute', () => {
  it('is the crank speed on a single shuttle loom', () => {
    expect(picksPerMinute(180)).toBe(180);
  });

  it('doubles on a machine inserting two picks a turn', () => {
    expect(picksPerMinute(180, { picksPerTurn: 2 })).toBe(360);
  });
});

describe('weftInsertionRate', () => {
  it('multiplies speed by the width woven', () => {
    expect(weftInsertionRate(200, 1.9)).toBe(380);
  });

  it('lets a wide slow loom beat a narrow fast one', () => {
    expect(weftInsertionRate(150, 3.2)).toBeGreaterThan(weftInsertionRate(300, 1.5));
  });
});

describe('clothPerHourMetres', () => {
  it('falls as the pick density rises', () => {
    const open = clothPerHourMetres({ picksMinute: 200, picksPerCm: 10 });
    const close = clothPerHourMetres({ picksMinute: 200, picksPerCm: 20 });
    expect(open).toBe(12);
    expect(close).toBe(6);
  });

  it('scales straight down with efficiency', () => {
    expect(clothPerHourMetres({ picksMinute: 200, picksPerCm: 10, efficiency: 0.85 })).toBe(10.2);
  });

  it('is nothing when the loom is stopped', () => {
    expect(clothPerHourMetres({ picksMinute: 200, picksPerCm: 10, efficiency: 0 })).toBe(0);
  });

  it('refuses an efficiency over one', () => {
    expect(() => clothPerHourMetres({ picksMinute: 200, picksPerCm: 10, efficiency: 1.2 })).toThrow(
      InvalidArgument,
    );
  });
});

describe('picksForCloth', () => {
  it('counts the picks a run needs', () => {
    expect(picksForCloth({ metres: 50, picksPerCm: 20 })).toBe(100000);
  });

  it('rounds to a whole pick', () => {
    expect(picksForCloth({ metres: 0.503, picksPerCm: 20 })).toBe(1006);
  });
});

describe('runHours', () => {
  it('is the picks over the running speed', () => {
    expect(runHours({ metres: 100, picksPerCm: 20, picksMinute: 200 })).toBe(16.667);
  });

  it('takes longer at a lower efficiency', () => {
    const full = runHours({ metres: 100, picksPerCm: 20, picksMinute: 200 });
    const real = runHours({ metres: 100, picksPerCm: 20, picksMinute: 200, efficiency: 0.8 });
    expect(real).toBeGreaterThan(full);
    expect(real).toBeCloseTo(full / 0.8, 2);
  });

  it('refuses an efficiency of nothing, which never finishes', () => {
    expect(() =>
      runHours({ metres: 100, picksPerCm: 20, picksMinute: 200, efficiency: 0 }),
    ).toThrow(InvalidArgument);
  });
});

describe('shiftProduction', () => {
  it('is the hourly figure over the shift', () => {
    expect(shiftProduction({ picksMinute: 200, picksPerCm: 20, efficiency: 0.85 })).toBe(40.8);
  });

  it('takes a shift of a different length', () => {
    const eight = shiftProduction({ picksMinute: 200, picksPerCm: 20 });
    const twelve = shiftProduction({ hours: 12, picksMinute: 200, picksPerCm: 20 });
    expect(twelve).toBeCloseTo(eight * 1.5, 3);
  });
});

describe('weftPackageMinutes', () => {
  it('lasts longer on a narrow cloth than a wide one', () => {
    const narrow = weftPackageMinutes({
      packageMetres: 20000,
      reedWidthMetres: 1,
      picksMinute: 200,
    });
    const wide = weftPackageMinutes({
      packageMetres: 20000,
      reedWidthMetres: 2,
      picksMinute: 200,
    });
    expect(narrow).toBe(100);
    expect(wide).toBe(50);
  });

  it('runs out sooner once the weft crimp is allowed for', () => {
    const straight = weftPackageMinutes({
      packageMetres: 20000,
      reedWidthMetres: 1,
      picksMinute: 200,
    });
    const crimped = weftPackageMinutes({
      packageMetres: 20000,
      reedWidthMetres: 1,
      picksMinute: 200,
      crimp: 10,
    });
    expect(crimped).toBeLessThan(straight);
  });
});

describe('loomsPerWeaver', () => {
  it('divides the hour by the attention each loom needs', () => {
    expect(loomsPerWeaver({ attentionMinutesPerLoomHour: 5 })).toBe(10);
    expect(loomsPerWeaver({ attentionMinutesPerLoomHour: 10 })).toBe(5);
  });

  it('rounds down, because a part loom is not a loom', () => {
    expect(loomsPerWeaver({ attentionMinutesPerLoomHour: 7 })).toBe(7);
  });

  it('takes a different personal allowance', () => {
    expect(loomsPerWeaver({ attentionMinutesPerLoomHour: 5, allowance: 1 })).toBe(12);
  });
});

describe('compareLooms', () => {
  it('compares on insertion rate, not on picks a minute', () => {
    const comparison = compareLooms(
      { picksMinute: 300, reedWidthMetres: 1.5 },
      { picksMinute: 150, reedWidthMetres: 3.2 },
    );
    expect(comparison.faster).toBe('right');
    expect(comparison.ratio).toBeCloseTo(480 / 450, 3);
  });

  it('calls the left one faster on a tie', () => {
    const same = { picksMinute: 200, reedWidthMetres: 2 };
    expect(compareLooms(same, same)).toMatchObject({ faster: 'left', ratio: 1 });
  });
});
