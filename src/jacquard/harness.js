import { CapacityError, DraftError } from '../util/errors.js';
import { array, oneOf, positiveInteger } from '../util/check.js';
import { mod } from '../util/numeric.js';
import { positionsOf } from '../util/indexing.js';

/**
 * The harness tie.
 *
 * A Jacquard machine has far fewer hooks than the cloth has ends, so the
 * harness cords are tied so that one hook pulls several ends across the width.
 * Which ends a hook pulls is the tie, and the tie decides how the pattern
 * repeats: straight through gives the same figure over and over, a point tie
 * mirrors it, and a centre tie puts one figure in the middle of the cloth.
 */

export const TIE_TYPES = ['straight', 'point', 'centre', 'bordered'];

/** Straight through: hook 1 to end 1, and round again every `hooks` ends. */
export function straightTie(hooks, totalEnds) {
  positiveInteger(hooks, 'hooks');
  positiveInteger(totalEnds, 'totalEnds');
  return Array.from({ length: totalEnds }, (_, e) => mod(e, hooks) + 1);
}

/**
 * A point tie mirrors the figure, so one repeat covers 2h - 2 ends and the
 * cloth reads as a reflected pattern rather than a marching one.
 */
export function pointTie(hooks, totalEnds) {
  positiveInteger(hooks, 'hooks');
  positiveInteger(totalEnds, 'totalEnds');
  if (hooks < 2) {
    throw new DraftError('a point tie needs at least two hooks', { hooks });
  }
  const cycle = 2 * hooks - 2;
  return Array.from({ length: totalEnds }, (_, e) => {
    const at = mod(e, cycle);
    return at < hooks ? at + 1 : cycle - at + 1;
  });
}

/**
 * A centre tie runs one figure out from the middle of the cloth and mirrors it
 * back, which is how a shawl or a panel is centred on the loom.
 */
export function centreTie(hooks, totalEnds) {
  positiveInteger(hooks, 'hooks');
  positiveInteger(totalEnds, 'totalEnds');
  const middle = totalEnds / 2;
  return Array.from({ length: totalEnds }, (_, e) => {
    const fromCentre = e < middle ? Math.floor(middle) - 1 - e : e - Math.ceil(middle);
    return mod(fromCentre, hooks) + 1;
  });
}

/**
 * A bordered tie reserves a block of hooks for the selvedge figure and gives
 * the rest to the body, which is how a bordered shawl is woven in one piece.
 */
export function borderedTie({ bodyHooks, borderHooks, bodyEnds, borderEnds }) {
  positiveInteger(bodyHooks, 'bodyHooks');
  positiveInteger(borderHooks, 'borderHooks');
  positiveInteger(bodyEnds, 'bodyEnds');
  positiveInteger(borderEnds, 'borderEnds');
  const left = Array.from({ length: borderEnds }, (_, e) => mod(e, borderHooks) + 1);
  const body = Array.from({ length: bodyEnds }, (_, e) => borderHooks + mod(e, bodyHooks) + 1);
  const right = [...left].reverse();
  return [...left, ...body, ...right];
}

/** Build a tie by name, so a style sheet can name one rather than list it. */
export function tieByType(type, options) {
  oneOf(type, 'type', TIE_TYPES);
  const { hooks, totalEnds } = options;
  switch (type) {
    case 'straight':
      return straightTie(hooks, totalEnds);
    case 'point':
      return pointTie(hooks, totalEnds);
    case 'centre':
      return centreTie(hooks, totalEnds);
    default:
      return borderedTie(options);
  }
}

/** How many ends each hook pulls, 1-based, which is the load on its cord. */
export function hookLoad(tie, hooks) {
  array(tie, 'tie', { min: 1 });
  positiveInteger(hooks, 'hooks');
  const load = new Array(hooks).fill(0);
  for (const [e, hook] of tie.entries()) {
    if (hook < 1 || hook > hooks) {
      throw new CapacityError(`end ${e + 1} is tied to a hook the machine does not have`, {
        end: e + 1,
        hook,
        hooks,
      });
    }
    load[hook - 1] += 1;
  }
  return load;
}

/** Hooks pulling nothing, whose cords could be taken out of the comber board. */
export function idleHooks(tie, hooks) {
  return positionsOf(hookLoad(tie, hooks), 0);
}

/** The 1-based ends a given hook pulls. */
export function endsOnHook(tie, hook) {
  positiveInteger(hook, 'hook');
  return positionsOf(tie, hook);
}

/** How wide one pattern repeat is, in ends, for a tie of this kind. */
export function repeatWidth(type, hooks) {
  oneOf(type, 'type', TIE_TYPES);
  positiveInteger(hooks, 'hooks');
  if (type === 'point') return 2 * hooks - 2;
  return hooks;
}

/**
 * The comber board spaces the cords above the loom. Each cord passes through
 * one hole, and the board is a grid, so this says which row and hole a cord
 * runs through.
 */
export function comberBoardPosition(end, { rowsDeep, holesPerRow }) {
  positiveInteger(end, 'end');
  positiveInteger(rowsDeep, 'rowsDeep');
  positiveInteger(holesPerRow, 'holesPerRow');
  const capacity = rowsDeep * holesPerRow;
  if (end > capacity) {
    throw new CapacityError('the comber board has no hole for that end', { end, capacity });
  }
  const index = end - 1;
  return { row: (index % rowsDeep) + 1, hole: Math.floor(index / rowsDeep) + 1 };
}

/** How many holes a comber board needs for a warp of this width. */
export function comberBoardSize(totalEnds, { rowsDeep = 8 } = {}) {
  positiveInteger(totalEnds, 'totalEnds');
  positiveInteger(rowsDeep, 'rowsDeep');
  return { rowsDeep, holesPerRow: Math.ceil(totalEnds / rowsDeep), holes: totalEnds };
}
