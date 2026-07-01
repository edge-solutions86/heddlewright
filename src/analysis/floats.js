import { integer, positiveInteger } from '../util/check.js';
import { assertMatrix, columns } from '../util/matrix.js';
import { cyclicRuns, longestRun } from '../util/runs.js';
import { meanBy } from '../util/numeric.js';

/**
 * Float measurement.
 *
 * A warp float is a run of raised cells down one end; a weft float is a run of
 * lowered cells across one pick. Both are measured round the repeat, because
 * the cloth does not stop at the edge of the diagram, and both are what decide
 * whether a cloth will snag, how it will drape and how closely it can be set.
 */

/** Every warp float in the repeat, as `{ end, start, length }`, 1-based ends. */
export function warpFloats(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  const out = [];
  for (const [e, column] of columns(drawdown).entries()) {
    for (const run of cyclicRuns(column)) {
      if (run.value !== 1) continue;
      out.push({ end: e + 1, start: run.start + 1, length: run.length, wraps: run.wraps });
    }
  }
  return out;
}

/** Every weft float, as `{ pick, start, length }`, 1-based picks and ends. */
export function weftFloats(drawdown) {
  assertMatrix(drawdown, 'drawdown');
  const out = [];
  for (const [p, row] of drawdown.entries()) {
    for (const run of cyclicRuns(row)) {
      if (run.value !== 0) continue;
      out.push({ pick: p + 1, start: run.start + 1, length: run.length, wraps: run.wraps });
    }
  }
  return out;
}

/** The longest float either way, and which thread carries it. */
export function longestFloat(drawdown) {
  const warp = warpFloats(drawdown);
  const weft = weftFloats(drawdown);
  const longestWarp = warp.reduce((best, f) => (f.length > best.length ? f : best), {
    length: 0,
  });
  const longestWeft = weft.reduce((best, f) => (f.length > best.length ? f : best), {
    length: 0,
  });
  return {
    warp: longestWarp.length,
    weft: longestWeft.length,
    longest: Math.max(longestWarp.length, longestWeft.length),
    onEnd: longestWarp.end ?? null,
    onPick: longestWeft.pick ?? null,
  };
}

/** Floats longer than the limit, both ways, ready to print in a report. */
export function floatsOver(drawdown, limit) {
  positiveInteger(limit, 'limit');
  return [
    ...warpFloats(drawdown)
      .filter((float) => float.length > limit)
      .map((float) => ({ ...float, direction: 'warp' })),
    ...weftFloats(drawdown)
      .filter((float) => float.length > limit)
      .map((float) => ({ ...float, direction: 'weft' })),
  ];
}

/** A count of floats by length, for each direction. */
export function floatHistogram(drawdown) {
  const warp = new Map();
  const weft = new Map();
  for (const float of warpFloats(drawdown)) {
    warp.set(float.length, (warp.get(float.length) ?? 0) + 1);
  }
  for (const float of weftFloats(drawdown)) {
    weft.set(float.length, (weft.get(float.length) ?? 0) + 1);
  }
  return { warp, weft };
}

/** The mean float length in each direction, which reads as cloth firmness. */
export function meanFloatLength(drawdown) {
  const warp = warpFloats(drawdown);
  const weft = weftFloats(drawdown);
  const average = (list) => meanBy(list, (float) => float.length, 'floats');
  return { warp: average(warp), weft: average(weft) };
}

/**
 * The float limit a cloth can carry before it snags, taken from the sett: the
 * usual shed rule is that a float should not span more than about a tenth of an
 * inch, so the limit falls as the cloth is set closer.
 */
export function floatLimitForSett(threadsPerInch, { spanInches = 0.1 } = {}) {
  positiveInteger(threadsPerInch, 'threadsPerInch');
  return Math.max(1, Math.floor(threadsPerInch * spanInches));
}

/** True when nothing in the cloth floats further than the limit. */
export function withinFloatLimit(drawdown, limit) {
  integer(limit, 'limit', { min: 1 });
  return longestFloat(drawdown).longest <= limit;
}

/** The longest float down a single named end, 1-based. */
export function floatOnEnd(drawdown, end) {
  assertMatrix(drawdown, 'drawdown');
  positiveInteger(end, 'end');
  return longestRun(
    drawdown.map((row) => row[end - 1]),
    1,
  );
}

/** The longest float across a single named pick, 1-based. */
export function floatOnPick(drawdown, pick) {
  assertMatrix(drawdown, 'drawdown');
  positiveInteger(pick, 'pick');
  return longestRun(drawdown[pick - 1], 0);
}
