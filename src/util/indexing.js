import { array } from './check.js';

/**
 * One-based position work.
 *
 * Weavers count ends, picks, shafts and treadles from one, and the arrays hold
 * them from zero. The same three shapes kept being written out by hand at every
 * call site: positions where a test holds, positions grouped by an identical
 * value, and a bit row read back as a list of set positions. They live here
 * now, so the conversion happens in one place and is tested once.
 */

/** The 1-based positions where `test` holds. */
export function positionsWhere(list, test) {
  array(list, 'list');
  const out = [];
  for (const [index, value] of list.entries()) {
    if (test(value, index)) out.push(index + 1);
  }
  return out;
}

/** The 1-based positions holding exactly `value`. */
export function positionsOf(list, value) {
  return positionsWhere(list, (candidate) => candidate === value);
}

/** The 1-based positions of the set bits in a row of 0s and 1s. */
export function setPositions(row) {
  return positionsOf(row, 1);
}

/**
 * Groups of 1-based positions whose entries share a key, keeping only the
 * groups with more than one member and preserving first-seen order.
 */
export function duplicateGroups(list, keyOf = (value) => JSON.stringify(value)) {
  array(list, 'list');
  const groups = new Map();
  for (const [index, value] of list.entries()) {
    const key = keyOf(value, index);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(index + 1);
  }
  return [...groups.values()].filter((group) => group.length > 1);
}

/**
 * Number each distinct entry from one in first-seen order, returning the label
 * per position and the representative value for each label.
 */
export function labelDistinct(list, keyOf = (value) => JSON.stringify(value)) {
  array(list, 'list');
  const seen = new Map();
  const representatives = [];
  const labels = [];
  for (const [index, value] of list.entries()) {
    const key = keyOf(value, index);
    if (!seen.has(key)) {
      seen.set(key, representatives.length + 1);
      representatives.push(value);
    }
    labels.push(seen.get(key));
  }
  return { labels, representatives, count: representatives.length };
}
