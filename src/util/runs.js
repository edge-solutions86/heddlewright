import { array, integer, oneOf } from './check.js';

/**
 * Run-length work over a one-dimensional sequence.
 *
 * Float measurement is the reason this exists: a warp float is a run of raised
 * cells down a column of the drawdown, and a weft float is a run of lowered
 * cells across a row. Both are the same question asked of different slices, and
 * both have to understand that a repeat wraps round.
 */

/** Split a sequence into { value, start, length } runs, left to right. */
export function runs(sequence) {
  array(sequence, 'sequence');
  const out = [];
  let start = 0;
  for (let i = 1; i <= sequence.length; i += 1) {
    if (i === sequence.length || sequence[i] !== sequence[start]) {
      out.push({ value: sequence[start], start, length: i - start });
      start = i;
    }
  }
  return out;
}

/**
 * Runs over a sequence read as a closed loop, so a run touching both ends is
 * reported once, anchored at its true start, with a start index that may be
 * larger than its end.
 */
export function cyclicRuns(sequence) {
  array(sequence, 'sequence');
  if (sequence.length === 0) return [];
  const flat = runs(sequence);
  if (flat.length === 1) {
    return [{ value: sequence[0], start: 0, length: sequence.length, wraps: false }];
  }
  const first = flat[0];
  const last = flat[flat.length - 1];
  if (first.value !== last.value) {
    return flat.map((r) => ({ ...r, wraps: false }));
  }
  const merged = flat.slice(1, -1).map((r) => ({ ...r, wraps: false }));
  merged.push({
    value: last.value,
    start: last.start,
    length: last.length + first.length,
    wraps: true,
  });
  return merged;
}

/** The longest run of `value`, counting the sequence as a loop unless told otherwise. */
export function longestRun(sequence, value, { cyclic = true } = {}) {
  const found = (cyclic ? cyclicRuns(sequence) : runs(sequence)).filter((r) => r.value === value);
  return found.reduce((best, r) => Math.max(best, r.length), 0);
}

/** How many runs of `value` there are, which is the interlacement count for a column. */
export function countRuns(sequence, value, { cyclic = true } = {}) {
  return (cyclic ? cyclicRuns(sequence) : runs(sequence)).filter((r) => r.value === value).length;
}

/** A map from run length to how many runs of that length carry `value`. */
export function runHistogram(sequence, value, { cyclic = true } = {}) {
  const hist = new Map();
  for (const run of cyclic ? cyclicRuns(sequence) : runs(sequence)) {
    if (run.value !== value) continue;
    hist.set(run.length, (hist.get(run.length) ?? 0) + 1);
  }
  return hist;
}

/** Every run of `value` longer than `limit`, for float-length reporting. */
export function runsOver(sequence, value, limit, { cyclic = true } = {}) {
  integer(limit, 'limit', { min: 0 });
  return (cyclic ? cyclicRuns(sequence) : runs(sequence)).filter(
    (r) => r.value === value && r.length > limit,
  );
}

/** Rebuild the original sequence from its runs, for round-trip checking. */
export function expandRuns(list) {
  array(list, 'list');
  const out = [];
  for (const [i, run] of list.entries()) {
    integer(run.length, `list[${i}].length`, { min: 1 });
    for (let n = 0; n < run.length; n += 1) out.push(run.value);
  }
  return out;
}

/** Compact run notation, "1x4 0x2", useful in a card deck listing. */
export function formatRuns(sequence, { style = 'compact' } = {}) {
  oneOf(style, 'style', ['compact', 'long']);
  return runs(sequence)
    .map((r) => (style === 'compact' ? `${r.value}x${r.length}` : `${r.length} of ${r.value}`))
    .join(style === 'compact' ? ' ' : ', ');
}
