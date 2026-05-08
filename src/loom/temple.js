import { CapacityError, InvalidArgument } from '../util/errors.js';
import { number, oneOf, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * Temples and pulling in.
 *
 * Cloth comes off the reed narrower than the warp went into it, because the
 * weft draws the outside ends inwards as it bends round them. A temple holds
 * the cloth out at the fell so the selvedge ends are not cut on the reed, and
 * how much it has to hold depends on how much the cloth would otherwise pull
 * in, which is a function of the weft and the weave rather than of the loom.
 */

export const TEMPLE_KINDS = ['ring', 'roller', 'full width'];

/** How much narrower the cloth is than the reed, as a share. */
export function pullIn({ reedWidthCm, clothWidthCm }) {
  positiveNumber(reedWidthCm, 'reedWidthCm');
  positiveNumber(clothWidthCm, 'clothWidthCm');
  if (clothWidthCm > reedWidthCm) {
    throw new InvalidArgument('the cloth cannot be wider than the reed it came through', {
      reedWidthCm,
      clothWidthCm,
    });
  }
  return roundTo((reedWidthCm - clothWidthCm) / reedWidthCm, 4);
}

/** The cloth width a reed width and a pull-in give. */
export function clothWidth({ reedWidthCm, pullInShare }) {
  positiveNumber(reedWidthCm, 'reedWidthCm');
  number(pullInShare, 'pullInShare', { min: 0, max: 0.5 });
  return roundTo(reedWidthCm * (1 - pullInShare), 2);
}

/** The reed width a wanted cloth width needs, allowing for the pull-in. */
export function reedWidth({ clothWidthCm, pullInShare }) {
  positiveNumber(clothWidthCm, 'clothWidthCm');
  number(pullInShare, 'pullInShare', { min: 0, max: 0.5 });
  return roundTo(clothWidthCm / (1 - pullInShare), 2);
}

/**
 * A working estimate of the pull-in, from the weft crimp and how firmly the
 * cloth is set. A cloth that bends its weft further round the warp pulls in
 * further, and a closer sett resists it.
 */
export function estimatePullIn({ weftCrimpPercent, coverFactor }) {
  number(weftCrimpPercent, 'weftCrimpPercent', { min: 0 });
  positiveNumber(coverFactor, 'coverFactor');
  const raw = weftCrimpPercent / 100 / (1 + coverFactor / 28);
  return roundTo(Math.min(0.25, raw), 4);
}

/** How far a temple has to hold the cloth out, in centimetres. */
export function templeGrip({ reedWidthCm, clothWidthCm }) {
  // Called for the checks it makes: it refuses cloth wider than the reed, which
  // would otherwise come back here as a negative grip.
  pullIn({ reedWidthCm, clothWidthCm });
  return roundTo((reedWidthCm - clothWidthCm) / 2, 2);
}

/** Whether a shed's temple will reach across a given pull-in. */
export function templeFits({ reedWidthCm, clothWidthCm, maxGripCm }) {
  positiveNumber(maxGripCm, 'maxGripCm');
  const grip = templeGrip({ reedWidthCm, clothWidthCm });
  return { grip, maxGripCm, fits: grip <= maxGripCm };
}

/** Refuse a setting the temple cannot hold. */
export function assertTempleFits(options) {
  const reading = templeFits(options);
  if (!reading.fits) {
    throw new CapacityError('the temple will not hold the cloth out that far', reading);
  }
  return reading;
}

/**
 * A pull-in past about a tenth is the point where selvedge ends start breaking
 * on the reed, whatever temple is fitted.
 */
export function pullInWarning(share, { limit = 0.1 } = {}) {
  number(share, 'share', { min: 0 });
  if (share <= limit) return null;
  return {
    code: 'excessive_pull_in',
    share,
    limit,
    message: 'the selvedge ends will be cut on the reed at that pull-in',
  };
}

/** How many selvedge ends a temple covers at a given sett. */
export function endsUnderTemple({ gripCm, endsPerCm }) {
  positiveNumber(gripCm, 'gripCm');
  positiveNumber(endsPerCm, 'endsPerCm');
  return Math.ceil(gripCm * endsPerCm);
}

/** Check a temple kind. */
export function assertTempleKind(kind) {
  return oneOf(kind, 'kind', TEMPLE_KINDS);
}

/** How many temple rings a width of cloth needs, at a ring spacing. */
export function ringsNeeded({ clothWidthCm, ringWidthCm = 5 }) {
  positiveNumber(clothWidthCm, 'clothWidthCm');
  positiveNumber(ringWidthCm, 'ringWidthCm');
  return positiveInteger(Math.max(2, Math.ceil(clothWidthCm / ringWidthCm)), 'rings');
}
