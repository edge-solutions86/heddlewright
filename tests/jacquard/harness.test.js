import { describe, it, expect } from 'vitest';
import { CapacityError, DraftError, InvalidArgument } from '../../src/util/errors.js';
import {
  TIE_TYPES,
  straightTie,
  pointTie,
  centreTie,
  borderedTie,
  tieByType,
  hookLoad,
  idleHooks,
  endsOnHook,
  repeatWidth,
  comberBoardPosition,
  comberBoardSize,
} from '../../src/jacquard/harness.js';

describe('straightTie', () => {
  it('runs through and starts again', () => {
    expect(straightTie(4, 10)).toEqual([1, 2, 3, 4, 1, 2, 3, 4, 1, 2]);
  });

  it('gives every hook the same load when the ends divide', () => {
    expect(hookLoad(straightTie(4, 16), 4)).toEqual([4, 4, 4, 4]);
  });
});

describe('pointTie', () => {
  it('mirrors the figure without repeating the turn', () => {
    expect(pointTie(4, 12)).toEqual([1, 2, 3, 4, 3, 2, 1, 2, 3, 4, 3, 2]);
  });

  it('covers twice the width less two ends per repeat', () => {
    expect(repeatWidth('point', 4)).toBe(6);
    expect(repeatWidth('straight', 4)).toBe(4);
  });

  it('needs at least two hooks', () => {
    expect(() => pointTie(1, 4)).toThrow(DraftError);
  });

  it('loads the turning hooks half as often as the rest', () => {
    const load = hookLoad(pointTie(4, 12), 4);
    expect(load[0]).toBe(2);
    expect(load[3]).toBe(2);
    expect(load[1]).toBe(4);
  });
});

describe('centreTie', () => {
  it('runs out from the middle and mirrors back', () => {
    expect(centreTie(4, 8)).toEqual([4, 3, 2, 1, 1, 2, 3, 4]);
  });

  it('is symmetric about the centre of the cloth', () => {
    const tie = centreTie(5, 20);
    for (let i = 0; i < 10; i += 1) {
      expect(tie[i]).toBe(tie[19 - i]);
    }
  });
});

describe('borderedTie', () => {
  it('gives the border its own hooks and mirrors it on the far side', () => {
    const tie = borderedTie({ bodyHooks: 4, borderHooks: 2, bodyEnds: 4, borderEnds: 2 });
    expect(tie).toEqual([1, 2, 3, 4, 5, 6, 2, 1]);
  });

  it('never lets a body end reach a border hook', () => {
    const tie = borderedTie({ bodyHooks: 3, borderHooks: 2, bodyEnds: 9, borderEnds: 4 });
    const body = tie.slice(4, 13);
    for (const hook of body) expect(hook).toBeGreaterThan(2);
  });
});

describe('tieByType', () => {
  it('names its four ties and routes to each', () => {
    expect(TIE_TYPES).toEqual(['straight', 'point', 'centre', 'bordered']);
    expect(tieByType('straight', { hooks: 4, totalEnds: 8 })).toEqual(straightTie(4, 8));
    expect(tieByType('point', { hooks: 4, totalEnds: 8 })).toEqual(pointTie(4, 8));
    expect(tieByType('centre', { hooks: 4, totalEnds: 8 })).toEqual(centreTie(4, 8));
  });

  it('refuses a tie it does not know', () => {
    expect(() => tieByType('spiral', { hooks: 4, totalEnds: 8 })).toThrow(InvalidArgument);
  });
});

describe('hookLoad', () => {
  it('counts the cords hanging on each hook', () => {
    expect(hookLoad([1, 1, 2], 3)).toEqual([2, 1, 0]);
  });

  it('refuses a tie naming a hook the machine has not got, and says which end', () => {
    try {
      hookLoad([1, 9], 4);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail).toMatchObject({ end: 2, hook: 9, hooks: 4 });
    }
  });
});

describe('idleHooks and endsOnHook', () => {
  it('finds hooks nothing is tied to', () => {
    expect(idleHooks([1, 1, 3], 4)).toEqual([2, 4]);
    expect(idleHooks(straightTie(4, 8), 4)).toEqual([]);
  });

  it('lists the ends a hook pulls', () => {
    expect(endsOnHook(straightTie(4, 10), 2)).toEqual([2, 6, 10]);
  });
});

describe('comberBoardPosition', () => {
  it('fills the rows before moving along the board', () => {
    const board = { rowsDeep: 4, holesPerRow: 3 };
    expect(comberBoardPosition(1, board)).toEqual({ row: 1, hole: 1 });
    expect(comberBoardPosition(4, board)).toEqual({ row: 4, hole: 1 });
    expect(comberBoardPosition(5, board)).toEqual({ row: 1, hole: 2 });
    expect(comberBoardPosition(12, board)).toEqual({ row: 4, hole: 3 });
  });

  it('gives every end a different hole', () => {
    const board = { rowsDeep: 4, holesPerRow: 3 };
    const seen = new Set();
    for (let end = 1; end <= 12; end += 1) {
      const { row, hole } = comberBoardPosition(end, board);
      seen.add(`${row}:${hole}`);
    }
    expect(seen.size).toBe(12);
  });

  it('refuses an end the board has no hole for', () => {
    expect(() => comberBoardPosition(13, { rowsDeep: 4, holesPerRow: 3 })).toThrow(CapacityError);
  });
});

describe('comberBoardSize', () => {
  it('works the holes per row out from the warp width', () => {
    expect(comberBoardSize(1600)).toEqual({ rowsDeep: 8, holesPerRow: 200, holes: 1600 });
  });

  it('rounds a row up rather than losing an end', () => {
    expect(comberBoardSize(1601).holesPerRow).toBe(201);
  });

  it('takes a shallower board', () => {
    expect(comberBoardSize(1600, { rowsDeep: 4 }).holesPerRow).toBe(400);
  });
});
