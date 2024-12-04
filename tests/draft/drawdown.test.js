import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, parseMatrix } from '../../src/util/matrix.js';
import { straightDraw, pointDraw } from '../../src/draft/threading.js';
import { twillTieup, directTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import { liftplanFromShaftLists } from '../../src/draft/liftplan.js';
import {
  drawdownFromLiftplan,
  drawdownFromDraft,
  clothSize,
  endColumn,
  pickRow,
  warpFaceRatio,
  unwovenEnds,
  unwovenPicks,
  pairedEnds,
  reverseFace,
  drawdownFromRows,
} from '../../src/draft/drawdown.js';

const PLAIN_DRAFT = {
  threading: straightDraw(2, 4),
  tieup: [[1], [2]],
  treadling: straightTreadling(2, 4),
  shafts: 2,
  treadles: 2,
};

describe('drawdownFromLiftplan', () => {
  it('raises an end when its shaft is lifted', () => {
    const plan = liftplanFromShaftLists([[1], [2]], 2);
    const cloth = drawdownFromLiftplan([1, 2, 1, 2], plan, { shafts: 2 });
    expect(formatMatrix(cloth)).toBe('X.X.\n.X.X');
  });

  it('takes the shaft count from the liftplan when it is not given', () => {
    const plan = liftplanFromShaftLists([[1]], 3);
    expect(formatMatrix(drawdownFromLiftplan([1, 2, 3], plan))).toBe('X..');
  });

  it('sinks instead of lifting on a counterbalance loom', () => {
    const plan = liftplanFromShaftLists([[1], [2]], 2);
    const cloth = drawdownFromLiftplan([1, 2], plan, { shafts: 2, shed: 'sinking' });
    expect(formatMatrix(cloth)).toBe('.X\nX.');
  });

  it('rejects a threading that names a shaft the plan has no column for', () => {
    const plan = liftplanFromShaftLists([[1]], 2);
    expect(() => drawdownFromLiftplan([1, 3], plan, { shafts: 2 })).toThrow(InvalidArgument);
  });

  it('rejects a shed it does not know', () => {
    const plan = liftplanFromShaftLists([[1]], 2);
    expect(() => drawdownFromLiftplan([1], plan, { shed: 'half' })).toThrow(/rising, sinking/);
  });
});

describe('drawdownFromDraft', () => {
  it('weaves plain weave from a two shaft draft', () => {
    expect(formatMatrix(drawdownFromDraft(PLAIN_DRAFT))).toBe('X.X.\n.X.X\nX.X.\n.X.X');
  });

  it('weaves a 2/2 twill that steps one end per pick', () => {
    const cloth = drawdownFromDraft({
      threading: straightDraw(4, 8),
      tieup: twillTieup(4, 2),
      treadling: straightTreadling(4, 4),
      shafts: 4,
      treadles: 4,
    });
    expect(formatMatrix(cloth)).toBe('XX..XX..\n.XX..XX.\n..XX..XX\nX..XX..X');
  });

  it('turns a point threading into a diamond when trodden as writ', () => {
    const cloth = drawdownFromDraft({
      threading: pointDraw(4),
      tieup: directTieup(4),
      treadling: pointDraw(4).map((shaft) => [shaft]),
      shafts: 4,
      treadles: 4,
    });
    expect(formatMatrix(cloth)).toBe('X.....\n.X...X\n..X.X.\n...X..\n..X.X.\n.X...X');
  });
});

describe('clothSize', () => {
  it('names the rows picks and the columns ends', () => {
    expect(clothSize(drawdownFromDraft(PLAIN_DRAFT))).toEqual({ picks: 4, ends: 4 });
  });
});

describe('endColumn and pickRow', () => {
  const cloth = parseMatrix('XX..\n.XX.');

  it('reads an end down and a pick across, 1-based', () => {
    expect(endColumn(cloth, 1)).toEqual([1, 0]);
    expect(endColumn(cloth, 2)).toEqual([1, 1]);
    expect(pickRow(cloth, 2)).toEqual([0, 1, 1, 0]);
  });

  it('refuses a position off the cloth', () => {
    expect(() => endColumn(cloth, 5)).toThrow(RangeError);
    expect(() => pickRow(cloth, 3)).toThrow(RangeError);
    expect(() => pickRow(cloth, 0)).toThrow(InvalidArgument);
  });

  it('hands back a copy of the pick', () => {
    pickRow(cloth, 1)[0] = 0;
    expect(cloth[0][0]).toBe(1);
  });
});

describe('warpFaceRatio', () => {
  it('is a half for plain weave', () => {
    expect(warpFaceRatio(drawdownFromDraft(PLAIN_DRAFT))).toBe(0.5);
  });

  it('is three quarters for a 3/1 warp-faced twill', () => {
    const cloth = drawdownFromDraft({
      threading: straightDraw(4),
      tieup: twillTieup(4, 3),
      treadling: straightTreadling(4, 4),
      shafts: 4,
      treadles: 4,
    });
    expect(warpFaceRatio(cloth)).toBe(0.75);
  });

  it('is one when every cell is raised', () => {
    expect(warpFaceRatio(parseMatrix('XX\nXX'))).toBe(1);
  });
});

describe('unwoven threads', () => {
  it('finds an end that never changes state', () => {
    expect(unwovenEnds(parseMatrix('X.X\nX.X'))).toEqual([
      { end: 1, state: 'always up' },
      { end: 2, state: 'always down' },
      { end: 3, state: 'always up' },
    ]);
  });

  it('finds a pick that never binds', () => {
    expect(unwovenPicks(parseMatrix('XXX\nX.X\n...'))).toEqual([
      { pick: 1, state: 'all warp' },
      { pick: 3, state: 'all weft' },
    ]);
  });

  it('reports nothing for a sound cloth', () => {
    const cloth = drawdownFromDraft(PLAIN_DRAFT);
    expect(unwovenEnds(cloth)).toEqual([]);
    expect(unwovenPicks(cloth)).toEqual([]);
  });
});

describe('pairedEnds', () => {
  it('groups ends that lift together on every pick', () => {
    expect(pairedEnds(parseMatrix('X.X.\n.X.X'))).toEqual([
      [1, 3],
      [2, 4],
    ]);
  });

  it('reports nothing when every end differs', () => {
    expect(pairedEnds(parseMatrix('XX.\n.XX'))).toEqual([]);
  });
});

describe('reverseFace', () => {
  it('is the cloth seen from the back', () => {
    expect(formatMatrix(reverseFace(parseMatrix('XX..\n.XX.')))).toBe('..XX\nX..X');
  });

  it('turns a 3/1 warp face into a 1/3 weft face', () => {
    const cloth = drawdownFromDraft({
      threading: straightDraw(4),
      tieup: twillTieup(4, 3),
      treadling: straightTreadling(4, 4),
      shafts: 4,
      treadles: 4,
    });
    expect(warpFaceRatio(reverseFace(cloth))).toBe(0.25);
  });
});

describe('drawdownFromRows', () => {
  it('reads strings of X and dot', () => {
    expect(formatMatrix(drawdownFromRows(['X.', '.X']))).toBe('X.\n.X');
  });

  it('takes rows that are already bit arrays', () => {
    expect(formatMatrix(drawdownFromRows([[1, 0]]))).toBe('X.');
  });

  it('refuses ragged rows', () => {
    expect(() => drawdownFromRows(['X.', 'X'])).toThrow(InvalidArgument);
  });
});
