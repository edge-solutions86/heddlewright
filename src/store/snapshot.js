import { ConflictError, InvalidArgument } from '../util/errors.js';
import { integer, plainObject } from '../util/check.js';
import { canonicalStringify, canonicalClone } from '../util/json.js';
import { stableHash } from '../util/ids.js';
import { entries, head, length, since } from './journal.js';
import { createRepository, PUT, REMOVE } from './repository.js';

/**
 * Snapshots.
 *
 * Replaying a long journal to answer one question is wasteful, so the state is
 * written out at a sequence number and read back with only the entries after it
 * replayed on top. The snapshot carries the journal head it was taken at, and
 * loading refuses a journal that does not still contain that entry, because a
 * snapshot taken from a different history is worse than no snapshot at all.
 */

export const SNAPSHOT_VERSION = 1;

/** Take a snapshot of a repository as it stands. */
export function takeSnapshot(repository) {
  plainObject(repository, 'repository');
  const state = {};
  for (const [collection, records] of repository.state) {
    state[collection] = Object.fromEntries(
      [...records.entries()].map(([id, record]) => [id, canonicalClone(record)]),
    );
  }
  const snapshot = {
    version: SNAPSHOT_VERSION,
    sequence: length(repository.journal),
    head: head(repository.journal),
    state,
  };
  snapshot.digest = stableHash(canonicalStringify(snapshot));
  return snapshot;
}

/** Refuse a snapshot that has been edited since it was taken. */
export function assertSnapshotSound(snapshot) {
  plainObject(snapshot, 'snapshot');
  if (snapshot.version !== SNAPSHOT_VERSION) {
    throw new InvalidArgument('that snapshot was written by a different version', {
      version: snapshot.version,
      expected: SNAPSHOT_VERSION,
    });
  }
  const { digest, ...body } = snapshot;
  if (stableHash(canonicalStringify(body)) !== digest) {
    throw new ConflictError('the snapshot has been altered since it was taken', {
      sequence: snapshot.sequence,
    });
  }
  return snapshot;
}

/** Whether a journal still contains the history a snapshot was taken from. */
export function snapshotMatches(snapshot, journal) {
  if (length(journal) < snapshot.sequence) return false;
  if (snapshot.sequence === 0) return true;
  return entries(journal)[snapshot.sequence - 1].hash === snapshot.head;
}

/**
 * Open a repository from a snapshot and the entries written after it. The
 * result has the same state as replaying the whole journal, which is what the
 * tests hold it to.
 */
export function openFromSnapshot(snapshot, journal) {
  assertSnapshotSound(snapshot);
  if (!snapshotMatches(snapshot, journal)) {
    throw new ConflictError('that snapshot was taken from a different journal', {
      sequence: snapshot.sequence,
      head: snapshot.head,
    });
  }
  const repository = createRepository(journal);
  const state = new Map();
  for (const [collection, records] of Object.entries(snapshot.state)) {
    state.set(collection, new Map(Object.entries(records)));
  }
  // The entries after the snapshot are applied here rather than replayed
  // through a fresh journal: they carry the chain they were written with, which
  // starts part way through, so a new journal would refuse the first of them.
  for (const entry of since(journal, snapshot.sequence)) {
    applyEntry(state, entry);
  }
  repository.state = state;
  return repository;
}

/** Apply one journal entry to a state map, the same way the projection does. */
export function applyEntry(state, entry) {
  const { collection, id, record } = entry.payload;
  if (entry.type === PUT) {
    if (!state.has(collection)) state.set(collection, new Map());
    state.get(collection).set(id, canonicalClone(record));
    return state;
  }
  if (entry.type === REMOVE) {
    state.get(collection)?.delete(id);
  }
  return state;
}

/** How many entries a snapshot saves replaying. */
export function entriesSaved(snapshot, journal) {
  integer(snapshot.sequence, 'snapshot.sequence', { min: 0 });
  return Math.max(0, Math.min(snapshot.sequence, length(journal)));
}
