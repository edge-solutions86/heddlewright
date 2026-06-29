import { describe, it, expect, beforeEach } from 'vitest';
import { createRouter, handle } from '../../src/service/router.js';
import { mountWeaveRoutes, weaveNames } from '../../src/service/weaveroutes.js';

import { twillRecord } from '../fixtures.js';

const DRAFT = twillRecord({ ends: 16, picks: 16 });

let router;

beforeEach(() => {
  router = mountWeaveRoutes(createRouter());
});

describe('GET /weaves', () => {
  it('lists the whole catalogue measured', async () => {
    const response = await handle(router, { path: '/weaves' });
    expect(response.body.length).toBe(weaveNames().length);
    expect(response.body[0]).toHaveProperty('warpFace');
  });

  it('filters to a family', async () => {
    const response = await handle(router, { path: '/weaves', query: { family: 'satin' } });
    expect(response.body.every((row) => row.family === 'satin')).toBe(true);
  });

  it('lists the families', async () => {
    const response = await handle(router, { path: '/weaves/families' });
    expect(response.body).toContain('twill');
  });
});

describe('GET /weaves/:name', () => {
  it('measures one weave and hands back its repeat', async () => {
    const response = await handle(router, { path: '/weaves/plain' });
    expect(response.body.picks).toBe(2);
    expect(response.body.rows).toEqual([
      [1, 0],
      [0, 1],
    ]);
  });

  it('writes the repeat as text when asked', async () => {
    const response = await handle(router, { path: '/weaves/plain', query: { rows: 'text' } });
    expect(response.body.rows).toEqual(['X.', '.X']);
  });

  it('is a four hundred and four for a weave nobody has', async () => {
    expect((await handle(router, { path: '/weaves/gauze' })).status).toBe(404);
  });

  it('reads a weave for whether it is a crepe', async () => {
    const twill = await handle(router, { path: '/weaves/twill%202%2F2/crepe' });
    expect(twill.body.reads).toBe(false);
    const crepe = await handle(router, { path: '/weaves/crepe%20rearranged/crepe' });
    expect(crepe.body.reads).toBe(true);
  });
});

describe('POST /twill', () => {
  it('weaves a twill from its notation and reports its balance', async () => {
    const body = { notation: '3/1' };
    const response = await handle(router, { method: 'POST', path: '/twill', body });
    expect(response.body.rows[0]).toBe('XXX.');
    expect(response.body.balance).toEqual({ warp: 3, weft: 1, repeat: 4 });
    expect(response.body.angle).toBeNull();
  });

  it('works the angle out when a sett is given', async () => {
    const body = { notation: '2/2', endsPerInch: 48, picksPerInch: 24 };
    const response = await handle(router, { method: 'POST', path: '/twill', body });
    expect(response.body.angle).toBe(63.43);
  });

  it('refuses notation it cannot read', async () => {
    const body = { notation: '2' };
    expect((await handle(router, { method: 'POST', path: '/twill', body })).status).toBe(422);
  });
});

describe('GET /satin/:threads', () => {
  it('weaves a satin and names its legal moves', async () => {
    const response = await handle(router, { path: '/satin/5' });
    expect(response.body.regular).toBe(true);
    expect(response.body.moves).toEqual([2, 3]);
    expect(response.body.rows[0]).toBe('.XXXX');
  });

  it('takes a move from the query', async () => {
    const response = await handle(router, { path: '/satin/8', query: { move: '5' } });
    expect(response.body.move).toBe(5);
  });

  it('says plainly when no regular satin exists', async () => {
    const response = await handle(router, { path: '/satin/6' });
    expect(response.body.regular).toBe(false);
    expect(response.body.moves).toEqual([]);
    expect(response.body.reason).toContain('coprime');
  });

  it('refuses a thread count that is not a number', async () => {
    expect((await handle(router, { path: '/satin/lots' })).status).toBe(400);
  });
});

describe('POST /report', () => {
  it('reports on a draft handed straight in', async () => {
    const body = { draft: DRAFT, options: { count: 30 } };
    const response = await handle(router, { method: 'POST', path: '/report', body });
    expect(response.body.structure.repeatEnds).toBe(4);
    expect(response.body.floats.longestWarp).toBe(2);
  });

  it('renders it as text when asked', async () => {
    const body = { draft: DRAFT, options: { count: 30 }, render: 'text' };
    const response = await handle(router, { method: 'POST', path: '/report', body });
    expect(response.body.text).toContain('Structure');
  });

  it('refuses a draft the domain will not take', async () => {
    const body = { draft: { ...DRAFT, shafts: 0 } };
    expect((await handle(router, { method: 'POST', path: '/report', body })).status).toBe(400);
  });
});
