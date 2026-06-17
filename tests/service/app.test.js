import { describe, it, expect, beforeEach } from 'vitest';
import { straightDraw } from '../../src/draft/threading.js';
import { twillTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import { createJournal, toJson, fromJson } from '../../src/store/journal.js';
import { createApp, routeTable, duplicateRoutes, VERSION } from '../../src/service/app.js';

const DRAFT = {
  name: 'four shaft twill',
  shafts: 4,
  treadles: 4,
  threading: straightDraw(4, 8),
  tieup: twillTieup(4, 2),
  treadling: straightTreadling(4, 8),
};

let app;

beforeEach(() => {
  app = createApp();
});

describe('GET /health', () => {
  it('reports an empty service', async () => {
    const response = await app.request({ path: '/health' });
    expect(response.body.ok).toBe(true);
    expect(response.body.version).toBe(VERSION);
    expect(response.body.entries).toBe(0);
    expect(response.body.records).toEqual({});
  });

  it('moves the head on with every write', async () => {
    const before = (await app.request({ path: '/health' })).body.head;
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const after = (await app.request({ path: '/health' })).body.head;
    expect(after).not.toBe(before);
    expect((await app.request({ path: '/health' })).body.entries).toBe(1);
  });

  it('counts the records by collection', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const response = await app.request({ path: '/health' });
    expect(response.body.records).toEqual({ drafts: 1 });
  });
});

describe('the route table', () => {
  it('mounts every part of the service', () => {
    const table = routeTable(app);
    expect(table).toContain('GET /drafts');
    expect(table).toContain('GET /pieces');
    expect(table).toContain('POST /plan');
    expect(table).toContain('GET /health');
  });

  it('declares no route twice', () => {
    expect(duplicateRoutes(app)).toEqual([]);
  });

  it('serves its own table', async () => {
    const response = await app.request({ path: '/routes' });
    expect(response.body.length).toBe(routeTable(app).length);
  });

  it('answers a wrong method with the methods that path does take', async () => {
    const response = await app.request({ method: 'POST', path: '/health' });
    expect(response.status).toBe(405);
    expect(response.body.allowed).toEqual(['GET']);
  });

  it('answers a path nobody declared with the ones it knows', async () => {
    const response = await app.request({ path: '/nothing' });
    expect(response.status).toBe(404);
    expect(response.body.known).toContain('/health');
  });
});

describe('GET /journal', () => {
  it('lists what has been written, without the payloads', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const response = await app.request({ path: '/journal' });
    expect(response.body.length).toBe(1);
    expect(response.body[0].type).toBe('record.put');
    expect(response.body[0].payload).toBeUndefined();
  });

  it('catches a reader up from a sequence number', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const response = await app.request({ path: '/journal', query: { since: '1' } });
    expect(response.body).toEqual([]);
  });

  it('treats an unreadable since as the beginning', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const response = await app.request({ path: '/journal', query: { since: 'lots' } });
    expect(response.body.length).toBe(1);
  });
});

describe('GET /journal/verify', () => {
  it('reads a fresh service as sound', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const response = await app.request({ path: '/journal/verify' });
    expect(response.body.sound).toBe(true);
    expect(response.body.entries).toBe(1);
  });

  it('spots a journal that has been meddled with', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const journal = app.repository.journal;
    journal.entries[0] = { ...journal.entries[0], type: 'record.removed' };
    const response = await app.request({ path: '/journal/verify' });
    expect(response.body.sound).toBe(false);
  });
});

describe('reopening from a journal', () => {
  it('comes back with the same state', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const saved = toJson(app.repository.journal);
    const reopened = createApp({ journal: fromJson(saved) });
    const response = await reopened.request({ path: '/drafts' });
    expect(response.body.length).toBe(1);
    expect(response.body[0].name).toBe('four shaft twill');
  });

  it('comes back with the same head', async () => {
    await app.request({ method: 'POST', path: '/drafts', body: DRAFT });
    const reopened = createApp({ journal: fromJson(toJson(app.repository.journal)) });
    const mine = (await app.request({ path: '/health' })).body.head;
    const theirs = (await reopened.request({ path: '/health' })).body.head;
    expect(theirs).toBe(mine);
  });

  it('starts empty on a fresh journal', async () => {
    const fresh = createApp({ journal: createJournal() });
    expect((await fresh.request({ path: '/drafts' })).body).toEqual([]);
  });
});
