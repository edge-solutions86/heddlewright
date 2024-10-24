import { array, integer, positiveInteger } from './check.js';
import { mod, gcd } from './numeric.js';

/**
 * Rotation and period work over a repeating sequence.
 *
 * A treadling, a threading and a card chain all repeat, so "how long is the
 * real repeat" and "are these two the same pattern started in a different
 * place" are questions this package asks constantly.
 */

/** Rotate left by `n`; a negative `n` rotates right. */
export function rotate(sequence, n) {
  array(sequence, 'sequence');
  integer(n, 'n');
  if (sequence.length === 0) return [];
  const k = mod(n, sequence.length);
  return [...sequence.slice(k), ...sequence.slice(0, k)];
}

/** Every distinct rotation, in rotation order starting from the input. */
export function rotations(sequence) {
  array(sequence, 'sequence');
  const seen = new Set();
  const out = [];
  for (let n = 0; n < sequence.length; n += 1) {
    const r = rotate(sequence, n);
    const key = JSON.stringify(r);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(r);
  }
  return out;
}

/**
 * The shortest prefix that, repeated, rebuilds the whole sequence. A treadling
 * written out four times has a period of a quarter of its length.
 */
export function period(sequence) {
  array(sequence, 'sequence');
  const n = sequence.length;
  if (n === 0) return 0;
  for (let p = 1; p <= n; p += 1) {
    if (n % p !== 0) continue;
    let matches = true;
    for (let i = p; i < n && matches; i += 1) {
      if (sequence[i] !== sequence[i % p]) matches = false;
    }
    if (matches) return p;
  }
  return n;
}

/** The sequence cut down to one period. */
export function minimalRepeat(sequence) {
  const p = period(sequence);
  return sequence.slice(0, p);
}

/** True when b is a rotation of a, so both weave the same cloth started elsewhere. */
export function isRotationOf(a, b) {
  array(a, 'a');
  array(b, 'b');
  if (a.length !== b.length) return false;
  if (a.length === 0) return true;
  return rotations(a).some((r) => r.every((value, i) => value === b[i]));
}

/** The offset that turns a into b, or -1 when no rotation does. */
export function rotationOffset(a, b) {
  array(a, 'a');
  array(b, 'b');
  if (a.length !== b.length) return -1;
  for (let n = 0; n < a.length; n += 1) {
    const r = rotate(a, n);
    if (r.every((value, i) => value === b[i])) return n;
  }
  return a.length === 0 ? 0 : -1;
}

/**
 * The rotation that sorts smallest, used as a stable key so two chains cut at
 * different lags hash the same.
 */
export function canonicalRotation(sequence) {
  array(sequence, 'sequence');
  if (sequence.length === 0) return [];
  let best = null;
  let bestKey = null;
  for (const r of rotations(sequence)) {
    const key = JSON.stringify(r);
    if (bestKey === null || key < bestKey) {
      bestKey = key;
      best = r;
    }
  }
  return best;
}

/**
 * A step sequence: start at 0 and add `step` `count` times, modulo `size`.
 * Satin move numbers, sectional warping and card lag advances all use it.
 */
export function stepSequence(size, step, count = size) {
  positiveInteger(size, 'size');
  integer(step, 'step');
  positiveInteger(count, 'count');
  const out = [];
  for (let i = 0; i < count; i += 1) out.push(mod(i * step, size));
  return out;
}

/** How many steps of `step` in a ring of `size` before the start comes back. */
export function stepCycleLength(size, step) {
  positiveInteger(size, 'size');
  integer(step, 'step');
  const s = mod(step, size);
  if (s === 0) return 1;
  return size / gcd(size, s);
}

/** True when stepping by `step` visits every position of the ring exactly once. */
export function stepCoversRing(size, step) {
  return stepCycleLength(size, step) === size;
}
