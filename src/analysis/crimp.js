import { InvalidArgument } from '../util/errors.js';
import { number, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * Crimp and take-up.
 *
 * A thread does not lie straight in cloth: it rides over and under its
 * neighbours, so a yard of cloth eats more than a yard of yarn. Crimp measures
 * that against the cloth, take-up measures it against the yarn, and the two are
 * different numbers for the same fact, which is where warp calculations most
 * often go wrong.
 */

/** Crimp as a percentage of the cloth length. */
export function crimpPercent(yarnLength, clothLength) {
  positiveNumber(yarnLength, 'yarnLength');
  positiveNumber(clothLength, 'clothLength');
  if (yarnLength < clothLength) {
    throw new InvalidArgument('the yarn cannot be shorter than the cloth it wove', {
      yarnLength,
      clothLength,
    });
  }
  return roundTo(((yarnLength - clothLength) / clothLength) * 100, 4);
}

/** Take-up as a percentage of the yarn length. */
export function takeUpPercent(yarnLength, clothLength) {
  positiveNumber(yarnLength, 'yarnLength');
  positiveNumber(clothLength, 'clothLength');
  if (yarnLength < clothLength) {
    throw new InvalidArgument('the yarn cannot be shorter than the cloth it wove', {
      yarnLength,
      clothLength,
    });
  }
  return roundTo(((yarnLength - clothLength) / yarnLength) * 100, 4);
}

/** Take-up from crimp, which is the conversion people get backwards. */
export function takeUpFromCrimp(crimp) {
  number(crimp, 'crimp', { min: 0 });
  return roundTo((crimp / (100 + crimp)) * 100, 4);
}

/** Crimp from take-up, the other way round. */
export function crimpFromTakeUp(takeUp) {
  number(takeUp, 'takeUp', { min: 0, max: 99.999 });
  return roundTo((takeUp / (100 - takeUp)) * 100, 4);
}

/** The yarn a length of cloth needs at a given crimp. */
export function yarnForCloth(clothLength, crimp) {
  positiveNumber(clothLength, 'clothLength');
  number(crimp, 'crimp', { min: 0 });
  return roundTo(clothLength * (1 + crimp / 100), 4);
}

/** The cloth a length of yarn makes at a given crimp. */
export function clothFromYarn(yarnLength, crimp) {
  positiveNumber(yarnLength, 'yarnLength');
  number(crimp, 'crimp', { min: 0 });
  return roundTo(yarnLength / (1 + crimp / 100), 4);
}

/**
 * A full warp length: the cloth, plus crimp, plus the loom waste that never
 * becomes cloth at all, plus a shrinkage allowance for wet finishing.
 */
export function warpLength({ clothLength, crimp = 0, loomWaste = 0, shrinkage = 0 }) {
  positiveNumber(clothLength, 'clothLength');
  number(crimp, 'crimp', { min: 0 });
  number(loomWaste, 'loomWaste', { min: 0 });
  number(shrinkage, 'shrinkage', { min: 0, max: 99 });
  const finished = clothLength / (1 - shrinkage / 100);
  const onLoom = finished * (1 + crimp / 100);
  return {
    finished: roundTo(finished, 4),
    woven: roundTo(onLoom, 4),
    total: roundTo(onLoom + loomWaste, 4),
    loomWaste: roundTo(loomWaste, 4),
  };
}

/**
 * Crimp splits between the two directions: the more one system is set, the more
 * the other has to bend round it. This is the usual working split, given the
 * total crimp a cloth shows and the ratio of the two setts.
 */
export function splitCrimp(totalCrimp, { endsPerInch, picksPerInch }) {
  number(totalCrimp, 'totalCrimp', { min: 0 });
  positiveNumber(endsPerInch, 'endsPerInch');
  positiveNumber(picksPerInch, 'picksPerInch');
  const total = endsPerInch + picksPerInch;
  return {
    warp: roundTo((totalCrimp * picksPerInch) / total, 4),
    weft: roundTo((totalCrimp * endsPerInch) / total, 4),
  };
}

/** How much yarn a whole warp needs, in metres, ends counted. */
export function warpYarnMetres({ ends, lengthMetres, crimp = 0 }) {
  positiveNumber(ends, 'ends');
  positiveNumber(lengthMetres, 'lengthMetres');
  return roundTo(ends * yarnForCloth(lengthMetres, crimp), 3);
}

/** How much weft a piece needs, in metres. */
export function weftYarnMetres({ picksPerMetre, lengthMetres, widthMetres, crimp = 0 }) {
  positiveNumber(picksPerMetre, 'picksPerMetre');
  positiveNumber(lengthMetres, 'lengthMetres');
  positiveNumber(widthMetres, 'widthMetres');
  const picks = picksPerMetre * lengthMetres;
  return roundTo(picks * yarnForCloth(widthMetres, crimp), 3);
}
