import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { formatMatrix, parseMatrix, dimensions } from '../../src/util/matrix.js';
import { warpFaceRatio } from '../../src/draft/drawdown.js';
import { twillWeave } from '../../src/weave/twill.js';
import {
  profileBlocks,
  blockSizes,
  expandProfileOrder,
  substituteProfile,
  blockAt,
  blockSpans,
  hasAdjacentRepeats,
  profileMatrix,
} from '../../src/weave/blocks.js';

describe('profileBlocks', () => {
  it('lists the distinct blocks in the order they first appear', () => {
    expect(profileBlocks(['B', 'A', 'B', 'C'])).toEqual(['B', 'A', 'C']);
  });

  it('refuses an empty order and a block that is not a name', () => {
    expect(() => profileBlocks([])).toThrow(InvalidArgument);
    expect(() => profileBlocks(['A', ''])).toThrow(InvalidArgument);
  });
});

describe('blockSizes', () => {
  it('gives every block the same count from a number', () => {
    const sizes = blockSizes(['A', 'B'], 4);
    expect(sizes.get('A')).toBe(4);
    expect(sizes.get('B')).toBe(4);
  });

  it('takes a count per block', () => {
    const sizes = blockSizes(['A', 'B'], { A: 6, B: 2 });
    expect(sizes.get('A')).toBe(6);
    expect(sizes.get('B')).toBe(2);
  });

  it('refuses a block the count object does not mention', () => {
    expect(() => blockSizes(['A', 'B'], { A: 4 })).toThrow(/block B has no thread count/);
  });

  it('refuses a count of zero threads', () => {
    expect(() => blockSizes(['A'], 0)).toThrow(InvalidArgument);
    expect(() => blockSizes(['A'], { A: 0 })).toThrow(InvalidArgument);
  });
});

describe('expandProfileOrder', () => {
  it('repeats each block for its thread count', () => {
    expect(expandProfileOrder(['A', 'B'], 2)).toEqual(['A', 'A', 'B', 'B']);
  });

  it('follows uneven block sizes', () => {
    expect(expandProfileOrder(['A', 'B'], { A: 3, B: 1 })).toEqual(['A', 'A', 'A', 'B']);
  });
});

describe('substituteProfile', () => {
  const figure = parseMatrix('X.\n.X');
  const ground = parseMatrix('.X\nX.');

  it('uses the figure where a block meets itself and the ground elsewhere', () => {
    const cloth = substituteProfile(['A', 'B'], ['A', 'B'], { figure, ground, warpUnits: 2 });
    expect(dimensions(cloth)).toEqual({ rows: 4, cols: 4 });
    expect(formatMatrix(cloth)).toBe('X..X\n.XX.\n.XX.\nX..X');
  });

  it('takes different unit counts for warp and weft', () => {
    const cloth = substituteProfile(['A', 'B'], ['A', 'B'], {
      figure,
      ground,
      warpUnits: 3,
      weftUnits: 1,
    });
    expect(dimensions(cloth)).toEqual({ rows: 2, cols: 6 });
  });

  it('substitutes a real twill for the figure', () => {
    const cloth = substituteProfile(['A'], ['A'], {
      figure: twillWeave('3/1'),
      ground: twillWeave('1/3'),
      warpUnits: 4,
    });
    expect(warpFaceRatio(cloth)).toBe(0.75);
  });

  it('refuses a figure or ground that does not interlace', () => {
    expect(() =>
      substituteProfile(['A'], ['A'], { figure: parseMatrix('XX\nXX'), ground }),
    ).toThrow(DraftError);
  });
});

describe('blockAt and blockSpans', () => {
  it('finds the block a thread falls in', () => {
    expect(blockAt(['A', 'B'], 3, 1)).toBe('A');
    expect(blockAt(['A', 'B'], 3, 3)).toBe('A');
    expect(blockAt(['A', 'B'], 3, 4)).toBe('B');
  });

  it('refuses a thread past the end of the profile', () => {
    expect(() => blockAt(['A', 'B'], 3, 7)).toThrow(/past the end/);
  });

  it('reports the span each block covers', () => {
    expect(blockSpans(['A', 'B', 'A'], { A: 2, B: 3 })).toEqual([
      { block: 'A', from: 1, to: 2, threads: 2 },
      { block: 'B', from: 3, to: 5, threads: 3 },
      { block: 'A', from: 6, to: 7, threads: 2 },
    ]);
  });

  it('the spans cover every thread the expansion produces', () => {
    const spans = blockSpans(['A', 'B', 'C'], { A: 2, B: 1, C: 4 });
    const last = spans[spans.length - 1];
    expect(last.to).toBe(expandProfileOrder(['A', 'B', 'C'], { A: 2, B: 1, C: 4 }).length);
  });
});

describe('hasAdjacentRepeats', () => {
  it('spots a block sitting next to itself', () => {
    expect(hasAdjacentRepeats(['A', 'A', 'B'])).toBe(true);
    expect(hasAdjacentRepeats(['A', 'B', 'A'])).toBe(false);
    expect(hasAdjacentRepeats(['A'])).toBe(false);
  });
});

describe('profileMatrix', () => {
  it('draws the block diagram itself', () => {
    expect(formatMatrix(profileMatrix(['A', 'B', 'A'], ['A', 'B']))).toBe('X.X\n.X.');
  });

  it('is square and diagonal when the two orders match', () => {
    expect(formatMatrix(profileMatrix(['A', 'B', 'C'], ['A', 'B', 'C']))).toBe('X..\n.X.\n..X');
  });
});
