import { ConflictError, InvalidArgument, NotFound } from '../util/errors.js';
import { array, nonEmptyString, number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';
import { contentId } from '../util/ids.js';
import { tally } from '../util/group.js';

/**
 * Despatch.
 *
 * A note lists pieces, not metres. The metres follow from the pieces, and the
 * moment a note is allowed to carry a length of its own the two stop agreeing
 * and nobody can say which is right. So the length is always derived, and a
 * piece can only be on one open note at a time.
 */

/** Build a despatch note from pieces already in the register. */
export function createNote({ reference, customer, pieces, sentOn = null, carrier = '' }) {
  nonEmptyString(reference, 'reference');
  nonEmptyString(customer, 'customer');
  array(pieces, 'pieces', { min: 1 });
  for (const [index, piece] of pieces.entries()) {
    plainObject(piece, `pieces[${index}]`);
    nonEmptyString(piece.reference, `pieces[${index}].reference`);
    positiveNumber(piece.yards, `pieces[${index}].yards`);
  }
  const note = {
    reference,
    customer,
    carrier,
    sentOn,
    pieces: pieces.map((piece) => ({
      reference: piece.reference,
      yards: piece.yards,
      grade: piece.grade ?? 'first',
    })),
  };
  note.yards = roundTo(
    sumBy(note.pieces, (piece) => piece.yards, 'pieces'),
    3,
  );
  note.id = contentId('note', note);
  return Object.freeze(note);
}

/** The lengths on a note, split by grade. */
export function noteByGrade(note) {
  return tally(note.pieces, {
    key: (piece) => piece.grade,
    label: 'grade',
    fields: { yards: (piece) => piece.yards },
    sortBy: 'yards',
  });
}

/** Refuse a note that sends a piece twice, or one already on another note. */
export function assertPiecesFree(note, otherNotes) {
  array(otherNotes, 'otherNotes');
  const seen = new Set();
  for (const piece of note.pieces) {
    if (seen.has(piece.reference)) {
      throw new ConflictError(`${piece.reference} is on this note twice`, {
        piece: piece.reference,
      });
    }
    seen.add(piece.reference);
  }
  for (const other of otherNotes) {
    for (const piece of other.pieces) {
      if (seen.has(piece.reference)) {
        throw new ConflictError(`${piece.reference} is already on ${other.reference}`, {
          piece: piece.reference,
          note: other.reference,
        });
      }
    }
  }
  return note;
}

/** Mark a note sent, which it cannot be twice. */
export function sendNote(note, { on }) {
  nonEmptyString(on, 'on');
  if (note.sentOn !== null) {
    throw new ConflictError(`${note.reference} went out on ${note.sentOn}`, {
      reference: note.reference,
      sentOn: note.sentOn,
    });
  }
  return createNote({ ...note, sentOn: on });
}

/** What is still waiting to go. */
export function pending(notes) {
  array(notes, 'notes');
  return notes.filter((note) => note.sentOn === null);
}

/** What has gone, most recent first. */
export function sent(notes) {
  array(notes, 'notes');
  return notes
    .filter((note) => note.sentOn !== null)
    .sort((a, b) => b.sentOn.localeCompare(a.sentOn) || a.reference.localeCompare(b.reference));
}

/** Find a note by reference. */
export function noteByReference(notes, reference) {
  const found = notes.find((note) => note.reference === reference);
  if (found === undefined) {
    throw new NotFound(`no note called "${reference}"`, { reference });
  }
  return found;
}

/** What went out to each customer, most first. */
export function byCustomer(notes) {
  array(notes, 'notes');
  return tally(notes, {
    key: (note) => note.customer,
    label: 'customer',
    fields: { yards: (note) => note.yards },
    sortBy: 'yards',
  });
}

/** Whether a note covers what an order asked for, and by how much it falls short. */
export function coversOrder(notes, { order, orderedYards }) {
  positiveNumber(orderedYards, 'orderedYards');
  number(orderedYards, 'orderedYards', { min: 0 });
  const mine = notes.filter((note) => note.customer === order);
  const sentYards = roundTo(
    sumBy(mine, (note) => note.yards, 'notes'),
    3,
  );
  if (sentYards < 0) {
    throw new InvalidArgument('a note cannot send a negative length', { sentYards });
  }
  return {
    orderedYards,
    sentYards,
    covered: sentYards >= orderedYards,
    shortYards: roundTo(Math.max(0, orderedYards - sentYards), 3),
  };
}
