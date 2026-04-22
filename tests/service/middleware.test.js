import { describe, it, expect } from 'vitest';
import { InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  compose,
  withTiming,
  requireBody,
  requireFields,
  withPaging,
  readPaging,
  withStamp,
  withErrorFloor,
} from '../../src/service/middleware.js';

describe('compose', () => {
  it('runs the wrappers with the first one outermost', async () => {
    const order = [];
    const mark = (name) => (next) => async (request) => {
      order.push(`in ${name}`);
      const result = await next(request);
      order.push(`out ${name}`);
      return result;
    };
    const handler = compose(() => 'done', [mark('a'), mark('b')]);
    expect(await handler({})).toBe('done');
    expect(order).toEqual(['in a', 'in b', 'out b', 'out a']);
  });

  it('returns the handler untouched with no wrappers', async () => {
    expect(await compose(() => 1)({})).toBe(1);
  });

  it('refuses something that is not a handler', () => {
    expect(() => compose(null)).toThrow(InvalidArgument);
  });
});

describe('withTiming', () => {
  it('counts the calls and adds up the time', async () => {
    const record = {};
    const handler = compose(() => 1, [withTiming(record)]);
    await handler({});
    await handler({});
    expect(record.calls).toBe(2);
    expect(record.milliseconds).toBeGreaterThanOrEqual(0);
  });

  it('counts a call that threw', async () => {
    const record = {};
    const handler = compose(() => {
      throw new NotFound('gone');
    }, [withTiming(record)]);
    await expect(handler({})).rejects.toThrow(NotFound);
    expect(record.calls).toBe(1);
  });
});

describe('requireBody and requireFields', () => {
  it('refuses a request with no body', async () => {
    const handler = compose(() => 1, [requireBody()]);
    await expect(handler({ body: null })).rejects.toThrow(/needs a body/);
    expect(await handler({ body: {} })).toBe(1);
  });

  it('names the fields a body is missing', async () => {
    const handler = compose(() => 1, [requireFields(['name', 'shafts'])]);
    try {
      await handler({ body: { name: 'twill' } });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.detail.missing).toEqual(['shafts']);
    }
  });

  it('lets a complete body through', async () => {
    const handler = compose(() => 1, [requireFields(['name'])]);
    expect(await handler({ body: { name: 'twill' } })).toBe(1);
  });

  it('refuses a wrapper declared with no fields', () => {
    expect(() => requireFields([])).toThrow(InvalidArgument);
  });
});

describe('readPaging', () => {
  it('fills in the defaults', () => {
    expect(readPaging({})).toEqual({ limit: 50, offset: 0 });
  });

  it('reads the query and floors it to whole rows', () => {
    expect(readPaging({ limit: '10.7', offset: '5.2' })).toEqual({ limit: 10, offset: 5 });
  });

  it('refuses a limit past the cap and a negative offset', () => {
    expect(() => readPaging({ limit: '10000' })).toThrow(InvalidArgument);
    expect(() => readPaging({ offset: '-1' })).toThrow(InvalidArgument);
  });
});

describe('withPaging', () => {
  const rows = Array.from({ length: 10 }, (_, index) => index + 1);

  it('cuts a list to the page asked for', async () => {
    const handler = compose(() => rows, [withPaging()]);
    const result = await handler({ query: { limit: '3', offset: '2' } });
    expect(result).toEqual({ items: [3, 4, 5], total: 10, offset: 2, limit: 3 });
  });

  it('leaves something that is not a list alone', async () => {
    const handler = compose(() => ({ ok: true }), [withPaging()]);
    expect(await handler({ query: {} })).toEqual({ ok: true });
  });

  it('reports the whole total even when the page is short', async () => {
    const handler = compose(() => rows, [withPaging()]);
    const result = await handler({ query: { offset: '8' } });
    expect(result.items.length).toBe(2);
    expect(result.total).toBe(10);
  });
});

describe('withStamp', () => {
  it('adds a field to an object body', async () => {
    const handler = compose(() => ({ ok: true }), [withStamp('version', '1.0')]);
    expect(await handler({})).toEqual({ ok: true, version: '1.0' });
  });

  it('leaves a list and a bare value alone', async () => {
    expect(await compose(() => [1], [withStamp('v', 1)])({})).toEqual([1]);
    expect(await compose(() => 'text', [withStamp('v', 1)])({})).toBe('text');
    expect(await compose(() => null, [withStamp('v', 1)])({})).toBeNull();
  });
});

describe('withErrorFloor', () => {
  it('lets one of our errors through untouched', async () => {
    const handler = compose(() => {
      throw new NotFound('gone');
    }, [withErrorFloor()]);
    await expect(handler({})).rejects.toThrow(NotFound);
  });

  it('turns anything else into an InvalidArgument', async () => {
    const handler = compose(() => {
      throw new TypeError('undefined is not a function');
    }, [withErrorFloor()]);
    await expect(handler({})).rejects.toThrow(InvalidArgument);
  });
});
