import { array, oneOf, positiveInteger } from '../util/check.js';
import { createMatrix, assertMatrix, dimensions, columns } from '../util/matrix.js';
import { duplicateGroups } from '../util/indexing.js';
import { assertThreading } from './threading.js';
import { SHED_TYPES } from './tieup.js';
import { liftplanFromTreadling } from './liftplan.js';

/**
 * The drawdown: one row per pick, one column per warp end, a set cell where the
 * warp end lies over the weft. It is the cloth, and every analysis in the
 * package reads it rather than the draft that produced it.
 */

/** Compute a drawdown from a threading and a liftplan. */
export function drawdownFromLiftplan(threading, liftplan, { shafts, shed = 'rising' } = {}) {
  assertMatrix(liftplan, 'liftplan');
  const shaftCount = shafts ?? liftplan[0].length;
  positiveInteger(shaftCount, 'shafts');
  assertThreading(threading, shaftCount);
  oneOf(shed, 'shed', SHED_TYPES);
  const cloth = createMatrix(liftplan.length, threading.length);
  for (const [p, row] of liftplan.entries()) {
    for (const [e, shaft] of threading.entries()) {
      const lifted = row[shaft - 1] === 1;
      cloth[p][e] = (shed === 'rising' ? lifted : !lifted) ? 1 : 0;
    }
  }
  return cloth;
}

/** Compute a drawdown from a full treadle draft. */
export function drawdownFromDraft(draft) {
  const { threading, tieup, treadling, shafts, treadles, shed = 'rising' } = draft;
  const plan = liftplanFromTreadling(tieup, treadling, { shafts, treadles });
  return drawdownFromLiftplan(threading, plan, { shafts, shed });
}

/** The number of picks and ends in a drawdown, named the way a weaver says them. */
export function clothSize(drawdown) {
  const { rows, cols } = dimensions(drawdown);
  return { picks: rows, ends: cols };
}

/** One warp end down the cloth, top to bottom. */
export function endColumn(drawdown, end) {
  assertMatrix(drawdown, 'drawdown');
  positiveInteger(end, 'end');
  if (end > drawdown[0].length) {
    throw new RangeError(`end ${end} is past the cloth width`);
  }
  return drawdown.map((row) => row[end - 1]);
}

/** One pick across the cloth, left to right. */
export function pickRow(drawdown, pick) {
  assertMatrix(drawdown, 'drawdown');
  positiveInteger(pick, 'pick');
  if (pick > drawdown.length) {
    throw new RangeError(`pick ${pick} is past the cloth length`);
  }
  return [...drawdown[pick - 1]];
}

/**
 * The proportion of the face showing warp. A balanced weave sits at a half; a
 * warp-faced satin runs well above it.
 */
export function warpFaceRatio(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  const total = drawdown.length * drawdown[0].length;
  const raised = drawdown.reduce((sum, row) => sum + row.reduce((s, cell) => s + cell, 0), 0);
  return raised / total;
}

/** Ends that never lift, so their warp lies loose down the back of the cloth. */
export function unwovenEnds(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  return columns(drawdown)
    .map((column, index) => ({ column, end: index + 1 }))
    .filter(({ column }) => column.every((cell) => cell === column[0]))
    .map(({ column, end }) => ({ end, state: column[0] === 1 ? 'always up' : 'always down' }));
}

/** Picks that never bind, so the weft simply lies on the surface. */
export function unwovenPicks(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  const out = [];
  for (const [p, row] of drawdown.entries()) {
    if (row.every((cell) => cell === 1) || row.every((cell) => cell === 0)) {
      out.push({ pick: p + 1, state: row[0] === 1 ? 'all warp' : 'all weft' });
    }
  }
  return out;
}

/** Two ends that lift identically on every pick, so they weave as one thread. */
export function pairedEnds(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  return duplicateGroups(columns(drawdown), (column) => column.join(''));
}

/** The back of the cloth, which is the face with every cell swapped. */
export function reverseFace(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  return drawdown.map((row) => row.map((cell) => (cell === 1 ? 0 : 1)));
}

/** Read a drawdown out of the text form, with a set cell meaning warp up. */
export function drawdownFromRows(rows) {
  array(rows, 'rows', { min: 1 });
  return assertMatrix(
    rows.map((row) => (typeof row === 'string' ? [...row].map((c) => (c === 'X' ? 1 : 0)) : row)),
    'drawdown',
  );
}
