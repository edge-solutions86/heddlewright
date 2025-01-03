import { DraftError } from '../util/errors.js';
import { array, integer, oneOf, positiveInteger } from '../util/check.js';
import { gcd, mod } from '../util/numeric.js';
import { createMatrix } from '../util/matrix.js';

/**
 * Satins and sateens.
 *
 * A regular satin over n threads binds once per thread, stepping by a fixed
 * move number. The move has to be coprime with n, so that the binding points
 * visit every thread, and it may not be 1 or n-1, which would lay the points
 * next to each other and make a twill instead. That rules out a regular satin
 * on four or six threads, which is why the trade builds those as irregular
 * satins from a written move sequence.
 */

export const SATIN_FACES = ['warp', 'weft'];

/** Every legal move number for an n-thread regular satin, ascending. */
export function regularSatinMoves(threads) {
  positiveInteger(threads, 'threads');
  const moves = [];
  for (let move = 2; move < threads - 1; move += 1) {
    if (gcd(move, threads) === 1) moves.push(move);
  }
  return moves;
}

/** True when a regular satin exists at all on this many threads. */
export function hasRegularSatin(threads) {
  return regularSatinMoves(threads).length > 0;
}

/** Check a move number, throwing with the reason it cannot be used. */
export function assertSatinMove(threads, move) {
  positiveInteger(threads, 'threads');
  integer(move, 'move', { min: 1 });
  if (threads < 5) {
    throw new DraftError('a regular satin needs at least five threads', { threads });
  }
  if (move === 1 || move === threads - 1) {
    throw new DraftError('a move of one either way lays the binding points in a twill line', {
      threads,
      move,
    });
  }
  if (move >= threads) {
    throw new DraftError('the move must be smaller than the repeat', { threads, move });
  }
  if (gcd(move, threads) !== 1) {
    throw new DraftError('the move shares a factor with the repeat, so it closes early', {
      threads,
      move,
      commonFactor: gcd(move, threads),
    });
  }
  return move;
}

/**
 * One repeat of a regular satin. A warp-faced satin shows warp everywhere
 * except one binding point per thread; a weft-faced one, the sateen, is its
 * reverse.
 */
export function satinWeave(threads, move, { face = 'warp' } = {}) {
  assertSatinMove(threads, move);
  oneOf(face, 'face', SATIN_FACES);
  const repeat = createMatrix(threads, threads, face === 'warp' ? 1 : 0);
  for (let p = 0; p < threads; p += 1) {
    repeat[p][mod(p * move, threads)] = face === 'warp' ? 0 : 1;
  }
  return repeat;
}

/**
 * An irregular satin built from an explicit move sequence, which is how a four
 * or six thread satin is written when no regular move exists. The sequence
 * gives the step from each binding point to the next.
 */
export function irregularSatin(threads, moves, { face = 'warp' } = {}) {
  positiveInteger(threads, 'threads');
  array(moves, 'moves', { min: 1 });
  oneOf(face, 'face', SATIN_FACES);
  if (moves.length !== threads) {
    throw new DraftError('an irregular satin needs one move per thread', {
      threads,
      moves: moves.length,
    });
  }
  const positions = [];
  let at = 0;
  for (const [i, step] of moves.entries()) {
    integer(step, `moves[${i}]`);
    positions.push(at);
    at = mod(at + step, threads);
  }
  if (new Set(positions).size !== threads) {
    throw new DraftError('the move sequence does not reach every thread', {
      threads,
      reached: new Set(positions).size,
    });
  }
  const repeat = createMatrix(threads, threads, face === 'warp' ? 1 : 0);
  for (const [p, position] of positions.entries()) {
    repeat[p][position] = face === 'warp' ? 0 : 1;
  }
  return repeat;
}

/**
 * The four thread irregular satin, bound at ends 1, 3, 2, 4. On four threads no
 * arrangement can keep every binding point clear of its neighbours, which is
 * why this cloth reads as a crow weave rather than a true satin.
 */
export function fourThreadSatinMoves() {
  return [2, 3, 2, 1];
}

/** The six thread irregular satin, bound at ends 1, 3, 5, 2, 6, 4. */
export function sixThreadSatinMoves() {
  return [2, 2, 3, 4, 4, 3];
}

/** Where the binding point sits on each pick, as 1-based end numbers. */
export function bindingPoints(repeat, { face = 'warp' } = {}) {
  oneOf(face, 'face', SATIN_FACES);
  const looking = face === 'warp' ? 0 : 1;
  return repeat.map((row) => row.indexOf(looking) + 1);
}

/**
 * A satin is sound when no two binding points touch, side by side or
 * diagonally, because that is what a satin is for: an unbroken face.
 */
export function bindingPointsIsolated(repeat) {
  const threads = repeat.length;
  const points = bindingPoints(repeat).map((end) => end - 1);
  for (let p = 0; p < threads; p += 1) {
    const next = points[(p + 1) % threads];
    const here = points[p];
    const gap = Math.min(mod(next - here, threads), mod(here - next, threads));
    if (gap <= 1) return false;
  }
  return true;
}

/** The satin that weaves the back of this one. */
export function reverseSatin(repeat) {
  return repeat.map((row) => row.map((cell) => (cell === 1 ? 0 : 1)));
}
