import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  STOP_CAUSES,
  normaliseStop,
  lostMinutes,
  efficiency,
  lossByCause,
  breakRate,
  efficiencyFromStopRate,
  stopSensitivity,
  shiftReport,
} from '../../src/loom/efficiency.js';

const SHIFT = [
  { cause: 'warp break', count: 12, minutesEach: 1.5, waitEach: 0.5 },
  { cause: 'weft break', count: 20, minutesEach: 0.5, waitEach: 0.2 },
  { cause: 'beam change', count: 1, minutesEach: 25, waitEach: 5 },
];

describe('STOP_CAUSES', () => {
  it('names the causes a shed books time against', () => {
    expect(STOP_CAUSES).toContain('warp break');
    expect(STOP_CAUSES).toContain('no weaver');
    expect(new Set(STOP_CAUSES).size).toBe(STOP_CAUSES.length);
  });
});

describe('normaliseStop', () => {
  it('fills in the counts a record leaves out', () => {
    expect(normaliseStop({ cause: 'mechanical' })).toEqual({
      cause: 'mechanical',
      count: 1,
      minutesEach: 0,
      waitEach: 0,
      minutesLost: 0,
    });
  });

  it('adds the wait to the mend for the time lost', () => {
    expect(
      normaliseStop({ cause: 'warp break', count: 2, minutesEach: 1, waitEach: 3 }),
    ).toMatchObject({ minutesLost: 8 });
  });

  it('refuses a record with no cause and a negative count', () => {
    expect(() => normaliseStop({})).toThrow(InvalidArgument);
    expect(() => normaliseStop({ cause: 'x', count: -1 })).toThrow(InvalidArgument);
  });
});

describe('lostMinutes', () => {
  it('splits mending from waiting', () => {
    const lost = lostMinutes(SHIFT);
    expect(lost.mending).toBe(12 * 1.5 + 20 * 0.5 + 25);
    expect(lost.waiting).toBe(12 * 0.5 + 20 * 0.2 + 5);
    expect(lost.total).toBe(lost.mending + lost.waiting);
  });

  it('is nothing for a shift with no stops', () => {
    expect(lostMinutes([])).toEqual({ mending: 0, waiting: 0, total: 0 });
  });
});

describe('efficiency', () => {
  it('is the running share of the shift', () => {
    expect(efficiency({ shiftMinutes: 480, stops: SHIFT })).toBeCloseTo(
      (480 - lostMinutes(SHIFT).total) / 480,
      4,
    );
  });

  it('is one when nothing stops', () => {
    expect(efficiency({ shiftMinutes: 480, stops: [] })).toBe(1);
  });

  it('refuses stops adding up to more than the shift', () => {
    expect(() =>
      efficiency({ shiftMinutes: 60, stops: [{ cause: 'mechanical', minutesEach: 120 }] }),
    ).toThrow(/more than the shift/);
  });
});

describe('lossByCause', () => {
  it('ranks the causes by time lost', () => {
    const ranked = lossByCause(SHIFT);
    expect(ranked[0].cause).toBe('beam change');
    expect(ranked[0].minutesLost).toBe(30);
  });

  it('adds up repeated records for the same cause', () => {
    const ranked = lossByCause([
      { cause: 'warp break', count: 2, minutesEach: 1 },
      { cause: 'warp break', count: 3, minutesEach: 1 },
    ]);
    expect(ranked.length).toBe(1);
    expect(ranked[0].count).toBe(5);
    expect(ranked[0].minutesLost).toBe(5);
  });

  it('separates a frequent cheap cause from a rare expensive one', () => {
    const ranked = lossByCause(SHIFT);
    const weft = ranked.find((entry) => entry.cause === 'weft break');
    expect(weft.count).toBe(20);
    expect(weft.minutesLost).toBeLessThan(ranked[0].minutesLost);
  });
});

describe('breakRate', () => {
  it('is quoted per hundred thousand picks', () => {
    expect(breakRate({ breaks: 12, picks: 240000 })).toBe(5);
  });

  it('takes a different base', () => {
    expect(breakRate({ breaks: 12, picks: 240000, per: 1000000 })).toBe(50);
  });

  it('is nothing when nothing broke', () => {
    expect(breakRate({ breaks: 0, picks: 240000 })).toBe(0);
  });
});

describe('efficiencyFromStopRate', () => {
  it('takes the stop time out of the hour', () => {
    expect(efficiencyFromStopRate({ stopsPerHour: 6, minutesPerStop: 1 })).toBe(0.9);
    expect(efficiencyFromStopRate({ stopsPerHour: 0, minutesPerStop: 1 })).toBe(1);
  });

  it('bottoms out at nothing rather than going negative', () => {
    expect(efficiencyFromStopRate({ stopsPerHour: 100, minutesPerStop: 1 })).toBe(0);
  });
});

describe('stopSensitivity', () => {
  it('says what one stop an hour is worth', () => {
    expect(stopSensitivity({ minutesPerStop: 1.2 })).toBe(0.02);
  });

  it('agrees with the stop rate reading', () => {
    const base = efficiencyFromStopRate({ stopsPerHour: 5, minutesPerStop: 1.2 });
    const worse = efficiencyFromStopRate({ stopsPerHour: 6, minutesPerStop: 1.2 });
    expect(base - worse).toBeCloseTo(stopSensitivity({ minutesPerStop: 1.2 }), 4);
  });
});

describe('shiftReport', () => {
  it('gathers the whole shift into one record', () => {
    const report = shiftReport({ shiftMinutes: 480, stops: SHIFT, picksMinute: 200 });
    expect(report.efficiencyPercent).toBeCloseTo(report.efficiency * 100, 2);
    expect(report.byCause[0].cause).toBe('beam change');
    expect(report.picksWoven).toBe(Math.round(480 * report.efficiency * 200));
  });

  it('leaves the pick count out when no speed is given', () => {
    expect(shiftReport({ shiftMinutes: 480, stops: SHIFT }).picksWoven).toBeNull();
  });
});
