import { ConflictError, InvalidArgument } from '../util/errors.js';
import { nonEmptyString, oneOf, plainObject, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { tallyOver } from '../util/group.js';
import { contentId } from '../util/ids.js';

/**
 * Orders and the states they move through.
 *
 * An order is a length of a named style for a named customer, wanted by a date.
 * It moves forward through a small set of states and never backwards, because a
 * shed that can quietly un-weave an order loses the ability to say what it has
 * actually made.
 */

export const ORDER_STATES = ['quoted', 'confirmed', 'warped', 'weaving', 'finished', 'despatched'];
export const CANCELLED = 'cancelled';

const FORWARD = new Map(
  ORDER_STATES.map((state, index) => [state, ORDER_STATES[index + 1] ?? null]),
);

/** Open an order in the quoted state. */
export function createOrder(input) {
  plainObject(input, 'order');
  const order = {
    reference: nonEmptyString(input.reference, 'reference'),
    customer: nonEmptyString(input.customer, 'customer'),
    style: nonEmptyString(input.style, 'style'),
    metres: positiveNumber(input.metres, 'metres'),
    widthCm: positiveNumber(input.widthCm, 'widthCm'),
    pricePerMetre: positiveNumber(input.pricePerMetre, 'pricePerMetre'),
    wantedBy: input.wantedBy ?? null,
    state: oneOf(input.state ?? 'quoted', 'state', [...ORDER_STATES, CANCELLED]),
  };
  order.value = roundTo(order.metres * order.pricePerMetre, 2);
  order.id = contentId('order', order);
  return Object.freeze(order);
}

/** The state an order moves to next, or null when it is finished. */
export function nextState(state) {
  oneOf(state, 'state', [...ORDER_STATES, CANCELLED]);
  return state === CANCELLED ? null : FORWARD.get(state);
}

/** Move an order on one step. */
export function advanceOrder(order, { to } = {}) {
  const wanted = to ?? nextState(order.state);
  if (wanted === null) {
    throw new ConflictError(`${order.reference} is already ${order.state}`, {
      reference: order.reference,
      state: order.state,
    });
  }
  oneOf(wanted, 'to', [...ORDER_STATES, CANCELLED]);
  if (wanted !== CANCELLED && stateIndex(wanted) <= stateIndex(order.state)) {
    throw new ConflictError('an order cannot go backwards', {
      reference: order.reference,
      from: order.state,
      to: wanted,
    });
  }
  return createOrder({ ...order, state: wanted });
}

/** Cancel an order, which is allowed until it has been despatched. */
export function cancelOrder(order) {
  if (order.state === 'despatched') {
    throw new ConflictError('a despatched order cannot be cancelled', {
      reference: order.reference,
    });
  }
  return createOrder({ ...order, state: CANCELLED });
}

function stateIndex(state) {
  const at = ORDER_STATES.indexOf(state);
  if (at === -1) {
    throw new InvalidArgument(`${state} is not a running state`, { state });
  }
  return at;
}

/** True when the order still has work to do on the floor. */
export function isOpen(order) {
  return order.state !== 'despatched' && order.state !== CANCELLED;
}

/** The orders still open, in wanted-by order with the undated last. */
export function openOrders(orders) {
  return orders
    .filter((order) => isOpen(order))
    .sort((a, b) => {
      if (a.wantedBy === b.wantedBy) return a.reference.localeCompare(b.reference);
      if (a.wantedBy === null) return 1;
      if (b.wantedBy === null) return -1;
      return a.wantedBy.localeCompare(b.wantedBy);
    });
}

/** The order book, totalled by state. */
export function orderBook(orders) {
  const totals = tallyOver([...ORDER_STATES, CANCELLED], orders, {
    key: (order) => order.state,
    label: 'state',
    fields: { metres: (order) => order.metres, value: (order) => order.value },
  });
  return {
    orders: orders.length,
    openMetres: roundTo(
      orders.filter((order) => isOpen(order)).reduce((total, order) => total + order.metres, 0),
      3,
    ),
    byState: totals.map(({ state, count, metres, value }) => ({
      state,
      orders: count,
      metres,
      value: roundTo(value, 2),
    })),
  };
}

/** Every order for one style, whatever state it is in. */
export function ordersForStyle(orders, style) {
  nonEmptyString(style, 'style');
  return orders.filter((order) => order.style === style);
}
