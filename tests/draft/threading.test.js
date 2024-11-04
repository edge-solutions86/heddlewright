import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import {
  DRAW_STYLES,
  assertThreading,
  straightDraw,
  pointDraw,
  brokenDraw,
  undulatingDraw,
  dividedDraw,
  drawByStyle,
  shaftLoad,
  idleShafts,
  endsOnShaft,
  shaftsUsed,
} from '../../src/draft/threading.js';

describe('assertThreading', () => {
  it('accepts shafts inside the count', () => {
    expect(assertThreading([1, 2, 3, 4], 4)).toEqual([1, 2, 3, 4]);
  });

  it('rejects a shaft above the loom and one below one', () => {
    expect(() => assertThreading([1, 5], 4)).toThrow(/threading\[1\]/);
    expect(() => assertThreading([0, 1], 4)).toThrow(InvalidArgument);
  });

  it('rejects an empty threading', () => {
    expect(() => assertThreading([], 4)).toThrow(InvalidArgument);
  });
});

describe('straightDraw', () => {
  it('counts up the shafts and starts again', () => {
    expect(straightDraw(4)).toEqual([1, 2, 3, 4]);
    expect(straightDraw(4, 10)).toEqual([1, 2, 3, 4, 1, 2, 3, 4, 1, 2]);
  });

  it('starts on a different shaft when asked', () => {
    expect(straightDraw(4, 4, { start: 3 })).toEqual([3, 4, 1, 2]);
  });

  it('runs the other way on a reversed direction', () => {
    expect(straightDraw(4, 4, { direction: -1 })).toEqual([1, 4, 3, 2]);
  });

  it('is a single shaft on a one shaft loom', () => {
    expect(straightDraw(1, 3)).toEqual([1, 1, 1]);
  });

  it('refuses a start off the loom', () => {
    expect(() => straightDraw(4, 4, { start: 5 })).toThrow(InvalidArgument);
    expect(() => straightDraw(4, 4, { direction: 0 })).toThrow(InvalidArgument);
  });
});

describe('pointDraw', () => {
  it('runs up and back without repeating the turn', () => {
    expect(pointDraw(4)).toEqual([1, 2, 3, 4, 3, 2]);
    expect(pointDraw(5)).toEqual([1, 2, 3, 4, 5, 4, 3, 2]);
  });

  it('repeats its cycle over a longer threading', () => {
    expect(pointDraw(4, 12)).toEqual([1, 2, 3, 4, 3, 2, 1, 2, 3, 4, 3, 2]);
  });

  it('cuts short when fewer ends are asked for', () => {
    expect(pointDraw(4, 3)).toEqual([1, 2, 3]);
  });

  it('needs two shafts to have a point at all', () => {
    expect(() => pointDraw(1)).toThrow(DraftError);
  });
});

describe('brokenDraw', () => {
  it('runs a block then steps the start across', () => {
    expect(brokenDraw(4, 8, { blockSize: 4, stagger: 2 })).toEqual([1, 2, 3, 4, 3, 4, 1, 2]);
  });

  it('a stagger of the block size is just a straight draw', () => {
    expect(brokenDraw(4, 8, { blockSize: 4, stagger: 4 })).toEqual(straightDraw(4, 8));
  });

  it('takes a block shorter than the shaft count', () => {
    expect(brokenDraw(6, 6, { blockSize: 2, stagger: 3 })).toEqual([1, 2, 4, 5, 1, 2]);
  });

  it('stays inside the loom whatever the stagger', () => {
    for (const shaft of brokenDraw(8, 40, { blockSize: 3, stagger: 5 })) {
      expect(shaft).toBeGreaterThanOrEqual(1);
      expect(shaft).toBeLessThanOrEqual(8);
    }
  });
});

describe('undulatingDraw', () => {
  it('holds each shaft for the next run length in the list', () => {
    expect(undulatingDraw(4, 9, [1, 2])).toEqual([1, 2, 2, 3, 4, 4, 1, 2, 2]);
  });

  it('a run list of all ones is a straight draw', () => {
    expect(undulatingDraw(4, 8, [1])).toEqual(straightDraw(4, 8));
  });

  it('stops exactly on the end count even mid-run', () => {
    expect(undulatingDraw(3, 4, [3])).toEqual([1, 1, 1, 2]);
  });

  it('refuses a run length of zero, which would never advance', () => {
    expect(() => undulatingDraw(4, 4, [0])).toThrow(InvalidArgument);
  });
});

describe('dividedDraw', () => {
  it('alternates the two groups, advancing each on its own turn', () => {
    expect(dividedDraw([1, 2], [3, 4, 5], 10)).toEqual([1, 3, 2, 4, 1, 5, 2, 3, 1, 4]);
  });

  it('works with a single ground shaft', () => {
    expect(dividedDraw([1], [3, 4], 6)).toEqual([1, 3, 1, 4, 1, 3]);
  });

  it('needs both groups', () => {
    expect(() => dividedDraw([], [1], 4)).toThrow(InvalidArgument);
  });
});

describe('drawByStyle', () => {
  it('names every style it can build', () => {
    expect(DRAW_STYLES).toEqual(['straight', 'point', 'broken', 'undulating', 'divided']);
  });

  it('routes to the matching builder with the same defaults', () => {
    expect(drawByStyle('straight', { shafts: 4 })).toEqual(straightDraw(4));
    expect(drawByStyle('point', { shafts: 4 })).toEqual(pointDraw(4));
    expect(drawByStyle('broken', { shafts: 4, ends: 8 })).toEqual(brokenDraw(4, 8));
    expect(drawByStyle('undulating', { shafts: 4, ends: 6, runLengths: [2] })).toEqual(
      undulatingDraw(4, 6, [2]),
    );
    expect(
      drawByStyle('divided', { shafts: 4, ends: 6, groundShafts: [1, 2], patternShafts: [3, 4] }),
    ).toEqual(dividedDraw([1, 2], [3, 4], 6));
  });

  it('rejects a style it does not know', () => {
    expect(() => drawByStyle('spiral', { shafts: 4 })).toThrow(/straight, point/);
  });
});

describe('shaft usage', () => {
  const threading = [1, 2, 1, 2, 4, 4];

  it('counts the ends carried by each shaft', () => {
    expect(shaftLoad(threading, 4)).toEqual([2, 2, 0, 2]);
  });

  it('finds the shafts with nothing on them', () => {
    expect(idleShafts(threading, 4)).toEqual([3]);
    expect(idleShafts([1, 2, 3, 4], 4)).toEqual([]);
    expect(idleShafts([1, 1], 3)).toEqual([2, 3]);
  });

  it('lists the ends on a shaft as 1-based positions', () => {
    expect(endsOnShaft(threading, 1)).toEqual([1, 3]);
    expect(endsOnShaft(threading, 4)).toEqual([5, 6]);
    expect(endsOnShaft(threading, 3)).toEqual([]);
  });

  it('counts the distinct shafts a threading actually needs', () => {
    expect(shaftsUsed(threading)).toBe(3);
    expect(shaftsUsed([1, 1, 1])).toBe(1);
  });

  it('a straight draw loads every shaft evenly when the ends divide', () => {
    expect(shaftLoad(straightDraw(4, 16), 4)).toEqual([4, 4, 4, 4]);
  });
});
