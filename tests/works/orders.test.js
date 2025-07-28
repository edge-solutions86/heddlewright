import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument } from '../../src/util/errors.js';
import {
  ORDER_STATES,
  CANCELLED,
  createOrder,
  nextState,
  advanceOrder,
  cancelOrder,
  isOpen,
  openOrders,
  orderBook,
  ordersForStyle,
} from '../../src/works/orders.js';

const ORDER = createOrder({
  reference: 'SO-1001',
  customer: 'Ravensward',
  style: 'gabardine',
  metres: 500,
  widthCm: 150,
  pricePerMetre: 6.4,
  wantedBy: '2025-09-01',
});

describe('createOrder', () => {
  it('prices the order and freezes it', () => {
    expect(ORDER.value).toBe(3200);
    expect(ORDER.state).toBe('quoted');
    expect(Object.isFrozen(ORDER)).toBe(true);
  });

  it('gives it a content id', () => {
    expect(ORDER.id).toMatch(/^order-[0-9a-f]{8}$/);
  });

  it('refuses an order with no length or no customer', () => {
    expect(() => createOrder({ ...ORDER, metres: 0 })).toThrow(InvalidArgument);
    expect(() => createOrder({ ...ORDER, customer: '' })).toThrow(InvalidArgument);
  });

  it('refuses a state it does not know', () => {
    expect(() => createOrder({ ...ORDER, state: 'lost' })).toThrow(InvalidArgument);
  });
});

describe('nextState', () => {
  it('walks the states in order', () => {
    expect(nextState('quoted')).toBe('confirmed');
    expect(nextState('weaving')).toBe('finished');
    expect(nextState('despatched')).toBeNull();
    expect(nextState(CANCELLED)).toBeNull();
  });

  it('names every state it moves through', () => {
    expect(ORDER_STATES).toEqual([
      'quoted',
      'confirmed',
      'warped',
      'weaving',
      'finished',
      'despatched',
    ]);
  });
});

describe('advanceOrder', () => {
  it('moves an order on one step', () => {
    expect(advanceOrder(ORDER).state).toBe('confirmed');
  });

  it('jumps to a later state when told to', () => {
    expect(advanceOrder(ORDER, { to: 'weaving' }).state).toBe('weaving');
  });

  it('refuses to go backwards', () => {
    const weaving = advanceOrder(ORDER, { to: 'weaving' });
    expect(() => advanceOrder(weaving, { to: 'confirmed' })).toThrow(/cannot go backwards/);
    expect(() => advanceOrder(weaving, { to: 'weaving' })).toThrow(ConflictError);
  });

  it('refuses to move a despatched order', () => {
    const done = advanceOrder(ORDER, { to: 'despatched' });
    expect(() => advanceOrder(done)).toThrow(/already despatched/);
  });

  it('gives the moved order a new id', () => {
    expect(advanceOrder(ORDER).id).not.toBe(ORDER.id);
  });

  it('walks the whole run through to despatch', () => {
    let order = ORDER;
    for (const state of ORDER_STATES.slice(1)) {
      order = advanceOrder(order);
      expect(order.state).toBe(state);
    }
    expect(order.state).toBe('despatched');
  });
});

describe('cancelOrder', () => {
  it('cancels an open order', () => {
    expect(cancelOrder(ORDER).state).toBe(CANCELLED);
    expect(cancelOrder(advanceOrder(ORDER, { to: 'weaving' })).state).toBe(CANCELLED);
  });

  it('refuses to cancel one already despatched', () => {
    const done = advanceOrder(ORDER, { to: 'despatched' });
    expect(() => cancelOrder(done)).toThrow(ConflictError);
  });
});

describe('isOpen and openOrders', () => {
  const other = createOrder({ ...ORDER, reference: 'SO-1002', wantedBy: '2025-08-01' });
  const undated = createOrder({ ...ORDER, reference: 'SO-1003', wantedBy: null });
  const done = advanceOrder(ORDER, { to: 'despatched' });

  it('counts anything not despatched or cancelled as open', () => {
    expect(isOpen(ORDER)).toBe(true);
    expect(isOpen(done)).toBe(false);
    expect(isOpen(cancelOrder(ORDER))).toBe(false);
  });

  it('sorts open orders by wanted date, undated last', () => {
    const sorted = openOrders([undated, ORDER, other, done]);
    expect(sorted.map((order) => order.reference)).toEqual(['SO-1002', 'SO-1001', 'SO-1003']);
  });
});

describe('orderBook', () => {
  it('totals by state, listing every state even at zero', () => {
    const book = orderBook([ORDER, advanceOrder(ORDER, { to: 'weaving' })]);
    expect(book.orders).toBe(2);
    expect(book.byState.map((entry) => entry.state)).toEqual([...ORDER_STATES, CANCELLED]);
    expect(book.byState.find((entry) => entry.state === 'weaving').metres).toBe(500);
  });

  it('counts only open orders in the open metres', () => {
    const book = orderBook([ORDER, advanceOrder(ORDER, { to: 'despatched' })]);
    expect(book.openMetres).toBe(500);
  });
});

describe('ordersForStyle', () => {
  it('picks out one style', () => {
    const other = createOrder({ ...ORDER, reference: 'SO-2', style: 'poplin' });
    expect(ordersForStyle([ORDER, other], 'poplin').length).toBe(1);
    expect(ordersForStyle([ORDER, other], 'nothing')).toEqual([]);
  });
});
