import { describe, it, expect } from 'vitest';
import { InvalidArgument, NotFound, DraftError, ConflictError } from '../../src/util/errors.js';
import {
  METHODS,
  createRouter,
  route,
  match,
  methodsFor,
  pathsFor,
  handle,
  errorResponse,
  describeRoutes,
} from '../../src/service/router.js';

function built() {
  const router = createRouter();
  route(router, 'GET', '/drafts', () => [{ id: 'd1' }]);
  route(router, 'GET', '/drafts/:id', ({ params }) => ({ id: params.id }));
  route(router, 'GET', '/drafts/:id/cloth', ({ params }) => ({ cloth: params.id }));
  route(router, 'POST', '/drafts', ({ body }) => ({ status: 201, body }));
  return router;
}

describe('route', () => {
  it('names the methods it takes', () => {
    expect(METHODS).toEqual(['GET', 'POST', 'PUT', 'DELETE']);
  });

  it('refuses a method it does not know', () => {
    expect(() => route(createRouter(), 'PATCH', '/x', () => null)).toThrow(InvalidArgument);
  });

  it('refuses a path with no leading slash and a missing handler', () => {
    expect(() => route(createRouter(), 'GET', 'x', () => null)).toThrow(/starts with a slash/);
    expect(() => route(createRouter(), 'GET', '/x', null)).toThrow(/handler function/);
  });
});

describe('match', () => {
  const router = built();

  it('matches a plain path', () => {
    expect(match(router, 'GET', '/drafts').params).toEqual({});
  });

  it('captures named segments', () => {
    expect(match(router, 'GET', '/drafts/d7').params).toEqual({ id: 'd7' });
  });

  it('does not let a parameter swallow a longer path', () => {
    expect(match(router, 'GET', '/drafts/d7/cloth').route.path).toBe('/drafts/:id/cloth');
  });

  it('is null for a path nothing declares', () => {
    expect(match(router, 'GET', '/looms')).toBeNull();
    expect(match(router, 'DELETE', '/drafts')).toBeNull();
  });

  it('decodes a segment written with an escape', () => {
    expect(match(router, 'GET', '/drafts/a%2Fb').params.id).toBe('a/b');
  });

  it('ignores trailing and repeated slashes', () => {
    expect(match(router, 'GET', '/drafts/')).not.toBeNull();
    expect(match(router, 'GET', '//drafts//')).not.toBeNull();
  });
});

describe('handle', () => {
  const router = built();

  it('returns what the handler gives, as a two hundred', async () => {
    expect(await handle(router, { method: 'GET', path: '/drafts/d7' })).toEqual({
      status: 200,
      body: { id: 'd7' },
    });
  });

  it('lets a handler set its own status', async () => {
    const response = await handle(router, { method: 'POST', path: '/drafts', body: { a: 1 } });
    expect(response).toEqual({ status: 201, body: { a: 1 } });
  });

  it('turns nothing at all into a two hundred and four', async () => {
    const quiet = createRouter();
    route(quiet, 'DELETE', '/drafts/:id', () => undefined);
    expect(await handle(quiet, { method: 'DELETE', path: '/drafts/d1' })).toEqual({
      status: 204,
      body: null,
    });
  });

  it('answers a missing route with the paths it does know', async () => {
    const response = await handle(router, { method: 'GET', path: '/looms' });
    expect(response.status).toBe(404);
    expect(response.body.known).toContain('/drafts');
  });

  it('tells a wrong method apart from a missing path', async () => {
    const response = await handle(router, { method: 'DELETE', path: '/drafts' });
    expect(response.status).toBe(405);
    expect(response.body.allowed).toEqual(['GET', 'POST']);
  });

  it('still answers four hundred and four when nothing declares the path', async () => {
    expect((await handle(router, { method: 'DELETE', path: '/looms' })).status).toBe(404);
  });

  it('reads a wrong method on a parameter path too', async () => {
    const response = await handle(router, { method: 'POST', path: '/drafts/d7' });
    expect(response.status).toBe(405);
    expect(response.body.allowed).toEqual(['GET']);
  });

  it('defaults the method to GET', async () => {
    expect((await handle(router, { path: '/drafts' })).status).toBe(200);
  });

  it('awaits a handler that returns a promise', async () => {
    const slow = createRouter();
    route(slow, 'GET', '/x', async () => ({ ok: true }));
    expect((await handle(slow, { path: '/x' })).body).toEqual({ ok: true });
  });

  it('refuses a request with no path', async () => {
    await expect(handle(router, {})).rejects.toThrow(InvalidArgument);
  });
});

describe('errorResponse', () => {
  it('maps each of our codes to a status', () => {
    expect(errorResponse(new NotFound('gone')).status).toBe(404);
    expect(errorResponse(new InvalidArgument('bad')).status).toBe(400);
    expect(errorResponse(new DraftError('unweavable')).status).toBe(422);
    expect(errorResponse(new ConflictError('clash')).status).toBe(409);
  });

  it('keeps the code and the detail in the body', () => {
    const response = errorResponse(new DraftError('unweavable', { ends: [3] }));
    expect(response.body.code).toBe('draft_error');
    expect(response.body.detail).toEqual({ ends: [3] });
  });

  it('falls back to a five hundred for anything else', () => {
    const response = errorResponse(new Error('boom'));
    expect(response.status).toBe(500);
    expect(response.body.message).toBe('boom');
  });

  it('is what a throwing handler produces', async () => {
    const router = createRouter();
    route(router, 'GET', '/x', () => {
      throw new NotFound('nothing here');
    });
    expect((await handle(router, { path: '/x' })).status).toBe(404);
  });
});

describe('methodsFor', () => {
  const router = built();

  it('names every method declared for a path', () => {
    expect(methodsFor(router, '/drafts')).toEqual(['GET', 'POST']);
    expect(methodsFor(router, '/drafts/d7')).toEqual(['GET']);
  });

  it('is empty for a path nobody declared', () => {
    expect(methodsFor(router, '/looms')).toEqual([]);
  });

  it('does not let a parameter path answer for a longer one', () => {
    expect(methodsFor(router, '/drafts/d7/nothing')).toEqual([]);
  });
});

describe('pathsFor and describeRoutes', () => {
  it('lists the paths a method reaches, sorted', () => {
    expect(pathsFor(built(), 'GET')).toEqual(['/drafts', '/drafts/:id', '/drafts/:id/cloth']);
    expect(pathsFor(built(), 'DELETE')).toEqual([]);
  });

  it('describes the whole table in declaration order', () => {
    expect(describeRoutes(built())).toEqual([
      'GET /drafts',
      'GET /drafts/:id',
      'GET /drafts/:id/cloth',
      'POST /drafts',
    ]);
  });
});
