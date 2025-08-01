import { CapacityError, InvalidArgument } from '../util/errors.js';
import { array, nonEmptyString, number, plainObject, positiveNumber } from '../util/check.js';
import { roundTo } from '../util/numeric.js';
import { canWeave } from '../loom/registry.js';
import { runHours } from '../loom/speed.js';

/**
 * Putting work on looms.
 *
 * A style can only go on a loom that will hold it, and a loom can only run one
 * style at a time. Beyond that the choice is a trade: the fastest loom finishes
 * soonest but a wide loom running a narrow cloth wastes reed all week, so the
 * plan here fills the narrowest capable loom first and only reaches for a wide
 * one when nothing else will do.
 */

/** What one run asks of the floor. */
export function createRun(input) {
  plainObject(input, 'run');
  return Object.freeze({
    order: nonEmptyString(input.order, 'order'),
    style: nonEmptyString(input.style, 'style'),
    metres: positiveNumber(input.metres, 'metres'),
    picksPerCm: positiveNumber(input.picksPerCm, 'picksPerCm'),
    requirements: plainObject(input.requirements, 'requirements'),
    priority: number(input.priority ?? 0, 'priority'),
  });
}

/** The looms that could take a run, narrowest capable first. */
export function candidates(looms, run) {
  return looms
    .filter((loom) => canWeave(loom, run.requirements))
    .sort((a, b) => a.reedWidthCm - b.reedWidthCm || b.picksMinute - a.picksMinute);
}

/** How long a run takes on a given loom, in hours. */
export function runHoursOn(loom, run, { efficiency = 0.85 } = {}) {
  return runHours({
    metres: run.metres,
    picksPerCm: run.picksPerCm,
    picksMinute: loom.picksMinute,
    efficiency,
  });
}

/**
 * Build a plan. Runs are taken in priority order, highest first, and each goes
 * on the capable loom that is free soonest, breaking a tie on the narrower
 * loom so wide capacity is kept back for work that needs it.
 */
export function schedule(looms, runs, { efficiency = 0.85, startHour = 0 } = {}) {
  array(looms, 'looms', { min: 1 });
  array(runs, 'runs');
  number(startHour, 'startHour', { min: 0 });
  const free = new Map(looms.map((loom) => [loom.reference, startHour]));
  const placed = [];
  const unplaced = [];

  const ordered = [...runs].sort(
    (a, b) => b.priority - a.priority || a.order.localeCompare(b.order),
  );

  for (const run of ordered) {
    const able = candidates(looms, run);
    if (able.length === 0) {
      unplaced.push({ order: run.order, reason: 'no loom on the floor can weave it' });
      continue;
    }
    const chosen = able.reduce((best, loom) => {
      if (best === null) return loom;
      const freeAt = free.get(loom.reference);
      const bestFree = free.get(best.reference);
      if (freeAt < bestFree) return loom;
      if (freeAt > bestFree) return best;
      return loom.reedWidthCm < best.reedWidthCm ? loom : best;
    }, null);
    const hours = runHoursOn(chosen, run, { efficiency });
    const from = free.get(chosen.reference);
    placed.push({
      order: run.order,
      style: run.style,
      loom: chosen.reference,
      fromHour: roundTo(from, 3),
      toHour: roundTo(from + hours, 3),
      hours,
    });
    free.set(chosen.reference, from + hours);
  }

  return {
    placed: placed.sort((a, b) => a.fromHour - b.fromHour || a.loom.localeCompare(b.loom)),
    unplaced,
    finishesAt: roundTo(Math.max(startHour, ...placed.map((entry) => entry.toHour)), 3),
    loomFinish: Object.fromEntries(
      [...free.entries()].map(([reference, hour]) => [reference, roundTo(hour, 3)]),
    ),
  };
}

/** How busy each loom is over the plan, as a share of the whole span. */
export function loomLoad(plan) {
  plainObject(plan, 'plan');
  const span = plan.finishesAt;
  const busy = new Map();
  for (const entry of plan.placed) {
    busy.set(entry.loom, roundTo((busy.get(entry.loom) ?? 0) + entry.hours, 3));
  }
  return [...busy.entries()]
    .map(([loom, hours]) => ({
      loom,
      hours,
      utilisation: span === 0 ? 0 : roundTo(hours / span, 4),
    }))
    .sort((a, b) => b.hours - a.hours || a.loom.localeCompare(b.loom));
}

/** Refuse a plan that leaves work with nowhere to go. */
export function assertFullyPlaced(plan) {
  if (plan.unplaced.length > 0) {
    throw new CapacityError('some runs have no loom to go on', {
      unplaced: plan.unplaced,
    });
  }
  return plan;
}

/** When one order finishes, or null when it was never placed. */
export function finishOf(plan, order) {
  nonEmptyString(order, 'order');
  const entry = plan.placed.find((placed) => placed.order === order);
  return entry === undefined ? null : entry.toHour;
}

/** Orders that will not be finished by the hour they are wanted. */
export function lateOrders(plan, wantedByHour) {
  plainObject(wantedByHour, 'wantedByHour');
  const late = [];
  for (const entry of plan.placed) {
    const due = wantedByHour[entry.order];
    if (due === undefined) continue;
    number(due, `wantedByHour.${entry.order}`, { min: 0 });
    if (entry.toHour > due) {
      late.push({
        order: entry.order,
        due,
        finishes: entry.toHour,
        byHours: roundTo(entry.toHour - due, 3),
      });
    }
  }
  return late.sort((a, b) => b.byHours - a.byHours);
}

/** A run that no loom can take is a design problem, not a planning one. */
export function unweavableRuns(looms, runs) {
  array(runs, 'runs');
  return runs.filter((run) => candidates(looms, run).length === 0).map((run) => run.order);
}

/** Refuse a run whose requirements were never filled in. */
export function assertRunComplete(run) {
  if (Object.keys(run.requirements).length === 0) {
    throw new InvalidArgument('the run says nothing about what the loom must do', {
      order: run.order,
    });
  }
  return run;
}
