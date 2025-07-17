import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument, NotFound } from '../../src/util/errors.js';
import { createJournal, length, toJson, fromJson } from '../../src/store/journal.js';
import {
  PUT,
  REMOVE,
  createRepository,
  project,
  put,
  insert,
  get,
  find,
  has,
  list,
  where,
  remove,
  collections,
  counts,
  reconcileWithJournal,
} from '../../src/store/repository.js';

function filled() {
  const repository = createRepository();
  put(repository, 'looms', 'L1', { make: 'dobby', shafts: 16 });
  put(repository, 'looms', 'L2', { make: 'jacquard', hooks: 400 });
  put(repository, 'drafts', 'D1', { name: 'twill' });
  return repository;
}

describe('put and get', () => {
  it('stores a record and reads it back', () => {
    const repository = filled();
    expect(get(repository, 'looms', 'L1')).toEqual({ make: 'dobby', shafts: 16 });
  });

  it('records the write in the journal', () => {
    const repository = filled();
    expect(length(repository.journal)).toBe(3);
    expect(repository.journal.entries[0].type).toBe(PUT);
  });

  it('freezes the stored record and copies it, so the caller cannot reach in', () => {
    const repository = createRepository();
    const record = { make: 'dobby' };
    const stored = put(repository, 'looms', 'L1', record);
    record.make = 'jacquard';
    expect(Object.isFrozen(stored)).toBe(true);
    expect(get(repository, 'looms', 'L1').make).toBe('dobby');
  });

  it('overwrites on a second put', () => {
    const repository = filled();
    put(repository, 'looms', 'L1', { make: 'shuttleless' });
    expect(get(repository, 'looms', 'L1')).toEqual({ make: 'shuttleless' });
  });

  it('says plainly when a record is not there', () => {
    expect(() => get(filled(), 'looms', 'L9')).toThrow(NotFound);
    expect(() => get(filled(), 'nothing', 'L1')).toThrow(NotFound);
  });

  it('refuses a record that is not an object', () => {
    expect(() => put(createRepository(), 'looms', 'L1', 'dobby')).toThrow(InvalidArgument);
  });
});

describe('insert', () => {
  it('writes when nothing is there', () => {
    const repository = createRepository();
    expect(insert(repository, 'looms', 'L1', { make: 'dobby' }).make).toBe('dobby');
  });

  it('refuses to overwrite', () => {
    const repository = filled();
    expect(() => insert(repository, 'looms', 'L1', { make: 'other' })).toThrow(ConflictError);
  });
});

describe('find and has', () => {
  it('answer without throwing', () => {
    const repository = filled();
    expect(find(repository, 'looms', 'L1')).not.toBeNull();
    expect(find(repository, 'looms', 'L9')).toBeNull();
    expect(has(repository, 'looms', 'L1')).toBe(true);
    expect(has(repository, 'looms', 'L9')).toBe(false);
  });
});

describe('list and where', () => {
  it('lists a collection in id order with the id folded in', () => {
    expect(list(filled(), 'looms').map((record) => record.id)).toEqual(['L1', 'L2']);
    expect(list(filled(), 'looms')[0].make).toBe('dobby');
  });

  it('is empty for a collection nothing was written to', () => {
    expect(list(filled(), 'nothing')).toEqual([]);
  });

  it('filters', () => {
    expect(where(filled(), 'looms', (loom) => loom.make === 'jacquard').length).toBe(1);
  });
});

describe('remove', () => {
  it('takes the record out and journals the removal', () => {
    const repository = filled();
    remove(repository, 'looms', 'L1');
    expect(has(repository, 'looms', 'L1')).toBe(false);
    expect(repository.journal.entries.at(-1).type).toBe(REMOVE);
  });

  it('refuses to remove what is not there', () => {
    expect(() => remove(filled(), 'looms', 'L9')).toThrow(NotFound);
  });

  it('leaves no tombstone in the projection', () => {
    const repository = filled();
    remove(repository, 'looms', 'L1');
    expect(list(repository, 'looms').map((record) => record.id)).toEqual(['L2']);
  });
});

describe('project', () => {
  it('rebuilds the same state from the journal alone', () => {
    const repository = filled();
    remove(repository, 'looms', 'L2');
    put(repository, 'drafts', 'D1', { name: 'satin' });
    const rebuilt = project(repository.journal);
    expect([...rebuilt.get('looms').keys()]).toEqual(['L1']);
    expect(rebuilt.get('drafts').get('D1')).toEqual({ name: 'satin' });
  });

  it('is the same after a round trip through the journal file', () => {
    const repository = filled();
    const reopened = createRepository(fromJson(toJson(repository.journal)));
    expect(counts(reopened)).toEqual(counts(repository));
  });

  it('is empty for an empty journal', () => {
    expect(project(createJournal()).size).toBe(0);
  });
});

describe('collections and counts', () => {
  it('name the collections holding anything', () => {
    expect(collections(filled())).toEqual(['drafts', 'looms']);
    expect(counts(filled())).toEqual({ drafts: 1, looms: 2 });
  });

  it('drops a collection once its last record goes', () => {
    const repository = filled();
    remove(repository, 'drafts', 'D1');
    expect(collections(repository)).toEqual(['looms']);
  });
});

describe('reconcileWithJournal', () => {
  it('agrees on a repository that has only been written through', () => {
    expect(reconcileWithJournal(filled())).toEqual({ agrees: true, differences: [] });
  });

  it('catches a record written straight into the state', () => {
    const repository = filled();
    repository.state.get('looms').set('L3', { make: 'smuggled' });
    const result = reconcileWithJournal(repository);
    expect(result.agrees).toBe(false);
    expect(result.differences).toEqual([{ collection: 'looms', id: 'L3' }]);
  });

  it('catches a record edited behind the journal', () => {
    const repository = filled();
    repository.state.get('looms').set('L1', { make: 'changed' });
    expect(reconcileWithJournal(repository).agrees).toBe(false);
  });
});
