import { describe, it, expect } from 'vitest';
import { InvalidArgument, NotFound } from '../../src/util/errors.js';
import {
  listProcesses,
  processByName,
  routeEffect,
  finishedPiece,
  greigeNeeded,
  meetsWidth,
  assertRouteKnown,
  naiveRouteShrink,
  assertMeetsWidth,
} from '../../src/cloth/finishing.js';

const ROUTE = ['scour', 'bleach', 'dye', 'sanforise'];
const GREIGE = { metres: 100, widthCm: 160, gsm: 150 };

describe('listProcesses', () => {
  it('lists them in name order with their effects', () => {
    const names = listProcesses().map((process) => process.name);
    expect(names).toEqual([...names].sort());
    expect(processByName('sanforise').lengthShrink).toBe(3);
  });

  it('has one process that widens rather than shrinks', () => {
    expect(processByName('calender').widthShrink).toBeLessThan(0);
  });

  it('names what it knows when it does not know one', () => {
    try {
      processByName('starch');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(NotFound);
      expect(err.detail.known).toContain('dye');
    }
  });
});

describe('routeEffect', () => {
  it('compounds the ratios rather than adding the percentages', () => {
    const effect = routeEffect(ROUTE);
    const naive = naiveRouteShrink(ROUTE);
    expect(effect.lengthShrinkPercent).toBeLessThan(naive.lengthShrinkPercent);
    expect(effect.lengthShrinkPercent).toBeGreaterThan(naive.lengthShrinkPercent - 1);
  });

  it('multiplies the ratios stage by stage', () => {
    const wanted = 0.985 * 0.99 * 0.99 * 0.97;
    expect(routeEffect(ROUTE).lengthRatio).toBeCloseTo(wanted, 6);
  });

  it('is the process itself for a route of one', () => {
    expect(routeEffect(['sanforise']).lengthRatio).toBeCloseTo(0.97, 6);
  });

  it('lets a widening process pull the width back out', () => {
    expect(routeEffect(['calender']).widthRatio).toBeGreaterThan(1);
  });
});

describe('finishedPiece', () => {
  it('shrinks the length and the width', () => {
    const finished = finishedPiece(GREIGE, ROUTE);
    expect(finished.metres).toBeLessThan(100);
    expect(finished.widthCm).toBeLessThan(160);
  });

  it('raises the weight per square metre as the cloth shrinks into itself', () => {
    expect(finishedPiece(GREIGE, ['sanforise']).gsm).toBeGreaterThan(150);
  });

  it('leaves the weight out when none was given', () => {
    expect(finishedPiece({ metres: 100, widthCm: 160 }, ROUTE).gsm).toBeUndefined();
  });

  it('refuses a piece of no length', () => {
    expect(() => finishedPiece({ metres: 0, widthCm: 160 }, ROUTE)).toThrow(InvalidArgument);
  });
});

describe('greigeNeeded', () => {
  it('works backwards from the finished piece', () => {
    const needed = greigeNeeded({ metres: 100, widthCm: 150 }, ROUTE);
    expect(needed.metres).toBeGreaterThan(100);
    expect(needed.widthCm).toBeGreaterThan(150);
  });

  it('is the inverse of finishing', () => {
    const needed = greigeNeeded({ metres: 100, widthCm: 150 }, ROUTE);
    const finished = finishedPiece(needed, ROUTE);
    expect(finished.metres).toBeCloseTo(100, 2);
    expect(finished.widthCm).toBeCloseTo(150, 2);
  });
});

describe('meetsWidth', () => {
  it('passes a greige wide enough to finish on contract', () => {
    const reading = meetsWidth(GREIGE, ROUTE, { contractWidthCm: 150 });
    expect(reading.meets).toBe(true);
    expect(reading.shortfallCm).toBeLessThan(0);
  });

  it('fails one that finishes too narrow', () => {
    const reading = meetsWidth({ metres: 100, widthCm: 150 }, ROUTE, { contractWidthCm: 150 });
    expect(reading.meets).toBe(false);
    expect(reading.shortfallCm).toBeGreaterThan(0);
  });

  it('lets a small shortfall through on the tolerance', () => {
    const narrow = { metres: 100, widthCm: 156 };
    expect(meetsWidth(narrow, ROUTE, { contractWidthCm: 150 }).meets).toBe(true);
    expect(meetsWidth(narrow, ROUTE, { contractWidthCm: 150, tolerancePercent: 0 }).meets).toBe(
      false,
    );
  });

  it('refuses a route that will not make the contract', () => {
    const narrow = { metres: 100, widthCm: 150 };
    expect(() => assertMeetsWidth(narrow, ROUTE, { contractWidthCm: 150 })).toThrow(
      /narrower than the contract/,
    );
    expect(assertMeetsWidth(GREIGE, ROUTE, { contractWidthCm: 150 }).meets).toBe(true);
  });
});

describe('assertRouteKnown', () => {
  it('passes a route of known processes', () => {
    expect(assertRouteKnown(ROUTE)).toBe(ROUTE);
  });

  it('refuses one with a process nobody has', () => {
    expect(() => assertRouteKnown(['scour', 'starch'])).toThrow(NotFound);
    expect(() => assertRouteKnown([])).toThrow(InvalidArgument);
  });
});
