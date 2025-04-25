import { number, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * Loom speed and what it turns into.
 *
 * A loom is quoted in picks a minute, but nobody buys picks. What matters is
 * cloth an hour, and that depends on the pick density as much as on the speed:
 * the same loom running the same speed makes half as much cloth at twice the
 * picks per inch. Weft insertion rate is the figure that lets two looms of
 * different widths be compared honestly.
 */

/** Picks a minute from the crank speed. One turn is one pick. */
export function picksPerMinute(crankRpm, { picksPerTurn = 1 } = {}) {
  positiveNumber(crankRpm, 'crankRpm');
  positiveNumber(picksPerTurn, 'picksPerTurn');
  return roundTo(crankRpm * picksPerTurn, 2);
}

/**
 * Weft insertion rate in metres a minute, which is what lets a narrow fast loom
 * be compared with a wide slow one.
 */
export function weftInsertionRate(picksMinute, reedWidthMetres) {
  positiveNumber(picksMinute, 'picksMinute');
  positiveNumber(reedWidthMetres, 'reedWidthMetres');
  return roundTo(picksMinute * reedWidthMetres, 2);
}

/** Cloth woven in an hour, in metres, at full running. */
export function clothPerHourMetres({ picksMinute, picksPerCm, efficiency = 1 }) {
  positiveNumber(picksMinute, 'picksMinute');
  positiveNumber(picksPerCm, 'picksPerCm');
  number(efficiency, 'efficiency', { min: 0, max: 1 });
  const cmPerHour = ((picksMinute * 60) / picksPerCm) * efficiency;
  return roundTo(cmPerHour / 100, 4);
}

/** The picks a run of cloth needs. */
export function picksForCloth({ metres, picksPerCm }) {
  positiveNumber(metres, 'metres');
  positiveNumber(picksPerCm, 'picksPerCm');
  return Math.round(metres * 100 * picksPerCm);
}

/** How long a run takes, in hours, at a given speed and efficiency. */
export function runHours({ metres, picksPerCm, picksMinute, efficiency = 1 }) {
  const picks = picksForCloth({ metres, picksPerCm });
  number(efficiency, 'efficiency', { min: 0.0001, max: 1 });
  return roundTo(picks / (picksMinute * 60 * efficiency), 3);
}

/** Production over a shift, in metres. */
export function shiftProduction({ hours = 8, picksMinute, picksPerCm, efficiency = 1 }) {
  positiveNumber(hours, 'hours');
  return roundTo(clothPerHourMetres({ picksMinute, picksPerCm, efficiency }) * hours, 3);
}

/**
 * How long a weft package lasts before the shuttle has to be changed, in
 * minutes, from the length on the package and the width being woven.
 */
export function weftPackageMinutes({ packageMetres, reedWidthMetres, picksMinute, crimp = 0 }) {
  positiveNumber(packageMetres, 'packageMetres');
  positiveNumber(reedWidthMetres, 'reedWidthMetres');
  positiveNumber(picksMinute, 'picksMinute');
  number(crimp, 'crimp', { min: 0 });
  const perPick = reedWidthMetres * (1 + crimp / 100);
  const picks = packageMetres / perPick;
  return roundTo(picks / picksMinute, 3);
}

/** How many looms one weaver can mind, given the time each one needs an hour. */
export function loomsPerWeaver({ attentionMinutesPerLoomHour, allowance = 0.85 }) {
  positiveNumber(attentionMinutesPerLoomHour, 'attentionMinutesPerLoomHour');
  number(allowance, 'allowance', { min: 0.01, max: 1 });
  return Math.floor((60 * allowance) / attentionMinutesPerLoomHour);
}

/** Two looms compared on insertion rate rather than on picks a minute. */
export function compareLooms(a, b) {
  const rateA = weftInsertionRate(a.picksMinute, a.reedWidthMetres);
  const rateB = weftInsertionRate(b.picksMinute, b.reedWidthMetres);
  return {
    left: rateA,
    right: rateB,
    faster: rateA >= rateB ? 'left' : 'right',
    ratio: roundTo(Math.max(rateA, rateB) / Math.min(rateA, rateB), 3),
  };
}
