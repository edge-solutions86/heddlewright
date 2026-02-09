import { array, positiveInteger } from '../util/check.js';
import { formatMatrix } from '../util/matrix.js';
import { canonicalRotation } from '../util/cycle.js';
import { stableHash } from '../util/ids.js';
import { drawdownFromDraft } from './drawdown.js';

/**
 * When are two drafts the same draft?
 *
 * Almost never by their fields. Renumber the shafts and every list changes
 * while the cloth does not; start the threading at a different end and the same
 * thing happens. The only honest answer is to weave both and compare the cloth,
 * and then to say which of those two things was different, because a draft that
 * weaves the same cloth on fewer shafts is a better draft.
 */

/**
 * A key for the cloth a draft makes, taken round the repeat both ways so a
 * draft cut at a different end matches.
 */
export function clothKey(draft) {
  const cloth = drawdownFromDraft(draft);
  const rows = canonicalRotation(cloth.map((row) => row.join('')));
  const columns = canonicalRotation(cloth[0].map((_, e) => cloth.map((row) => row[e]).join('')));
  const byRow = rows.join('|');
  const byColumn = columns.join('|');
  return stableHash(byRow < byColumn ? byRow : byColumn);
}

/** Two drafts weave the same cloth, however their shafts are numbered. */
export function sameCloth(left, right) {
  return clothKey(left) === clothKey(right);
}

/** Two drafts are written the same way, field for field. */
export function sameWriting(left, right) {
  return (
    left.shafts === right.shafts &&
    left.treadles === right.treadles &&
    left.threading.join(',') === right.threading.join(',') &&
    left.tieup.map((entry) => entry.join('.')).join(',') ===
      right.tieup.map((entry) => entry.join('.')).join(',') &&
    left.treadling.map((pick) => pick.join('.')).join(',') ===
      right.treadling.map((pick) => pick.join('.')).join(',')
  );
}

/**
 * The permutation that turns one threading into another, or null when no
 * renumbering of the shafts will do it.
 */
export function shaftPermutation(left, right) {
  array(left, 'left', { min: 1 });
  array(right, 'right', { min: 1 });
  if (left.length !== right.length) return null;
  const mapping = new Map();
  const claimed = new Set();
  for (const [index, shaft] of left.entries()) {
    const wanted = right[index];
    if (mapping.has(shaft)) {
      if (mapping.get(shaft) !== wanted) return null;
      continue;
    }
    if (claimed.has(wanted)) return null;
    mapping.set(shaft, wanted);
    claimed.add(wanted);
  }
  return mapping;
}

/** True when two threadings differ only in how the shafts are numbered. */
export function sameThreadingUpToNumbering(left, right) {
  return shaftPermutation(left, right) !== null;
}

/**
 * Compare two drafts and say what actually differs: the cloth, the writing, or
 * only the numbering.
 */
export function compareDrafts(left, right) {
  if (sameWriting(left, right)) return { verdict: 'identical', cloth: true };
  const cloth = sameCloth(left, right);
  if (!cloth) return { verdict: 'different cloth', cloth: false };
  if (sameThreadingUpToNumbering(left.threading, right.threading)) {
    return { verdict: 'same cloth, shafts renumbered', cloth: true };
  }
  return { verdict: 'same cloth, drafted differently', cloth: true };
}

/** How many shafts a draft actually needs, whatever it says it has. */
export function shaftsNeeded(draft) {
  return new Set(draft.threading).size;
}

/**
 * The better of two drafts weaving the same cloth: fewer shafts, then fewer
 * treadles. Two drafts weaving different cloths cannot be ranked at all.
 */
export function simpler(left, right) {
  if (!sameCloth(left, right)) return null;
  const a = { shafts: shaftsNeeded(left), treadles: left.treadles };
  const b = { shafts: shaftsNeeded(right), treadles: right.treadles };
  if (a.shafts !== b.shafts) return a.shafts < b.shafts ? 'left' : 'right';
  if (a.treadles !== b.treadles) return a.treadles < b.treadles ? 'left' : 'right';
  return 'neither';
}

/** Group drafts by the cloth they weave. */
export function groupByCloth(drafts) {
  array(drafts, 'drafts', { min: 1 });
  const groups = new Map();
  for (const draft of drafts) {
    const key = clothKey(draft);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(draft.name ?? formatMatrix(drawdownFromDraft(draft)).slice(0, 8));
  }
  return [...groups.values()];
}

/** How many distinct cloths a pile of drafts actually holds. */
export function distinctCloths(drafts) {
  positiveInteger(drafts.length, 'drafts');
  return groupByCloth(drafts).length;
}
