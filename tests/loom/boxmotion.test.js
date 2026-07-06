import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  BOXING,
  weftRuns,
  coloursIn,
  allotBoxes,
  boxChain,
  boxChanges,
  longestLift,
  oddRuns,
  shuttlesReturn,
  assertShuttlesReturn,
  picksLostToChanging,
  speedWithChanging,
  pickShare,
  boxMotionPlan,
  patternFromChain,
} from '../../src/loom/boxmotion.js';

/** A two and two check, the pattern every box loom is set up on first. */
const CHECK = ['red', 'red', 'blue', 'blue'];

/** Four picks of ground to two of each colour, the way a shot stripe runs. */
const STRIPE = [
  'ground',
  'ground',
  'ground',
  'ground',
  'blue',
  'blue',
  'ground',
  'ground',
  'ground',
  'ground',
  'gold',
  'gold',
];

describe('weftRuns', () => {
  it('reads the pattern into runs of one colour, numbered from one', () => {
    expect(weftRuns(CHECK)).toEqual([
      { colour: 'red', start: 1, picks: 2 },
      { colour: 'blue', start: 3, picks: 2 },
    ]);
  });

  it('does not join the last run to the first', () => {
    // The chain is cut and laced as written, so a weaver reads it straight
    // through even though the cloth repeats.
    expect(weftRuns(['a', 'b', 'a']).length).toBe(3);
  });

  it('refuses an empty pattern and one with something that is not a colour', () => {
    expect(() => weftRuns([])).toThrow(InvalidArgument);
    expect(() => weftRuns(['red', 2])).toThrow(/pattern\[1\]/);
    expect(() => weftRuns(['red', ''])).toThrow(InvalidArgument);
  });
});

describe('coloursIn', () => {
  it('lists each weft once, in the order it is first picked', () => {
    expect(coloursIn(STRIPE)).toEqual(['ground', 'blue', 'gold']);
  });
});

describe('allotBoxes', () => {
  it('puts the commonest weft in box one, which is the box at rest', () => {
    expect(allotBoxes(STRIPE, { boxes: 4 })).toEqual([
      { colour: 'ground', box: 1, picks: 8 },
      { colour: 'blue', box: 2, picks: 2 },
      { colour: 'gold', box: 3, picks: 2 },
    ]);
  });

  it('breaks a tie on which colour is picked first, so it is stable', () => {
    const twice = allotBoxes(CHECK, { boxes: 2 });
    expect(twice.map((entry) => entry.colour)).toEqual(['red', 'blue']);
    expect(allotBoxes(CHECK, { boxes: 2 })).toEqual(twice);
  });

  it('refuses more wefts than the loom has boxes', () => {
    expect(() => allotBoxes(STRIPE, { boxes: 2 })).toThrow(CapacityError);
    expect(() => allotBoxes(STRIPE, { boxes: 2 })).toThrow(/3 wefts will not go in 2 boxes/);
  });

  it('needs a box count', () => {
    expect(() => allotBoxes(CHECK, {})).toThrow(InvalidArgument);
  });
});

describe('boxChain', () => {
  const allotment = allotBoxes(STRIPE, { boxes: 4 });

  it('gives one box number per pick', () => {
    expect(boxChain(STRIPE, allotment)).toEqual([1, 1, 1, 1, 2, 2, 1, 1, 1, 1, 3, 3]);
  });

  it('names the pick that wants a colour with no box', () => {
    expect(() => boxChain(['ground', 'silver'], allotment)).toThrow(/pick 2 wants silver/);
  });

  it('refuses an empty allotment', () => {
    expect(() => boxChain(STRIPE, [])).toThrow(InvalidArgument);
  });
});

describe('boxChanges', () => {
  it('counts the lifts over one repeat, joining the end back to the start', () => {
    // 1 1 1 1 2 2 1 1 1 1 3 3 changes at picks 5, 7, 11 and again on the repeat.
    expect(boxChanges(boxChain(STRIPE, allotBoxes(STRIPE, { boxes: 4 })))).toBe(4);
  });

  it('counts one fewer when the chain is read straight through', () => {
    expect(boxChanges([1, 1, 2, 2], { cyclic: false })).toBe(1);
    expect(boxChanges([1, 1, 2, 2])).toBe(2);
  });

  it('is nothing at all for one weft', () => {
    expect(boxChanges([1, 1, 1, 1])).toBe(0);
    expect(boxChanges([2])).toBe(0);
  });
});

describe('longestLift', () => {
  it('is how far the box travels in the worst change', () => {
    expect(longestLift([1, 1, 4, 4])).toBe(3);
  });

  it('counts the change back round the repeat too', () => {
    expect(longestLift([1, 2, 2, 4])).toBe(3);
    expect(longestLift([1, 2, 2, 4], { cyclic: false })).toBe(2);
  });
});

