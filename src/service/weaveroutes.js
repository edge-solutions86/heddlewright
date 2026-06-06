import { InvalidArgument } from '../util/errors.js';
import { plainObject } from '../util/check.js';
import { formatMatrix } from '../util/matrix.js';
import { route } from './router.js';
import { listWeaves, families, weaveByName, measure, measureAll } from '../weave/catalogue.js';
import { twillWeave, twillAngle, twillBalance } from '../weave/twill.js';
import { satinWeave, regularSatinMoves, hasRegularSatin } from '../weave/satin.js';
import { readsAsCrepe } from '../weave/crepe.js';
import { clothReport, renderReport } from '../analysis/report.js';
import { createDraft } from '../draft/draft.js';

/**
 * The weave side of the service.
 *
 * Nothing here is stored. Every answer is worked out from the catalogue or from
 * the arguments, which is what makes these routes safe to call as often as a
 * design tool wants to: there is no state to get out of step.
 */

/** Add the weave routes to a router. */
export function mountWeaveRoutes(router) {
  route(router, 'GET', '/weaves', ({ query }) => {
    const rows = measureAll();
    if (query.family === undefined) return rows;
    return rows.filter((row) => row.family === query.family);
  });

  route(router, 'GET', '/weaves/families', () => families());

  route(router, 'GET', '/weaves/:name', ({ params, query }) => {
    const repeat = weaveByName(params.name);
    return {
      ...measure(params.name),
      rows: query.rows === 'text' ? formatMatrix(repeat).split('\n') : repeat,
    };
  });

  route(router, 'GET', '/weaves/:name/crepe', ({ params }) => ({
    name: params.name,
    ...readsAsCrepe(weaveByName(params.name)),
  }));

  route(router, 'POST', '/twill', ({ body }) => {
    plainObject(body, 'body');
    const notation = String(body.notation ?? '2/2');
    const repeat = twillWeave(notation, body.options ?? {});
    return {
      notation,
      balance: twillBalance(notation),
      angle:
        body.endsPerInch === undefined
          ? null
          : twillAngle({
              step: body.options?.step ?? 1,
              endsPerInch: body.endsPerInch,
              picksPerInch: body.picksPerInch ?? body.endsPerInch,
            }),
      rows: formatMatrix(repeat).split('\n'),
    };
  });

  route(router, 'GET', '/satin/:threads', ({ params, query }) => {
    const threads = Number.parseInt(params.threads, 10);
    if (!Number.isInteger(threads) || threads < 1) {
      throw new InvalidArgument('the thread count must be a whole number', {
        got: params.threads,
      });
    }
    const moves = regularSatinMoves(threads);
    if (!hasRegularSatin(threads)) {
      return {
        threads,
        regular: false,
        moves,
        reason: 'no move number is coprime and clear of one',
      };
    }
    const move = query.move === undefined ? moves[0] : Number.parseInt(query.move, 10);
    return {
      threads,
      regular: true,
      moves,
      move,
      rows: formatMatrix(satinWeave(threads, move)).split('\n'),
    };
  });

  route(router, 'POST', '/report', ({ body }) => {
    plainObject(body, 'body');
    const draft = createDraft(body.draft ?? body);
    const options = body.options ?? { count: 30 };
    const report = clothReport(draft, options);
    return body.render === 'text' ? { text: renderReport(report) } : report;
  });

  return router;
}

/** The names a design tool can ask for, for a picker. */
export function weaveNames() {
  return listWeaves().map((weave) => weave.name);
}
