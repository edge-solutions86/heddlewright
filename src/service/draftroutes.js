import { InvalidArgument } from '../util/errors.js';
import { plainObject } from '../util/check.js';
import { formatMatrix } from '../util/matrix.js';
import { createDraft, draftSummary, inspectDraft } from '../draft/draft.js';
import { drawdownFromDraft } from '../draft/drawdown.js';
import { longestFloat, floatsOver } from '../analysis/floats.js';
import { interlacementFactor, firmnessClass } from '../analysis/interlacement.js';
import { writeWif } from '../draft/wifwrite.js';
import { draftFromWif } from '../draft/wif.js';
import { route } from './router.js';
import { get, insert, list, remove } from '../store/repository.js';

/**
 * The draft side of the service.
 *
 * A draft is stored as the plain record that built it, not as a drawdown: the
 * cloth is derived on the way out, so a stored draft can never disagree with
 * the cloth it makes.
 */

export const COLLECTION = 'drafts';

/** Add the draft routes to a router, reading and writing through a repository. */
export function mountDraftRoutes(router, repository) {
  route(router, 'GET', '/drafts', () =>
    list(repository, COLLECTION).map((record) => draftSummary(createDraft(record))),
  );

  route(router, 'GET', '/drafts/:id', ({ params }) =>
    draftSummary(createDraft(get(repository, COLLECTION, params.id))),
  );

  route(router, 'GET', '/drafts/:id/cloth', ({ params, query }) => {
    const draft = createDraft(get(repository, COLLECTION, params.id));
    const cloth = drawdownFromDraft(draft);
    return {
      id: draft.id,
      picks: cloth.length,
      ends: cloth[0].length,
      rows: query.rows === 'text' ? formatMatrix(cloth).split('\n') : cloth,
    };
  });

  route(router, 'GET', '/drafts/:id/analysis', ({ params, query }) => {
    const draft = createDraft(get(repository, COLLECTION, params.id));
    const cloth = drawdownFromDraft(draft);
    const limit = query.floatLimit === undefined ? 3 : Number.parseInt(query.floatLimit, 10);
    if (!Number.isInteger(limit) || limit < 1) {
      throw new InvalidArgument('floatLimit must be a whole number of threads', {
        got: query.floatLimit,
      });
    }
    return {
      id: draft.id,
      longestFloat: longestFloat(cloth),
      over: floatsOver(cloth, limit).length,
      floatLimit: limit,
      interlacement: interlacementFactor(cloth),
      firmness: firmnessClass(cloth),
      problems: inspectDraft(draft),
    };
  });

  route(router, 'GET', '/drafts/:id/wif', ({ params }) => {
    const draft = createDraft(get(repository, COLLECTION, params.id));
    return { status: 200, body: { id: draft.id, wif: writeWif(draft) } };
  });

  route(router, 'POST', '/drafts', ({ body }) => {
    plainObject(body, 'body');
    const draft = body.wif === undefined ? createDraft(body) : draftFromWif(String(body.wif));
    insert(repository, COLLECTION, draft.id, storable(draft));
    return { status: 201, body: draftSummary(draft) };
  });

  route(router, 'DELETE', '/drafts/:id', ({ params }) => {
    remove(repository, COLLECTION, params.id);
    return { status: 204, body: null };
  });

  return router;
}

/** The plain record a draft is stored as, which `createDraft` reads straight back. */
export function storable(draft) {
  return {
    name: draft.name,
    shafts: draft.shafts,
    treadles: draft.treadles,
    shed: draft.shed,
    threading: [...draft.threading],
    tieup: draft.tieup.map((entry) => [...entry]),
    treadling: draft.treadling.map((pick) => [...pick]),
    notes: draft.notes,
  };
}
