import { InvalidArgument, NotFound } from '../util/errors.js';
import { array, nonEmptyString, number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo, sumBy } from '../util/numeric.js';

/**
 * Finishing.
 *
 * Cloth leaves the loom longer, wider and lighter than it will be sold. Every
 * finishing route shrinks it by its own amount in each direction and changes
 * its weight, and the changes compound: a route of four processes is not the
 * sum of four shrinkages, it is the product of four ratios. Adding percentages
 * here is the mistake that puts a piece under its contract width.
 */

const PROCESSES = new Map(
  [
    { name: 'scour', lengthShrink: 1.5, widthShrink: 2, weightChange: -4 },
    { name: 'bleach', lengthShrink: 1, widthShrink: 1.5, weightChange: -2 },
    { name: 'mercerise', lengthShrink: 2, widthShrink: 3, weightChange: 1 },
    { name: 'dye', lengthShrink: 1, widthShrink: 1, weightChange: 1.5 },
    { name: 'sanforise', lengthShrink: 3, widthShrink: 0.5, weightChange: 0 },
    { name: 'calender', lengthShrink: 0, widthShrink: -1, weightChange: 0 },
    { name: 'raise', lengthShrink: 2, widthShrink: 2, weightChange: -3 },
  ].map((process) => [process.name, Object.freeze(process)]),
);

/** Every finishing process the works knows. */
export function listProcesses() {
  return [...PROCESSES.values()].sort((a, b) => a.name.localeCompare(b.name));
}

/** Look one up. */
export function processByName(name) {
  nonEmptyString(name, 'name');
  const found = PROCESSES.get(name);
  if (found === undefined) {
    throw new NotFound(`no finishing process called "${name}"`, {
      name,
      known: [...PROCESSES.keys()],
    });
  }
  return found;
}

/**
 * The compound effect of a route. Each stage takes its share of what is left,
 * so the ratios multiply rather than the percentages adding.
 */
export function routeEffect(route) {
  array(route, 'route', { min: 1 });
  let length = 1;
  let width = 1;
  let weight = 1;
  for (const name of route) {
    const process = processByName(name);
    length *= 1 - process.lengthShrink / 100;
    width *= 1 - process.widthShrink / 100;
    weight *= 1 + process.weightChange / 100;
  }
  return {
    lengthRatio: roundTo(length, 6),
    widthRatio: roundTo(width, 6),
    weightRatio: roundTo(weight, 6),
    lengthShrinkPercent: roundTo((1 - length) * 100, 3),
    widthShrinkPercent: roundTo((1 - width) * 100, 3),
  };
}

/** What a piece measures after a route. */
export function finishedPiece({ metres, widthCm, gsm }, route) {
  positiveNumber(metres, 'metres');
  positiveNumber(widthCm, 'widthCm');
  const effect = routeEffect(route);
  const finished = {
    metres: roundTo(metres * effect.lengthRatio, 3),
    widthCm: roundTo(widthCm * effect.widthRatio, 2),
  };
  if (gsm !== undefined) {
    positiveNumber(gsm, 'gsm');
    // Weight per square metre rises as the cloth shrinks into itself, on top of
    // whatever the process added or took away.
    const area = effect.lengthRatio * effect.widthRatio;
    finished.gsm = roundTo((gsm * effect.weightRatio) / area, 2);
  }
  return finished;
}

/**
 * The greige a finished piece has to start from, which is the calculation a
 * planner actually needs: work backwards from the contract width and length.
 */
export function greigeNeeded({ metres, widthCm }, route) {
  const effect = routeEffect(route);
  return {
    metres: roundTo(metres / effect.lengthRatio, 3),
    widthCm: roundTo(widthCm / effect.widthRatio, 2),
  };
}

/** Whether a finished piece will meet a contract width. */
export function meetsWidth(greige, route, { contractWidthCm, tolerancePercent = 2 }) {
  positiveNumber(contractWidthCm, 'contractWidthCm');
  number(tolerancePercent, 'tolerancePercent', { min: 0 });
  const finished = finishedPiece(greige, route);
  const shortfall = contractWidthCm - finished.widthCm;
  const allowed = (contractWidthCm * tolerancePercent) / 100;
  return {
    finishedWidthCm: finished.widthCm,
    contractWidthCm,
    shortfallCm: roundTo(shortfall, 2),
    meets: shortfall <= allowed,
  };
}

/** Refuse a route naming a process nobody has. */
export function assertRouteKnown(route) {
  array(route, 'route', { min: 1 });
  for (const name of route) processByName(name);
  return route;
}

/**
 * Adding the shrinkages instead of compounding them, which is what a docket
 * written by hand usually does. It is here so the difference can be shown
 * rather than argued about.
 */
export function naiveRouteShrink(route) {
  assertRouteKnown(route);
  return {
    lengthShrinkPercent: roundTo(
      sumBy(route, (name) => processByName(name).lengthShrink, 'route'),
      3,
    ),
    widthShrinkPercent: roundTo(
      sumBy(route, (name) => processByName(name).widthShrink, 'route'),
      3,
    ),
  };
}

/** Refuse a route whose finished width will miss the contract. */
export function assertMeetsWidth(greige, route, options) {
  plainObject(options, 'options');
  const reading = meetsWidth(greige, route, options);
  if (!reading.meets) {
    throw new InvalidArgument('that route finishes narrower than the contract allows', reading);
  }
  return reading;
}
