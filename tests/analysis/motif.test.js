import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix, formatMatrix } from '../../src/util/matrix.js';
import { plainWeave } from '../../src/weave/plain.js';
import { twillWeave } from '../../src/weave/twill.js';
import { satinWeave } from '../../src/weave/satin.js';
import {
  tilesCloth,
  pickRepeat,
  endRepeat,
  motif,
  repeatsAcross,
  reduceToMotif,
  compression,
  sameMotif,
  assertWholeRepeats,
} from '../../src/analysis/motif.js';

const WIDE_PLAIN = parseMatrix('X.X.X.\n.X.X.X\nX.X.X.\n.X.X.X');

describe('tilesCloth', () => {
  it('is true for a block that rebuilds the cloth', () => {
    expect(tilesCloth(WIDE_PLAIN, { picks: 2, ends: 2 })).toBe(true);
  });

  it('is false for one that only matches the first repeat', () => {
    const nearly = parseMatrix('X.X.X.\n.X.X.X\nX.X.XX\n.X.X.X');
    expect(tilesCloth(nearly, { picks: 2, ends: 2 })).toBe(false);
  });

  it('is true for the whole cloth as its own block', () => {
    expect(tilesCloth(WIDE_PLAIN, { picks: 4, ends: 6 })).toBe(true);
  });
});

describe('pickRepeat and endRepeat', () => {
  it('finds the repeat of a wide plain weave', () => {
    expect(pickRepeat(WIDE_PLAIN)).toBe(2);
    expect(endRepeat(WIDE_PLAIN)).toBe(2);
  });

  it('finds the repeat of a twill drawn out wide', () => {
    const cloth = twillWeave('2/2', { picks: 8, ends: 12 });
    expect(pickRepeat(cloth)).toBe(4);
    expect(endRepeat(cloth)).toBe(4);
  });

  it('is the whole size when nothing repeats', () => {
    const satin = satinWeave(5, 2);
    expect(pickRepeat(satin)).toBe(5);
    expect(endRepeat(satin)).toBe(5);
  });
});

describe('motif', () => {
  it('cuts the cloth down to its smallest block', () => {
    const found = motif(WIDE_PLAIN);
    expect(found).toMatchObject({ picks: 2, ends: 2 });
    expect(formatMatrix(found.block)).toBe(formatMatrix(plainWeave()));
  });

  it('leaves a cloth that is already one repeat alone', () => {
    expect(formatMatrix(reduceToMotif(satinWeave(5, 2)))).toBe(formatMatrix(satinWeave(5, 2)));
  });
});

describe('repeatsAcross', () => {
  it('counts the repeats each way', () => {
    expect(repeatsAcross(WIDE_PLAIN)).toEqual({ down: 2, across: 3, whole: true });
  });

  it('reports a cloth cut part way through a repeat', () => {
    const ragged = parseMatrix('X.X.X\n.X.X.\nX.X.X\n.X.X.');
    const found = repeatsAcross(ragged);
    expect(found.whole).toBe(false);
    expect(() => assertWholeRepeats(ragged)).toThrow(InvalidArgument);
  });

  it('passes a cloth that is whole repeats', () => {
    expect(assertWholeRepeats(WIDE_PLAIN).whole).toBe(true);
  });
});

describe('compression', () => {
  it('says how much smaller the motif is', () => {
    const reading = compression(WIDE_PLAIN);
    expect(reading.cells).toBe(24);
    expect(reading.motifCells).toBe(4);
    expect(reading.ratio).toBeCloseTo(1 / 6, 6);
  });

  it('is one for a cloth already at its repeat', () => {
    expect(compression(plainWeave()).ratio).toBe(1);
  });
});

describe('sameMotif', () => {
  it('sees through the number of repeats', () => {
    expect(sameMotif(WIDE_PLAIN, plainWeave())).toBe(true);
  });

  it('separates two different weaves', () => {
    expect(sameMotif(plainWeave(), twillWeave('2/2'))).toBe(false);
  });
});
