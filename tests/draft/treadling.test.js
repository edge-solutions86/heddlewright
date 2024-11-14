import { describe, it, expect } from 'vitest';
import { DraftError, InvalidArgument } from '../../src/util/errors.js';
import { pointDraw } from '../../src/draft/threading.js';
import {
  normaliseTreadling,
  assertNoBlankPicks,
  straightTreadling,
  pointTreadling,
  trompAsWrit,
  advancingTreadling,
  boustrophedon,
  treadlingRepeat,
  treadleUsage,
  unusedTreadles,
  extendTreadling,
} from '../../src/draft/treadling.js';

describe('normaliseTreadling', () => {
  it('wraps a bare number into a single-treadle pick', () => {
    expect(normaliseTreadling([1, 2, 3], 4)).toEqual([[1], [2], [3]]);
  });

  it('sorts and deduplicates a two-treadle pick', () => {
    expect(normaliseTreadling([[3, 1, 3]], 4)).toEqual([[1, 3]]);
  });

  it('keeps a blank pick rather than dropping it', () => {
    expect(normaliseTreadling([[], [1]], 4)).toEqual([[], [1]]);
  });

  it('rejects a treadle the loom does not have', () => {
    expect(() => normaliseTreadling([1, 9], 4)).toThrow(/treadling\[1\]\[0\]/);
  });
});

describe('assertNoBlankPicks', () => {
  it('passes a treadling with every pick pressed', () => {
    expect(assertNoBlankPicks([1, 2], 4)).toEqual([[1], [2]]);
  });

  it('names the blank picks by 1-based position', () => {
    try {
      assertNoBlankPicks([[1], [], [2], []], 4);
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(DraftError);
      expect(err.detail.picks).toEqual([2, 4]);
    }
  });
});

describe('straightTreadling', () => {
  it('walks the treadles in order', () => {
    expect(straightTreadling(4)).toEqual([[1], [2], [3], [4]]);
    expect(straightTreadling(3, 5)).toEqual([[1], [2], [3], [1], [2]]);
  });

  it('starts elsewhere and runs backwards when asked', () => {
    expect(straightTreadling(4, 4, { start: 2 })).toEqual([[2], [3], [4], [1]]);
    expect(straightTreadling(4, 4, { direction: -1 })).toEqual([[1], [4], [3], [2]]);
  });
});

describe('pointTreadling', () => {
  it('turns without repeating the end pick', () => {
    expect(pointTreadling(4)).toEqual([[1], [2], [3], [4], [3], [2]]);
  });

  it('mirrors the shape of a point draw on the same count', () => {
    expect(pointTreadling(5).map((pick) => pick[0])).toEqual(pointDraw(5));
  });

  it('needs two treadles', () => {
    expect(() => pointTreadling(1)).toThrow(DraftError);
  });
});

describe('trompAsWrit', () => {
  it('treadles in threading order', () => {
    expect(trompAsWrit([1, 2, 3, 2])).toEqual([[1], [2], [3], [2]]);
  });

  it('takes a shaft to treadle map for a loom tied differently', () => {
    const treadling = trompAsWrit([1, 2, 3, 4], { shaftToTreadle: (shaft) => 5 - shaft });
    expect(treadling).toEqual([[4], [3], [2], [1]]);
  });

  it('passes the end index to the map', () => {
    const seen = [];
    trompAsWrit([1, 1, 1], {
      shaftToTreadle: (shaft, index) => {
        seen.push(index);
        return shaft;
      },
    });
    expect(seen).toEqual([0, 1, 2]);
  });

  it('rejects a map that returns something off the loom', () => {
    expect(() => trompAsWrit([1], { shaftToTreadle: () => 0 })).toThrow(InvalidArgument);
  });
});

describe('advancingTreadling', () => {
  it('rolls each block on by the advance', () => {
    expect(advancingTreadling(8, 8, { blockSize: 4, advance: 1 })).toEqual([
      [1],
      [2],
      [3],
      [4],
      [2],
      [3],
      [4],
      [5],
    ]);
  });

  it('an advance equal to the block size is a straight treadling', () => {
    expect(advancingTreadling(4, 8, { blockSize: 4, advance: 4 })).toEqual(straightTreadling(4, 8));
  });
});

describe('boustrophedon', () => {
  it('runs back down without repeating the turning picks', () => {
    expect(boustrophedon([[1], [2], [3]])).toEqual([[1], [2], [3], [2]]);
  });

  it('repeats the turns when asked', () => {
    expect(boustrophedon([[1], [2], [3]], { repeatTurn: true })).toEqual([
      [1],
      [2],
      [3],
      [3],
      [2],
      [1],
    ]);
  });

  it('leaves the input alone', () => {
    const source = [[1], [2]];
    boustrophedon(source);
    expect(source).toEqual([[1], [2]]);
  });
});

describe('treadlingRepeat', () => {
  it('finds the shortest block that rebuilds the sequence', () => {
    expect(treadlingRepeat([1, 2, 1, 2, 1, 2], 4)).toBe(2);
    expect(treadlingRepeat(straightTreadling(4, 8), 4)).toBe(4);
  });

  it('is the whole length when nothing repeats', () => {
    expect(treadlingRepeat([1, 2, 3], 4)).toBe(3);
  });

  it('sees a two-treadle pick as one symbol', () => {
    expect(treadlingRepeat([[1, 2], [3], [1, 2], [3]], 4)).toBe(2);
  });
});

describe('treadle usage', () => {
  it('counts the picks each treadle carries', () => {
    expect(treadleUsage([1, 1, 2], 4)).toEqual([2, 1, 0, 0]);
  });

  it('counts both treadles of a doubled pick', () => {
    expect(treadleUsage([[1, 2]], 2)).toEqual([1, 1]);
  });

  it('lists the treadles never pressed', () => {
    expect(unusedTreadles([1, 2], 4)).toEqual([3, 4]);
    expect(unusedTreadles([1, 2, 3, 4], 4)).toEqual([]);
  });
});

describe('extendTreadling', () => {
  it('repeats to the requested pick count', () => {
    expect(extendTreadling([[1], [2]], 5)).toEqual([[1], [2], [1], [2], [1]]);
  });

  it('accepts bare numbers and normalises them', () => {
    expect(extendTreadling([1, 2], 3)).toEqual([[1], [2], [1]]);
  });

  it('gives each pick its own array, so editing one leaves the rest alone', () => {
    const extended = extendTreadling([[1]], 3);
    extended[0].push(2);
    expect(extended[1]).toEqual([1]);
  });
});
