import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument } from '../../src/util/errors.js';
import { createLoom, styleRequirements } from '../../src/loom/registry.js';
import { createRun, schedule } from '../../src/works/schedule.js';
import {
  recordOutturn,
  recordBook,
  producedFor,
  orderProgress,
  loomRates,
  shiftRates,
  againstPlan,
  assertBookMatchesPlan,
} from '../../src/works/production.js';

const BOOK = recordBook([
  { order: 'SO-1', loom: 'N1', shift: 'day', metres: 120, hours: 8, stops: 16 },
  { order: 'SO-1', loom: 'N1', shift: 'night', metres: 96, hours: 8, stops: 30 },
  { order: 'SO-2', loom: 'W1', shift: 'day', metres: 200, hours: 8, stops: 10 },
]);

describe('recordOutturn', () => {
  it('fills in the shift and the stop count', () => {
    const entry = recordOutturn({ order: 'SO-1', loom: 'N1', metres: 10, hours: 1 });
    expect(entry.shift).toBe('day');
    expect(entry.stops).toBe(0);
    expect(entry.at).toBeNull();
  });

  it('refuses an entry with no length or no hours', () => {
    const bad = { order: 'SO-1', loom: 'N1', metres: 0, hours: 1 };
    expect(() => recordOutturn(bad)).toThrow(InvalidArgument);
    expect(() => recordOutturn({ ...bad, metres: 1, hours: 0 })).toThrow(InvalidArgument);
  });

  it('names the entry that is wrong', () => {
    expect(() => recordOutturn({ loom: 'N1', metres: 1, hours: 1 }, 3)).toThrow(/outturn\[3\]/);
  });
});

describe('producedFor', () => {
  it('adds up one order across its shifts', () => {
    const made = producedFor(BOOK, 'SO-1');
    expect(made.entries).toBe(2);
    expect(made.metres).toBe(216);
    expect(made.hours).toBe(16);
    expect(made.stops).toBe(46);
  });

  it('is empty for an order nothing was booked against', () => {
    expect(producedFor(BOOK, 'SO-9').metres).toBe(0);
  });
});

describe('orderProgress', () => {
  it('reads the share made and what is left', () => {
    const progress = orderProgress(BOOK, 'SO-1', 400);
    expect(progress.remainingMetres).toBe(184);
    expect(progress.share).toBe(0.54);
    expect(progress.complete).toBe(false);
  });

  it('calls an order complete once it reaches the length', () => {
    expect(orderProgress(BOOK, 'SO-1', 216).complete).toBe(true);
  });

  it('reports an overrun rather than a share above one', () => {
    const progress = orderProgress(BOOK, 'SO-1', 200);
    expect(progress.share).toBe(1);
    expect(progress.overrunMetres).toBe(16);
    expect(progress.remainingMetres).toBe(0);
  });

  it('refuses an ordered length of nothing', () => {
    expect(() => orderProgress(BOOK, 'SO-1', 0)).toThrow(InvalidArgument);
  });
});

describe('loomRates', () => {
  it('ranks the looms on metres an hour', () => {
    const rates = loomRates(BOOK);
    expect(rates[0].loom).toBe('W1');
    expect(rates[0].metresPerHour).toBe(25);
    expect(rates[1].metresPerHour).toBe(13.5);
  });

  it('reports the stop rate alongside', () => {
    const narrow = loomRates(BOOK).find((entry) => entry.loom === 'N1');
    expect(narrow.stopsPerHour).toBe(roundThree(46 / 16));
  });

  it('is empty for an empty book', () => {
    expect(loomRates([])).toEqual([]);
  });
});

describe('shiftRates', () => {
  it('separates the day crew from the night one', () => {
    const rates = shiftRates(BOOK);
    expect(rates.map((entry) => entry.shift)).toEqual(['day', 'night']);
    expect(rates[0].metresPerHour).toBe(20);
    expect(rates[1].metresPerHour).toBe(12);
  });
});

describe('againstPlan', () => {
  const looms = [
    createLoom({
      reference: 'N1',
      shedding: 'dobby',
      reedWidthCm: 120,
      shafts: 8,
      picksMinute: 200,
    }),
    createLoom({
      reference: 'W1',
      shedding: 'dobby',
      reedWidthCm: 260,
      shafts: 16,
      picksMinute: 200,
    }),
  ];
  const runs = [
    createRun({
      order: 'SO-1',
      style: 'poplin',
      metres: 400,
      picksPerCm: 20,
      requirements: styleRequirements({ widthCm: 100, shafts: 4 }),
    }),
    createRun({
      order: 'SO-2',
      style: 'sheeting',
      metres: 400,
      picksPerCm: 20,
      requirements: styleRequirements({ widthCm: 250, shafts: 4 }),
    }),
  ];
  const plan = schedule(looms, runs);

  it('sets what was made beside what was planned', () => {
    const rows = againstPlan(plan, BOOK, { 'SO-1': 400, 'SO-2': 400 });
    const first = rows.find((row) => row.order === 'SO-1');
    expect(first.metres).toBe(216);
    expect(first.plannedHours).toBeGreaterThan(0);
    expect(first.hoursOver).toBe(roundThree(16 - first.plannedHours));
  });

  it('refuses a plan entry with no ordered length against it', () => {
    expect(() => againstPlan(plan, BOOK, { 'SO-1': 400 })).toThrow(/no ordered length for SO-2/);
  });
});

describe('assertBookMatchesPlan', () => {
  const looms = [
    createLoom({
      reference: 'N1',
      shedding: 'dobby',
      reedWidthCm: 120,
      shafts: 8,
      picksMinute: 200,
    }),
  ];
  const runs = [
    createRun({
      order: 'SO-1',
      style: 'poplin',
      metres: 400,
      picksPerCm: 20,
      requirements: styleRequirements({ widthCm: 100, shafts: 4 }),
    }),
  ];
  const plan = schedule(looms, runs);

  it('passes a book that follows the plan', () => {
    const book = recordBook([{ order: 'SO-1', loom: 'N1', metres: 10, hours: 1 }]);
    expect(assertBookMatchesPlan(plan, book)).toBe(book);
  });

  it('catches an order booked against the wrong loom', () => {
    const book = recordBook([{ order: 'SO-1', loom: 'W1', metres: 10, hours: 1 }]);
    expect(() => assertBookMatchesPlan(plan, book)).toThrow(/was planned for N1/);
  });

  it('catches an order that was never planned', () => {
    const book = recordBook([{ order: 'SO-7', loom: 'N1', metres: 10, hours: 1 }]);
    expect(() => assertBookMatchesPlan(plan, book)).toThrow(ConflictError);
  });
});

function roundThree(value) {
  return Math.round(value * 1000) / 1000;
}
