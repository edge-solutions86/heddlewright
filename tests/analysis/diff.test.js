import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix, formatMatrix } from '../../src/util/matrix.js';
import { twillWeave } from '../../src/weave/twill.js';
import { plainWeave } from '../../src/weave/plain.js';
import {
  assertSameShape,
  differenceMask,
  differenceCount,
  endsChanged,
  picksChanged,
  sameCloth,
  describeChange,
  renderSideBySide,
} from '../../src/analysis/diff.js';

const A = parseMatrix('XX..\n.XX.\n..XX\nX..X');
const B = parseMatrix('XX..\n.XX.\n..XX\nX.XX');

describe('assertSameShape', () => {
  it('passes two cloths of the same size', () => {
    expect(assertSameShape(A, B)).toEqual({ rows: 4, cols: 4 });
  });

  it('refuses two of different sizes', () => {
    expect(() => assertSameShape(A, plainWeave())).toThrow(InvalidArgument);
  });
});

describe('differenceMask', () => {
  it('sets a cell wherever the two disagree', () => {
    expect(formatMatrix(differenceMask(A, B))).toBe('....\n....\n....\n..X.');
  });

  it('is empty for two identical cloths', () => {
    expect(formatMatrix(differenceMask(A, A))).toBe('....\n....\n....\n....');
  });
});

describe('differenceCount', () => {
  it('counts the cells and the share of the cloth', () => {
    expect(differenceCount(A, B)).toEqual({ cells: 1, total: 16, share: 0.0625 });
  });

  it('is nothing for the same cloth twice', () => {
    expect(differenceCount(A, A).cells).toBe(0);
    expect(sameCloth(A, A)).toBe(true);
    expect(sameCloth(A, B)).toBe(false);
  });

  it('reads a whole cloth changed as a share of one', () => {
    const inverted = A.map((row) => row.map((cell) => (cell === 1 ? 0 : 1)));
    expect(differenceCount(A, inverted).share).toBe(1);
  });
});

describe('endsChanged and picksChanged', () => {
  it('names the threads that moved and how much', () => {
    expect(endsChanged(A, B)).toEqual([{ end: 3, cells: 1 }]);
    expect(picksChanged(A, B)).toEqual([{ pick: 4, cells: 1 }]);
  });

  it('is empty when nothing moved', () => {
    expect(endsChanged(A, A)).toEqual([]);
    expect(picksChanged(A, A)).toEqual([]);
  });

  it('counts every cell on a thread that changed twice', () => {
    const changed = parseMatrix('.X..\n.XX.\n..XX\n.X.X');
    expect(endsChanged(A, changed)).toEqual([
      { end: 1, cells: 2 },
      { end: 2, cells: 1 },
    ]);
  });
});

describe('describeChange', () => {
  it('calls a one cell change local', () => {
    const change = describeChange(A, B);
    expect(change.kind).toBe('local');
    expect(change.firstEnd).toBe(3);
    expect(change.firstPick).toBe(4);
  });

  it('calls a different weave wholesale', () => {
    const change = describeChange(twillWeave('2/2'), twillWeave('3/1'));
    expect(change.kind).toBe('wholesale');
    expect(change.endsChanged).toBe(4);
  });

  it('calls no change none, with no first thread to name', () => {
    const change = describeChange(A, A);
    expect(change.kind).toBe('none');
    expect(change.firstEnd).toBeNull();
    expect(change.firstPick).toBeNull();
  });

  it('takes a different line between local and wholesale', () => {
    expect(describeChange(A, B, { localShare: 0.01 }).kind).toBe('wholesale');
  });

  it('refuses a share outside nought to one', () => {
    expect(() => describeChange(A, B, { localShare: 2 })).toThrow(InvalidArgument);
  });
});

describe('renderSideBySide', () => {
  it('puts the two cloths beside each other with the changes marked', () => {
    const text = renderSideBySide(A, B);
    expect(text.split('\n')[0]).toBe('XX..   XX..       ');
    expect(text.split('\n')[3]).toBe('X..X   X.XX     ! ');
  });

  it('takes a different gap', () => {
    expect(renderSideBySide(A, A, { gap: '|' }).split('\n')[0]).toBe('XX..|XX..|    ');
  });
});
