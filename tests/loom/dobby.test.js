import { describe, it, expect } from 'vitest';
import { CapacityError, DraftError, InvalidArgument } from '../../src/util/errors.js';
import { parseMatrix, formatMatrix, matrixEquals } from '../../src/util/matrix.js';
import { twillWeave } from '../../src/weave/twill.js';
import { warpFaceRatio } from '../../src/draft/drawdown.js';
import {
  DOBBY_ACTIONS,
  lagFromShafts,
  shaftsOnLag,
  chainFromLiftplan,
  liftplanFromChain,
  pegCount,
  chainLength,
  chainRepeat,
  sameChain,
  repeatedLags,
  assertChainFitsBarrel,
  shedlessLags,
  renderChain,
  assertChainWeaves,
} from '../../src/loom/dobby.js';

describe('lagFromShafts', () => {
  it('pegs a hole for each lifted shaft', () => {
    expect(lagFromShafts(6, [1, 4])).toEqual([1, 0, 0, 1, 0, 0]);
  });

  it('leaves a lag bare when nothing lifts', () => {
    expect(lagFromShafts(4, [])).toEqual([0, 0, 0, 0]);
  });

  it('refuses a shaft the dobby has no hole for', () => {
    expect(() => lagFromShafts(4, [5])).toThrow(CapacityError);
    expect(() => lagFromShafts(4, [0])).toThrow(InvalidArgument);
  });
});

describe('shaftsOnLag', () => {
  it('reads the pegs back on a positive dobby', () => {
    expect(shaftsOnLag([1, 0, 0, 1])).toEqual([1, 4]);
  });

  it('reads the other shafts on a negative one', () => {
    expect(shaftsOnLag([1, 0, 0, 1], { action: 'negative' })).toEqual([2, 3]);
  });

  it('names the two actions', () => {
    expect(DOBBY_ACTIONS).toEqual(['positive', 'negative']);
    expect(() => shaftsOnLag([1], { action: 'half' })).toThrow(InvalidArgument);
  });

  it('round-trips through lagFromShafts', () => {
    expect(shaftsOnLag(lagFromShafts(8, [2, 5, 7]))).toEqual([2, 5, 7]);
  });
});

describe('chainFromLiftplan', () => {
  it('lays the liftplan out as lags', () => {
    const chain = chainFromLiftplan(twillWeave('2/2'));
    expect(renderChain(chain)).toBe('oo..\n.oo.\n..oo\no..o');
  });

  it('pads to a wider dobby without moving the pegs', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n.X'), { shafts: 4 });
    expect(renderChain(chain)).toBe('o...\n.o..');
  });

  it('refuses a plan wider than the dobby', () => {
    expect(() => chainFromLiftplan(parseMatrix('XXXX'), { shafts: 2 })).toThrow(CapacityError);
  });
});

describe('liftplanFromChain', () => {
  it('is the chain itself on a positive dobby', () => {
    const chain = chainFromLiftplan(twillWeave('2/2'));
    expect(matrixEquals(liftplanFromChain(chain), chain)).toBe(true);
  });

  it('is the cell by cell reverse on a negative one', () => {
    const chain = chainFromLiftplan(twillWeave('3/1'));
    const negative = liftplanFromChain(chain, { action: 'negative' });
    expect(formatMatrix(negative)).toBe('...X\nX...\n.X..\n..X.');
    expect(warpFaceRatio(negative)).toBe(1 - warpFaceRatio(chain));
  });

  it('lifts three shafts where the positive reading lifts one', () => {
    const chain = chainFromLiftplan(twillWeave('1/3'));
    expect(shaftsOnLag(chain[0]).length).toBe(1);
    expect(shaftsOnLag(chain[0], { action: 'negative' }).length).toBe(3);
  });

  it('hands back a copy, so the chain is not edited through it', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n.X'));
    liftplanFromChain(chain)[0][0] = 0;
    expect(chain[0][0]).toBe(1);
  });
});

describe('pegCount and chainLength', () => {
  it('count the pegs and the lags', () => {
    const chain = chainFromLiftplan(twillWeave('2/2'));
    expect(pegCount(chain)).toBe(8);
    expect(chainLength(chain)).toBe(4);
  });

  it('a 3/1 twill needs half as many pegs again as a 2/2', () => {
    expect(pegCount(chainFromLiftplan(twillWeave('3/1')))).toBe(12);
  });
});

describe('chainRepeat', () => {
  it('finds the shortest chain that weaves the same cloth', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n.X\nX.\n.X'));
    expect(chainRepeat(chain)).toBe(2);
  });

  it('is the whole chain when nothing repeats', () => {
    expect(chainRepeat(chainFromLiftplan(twillWeave('2/2')))).toBe(4);
  });
});

describe('sameChain', () => {
  it('is true wherever the barrel was started', () => {
    const chain = chainFromLiftplan(twillWeave('2/2'));
    const moved = [...chain.slice(2), ...chain.slice(0, 2)];
    expect(sameChain(chain, moved)).toBe(true);
  });

  it('is false once a peg moves', () => {
    const chain = chainFromLiftplan(twillWeave('2/2'));
    const changed = chain.map((lag, index) => (index === 0 ? [0, 0, 1, 1] : lag));
    expect(sameChain(chain, changed)).toBe(false);
  });
});

describe('repeatedLags', () => {
  it('groups lags pegged the same way', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n.X\nX.'));
    expect(repeatedLags(chain)).toEqual([[1, 3]]);
  });

  it('is empty when every lag differs', () => {
    expect(repeatedLags(chainFromLiftplan(twillWeave('2/2')))).toEqual([]);
  });
});

describe('assertChainFitsBarrel', () => {
  it('passes a chain the barrel holds', () => {
    const chain = chainFromLiftplan(twillWeave('2/2'));
    expect(assertChainFitsBarrel(chain, { lagCapacity: 40 })).toBe(chain);
  });

  it('says how many lags are over', () => {
    try {
      assertChainFitsBarrel(chainFromLiftplan(twillWeave('2/2')), { lagCapacity: 3 });
      throw new Error('should have thrown');
    } catch (err) {
      expect(err.detail.over).toBe(1);
    }
  });
});

describe('shedlessLags', () => {
  it('finds a bare lag on a positive dobby', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n..'));
    expect(shedlessLags(chain)).toEqual([{ lag: 2, reason: 'nothing lifted' }]);
  });

  it('swaps the two cases on a negative dobby', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n..'));
    expect(shedlessLags(chain, { action: 'negative' })).toEqual([
      { lag: 2, reason: 'everything lifted' },
    ]);
  });

  it('is quiet on a chain that opens a shed every pick', () => {
    expect(shedlessLags(chainFromLiftplan(twillWeave('2/2')))).toEqual([]);
  });
});

describe('assertChainWeaves', () => {
  it('passes a chain that opens a shed somewhere', () => {
    const chain = chainFromLiftplan(parseMatrix('X.\n..'));
    expect(assertChainWeaves(chain)).toBe(chain);
  });

  it('refuses a chain that never opens one', () => {
    expect(() => assertChainWeaves(chainFromLiftplan(parseMatrix('..\n..')))).toThrow(DraftError);
  });
});
