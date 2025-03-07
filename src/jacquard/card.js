import { CapacityError, InvalidArgument } from '../util/errors.js';
import { array, integer, positiveInteger } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { createMatrix, assertMatrix, countOnes, formatMatrix } from '../util/matrix.js';
import { hookPosition, hookAt } from './machine.js';

/**
 * The punched card.
 *
 * One card is one pick. Its holes line up with the needle board, and a needle
 * that finds a hole passes through and leaves its hook standing in the griffe,
 * so the hook lifts. A needle that meets solid card is pushed back and its hook
 * is left behind. That is the convention this package uses: a hole lifts.
 *
 * The card carries more than the pattern. It has peg holes that the cylinder's
 * pegs drive, and lacing holes along both ends where the cards are strung
 * together into a chain.
 */

export const PEG_HOLES = 2;
export const LACING_HOLES = 2;

/** An unpunched card sized for a machine's needle board. */
export function blankCard(machine, { number = 1 } = {}) {
  positiveInteger(number, 'number');
  return {
    number,
    rows: machine.rows,
    columns: machine.columns,
    machine: machine.name,
    holes: createMatrix(machine.rows, machine.columns),
  };
}

/** Punch a card so the named hooks lift. */
export function punchCard(machine, hooks, { number = 1 } = {}) {
  array(hooks, 'hooks');
  const card = blankCard(machine, { number });
  for (const [i, hook] of hooks.entries()) {
    integer(hook, `hooks[${i}]`, { min: 1 });
    if (hook > machine.hooks) {
      throw new CapacityError('that hook is past the end of the machine', {
        hook,
        hooks: machine.hooks,
      });
    }
    const { row, column } = hookPosition(machine, hook);
    card.holes[row - 1][column - 1] = 1;
  }
  return card;
}

/** The hooks a card lifts, in machine order. */
export function cardHooks(card, machine, { holeMeansLift = true } = {}) {
  assertCardFitsMachine(card, machine);
  const out = [];
  for (let row = 1; row <= machine.rows; row += 1) {
    for (let column = 1; column <= machine.columns; column += 1) {
      const punched = card.holes[row - 1][column - 1] === 1;
      if (punched === holeMeansLift) out.push(hookAt(machine, row, column));
    }
  }
  return out;
}

/** How many holes are punched, which is how much of the card is cut away. */
export function holeCount(card) {
  assertMatrix(card.holes, 'card.holes');
  return countOnes(card.holes);
}

/** The share of the card that is cut away, which is what weakens it. */
export function cutAwayRatio(card) {
  const total = card.rows * card.columns;
  return roundTo(holeCount(card) / total, 4);
}

/** Refuse a card whose grid does not match the machine it is meant for. */
export function assertCardFitsMachine(card, machine) {
  assertMatrix(card.holes, 'card.holes');
  if (card.rows !== machine.rows || card.columns !== machine.columns) {
    throw new InvalidArgument('the card does not fit that machine', {
      card: { rows: card.rows, columns: card.columns },
      machine: { rows: machine.rows, columns: machine.columns },
    });
  }
  if (card.holes.length !== machine.rows || card.holes[0].length !== machine.columns) {
    throw new InvalidArgument('the card grid does not match its own stated size', {
      stated: { rows: card.rows, columns: card.columns },
      grid: { rows: card.holes.length, columns: card.holes[0].length },
    });
  }
  return card;
}

/**
 * The physical size of a card in millimetres, worked out from the needle pitch
 * plus the margins that carry the peg and lacing holes.
 */
export function cardSizeMm(machine, { marginMm = 12 } = {}) {
  return {
    width: roundTo(machine.columns * machine.pitchMm + marginMm * 2, 2),
    height: roundTo(machine.rows * machine.pitchMm + marginMm * 2, 2),
    pitchMm: machine.pitchMm,
  };
}

/** Two cards lift the same hooks when their holes match. */
export function sameCard(a, b) {
  assertMatrix(a.holes, 'a.holes');
  assertMatrix(b.holes, 'b.holes');
  return formatMatrix(a.holes) === formatMatrix(b.holes);
}

/** A card with nothing punched leaves every hook down, so no shed opens. */
export function isBlank(card) {
  return holeCount(card) === 0;
}

/** A card punched right through lifts everything, which also opens no shed. */
export function isFullyPunched(card) {
  return holeCount(card) === card.rows * card.columns;
}

/** The card printed for a paper record, holes as O and solid card as a dot. */
export function renderCard(card) {
  assertMatrix(card.holes, 'card.holes');
  return formatMatrix(card.holes, { on: 'O', off: '.' });
}
