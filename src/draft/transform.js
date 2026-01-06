import { DraftError } from '../util/errors.js';
import { array, integer, oneOf, positiveInteger } from '../util/check.js';
import { mod } from '../util/numeric.js';
import { assertThreading } from './threading.js';
import { normaliseTieup } from './tieup.js';
import { normaliseTreadling } from './treadling.js';

/**
 * Transforming a draft.
 *
 * A weaver rewrites a draft far more often than they invent one: turn it so the
 * warp becomes the weft, mirror it, shift the threading along, or fold it back
 * on itself. Each of these is a small change to the three lists, and each one
 * has a different effect on the cloth, so they are kept apart rather than
 * gathered behind one clever function with a mode argument.
 */

export const DIRECTIONS = ['warp', 'weft'];

/** Reverse the threading, which mirrors the cloth left to right. */
export function mirrorThreading(draft) {
  assertThreading(draft.threading, draft.shafts);
  return { ...draft, threading: [...draft.threading].reverse() };
}

/** Reverse the treadling, which turns the cloth upside down. */
export function mirrorTreadling(draft) {
  normaliseTreadling(draft.treadling, draft.treadles);
  return { ...draft, treadling: [...draft.treadling].reverse() };
}

/** Shift the threading along by a number of ends, wrapping round. */
export function shiftThreading(draft, by) {
  integer(by, 'by');
  const threading = assertThreading(draft.threading, draft.shafts);
  const at = mod(by, threading.length);
  return { ...draft, threading: [...threading.slice(at), ...threading.slice(0, at)] };
}

/** Shift the treadling along by a number of picks, wrapping round. */
export function shiftTreadling(draft, by) {
  integer(by, 'by');
  const treadling = normaliseTreadling(draft.treadling, draft.treadles);
  const at = mod(by, treadling.length);
  return { ...draft, treadling: [...treadling.slice(at), ...treadling.slice(0, at)] };
}

/**
 * Turn the draft: the threading becomes the treadling and the tie-up is
 * transposed, so what was warp is now weft. It needs as many treadles as the
 * old draft had shafts and the other way round, which is where a turned draft
 * usually stops being weavable.
 */
export function turnDraft(draft) {
  const threading = assertThreading(draft.threading, draft.shafts);
  const tieup = normaliseTieup(draft.tieup, draft.shafts);
  const treadling = normaliseTreadling(draft.treadling, draft.treadles);
  if (treadling.some((pick) => pick.length !== 1)) {
    throw new DraftError('a draft with a doubled pick cannot be turned', {});
  }
  const turnedTieup = Array.from({ length: draft.shafts }, (_, shaft) =>
    tieup
      .map((entry, treadle) => (entry.includes(shaft + 1) ? treadle + 1 : 0))
      .filter((treadle) => treadle !== 0),
  );
  return {
    ...draft,
    name: `${draft.name} turned`,
    shafts: draft.treadles,
    treadles: draft.shafts,
    threading: treadling.map((pick) => pick[0]),
    tieup: turnedTieup,
    treadling: threading.map((shaft) => [shaft]),
  };
}

/**
 * Fold a list back on itself without repeating the turning thread, which is how
 * a straight draw becomes a point.
 */
export function foldBack(list) {
  array(list, 'list', { min: 2 });
  return [...list, ...[...list].reverse().slice(1, -1)];
}

/** Fold the threading, the treadling, or both. */
export function foldDraft(draft, direction) {
  oneOf(direction, 'direction', [...DIRECTIONS, 'both']);
  let folded = draft;
  if (direction === 'warp' || direction === 'both') {
    folded = { ...folded, threading: foldBack(folded.threading) };
  }
  if (direction === 'weft' || direction === 'both') {
    folded = { ...folded, treadling: foldBack(folded.treadling) };
  }
  return folded;
}

/** Repeat the threading and the treadling until they reach a given size. */
export function extendDraft(draft, { ends, picks }) {
  positiveInteger(ends, 'ends');
  positiveInteger(picks, 'picks');
  const threading = assertThreading(draft.threading, draft.shafts);
  const treadling = normaliseTreadling(draft.treadling, draft.treadles);
  return {
    ...draft,
    threading: Array.from({ length: ends }, (_, i) => threading[i % threading.length]),
    treadling: Array.from({ length: picks }, (_, i) => [...treadling[i % treadling.length]]),
  };
}

/**
 * Rewrite a draft onto the lowest shaft numbers it actually uses, which is what
 * lets a draft written for sixteen shafts go on an eight shaft loom when only
 * eight of them were ever threaded.
 */
export function compactShafts(draft) {
  const threading = assertThreading(draft.threading, draft.shafts);
  const used = [...new Set(threading)].sort((a, b) => a - b);
  const renumber = new Map(used.map((shaft, index) => [shaft, index + 1]));
  const tieup = normaliseTieup(draft.tieup, draft.shafts).map((entry) =>
    entry.map((shaft) => renumber.get(shaft)).filter((shaft) => shaft !== undefined),
  );
  return {
    ...draft,
    shafts: used.length,
    threading: threading.map((shaft) => renumber.get(shaft)),
    tieup,
  };
}
