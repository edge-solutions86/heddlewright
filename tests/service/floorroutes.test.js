import { describe, it, expect, beforeEach } from 'vitest';
import { createRouter, handle } from '../../src/service/router.js';
import { createRepository } from '../../src/store/repository.js';
import { mountFloorRoutes, loomRecords, outturnBook } from '../../src/service/floorroutes.js';

import { narrowLoom, wideLoom, poplinOrder } from '../fixtures.js';

const NARROW = narrowLoom();

const WIDE = wideLoom({ picksMinute: 180 });

const ORDER = poplinOrder();

const RUN = {
  order: 'SO-1',
  style: 'poplin',
  metres: 400,
  picksPerCm: 20,
  requirements: { widthCm: 100, shafts: 4, hooks: 0, weftColours: 1 },
};

let router;
let repository;

beforeEach(async () => {
  repository = createRepository();
  router = mountFloorRoutes(createRouter(), repository);
  await handle(router, { method: 'POST', path: '/looms', body: NARROW });
  await handle(router, { method: 'POST', path: '/looms', body: WIDE });
});

describe('looms', () => {
  it('lists what is registered', async () => {
    const response = await handle(router, { path: '/looms' });
    expect(response.body.map((loom) => loom.reference)).toEqual(['N1', 'W1']);
  });

  it('keeps the summary path off the reference route', async () => {
    const response = await handle(router, { path: '/looms/summary' });
    expect(response.body.find((entry) => entry.shedding === 'dobby').references.length).toBe(2);
  });

  it('finds one loom and refuses one that is not there', async () => {
    expect((await handle(router, { path: '/looms/N1' })).status).toBe(200);
    expect((await handle(router, { path: '/looms/N9' })).status).toBe(404);
  });

  it('refuses a loom with no shafts on a shaft machine', async () => {
    const body = { reference: 'X', shedding: 'dobby', reedWidthCm: 100, picksMinute: 100 };
    expect((await handle(router, { method: 'POST', path: '/looms', body })).status).toBe(400);
  });

  it('rebuilds the stored records into loom records', () => {
    expect(loomRecords(repository).every((loom) => Object.isFrozen(loom))).toBe(true);
  });
});

describe('POST /looms/capable', () => {
  it('names the looms that can take a style, narrow work off the wide loom last', async () => {
    const body = { widthCm: 100, shafts: 4 };
    const response = await handle(router, { method: 'POST', path: '/looms/capable', body });
    expect(response.body).toEqual(['N1', 'W1']);
  });

  it('is empty when nothing fits', async () => {
    const body = { widthCm: 400 };
    const response = await handle(router, { method: 'POST', path: '/looms/capable', body });
    expect(response.body).toEqual([]);
  });
});

describe('orders', () => {
  beforeEach(async () => {
    await handle(router, { method: 'POST', path: '/orders', body: ORDER });
  });

  it('opens an order in the quoted state', async () => {
    const response = await handle(router, { path: '/orders' });
    expect(response.body[0].state).toBe('quoted');
  });

  it('moves one on and stores where it got to', async () => {
    const path = '/orders/SO-1/advance';
    const moved = await handle(router, { method: 'POST', path, body: { to: 'weaving' } });
    expect(moved.body.state).toBe('weaving');
    const stored = await handle(router, { path: '/orders' });
    expect(stored.body[0].state).toBe('weaving');
  });

  it('refuses to move an order backwards', async () => {
    const path = '/orders/SO-1/advance';
    await handle(router, { method: 'POST', path, body: { to: 'weaving' } });
    const back = await handle(router, { method: 'POST', path, body: { to: 'quoted' } });
    expect(back.status).toBe(409);
  });

  it('filters to the open ones', async () => {
    const path = '/orders/SO-1/advance';
    await handle(router, { method: 'POST', path, body: { to: 'despatched' } });
    const open = await handle(router, { path: '/orders', query: { open: 'yes' } });
    expect(open.body).toEqual([]);
  });

  it('totals the book by state', async () => {
    const response = await handle(router, { path: '/orders/book' });
    expect(response.body.openMetres).toBe(400);
  });
});

describe('POST /plan', () => {
  it('works the plan out from the looms as they stand', async () => {
    const body = { runs: [RUN] };
    const response = await handle(router, { method: 'POST', path: '/plan', body });
    expect(response.body.placed[0].loom).toBe('N1');
    expect(response.body.load[0].utilisation).toBe(1);
  });

  it('reports work no loom can take', async () => {
    const wide = {
      ...RUN,
      order: 'SO-9',
      requirements: { widthCm: 400, shafts: 0, hooks: 0, weftColours: 1 },
    };
    const body = { runs: [wide] };
    const response = await handle(router, { method: 'POST', path: '/plan', body });
    expect(response.body.unplaced[0].order).toBe('SO-9');
  });

  it('names the orders that will be late when dates are given', async () => {
    const body = { runs: [RUN], wantedByHour: { 'SO-1': 1 } };
    const response = await handle(router, { method: 'POST', path: '/plan', body });
    expect(response.body.late[0].order).toBe('SO-1');
  });

  it('refuses a run with no length', async () => {
    const body = { runs: [{ ...RUN, metres: 0 }] };
    expect((await handle(router, { method: 'POST', path: '/plan', body })).status).toBe(400);
  });
});

describe('outturn', () => {
  const entries = [{ order: 'SO-1', loom: 'N1', metres: 120, hours: 8, stops: 12 }];

  it('books what was made and reports the rates', async () => {
    const body = { entries };
    const response = await handle(router, { method: 'POST', path: '/outturn', body });
    expect(response.status).toBe(201);
    expect(response.body.rates[0].metresPerHour).toBe(15);
    expect(outturnBook(repository).length).toBe(1);
  });

  it('sets the outturn beside the plan', async () => {
    await handle(router, { method: 'POST', path: '/outturn', body: { entries } });
    const body = { runs: [RUN], orderedMetres: { 'SO-1': 400 } };
    const path = '/outturn/against-plan';
    const response = await handle(router, { method: 'POST', path, body });
    expect(response.body[0].metres).toBe(120);
    expect(response.body[0].share).toBe(0.3);
  });

  it('reports no outturn before anything is booked', () => {
    expect(outturnBook(repository)).toEqual([]);
  });
});
