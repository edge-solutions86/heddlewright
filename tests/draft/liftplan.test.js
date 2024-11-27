import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, parseMatrix, matrixEquals } from '../../src/util/matrix.js';
import { twillTieup, directTieup } from '../../src/draft/tieup.js';
import { straightTreadling } from '../../src/draft/treadling.js';
import {
  liftplanFromTreadling,
  treadlingFromLiftplan,
  liftplanFromShaftLists,
  shaftListsFromLiftplan,
  liftLoad,
  shedlessPicks,
  distinctLifts,
  invertLiftplan,
} from '../../src/draft/liftplan.js';

const TWILL = twillTieup(4, 2);

describe('liftplanFromTreadling', () => {
  it('lifts the shafts the pressed treadle is tied to', () => {
    const plan = liftplanFromTreadling(TWILL, straightTreadling(4), { shafts: 4, treadles: 4 });
    expect(formatMatrix(plan)).toBe('XX..\n.XX.\n..XX\nX..X');
  });

  it('unions the shafts when a pick presses two treadles', () => {
    const plan = liftplanFromTreadling(directTieup(4), [[1, 3]], { shafts: 4, treadles: 4 });
    expect(formatMatrix(plan)).toBe('X.X.');
  });

  it('leaves a blank row for a pick that presses nothing', () => {
    const plan = liftplanFromTreadling(directTieup(4), [[1], []], { shafts: 4, treadles: 4 });
    expect(formatMatrix(plan)).toBe('X...\n....');
  });

  it('refuses a tie-up that does not cover the treadles', () => {
    expect(() => liftplanFromTreadling([[1], [2]], [[1]], { shafts: 4, treadles: 4 })).toThrow(
      CapacityError,
    );
  });

  it('refuses a treadle the loom does not have', () => {
    expect(() => liftplanFromTreadling(directTieup(4), [[5]], { shafts: 4, treadles: 4 })).toThrow(
      InvalidArgument,
    );
  });
});

describe('treadlingFromLiftplan', () => {
  it('gives each distinct lift its own treadle in first-seen order', () => {
    const plan = parseMatrix('XX..\n..XX\nXX..');
    const result = treadlingFromLiftplan(plan);
    expect(result.tieup).toEqual([
      [1, 2],
      [3, 4],
    ]);
    expect(result.treadling).toEqual([[1], [2], [1]]);
    expect(result.treadles).toBe(2);
  });

  it('round-trips a treadle draft back to the same liftplan', () => {
    const plan = liftplanFromTreadling(TWILL, straightTreadling(4, 8), { shafts: 4, treadles: 4 });
    const { tieup, treadling, treadles } = treadlingFromLiftplan(plan);
    const rebuilt = liftplanFromTreadling(tieup, treadling, { shafts: 4, treadles });
    expect(matrixEquals(rebuilt, plan)).toBe(true);
  });

  it('refuses to squeeze more lifts than the loom has treadles', () => {
    const plan = parseMatrix('X...\n.X..\n..X.\n...X');
    expect(() => treadlingFromLiftplan(plan, { treadles: 3 })).toThrow(CapacityError);
    expect(treadlingFromLiftplan(plan, { treadles: 4 }).treadles).toBe(4);
  });

  it('keeps a blank pick as its own treadle tied to nothing', () => {
    const result = treadlingFromLiftplan(parseMatrix('X...\n....'));
    expect(result.tieup).toEqual([[1], []]);
  });
});

describe('liftplanFromShaftLists', () => {
  it('sets a cell per lifted shaft', () => {
    expect(formatMatrix(liftplanFromShaftLists([[1, 3], [2]], 4))).toBe('X.X.\n.X..');
  });

  it('takes a bare number for a single shaft', () => {
    expect(formatMatrix(liftplanFromShaftLists([1, 2], 2))).toBe('X.\n.X');
  });

  it('round-trips with shaftListsFromLiftplan', () => {
    const lists = [[1, 3], [2], []];
    expect(shaftListsFromLiftplan(liftplanFromShaftLists(lists, 4))).toEqual(lists);
  });

  it('rejects a shaft off the loom', () => {
    expect(() => liftplanFromShaftLists([[5]], 4)).toThrow(/shaftLists\[0\]\[0\]/);
  });
});

describe('liftLoad', () => {
  it('counts the shafts up on each pick', () => {
    expect(liftLoad(parseMatrix('XX..\nX...\n....'))).toEqual([2, 1, 0]);
  });
});

describe('shedlessPicks', () => {
  it('finds the picks that lift nothing and the picks that lift everything', () => {
    expect(shedlessPicks(parseMatrix('....\nXXXX\nXX..'))).toEqual([
      { pick: 1, reason: 'nothing lifted' },
      { pick: 2, reason: 'everything lifted' },
    ]);
  });

  it('is empty for a plan that opens a shed on every pick', () => {
    expect(shedlessPicks(parseMatrix('X.\n.X'))).toEqual([]);
  });
});

describe('distinctLifts', () => {
  it('counts the different rows', () => {
    expect(distinctLifts(parseMatrix('XX..\n..XX\nXX..'))).toBe(2);
    expect(distinctLifts(parseMatrix('X.\n.X'))).toBe(2);
  });

  it('matches the treadle count the splitter reports', () => {
    const plan = parseMatrix('XX..\n..XX\nXX..\n.X.X');
    expect(distinctLifts(plan)).toBe(treadlingFromLiftplan(plan).treadles);
  });
});

describe('invertLiftplan', () => {
  it('swaps every cell', () => {
    expect(formatMatrix(invertLiftplan(parseMatrix('XX..\n.X.X')))).toBe('..XX\nX.X.');
  });

  it('is its own inverse', () => {
    const plan = parseMatrix('XX..\n.X.X');
    expect(matrixEquals(invertLiftplan(invertLiftplan(plan)), plan)).toBe(true);
  });
});
