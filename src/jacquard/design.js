import { CapacityError, DraftError } from '../util/errors.js';
import { integer, oneOf, positiveInteger } from '../util/check.js';
import { assertMatrix, createMatrix, dimensions } from '../util/matrix.js';
import { roundTo } from '../util/numeric.js';

/**
 * Sizing a figured design for a machine.
 *
 * A designer draws a figure at whatever size looks right and then somebody has
 * to fit it to the hooks and the picks. The figure is scaled, placed and
 * repeated, and the awkward part is that scaling a binary design is not
 * resampling a photograph: every cell has to end up as a whole cell, so the
 * scale factors are whole numbers or the figure comes out ragged.
 */

export const PLACEMENTS = ['left', 'centre', 'right'];

/** Enlarge a design by whole factors, which is the only honest way to scale one. */
export function scaleDesign(design, { across = 1, down = across } = {}) {
  assertMatrix(design, 'design');
  positiveInteger(across, 'across');
  positiveInteger(down, 'down');
  const out = [];
  for (const row of design) {
    const wide = [];
    for (const cell of row) {
      for (let a = 0; a < across; a += 1) wide.push(cell);
    }
    for (let d = 0; d < down; d += 1) out.push([...wide]);
  }
  return out;
}

/** The largest whole scale a design can take inside a hook count. */
export function largestScale(design, { hooks }) {
  assertMatrix(design, 'design');
  positiveInteger(hooks, 'hooks');
  const width = design[0].length;
  const scale = Math.floor(hooks / width);
  if (scale < 1) {
    throw new CapacityError('the design is wider than the machine even unscaled', {
      designEnds: width,
      hooks,
    });
  }
  return scale;
}

/** Place a design across a hook count, padding either side. */
export function placeDesign(design, { hooks, placement = 'centre' }) {
  assertMatrix(design, 'design');
  positiveInteger(hooks, 'hooks');
  oneOf(placement, 'placement', PLACEMENTS);
  const { rows, cols } = dimensions(design);
  if (cols > hooks) {
    throw new CapacityError('the design is wider than the machine', { designEnds: cols, hooks });
  }
  const spare = hooks - cols;
  const left = placement === 'left' ? 0 : placement === 'right' ? spare : Math.floor(spare / 2);
  const out = createMatrix(rows, hooks);
  for (let p = 0; p < rows; p += 1) {
    for (let e = 0; e < cols; e += 1) out[p][left + e] = design[p][e];
  }
  return { plan: out, left, right: spare - left };
}

/** Repeat a design down the cloth to reach a pick count. */
export function repeatDown(design, picks) {
  assertMatrix(design, 'design');
  positiveInteger(picks, 'picks');
  return Array.from({ length: picks }, (_, p) => [...design[p % design.length]]);
}

/**
 * The whole fitting: scale the figure as large as the hooks allow, place it,
 * and report what was left over. A design that fits exactly is rare, and the
 * spare hooks are what a border is woven on.
 */
export function fitDesign(design, { hooks, picks, placement = 'centre', scale }) {
  const wanted = scale ?? largestScale(design, { hooks });
  positiveInteger(wanted, 'scale');
  const scaled = scaleDesign(design, { across: wanted, down: wanted });
  const placed = placeDesign(scaled, { hooks, placement });
  const plan = picks === undefined ? placed.plan : repeatDown(placed.plan, picks);
  return {
    scale: wanted,
    designEnds: scaled[0].length,
    designPicks: scaled.length,
    spareHooks: placed.left + placed.right,
    left: placed.left,
    right: placed.right,
    plan,
  };
}

/** How much of the machine a fitted design actually uses. */
export function hookUtilisation(fitted, { hooks }) {
  positiveInteger(hooks, 'hooks');
  return roundTo(fitted.designEnds / hooks, 4);
}

/**
 * How many times the figure repeats down a length of cloth, and whether the
 * length holds a whole number of them.
 */
export function repeatsInLength(fitted, { picks }) {
  integer(picks, 'picks', { min: 1 });
  return {
    repeats: Math.floor(picks / fitted.designPicks),
    remainder: picks % fitted.designPicks,
    whole: picks % fitted.designPicks === 0,
  };
}

/** Refuse a fitting that leaves the figure off centre when it was meant to be centred. */
export function assertCentred(fitted) {
  if (Math.abs(fitted.left - fitted.right) > 1) {
    throw new DraftError('the figure is not centred on the machine', {
      left: fitted.left,
      right: fitted.right,
    });
  }
  return fitted;
}
