import { InvalidArgument } from '../util/errors.js';
import { number, oneOf, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * The let-off motion.
 *
 * The warp beam pays out as the cloth is taken up, and the tension in the warp
 * is what a weight or a spring holds against it. The awkward part is that the
 * beam gets thinner as it empties, so a weight that gives the right tension on
 * a full beam gives far too much on an empty one. A negative let-off lives with
 * that; a positive one drives the beam and does not.
 */

export const LET_OFF_TYPES = ['negative', 'positive'];

/** Warp tension from a weight on a lever, in newtons. */
export function tensionFromWeight({ weightKg, leverRatio = 1, beamDiameterCm, ropeTurns = 1 }) {
  positiveNumber(weightKg, 'weightKg');
  positiveNumber(leverRatio, 'leverRatio');
  positiveNumber(beamDiameterCm, 'beamDiameterCm');
  positiveInteger(ropeTurns, 'ropeTurns');
  const force = weightKg * 9.80665 * leverRatio * ropeTurns;
  // The braking force acts at the beam surface, so the tension it holds falls
  // as the beam empties and the radius shrinks.
  return roundTo(force * (10 / beamDiameterCm), 3);
}

/**
 * How much the tension moves between a full beam and an empty one, on a
 * negative let-off where nothing compensates for the shrinking radius.
 */
export function tensionDrift({ weightKg, leverRatio, fullDiameterCm, emptyDiameterCm }) {
  const full = tensionFromWeight({ weightKg, leverRatio, beamDiameterCm: fullDiameterCm });
  const empty = tensionFromWeight({ weightKg, leverRatio, beamDiameterCm: emptyDiameterCm });
  if (emptyDiameterCm >= fullDiameterCm) {
    throw new InvalidArgument('an empty beam is thinner than a full one', {
      fullDiameterCm,
      emptyDiameterCm,
    });
  }
  return {
    full,
    empty,
    ratio: roundTo(empty / full, 4),
    risePercent: roundTo((empty / full - 1) * 100, 2),
  };
}

/**
 * The weight a wanted tension needs at a given beam diameter, which is the
 * calculation a weaver actually does when they hang the lever.
 */
export function weightForTension({ tensionNewtons, leverRatio = 1, beamDiameterCm }) {
  positiveNumber(tensionNewtons, 'tensionNewtons');
  positiveNumber(leverRatio, 'leverRatio');
  positiveNumber(beamDiameterCm, 'beamDiameterCm');
  return roundTo(tensionNewtons / (9.80665 * leverRatio * (10 / beamDiameterCm)), 4);
}

/** Tension per end, which is what the yarn actually feels. */
export function tensionPerEnd({ tensionNewtons, ends }) {
  positiveNumber(tensionNewtons, 'tensionNewtons');
  positiveInteger(ends, 'ends');
  return roundTo(tensionNewtons / ends, 5);
}

/**
 * Whether the tension per end is inside what the yarn will take. The usual
 * working figure is a fraction of the single end breaking load, because a warp
 * held near its breaking load breaks somewhere every few minutes.
 */
export function withinSafeTension({ tensionNewtons, ends, breakingLoadN, share = 0.15 }) {
  positiveNumber(breakingLoadN, 'breakingLoadN');
  number(share, 'share', { min: 0.01, max: 1 });
  const perEnd = tensionPerEnd({ tensionNewtons, ends });
  const limit = roundTo(breakingLoadN * share, 5);
  return { perEnd, limit, safe: perEnd <= limit, headroom: roundTo(limit - perEnd, 5) };
}

/**
 * A positive let-off drives the beam at a rate, so it has to be reset as the
 * beam empties. This is how far the beam turns per pick at a given build-up.
 */
export function beamTurnsPerPick({ picksPerCm, beamDiameterCm }) {
  positiveNumber(picksPerCm, 'picksPerCm');
  positiveNumber(beamDiameterCm, 'beamDiameterCm');
  const circumference = Math.PI * beamDiameterCm;
  return roundTo(1 / (picksPerCm * circumference), 8);
}

/** How many picks one turn of the beam pays out. */
export function picksPerBeamTurn(options) {
  return roundTo(1 / beamTurnsPerPick(options), 2);
}

/** Which kind of let-off a shed is describing, checked. */
export function assertLetOffType(type) {
  return oneOf(type, 'type', LET_OFF_TYPES);
}
