import { CapacityError, NotFound } from '../util/errors.js';
import {
  integer,
  nonEmptyString,
  number,
  oneOf,
  plainObject,
  positiveInteger,
  positiveNumber,
} from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { groupBy } from '../util/group.js';

/**
 * The loom register.
 *
 * A shed floor is a list of machines, each with a width, a shedding motion and
 * a speed, and a style can only be put on a loom that can actually hold it.
 * Matching a style to a loom is a capacity question with four parts, and this
 * is where they are all asked at once.
 */

export const SHEDDING = ['tappet', 'dobby', 'jacquard'];
export const PICKING = ['shuttle', 'rapier', 'projectile', 'air jet'];

/** Register a loom. */
export function createLoom(input) {
  plainObject(input, 'loom');
  return Object.freeze({
    reference: nonEmptyString(input.reference, 'reference'),
    shedding: oneOf(input.shedding, 'shedding', SHEDDING),
    picking: oneOf(input.picking ?? 'shuttle', 'picking', PICKING),
    reedWidthCm: positiveNumber(input.reedWidthCm, 'reedWidthCm'),
    shafts: input.shedding === 'jacquard' ? 0 : positiveInteger(input.shafts, 'shafts'),
    hooks: input.shedding === 'jacquard' ? positiveInteger(input.hooks, 'hooks') : 0,
    // A dobby or jacquard has no treadles at all, so zero is a real answer here
    // rather than a missing one.
    treadles: integer(input.treadles ?? 0, 'treadles', { min: 0 }),
    picksMinute: positiveNumber(input.picksMinute, 'picksMinute'),
    shuttleBoxes:
      input.shuttleBoxes === undefined ? 1 : positiveInteger(input.shuttleBoxes, 'shuttleBoxes'),
    installed: input.installed ?? null,
  });
}

/** Find a loom by reference. */
export function loomByReference(looms, reference) {
  const found = looms.find((loom) => loom.reference === reference);
  if (found === undefined) {
    throw new NotFound(`no loom called "${reference}"`, { reference });
  }
  return found;
}

/**
 * What a style asks of a loom: a width, a shedding capacity, and as many
 * shuttle boxes as it has weft colours.
 */
export function styleRequirements({ widthCm, shafts = 0, hooks = 0, weftColours = 1 }) {
  positiveNumber(widthCm, 'widthCm');
  number(shafts, 'shafts', { min: 0 });
  number(hooks, 'hooks', { min: 0 });
  positiveInteger(weftColours, 'weftColours');
  return { widthCm, shafts, hooks, weftColours };
}

/** Every reason a loom cannot take a style, or an empty list when it can. */
export function shortfalls(loom, requirements) {
  const problems = [];
  if (requirements.widthCm > loom.reedWidthCm) {
    problems.push({
      code: 'too_narrow',
      message: `${loom.reference} is ${loom.reedWidthCm} cm in the reed`,
      detail: { needed: requirements.widthCm, available: loom.reedWidthCm },
    });
  }
  if (requirements.hooks > 0 && loom.shedding !== 'jacquard') {
    problems.push({
      code: 'not_jacquard',
      message: `${loom.reference} has no jacquard head`,
      detail: { shedding: loom.shedding },
    });
  }
  if (requirements.hooks > loom.hooks && loom.shedding === 'jacquard') {
    problems.push({
      code: 'too_few_hooks',
      message: `${loom.reference} carries ${loom.hooks} hooks`,
      detail: { needed: requirements.hooks, available: loom.hooks },
    });
  }
  if (requirements.shafts > loom.shafts && loom.shedding !== 'jacquard') {
    problems.push({
      code: 'too_few_shafts',
      message: `${loom.reference} carries ${loom.shafts} shafts`,
      detail: { needed: requirements.shafts, available: loom.shafts },
    });
  }
  if (requirements.weftColours > loom.shuttleBoxes) {
    problems.push({
      code: 'too_few_boxes',
      message: `${loom.reference} has ${loom.shuttleBoxes} shuttle boxes`,
      detail: { needed: requirements.weftColours, available: loom.shuttleBoxes },
    });
  }
  return problems;
}

/** Whether a loom can take a style at all. */
export function canWeave(loom, requirements) {
  return shortfalls(loom, requirements).length === 0;
}

/** Refuse a loom that cannot take the style, naming every reason. */
export function assertCanWeave(loom, requirements) {
  const problems = shortfalls(loom, requirements);
  if (problems.length > 0) {
    throw new CapacityError(`${loom.reference} cannot weave that style`, {
      loom: loom.reference,
      problems,
    });
  }
  return loom;
}

/** Every loom that can take a style, fastest first. */
export function loomsFor(looms, requirements) {
  return looms
    .filter((loom) => canWeave(loom, requirements))
    .sort((a, b) => b.picksMinute - a.picksMinute);
}

/** How much of a loom's width a style uses, which is what idle reed costs. */
export function widthUtilisation(loom, requirements) {
  return roundTo(requirements.widthCm / loom.reedWidthCm, 4);
}

/** The register grouped by shedding motion. */
export function byShedding(looms) {
  const found = groupBy(looms, (loom) => loom.shedding);
  return SHEDDING.map((shedding) => ({
    shedding,
    references: (found.get(shedding) ?? []).map((loom) => loom.reference),
  }));
}
