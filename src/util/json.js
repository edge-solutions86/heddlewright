import { InvalidArgument } from './errors.js';

/**
 * Canonical JSON. Two records that hold the same information have to serialise
 * to the same bytes, otherwise a content id changes because a key moved and the
 * journal starts reporting differences that are not there.
 */

/** Stable stringify: object keys sorted, arrays left in order, no whitespace. */
export function canonicalStringify(value) {
  return write(value, new Set());
}

function write(value, seen) {
  if (value === null) return 'null';
  const type = typeof value;
  if (type === 'number') {
    if (!Number.isFinite(value)) {
      throw new InvalidArgument('canonical JSON cannot hold a non-finite number', { got: value });
    }
    return JSON.stringify(value === 0 ? 0 : value);
  }
  if (type === 'string' || type === 'boolean') return JSON.stringify(value);
  if (type === 'bigint') {
    throw new InvalidArgument('canonical JSON cannot hold a bigint', {});
  }
  if (type === 'undefined' || type === 'function' || type === 'symbol') {
    throw new InvalidArgument(`canonical JSON cannot hold a ${type}`, {});
  }
  if (seen.has(value)) {
    throw new InvalidArgument('canonical JSON cannot hold a cycle', {});
  }
  seen.add(value);
  try {
    if (Array.isArray(value)) {
      return `[${value.map((item) => write(item === undefined ? null : item, seen)).join(',')}]`;
    }
    if (value instanceof Map) {
      return write(Object.fromEntries(value), seen);
    }
    if (value instanceof Set) {
      return write([...value], seen);
    }
    if (value instanceof Date) {
      return JSON.stringify(value.toISOString());
    }
    const keys = Object.keys(value)
      .filter((key) => value[key] !== undefined)
      .sort();
    const body = keys.map((key) => `${JSON.stringify(key)}:${write(value[key], seen)}`).join(',');
    return `{${body}}`;
  } finally {
    seen.delete(value);
  }
}

/** Deep-freeze so a record handed out of the store cannot be edited in place. */
export function deepFreeze(value) {
  if (value === null || typeof value !== 'object' || Object.isFrozen(value)) return value;
  Object.freeze(value);
  for (const key of Object.getOwnPropertyNames(value)) {
    deepFreeze(value[key]);
  }
  return value;
}

/** A structured clone that goes through the canonical form, so it is also a check. */
export function canonicalClone(value) {
  return JSON.parse(canonicalStringify(value));
}

/** True when two values have the same canonical form. */
export function canonicalEquals(a, b) {
  return canonicalStringify(a) === canonicalStringify(b);
}

/** Parse, with our error type instead of a bare SyntaxError. */
export function parseJson(text, field = 'body') {
  if (typeof text !== 'string') {
    throw new InvalidArgument(`${field} must be a string`, { field, got: typeof text });
  }
  try {
    return JSON.parse(text);
  } catch (err) {
    throw new InvalidArgument(`${field} is not valid JSON`, { field, reason: err.message });
  }
}
