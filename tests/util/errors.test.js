import { describe, it, expect } from 'vitest';
import {
  HeddleError,
  InvalidArgument,
  DraftError,
  CapacityError,
  NotFound,
  ConflictError,
  reviveError,
  isHeddleError,
  knownErrorCodes,
} from '../../src/util/errors.js';

describe('error hierarchy', () => {
  it('every subclass is a HeddleError and an Error', () => {
    for (const err of [
      new InvalidArgument('a'),
      new DraftError('b'),
      new CapacityError('c'),
      new NotFound('d'),
      new ConflictError('e'),
    ]) {
      expect(err).toBeInstanceOf(HeddleError);
      expect(err).toBeInstanceOf(Error);
    }
  });

  it('carries the code the subclass fixes', () => {
    expect(new InvalidArgument('x').code).toBe('invalid_argument');
    expect(new DraftError('x').code).toBe('draft_error');
    expect(new CapacityError('x').code).toBe('capacity_error');
    expect(new NotFound('x').code).toBe('not_found');
    expect(new ConflictError('x').code).toBe('conflict');
  });

  it('names itself after the subclass, not after Error', () => {
    expect(new CapacityError('x').name).toBe('CapacityError');
    expect(new HeddleError('x', 'zzz').name).toBe('HeddleError');
  });

  it('keeps structured detail rather than folding it into the message', () => {
    const err = new CapacityError('too many hooks', { asked: 1600, machine: 1344 });
    expect(err.detail).toEqual({ asked: 1600, machine: 1344 });
    expect(err.message).toBe('too many hooks');
  });

  it('detail defaults to an empty object, never undefined', () => {
    expect(new NotFound('gone').detail).toEqual({});
  });
});

describe('toJSON', () => {
  it('round-trips through JSON with the code and detail intact', () => {
    const err = new DraftError('shaft 9 is not threaded', { shaft: 9, shafts: 8 });
    const plain = JSON.parse(JSON.stringify(err));
    expect(plain).toEqual({
      name: 'DraftError',
      code: 'draft_error',
      message: 'shaft 9 is not threaded',
      detail: { shaft: 9, shafts: 8 },
    });
  });
});

describe('reviveError', () => {
  it('rebuilds the exact subclass for a known code', () => {
    const revived = reviveError({
      code: 'not_found',
      message: 'no such loom',
      detail: { id: 'L4' },
    });
    expect(revived).toBeInstanceOf(NotFound);
    expect(revived.detail).toEqual({ id: 'L4' });
  });

  it('falls back to HeddleError for a code it does not know', () => {
    const revived = reviveError({ code: 'something_new', message: 'later' });
    expect(revived).toBeInstanceOf(HeddleError);
    expect(revived).not.toBeInstanceOf(InvalidArgument);
    expect(revived.code).toBe('something_new');
  });

  it('does not share the detail object with the input', () => {
    const input = { code: 'conflict', message: 'clash', detail: { at: 3 } };
    const revived = reviveError(input);
    revived.detail.at = 99;
    expect(input.detail.at).toBe(3);
  });

  it('refuses a non-object', () => {
    expect(() => reviveError('not_found')).toThrow(InvalidArgument);
    expect(() => reviveError(null)).toThrow(InvalidArgument);
  });
});

describe('isHeddleError', () => {
  it('is false for a plain Error', () => {
    expect(isHeddleError(new Error('plain'))).toBe(false);
    expect(isHeddleError('not_found')).toBe(false);
  });

  it('narrows on code when one is given', () => {
    const err = new NotFound('gone');
    expect(isHeddleError(err)).toBe(true);
    expect(isHeddleError(err, 'not_found')).toBe(true);
    expect(isHeddleError(err, 'conflict')).toBe(false);
  });
});

describe('knownErrorCodes', () => {
  it('lists exactly the codes reviveError reconstructs, sorted', () => {
    const codes = knownErrorCodes();
    expect(codes).toEqual([
      'capacity_error',
      'conflict',
      'draft_error',
      'invalid_argument',
      'not_found',
    ]);
    for (const code of codes) {
      expect(reviveError({ code, message: 'x' }).code).toBe(code);
    }
  });
});
