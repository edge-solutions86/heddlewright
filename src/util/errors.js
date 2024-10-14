/**
 * Error types shared by every layer of heddlewright.
 *
 * Everything thrown out of this package is one of these, carries a stable
 * `code`, and keeps whatever structured detail the caller needs to report the
 * problem back to a weaver without re-parsing a message string.
 */

export class HeddleError extends Error {
  constructor(message, code, detail = {}) {
    super(message);
    this.name = 'HeddleError';
    this.code = code;
    this.detail = detail;
  }

  toJSON() {
    return { name: this.name, code: this.code, message: this.message, detail: this.detail };
  }
}

/** A value handed in by a caller is the wrong shape, type or range. */
export class InvalidArgument extends HeddleError {
  constructor(message, detail = {}) {
    super(message, 'invalid_argument', detail);
    this.name = 'InvalidArgument';
  }
}

/** A draft, tie-up or card deck is internally inconsistent. */
export class DraftError extends HeddleError {
  constructor(message, detail = {}) {
    super(message, 'draft_error', detail);
    this.name = 'DraftError';
  }
}

/** A loom, machine or harness cannot physically do what was asked of it. */
export class CapacityError extends HeddleError {
  constructor(message, detail = {}) {
    super(message, 'capacity_error', detail);
    this.name = 'CapacityError';
  }
}

/** A record was looked up and is not there. */
export class NotFound extends HeddleError {
  constructor(message, detail = {}) {
    super(message, 'not_found', detail);
    this.name = 'NotFound';
  }
}

/** A write would break an invariant the store guarantees. */
export class ConflictError extends HeddleError {
  constructor(message, detail = {}) {
    super(message, 'conflict', detail);
    this.name = 'ConflictError';
  }
}

const CODES = new Map([
  ['invalid_argument', InvalidArgument],
  ['draft_error', DraftError],
  ['capacity_error', CapacityError],
  ['not_found', NotFound],
  ['conflict', ConflictError],
]);

/** Rebuild an error from its `toJSON` form, for transport across the service edge. */
export function reviveError(plain) {
  if (plain === null || typeof plain !== 'object') {
    throw new InvalidArgument('reviveError needs an object', { got: typeof plain });
  }
  const Ctor = CODES.get(plain.code) ?? HeddleError;
  if (Ctor === HeddleError) {
    return new HeddleError(String(plain.message ?? ''), String(plain.code ?? 'unknown'), {
      ...(plain.detail ?? {}),
    });
  }
  return new Ctor(String(plain.message ?? ''), { ...(plain.detail ?? {}) });
}

/** True when `value` is one of our errors, optionally of a particular code. */
export function isHeddleError(value, code) {
  if (!(value instanceof HeddleError)) return false;
  return code === undefined || value.code === code;
}

/** The set of codes `reviveError` knows how to reconstruct exactly. */
export function knownErrorCodes() {
  return [...CODES.keys()].sort();
}
