import { InvalidArgument, NotFound } from '../util/errors.js';
import {
  array,
  nonEmptyString,
  number,
  oneOf,
  plainObject,
  positiveNumber,
} from '../util/check.js';
import { roundTo, sum } from '../util/numeric.js';
import { contentId } from '../util/ids.js';

/**
 * The piece register.
 *
 * Cloth comes off the loom as a continuous length and leaves the shed as
 * numbered pieces. A piece is cut where the cloth changes: a beam runs out, a
 * shade changes, or a stretch is too faulty to sell as first quality. The
 * register has to add back up to the length that came off the loom, and that
 * check is the one thing a shed cannot afford to get wrong.
 */

export const GRADES = ['first', 'second', 'reject'];

/** Open a piece. */
export function createPiece({ reference, yards, widthInches, grade = 'first', lot = '' }) {
  nonEmptyString(reference, 'reference');
  positiveNumber(yards, 'yards');
  positiveNumber(widthInches, 'widthInches');
  oneOf(grade, 'grade', GRADES);
  const piece = {
    reference,
    yards: roundTo(yards, 3),
    widthInches,
    grade,
    lot,
    squareYards: roundTo((yards * widthInches) / 36, 3),
  };
  piece.id = contentId('piece', piece);
  return Object.freeze(piece);
}

/**
 * Cut a piece in two at a point along its length, keeping both halves in the
 * register. The two lengths must add back to the original.
 */
export function cutPiece(piece, atYards, { suffixes = ['A', 'B'] } = {}) {
  plainObject(piece, 'piece');
  number(atYards, 'atYards', { min: 0.001 });
  if (atYards >= piece.yards) {
    throw new InvalidArgument('the cut falls at or past the end of the piece', {
      atYards,
      yards: piece.yards,
    });
  }
  array(suffixes, 'suffixes', { min: 2, max: 2 });
  return [
    createPiece({ ...piece, reference: `${piece.reference}${suffixes[0]}`, yards: atYards }),
    createPiece({
      ...piece,
      reference: `${piece.reference}${suffixes[1]}`,
      yards: roundTo(piece.yards - atYards, 3),
    }),
  ];
}

/** Cut a piece at several points at once, left to right. */
export function cutPieces(piece, atYards) {
  array(atYards, 'atYards', { min: 1 });
  const points = [...atYards].sort((a, b) => a - b);
  const out = [];
  let start = 0;
  for (const [index, at] of points.entries()) {
    if (at <= start || at >= piece.yards) {
      throw new InvalidArgument('the cuts must run forwards inside the piece', {
        at,
        previous: start,
        yards: piece.yards,
      });
    }
    out.push(
      createPiece({
        ...piece,
        reference: `${piece.reference}-${index + 1}`,
        yards: roundTo(at - start, 3),
      }),
    );
    start = at;
  }
  out.push(
    createPiece({
      ...piece,
      reference: `${piece.reference}-${points.length + 1}`,
      yards: roundTo(piece.yards - start, 3),
    }),
  );
  return out;
}

/** Regrade a piece without touching its length. */
export function regrade(piece, grade) {
  return createPiece({ ...piece, grade: oneOf(grade, 'grade', GRADES) });
}

/** The totals a register adds up to, by grade and overall. */
export function registerTotals(pieces) {
  array(pieces, 'pieces');
  const byGrade = new Map(GRADES.map((grade) => [grade, { grade, pieces: 0, yards: 0 }]));
  for (const [index, piece] of pieces.entries()) {
    plainObject(piece, `pieces[${index}]`);
    const running = byGrade.get(piece.grade);
    if (running === undefined) {
      throw new InvalidArgument(`piece ${piece.reference} carries an unknown grade`, {
        grade: piece.grade,
      });
    }
    running.pieces += 1;
    running.yards = roundTo(running.yards + piece.yards, 3);
  }
  return {
    pieces: pieces.length,
    yards: roundTo(sum(pieces.map((piece) => piece.yards)), 3),
    squareYards: roundTo(sum(pieces.map((piece) => piece.squareYards)), 3),
    byGrade: [...byGrade.values()],
  };
}

/**
 * Check a register against the length that came off the loom. Anything that
 * does not reconcile is cloth nobody can account for.
 */
export function reconcile(pieces, wovenYards, { toleranceYards = 0.5 } = {}) {
  positiveNumber(wovenYards, 'wovenYards');
  number(toleranceYards, 'toleranceYards', { min: 0 });
  const totals = registerTotals(pieces);
  const difference = roundTo(totals.yards - wovenYards, 3);
  return {
    wovenYards,
    registeredYards: totals.yards,
    difference,
    balanced: Math.abs(difference) <= toleranceYards,
  };
}

/** Find a piece by reference. */
export function pieceByReference(pieces, reference) {
  array(pieces, 'pieces');
  const found = pieces.find((piece) => piece.reference === reference);
  if (found === undefined) {
    throw new NotFound(`no piece called "${reference}"`, { reference });
  }
  return found;
}

/** The share of the register that is first quality, by length. */
export function firstQualityShare(pieces) {
  const totals = registerTotals(pieces);
  if (totals.yards === 0) return 0;
  const first = totals.byGrade.find((entry) => entry.grade === 'first');
  return roundTo(first.yards / totals.yards, 4);
}
