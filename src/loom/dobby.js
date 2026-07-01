import { CapacityError, DraftError } from '../util/errors.js';
import { array, integer, oneOf, positiveInteger } from '../util/check.js';
import { assertMatrix, createMatrix, formatMatrix } from '../util/matrix.js';
import { setPositions, duplicateGroups } from '../util/indexing.js';
import { period, canonicalRotation } from '../util/cycle.js';
import { assertFits } from '../util/limits.js';
import { sum, sumBy } from '../util/numeric.js';

/**
 * The dobby chain.
 *
 * A dobby does the same job as a Jacquard on a much smaller scale: instead of a
 * card per pick it has a wooden lag with a peg hole for every shaft, and the
 * lags are hinged into a chain that runs over a barrel. A peg in the hole lifts
 * the shaft on a positive dobby and holds it down on a negative one, so the
 * same chain weaves the reverse cloth on the other kind of machine.
 */

export const DOBBY_ACTIONS = ['positive', 'negative'];

/** A single lag: one peg position per shaft. */
export function lagFromShafts(shafts, lifted) {
  positiveInteger(shafts, 'shafts');
  array(lifted, 'lifted');
  const lag = new Array(shafts).fill(0);
  for (const [i, shaft] of lifted.entries()) {
    integer(shaft, `lifted[${i}]`, { min: 1 });
    if (shaft > shafts) {
      throw new CapacityError('the dobby has no peg hole for that shaft', { shaft, shafts });
    }
    lag[shaft - 1] = 1;
  }
  return lag;
}

/** The shafts a lag lifts, read back. */
export function shaftsOnLag(lag, { action = 'positive' } = {}) {
  array(lag, 'lag', { min: 1 });
  oneOf(action, 'action', DOBBY_ACTIONS);
  const pegged = setPositions(lag);
  if (action === 'positive') return pegged;
  return lag.map((_, index) => index + 1).filter((shaft) => !pegged.includes(shaft));
}

/** A whole chain from a liftplan, one lag per pick. */
export function chainFromLiftplan(liftplan, { shafts } = {}) {
  assertMatrix(liftplan, 'liftplan');
  const width = shafts ?? liftplan[0].length;
  positiveInteger(width, 'shafts');
  if (liftplan[0].length > width) {
    throw new CapacityError('the liftplan needs more shafts than the dobby has', {
      shafts: width,
      needed: liftplan[0].length,
    });
  }
  const chain = createMatrix(liftplan.length, width);
  for (const [p, row] of liftplan.entries()) {
    for (const [s, cell] of row.entries()) chain[p][s] = cell;
  }
  return chain;
}

/** The liftplan a chain weaves, which is the reverse on a negative dobby. */
export function liftplanFromChain(chain, { action = 'positive' } = {}) {
  assertMatrix(chain, 'chain');
  oneOf(action, 'action', DOBBY_ACTIONS);
  if (action === 'positive') return chain.map((lag) => [...lag]);
  return chain.map((lag) => lag.map((peg) => (peg === 1 ? 0 : 1)));
}

/** How many pegs the chain needs, which is what the peg box has to hold. */
export function pegCount(chain) {
  assertMatrix(chain, 'chain');
  return sumBy(chain, (lag) => sum(lag), 'chain');
}

/** How many lags the chain runs to. */
export function chainLength(chain) {
  assertMatrix(chain, 'chain');
  return chain.length;
}

/** The shortest chain that weaves the same cloth. */
export function chainRepeat(chain) {
  assertMatrix(chain, 'chain');
  return period(chain.map((lag) => lag.join('')));
}

/** Two chains weave the same cloth when they match round the barrel. */
export function sameChain(a, b) {
  const key = (chain) => canonicalRotation(chain.map((lag) => lag.join(''))).join('|');
  return key(a) === key(b);
}

/** Lags pegged identically, which a shed could have cut once and reused. */
export function repeatedLags(chain) {
  assertMatrix(chain, 'chain');
  return duplicateGroups(chain, (lag) => lag.join(''));
}

/** Refuse a chain the barrel cannot carry. */
export function assertChainFitsBarrel(chain, { lagCapacity }) {
  assertMatrix(chain, 'chain');
  positiveInteger(lagCapacity, 'lagCapacity');
  assertFits(chain.length, lagCapacity, {
    what: 'chain',
    has: 'the chain is longer than the barrel holds',
    detail: { lags: chain.length, lagCapacity },
  });
  return chain;
}

/**
 * A lag with every hole pegged, or none, opens no shed. On a negative dobby the
 * two cases swap over, which is exactly the mistake this catches.
 */
export function shedlessLags(chain, { action = 'positive' } = {}) {
  const plan = liftplanFromChain(chain, { action });
  const out = [];
  for (const [p, lift] of plan.entries()) {
    const raised = sum(lift);
    if (raised === 0) out.push({ lag: p + 1, reason: 'nothing lifted' });
    else if (raised === lift.length) out.push({ lag: p + 1, reason: 'everything lifted' });
  }
  return out;
}

/** The chain written out as a peg plan for the shed floor. */
export function renderChain(chain) {
  assertMatrix(chain, 'chain');
  return formatMatrix(chain, { on: 'o', off: '.' });
}

/** Refuse a chain that would leave the loom standing with no shed at all. */
export function assertChainWeaves(chain, options = {}) {
  const blanks = shedlessLags(chain, options);
  if (blanks.length === chain.length) {
    throw new DraftError('no lag in the chain opens a shed', { lags: chain.length });
  }
  return chain;
}
