import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  SHEDDING,
  PICKING,
  createLoom,
  loomByReference,
  styleRequirements,
  shortfalls,
  canWeave,
  assertCanWeave,
  loomsFor,
  widthUtilisation,
  byShedding,
} from '../../src/loom/registry.js';

const DOBBY = createLoom({
  reference: 'L1',
  shedding: 'dobby',
  reedWidthCm: 190,
  shafts: 16,
  treadles: 0,
  picksMinute: 220,
  shuttleBoxes: 4,
});

const JACQUARD = createLoom({
  reference: 'L2',
  shedding: 'jacquard',
  picking: 'rapier',
  reedWidthCm: 260,
  hooks: 1344,
  picksMinute: 180,
});

const TAPPET = createLoom({
  reference: 'L3',
  shedding: 'tappet',
  reedWidthCm: 120,
  shafts: 4,
  picksMinute: 300,
});

const REGISTER = [DOBBY, JACQUARD, TAPPET];

describe('createLoom', () => {
  it('records the machine and freezes it', () => {
    expect(DOBBY.shafts).toBe(16);
    expect(DOBBY.hooks).toBe(0);
    expect(Object.isFrozen(DOBBY)).toBe(true);
  });

  it('counts hooks instead of shafts on a jacquard', () => {
    expect(JACQUARD.hooks).toBe(1344);
    expect(JACQUARD.shafts).toBe(0);
  });

  it('defaults the picking motion and the box count', () => {
    expect(TAPPET.picking).toBe('shuttle');
    expect(TAPPET.shuttleBoxes).toBe(1);
  });

  it('names the motions it knows and refuses others', () => {
    expect(SHEDDING).toEqual(['tappet', 'dobby', 'jacquard']);
    expect(PICKING).toContain('air jet');
    expect(() => createLoom({ ...DOBBY, shedding: 'lever' })).toThrow(InvalidArgument);
  });

  it('refuses a shaft loom with no shafts', () => {
    expect(() =>
      createLoom({ reference: 'x', shedding: 'dobby', reedWidthCm: 100, picksMinute: 200 }),
    ).toThrow(InvalidArgument);
  });
});

describe('loomByReference', () => {
  it('finds a loom and refuses one that is not registered', () => {
    expect(loomByReference(REGISTER, 'L2')).toBe(JACQUARD);
    expect(() => loomByReference(REGISTER, 'L9')).toThrow(NotFound);
  });
});

describe('shortfalls', () => {
  it('is empty when the loom can take the style', () => {
    expect(shortfalls(DOBBY, styleRequirements({ widthCm: 150, shafts: 12 }))).toEqual([]);
  });

  it('names a loom that is too narrow', () => {
    const problems = shortfalls(TAPPET, styleRequirements({ widthCm: 150, shafts: 4 }));
    expect(problems.map((problem) => problem.code)).toEqual(['too_narrow']);
    expect(problems[0].detail).toEqual({ needed: 150, available: 120 });
  });

  it('names every reason at once rather than stopping at the first', () => {
    const problems = shortfalls(
      TAPPET,
      styleRequirements({ widthCm: 200, shafts: 12, weftColours: 3 }),
    );
    expect(problems.map((problem) => problem.code).sort()).toEqual([
      'too_few_boxes',
      'too_few_shafts',
      'too_narrow',
    ]);
  });

  it('refuses a jacquard style on a shaft loom', () => {
    const problems = shortfalls(DOBBY, styleRequirements({ widthCm: 150, hooks: 400 }));
    expect(problems.map((problem) => problem.code)).toContain('not_jacquard');
  });

  it('counts hooks rather than shafts on a jacquard', () => {
    expect(shortfalls(JACQUARD, styleRequirements({ widthCm: 250, hooks: 1344 }))).toEqual([]);
    expect(shortfalls(JACQUARD, styleRequirements({ widthCm: 250, hooks: 2688 }))[0].code).toBe(
      'too_few_hooks',
    );
  });
});

describe('canWeave and assertCanWeave', () => {
  it('answers the same question two ways', () => {
    const style = styleRequirements({ widthCm: 150, shafts: 12 });
    expect(canWeave(DOBBY, style)).toBe(true);
    expect(assertCanWeave(DOBBY, style)).toBe(DOBBY);
  });

  it('throws with every reason attached', () => {
    try {
      assertCanWeave(TAPPET, styleRequirements({ widthCm: 200, shafts: 12 }));
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail.problems.length).toBe(2);
    }
  });
});

describe('loomsFor', () => {
  it('lists the looms that can take a style, fastest first', () => {
    const found = loomsFor(REGISTER, styleRequirements({ widthCm: 100, shafts: 4 }));
    expect(found.map((loom) => loom.reference)).toEqual(['L3', 'L1', 'L2']);
  });

  it('lets the jacquard take a shaft style, because every end is its own hook', () => {
    expect(canWeave(JACQUARD, styleRequirements({ widthCm: 100, shafts: 16 }))).toBe(true);
    expect(canWeave(TAPPET, styleRequirements({ widthCm: 100, shafts: 16 }))).toBe(false);
  });

  it('is empty when nothing on the floor will do', () => {
    expect(loomsFor(REGISTER, styleRequirements({ widthCm: 400, shafts: 4 }))).toEqual([]);
  });

  it('finds the jacquard for a jacquard style and nothing else', () => {
    const found = loomsFor(REGISTER, styleRequirements({ widthCm: 200, hooks: 400 }));
    expect(found.map((loom) => loom.reference)).toEqual(['L2']);
  });
});

describe('widthUtilisation', () => {
  it('is the share of the reed the style uses', () => {
    expect(widthUtilisation(DOBBY, styleRequirements({ widthCm: 95 }))).toBe(0.5);
  });

  it('reads low when a narrow style goes on a wide loom', () => {
    expect(widthUtilisation(JACQUARD, styleRequirements({ widthCm: 65 }))).toBe(0.25);
  });
});

describe('byShedding', () => {
  it('groups the register, listing every motion even when empty', () => {
    expect(byShedding([DOBBY, TAPPET])).toEqual([
      { shedding: 'tappet', references: ['L3'] },
      { shedding: 'dobby', references: ['L1'] },
      { shedding: 'jacquard', references: [] },
    ]);
  });
});
