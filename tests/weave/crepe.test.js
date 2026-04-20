import { describe, it, expect } from 'vitest';
import { DraftError } from '../../src/util/errors.js';
import { dimensions } from '../../src/util/matrix.js';
import { warpFaceRatio } from '../../src/draft/drawdown.js';
import { twillWeave } from '../../src/weave/twill.js';
import { plainWeave } from '../../src/weave/plain.js';
import { interlacementFactor } from '../../src/analysis/interlacement.js';
import {
  satinCrepe,
  rearrangedCrepe,
  longestLine,
  diagonalScore,
  readsAsCrepe,
} from '../../src/weave/crepe.js';

describe('longestLine', () => {
  it('reads a twill as running two threads', () => {
    expect(longestLine(twillWeave('2/2'))).toEqual({ warp: 2, weft: 2, longest: 2 });
  });

  it('reads plain weave as running one', () => {
    expect(longestLine(plainWeave()).longest).toBe(1);
  });

  it('counts the down runs as well as the up ones', () => {
    expect(longestLine(twillWeave('3/1')).longest).toBe(3);
  });
});

describe('diagonalScore', () => {
  it('is one for a twill, where every cell continues the one below it', () => {
    expect(diagonalScore(twillWeave('2/2'))).toBe(1);
    expect(diagonalScore(twillWeave('3/1'))).toBe(1);
  });

  it('reads plain weave as fully diagonal too, because it is a one over one twill', () => {
    expect(diagonalScore(plainWeave())).toBe(1);
  });

  it('is nothing for a cloth whose threads have been rearranged out of line', () => {
    const rearranged = rearrangedCrepe(twillWeave('2/2', { picks: 8, ends: 8 }));
    expect(diagonalScore(rearranged)).toBe(0);
  });
});

describe('satinCrepe', () => {
  const crepe = satinCrepe({ threads: 8 });

  it('builds a repeat the size of the satin it came from', () => {
    expect(dimensions(crepe)).toEqual({ rows: 8, cols: 8 });
  });

  it('leaves the cloth balanced rather than warp or weft faced', () => {
    expect(warpFaceRatio(crepe)).toBeGreaterThan(0.3);
    expect(warpFaceRatio(crepe)).toBeLessThan(0.7);
  });

  it('keeps every line short', () => {
    expect(longestLine(crepe).longest).toBeLessThanOrEqual(3);
  });

  it('reads as a crepe', () => {
    expect(readsAsCrepe(crepe).reads).toBe(true);
  });

  it('takes a different ground', () => {
    const other = satinCrepe({ threads: 8, ground: twillWeave('3/1', { picks: 8, ends: 8 }) });
    expect(longestLine(other).longest).toBeLessThanOrEqual(3);
  });

  it('refuses a thread count with no regular satin', () => {
    expect(() => satinCrepe({ threads: 6 })).toThrow(DraftError);
  });
});

describe('rearrangedCrepe', () => {
  const twill = twillWeave('2/2', { picks: 8, ends: 8 });
  const crepe = rearrangedCrepe(twill);

  it('takes the diagonal out of a twill', () => {
    expect(diagonalScore(twill)).toBe(1);
    expect(diagonalScore(crepe)).toBeLessThan(0.5);
  });

  it('leaves the interlacement of the cloth alone', () => {
    expect(interlacementFactor(crepe)).toBe(interlacementFactor(twill));
  });

  it('leaves the balance of the cloth alone', () => {
    expect(warpFaceRatio(crepe)).toBe(warpFaceRatio(twill));
  });

  it('reads as a crepe where the twill it came from did not', () => {
    expect(readsAsCrepe(twill).reads).toBe(false);
    expect(readsAsCrepe(crepe).reads).toBe(true);
  });

  it('refuses a repeat with no satin order to rearrange by', () => {
    expect(() => rearrangedCrepe(twillWeave('2/2'))).toThrow(DraftError);
  });
});

describe('readsAsCrepe', () => {
  it('names what a cloth fails on', () => {
    const twill = twillWeave('2/2', { picks: 8, ends: 8 });
    expect(readsAsCrepe(twill).findings[0].code).toBe('diagonal');
  });

  it('fails a cloth with a long line', () => {
    const reading = readsAsCrepe(twillWeave('3/1', { picks: 8, ends: 8 }), { maxLine: 2 });
    expect(reading.findings.map((finding) => finding.code)).toContain('long_line');
  });

  it('takes a looser reading when a designer wants one', () => {
    const twill = twillWeave('2/2', { picks: 8, ends: 8 });
    expect(readsAsCrepe(twill, { maxDiagonal: 1 }).reads).toBe(true);
  });
});
