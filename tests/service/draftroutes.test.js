import { describe, it, expect, beforeEach } from 'vitest';
import { createRouter, handle } from '../../src/service/router.js';
import { createRepository, list } from '../../src/store/repository.js';
import { mountDraftRoutes, storable, COLLECTION } from '../../src/service/draftroutes.js';
import { createDraft } from '../../src/draft/draft.js';

import { twillRecord } from '../fixtures.js';

const INPUT = twillRecord();

let router;
let repository;

beforeEach(() => {
  repository = createRepository();
  router = mountDraftRoutes(createRouter(), repository);
});

async function post(body) {
  return handle(router, { method: 'POST', path: '/drafts', body });
}

describe('POST /drafts', () => {
  it('stores a draft and answers with its summary', async () => {
    const response = await post(INPUT);
    expect(response.status).toBe(201);
    expect(response.body.name).toBe('four shaft twill');
    expect(list(repository, COLLECTION).length).toBe(1);
  });

  it('refuses a draft the domain will not accept', async () => {
    const response = await post({ ...INPUT, shafts: 0 });
    expect(response.status).toBe(400);
    expect(response.body.code).toBe('invalid_argument');
  });

  it('refuses a body that is not an object', async () => {
    expect((await post('twill')).status).toBe(400);
  });

  it('takes a weaving file instead of a draft record', async () => {
    const created = await post(INPUT);
    const wif = (await handle(router, { path: `/drafts/${created.body.id}/wif` })).body.wif;
    const fresh = mountDraftRoutes(createRouter(), createRepository());
    const response = await handle(fresh, { method: 'POST', path: '/drafts', body: { wif } });
    expect(response.status).toBe(201);
    expect(response.body.id).toBe(created.body.id);
  });

  it('refuses the same draft twice', async () => {
    await post(INPUT);
    expect((await post(INPUT)).status).toBe(409);
  });
});

describe('GET /drafts', () => {
  it('is empty before anything is stored', async () => {
    expect((await handle(router, { path: '/drafts' })).body).toEqual([]);
  });

  it('summarises every stored draft', async () => {
    await post(INPUT);
    const response = await handle(router, { path: '/drafts' });
    expect(response.body.length).toBe(1);
    expect(response.body[0].shafts).toBe(4);
  });
});

describe('GET /drafts/:id', () => {
  it('answers with the summary', async () => {
    const created = await post(INPUT);
    const response = await handle(router, { path: `/drafts/${created.body.id}` });
    expect(response.status).toBe(200);
    expect(response.body.warpFaceRatio).toBe(0.5);
  });

  it('is a four hundred and four for a draft that is not there', async () => {
    expect((await handle(router, { path: '/drafts/draft-00000000' })).status).toBe(404);
  });
});

describe('GET /drafts/:id/cloth', () => {
  it('derives the cloth rather than storing it', async () => {
    const created = await post(INPUT);
    const response = await handle(router, { path: `/drafts/${created.body.id}/cloth` });
    expect(response.body.picks).toBe(8);
    expect(response.body.ends).toBe(8);
    expect(response.body.rows[0]).toEqual([1, 1, 0, 0, 1, 1, 0, 0]);
  });

  it('writes the cloth as text when asked', async () => {
    const created = await post(INPUT);
    const path = `/drafts/${created.body.id}/cloth`;
    const response = await handle(router, { path, query: { rows: 'text' } });
    expect(response.body.rows[0]).toBe('XX..XX..');
  });
});

describe('GET /drafts/:id/analysis', () => {
  it('reads the floats and the firmness', async () => {
    const created = await post(INPUT);
    const response = await handle(router, { path: `/drafts/${created.body.id}/analysis` });
    expect(response.body.longestFloat.warp).toBe(2);
    expect(response.body.firmness).toBe('medium');
    expect(response.body.problems).toEqual([]);
  });

  it('takes a float limit from the query', async () => {
    const created = await post(INPUT);
    const path = `/drafts/${created.body.id}/analysis`;
    const response = await handle(router, { path, query: { floatLimit: '1' } });
    expect(response.body.floatLimit).toBe(1);
    expect(response.body.over).toBeGreaterThan(0);
  });

  it('refuses a float limit that is not a whole number', async () => {
    const created = await post(INPUT);
    const path = `/drafts/${created.body.id}/analysis`;
    const response = await handle(router, { path, query: { floatLimit: 'lots' } });
    expect(response.status).toBe(400);
  });
});

describe('DELETE /drafts/:id', () => {
  it('removes the draft', async () => {
    const created = await post(INPUT);
    const path = `/drafts/${created.body.id}`;
    expect((await handle(router, { method: 'DELETE', path })).status).toBe(204);
    expect(list(repository, COLLECTION)).toEqual([]);
  });

  it('is a four hundred and four for one that is not there', async () => {
    const path = '/drafts/draft-00000000';
    expect((await handle(router, { method: 'DELETE', path })).status).toBe(404);
  });
});

describe('storable', () => {
  it('round-trips through createDraft', () => {
    const draft = createDraft(INPUT);
    expect(createDraft(storable(draft)).id).toBe(draft.id);
  });

  it('copies the arrays out of the frozen draft', () => {
    const draft = createDraft(INPUT);
    const record = storable(draft);
    record.threading[0] = 3;
    expect(draft.threading[0]).toBe(1);
  });
});
