import { InvalidArgument } from './errors.js';

/**
 * Argument checks. Every one of these either returns the value it was given, so
 * it can sit inline in a constructor, or throws InvalidArgument naming the
 * field and what it actually got.
 */

export function required(value, field) {
  if (value === undefined || value === null) {
    throw new InvalidArgument(`${field} is required`, {
      field,
      got: value === null ? 'null' : 'undefined',
    });
  }
  return value;
}

export function isInteger(value) {
  return typeof value === 'number' && Number.isInteger(value);
}

export function integer(value, field, { min, max } = {}) {
  if (!isInteger(value)) {
    throw new InvalidArgument(`${field} must be an integer`, { field, got: describe(value) });
  }
  if (min !== undefined && value < min) {
    throw new InvalidArgument(`${field} must be at least ${min}`, { field, got: value, min });
  }
  if (max !== undefined && value > max) {
    throw new InvalidArgument(`${field} must be at most ${max}`, { field, got: value, max });
  }
  return value;
}

export function positiveInteger(value, field) {
  return integer(value, field, { min: 1 });
}

export function number(value, field, { min, max } = {}) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new InvalidArgument(`${field} must be a finite number`, { field, got: describe(value) });
  }
  if (min !== undefined && value < min) {
    throw new InvalidArgument(`${field} must be at least ${min}`, { field, got: value, min });
  }
  if (max !== undefined && value > max) {
    throw new InvalidArgument(`${field} must be at most ${max}`, { field, got: value, max });
  }
  return value;
}

export function positiveNumber(value, field) {
  const n = number(value, field);
  if (n <= 0) {
    throw new InvalidArgument(`${field} must be greater than zero`, { field, got: n });
  }
  return n;
}

export function nonEmptyString(value, field) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new InvalidArgument(`${field} must be a non-empty string`, {
      field,
      got: describe(value),
    });
  }
  return value;
}

export function oneOf(value, field, allowed) {
  const list = [...allowed];
  if (!list.includes(value)) {
    throw new InvalidArgument(`${field} must be one of ${list.join(', ')}`, {
      field,
      got: describe(value),
      allowed: list,
    });
  }
  return value;
}

export function array(value, field, { min = 0, max } = {}) {
  if (!Array.isArray(value)) {
    throw new InvalidArgument(`${field} must be an array`, { field, got: describe(value) });
  }
  if (value.length < min) {
    throw new InvalidArgument(`${field} needs at least ${min} entries`, {
      field,
      got: value.length,
      min,
    });
  }
  if (max !== undefined && value.length > max) {
    throw new InvalidArgument(`${field} takes at most ${max} entries`, {
      field,
      got: value.length,
      max,
    });
  }
  return value;
}

export function plainObject(value, field) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new InvalidArgument(`${field} must be an object`, { field, got: describe(value) });
  }
  return value;
}

/** A short, safe description of any value, for error detail. */
export function describe(value) {
  if (value === null) return 'null';
  if (Array.isArray(value)) return `array(${value.length})`;
  const t = typeof value;
  if (t === 'string') return value.length > 24 ? `string(${value.length})` : `"${value}"`;
  if (t === 'number' || t === 'boolean' || t === 'undefined') return String(value);
  return t;
}
