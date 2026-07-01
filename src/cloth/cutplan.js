import { array, number, positiveNumber } from '../util/check.js';
import { roundTo, sum, sumBy } from '../util/numeric.js';

/**
 * Cutting a roll around its faults.
 *
 * A garment cutter wants unbroken lengths. A roll with a fault every few yards
 * is worth much less than its length suggests, because a fault in the middle of
 * a length spoils the whole length. Planning the cuts means finding the clear
 * runs first and only then deciding what to take out of them.
 */

/**
 * The stretches of a roll clear of faults, each with a margin either side of
 * the fault so a cut never lands on one.
 */
export function clearRuns(defectYards, rollYards, { clearance = 0.25 } = {}) {
  array(defectYards, 'defectYards');
  positiveNumber(rollYards, 'rollYards');
  number(clearance, 'clearance', { min: 0 });
  const blocked = [...defectYards]
    .map((at, index) => number(at, `defectYards[${index}]`, { min: 0 }))
    .sort((a, b) => a - b)
    .map((at) => ({ from: Math.max(0, at - clearance), to: Math.min(rollYards, at + clearance) }));

  const merged = [];
  for (const span of blocked) {
    const last = merged[merged.length - 1];
    if (last !== undefined && span.from <= last.to) {
      last.to = Math.max(last.to, span.to);
      continue;
    }
    merged.push({ ...span });
  }

  const runs = [];
  let at = 0;
  for (const span of merged) {
    if (span.from > at) runs.push({ from: roundTo(at, 3), to: roundTo(span.from, 3) });
    at = Math.max(at, span.to);
  }
  if (at < rollYards) runs.push({ from: roundTo(at, 3), to: roundTo(rollYards, 3) });
  return runs.map((run) => ({ ...run, yards: roundTo(run.to - run.from, 3) }));
}

/**
 * Fit wanted lengths into the clear runs, longest first, taking each length
 * out of the first run that still holds it.
 */
export function planCuts(runs, wantedLengths) {
  array(runs, 'runs');
  array(wantedLengths, 'wantedLengths');
  const remaining = runs.map((run) => ({ ...run, left: run.yards }));
  const wanted = [...wantedLengths].sort((a, b) => b - a);
  const cuts = [];
  const unmet = [];
  for (const length of wanted) {
    positiveNumber(length, 'wanted length');
    const run = remaining.find((candidate) => candidate.left >= length);
    if (run === undefined) {
      unmet.push(length);
      continue;
    }
    const from = roundTo(run.to - run.left, 3);
    cuts.push({ from, to: roundTo(from + length, 3), yards: length });
    run.left = roundTo(run.left - length, 3);
  }
  return {
    cuts: cuts.sort((a, b) => a.from - b.from),
    unmet,
    offcuts: remaining
      .filter((run) => run.left > 0)
      .map((run) => ({ from: roundTo(run.to - run.left, 3), to: run.to, yards: run.left })),
  };
}

/** What a plan actually yields, against the roll it came out of. */
export function cutPlanSummary(plan, rollYards) {
  positiveNumber(rollYards, 'rollYards');
  const cut = roundTo(sum(plan.cuts.map((entry) => entry.yards)), 3);
  const offcut = roundTo(sum(plan.offcuts.map((entry) => entry.yards)), 3);
  return {
    rollYards,
    cutYards: cut,
    offcutYards: offcut,
    wasteYards: roundTo(rollYards - cut - offcut, 3),
    yield: roundTo(cut / rollYards, 4),
    unmet: plan.unmet.length,
  };
}

/** The longest single length the roll can give, clear of faults. */
export function bestSingleLength(runs) {
  array(runs, 'runs');
  return runs.reduce((best, run) => Math.max(best, run.yards), 0);
}

/** How many lengths of one size the roll gives. */
export function lengthsAvailable(runs, length) {
  positiveNumber(length, 'length');
  return sumBy(runs, (run) => Math.floor(run.yards / length), 'runs');
}

/**
 * The whole job in one call: find the clear runs, fit the order into them and
 * report what came out.
 */
export function cutRoll({ rollYards, defectYards, wantedLengths, clearance }) {
  const runs = clearRuns(defectYards, rollYards, { clearance });
  const plan = planCuts(runs, wantedLengths);
  return { runs, ...plan, summary: cutPlanSummary(plan, rollYards) };
}
