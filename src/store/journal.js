import { ConflictError, InvalidArgument, NotFound } from '../util/errors.js';
import { array, integer, nonEmptyString, plainObject } from '../util/check.js';
import { canonicalClone, canonicalStringify, deepFreeze } from '../util/json.js';
import { stableHash } from '../util/ids.js';

/**
 * The append-only journal.
 *
 * Nothing in this package is edited in place. A draft is revised by appending
 * the revision, a piece is regraded by appending the regrade, and the current
 * state is whatever falls out of replaying the entries in order. Each entry
 * carries the hash of the one before it, so a journal that has been meddled
 * with can be spotted rather than merely suspected.
 */

export const GENESIS = '00000000';

/** Open an empty journal, or reopen one from entries already written. */
export function createJournal(entries = []) {
  array(entries, 'entries');
  const journal = { entries: [] };
  for (const entry of entries) appendRaw(journal, entry);
  return journal;
}

/** Append an event and return the entry that was written. */
export function append(journal, { type, payload = {}, at }) {
  nonEmptyString(type, 'type');
  plainObject(payload, 'payload');
  const sequence = journal.entries.length + 1;
  const previous = sequence === 1 ? GENESIS : journal.entries[sequence - 2].hash;
  const entry = {
    sequence,
    type,
    // The journal takes its own copy. Freezing the caller's object instead
    // would reach back out of the store and lock a record the caller still
    // means to edit, which is a surprise nobody would connect to appending.
    payload: canonicalClone(payload),
    at: at ?? null,
    previous,
  };
  entry.hash = entryHash(entry);
  journal.entries.push(deepFreeze(entry));
  return entry;
}

function appendRaw(journal, entry) {
  plainObject(entry, 'entry');
  const expected = journal.entries.length + 1;
  if (entry.sequence !== expected) {
    throw new ConflictError('the journal entries are out of order', {
      expected,
      got: entry.sequence,
    });
  }
  const previous = expected === 1 ? GENESIS : journal.entries[expected - 2].hash;
  if (entry.previous !== previous) {
    throw new ConflictError('an entry does not follow the one before it', {
      sequence: entry.sequence,
      expected: previous,
      got: entry.previous,
    });
  }
  if (entry.hash !== entryHash(entry)) {
    throw new ConflictError('an entry has been altered since it was written', {
      sequence: entry.sequence,
    });
  }
  journal.entries.push(deepFreeze({ ...entry }));
  return entry;
}

/** The hash an entry should carry, from everything but the hash itself. */
export function entryHash({ sequence, type, payload, at, previous }) {
  return stableHash(canonicalStringify({ sequence, type, payload, at, previous }));
}

/** Every entry, oldest first. */
export function entries(journal) {
  return [...journal.entries];
}

/** Entries after a sequence number, for a reader catching up. */
export function since(journal, sequence) {
  integer(sequence, 'sequence', { min: 0 });
  return journal.entries.filter((entry) => entry.sequence > sequence);
}

/** Entries of one type, oldest first. */
export function ofType(journal, type) {
  nonEmptyString(type, 'type');
  return journal.entries.filter((entry) => entry.type === type);
}

/** One entry by sequence number. */
export function at(journal, sequence) {
  integer(sequence, 'sequence', { min: 1 });
  const found = journal.entries[sequence - 1];
  if (found === undefined) {
    throw new NotFound(`the journal has no entry ${sequence}`, {
      sequence,
      length: journal.entries.length,
    });
  }
  return found;
}

/** How many entries the journal holds. */
export function length(journal) {
  return journal.entries.length;
}

/** The hash of the last entry, which identifies the whole journal so far. */
export function head(journal) {
  if (journal.entries.length === 0) return GENESIS;
  return journal.entries[journal.entries.length - 1].hash;
}

/**
 * Walk the chain and report the first entry that does not follow. A journal
 * that reads clean here has not been edited after the fact.
 */
export function verify(journal) {
  let previous = GENESIS;
  for (const [index, entry] of journal.entries.entries()) {
    if (entry.sequence !== index + 1) {
      return { sound: false, sequence: index + 1, reason: 'out of order' };
    }
    if (entry.previous !== previous) {
      return { sound: false, sequence: entry.sequence, reason: 'broken chain' };
    }
    if (entry.hash !== entryHash(entry)) {
      return { sound: false, sequence: entry.sequence, reason: 'altered entry' };
    }
    previous = entry.hash;
  }
  return { sound: true, entries: journal.entries.length, head: previous };
}

/** Refuse a journal that does not verify. */
export function assertSound(journal) {
  const result = verify(journal);
  if (!result.sound) {
    throw new ConflictError(`the journal is not sound at entry ${result.sequence}`, result);
  }
  return journal;
}

/** The journal as plain data, for writing to a file. */
export function toJson(journal) {
  return canonicalStringify(journal.entries);
}

/** Read a journal back from that form, checking the chain as it goes. */
export function fromJson(text) {
  const parsed = JSON.parse(text);
  if (!Array.isArray(parsed)) {
    throw new InvalidArgument('a journal file holds an array of entries', {
      got: typeof parsed,
    });
  }
  return createJournal(parsed);
}
