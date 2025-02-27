import { CapacityError, NotFound } from '../util/errors.js';
import { nonEmptyString, oneOf, positiveInteger } from '../util/check.js';

/**
 * Jacquard machines.
 *
 * A machine is counted in hooks. Each hook pulls one harness cord, and one card
 * position selects one hook, so the hook count is the widest pattern repeat the
 * machine can weave before the harness has to repeat it across the cloth.
 *
 * The needle board is a grid, and the card is punched to match it, so a machine
 * carries a row and column layout as well as a total. Card positions are read
 * along the rows of the board, which is why a machine of the same hook count
 * can still need a differently punched card.
 */

export const LIFT_TYPES = ['single', 'double'];

const MACHINES = new Map(
  [
    { name: '100 hook', hooks: 100, rows: 4, columns: 25, lift: 'single', pitchMm: 8 },
    { name: '200 hook', hooks: 200, rows: 8, columns: 25, lift: 'single', pitchMm: 8 },
    { name: '400 hook', hooks: 400, rows: 8, columns: 50, lift: 'single', pitchMm: 8 },
    { name: '600 hook', hooks: 600, rows: 8, columns: 75, lift: 'double', pitchMm: 8 },
    { name: '896 hook', hooks: 896, rows: 8, columns: 112, lift: 'double', pitchMm: 4 },
    { name: '1344 hook', hooks: 1344, rows: 12, columns: 112, lift: 'double', pitchMm: 4 },
  ].map((machine) => [machine.name, Object.freeze(machine)]),
);

/** Every machine on the books, smallest first. */
export function listMachines() {
  return [...MACHINES.values()].sort((a, b) => a.hooks - b.hooks);
}

/** Look a machine up by name. */
export function machineByName(name) {
  nonEmptyString(name, 'name');
  const machine = MACHINES.get(name);
  if (machine === undefined) {
    throw new NotFound(`no machine called "${name}"`, {
      name,
      known: [...MACHINES.keys()],
    });
  }
  return machine;
}

/** The smallest machine that carries a repeat of this many ends. */
export function smallestMachineFor(hooksNeeded) {
  positiveInteger(hooksNeeded, 'hooksNeeded');
  const found = listMachines().find((machine) => machine.hooks >= hooksNeeded);
  if (found === undefined) {
    throw new CapacityError('no machine on the books carries that many hooks', {
      hooksNeeded,
      largest: listMachines().at(-1).hooks,
    });
  }
  return found;
}

/** Build a machine that is not on the books, checking the layout adds up. */
export function defineMachine({ name, rows, columns, lift = 'single', pitchMm = 8 }) {
  nonEmptyString(name, 'name');
  positiveInteger(rows, 'rows');
  positiveInteger(columns, 'columns');
  oneOf(lift, 'lift', LIFT_TYPES);
  return Object.freeze({ name, hooks: rows * columns, rows, columns, lift, pitchMm });
}

/** Where on the needle board a given hook sits, counting along the rows. */
export function hookPosition(machine, hook) {
  positiveInteger(hook, 'hook');
  if (hook > machine.hooks) {
    throw new CapacityError('that hook is past the end of the machine', {
      hook,
      hooks: machine.hooks,
    });
  }
  const index = hook - 1;
  return { row: Math.floor(index / machine.columns) + 1, column: (index % machine.columns) + 1 };
}

/** The hook at a row and column of the needle board. */
export function hookAt(machine, row, column) {
  positiveInteger(row, 'row');
  positiveInteger(column, 'column');
  if (row > machine.rows || column > machine.columns) {
    throw new CapacityError('that position is off the needle board', {
      row,
      column,
      rows: machine.rows,
      columns: machine.columns,
    });
  }
  return (row - 1) * machine.columns + column;
}

/** How many times a repeat covers the cloth width, and what is left over. */
export function repeatsAcross(machine, { totalEnds, hooksPerRepeat }) {
  positiveInteger(totalEnds, 'totalEnds');
  positiveInteger(hooksPerRepeat, 'hooksPerRepeat');
  if (hooksPerRepeat > machine.hooks) {
    throw new CapacityError('the repeat needs more hooks than the machine has', {
      hooksPerRepeat,
      hooks: machine.hooks,
    });
  }
  return {
    repeats: Math.floor(totalEnds / hooksPerRepeat),
    remainder: totalEnds % hooksPerRepeat,
    hooksIdle: machine.hooks - hooksPerRepeat,
  };
}

/** Refuse a repeat the machine cannot hold, with the shortfall named. */
export function assertFits(machine, hooksNeeded) {
  positiveInteger(hooksNeeded, 'hooksNeeded');
  if (hooksNeeded > machine.hooks) {
    throw new CapacityError(`${machine.name} has ${machine.hooks} hooks`, {
      machine: machine.name,
      hooks: machine.hooks,
      hooksNeeded,
      short: hooksNeeded - machine.hooks,
    });
  }
  return machine;
}

/** A double-lift machine reads two cards at once, so it weaves twice as fast. */
export function cardsPerCycle(machine) {
  return machine.lift === 'double' ? 2 : 1;
}
