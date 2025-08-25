import { plainObject, positiveNumber } from '../util/check.js';
import { route } from './router.js';
import { get, insert, list, put } from '../store/repository.js';
import { createPiece, registerTotals, reconcile, firstQualityShare } from '../cloth/roll.js';
import { recordSheet, inspectionSheet } from '../cloth/inspection.js';
import { scoreRoll, gradeRoll } from '../cloth/fourpoint.js';
import { cutRoll } from '../cloth/cutplan.js';

/**
 * The cloth side of the service.
 *
 * Pieces are stored; scores are not. A grading is worked out from the defect
 * sheet every time it is asked for, so the day the acceptance limit changes,
 * every piece is regraded by the next request rather than by a migration.
 */

export const PIECES = 'pieces';
export const DEFECTS = 'defects';

/** Add the cloth routes to a router. */
export function mountClothRoutes(router, repository) {
  route(router, 'GET', '/pieces', () => list(repository, PIECES));

  // This one comes before /pieces/:reference on purpose. The router takes the
  // first route that fits, and "totals" is a perfectly good piece reference as
  // far as the pattern is concerned.
  route(router, 'GET', '/pieces/totals', () => registerTotals(list(repository, PIECES)));

  route(router, 'GET', '/pieces/:reference', ({ params }) =>
    get(repository, PIECES, params.reference),
  );

  route(router, 'POST', '/pieces', ({ body }) => {
    plainObject(body, 'body');
    const piece = createPiece(body);
    insert(repository, PIECES, piece.reference, { ...piece });
    return { status: 201, body: piece };
  });

  route(router, 'POST', '/pieces/:reference/defects', ({ params, body }) => {
    plainObject(body, 'body');
    const piece = get(repository, PIECES, params.reference);
    const sheet = recordSheet(body.defects ?? []);
    put(repository, DEFECTS, params.reference, { defects: sheet });
    return {
      status: 201,
      body: inspectionSheet(sheet, { widthInches: piece.widthInches, yards: piece.yards }),
    };
  });

  route(router, 'GET', '/pieces/:reference/grade', ({ params, query }) => {
    const piece = get(repository, PIECES, params.reference);
    const sheet = defectsFor(repository, params.reference);
    const score = scoreRoll({
      defects: sheet,
      widthInches: piece.widthInches,
      yards: piece.yards,
    });
    const acceptance = query.acceptance === undefined ? undefined : Number(query.acceptance);
    if (acceptance !== undefined) positiveNumber(acceptance, 'acceptance');
    return {
      reference: piece.reference,
      score,
      grade: gradeRoll(score, acceptance === undefined ? {} : { acceptance }),
    };
  });

  route(router, 'POST', '/pieces/:reference/cut', ({ params, body }) => {
    plainObject(body, 'body');
    const piece = get(repository, PIECES, params.reference);
    const sheet = defectsFor(repository, params.reference);
    return cutRoll({
      rollYards: piece.yards,
      defectYards: sheet.map((defect) => defect.yard),
      wantedLengths: body.lengths ?? [],
      clearance: body.clearance,
    });
  });

  route(router, 'GET', '/cloth/summary', ({ query }) => {
    const pieces = list(repository, PIECES);
    const wovenYards = query.woven === undefined ? null : Number(query.woven);
    return {
      totals: registerTotals(pieces),
      firstQualityShare: pieces.length === 0 ? 0 : firstQualityShare(pieces),
      reconciliation: wovenYards === null ? null : reconcile(pieces, wovenYards),
    };
  });

  return router;
}

/** The defect sheet stored against a piece, or an empty one. */
export function defectsFor(repository, reference) {
  const stored = repository.state.get(DEFECTS)?.get(reference);
  return stored === undefined ? [] : stored.defects;
}
