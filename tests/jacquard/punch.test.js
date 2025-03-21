import { describe, it, expect } from 'vitest';
import { DraftError } from '../../src/util/errors.js';
import { formatMatrix, parseMatrix, matrixEquals } from '../../src/util/matrix.js';
import { defineMachine } from '../../src/jacquard/machine.js';
import { straightTie, pointTie } from '../../src/jacquard/harness.js';
import { deckHooks } from '../../src/jacquard/deck.js';
import {
  tieConflicts,
  hookPlanFromDesign,
  punchJob,
  usedHooks,
  designFromHookPlan,
  designRepeat,
  repeatWidthOfTie,
} from '../../src/jacquard/punch.js';

const MACHINE = defineMachine({ name: 'bench', rows: 2, columns: 4 });
const TIE = straightTie(4, 8);
const DESIGN = parseMatrix('XX..XX..\n.XX..XX.');

describe('tieConflicts', () => {
  it('is quiet when every end on a hook agrees', () => {
    expect(tieConflicts(DESIGN, TIE, 8)).toEqual([]);
  });

  it('names the pick, the hook and both ends when they disagree', () => {
    const design = parseMatrix('X..X.X..');
    const conflicts = tieConflicts(design, TIE, 8);
    expect(conflicts.length).toBeGreaterThan(0);
    expect(conflicts[0]).toMatchObject({ pick: 1, hook: 1, ends: [1, 5] });
  });

  it('reports one conflict per disagreeing pair, not one per end', () => {
    const design = parseMatrix('X...X...');
    expect(tieConflicts(design, straightTie(4, 8), 8).length).toBe(0);
  });

  it('refuses a tie that does not cover the design', () => {
    expect(() => tieConflicts(DESIGN, straightTie(4, 6), 8)).toThrow(/does not cover/);
  });

  it('refuses a tie naming a hook the machine has not got', () => {
    expect(() => tieConflicts(DESIGN, straightTie(4, 8), 3)).toThrow(DraftError);
  });
});

describe('hookPlanFromDesign', () => {
  it('collapses the design onto the hooks', () => {
    const plan = hookPlanFromDesign(DESIGN, TIE, 8);
    expect(formatMatrix(plan)).toBe('XX......\n.XX.....');
  });

  it('leaves the hooks past the repeat unlifted', () => {
    const plan = hookPlanFromDesign(DESIGN, TIE, 8);
    expect(plan[0].slice(4)).toEqual([0, 0, 0, 0]);
  });

  it('refuses a design the tie cannot weave, and says how many clashes', () => {
    try {
      hookPlanFromDesign(parseMatrix('X..X.X..'), TIE, 8);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.conflicts).toBeGreaterThan(0);
      expect(err.detail.first.pick).toBe(1);
    }
  });
});

describe('designFromHookPlan', () => {
  it('weaves the design back out through the same tie', () => {
    const plan = hookPlanFromDesign(DESIGN, TIE, 8);
    expect(matrixEquals(designFromHookPlan(plan, TIE), DESIGN)).toBe(true);
  });

  it('round-trips a point tie, which mirrors the figure across the cloth', () => {
    const tie = pointTie(4, 12);
    const plan = parseMatrix('XX..\n.XX.\n..XX');
    const design = designFromHookPlan(plan, tie);
    expect(formatMatrix(design)).toBe('XX...XXX...X\n.XX.XX.XX.XX\n..XXX...XXX.');
    expect(tieConflicts(design, tie, 4)).toEqual([]);
    expect(matrixEquals(hookPlanFromDesign(design, tie, 4), plan)).toBe(true);
  });

  it('refuses a tie reaching past the plan', () => {
    const plan = parseMatrix('X.\n.X');
    expect(() => designFromHookPlan(plan, [1, 3])).toThrow(DraftError);
  });
});

describe('punchJob', () => {
  it('reports the shape of the job and cuts the cards', () => {
    const job = punchJob(MACHINE, DESIGN, TIE);
    expect(job).toMatchObject({ machine: 'bench', hooks: 8, picks: 2, ends: 8, hooksUsed: 3 });
    expect(job.deck.length).toBe(2);
  });

  it('cuts cards that lift exactly the hooks the plan names', () => {
    const job = punchJob(MACHINE, DESIGN, TIE);
    expect(deckHooks(job.deck, MACHINE)).toEqual([
      [1, 2],
      [2, 3],
    ]);
  });
});

describe('usedHooks', () => {
  it('lists the hooks a plan ever lifts, sorted', () => {
    expect(usedHooks(parseMatrix('X..X\n.X..'))).toEqual([1, 2, 4]);
  });

  it('is empty for a plan that lifts nothing', () => {
    expect(usedHooks(parseMatrix('..\n..'))).toEqual([]);
  });
});

describe('repeatWidthOfTie', () => {
  it('finds the pass width of a straight tie', () => {
    expect(repeatWidthOfTie(straightTie(4, 12))).toBe(4);
    expect(repeatWidthOfTie(straightTie(6, 12))).toBe(6);
  });

  it('finds the wider pass of a point tie', () => {
    expect(repeatWidthOfTie(pointTie(4, 12))).toBe(6);
  });

  it('is the whole tie when nothing repeats', () => {
    expect(repeatWidthOfTie([1, 2, 3, 2, 1])).toBe(5);
  });
});

describe('designRepeat', () => {
  it('cuts the design to one pass of the tie', () => {
    expect(formatMatrix(designRepeat(DESIGN, TIE))).toBe('XX..\n.XX.');
  });

  it('leaves a design already one repeat wide alone', () => {
    const narrow = parseMatrix('XX..\n.XX.');
    expect(formatMatrix(designRepeat(narrow, straightTie(4, 4)))).toBe(formatMatrix(narrow));
  });
});
