import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import {
  rotate,
  rotations,
  period,
  minimalRepeat,
  isRotationOf,
  rotationOffset,
  canonicalRotation,
  stepSequence,
  stepCycleLength,
  stepCoversRing,
} from '../../src/util/cycle.js';

describe('rotate', () => {
  it('moves the front to the back on a positive count', () => {
    expect(rotate([1, 2, 3, 4], 1)).toEqual([2, 3, 4, 1]);
    expect(rotate([1, 2, 3, 4], 3)).toEqual([4, 1, 2, 3]);
  });

  it('rotates the other way on a negative count', () => {
    expect(rotate([1, 2, 3, 4], -1)).toEqual([4, 1, 2, 3]);
  });

  it('wraps counts larger than the length', () => {
    expect(rotate([1, 2, 3], 4)).toEqual([2, 3, 1]);
    expect(rotate([1, 2, 3], 0)).toEqual([1, 2, 3]);
    expect(rotate([], 5)).toEqual([]);
  });

  it('leaves the input alone', () => {
    const seq = [1, 2, 3];
    rotate(seq, 2);
    expect(seq).toEqual([1, 2, 3]);
  });
});

describe('rotations', () => {
  it('lists one entry per position for a sequence with no internal repeat', () => {
    expect(rotations([1, 2, 3])).toEqual([
      [1, 2, 3],
      [2, 3, 1],
      [3, 1, 2],
    ]);
  });

  it('drops the duplicates a repeating sequence produces', () => {
    expect(rotations([1, 0, 1, 0])).toEqual([
      [1, 0, 1, 0],
      [0, 1, 0, 1],
    ]);
    expect(rotations([1, 1, 1])).toEqual([[1, 1, 1]]);
  });
});

describe('period', () => {
  it('finds the shortest block that rebuilds the sequence', () => {
    expect(period([1, 0, 1, 0, 1, 0])).toBe(2);
    expect(period([1, 2, 3, 1, 2, 3])).toBe(3);
    expect(period([1, 1, 1, 1])).toBe(1);
  });

  it('is the whole length when nothing shorter works', () => {
    expect(period([1, 2, 3, 4])).toBe(4);
    expect(period([1, 0, 1, 0, 1])).toBe(5);
  });

  it('is zero for an empty sequence', () => {
    expect(period([])).toBe(0);
  });

  it('minimalRepeat cuts the sequence to one period', () => {
    expect(minimalRepeat([1, 0, 1, 0, 1, 0])).toEqual([1, 0]);
    expect(minimalRepeat([2, 4, 3])).toEqual([2, 4, 3]);
  });
});

describe('isRotationOf', () => {
  it('sees the same chain cut at a different lag', () => {
    expect(isRotationOf([1, 2, 3, 4], [3, 4, 1, 2])).toBe(true);
    expect(isRotationOf([1, 2, 3, 4], [1, 2, 3, 4])).toBe(true);
  });

  it('is false for a reversal or a different length', () => {
    expect(isRotationOf([1, 2, 3], [3, 2, 1])).toBe(false);
    expect(isRotationOf([1, 2, 3], [1, 2])).toBe(false);
  });

  it('treats two empty sequences as rotations of each other', () => {
    expect(isRotationOf([], [])).toBe(true);
  });
});

describe('rotationOffset', () => {
  it('reports how far the second chain has been advanced', () => {
    expect(rotationOffset([1, 2, 3, 4], [3, 4, 1, 2])).toBe(2);
    expect(rotationOffset([1, 2, 3], [1, 2, 3])).toBe(0);
  });

  it('is -1 when no rotation matches', () => {
    expect(rotationOffset([1, 2, 3], [3, 2, 1])).toBe(-1);
    expect(rotationOffset([1, 2], [1, 2, 3])).toBe(-1);
  });

  it('reports the smallest offset for a repeating chain', () => {
    expect(rotationOffset([1, 0, 1, 0], [0, 1, 0, 1])).toBe(1);
  });
});

describe('canonicalRotation', () => {
  it('gives every rotation of the same chain one key', () => {
    const base = canonicalRotation([3, 1, 2]);
    expect(canonicalRotation([1, 2, 3])).toEqual(base);
    expect(canonicalRotation([2, 3, 1])).toEqual(base);
  });

  it('separates chains that are not rotations', () => {
    expect(canonicalRotation([1, 2, 3])).not.toEqual(canonicalRotation([1, 3, 2]));
  });

  it('handles the empty sequence', () => {
    expect(canonicalRotation([])).toEqual([]);
  });
});

describe('stepSequence', () => {
  it('walks the ring by the move number, which is a satin pick order', () => {
    expect(stepSequence(5, 2)).toEqual([0, 2, 4, 1, 3]);
    expect(stepSequence(5, 3)).toEqual([0, 3, 1, 4, 2]);
    expect(stepSequence(8, 3)).toEqual([0, 3, 6, 1, 4, 7, 2, 5]);
  });

  it('takes a count shorter or longer than the ring', () => {
    expect(stepSequence(5, 2, 3)).toEqual([0, 2, 4]);
    expect(stepSequence(4, 1, 6)).toEqual([0, 1, 2, 3, 0, 1]);
  });

  it('handles a negative step by wrapping', () => {
    expect(stepSequence(5, -1)).toEqual([0, 4, 3, 2, 1]);
  });
});

describe('stepCycleLength and stepCoversRing', () => {
  it('a coprime step visits every position', () => {
    expect(stepCycleLength(5, 2)).toBe(5);
    expect(stepCoversRing(5, 2)).toBe(true);
    expect(stepCoversRing(8, 3)).toBe(true);
  });

  it('a step sharing a factor closes early', () => {
    expect(stepCycleLength(8, 2)).toBe(4);
    expect(stepCoversRing(8, 2)).toBe(false);
    expect(stepCycleLength(6, 3)).toBe(2);
  });

  it('a step of zero never moves', () => {
    expect(stepCycleLength(7, 0)).toBe(1);
    expect(stepCoversRing(7, 0)).toBe(false);
  });

  it('needs a positive ring size', () => {
    expect(() => stepCycleLength(0, 1)).toThrow(InvalidArgument);
  });
});
