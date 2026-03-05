import { describe, it, expect } from 'vitest';
import { ConflictError, InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  createNote,
  noteByGrade,
  assertPiecesFree,
  sendNote,
  pending,
  sent,
  noteByReference,
  byCustomer,
  coversOrder,
} from '../../src/works/despatch.js';

const PIECES = [
  { reference: 'P1', yards: 60 },
  { reference: 'P2', yards: 40, grade: 'second' },
];

const NOTE = createNote({ reference: 'DN-1', customer: 'Ravensward', pieces: PIECES });

describe('createNote', () => {
  it('derives the length from the pieces rather than taking one', () => {
    expect(NOTE.yards).toBe(100);
  });

  it('defaults a piece with no grade to first quality', () => {
    expect(NOTE.pieces[0].grade).toBe('first');
  });

  it('gives the note an id and freezes it', () => {
    expect(NOTE.id).toMatch(/^note-[0-9a-f]{8}$/);
    expect(Object.isFrozen(NOTE)).toBe(true);
  });

  it('refuses an empty note or a piece with no length', () => {
    const base = { reference: 'DN-2', customer: 'Ravensward' };
    expect(() => createNote({ ...base, pieces: [] })).toThrow(InvalidArgument);
    expect(() => createNote({ ...base, pieces: [{ reference: 'P1', yards: 0 }] })).toThrow(
      InvalidArgument,
    );
  });
});

describe('noteByGrade', () => {
  it('splits the length by grade, most first', () => {
    expect(noteByGrade(NOTE)).toEqual([
      { grade: 'first', count: 1, yards: 60 },
      { grade: 'second', count: 1, yards: 40 },
    ]);
  });
});

describe('assertPiecesFree', () => {
  it('passes a note whose pieces are nowhere else', () => {
    expect(assertPiecesFree(NOTE, [])).toBe(NOTE);
  });

  it('refuses a note that sends a piece twice', () => {
    const doubled = createNote({
      reference: 'DN-2',
      customer: 'Ravensward',
      pieces: [PIECES[0], PIECES[0]],
    });
    expect(() => assertPiecesFree(doubled, [])).toThrow(/on this note twice/);
  });

  it('refuses a piece already on another note', () => {
    const other = createNote({ reference: 'DN-3', customer: 'Other', pieces: [PIECES[0]] });
    try {
      assertPiecesFree(NOTE, [other]);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(ConflictError);
      expect(err.detail.note).toBe('DN-3');
    }
  });
});

describe('sendNote', () => {
  it('marks a note sent on a date', () => {
    expect(sendNote(NOTE, { on: '2026-03-01' }).sentOn).toBe('2026-03-01');
  });

  it('refuses to send one twice', () => {
    const gone = sendNote(NOTE, { on: '2026-03-01' });
    expect(() => sendNote(gone, { on: '2026-03-02' })).toThrow(/went out on 2026-03-01/);
  });

  it('keeps the pieces and the length', () => {
    const gone = sendNote(NOTE, { on: '2026-03-01' });
    expect(gone.yards).toBe(NOTE.yards);
    expect(gone.pieces).toEqual(NOTE.pieces);
  });
});

describe('pending and sent', () => {
  const gone = sendNote(NOTE, { on: '2026-03-01' });
  const later = sendNote(
    createNote({ reference: 'DN-4', customer: 'Other', pieces: [PIECES[1]] }),
    { on: '2026-03-05' },
  );

  it('separates what is waiting from what has gone', () => {
    expect(pending([NOTE, gone]).map((note) => note.reference)).toEqual(['DN-1']);
    expect(sent([NOTE, gone]).map((note) => note.reference)).toEqual(['DN-1']);
  });

  it('lists what has gone most recent first', () => {
    expect(sent([gone, later]).map((note) => note.sentOn)).toEqual(['2026-03-05', '2026-03-01']);
  });
});

describe('noteByReference', () => {
  it('finds a note and refuses one that is not there', () => {
    expect(noteByReference([NOTE], 'DN-1')).toBe(NOTE);
    expect(() => noteByReference([NOTE], 'DN-9')).toThrow(NotFound);
  });
});

describe('byCustomer', () => {
  it('totals what went to each customer', () => {
    const other = createNote({ reference: 'DN-5', customer: 'Other', pieces: [PIECES[1]] });
    expect(byCustomer([NOTE, other])).toEqual([
      { customer: 'Ravensward', count: 1, yards: 100 },
      { customer: 'Other', count: 1, yards: 40 },
    ]);
  });
});

describe('coversOrder', () => {
  it('says an order is covered once enough has gone', () => {
    expect(coversOrder([NOTE], { order: 'Ravensward', orderedYards: 100 })).toEqual({
      orderedYards: 100,
      sentYards: 100,
      covered: true,
      shortYards: 0,
    });
  });

  it('says how far short a part delivery is', () => {
    const reading = coversOrder([NOTE], { order: 'Ravensward', orderedYards: 250 });
    expect(reading.covered).toBe(false);
    expect(reading.shortYards).toBe(150);
  });

  it('counts nothing for a customer with no notes', () => {
    expect(coversOrder([NOTE], { order: 'Nobody', orderedYards: 100 }).sentYards).toBe(0);
  });
});
