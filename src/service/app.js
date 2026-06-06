import { createRouter, route, handle, describeRoutes } from './router.js';
import { createRepository, counts } from '../store/repository.js';
import { createJournal, head, length, verify } from '../store/journal.js';
import { mountDraftRoutes } from './draftroutes.js';
import { mountClothRoutes } from './clothroutes.js';
import { mountFloorRoutes } from './floorroutes.js';
import { mountWeaveRoutes } from './weaveroutes.js';

/**
 * The whole service in one object.
 *
 * Everything is built round a single journal, so the state of the service is
 * always exactly the projection of one append-only log. The health route says
 * so out loud: it reports the journal head, which is a fingerprint of every
 * write the service has taken.
 */

export const VERSION = '0.1.0';

/** Build the service over a journal, with every route mounted. */
export function createApp({ journal = createJournal() } = {}) {
  const repository = createRepository(journal);
  const router = createRouter();

  route(router, 'GET', '/health', () => ({
    ok: true,
    version: VERSION,
    entries: length(repository.journal),
    head: head(repository.journal),
    records: counts(repository),
  }));

  route(router, 'GET', '/routes', () => describeRoutes(router));

  route(router, 'GET', '/journal', ({ query }) => {
    const from = query.since === undefined ? 0 : Number.parseInt(query.since, 10);
    return repository.journal.entries
      .filter((entry) => entry.sequence > (Number.isInteger(from) ? from : 0))
      .map((entry) => ({ sequence: entry.sequence, type: entry.type, at: entry.at }));
  });

  route(router, 'GET', '/journal/verify', () => verify(repository.journal));

  mountDraftRoutes(router, repository);
  mountClothRoutes(router, repository);
  mountFloorRoutes(router, repository);
  mountWeaveRoutes(router);

  return { router, repository, request: (request) => handle(router, request) };
}

/** The paths the service answers on, sorted, for a smoke test to walk. */
export function routeTable(app) {
  return describeRoutes(app.router).sort();
}

/** Whether two routes were declared with the same method and path. */
export function duplicateRoutes(app) {
  const seen = new Map();
  for (const line of describeRoutes(app.router)) {
    seen.set(line, (seen.get(line) ?? 0) + 1);
  }
  return [...seen.entries()].filter(([, count]) => count > 1).map(([line]) => line);
}
