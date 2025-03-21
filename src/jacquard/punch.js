import { DraftError } from '../util/errors.js';
import { array, positiveInteger } from '../util/check.js';
import { assertMatrix, createMatrix } from '../util/matrix.js';
import { setPositions } from '../util/indexing.js';
import { deckFromLiftplan } from './deck.js';

/**
 * Turning a design into a punching job.
 *
 * A design is drawn at the full width of the cloth, but the machine only has so
 * many hooks, so the harness tie decides which ends move together. Every end a
 * hook pulls has to agree on every pick, because one hook can only be up or
 * down. When they do not agree the design cannot be woven on that tie, and
 * saying so before the cards are cut is the whole point of this module.
 */

/**
 * Ends that share a hook but disagree about a pick. Each entry names the pick,
 * the hook, and the ends on both sides of the disagreement.
 */
export function tieConflicts(design, tie, hooks) {
  assertMatrix(design, 'design');
  array(tie, 'tie', { min: 1 });
  positiveInteger(hooks, 'hooks');
  if (tie.length !== design[0].length) {
    throw new DraftError('the tie does not cover the design width', {
      designEnds: design[0].length,
      tieEnds: tie.length,
    });
  }
  const conflicts = [];
  for (const [p, row] of design.entries()) {
    const decided = new Map();
    for (const [e, hook] of tie.entries()) {
      if (hook < 1 || hook > hooks) {
        throw new DraftError(`end ${e + 1} is tied to a hook the machine does not have`, {
          end: e + 1,
          hook,
          hooks,
        });
      }
      const cell = row[e];
      if (!decided.has(hook)) {
        decided.set(hook, { cell, end: e + 1 });
        continue;
      }
      const first = decided.get(hook);
      if (first.cell !== cell) {
        conflicts.push({ pick: p + 1, hook, ends: [first.end, e + 1], states: [first.cell, cell] });
      }
    }
  }
  return conflicts;
}

/** The liftplan a design and a tie produce, once the design is known to agree. */
export function hookPlanFromDesign(design, tie, hooks) {
  const conflicts = tieConflicts(design, tie, hooks);
  if (conflicts.length > 0) {
    throw new DraftError('the design cannot be woven on that tie', {
      conflicts: conflicts.length,
      first: conflicts[0],
    });
  }
  const plan = createMatrix(design.length, hooks);
  for (const [p, row] of design.entries()) {
    for (const [e, hook] of tie.entries()) {
      plan[p][hook - 1] = row[e];
    }
  }
  return plan;
}

/** The whole job: the hook plan, the cards, and what was checked on the way. */
export function punchJob(machine, design, tie) {
  const plan = hookPlanFromDesign(design, tie, machine.hooks);
  const deck = deckFromLiftplan(machine, plan);
  return {
    machine: machine.name,
    hooks: machine.hooks,
    picks: design.length,
    ends: design[0].length,
    hookPlan: plan,
    deck,
    hooksUsed: usedHooks(plan).length,
  };
}

/** Hooks the plan ever lifts, 1-based. */
export function usedHooks(hookPlan) {
  assertMatrix(hookPlan, 'hookPlan');
  const used = new Set();
  for (const row of hookPlan) {
    for (const hook of setPositions(row)) used.add(hook);
  }
  return [...used].sort((a, b) => a - b);
}

/** Weave the design back out of a hook plan and a tie, to check the cutting. */
export function designFromHookPlan(hookPlan, tie) {
  assertMatrix(hookPlan, 'hookPlan');
  array(tie, 'tie', { min: 1 });
  const design = createMatrix(hookPlan.length, tie.length);
  for (const [p, row] of hookPlan.entries()) {
    for (const [e, hook] of tie.entries()) {
      if (hook < 1 || hook > row.length) {
        throw new DraftError(`end ${e + 1} is tied to a hook the plan has no column for`, {
          end: e + 1,
          hook,
          hooks: row.length,
        });
      }
      design[p][e] = row[hook - 1];
    }
  }
  return design;
}

/**
 * Cut a design down to one repeat of the tie, which is the part that actually
 * has to be punched. The rest of the width is the harness repeating it.
 */
export function designRepeat(design, tie) {
  assertMatrix(design, 'design');
  array(tie, 'tie', { min: 1 });
  const width = repeatWidthOfTie(tie);
  return design.map((row) => row.slice(0, width));
}

/** How wide one pass of the tie is before it starts over. */
export function repeatWidthOfTie(tie) {
  array(tie, 'tie', { min: 1 });
  for (let width = 1; width <= tie.length; width += 1) {
    if (tie.length % width !== 0) continue;
    let matches = true;
    for (let i = width; i < tie.length && matches; i += 1) {
      if (tie[i] !== tie[i % width]) matches = false;
    }
    if (matches) return width;
  }
  return tie.length;
}
