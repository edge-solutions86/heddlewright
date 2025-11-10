import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { groupBy, tally, tallyOver, sortRows, topOf } from '../../src/util/group.js';

const STOPS = [
  { cause: 'warp', minutes: 3, count: 2 },
  { cause: 'weft', minutes: 1, count: 5 },
  { cause: 'warp', minutes: 4, count: 1 },
];

describe('groupBy', () => {
  it('groups in first-seen order', () => {
    const groups = groupBy(STOPS, (stop) => stop.cause);
    expect([...groups.keys()]).toEqual(['warp', 'weft']);
    expect(groups.get('warp').length).toBe(2);
  });

  it('passes the index to the key function', () => {
    const groups = groupBy(STOPS, (_, index) => index % 2);
    expect(groups.get(0).length).toBe(2);
  });

  it('is empty for an empty list', () => {
    expect(groupBy([], () => 'x').size).toBe(0);
  });
});

describe('tally', () => {
  it('counts the members and totals the fields', () => {
    const rows = tally(STOPS, {
      key: (stop) => stop.cause,
      label: 'cause',
      fields: { minutes: (stop) => stop.minutes },
    });
    expect(rows).toEqual([
      { cause: 'warp', count: 2, minutes: 7 },
      { cause: 'weft', count: 1, minutes: 1 },
    ]);
  });

  it('overwrites count when a field of that name is given', () => {
    const rows = tally(STOPS, {
      key: (stop) => stop.cause,
      label: 'cause',
      fields: { count: (stop) => stop.count },
    });
    expect(rows.find((row) => row.cause === 'warp').count).toBe(3);
  });

  it('leaves the rows in first-seen order unless a sort is asked for', () => {
    const key = (stop) => stop.cause;
    const fields = { minutes: (stop) => stop.minutes };
    const plain = tally(STOPS, { key, label: 'cause', fields });
    const sorted = tally(STOPS, { key, label: 'cause', fields, sortBy: 'minutes' });
    expect(plain[0].cause).toBe('warp');
    expect(sorted[0].cause).toBe('warp');
    expect(sorted[1].cause).toBe('weft');
  });

  it('rounds the totals to the places it is given', () => {
    const items = [
      { k: 'a', v: 0.1 },
      { k: 'a', v: 0.2 },
    ];
    const rows = tally(items, {
      key: (item) => item.k,
      fields: { v: (item) => item.v },
      places: 2,
    });
    expect(rows[0].v).toBe(0.3);
  });

  it('refuses a fields object that is not one', () => {
    expect(() => tally(STOPS, { key: (s) => s.cause, fields: 'minutes' })).toThrow(InvalidArgument);
  });
});

describe('tallyOver', () => {
  const keys = ['warp', 'weft', 'mechanical'];

  it('keeps a key that scored nothing, in the order the list gave', () => {
    const rows = tallyOver(keys, STOPS, {
      key: (stop) => stop.cause,
      label: 'cause',
      fields: { minutes: (stop) => stop.minutes },
    });
    expect(rows.map((row) => row.cause)).toEqual(keys);
    expect(rows[2]).toEqual({ cause: 'mechanical', count: 0, minutes: 0 });
  });

  it('gives every row the same fields, whether or not it scored', () => {
    const rows = tallyOver(keys, STOPS, {
      key: (stop) => stop.cause,
      label: 'cause',
      fields: { minutes: (stop) => stop.minutes },
    });
    for (const row of rows) {
      expect(Object.keys(row).sort()).toEqual(['cause', 'count', 'minutes']);
    }
  });

  it('is all zeros for an empty list', () => {
    const rows = tallyOver(keys, [], { key: (stop) => stop.cause, label: 'cause' });
    expect(rows.every((row) => row.count === 0)).toBe(true);
  });

  it('needs at least one key', () => {
    expect(() => tallyOver([], STOPS, { key: (stop) => stop.cause })).toThrow(InvalidArgument);
  });
});

describe('sortRows', () => {
  const rows = [
    { k: 'b', n: 2 },
    { k: 'a', n: 2 },
    { k: 'c', n: 5 },
  ];

  it('sorts descending and breaks a tie on the label', () => {
    expect(sortRows(rows, 'k', 'n').map((row) => row.k)).toEqual(['c', 'a', 'b']);
  });

  it('leaves the rows alone when no field is named', () => {
    expect(sortRows(rows, 'k').map((row) => row.k)).toEqual(['b', 'a', 'c']);
  });

  it('does not disturb the input', () => {
    sortRows(rows, 'k', 'n');
    expect(rows[0].k).toBe('b');
  });
});

describe('topOf', () => {
  it('finds the row scoring highest', () => {
    const rows = [
      { k: 'a', n: 1 },
      { k: 'b', n: 9 },
    ];
    expect(topOf(rows, 'n').k).toBe('b');
  });

  it('is null when there are no rows', () => {
    expect(topOf([], 'n')).toBeNull();
  });
});
