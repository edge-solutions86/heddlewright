import { describe, it, expect } from 'vitest';
import { CapacityError, InvalidArgument } from '../../src/util/errors.js';
import {
  normaliseColourOrder,
  orderRepeat,
  expandColourOrder,
  endsByColour,
  creelPlan,
  assertCreelFits,
  stripeWidths,
  mirrorOrder,
  stripeSpans,
  yarnByColour,
} from '../../src/warp/creel.js';

const ORDER = [
  { colour: 'indigo', ends: 12 },
  { colour: 'ecru', ends: 4 },
];

describe('normaliseColourOrder', () => {
  it('keeps the stripes in order', () => {
    expect(normaliseColourOrder(ORDER)).toEqual(ORDER);
  });

  it('refuses a stripe with no colour or no ends', () => {
    expect(() => normaliseColourOrder([{ ends: 4 }])).toThrow(InvalidArgument);
    expect(() => normaliseColourOrder([{ colour: 'ecru', ends: 0 }])).toThrow(InvalidArgument);
    expect(() => normaliseColourOrder([])).toThrow(InvalidArgument);
  });
});

describe('orderRepeat', () => {
  it('adds the stripes up', () => {
    expect(orderRepeat(ORDER)).toBe(16);
  });
});

describe('expandColourOrder', () => {
  it('runs the stripes out across the warp', () => {
    const ends = expandColourOrder(ORDER, 20);
    expect(ends.slice(0, 12).every((colour) => colour === 'indigo')).toBe(true);
    expect(ends.slice(12, 16).every((colour) => colour === 'ecru')).toBe(true);
    expect(ends[16]).toBe('indigo');
  });

  it('cuts the last stripe short rather than overrunning', () => {
    expect(expandColourOrder(ORDER, 14).length).toBe(14);
    expect(expandColourOrder(ORDER, 14).at(-1)).toBe('ecru');
  });
});

describe('endsByColour', () => {
  it('counts the ends of each colour, most first', () => {
    expect(endsByColour(ORDER, 32)).toEqual([
      { colour: 'indigo', ends: 24 },
      { colour: 'ecru', ends: 8 },
    ]);
  });

  it('adds back to the warp width', () => {
    const counts = endsByColour(ORDER, 100);
    expect(counts.reduce((total, entry) => total + entry.ends, 0)).toBe(100);
  });
});

describe('creelPlan', () => {
  it('needs one package per end when the warp is warped in one pass', () => {
    const plan = creelPlan(ORDER, { totalEnds: 32 });
    expect(plan.packages).toBe(32);
    expect(plan.colours).toBe(2);
  });

  it('divides the packages between the sections', () => {
    const plan = creelPlan(ORDER, { totalEnds: 320, sections: 10 });
    expect(plan.packages).toBe(32);
  });

  it('rounds a colour up rather than leaving an end unsupplied', () => {
    const plan = creelPlan(ORDER, { totalEnds: 33, sections: 2 });
    const supplied = plan.positions.reduce((total, entry) => total + entry.packages * 2, 0);
    expect(supplied).toBeGreaterThanOrEqual(33);
  });
});

describe('assertCreelFits', () => {
  it('passes a plan the creel holds', () => {
    const plan = creelPlan(ORDER, { totalEnds: 32 });
    expect(assertCreelFits(plan, 400)).toBe(plan);
  });

  it('says how many packages are over', () => {
    const plan = creelPlan(ORDER, { totalEnds: 640 });
    try {
      assertCreelFits(plan, 400);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(CapacityError);
      expect(err.detail.over).toBe(240);
    }
  });
});

describe('stripeWidths', () => {
  it('turns the stripes into inches at the sett', () => {
    expect(stripeWidths(ORDER, 24)).toEqual([
      { colour: 'indigo', ends: 12, inches: 0.5 },
      { colour: 'ecru', ends: 4, inches: roundThree(4 / 24) },
    ]);
  });
});

describe('mirrorOrder', () => {
  it('mirrors without repeating the turning stripe', () => {
    const mirrored = mirrorOrder([
      { colour: 'a', ends: 4 },
      { colour: 'b', ends: 2 },
      { colour: 'c', ends: 6 },
    ]);
    expect(mirrored.map((stripe) => stripe.colour)).toEqual(['a', 'b', 'c', 'b', 'a']);
  });

  it('is symmetric about the middle', () => {
    const mirrored = mirrorOrder(ORDER);
    expect(mirrored.map((stripe) => stripe.colour)).toEqual(['indigo', 'ecru', 'indigo']);
  });

  it('refuses a single stripe, which has nothing to mirror', () => {
    expect(() => mirrorOrder([{ colour: 'a', ends: 4 }])).toThrow(InvalidArgument);
  });
});

describe('stripeSpans', () => {
  it('says where each stripe sits across the warp', () => {
    expect(stripeSpans(ORDER, 32)).toEqual([
      { colour: 'indigo', from: 1, to: 12, ends: 12 },
      { colour: 'ecru', from: 13, to: 16, ends: 4 },
      { colour: 'indigo', from: 17, to: 28, ends: 12 },
      { colour: 'ecru', from: 29, to: 32, ends: 4 },
    ]);
  });

  it('covers every end exactly once', () => {
    const spans = stripeSpans(ORDER, 50);
    expect(spans.reduce((total, span) => total + span.ends, 0)).toBe(50);
    expect(spans.at(-1).to).toBe(50);
  });
});

describe('yarnByColour', () => {
  it('works the weight out from the count in tex', () => {
    const yarn = yarnByColour(ORDER, {
      totalEnds: 32,
      warpLengthMetres: 1000,
      texByColour: { indigo: 30, ecru: 30 },
    });
    expect(yarn[0]).toEqual({ colour: 'indigo', ends: 24, metres: 24000, kilograms: 0.72 });
  });

  it('refuses a colour with no count given', () => {
    expect(() =>
      yarnByColour(ORDER, { totalEnds: 32, warpLengthMetres: 1000, texByColour: { indigo: 30 } }),
    ).toThrow(/texByColour.ecru/);
  });
});

function roundThree(value) {
  return Math.round(value * 1000) / 1000;
}
