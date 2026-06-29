import { describe, it, expect, afterEach } from 'vitest';
import { createApp } from '../../src/service/app.js';
import { createServer, listen, close, splitUrl } from '../../src/service/server.js';

import { twillRecord } from '../fixtures.js';

const DRAFT = twillRecord();

let running = null;

afterEach(async () => {
  if (running !== null) {
    await close(running);
    running = null;
  }
});

async function start() {
  running = createServer(createApp());
  const port = await listen(running);
  return `http://127.0.0.1:${port}`;
}

describe('splitUrl', () => {
  it('splits a path from its query', () => {
    expect(splitUrl('/drafts?rows=text')).toEqual({ path: '/drafts', query: { rows: 'text' } });
  });

  it('has an empty query when there is none', () => {
    expect(splitUrl('/drafts')).toEqual({ path: '/drafts', query: {} });
  });

  it('reads a key with no value as an empty string', () => {
    expect(splitUrl('/x?flag').query).toEqual({ flag: '' });
  });

  it('decodes escapes and reads a plus as a space', () => {
    expect(splitUrl('/x?name=four+shaft%20twill').query.name).toBe('four shaft twill');
  });

  it('ignores an empty pair', () => {
    expect(splitUrl('/x?&a=1').query).toEqual({ a: '1' });
  });
});

describe('the running server', () => {
  it('answers health over http', async () => {
    const base = await start();
    const response = await fetch(`${base}/health`);
    expect(response.status).toBe(200);
    const body = await response.json();
    expect(body.ok).toBe(true);
  });

  it('takes a posted draft and reads it back', async () => {
    const base = await start();
    const created = await fetch(`${base}/drafts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(DRAFT),
    });
    expect(created.status).toBe(201);
    const listed = await (await fetch(`${base}/drafts`)).json();
    expect(listed.length).toBe(1);
  });

  it('passes the query string through to the handler', async () => {
    const base = await start();
    const created = await fetch(`${base}/drafts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(DRAFT),
    });
    const { id } = await created.json();
    const cloth = await (await fetch(`${base}/drafts/${id}/cloth?rows=text`)).json();
    expect(cloth.rows[0]).toBe('XX..XX..');
  });

  it('answers a bad body with four hundred rather than falling over', async () => {
    const base = await start();
    const response = await fetch(`${base}/drafts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{oops',
    });
    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe('invalid_argument');
  });

  it('answers an unknown path with four hundred and four', async () => {
    const base = await start();
    const response = await fetch(`${base}/nothing`);
    expect(response.status).toBe(404);
  });

  it('answers a wrong method with four hundred and five and what is allowed', async () => {
    const base = await start();
    const response = await fetch(`${base}/health`, { method: 'DELETE' });
    expect(response.status).toBe(405);
    expect((await response.json()).allowed).toEqual(['GET']);
  });

  it('answers a wrong method on a stored record the same way', async () => {
    const base = await start();
    const created = await fetch(`${base}/drafts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(DRAFT),
    });
    const { id } = await created.json();
    const response = await fetch(`${base}/drafts/${id}`, { method: 'PUT' });
    expect(response.status).toBe(405);
    expect((await response.json()).allowed).toEqual(['DELETE', 'GET']);
  });

  it('sends a content length that matches the body', async () => {
    const base = await start();
    const response = await fetch(`${base}/health`);
    const text = await response.text();
    expect(Number(response.headers.get('content-length'))).toBe(Buffer.byteLength(text));
  });

  it('sends no body at all on a two hundred and four', async () => {
    const base = await start();
    const created = await fetch(`${base}/drafts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(DRAFT),
    });
    const { id } = await created.json();
    const removed = await fetch(`${base}/drafts/${id}`, { method: 'DELETE' });
    expect(removed.status).toBe(204);
    expect(await removed.text()).toBe('');
  });
});
