import { describe, it, expect } from 'vitest';
import { CapacityError, NotFound, InvalidArgument } from '../../src/util/errors.js';
import {
  LIFT_TYPES,
  listMachines,
  machineByName,
  smallestMachineFor,
  defineMachine,
  hookPosition,
  hookAt,
  repeatsAcross,
  assertFits,
  cardsPerCycle,
} from '../../src/jacquard/machine.js';

describe('listMachines', () => {
  it('lists them smallest first', () => {
    const hooks = listMachines().map((machine) => machine.hooks);
    expect(hooks).toEqual([...hooks].sort((a, b) => a - b));
  });

  it('every machine has a layout that multiplies out to its hook count', () => {
    for (const machine of listMachines()) {
      expect(machine.rows * machine.columns).toBe(machine.hooks);
    }
  });

  it('hands back frozen records', () => {
    expect(Object.isFrozen(listMachines()[0])).toBe(true);
  });
});

describe('machineByName', () => {
  it('finds a machine and reports its layout', () => {
    expect(machineByName('400 hook')).toMatchObject({ hooks: 400, rows: 8, columns: 50 });
  });

  it('names what it does know when it cannot find one', () => {
    try {
      machineByName('900 hook');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(NotFound);
      expect(err.detail.known).toContain('400 hook');
    }
  });
});

describe('smallestMachineFor', () => {
  it('picks the first machine large enough', () => {
    expect(smallestMachineFor(390).hooks).toBe(400);
    expect(smallestMachineFor(400).hooks).toBe(400);
    expect(smallestMachineFor(401).hooks).toBe(600);
  });

  it('refuses a repeat past the largest machine', () => {
    expect(() => smallestMachineFor(5000)).toThrow(CapacityError);
  });
});

describe('defineMachine', () => {
  it('works the hook count out from the board', () => {
    const machine = defineMachine({ name: 'shed spare', rows: 6, columns: 30 });
    expect(machine.hooks).toBe(180);
    expect(machine.lift).toBe('single');
  });

  it('names the two lifts and refuses anything else', () => {
    expect(LIFT_TYPES).toEqual(['single', 'double']);
    expect(() => defineMachine({ name: 'x', rows: 2, columns: 2, lift: 'triple' })).toThrow(
      InvalidArgument,
    );
  });
});

describe('hookPosition and hookAt', () => {
  const machine = machineByName('400 hook');

  it('reads along the rows of the board', () => {
    expect(hookPosition(machine, 1)).toEqual({ row: 1, column: 1 });
    expect(hookPosition(machine, 50)).toEqual({ row: 1, column: 50 });
    expect(hookPosition(machine, 51)).toEqual({ row: 2, column: 1 });
    expect(hookPosition(machine, 400)).toEqual({ row: 8, column: 50 });
  });

  it('round-trips every hook through its position', () => {
    for (const hook of [1, 7, 50, 51, 231, 400]) {
      const { row, column } = hookPosition(machine, hook);
      expect(hookAt(machine, row, column)).toBe(hook);
    }
  });

  it('refuses a hook past the machine and a position off the board', () => {
    expect(() => hookPosition(machine, 401)).toThrow(CapacityError);
    expect(() => hookAt(machine, 9, 1)).toThrow(CapacityError);
    expect(() => hookAt(machine, 1, 51)).toThrow(CapacityError);
  });
});

describe('repeatsAcross', () => {
  const machine = machineByName('400 hook');

  it('counts whole repeats and what is left over', () => {
    expect(repeatsAcross(machine, { totalEnds: 1600, hooksPerRepeat: 400 })).toEqual({
      repeats: 4,
      remainder: 0,
      hooksIdle: 0,
    });
  });

  it('reports the ends that do not fill a repeat', () => {
    expect(repeatsAcross(machine, { totalEnds: 1000, hooksPerRepeat: 300 })).toEqual({
      repeats: 3,
      remainder: 100,
      hooksIdle: 100,
    });
  });

  it('refuses a repeat wider than the machine', () => {
    expect(() => repeatsAcross(machine, { totalEnds: 1600, hooksPerRepeat: 500 })).toThrow(
      CapacityError,
    );
  });
});

describe('assertFits', () => {
  it('passes a repeat that fits', () => {
    const machine = machineByName('600 hook');
    expect(assertFits(machine, 600)).toBe(machine);
  });

  it('says how many hooks short it is', () => {
    try {
      assertFits(machineByName('400 hook'), 460);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.detail).toMatchObject({ hooks: 400, hooksNeeded: 460, short: 60 });
    }
  });
});

describe('cardsPerCycle', () => {
  it('is one on a single lift and two on a double', () => {
    expect(cardsPerCycle(machineByName('400 hook'))).toBe(1);
    expect(cardsPerCycle(machineByName('600 hook'))).toBe(2);
  });
});
