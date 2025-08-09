import { InvalidArgument, NotFound } from '../util/errors.js';
import { nonEmptyString, oneOf, plainObject } from '../util/check.js';

/**
 * A small router.
 *
 * Routes are declared as a method and a path with `:name` segments. Matching is
 * exact on segment count, so `/drafts/:id` never swallows `/drafts/:id/cloth`,
 * and the first route declared wins, which makes the table readable top to
 * bottom rather than by specificity rules nobody can hold in their head.
 */

export const METHODS = ['GET', 'POST', 'PUT', 'DELETE'];

/** Build an empty routing table. */
export function createRouter() {
  return { routes: [] };
}

/** Add a route. */
export function route(router, method, path, handler) {
  oneOf(method, 'method', METHODS);
  nonEmptyString(path, 'path');
  if (typeof handler !== 'function') {
    throw new InvalidArgument('a route needs a handler function', { path });
  }
  if (!path.startsWith('/')) {
    throw new InvalidArgument('a route path starts with a slash', { path });
  }
  router.routes.push({ method, path, segments: splitPath(path), handler });
  return router;
}

function splitPath(path) {
  return path.split('/').filter((part) => part !== '');
}

/** Find the route for a method and path, with the parameters it captured. */
export function match(router, method, path) {
  const wanted = splitPath(path);
  for (const entry of router.routes) {
    if (entry.method !== method) continue;
    if (entry.segments.length !== wanted.length) continue;
    const params = {};
    let matched = true;
    for (const [index, segment] of entry.segments.entries()) {
      if (segment.startsWith(':')) {
        params[segment.slice(1)] = decodeURIComponent(wanted[index]);
        continue;
      }
      if (segment !== wanted[index]) {
        matched = false;
        break;
      }
    }
    if (matched) return { route: entry, params };
  }
  return null;
}

/** The paths a method can reach, for a not-found body worth reading. */
export function pathsFor(router, method) {
  return router.routes
    .filter((entry) => entry.method === method)
    .map((entry) => entry.path)
    .sort();
}

/**
 * Run a request through the table. A handler returns `{ status, body }`, and
 * anything it throws that carries one of our codes becomes the matching status
 * rather than a five hundred.
 */
export async function handle(router, request) {
  plainObject(request, 'request');
  const method = oneOf(request.method ?? 'GET', 'request.method', METHODS);
  const path = nonEmptyString(request.path, 'request.path');
  const found = match(router, method, path);
  if (found === null) {
    return {
      status: 404,
      body: { error: 'no route', method, path, known: pathsFor(router, method) },
    };
  }
  try {
    const result = await found.route.handler({
      params: found.params,
      query: request.query ?? {},
      body: request.body ?? null,
    });
    return normalise(result);
  } catch (err) {
    return errorResponse(err);
  }
}

function normalise(result) {
  if (result === undefined || result === null) return { status: 204, body: null };
  if (typeof result === 'object' && 'status' in result) {
    return { status: result.status, body: result.body ?? null };
  }
  return { status: 200, body: result };
}

const STATUS_BY_CODE = new Map([
  ['invalid_argument', 400],
  ['draft_error', 422],
  ['capacity_error', 409],
  ['conflict', 409],
  ['not_found', 404],
]);

/** Turn an error into a response, keeping its code and detail. */
export function errorResponse(err) {
  if (err instanceof NotFound) {
    return { status: 404, body: { error: err.message, code: err.code, detail: err.detail } };
  }
  const status = STATUS_BY_CODE.get(err?.code);
  if (status === undefined) {
    return { status: 500, body: { error: 'unhandled', message: String(err?.message ?? err) } };
  }
  return { status, body: { error: err.message, code: err.code, detail: err.detail } };
}

/** Every route in the table, for a service description page. */
export function describeRoutes(router) {
  return router.routes.map((entry) => `${entry.method} ${entry.path}`);
}
