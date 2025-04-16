import { InvalidArgument } from '../util/errors.js';
import { array, positiveInteger, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * The take-up motion.
 *
 * A ratchet on the take-up shaft is advanced one tooth every pick, and a train
 * of wheels carries that movement to the cloth roller. Change one wheel and the
 * cloth moves a different distance per pick, which is how a weaver sets the
 * picks per inch. In the shed the whole train is collapsed into a single number
 * called the dividend, and the setting is the dividend over the change wheel.
 *
 *   picks per inch = dividend / change wheel teeth
 *
 * so the arithmetic a weaver actually does is one division, and everything else
 * is worked out once when the loom is set up.
 */

/** Change wheels a shed keeps in the drawer, in teeth. */
export function stockChangeWheels() {
  return [24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48, 50, 52, 56, 60];
}

/**
 * The dividend for a seven-wheel take-up. The ratchet, the carrier and the
 * wheel on the roller shaft all raise it; the stud wheel and the roller
 * circumference lower it.
 */
export function takeUpDividend({
  ratchetTeeth,
  carrierTeeth,
  studTeeth,
  rollerWheelTeeth,
  rollerDiameterInches,
}) {
  positiveInteger(ratchetTeeth, 'ratchetTeeth');
  positiveInteger(carrierTeeth, 'carrierTeeth');
  positiveInteger(studTeeth, 'studTeeth');
  positiveInteger(rollerWheelTeeth, 'rollerWheelTeeth');
  positiveNumber(rollerDiameterInches, 'rollerDiameterInches');
  const circumference = Math.PI * rollerDiameterInches;
  return roundTo((ratchetTeeth * carrierTeeth * rollerWheelTeeth) / (studTeeth * circumference), 4);
}

/** The picks per inch a change wheel gives on a known dividend. */
export function picksPerInch(dividend, changeWheelTeeth) {
  positiveNumber(dividend, 'dividend');
  positiveInteger(changeWheelTeeth, 'changeWheelTeeth');
  return roundTo(dividend / changeWheelTeeth, 3);
}

/** How far the cloth moves on each pick, in inches. */
export function clothPerPick(dividend, changeWheelTeeth) {
  return roundTo(1 / picksPerInch(dividend, changeWheelTeeth), 6);
}

/** The change wheel a wanted sett needs, before rounding to a real wheel. */
export function exactChangeWheel(dividend, wantedPicksPerInch) {
  positiveNumber(dividend, 'dividend');
  positiveNumber(wantedPicksPerInch, 'wantedPicksPerInch');
  return roundTo(dividend / wantedPicksPerInch, 3);
}

/**
 * The best wheel in the drawer for a wanted sett, with what it actually gives
 * and how far off that is. The error matters: a wheel two teeth out shows on
 * the cloth as a pick density the customer measures.
 */
export function bestChangeWheel(dividend, wantedPicksPerInch, { wheels } = {}) {
  const drawer = wheels ?? stockChangeWheels();
  array(drawer, 'wheels', { min: 1 });
  const exact = exactChangeWheel(dividend, wantedPicksPerInch);
  let best = null;
  for (const teeth of drawer) {
    positiveInteger(teeth, 'wheel');
    const achieved = picksPerInch(dividend, teeth);
    const error = Math.abs(achieved - wantedPicksPerInch);
    if (best === null || error < best.error) {
      best = { teeth, achieved, error: roundTo(error, 3) };
    }
  }
  return {
    ...best,
    exact,
    errorPercent: roundTo((best.error / wantedPicksPerInch) * 100, 3),
  };
}

/** Every wheel in the drawer with the sett it gives, for a setting card. */
export function settingCard(dividend, { wheels } = {}) {
  const drawer = wheels ?? stockChangeWheels();
  return drawer.map((teeth) => ({ teeth, picksPerInch: picksPerInch(dividend, teeth) }));
}

/** The sett range a drawer of wheels can reach on a given dividend. */
export function settRange(dividend, { wheels } = {}) {
  const card = settingCard(dividend, { wheels });
  const values = card.map((entry) => entry.picksPerInch);
  return { lowest: Math.min(...values), highest: Math.max(...values) };
}

/** Refuse a sett the drawer cannot reach within a tolerance. */
export function assertSettReachable(dividend, wantedPicksPerInch, { tolerance = 1, wheels } = {}) {
  positiveNumber(tolerance, 'tolerance');
  const best = bestChangeWheel(dividend, wantedPicksPerInch, { wheels });
  if (best.error > tolerance) {
    throw new InvalidArgument('no change wheel reaches that sett closely enough', {
      wantedPicksPerInch,
      closest: best.achieved,
      teeth: best.teeth,
      tolerance,
    });
  }
  return best;
}
