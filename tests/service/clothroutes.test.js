import { describe, it, expect, beforeEach } from 'vitest';
import { createRouter, handle } from '../../src/service/router.js';
import { createRepository } from '../../src/store/repository.js';
import { mountClothRoutes, defectsFor, PIECES } from '../../src/service/clothroutes.js';

const PIECE = { reference: 'P1001', yards: 100, widthInches: 60 };

let router;
let repository;

beforeEach(async () => {
  repository = createRepository();
  router = mountClothRoutes(createRouter(), repository);
  await handle(router, { method: 'POST', path: '/pieces', body: PIECE });
});

describe('POST /pieces', () => {
  it('opens a piece and answers with it', async () => {
    const fresh = mountClothRoutes(createRouter(), createRepository());
    const response = await handle(fresh, { method: 'POST', path: '/pieces', body: PIECE });
    expect(response.status).toBe(201);
    expect(response.body.squareYards).toBeCloseTo((100 * 60) / 36, 2);
  });

  it('refuses a piece with no length', async () => {
    const body = { ...PIECE, reference: 'P2', yards: 0 };
    expect((await handle(router, { method: 'POST', path: '/pieces', body })).status).toBe(400);
  });

  it('refuses the same reference twice', async () => {
    const response = await handle(router, { method: 'POST', path: '/pieces', body: PIECE });
    expect(response.status).toBe(409);
  });
});

describe('GET /pieces', () => {
  it('lists what is open', async () => {
    const response = await handle(router, { path: '/pieces' });
    expect(response.body.length).toBe(1);
    expect(response.body[0].reference).toBe('P1001');
  });

  it('finds one by reference and refuses one that is not there', async () => {
    expect((await handle(router, { path: '/pieces/P1001' })).status).toBe(200);
    expect((await handle(router, { path: '/pieces/P9999' })).status).toBe(404);
  });

  it('totals the register', async () => {
    const response = await handle(router, { path: '/pieces/totals' });
    expect(response.body.yards).toBe(100);
  });

  it('does not let the totals path be read as a piece reference', async () => {
    const body = { ...PIECE, reference: 'totals' };
    await handle(router, { method: 'POST', path: '/pieces', body });
    const response = await handle(router, { path: '/pieces/totals' });
    expect(response.body.pieces).toBe(2);
    expect(response.body.reference).toBeUndefined();
  });
});

describe('POST /pieces/:reference/defects', () => {
  const defects = {
    defects: [
      { code: 'weft_bar', yard: 3, inches: 12 },
      { code: 'slub', yard: 20, inches: 1 },
    ],
  };

  it('books a sheet and answers with the reading', async () => {
    const path = '/pieces/P1001/defects';
    const response = await handle(router, { method: 'POST', path, body: defects });
    expect(response.status).toBe(201);
    expect(response.body.defects).toBe(2);
    expect(response.body.dominantOrigin).toBe('weft');
  });

  it('stores the sheet against the piece', async () => {
    const path = '/pieces/P1001/defects';
    await handle(router, { method: 'POST', path, body: defects });
    expect(defectsFor(repository, 'P1001').length).toBe(2);
  });

  it('refuses a sheet for a piece that is not there', async () => {
    const path = '/pieces/P9999/defects';
    expect((await handle(router, { method: 'POST', path, body: defects })).status).toBe(404);
  });

  it('refuses a fault code the catalogue does not know', async () => {
    const path = '/pieces/P1001/defects';
    const body = { defects: [{ code: 'gremlins', yard: 1, inches: 1 }] };
    expect((await handle(router, { method: 'POST', path, body })).status).toBe(404);
  });
});

describe('GET /pieces/:reference/grade', () => {
  it('grades a clean piece as accepted', async () => {
    const response = await handle(router, { path: '/pieces/P1001/grade' });
    expect(response.body.score.totalPoints).toBe(0);
    expect(response.body.grade.accepted).toBe(true);
  });

  it('grades against the defects booked', async () => {
    const body = { defects: [{ code: 'weft_bar', yard: 3, inches: 12 }] };
    await handle(router, { method: 'POST', path: '/pieces/P1001/defects', body });
    const response = await handle(router, { path: '/pieces/P1001/grade' });
    expect(response.body.score.totalPoints).toBe(4);
  });

  it('takes a tighter acceptance from the query', async () => {
    const body = { defects: [{ code: 'weft_bar', yard: 3, inches: 12 }] };
    await handle(router, { method: 'POST', path: '/pieces/P1001/defects', body });
    const path = '/pieces/P1001/grade';
    const strict = await handle(router, { path, query: { acceptance: '1' } });
    expect(strict.body.grade.accepted).toBe(false);
  });

  it('refuses an acceptance of nothing', async () => {
    const path = '/pieces/P1001/grade';
    expect((await handle(router, { path, query: { acceptance: '0' } })).status).toBe(400);
  });
});

describe('POST /pieces/:reference/cut', () => {
  it('plans cuts round the defects that were booked', async () => {
    const body = { defects: [{ code: 'hole', yard: 50, inches: 0.5 }] };
    await handle(router, { method: 'POST', path: '/pieces/P1001/defects', body });
    const path = '/pieces/P1001/cut';
    const response = await handle(router, { method: 'POST', path, body: { lengths: [40, 40] } });
    expect(response.body.runs.length).toBe(2);
    expect(response.body.summary.cutYards).toBe(80);
  });

  it('cuts the whole piece when nothing is wrong with it', async () => {
    const path = '/pieces/P1001/cut';
    const response = await handle(router, { method: 'POST', path, body: { lengths: [100] } });
    expect(response.body.summary.yield).toBe(1);
  });
});

describe('GET /cloth/summary', () => {
  it('reports the totals and the first quality share', async () => {
    const response = await handle(router, { path: '/cloth/summary' });
    expect(response.body.totals.pieces).toBe(1);
    expect(response.body.firstQualityShare).toBe(1);
    expect(response.body.reconciliation).toBeNull();
  });

  it('reconciles against a woven length when one is given', async () => {
    const response = await handle(router, { path: '/cloth/summary', query: { woven: '100' } });
    expect(response.body.reconciliation.balanced).toBe(true);
  });

  it('reports an unbalanced register', async () => {
    const response = await handle(router, { path: '/cloth/summary', query: { woven: '150' } });
    expect(response.body.reconciliation.difference).toBe(-50);
  });
});

describe('the store', () => {
  it('keeps pieces in their own collection', () => {
    expect(repository.state.get(PIECES).size).toBe(1);
  });

  it('reports no defects for a piece nobody has graded', () => {
    expect(defectsFor(repository, 'P1001')).toEqual([]);
  });
});
