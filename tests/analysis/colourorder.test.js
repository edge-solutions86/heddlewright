import { describe, it, expect } from 'vitest';
import { InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix } from '../../src/util/matrix.js';
import { plainWeave } from '../../src/weave/plain.js';
import {
  colourGrid,
  effectRepeat,
  expandStripes,
  colourCounts,
  renderColourGrid,
  houndstooth,
  stepPattern,
  isBalancedEffect,
} from '../../src/analysis/colourorder.js';

describe('colourGrid', () => {
  it('takes the warp colour where the warp is up and the weft colour elsewhere', () => {
    const grid = colourGrid(plainWeave(), ['red'], ['blue']);
    expect(grid).toEqual([
      ['red', 'blue'],
      ['blue', 'red'],
    ]);
  });

  it('repeats a short colour order across the cloth', () => {
    const grid = colourGrid(parseMatrix('XXXX'), ['a', 'b'], ['c']);
    expect(grid[0]).toEqual(['a', 'b', 'a', 'b']);
  });

  it('shows nothing but the warp colours when the warp is always up', () => {
    const grid = colourGrid(parseMatrix('XX\nXX'), ['a', 'b'], ['c']);
    expect(colourCounts(grid).map((entry) => entry.colour)).toEqual(['a', 'b']);
  });
});

describe('effectRepeat', () => {
  it('is the least common multiple of weave and colour order', () => {
    expect(effectRepeat({ weavePicks: 4, weaveEnds: 4, warpOrder: 8, weftOrder: 8 })).toEqual({
      picks: 8,
      ends: 8,
    });
  });

  it('grows when the two do not divide', () => {
    expect(effectRepeat({ weavePicks: 4, weaveEnds: 4, warpOrder: 6, weftOrder: 6 })).toEqual({
      picks: 12,
      ends: 12,
    });
  });

  it('refuses a repeat of nothing', () => {
    expect(() => effectRepeat({ weavePicks: 0, weaveEnds: 4, warpOrder: 4, weftOrder: 4 })).toThrow(
      InvalidArgument,
    );
  });
});

describe('expandStripes', () => {
  it('runs a stripe list out one thread at a time', () => {
    expect(
      expandStripes([
        { colour: 'a', count: 2 },
        { colour: 'b', count: 1 },
      ]),
    ).toEqual(['a', 'a', 'b']);
  });

  it('refuses a stripe with no threads', () => {
    expect(() => expandStripes([{ colour: 'a', count: 0 }])).toThrow(InvalidArgument);
  });
});

describe('houndstooth', () => {
  it('draws the broken check that neither the weave nor the colour order shows', () => {
    expect(renderColourGrid(houndstooth(), { dark: '#', light: '.' })).toBe(
      [
        '####..##',
        '#####..#',
        '######..',
        '####.##.',
        '##......',
        '.##.....',
        '..##....',
        '#..#....',
      ].join('\n'),
    );
  });

  it('is balanced between the two colours', () => {
    expect(colourCounts(houndstooth())).toEqual([
      { colour: 'dark', cells: 32 },
      { colour: 'light', cells: 32 },
    ]);
    expect(isBalancedEffect(houndstooth())).toBe(true);
  });

  it('takes the colour names it is given', () => {
    const grid = houndstooth({ dark: 'navy', light: 'oat' });
    expect(
      colourCounts(grid)
        .map((entry) => entry.colour)
        .sort(),
    ).toEqual(['navy', 'oat']);
  });

  it('is not the same cloth as the weave it came from', () => {
    const plain = renderColourGrid(colourGrid(plainWeave(), ['dark'], ['light']), {
      dark: '#',
      light: '.',
    });
    expect(renderColourGrid(houndstooth(), { dark: '#', light: '.' })).not.toBe(plain);
  });
});

describe('stepPattern', () => {
  it('reads as fine lines rather than a check', () => {
    expect(renderColourGrid(stepPattern(), { dark: '#', light: '.' })).toBe(
      [
        '#.###.##',
        '..#...#.',
        '###.###.',
        '#...#...',
        '#.###.##',
        '..#...#.',
        '###.###.',
        '#...#...',
      ].join('\n'),
    );
  });

  it('repeats every four picks, unlike the houndstooth', () => {
    const grid = stepPattern();
    expect(grid[0]).toEqual(grid[4]);
    expect(houndstooth()[0]).not.toEqual(houndstooth()[4]);
  });
});

describe('renderColourGrid', () => {
  it('picks its own symbols when none are given', () => {
    const text = renderColourGrid(houndstooth());
    expect(new Set(text.replace(/\n/g, '')).size).toBe(2);
  });

  it('writes a question mark for a colour it has no symbol for', () => {
    expect(renderColourGrid([['a']], { b: '#' })).toBe('?');
  });
});

describe('isBalancedEffect', () => {
  it('is false when one colour dominates', () => {
    expect(isBalancedEffect([['a', 'a', 'b']])).toBe(false);
  });

  it('is false when there are not exactly two colours', () => {
    expect(isBalancedEffect([['a', 'b', 'c']])).toBe(false);
    expect(isBalancedEffect([['a', 'a']])).toBe(false);
  });
});
