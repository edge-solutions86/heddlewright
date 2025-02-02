import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  positionsWhere,
  positionsOf,
  setPositions,
  duplicateGroups,
  labelDistinct,
} from '../../src/util/indexing.js';

describe('positionsWhere', () => {
  it('counts from one, not from zero', () => {
    expect(positionsWhere([5, 0, 7], (value) => value > 0)).toEqual([1, 3]);
  });

  it('passes the zero-based index to the test as well', () => {
    expect(positionsWhere(['a', 'b', 'c'], (_, index) => index % 2 === 0)).toEqual([1, 3]);
  });

  it('is empty when nothing matches, and for an empty list', () => {
    expect(positionsWhere([1, 2], () => false)).toEqual([]);
    expect(positionsWhere([], () => true)).toEqual([]);
  });

  it('refuses something that is not a list', () => {
    expect(() => positionsWhere('abc', () => true)).toThrow(InvalidArgument);
  });
});

describe('positionsOf', () => {
  it('finds every position holding the value', () => {
    expect(positionsOf([1, 2, 1, 3], 1)).toEqual([1, 3]);
    expect(positionsOf([0, 0], 0)).toEqual([1, 2]);
  });

  it('compares strictly', () => {
    expect(positionsOf([0, false, '0'], 0)).toEqual([1]);
  });
});

describe('setPositions', () => {
  it('reads a bit row as the list of raised positions', () => {
    expect(setPositions([1, 0, 1, 1])).toEqual([1, 3, 4]);
    expect(setPositions([0, 0])).toEqual([]);
  });
});

describe('duplicateGroups', () => {
  it('groups identical entries and drops the singletons', () => {
    expect(duplicateGroups(['a', 'b', 'a', 'c', 'b'])).toEqual([
      [1, 3],
      [2, 5],
    ]);
  });

  it('keeps first-seen order between groups', () => {
    expect(duplicateGroups(['z', 'a', 'z', 'a'])).toEqual([
      [1, 3],
      [2, 4],
    ]);
  });

  it('compares by structure, not by reference', () => {
    expect(
      duplicateGroups([
        [1, 2],
        [1, 2],
      ]),
    ).toEqual([[1, 2]]);
  });

  it('takes a key function for a cheaper comparison', () => {
    const groups = duplicateGroups(
      [
        [1, 0],
        [1, 0],
        [0, 1],
      ],
      (row) => row.join(''),
    );
    expect(groups).toEqual([[1, 2]]);
  });

  it('is empty when every entry differs', () => {
    expect(duplicateGroups([1, 2, 3])).toEqual([]);
  });
});

describe('labelDistinct', () => {
  it('numbers each distinct entry from one in first-seen order', () => {
    const result = labelDistinct(['b', 'a', 'b']);
    expect(result.labels).toEqual([1, 2, 1]);
    expect(result.representatives).toEqual(['b', 'a']);
    expect(result.count).toBe(2);
  });

  it('gives every position a label', () => {
    const result = labelDistinct([1, 1, 1]);
    expect(result.labels).toEqual([1, 1, 1]);
    expect(result.count).toBe(1);
  });

  it('takes a key function', () => {
    const result = labelDistinct(
      [
        [1, 0],
        [0, 1],
        [1, 0],
      ],
      (row) => row.join(''),
    );
    expect(result.labels).toEqual([1, 2, 1]);
    expect(result.representatives).toEqual([
      [1, 0],
      [0, 1],
    ]);
  });

  it('handles an empty list', () => {
    expect(labelDistinct([])).toEqual({ labels: [], representatives: [], count: 0 });
  });
});
