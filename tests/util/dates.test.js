import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  WORKING_WEEK,
  assertDay,
  addDays,
  daysBetween,
  weekday,
  isWorkingDay,
  nextWorkingDay,
  workingDaysBetween,
  addWorkingDays,
  finishDay,
} from '../../src/util/dates.js';

describe('assertDay', () => {
  it('takes a calendar day and hands it back', () => {
    expect(assertDay('2026-03-09')).toBe('2026-03-09');
  });

  it('refuses anything that is not one', () => {
    expect(() => assertDay('9 March 2026')).toThrow(/calendar day/);
    expect(() => assertDay('2026-3-9')).toThrow(InvalidArgument);
    expect(() => assertDay('')).toThrow(InvalidArgument);
  });

  it('refuses a day the calendar does not have', () => {
    expect(() => assertDay('2026-02-30')).toThrow(/not a real day/);
    expect(() => assertDay('2025-02-29')).toThrow(/not a real day/);
  });

  it('takes the leap day in a leap year', () => {
    expect(assertDay('2024-02-29')).toBe('2024-02-29');
  });
});

describe('addDays and daysBetween', () => {
  it('counts forwards and backwards', () => {
    expect(addDays('2026-03-09', 1)).toBe('2026-03-10');
    expect(addDays('2026-03-09', -1)).toBe('2026-03-08');
    expect(addDays('2026-03-09', 0)).toBe('2026-03-09');
  });

  it('crosses a month and a year end', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('crosses a leap day', () => {
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29');
    expect(addDays('2025-02-28', 1)).toBe('2025-03-01');
  });

  it('measures the gap either way', () => {
    expect(daysBetween('2026-03-09', '2026-03-16')).toBe(7);
    expect(daysBetween('2026-03-16', '2026-03-09')).toBe(-7);
    expect(daysBetween('2026-03-09', '2026-03-09')).toBe(0);
  });

  it('is not thrown by a summer time change', () => {
    expect(daysBetween('2026-03-28', '2026-03-30')).toBe(2);
    expect(daysBetween('2026-10-24', '2026-10-26')).toBe(2);
  });
});

describe('weekday', () => {
  it('names the day', () => {
    expect(weekday('2026-03-09')).toBe('Monday');
    expect(weekday('2026-03-14')).toBe('Saturday');
    expect(weekday('2026-03-15')).toBe('Sunday');
  });
});

describe('isWorkingDay', () => {
  it('works the usual week and not the weekend', () => {
    expect(WORKING_WEEK).toEqual(['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']);
    expect(isWorkingDay('2026-03-09')).toBe(true);
    expect(isWorkingDay('2026-03-14')).toBe(false);
  });

  it('does not work a holiday', () => {
    expect(isWorkingDay('2026-03-09', { holidays: ['2026-03-09'] })).toBe(false);
  });

  it('takes a shed that works Saturdays', () => {
    const week = [...WORKING_WEEK, 'Saturday'];
    expect(isWorkingDay('2026-03-14', { week })).toBe(true);
  });
});

describe('nextWorkingDay', () => {
  it('stays put on a working day', () => {
    expect(nextWorkingDay('2026-03-09')).toBe('2026-03-09');
  });

  it('skips the weekend', () => {
    expect(nextWorkingDay('2026-03-14')).toBe('2026-03-16');
  });

  it('skips a holiday that falls on the Monday', () => {
    expect(nextWorkingDay('2026-03-14', { holidays: ['2026-03-16'] })).toBe('2026-03-17');
  });

  it('refuses a week that has no working days at all', () => {
    expect(() => nextWorkingDay('2026-03-09', { week: ['Neverday'] })).toThrow(/no working day/);
  });
});

describe('workingDaysBetween', () => {
  it('counts the first day but not the last', () => {
    expect(workingDaysBetween('2026-03-09', '2026-03-16')).toBe(5);
  });

  it('is nothing across a weekend alone', () => {
    expect(workingDaysBetween('2026-03-14', '2026-03-16')).toBe(0);
  });

  it('refuses a span that runs backwards', () => {
    expect(() => workingDaysBetween('2026-03-16', '2026-03-09')).toThrow(/before the first/);
  });
});

describe('addWorkingDays', () => {
  it('steps over the weekend', () => {
    expect(addWorkingDays('2026-03-13', 1)).toBe('2026-03-16');
    expect(addWorkingDays('2026-03-09', 4)).toBe('2026-03-13');
  });

  it('lands on the next working day when it starts on a weekend', () => {
    expect(addWorkingDays('2026-03-14', 0)).toBe('2026-03-16');
  });
});

describe('finishDay', () => {
  it('finishes the same day when the work fits in one', () => {
    expect(finishDay('2026-03-09', { hours: 8 })).toBe('2026-03-09');
    expect(finishDay('2026-03-09', { hours: 16 })).toBe('2026-03-09');
  });

  it('rolls on to the next working day when it does not', () => {
    expect(finishDay('2026-03-09', { hours: 17 })).toBe('2026-03-10');
    expect(finishDay('2026-03-13', { hours: 17 })).toBe('2026-03-16');
  });

  it('takes a shorter working day', () => {
    expect(finishDay('2026-03-09', { hours: 16, hoursPerDay: 8 })).toBe('2026-03-10');
  });

  it('refuses a working day of no hours', () => {
    expect(() => finishDay('2026-03-09', { hours: 8, hoursPerDay: 0 })).toThrow(InvalidArgument);
  });
});
