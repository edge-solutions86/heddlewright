import { InvalidArgument } from './errors.js';
import { integer, number, array } from './check.js';

/**
 * Small integer and statistics helpers. The number theory in here is what
 * decides whether a satin move number is legal and how long a card deck has to
 * be before a pattern comes back round, so it is kept separate and tested hard.
 */

export function gcd(a, b) {
  let x = Math.abs(integer(a, 'a'));
  let y = Math.abs(integer(b, 'b'));
  while (y !== 0) {
    const t = y;
    y = x % y;
    x = t;
  }
  return x;
}

export function lcm(a, b) {
  const g = gcd(a, b);
  if (g === 0) return 0;
  return Math.abs((a / g) * b);
}

export function areCoprime(a, b) {
  return gcd(a, b) === 1;
}

/** Every m in 1..n-1 that is coprime with n, in ascending order. */
export function coprimesBelow(n) {
  integer(n, 'n', { min: 1 });
  const out = [];
  for (let m = 1; m < n; m += 1) {
    if (gcd(m, n) === 1) out.push(m);
  }
  return out;
}

/** Non-negative remainder, unlike the % operator on negative operands. */
export function mod(value, modulus) {
  integer(modulus, 'modulus', { min: 1 });
  const r = integer(value, 'value') % modulus;
  // -16 % 8 is negative zero, which compares unequal to 0 under Object.is and
  // then shows up as an index that fails a strict lookup. Adding zero folds it.
  return r < 0 ? r + modulus : r + 0;
}

export function clamp(value, min, max) {
  number(value, 'value');
  number(min, 'min');
  number(max, 'max');
  if (min > max) {
    throw new InvalidArgument('clamp needs min <= max', { min, max });
  }
  return Math.min(Math.max(value, min), max);
}

/** Round to a fixed number of decimal places, away from zero on a tie. */
export function roundTo(value, places = 2) {
  number(value, 'value');
  integer(places, 'places', { min: 0, max: 12 });
  const factor = 10 ** places;
  const scaled = value * factor;
  const rounded = Math.sign(scaled) * Math.round(Math.abs(scaled));
  return rounded / factor;
}

export function sum(values) {
  array(values, 'values');
  let total = 0;
  for (const [i, v] of values.entries()) {
    total += number(v, `values[${i}]`);
  }
  return total;
}

export function mean(values) {
  array(values, 'values', { min: 1 });
  return sum(values) / values.length;
}

/**
 * Add a field up over a list of records.
 *
 * Twenty seven places in the source had written this reduce out by hand, and
 * three of them read a field that could be undefined, which quietly gave NaN
 * instead of naming the record at fault. This names it.
 */
export function sumBy(items, read, field = 'items') {
  array(items, field);
  let total = 0;
  for (const [i, item] of items.entries()) {
    total += number(read(item, i), `${field}[${i}]`);
  }
  return total;
}

/** The mean of a field over a list of records. An empty list averages zero. */
export function meanBy(items, read, field = 'items') {
  array(items, field);
  if (items.length === 0) return 0;
  return sumBy(items, read, field) / items.length;
}

/** Population standard deviation, which is what a striking or pick spacing report wants. */
export function stdev(values) {
  array(values, 'values', { min: 1 });
  const m = mean(values);
  const variance = sum(values.map((v) => (v - m) ** 2)) / values.length;
  return Math.sqrt(variance);
}

/** Linear-interpolated percentile over a sorted copy of the input. */
export function percentile(values, p) {
  array(values, 'values', { min: 1 });
  number(p, 'p', { min: 0, max: 100 });
  const sorted = [...values].sort((a, b) => a - b);
  if (sorted.length === 1) return sorted[0];
  const pos = (p / 100) * (sorted.length - 1);
  const low = Math.floor(pos);
  const high = Math.ceil(pos);
  if (low === high) return sorted[low];
  return sorted[low] + (sorted[high] - sorted[low]) * (pos - low);
}
