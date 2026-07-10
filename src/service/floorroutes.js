import { plainObject } from '../util/check.js';
import { route } from './router.js';
import { get, insert, list, put } from '../store/repository.js';
import {
  createLoom,
  styleRequirements,
  loomsFor,
  byShedding,
  loomByReference,
} from '../loom/registry.js';
import { boxMotionPlan } from '../loom/boxmotion.js';
import { createRun, schedule, loomLoad, lateOrders } from '../works/schedule.js';
import { createOrder, advanceOrder, orderBook, openOrders } from '../works/orders.js';
import { recordBook, againstPlan, loomRates } from '../works/production.js';

/**
 * The shop floor side of the service.
 *
 * The plan is never stored. It is worked out from the looms and the open orders
 * whenever it is asked for, which is the only way to be sure it reflects the
 * orders as they stand rather than as they stood when somebody last pressed a
 * button.
 */

export const LOOMS = 'looms';
export const ORDERS = 'orders';
export const OUTTURN = 'outturn';

/** Add the floor routes to a router. */
export function mountFloorRoutes(router, repository) {
  route(router, 'GET', '/looms', () => list(repository, LOOMS));

  route(router, 'GET', '/looms/summary', () => byShedding(loomRecords(repository)));

  route(router, 'GET', '/looms/:reference', ({ params }) =>
    get(repository, LOOMS, params.reference),
  );

  route(router, 'POST', '/looms', ({ body }) => {
    plainObject(body, 'body');
    const loom = createLoom(body);
    insert(repository, LOOMS, loom.reference, { ...loom });
    return { status: 201, body: loom };
  });

  route(router, 'POST', '/looms/capable', ({ body }) => {
    plainObject(body, 'body');
    const wanted = styleRequirements(body);
    return loomsFor(loomRecords(repository), wanted).map((loom) => loom.reference);
  });

  route(router, 'POST', '/looms/:reference/boxes', ({ params, body }) => {
    plainObject(body, 'body');
    const loom = loomByReference(loomRecords(repository), params.reference);
    return boxMotionPlan({
      pattern: body.pattern,
      boxes: loom.shuttleBoxes,
      boxedAt: loom.boxedAt,
      picksMinute: loom.picksMinute,
    });
  });

  route(router, 'GET', '/orders', ({ query }) => {
    const orders = orderRecords(repository);
    return query.open === 'yes' ? openOrders(orders) : orders;
  });

  route(router, 'GET', '/orders/book', () => orderBook(orderRecords(repository)));

  route(router, 'POST', '/orders', ({ body }) => {
    plainObject(body, 'body');
    const order = createOrder(body);
    insert(repository, ORDERS, order.reference, { ...order });
    return { status: 201, body: order };
  });

  route(router, 'POST', '/orders/:reference/advance', ({ params, body }) => {
    const stored = get(repository, ORDERS, params.reference);
    const moved = advanceOrder(createOrder(stored), body ?? {});
    put(repository, ORDERS, moved.reference, { ...moved });
    return moved;
  });

  route(router, 'POST', '/plan', ({ body }) => {
    plainObject(body, 'body');
    const runs = (body.runs ?? []).map((run) => createRun(run));
    const plan = schedule(loomRecords(repository), runs, body.options ?? {});
    return {
      ...plan,
      load: loomLoad(plan),
      late: body.wantedByHour === undefined ? [] : lateOrders(plan, body.wantedByHour),
    };
  });

  route(router, 'POST', '/outturn', ({ body }) => {
    plainObject(body, 'body');
    const entries = recordBook(body.entries ?? []);
    put(repository, OUTTURN, 'book', { entries });
    return { status: 201, body: { entries: entries.length, rates: loomRates(entries) } };
  });

  route(router, 'POST', '/outturn/against-plan', ({ body }) => {
    plainObject(body, 'body');
    const runs = (body.runs ?? []).map((run) => createRun(run));
    const plan = schedule(loomRecords(repository), runs, body.options ?? {});
    return againstPlan(plan, outturnBook(repository), body.orderedMetres ?? {});
  });

  return router;
}

/** The stored looms, rebuilt into loom records. */
export function loomRecords(repository) {
  return list(repository, LOOMS).map((record) => createLoom(record));
}

/** The stored orders, rebuilt into order records. */
export function orderRecords(repository) {
  return list(repository, ORDERS).map((record) => createOrder(record));
}

/** The outturn book, or an empty one. */
export function outturnBook(repository) {
  const stored = repository.state.get(OUTTURN)?.get('book');
  return stored === undefined ? [] : stored.entries;
}
