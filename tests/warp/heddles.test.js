import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import { straightDraw } from '../../src/draft/threading.js';
import {
  heddlesNeeded,
  heddleShortfalls,
  assertHeddlesFit,
  spareHeddles,
  heddleMoves,
  drawingInHours,
  tyingInHours,
  canTieIn,
  changeoverHours,
} from '../../src/warp/heddles.js';

const THREADING = straightDraw(4, 400);

describe('heddlesNeeded', () => {
  it('counts the ends on each shaft', () => {
    expect(heddlesNeeded(THREADING, 4)).toEqual([100, 100, 100, 100]);
  });

  it('reads an uneven threading unevenly', () => {
    expect(heddlesNeeded([1, 1, 1, 2], 2)).toEqual([3, 1]);
  });
});

describe('heddleShortfalls', () => {
  it('is empty when every shaft has enough', () => {
    expect(heddleShortfalls(THREADING, { shafts: 4, available: [120, 120, 120, 120] })).toEqual([]);
  });

  it('names the shaft that is short and by how much', () => {
    const short = heddleShortfalls(THREADING, { shafts: 4, available: [80, 120, 120, 120] });
    expect(short).toEqual([{ shaft: 1, needed: 100, available: 80, short: 20 }]);
  });

  it('does not let spare heddles on another shaft cover it', () => {
    const short = heddleShortfalls(THREADING, { shafts: 4, available: [80, 400, 120, 120] });
    expect(short.length).toBe(1);
  });

  it('needs one available count per shaft', () => {
    expect(() => heddleShortfalls(THREADING, { shafts: 4, available: [120] })).toThrow(
      InvalidArgument,
    );
  });
});

describe('assertHeddlesFit', () => {
  it('passes a draft the loom carries', () => {
    const options = { shafts: 4, available: [120, 120, 120, 120] };
    expect(assertHeddlesFit(THREADING, options)).toBe(THREADING);
  });

  it('refuses one it does not, naming the worst shaft', () => {
    try {
      assertHeddlesFit(THREADING, { shafts: 4, available: [80, 60, 120, 120] });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail.worst.shaft).toBe(2);
    }
  });
});

describe('spareHeddles', () => {
  it('reports the spares per shaft rather than as a total', () => {
    const spare = spareHeddles(THREADING, { shafts: 4, available: [150, 100, 120, 100] });
    expect(spare).toEqual([
      { shaft: 1, spare: 50 },
      { shaft: 2, spare: 0 },
      { shaft: 3, spare: 20 },
      { shaft: 4, spare: 0 },
    ]);
  });

  it('goes negative on a shaft that is short', () => {
    expect(spareHeddles(THREADING, { shafts: 4, available: [80, 120, 120, 120] })[0].spare).toBe(
      -20,
    );
  });
});

describe('heddleMoves', () => {
  it('is empty when nothing needs moving', () => {
    expect(heddleMoves(THREADING, { shafts: 4, available: [120, 120, 120, 120] })).toEqual([]);
  });

  it('takes heddles off the shaft with the most spare', () => {
    const moves = heddleMoves(THREADING, { shafts: 4, available: [80, 200, 130, 100] });
    expect(moves).toEqual([{ from: 2, to: 1, heddles: 20 }]);
  });

  it('draws on more than one shaft when it has to', () => {
    const moves = heddleMoves(THREADING, { shafts: 4, available: [60, 125, 120, 100] });
    expect(moves).toEqual([
      { from: 2, to: 1, heddles: 25 },
      { from: 3, to: 1, heddles: 15 },
    ]);
  });

  it('refuses when the loom has not enough anywhere', () => {
    expect(() => heddleMoves(THREADING, { shafts: 4, available: [50, 100, 100, 100] })).toThrow(
      /not enough heddles anywhere/,
    );
  });
});

describe('drawingInHours and tyingInHours', () => {
  it('divides the ends by the rate', () => {
    expect(drawingInHours({ ends: 4000 })).toBe(10);
    expect(tyingInHours({ ends: 4000 })).toBe(2);
  });

  it('is faster to tie in than to draw in', () => {
    expect(tyingInHours({ ends: 4000 })).toBeLessThan(drawingInHours({ ends: 4000 }));
  });
});

describe('canTieIn and changeoverHours', () => {
  it('ties in when the threading has not changed', () => {
    expect(canTieIn(THREADING, [...THREADING])).toBe(true);
    expect(changeoverHours(THREADING, [...THREADING]).method).toBe('tie in');
  });

  it('draws in when it has', () => {
    const changed = [...THREADING];
    changed[0] = 2;
    expect(canTieIn(THREADING, changed)).toBe(false);
    expect(changeoverHours(THREADING, changed).method).toBe('draw in');
  });

  it('draws in when the warp is a different width', () => {
    expect(canTieIn(THREADING, straightDraw(4, 300))).toBe(false);
  });

  it('refuses a change with no warp at all', () => {
    expect(() => changeoverHours(THREADING, [])).toThrow(InvalidArgument);
  });
});
