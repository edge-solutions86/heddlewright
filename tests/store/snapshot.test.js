import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument } from '../../src/util/errors.js';
import { createJournal, length } from '../../src/store/journal.js';
import { createRepository, put, remove, counts, list } from '../../src/store/repository.js';
import {
  SNAPSHOT_VERSION,
  takeSnapshot,
  assertSnapshotSound,
  snapshotMatches,
  openFromSnapshot,
  applyEntry,
  entriesSaved,
} from '../../src/store/snapshot.js';

function filled() {
  const repository = createRepository();
  put(repository, 'looms', 'L1', { make: 'dobby' });
  put(repository, 'looms', 'L2', { make: 'jacquard' });
  put(repository, 'drafts', 'D1', { name: 'twill' });
  return repository;
}

describe('takeSnapshot', () => {
  it('records the state, the sequence and the head', () => {
    const repository = filled();
    const snapshot = takeSnapshot(repository);
    expect(snapshot.version).toBe(SNAPSHOT_VERSION);
    expect(snapshot.sequence).toBe(3);
    expect(snapshot.state.looms.L1).toEqual({ make: 'dobby' });
  });

  it('is empty for a fresh repository', () => {
    const snapshot = takeSnapshot(createRepository());
    expect(snapshot.sequence).toBe(0);
    expect(snapshot.state).toEqual({});
  });

  it('carries a digest over everything else in it', () => {
    expect(takeSnapshot(filled()).digest).toMatch(/^[0-9a-f]{8}$/);
  });
});

describe('assertSnapshotSound', () => {
  it('passes one straight out of takeSnapshot', () => {
    const snapshot = takeSnapshot(filled());
    expect(assertSnapshotSound(snapshot)).toBe(snapshot);
  });

  it('catches a snapshot edited after it was taken', () => {
    const snapshot = takeSnapshot(filled());
    snapshot.state.looms.L1 = { make: 'smuggled' };
    expect(() => assertSnapshotSound(snapshot)).toThrow(ConflictError);
  });

  it('refuses one written by a different version', () => {
    const snapshot = { ...takeSnapshot(filled()), version: 99 };
    expect(() => assertSnapshotSound(snapshot)).toThrow(InvalidArgument);
  });
});

describe('snapshotMatches', () => {
  it('matches the journal it was taken from', () => {
    const repository = filled();
    expect(snapshotMatches(takeSnapshot(repository), repository.journal)).toBe(true);
  });

  it('still matches once more has been written', () => {
    const repository = filled();
    const snapshot = takeSnapshot(repository);
    put(repository, 'looms', 'L3', { make: 'tappet' });
    expect(snapshotMatches(snapshot, repository.journal)).toBe(true);
  });

  it('does not match a different history', () => {
    const snapshot = takeSnapshot(filled());
    const other = createRepository();
    put(other, 'looms', 'L9', { make: 'other' });
    put(other, 'looms', 'L8', { make: 'other' });
    put(other, 'looms', 'L7', { make: 'other' });
    expect(snapshotMatches(snapshot, other.journal)).toBe(false);
  });

  it('matches an empty snapshot against anything', () => {
    expect(snapshotMatches(takeSnapshot(createRepository()), createJournal())).toBe(true);
  });
});

describe('openFromSnapshot', () => {
  it('comes back with the same state as replaying the whole journal', () => {
    const repository = filled();
    const snapshot = takeSnapshot(repository);
    put(repository, 'looms', 'L3', { make: 'tappet' });
    remove(repository, 'drafts', 'D1');
    const reopened = openFromSnapshot(snapshot, repository.journal);
    expect(counts(reopened)).toEqual(counts(repository));
    expect(list(reopened, 'looms').map((loom) => loom.id)).toEqual(['L1', 'L2', 'L3']);
  });

  it('applies a removal written after the snapshot', () => {
    const repository = filled();
    const snapshot = takeSnapshot(repository);
    remove(repository, 'looms', 'L1');
    const reopened = openFromSnapshot(snapshot, repository.journal);
    expect(list(reopened, 'looms').map((loom) => loom.id)).toEqual(['L2']);
  });

  it('refuses a snapshot from a different journal', () => {
    const snapshot = takeSnapshot(filled());
    const other = createRepository();
    put(other, 'looms', 'L9', { make: 'other' });
    expect(() => openFromSnapshot(snapshot, other.journal)).toThrow(ConflictError);
  });

  it('refuses a snapshot that has been edited', () => {
    const repository = filled();
    const snapshot = takeSnapshot(repository);
    snapshot.sequence = 1;
    expect(() => openFromSnapshot(snapshot, repository.journal)).toThrow(ConflictError);
  });
});

describe('applyEntry', () => {
  it('writes a put into a bare state map', () => {
    const repository = filled();
    const state = applyEntry(new Map(), repository.journal.entries[0]);
    expect(state.get('looms').get('L1')).toEqual({ make: 'dobby' });
  });

  it('leaves a removal for a collection nothing wrote alone', () => {
    const repository = filled();
    remove(repository, 'looms', 'L1');
    const state = applyEntry(new Map(), repository.journal.entries.at(-1));
    expect(state.size).toBe(0);
  });
});

describe('entriesSaved', () => {
  it('counts the entries a snapshot saves replaying', () => {
    const repository = filled();
    const snapshot = takeSnapshot(repository);
    put(repository, 'looms', 'L3', { make: 'tappet' });
    expect(entriesSaved(snapshot, repository.journal)).toBe(3);
    expect(length(repository.journal)).toBe(4);
  });
});
