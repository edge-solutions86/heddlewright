import { ConflictError, NotFound } from '../util/errors.js';
import { nonEmptyString, plainObject } from '../util/check.js';
import { canonicalClone, deepFreeze } from '../util/json.js';
import { append, entries, createJournal } from './journal.js';

/**
 * A repository over the journal.
 *
 * Records are never edited. Putting a record appends a `put` event, removing
 * one appends a `remove`, and the current state is the projection of the whole
 * journal. That means the state can always be rebuilt from the journal alone,
 * and the projection is a pure function, so replaying it twice cannot drift.
 */

export const PUT = 'record.put';
export const REMOVE = 'record.removed';

/** Open a repository over a journal, projecting whatever is already there. */
export function createRepository(journal = createJournal()) {
  return { journal, state: project(journal) };
}

/**
 * Rebuild the current records from the journal. Later entries win, and a
 * removal takes the record out entirely rather than leaving a tombstone in the
 * view.
 */
export function project(journal) {
  const state = new Map();
  for (const entry of entries(journal)) {
    if (entry.type === PUT) {
      const { collection, id, record } = entry.payload;
      if (!state.has(collection)) state.set(collection, new Map());
      state.get(collection).set(id, deepFreeze(canonicalClone(record)));
      continue;
    }
    if (entry.type === REMOVE) {
      const { collection, id } = entry.payload;
      state.get(collection)?.delete(id);
    }
  }
  return state;
}

/** Write a record, appending the event that makes it so. */
export function put(repository, collection, id, record, { at } = {}) {
  nonEmptyString(collection, 'collection');
  nonEmptyString(id, 'id');
  plainObject(record, 'record');
  append(repository.journal, { type: PUT, payload: { collection, id, record }, at });
  if (!repository.state.has(collection)) repository.state.set(collection, new Map());
  const stored = deepFreeze(canonicalClone(record));
  repository.state.get(collection).set(id, stored);
  return stored;
}

/** Write a record only if nothing is there, which is how a create is done. */
export function insert(repository, collection, id, record, options = {}) {
  if (has(repository, collection, id)) {
    throw new ConflictError(`${collection}/${id} already exists`, { collection, id });
  }
  return put(repository, collection, id, record, options);
}

/** Read a record, or say plainly that it is not there. */
export function get(repository, collection, id) {
  const found = repository.state.get(collection)?.get(id);
  if (found === undefined) {
    throw new NotFound(`no ${collection} called "${id}"`, { collection, id });
  }
  return found;
}

/** Read a record, or null. */
export function find(repository, collection, id) {
  return repository.state.get(collection)?.get(id) ?? null;
}

/** Whether a record is there. */
export function has(repository, collection, id) {
  return repository.state.get(collection)?.has(id) === true;
}

/** Every record in a collection, in id order. */
export function list(repository, collection) {
  const records = repository.state.get(collection);
  if (records === undefined) return [];
  return [...records.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([id, record]) => ({ id, ...record }));
}

/** Every record matching a test. */
export function where(repository, collection, test) {
  return list(repository, collection).filter((record) => test(record));
}

/** Remove a record, appending the event that removes it. */
export function remove(repository, collection, id, { at } = {}) {
  if (!has(repository, collection, id)) {
    throw new NotFound(`no ${collection} called "${id}"`, { collection, id });
  }
  append(repository.journal, { type: REMOVE, payload: { collection, id }, at });
  repository.state.get(collection).delete(id);
  return true;
}

/** The collections holding anything at all. */
export function collections(repository) {
  return [...repository.state.entries()]
    .filter(([, records]) => records.size > 0)
    .map(([name]) => name)
    .sort();
}

/** How many records each collection holds. */
export function counts(repository) {
  return Object.fromEntries(
    collections(repository).map((name) => [name, repository.state.get(name).size]),
  );
}

/**
 * Rebuild the state from the journal and compare it with the state being kept
 * alongside. They should always agree, and a difference means something has
 * written to the state without going through the journal.
 */
export function reconcileWithJournal(repository) {
  const rebuilt = project(repository.journal);
  const differences = [];
  const names = new Set([...rebuilt.keys(), ...repository.state.keys()]);
  for (const name of names) {
    const left = rebuilt.get(name) ?? new Map();
    const right = repository.state.get(name) ?? new Map();
    const ids = new Set([...left.keys(), ...right.keys()]);
    for (const id of ids) {
      if (JSON.stringify(left.get(id)) !== JSON.stringify(right.get(id))) {
        differences.push({ collection: name, id });
      }
    }
  }
  return { agrees: differences.length === 0, differences };
}
