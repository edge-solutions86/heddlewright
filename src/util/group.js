import { array, nonEmptyString, plainObject } from './check.js';
import { roundTo, sumBy } from './numeric.js';

/**
 * Grouping and totalling.
 *
 * Six places in this package were writing the same twenty lines: build a Map
 * keyed on something, add a few numbers into it, turn it back into an array and
 * sort. They differed in two ways that matter and no others. Either the keys
 * are whatever the data happens to hold, and the answer is ranked; or the keys
 * are a fixed list and the answer has to include the ones that scored nothing,
 * because a report that silently drops an empty category is a report that
 * cannot be read as a total.
 */

/** Group items by a key, keeping first-seen order. */
export function groupBy(items, keyOf) {
  array(items, 'items');
  const groups = new Map();
  for (const [index, item] of items.entries()) {
    const key = keyOf(item, index);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  return groups;
}

/**
 * Total a few numeric fields per group. `fields` maps an output name to a
 * function reading the number off an item; `count` is always added.
 */
export function tally(items, { key, label = 'key', fields = {}, places = 3, sortBy } = {}) {
  nonEmptyString(label, 'label');
  plainObject(fields, 'fields');
  const groups = groupBy(items, key);
  const rows = [];
  for (const [value, members] of groups) {
    const row = { [label]: value, count: members.length };
    for (const [name, read] of Object.entries(fields)) {
      row[name] = roundTo(sumBy(members, read, 'members'), places);
    }
    rows.push(row);
  }
  return sortRows(rows, label, sortBy);
}

/**
 * The same, over a fixed set of keys, so a category that scored nothing is
 * still in the answer and in the order the list gave.
 */
export function tallyOver(keys, items, { key, label = 'key', fields = {}, places = 3 } = {}) {
  array(keys, 'keys', { min: 1 });
  const found = tally(items, { key, label, fields, places });
  const byKey = new Map(found.map((row) => [row[label], row]));
  return keys.map((value) => {
    const row = byKey.get(value);
    if (row !== undefined) return row;
    const empty = { [label]: value, count: 0 };
    for (const name of Object.keys(fields)) empty[name] = 0;
    return empty;
  });
}

/** Sort rows by a named field descending, breaking a tie on the label. */
export function sortRows(rows, label, sortBy) {
  if (sortBy === undefined) return rows;
  return [...rows].sort((a, b) => {
    const difference = b[sortBy] - a[sortBy];
    if (difference !== 0) return difference;
    return String(a[label]).localeCompare(String(b[label]));
  });
}

/** The group that scored highest on a field, or null when there are none. */
export function topOf(rows, field) {
  array(rows, 'rows');
  if (rows.length === 0) return null;
  return rows.reduce((best, row) => (row[field] > best[field] ? row : best), rows[0]);
}
