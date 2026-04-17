import { describe, it, expect } from 'vitest';
import { CapacityError, DraftError, InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix, formatMatrix, dimensions } from '../../src/util/matrix.js';
import {
  PLACEMENTS,
  scaleDesign,
  largestScale,
  placeDesign,
  repeatDown,
  fitDesign,
  hookUtilisation,
  repeatsInLength,
  assertCentred,
} from '../../src/jacquard/design.js';

const FIGURE = parseMatrix('X.\n.X');

describe('scaleDesign', () => {
  it('repeats each cell by whole factors', () => {
    expect(formatMatrix(scaleDesign(FIGURE, { across: 2 }))).toBe('XX..\nXX..\n..XX\n..XX');
  });

  it('scales the two directions separately', () => {
    expect(dimensions(scaleDesign(FIGURE, { across: 3, down: 1 }))).toEqual({ rows: 2, cols: 6 });
  });

  it('leaves the design alone at a scale of one', () => {
    expect(formatMatrix(scaleDesign(FIGURE))).toBe(formatMatrix(FIGURE));
  });

  it('refuses a fractional scale, which would not land on whole cells', () => {
    expect(() => scaleDesign(FIGURE, { across: 1.5 })).toThrow(InvalidArgument);
  });
});

describe('largestScale', () => {
  it('is the whole number of times the design fits the hooks', () => {
    expect(largestScale(FIGURE, { hooks: 8 })).toBe(4);
    expect(largestScale(FIGURE, { hooks: 9 })).toBe(4);
  });

  it('refuses a design wider than the machine', () => {
    expect(() => largestScale(parseMatrix('XXXX'), { hooks: 2 })).toThrow(CapacityError);
  });
});

describe('placeDesign', () => {
  it('centres a design by default', () => {
    const placed = placeDesign(FIGURE, { hooks: 6 });
    expect(placed.left).toBe(2);
    expect(placed.right).toBe(2);
    expect(formatMatrix(placed.plan)).toBe('..X...\n...X..');
  });

  it('places left and right when told to', () => {
    expect(PLACEMENTS).toEqual(['left', 'centre', 'right']);
    expect(placeDesign(FIGURE, { hooks: 6, placement: 'left' }).left).toBe(0);
    expect(placeDesign(FIGURE, { hooks: 6, placement: 'right' }).left).toBe(4);
  });

  it('puts the odd hook on the right when the spare will not divide', () => {
    const placed = placeDesign(FIGURE, { hooks: 5 });
    expect(placed.left).toBe(1);
    expect(placed.right).toBe(2);
  });

  it('refuses a design wider than the machine', () => {
    expect(() => placeDesign(parseMatrix('XXXX'), { hooks: 2 })).toThrow(CapacityError);
  });
});

describe('repeatDown', () => {
  it('repeats the design to the pick count', () => {
    expect(dimensions(repeatDown(FIGURE, 5))).toEqual({ rows: 5, cols: 2 });
    expect(formatMatrix(repeatDown(FIGURE, 5))).toBe('X.\n.X\nX.\n.X\nX.');
  });

  it('gives each row its own array', () => {
    const repeated = repeatDown(FIGURE, 4);
    repeated[0][0] = 0;
    expect(repeated[2][0]).toBe(1);
  });
});

describe('fitDesign', () => {
  it('scales the figure as large as the hooks allow and centres it', () => {
    const fitted = fitDesign(FIGURE, { hooks: 9 });
    expect(fitted.scale).toBe(4);
    expect(fitted.designEnds).toBe(8);
    expect(fitted.spareHooks).toBe(1);
  });

  it('takes a scale the designer asked for instead', () => {
    const fitted = fitDesign(FIGURE, { hooks: 9, scale: 2 });
    expect(fitted.designEnds).toBe(4);
    expect(fitted.spareHooks).toBe(5);
  });

  it('repeats down when a pick count is given', () => {
    const fitted = fitDesign(FIGURE, { hooks: 8, picks: 20 });
    expect(dimensions(fitted.plan)).toEqual({ rows: 20, cols: 8 });
  });

  it('leaves the plan at the design height when no pick count is given', () => {
    expect(dimensions(fitDesign(FIGURE, { hooks: 8 }).plan).rows).toBe(8);
  });
});

describe('hookUtilisation', () => {
  it('is the share of the machine the figure takes', () => {
    const fitted = fitDesign(FIGURE, { hooks: 10, scale: 4 });
    expect(hookUtilisation(fitted, { hooks: 10 })).toBe(0.8);
  });

  it('is one when the figure fills the machine', () => {
    const fitted = fitDesign(FIGURE, { hooks: 8 });
    expect(hookUtilisation(fitted, { hooks: 8 })).toBe(1);
  });
});

describe('repeatsInLength', () => {
  it('counts the whole repeats and what is left', () => {
    const fitted = fitDesign(FIGURE, { hooks: 8 });
    expect(repeatsInLength(fitted, { picks: 20 })).toEqual({
      repeats: 2,
      remainder: 4,
      whole: false,
    });
  });

  it('reads a length that divides exactly as whole', () => {
    const fitted = fitDesign(FIGURE, { hooks: 8 });
    expect(repeatsInLength(fitted, { picks: 16 }).whole).toBe(true);
  });
});

describe('assertCentred', () => {
  it('passes a figure centred on the machine', () => {
    const fitted = fitDesign(FIGURE, { hooks: 9, scale: 2 });
    expect(assertCentred(fitted)).toBe(fitted);
  });

  it('refuses one pushed to the side', () => {
    const fitted = fitDesign(FIGURE, { hooks: 10, scale: 2, placement: 'left' });
    expect(() => assertCentred(fitted)).toThrow(DraftError);
  });
});
