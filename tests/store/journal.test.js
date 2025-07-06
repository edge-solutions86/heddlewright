import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  GENESIS,
  createJournal,
  append,
  entryHash,
  entries,
  since,
  ofType,
  at,
  length,
  head,
  verify,
  assertSound,
  toJson,
  fromJson,
} from '../../src/store/journal.js';

function filled() {
  const journal = createJournal();
  append(journal, { type: 'draft.created', payload: { id: 'draft-1' } });
  append(journal, { type: 'draft.revised', payload: { id: 'draft-1', shafts: 8 } });
  append(journal, { type: 'piece.opened', payload: { reference: 'P1' } });
  return journal;
}

describe('append', () => {
  it('numbers entries from one and chains them', () => {
    const journal = filled();
    expect(length(journal)).toBe(3);
    expect(entries(journal)[0].sequence).toBe(1);
    expect(entries(journal)[0].previous).toBe(GENESIS);
    expect(entries(journal)[1].previous).toBe(entries(journal)[0].hash);
  });

  it('freezes what it writes', () => {
    const entry = append(createJournal(), { type: 'x' });
    expect(Object.isFrozen(entry)).toBe(true);
  });

  it('defaults the payload and the time', () => {
    const entry = append(createJournal(), { type: 'x' });
    expect(entry.payload).toEqual({});
    expect(entry.at).toBeNull();
  });

  it('keeps a time when one is given', () => {
    const entry = append(createJournal(), { type: 'x', at: '2025-04-01T09:00:00Z' });
    expect(entry.at).toBe('2025-04-01T09:00:00Z');
  });

  it('refuses an entry with no type', () => {
    expect(() => append(createJournal(), { type: '' })).toThrow(InvalidArgument);
  });
});

describe('reading', () => {
  const journal = filled();

  it('gives every entry oldest first', () => {
    expect(entries(journal).map((entry) => entry.type)).toEqual([
      'draft.created',
      'draft.revised',
      'piece.opened',
    ]);
  });

  it('catches a reader up from where it left off', () => {
    expect(since(journal, 1).map((entry) => entry.sequence)).toEqual([2, 3]);
    expect(since(journal, 3)).toEqual([]);
    expect(since(journal, 0).length).toBe(3);
  });

  it('filters by type', () => {
    expect(ofType(journal, 'draft.created').length).toBe(1);
    expect(ofType(journal, 'nothing.happened')).toEqual([]);
  });

  it('finds one entry by number and refuses one past the end', () => {
    expect(at(journal, 2).type).toBe('draft.revised');
    expect(() => at(journal, 9)).toThrow(NotFound);
    expect(() => at(journal, 0)).toThrow(InvalidArgument);
  });

  it('names the head as the last hash, and genesis when empty', () => {
    expect(head(journal)).toBe(entries(journal)[2].hash);
    expect(head(createJournal())).toBe(GENESIS);
  });
});

describe('verify', () => {
  it('reads a fresh journal as sound', () => {
    expect(verify(filled())).toMatchObject({ sound: true, entries: 3 });
    expect(verify(createJournal())).toMatchObject({ sound: true, entries: 0 });
  });

  it('spots an entry altered after it was written', () => {
    const journal = filled();
    journal.entries[1] = { ...journal.entries[1], payload: { id: 'draft-1', shafts: 16 } };
    expect(verify(journal)).toMatchObject({ sound: false, sequence: 2, reason: 'altered entry' });
  });

  it('spots a broken chain', () => {
    const journal = filled();
    journal.entries.splice(1, 1);
    journal.entries[1] = { ...journal.entries[1], sequence: 2 };
    expect(verify(journal).sound).toBe(false);
  });

  it('spots entries out of order', () => {
    const journal = filled();
    journal.entries.reverse();
    expect(verify(journal)).toMatchObject({ sound: false, reason: 'out of order' });
  });

  it('assertSound passes a good journal and throws on a bad one', () => {
    const journal = filled();
    expect(assertSound(journal)).toBe(journal);
    journal.entries[0] = { ...journal.entries[0], type: 'draft.deleted' };
    expect(() => assertSound(journal)).toThrow(ConflictError);
  });
});

describe('entryHash', () => {
  it('changes with every field it covers', () => {
    const base = { sequence: 1, type: 'a', payload: {}, at: null, previous: GENESIS };
    const hash = entryHash(base);
    expect(entryHash({ ...base, type: 'b' })).not.toBe(hash);
    expect(entryHash({ ...base, payload: { x: 1 } })).not.toBe(hash);
    expect(entryHash({ ...base, previous: 'aaaaaaaa' })).not.toBe(hash);
  });

  it('does not change when a payload key moves', () => {
    const base = { sequence: 1, type: 'a', at: null, previous: GENESIS };
    expect(entryHash({ ...base, payload: { a: 1, b: 2 } })).toBe(
      entryHash({ ...base, payload: { b: 2, a: 1 } }),
    );
  });
});

describe('toJson and fromJson', () => {
  it('round-trips a journal', () => {
    const journal = filled();
    const back = fromJson(toJson(journal));
    expect(length(back)).toBe(3);
    expect(head(back)).toBe(head(journal));
  });

  it('refuses a file whose entries have been meddled with', () => {
    const journal = filled();
    const tampered = JSON.parse(toJson(journal));
    tampered[1].payload.shafts = 16;
    expect(() => fromJson(JSON.stringify(tampered))).toThrow(/altered since it was written/);
  });

  it('refuses a file with the entries reordered', () => {
    const tampered = JSON.parse(toJson(filled())).reverse();
    expect(() => fromJson(JSON.stringify(tampered))).toThrow(ConflictError);
  });

  it('refuses a file that is not an array', () => {
    expect(() => fromJson('{}')).toThrow(InvalidArgument);
  });
});
