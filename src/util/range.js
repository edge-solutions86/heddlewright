import { InvalidArgument } from './errors.js';
import { array, integer, nonEmptyString } from './check.js';

/**
 * Integer range notation, "1-4,7,9-11".
 *
 * Card selections, shaft groups, denting plans and defect positions are all
 * written this way on a shed floor docket, so both directions are needed.
 */

/** Parse range notation into a sorted, deduplicated list of integers. */
export function parseRange(text, { min, max } = {}) {
  nonEmptyString(text, 'text');
  const found = new Set();
  for (const rawPart of text.split(',')) {
    const part = rawPart.trim();
    if (part === '') {
      throw new InvalidArgument('range has an empty part', { text });
    }
    const match = /^(-?\d+)(?:\s*-\s*(-?\d+))?$/.exec(part);
    if (match === null) {
      throw new InvalidArgument(`range part "${part}" is not a number or a span`, { part, text });
    }
    const from = Number.parseInt(match[1], 10);
    const to = match[2] === undefined ? from : Number.parseInt(match[2], 10);
    if (to < from) {
      throw new InvalidArgument(`range part "${part}" counts backwards`, { part, from, to });
    }
    for (let n = from; n <= to; n += 1) found.add(n);
  }
  const values = [...found].sort((a, b) => a - b);
  for (const value of values) {
    if (min !== undefined && value < min) {
      throw new InvalidArgument(`range value ${value} is below ${min}`, { value, min });
    }
    if (max !== undefined && value > max) {
      throw new InvalidArgument(`range value ${value} is above ${max}`, { value, max });
    }
  }
  return values;
}

/** Fold a list of integers back into the shortest range notation. */
export function formatRange(values) {
  array(values, 'values');
  const sorted = [...new Set(values.map((v, i) => integer(v, `values[${i}]`)))].sort(
    (a, b) => a - b,
  );
  if (sorted.length === 0) return '';
  const parts = [];
  let start = sorted[0];
  let previous = sorted[0];
  for (const value of sorted.slice(1)) {
    if (value === previous + 1) {
      previous = value;
      continue;
    }
    parts.push(start === previous ? `${start}` : `${start}-${previous}`);
    start = value;
    previous = value;
  }
  parts.push(start === previous ? `${start}` : `${start}-${previous}`);
  return parts.join(',');
}

/** The count a range covers without building the list. */
export function rangeSize(text) {
  return parseRange(text).length;
}

/** True when the value falls inside the range notation. */
export function rangeIncludes(text, value) {
  integer(value, 'value');
  return parseRange(text).includes(value);
}

/** Every integer from `from` to `to` inclusive, counting either way. */
export function inclusive(from, to) {
  integer(from, 'from');
  integer(to, 'to');
  const step = to >= from ? 1 : -1;
  const out = [];
  for (let n = from; step > 0 ? n <= to : n >= to; n += step) out.push(n);
  return out;
}
