import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import { createLoom, styleRequirements } from '../../src/loom/registry.js';
import {
  createRun,
  candidates,
  runHoursOn,
  schedule,
  loomLoad,
  assertFullyPlaced,
  finishOf,
  lateOrders,
  unweavableRuns,
  assertRunComplete,
} from '../../src/works/schedule.js';

import { narrowLoom, wideLoom } from '../fixtures.js';

const NARROW = createLoom(narrowLoom());

const WIDE = createLoom(wideLoom());

const LOOMS = [WIDE, NARROW];

const SMALL = createRun({
  order: 'SO-1',
  style: 'poplin',
  metres: 100,
  picksPerCm: 20,
  requirements: styleRequirements({ widthCm: 100, shafts: 4 }),
});

const BROAD = createRun({
  order: 'SO-2',
  style: 'sheeting',
  metres: 100,
  picksPerCm: 20,
  requirements: styleRequirements({ widthCm: 250, shafts: 4 }),
});

describe('candidates', () => {
  it('lists the capable looms narrowest first', () => {
    expect(candidates(LOOMS, SMALL).map((loom) => loom.reference)).toEqual(['N1', 'W1']);
  });

  it('drops the looms that cannot take the style', () => {
    expect(candidates(LOOMS, BROAD).map((loom) => loom.reference)).toEqual(['W1']);
  });

  it('is empty when nothing on the floor will do', () => {
    const huge = createRun({ ...SMALL, requirements: styleRequirements({ widthCm: 400 }) });
    expect(candidates(LOOMS, huge)).toEqual([]);
  });
});

describe('runHoursOn', () => {
  it('is the picks over the running speed', () => {
    expect(runHoursOn(NARROW, SMALL, { efficiency: 1 })).toBeCloseTo(200000 / (200 * 60), 3);
  });

  it('takes longer at a lower efficiency', () => {
    expect(runHoursOn(NARROW, SMALL, { efficiency: 0.5 })).toBeGreaterThan(
      runHoursOn(NARROW, SMALL, { efficiency: 1 }),
    );
  });
});

describe('schedule', () => {
  it('keeps a narrow style off the wide loom while the narrow one is free', () => {
    const plan = schedule(LOOMS, [SMALL]);
    expect(plan.placed[0].loom).toBe('N1');
  });

  it('puts a broad style on the only loom that holds it', () => {
    const plan = schedule(LOOMS, [BROAD]);
    expect(plan.placed[0].loom).toBe('W1');
  });

  it('runs two orders side by side on two looms', () => {
    const plan = schedule(LOOMS, [SMALL, BROAD]);
    expect(new Set(plan.placed.map((entry) => entry.loom)).size).toBe(2);
    expect(plan.placed.every((entry) => entry.fromHour === 0)).toBe(true);
  });

  it('queues a second narrow order behind the first', () => {
    const second = createRun({ ...SMALL, order: 'SO-3' });
    const plan = schedule([NARROW], [SMALL, second]);
    expect(plan.placed[0].toHour).toBe(plan.placed[1].fromHour);
    expect(plan.finishesAt).toBe(plan.placed[1].toHour);
  });

  it('takes the higher priority first', () => {
    const urgent = createRun({ ...SMALL, order: 'SO-9', priority: 5 });
    const plan = schedule([NARROW], [SMALL, urgent]);
    expect(plan.placed[0].order).toBe('SO-9');
  });

  it('reports a run nothing can take rather than dropping it', () => {
    const huge = createRun({
      ...SMALL,
      order: 'SO-8',
      requirements: styleRequirements({ widthCm: 400 }),
    });
    const plan = schedule(LOOMS, [huge]);
    expect(plan.placed).toEqual([]);
    expect(plan.unplaced[0].order).toBe('SO-8');
  });

  it('starts where it is told', () => {
    expect(schedule(LOOMS, [SMALL], { startHour: 12 }).placed[0].fromHour).toBe(12);
  });

  it('needs at least one loom', () => {
    expect(() => schedule([], [SMALL])).toThrow(InvalidArgument);
  });
});

describe('loomLoad', () => {
  it('reports the hours and the share of the span each loom carries', () => {
    const plan = schedule([NARROW], [SMALL, createRun({ ...SMALL, order: 'SO-3' })]);
    const load = loomLoad(plan);
    expect(load.length).toBe(1);
    expect(load[0].utilisation).toBe(1);
  });

  it('reads an idle loom out of the load entirely', () => {
    const plan = schedule(LOOMS, [SMALL]);
    expect(loomLoad(plan).map((entry) => entry.loom)).toEqual(['N1']);
  });
});

describe('assertFullyPlaced', () => {
  it('passes a plan with nothing left over', () => {
    const plan = schedule(LOOMS, [SMALL]);
    expect(assertFullyPlaced(plan)).toBe(plan);
  });

  it('refuses one with work that has nowhere to go', () => {
    const huge = createRun({ ...SMALL, requirements: styleRequirements({ widthCm: 400 }) });
    expect(() => assertFullyPlaced(schedule(LOOMS, [huge]))).toThrow(CapacityError);
  });
});

describe('finishOf and lateOrders', () => {
  it('says when an order finishes, and null when it was never placed', () => {
    const plan = schedule(LOOMS, [SMALL]);
    expect(finishOf(plan, 'SO-1')).toBeGreaterThan(0);
    expect(finishOf(plan, 'SO-9')).toBeNull();
  });

  it('finds the orders that will miss their date, worst first', () => {
    const second = createRun({ ...SMALL, order: 'SO-3' });
    const plan = schedule([NARROW], [SMALL, second]);
    const late = lateOrders(plan, { 'SO-1': 1, 'SO-3': 100 });
    expect(late.map((entry) => entry.order)).toEqual(['SO-1']);
    expect(late[0].byHours).toBeGreaterThan(0);
  });

  it('ignores an order with no date against it', () => {
    const plan = schedule(LOOMS, [SMALL]);
    expect(lateOrders(plan, {})).toEqual([]);
  });
});

describe('unweavableRuns and assertRunComplete', () => {
  it('names the runs no loom can take', () => {
    const huge = createRun({
      ...SMALL,
      order: 'SO-8',
      requirements: styleRequirements({ widthCm: 400 }),
    });
    expect(unweavableRuns(LOOMS, [SMALL, huge])).toEqual(['SO-8']);
  });

  it('refuses a run with empty requirements', () => {
    const bare = createRun({ ...SMALL, requirements: {} });
    expect(() => assertRunComplete(bare)).toThrow(/says nothing about what the loom/);
    expect(assertRunComplete(SMALL)).toBe(SMALL);
  });
});
