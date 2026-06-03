import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  createLine,
  reserved,
  available,
  reserve,
  release,
  issue,
  receive,
  lineByCode,
  fullyPromised,
  byOrder,
  assertStoreSound,
} from '../../src/works/stock.js';

const LINE = createLine({ code: 'C30', description: '30 tex cotton', tex: 30, kilograms: 100 });

describe('createLine', () => {
  it('opens a line with nothing promised', () => {
    expect(reserved(LINE)).toBe(0);
    expect(available(LINE)).toBe(100);
    expect(Object.isFrozen(LINE)).toBe(true);
  });

  it('takes a line with nothing on the shelf', () => {
    expect(available(createLine({ code: 'C40', tex: 25, kilograms: 0 }))).toBe(0);
  });

  it('refuses a negative stock or a count of nothing', () => {
    expect(() => createLine({ code: 'C40', tex: 25, kilograms: -1 })).toThrow(InvalidArgument);
    expect(() => createLine({ code: 'C40', tex: 0, kilograms: 1 })).toThrow(InvalidArgument);
  });
});

describe('reserve', () => {
  it('promises stock and takes it out of what is available', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 40 });
    expect(reserved(promised)).toBe(40);
    expect(available(promised)).toBe(60);
    expect(promised.kilograms).toBe(100);
  });

  it('will not promise the same yarn twice', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 80 });
    expect(() => reserve(promised, { order: 'SO-2', kilograms: 40 })).toThrow(ConflictError);
  });

  it('will not promise an order twice on the same line', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 10 });
    expect(() => reserve(promised, { order: 'SO-1', kilograms: 10 })).toThrow(/already has stock/);
  });

  it('leaves the line it was given alone', () => {
    reserve(LINE, { order: 'SO-1', kilograms: 40 });
    expect(reserved(LINE)).toBe(0);
  });
});

describe('release', () => {
  it('gives a promise back', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 40 });
    expect(available(release(promised, { order: 'SO-1' }))).toBe(100);
  });

  it('refuses to release what was never promised', () => {
    expect(() => release(LINE, { order: 'SO-9' })).toThrow(NotFound);
  });
});

describe('issue', () => {
  const promised = reserve(LINE, { order: 'SO-1', kilograms: 40 });

  it('takes stock off the shelf and off the promise together', () => {
    const issued = issue(promised, { order: 'SO-1', kilograms: 40 });
    expect(issued.kilograms).toBe(60);
    expect(reserved(issued)).toBe(0);
    expect(available(issued)).toBe(60);
  });

  it('leaves the rest of a part issue promised', () => {
    const issued = issue(promised, { order: 'SO-1', kilograms: 15 });
    expect(issued.kilograms).toBe(85);
    expect(reserved(issued)).toBe(25);
  });

  it('refuses more than the order was promised', () => {
    expect(() => issue(promised, { order: 'SO-1', kilograms: 60 })).toThrow(/more than the order/);
  });

  it('refuses an issue against no promise', () => {
    expect(() => issue(LINE, { order: 'SO-9', kilograms: 1 })).toThrow(NotFound);
  });
});

describe('receive', () => {
  it('puts stock in without touching the promises', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 40 });
    const delivered = receive(promised, { kilograms: 50 });
    expect(delivered.kilograms).toBe(150);
    expect(available(delivered)).toBe(110);
  });
});

describe('lineByCode and fullyPromised', () => {
  it('finds a line and refuses one that is not there', () => {
    expect(lineByCode([LINE], 'C30')).toBe(LINE);
    expect(() => lineByCode([LINE], 'C99')).toThrow(NotFound);
  });

  it('names the lines with nothing left to promise', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 100 });
    expect(fullyPromised([LINE, promised])).toEqual(['C30']);
  });
});

describe('byOrder', () => {
  it('adds up what each order has promised across the store', () => {
    const first = reserve(LINE, { order: 'SO-1', kilograms: 40 });
    const other = reserve(createLine({ code: 'C40', tex: 25, kilograms: 50 }), {
      order: 'SO-1',
      kilograms: 20,
    });
    expect(byOrder([first, other])).toEqual([{ order: 'SO-1', count: 2, kilograms: 60 }]);
  });

  it('is empty for a store with nothing promised', () => {
    expect(byOrder([LINE])).toEqual([]);
  });
});

describe('assertStoreSound', () => {
  it('passes a store whose promises fit', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 40 });
    expect(assertStoreSound([promised])).toEqual([promised]);
  });

  it('catches a line that has promised more than it holds', () => {
    const promised = reserve(LINE, { order: 'SO-1', kilograms: 80 });
    const shrunk = { ...promised, kilograms: 50 };
    expect(() => assertStoreSound([shrunk])).toThrow(/promised more than they hold/);
  });
});