describe('the shuttle has to come back', () => {
  it('lets anything through on a loom boxed at both ends', () => {
    expect(shuttlesReturn(['a', 'b', 'a'], { boxedAt: 'both' })).toBe(true);
    expect(BOXING).toEqual(['one', 'both']);
  });

  it('wants pairs on a loom boxed at one end', () => {
    expect(shuttlesReturn(CHECK, { boxedAt: 'one' })).toBe(true);
    expect(shuttlesReturn(['a', 'b', 'b'], { boxedAt: 'one' })).toBe(false);
  });

  it('names every odd run rather than only the first', () => {
    expect(oddRuns(['a', 'b', 'b', 'c'])).toEqual([
      { colour: 'a', start: 1, picks: 1 },
      { colour: 'c', start: 4, picks: 1 },
    ]);
  });

  it('refuses the pattern and says which runs are at fault', () => {
    expect(() => assertShuttlesReturn(['a', 'b', 'b'], { boxedAt: 'one' })).toThrow(CapacityError);
    try {
      assertShuttlesReturn(['a', 'b', 'b'], { boxedAt: 'one' });
    } catch (error) {
      expect(error.detail.runs).toEqual([{ colour: 'a', start: 1, picks: 1 }]);
    }
  });

  it('hands the pattern back when it is weavable', () => {
    expect(assertShuttlesReturn(CHECK, { boxedAt: 'one' })).toBe(CHECK);
  });

  it('refuses a boxing nobody builds', () => {
    expect(() => shuttlesReturn(CHECK, { boxedAt: 'three' })).toThrow(InvalidArgument);
  });
});

describe('what changing costs', () => {
  const chain = boxChain(STRIPE, allotBoxes(STRIPE, { boxes: 4 }));

  it('is a fraction of a pick for each lift', () => {
    expect(picksLostToChanging(chain)).toBe(0.4);
    expect(picksLostToChanging(chain, { picksPerChange: 0.25 })).toBe(1);
  });

  it('costs nothing when the motion is set right and nothing when it never lifts', () => {
    expect(picksLostToChanging(chain, { picksPerChange: 0 })).toBe(0);
    expect(picksLostToChanging([1, 1, 1])).toBe(0);
  });

  it('refuses a negative cost', () => {
    expect(() => picksLostToChanging(chain, { picksPerChange: -1 })).toThrow(InvalidArgument);
    expect(() => picksLostToChanging(chain, { picksPerChange: 'some' })).toThrow(InvalidArgument);
  });

  it('slows the loom in proportion to how much of the repeat is lost', () => {
    // Twelve picks plus four tenths of a pick of lifting, at 180 quoted.
    expect(speedWithChanging(chain, { picksMinute: 180 })).toBeCloseTo((180 * 12) / 12.4, 1);
  });

  it('leaves a one weft chain at the quoted speed', () => {
    expect(speedWithChanging([1, 1, 1, 1], { picksMinute: 200 })).toBe(200);
  });

  it('needs a speed to work from', () => {
    expect(() => speedWithChanging(chain, {})).toThrow(InvalidArgument);
  });
});

describe('pickShare', () => {
  it('says how much of the repeat each box carries', () => {
    expect(pickShare(allotBoxes(STRIPE, { boxes: 4 }))).toEqual([
      { box: 'box 1', count: 1, picks: 8, share: 0.6667 },
      { box: 'box 2', count: 1, picks: 2, share: 0.1667 },
      { box: 'box 3', count: 1, picks: 2, share: 0.1667 },
    ]);
  });

  it('adds up to the whole repeat', () => {
    const share = pickShare(allotBoxes(STRIPE, { boxes: 4 }));
    expect(share.reduce((total, row) => total + row.share, 0)).toBeCloseTo(1, 3);
  });
});

describe('boxMotionPlan', () => {
  it('gaits a pattern up on a loom boxed at one end', () => {
    const plan = boxMotionPlan({ pattern: STRIPE, boxes: 4, boxedAt: 'one' });
    expect(plan.picks).toBe(12);
    expect(plan.colours).toBe(3);
    expect(plan.chain).toEqual([1, 1, 1, 1, 2, 2, 1, 1, 1, 1, 3, 3]);
    expect(plan.changes).toBe(4);
    expect(plan.longestLift).toBe(2);
  });

  it('leaves the speed out until it is given one', () => {
    const plan = boxMotionPlan({ pattern: STRIPE, boxes: 4 });
    expect(plan.picksMinute).toBeUndefined();
    expect(plan.picksLost).toBeUndefined();
    expect(boxMotionPlan({ pattern: STRIPE, boxes: 4, picksMinute: 180 }).picksMinute).toBe(174.2);
  });

  it('refuses a pattern the boxing cannot pick', () => {
    const single = ['red', 'blue', 'blue'];
    expect(() => boxMotionPlan({ pattern: single, boxes: 2, boxedAt: 'one' })).toThrow(
      CapacityError,
    );
    expect(() => boxMotionPlan({ pattern: single, boxes: 2, boxedAt: 'both' })).not.toThrow();
  });

  it('refuses an option nobody declared, rather than ignoring it', () => {
    expect(() => boxMotionPlan({ pattern: CHECK, boxes: 2, speed: 180 })).toThrow(/does not take/);
  });
});

describe('patternFromChain', () => {
  const allotment = allotBoxes(STRIPE, { boxes: 4 });

  it('reads a laced chain back into the order it picks', () => {
    expect(patternFromChain(boxChain(STRIPE, allotment), allotment)).toEqual(STRIPE);
  });

  it('names a box the loom has nothing in', () => {
    expect(() => patternFromChain([1, 4], allotment)).toThrow(/names box 4, which is empty/);
  });

  it('refuses a chain entry that is not a box number', () => {
    expect(() => patternFromChain([0], allotment)).toThrow(InvalidArgument);
    expect(() => patternFromChain([], allotment)).toThrow(InvalidArgument);
  });
});
