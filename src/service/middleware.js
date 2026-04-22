import { InvalidArgument } from '../util/errors.js';
import { integer, nonEmptyString, number, plainObject } from '../util/check.js';

/**
 * Wrapping a request.
 *
 * Everything here takes a handler and returns a handler, so the router never
 * has to know about any of it. They compose left to right, which is the order
 * they run in, because a list that reads in the opposite order to the thing it
 * describes is a list nobody gets right.
 */

/** Wrap a handler in a list of wrappers, the first running outermost. */
export function compose(handler, wrappers = []) {
  if (typeof handler !== 'function') {
    throw new InvalidArgument('compose needs a handler function', {});
  }
  return [...wrappers].reverse().reduce((next, wrap) => wrap(next), handler);
}

/** Count how many times a handler was called and how long it took. */
export function withTiming(record = {}) {
  plainObject(record, 'record');
  record.calls = record.calls ?? 0;
  record.milliseconds = record.milliseconds ?? 0;
  return (handler) => async (request) => {
    const started = process.hrtime.bigint();
    try {
      return await handler(request);
    } finally {
      record.calls += 1;
      record.milliseconds += Number(process.hrtime.bigint() - started) / 1e6;
    }
  };
}

/**
 * Refuse a request whose body is missing when the route needs one. The wrapper
 * is async so that a caller never has to handle both a throw and a rejection
 * from the same handler.
 */
export function requireBody() {
  return (handler) => async (request) => {
    if (request.body === null || request.body === undefined) {
      throw new InvalidArgument('this route needs a body', {});
    }
    return handler(request);
  };
}

/** Refuse a body missing any of the fields a route needs. */
export function requireFields(fields) {
  if (!Array.isArray(fields) || fields.length === 0) {
    throw new InvalidArgument('requireFields needs at least one field', {});
  }
  return (handler) => async (request) => {
    plainObject(request.body, 'body');
    const missing = fields.filter((field) => request.body[field] === undefined);
    if (missing.length > 0) {
      throw new InvalidArgument(`the body is missing ${missing.join(', ')}`, { missing, fields });
    }
    return handler(request);
  };
}

/**
 * Read paging out of the query and apply it to whatever the handler returns,
 * when that is a list. A handler that returns something else is left alone.
 */
export function withPaging({ defaultLimit = 50, maxLimit = 500 } = {}) {
  integer(defaultLimit, 'defaultLimit', { min: 1 });
  integer(maxLimit, 'maxLimit', { min: 1 });
  return (handler) => async (request) => {
    const page = readPaging(request.query ?? {}, { defaultLimit, maxLimit });
    const result = await handler(request);
    if (!Array.isArray(result)) return result;
    return {
      items: result.slice(page.offset, page.offset + page.limit),
      total: result.length,
      offset: page.offset,
      limit: page.limit,
    };
  };
}

/** The paging a query asks for, checked against the limits. */
export function readPaging(query, { defaultLimit = 50, maxLimit = 500 } = {}) {
  const limit = query.limit === undefined ? defaultLimit : Number(query.limit);
  const offset = query.offset === undefined ? 0 : Number(query.offset);
  number(limit, 'limit', { min: 1, max: maxLimit });
  number(offset, 'offset', { min: 0 });
  return { limit: Math.floor(limit), offset: Math.floor(offset) };
}

/** Put a fixed field into every response body a handler returns. */
export function withStamp(field, value) {
  nonEmptyString(field, 'field');
  return (handler) => async (request) => {
    const result = await handler(request);
    if (result === null || result === undefined || Array.isArray(result)) return result;
    if (typeof result !== 'object') return result;
    return { ...result, [field]: value };
  };
}

/** Turn anything a handler throws that is not ours into an InvalidArgument. */
export function withErrorFloor() {
  return (handler) => async (request) => {
    try {
      return await handler(request);
    } catch (err) {
      if (err?.code !== undefined) throw err;
      throw new InvalidArgument(err?.message ?? 'the request could not be handled', {});
    }
  };
}
