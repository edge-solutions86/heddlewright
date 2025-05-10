import { InvalidArgument } from '../util/errors.js';
import { array, number, oneOf, plainObject, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';

/**
 * Four-point cloth grading.
 *
 * The four-point system scores a defect by how long it is, whichever way it
 * runs: up to three inches is one point, up to six is two, up to nine is three,
 * and anything longer is four. A hole is scored on its largest dimension and
 * starts at two points however small it is. No linear yard may score more than
 * four points however many defects it holds, which stops one bad yard from
 * condemning a roll on its own.
 *
 * The result is quoted as points per hundred square yards, so rolls of
 * different widths and lengths can be compared:
 *
 *   points per 100 square yards = total points x 3600 / (width in inches x yards)
 */

export const DEFECT_KINDS = ['length', 'hole'];
export const MAX_POINTS_PER_YARD = 4;

/** The points a single defect scores. */
export function defectPoints(defect) {
  plainObject(defect, 'defect');
  const kind = oneOf(defect.kind ?? 'length', 'defect.kind', DEFECT_KINDS);
  const inches = positiveNumber(defect.inches, 'defect.inches');
  if (kind === 'hole') return inches <= 1 ? 2 : 4;
  if (inches <= 3) return 1;
  if (inches <= 6) return 2;
  if (inches <= 9) return 3;
  return 4;
}

/**
 * Score a roll. Defects carry the yard they fall in, and each yard is capped at
 * four points before anything is added up.
 */
export function scoreRoll({ defects, widthInches, yards, cap = MAX_POINTS_PER_YARD }) {
  array(defects, 'defects');
  positiveNumber(widthInches, 'widthInches');
  positiveNumber(yards, 'yards');
  number(cap, 'cap', { min: 1 });
  const perYard = new Map();
  for (const [index, defect] of defects.entries()) {
    const yard = Math.ceil(number(defect.yard ?? 1, `defects[${index}].yard`, { min: 0.0001 }));
    if (yard > Math.ceil(yards)) {
      throw new InvalidArgument('a defect falls past the end of the roll', {
        yard,
        yards,
        index,
      });
    }
    perYard.set(yard, (perYard.get(yard) ?? 0) + defectPoints(defect));
  }
  const capped = [...perYard.entries()].map(([yard, points]) => ({
    yard,
    points,
    counted: Math.min(points, cap),
  }));
  const totalPoints = capped.reduce((total, entry) => total + entry.counted, 0);
  return {
    defects: defects.length,
    rawPoints: capped.reduce((total, entry) => total + entry.points, 0),
    totalPoints,
    cappedYards: capped.filter((entry) => entry.points > cap).map((entry) => entry.yard),
    pointsPer100SquareYards: roundTo((totalPoints * 3600) / (widthInches * yards), 2),
    perYard: capped.sort((a, b) => a.yard - b.yard),
  };
}

/** The usual trade limit: forty points per hundred square yards. */
export const DEFAULT_ACCEPTANCE = 40;

/** Whether a scored roll passes, and by how much. */
export function gradeRoll(score, { acceptance = DEFAULT_ACCEPTANCE } = {}) {
  positiveNumber(acceptance, 'acceptance');
  const points = score.pointsPer100SquareYards;
  return {
    points,
    acceptance,
    accepted: points <= acceptance,
    margin: roundTo(acceptance - points, 2),
  };
}

/** How many points a roll of this size may carry before it is rejected. */
export function pointsAllowed({ widthInches, yards, acceptance = DEFAULT_ACCEPTANCE }) {
  positiveNumber(widthInches, 'widthInches');
  positiveNumber(yards, 'yards');
  positiveNumber(acceptance, 'acceptance');
  return Math.floor((acceptance * widthInches * yards) / 3600);
}

/** Score several rolls as one lot, weighting each by its own area. */
export function scoreLot(rolls) {
  array(rolls, 'rolls', { min: 1 });
  let points = 0;
  let squareYards = 0;
  for (const [index, roll] of rolls.entries()) {
    plainObject(roll, `rolls[${index}]`);
    const score = scoreRoll(roll);
    points += score.totalPoints;
    squareYards += (roll.widthInches * roll.yards) / 36;
  }
  return {
    rolls: rolls.length,
    totalPoints: points,
    squareYards: roundTo(squareYards, 3),
    pointsPer100SquareYards: roundTo((points * 100) / squareYards, 2),
  };
}
