import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, parseMatrix, dimensions } from '../../src/util/matrix.js';
import { plainWeave, basketWeave } from '../../src/weave/plain.js';
import { twillWeave } from '../../src/weave/twill.js';
import {
  OVERLAY_MODES,
  expandWeave,
  alignWeaves,
  overlayWeaves,
  interleaveEnds,
  interleavePicks,
  stitchWeave,
  cutWeave,
  assertInterlaces,
} from '../../src/weave/combine.js';

describe('expandWeave', () => {
  it('repeats each thread, which is how plain weave becomes basket', () => {
    expect(formatMatrix(expandWeave(plainWeave(), 2, 2))).toBe(formatMatrix(basketWeave(2)));
  });

  it('takes different factors down and across', () => {
    expect(formatMatrix(expandWeave(plainWeave(), 1, 3))).toBe('XXX...\n...XXX');
  });

  it('a factor of one leaves the weave alone', () => {
    expect(formatMatrix(expandWeave(plainWeave(), 1, 1))).toBe('X.\n.X');
  });

  it('refuses a factor of zero', () => {
    expect(() => expandWeave(plainWeave(), 0)).toThrow(InvalidArgument);
  });
});

describe('alignWeaves', () => {
  it('grows both to the smallest common repeat', () => {
    const aligned = alignWeaves(plainWeave(), twillWeave('2/2'));
    expect(aligned.picks).toBe(4);
    expect(aligned.ends).toBe(4);
    expect(formatMatrix(aligned.a)).toBe('X.X.\n.X.X\nX.X.\n.X.X');
  });

  it('uses the least common multiple, not the larger of the two', () => {
    const aligned = alignWeaves(parseMatrix('X.\n.X'), parseMatrix('X..\n.X.\n..X'));
    expect(aligned.picks).toBe(6);
    expect(aligned.ends).toBe(6);
  });
});

describe('overlayWeaves', () => {
  const twill = twillWeave('2/2');

  it('names its three modes', () => {
    expect(OVERLAY_MODES).toEqual(['over', 'under', 'either']);
  });

  it('raises a cell when either weave raises it', () => {
    expect(formatMatrix(overlayWeaves(plainWeave(), twill, 'over'))).toBe('XXX.\n.XXX\nX.XX\nXX.X');
  });

  it('raises a cell only when both do, which leaves the shared diagonal', () => {
    expect(formatMatrix(overlayWeaves(plainWeave(), twill, 'under'))).toBe(
      'X...\n.X..\n..X.\n...X',
    );
  });

  it('raises a cell when exactly one does, which draws a crepe', () => {
    const crepe = overlayWeaves(plainWeave(), twill, 'either');
    expect(formatMatrix(crepe)).toBe('.XX.\n..XX\nX..X\nXX..');
  });

  it('the three modes agree: over less under is exactly either', () => {
    const over = overlayWeaves(plainWeave(), twill, 'over');
    const under = overlayWeaves(plainWeave(), twill, 'under');
    const either = overlayWeaves(plainWeave(), twill, 'either');
    for (let p = 0; p < 4; p += 1) {
      for (let e = 0; e < 4; e += 1) {
        expect(over[p][e] - under[p][e]).toBe(either[p][e]);
      }
    }
  });

  it('defaults to the over mode', () => {
    expect(formatMatrix(overlayWeaves(plainWeave(), twill))).toBe(
      formatMatrix(overlayWeaves(plainWeave(), twill, 'over')),
    );
  });

  it('rejects a mode it does not know', () => {
    expect(() => overlayWeaves(plainWeave(), twill, 'beside')).toThrow(InvalidArgument);
  });
});

describe('interleaveEnds', () => {
  it('takes alternate ends from each weave', () => {
    const doubled = interleaveEnds(plainWeave(), twillWeave('2/2'));
    expect(dimensions(doubled)).toEqual({ rows: 4, cols: 8 });
  });

  it('follows an uneven order, two ends of the face to one of the back', () => {
    const cloth = interleaveEnds(parseMatrix('XX\nXX'), parseMatrix('..\n..'), [0, 0, 1]);
    expect(formatMatrix(cloth)).toBe('XX.XX.\nXX.XX.');
  });

  it('rejects an order naming a weave that is not there', () => {
    expect(() => interleaveEnds(plainWeave(), plainWeave(), [0, 2])).toThrow(InvalidArgument);
  });
});

describe('interleavePicks', () => {
  it('takes alternate picks from each weave', () => {
    const cloth = interleavePicks(parseMatrix('XX\nXX'), parseMatrix('..\n..'));
    expect(formatMatrix(cloth)).toBe('XX\n..\nXX\n..');
  });

  it('doubles the pick count against the aligned repeat', () => {
    expect(dimensions(interleavePicks(plainWeave(), twillWeave('2/2')))).toEqual({
      rows: 8,
      cols: 4,
    });
  });
});

describe('stitchWeave', () => {
  it('forces the named cells down', () => {
    const stitched = stitchWeave(parseMatrix('XX\nXX'), parseMatrix('X.\n..'));
    expect(formatMatrix(stitched)).toBe('.X\nXX');
  });

  it('forces them up when asked', () => {
    const stitched = stitchWeave(parseMatrix('..\n..'), parseMatrix('X.\n..'), { raise: true });
    expect(formatMatrix(stitched)).toBe('X.\n..');
  });

  it('leaves cells the stitch weave does not name', () => {
    const cloth = twillWeave('2/2');
    expect(formatMatrix(stitchWeave(cloth, parseMatrix('....\n....\n....\n....')))).toBe(
      formatMatrix(cloth),
    );
  });
});

describe('cutWeave', () => {
  const twill = twillWeave('2/2');

  it('takes a window out of the repeat', () => {
    expect(formatMatrix(cutWeave(twill, { picks: 2, ends: 2 }))).toBe('XX\n.X');
  });

  it('starts where it is told and wraps past the edge', () => {
    expect(formatMatrix(cutWeave(twill, { fromPick: 3, fromEnd: 3, picks: 2, ends: 2 }))).toBe(
      'XX\n.X',
    );
  });

  it('returns the whole repeat when no size is given', () => {
    expect(formatMatrix(cutWeave(twill, {}))).toBe(formatMatrix(twill));
  });
});

describe('assertInterlaces', () => {
  it('passes a real weave', () => {
    expect(assertInterlaces(plainWeave())).toEqual(plainWeave());
  });

  it('refuses a repeat that is all warp or all weft', () => {
    expect(() => assertInterlaces(parseMatrix('XX\nXX'))).toThrow(DraftError);
    expect(() => assertInterlaces(parseMatrix('..\n..'), 'backing')).toThrow(/backing/);
  });
});
