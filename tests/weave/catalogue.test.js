import { describe, it, expect, afterEach } from 'vitest';
import { NotFound, InvalidArgument } from '../../src/util/errors.js';
import { plainWeave } from '../../src/weave/plain.js';
import { diagonalScore } from '../../src/weave/crepe.js';
import {
  listWeaves,
  families,
  weavesInFamily,
  weaveByName,
  measure,
  measureAll,
  weavesWithin,
  addWeave,
  removeWeave,
} from '../../src/weave/catalogue.js';

afterEach(() => {
  removeWeave('shed special');
});

describe('listWeaves', () => {
  it('lists them in name order', () => {
    const names = listWeaves().map((weave) => weave.name);
    expect(names).toEqual([...names].sort());
  });

  it('gives every weave a family and a builder', () => {
    for (const weave of listWeaves()) {
      expect(typeof weave.family).toBe('string');
      expect(typeof weave.build).toBe('function');
    }
  });

  it('groups into the families the report uses', () => {
    expect(families()).toEqual(['block', 'crepe', 'figured', 'plain', 'satin', 'twill']);
    expect(weavesInFamily('satin').length).toBe(3);
    expect(weavesInFamily('nothing')).toEqual([]);
  });

  it('carries the block weaves as woven cloth rather than as drafts', () => {
    const overshot = measure('overshot ABCD');
    expect(overshot.picks).toBe(16);
    expect(overshot.ends).toBe(16);
    expect(overshot.warpFace).toBe(0.5);
  });

  it('carries the crepes, and reads them as having no long line', () => {
    for (const weave of weavesInFamily('crepe')) {
      expect(measure(weave.name).longestFloat).toBeLessThanOrEqual(3);
    }
  });
});

describe('weaveByName', () => {
  it('builds one repeat', () => {
    expect(weaveByName('plain')).toEqual(plainWeave());
  });

  it('names what it does have when it cannot find one', () => {
    try {
      weaveByName('gauze');
      throw new Error('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(NotFound);
      expect(err.detail.known).toContain('plain');
    }
  });

  it('builds a fresh repeat each time, so a caller cannot edit the catalogue', () => {
    const first = weaveByName('plain');
    first[0][0] = 0;
    expect(weaveByName('plain')[0][0]).toBe(1);
  });
});

describe('measure', () => {
  it('reads plain weave as the firmest thing in the book', () => {
    expect(measure('plain')).toMatchObject({
      family: 'plain',
      picks: 2,
      ends: 2,
      warpFace: 0.5,
      longestFloat: 1,
      interlacement: 1,
      firmness: 'firm',
    });
  });

  it('reads a satin as long floating and open', () => {
    const satin = measure('satin 8');
    expect(satin.longestFloat).toBe(7);
    expect(satin.warpFace).toBe(0.875);
    expect(satin.firmness).toBe('open');
  });

  it('reads a warp faced twill as showing more warp', () => {
    expect(measure('twill 3/1').warpFace).toBe(0.75);
    expect(measure('twill 1/3').warpFace).toBe(0.25);
  });
});

describe('measureAll', () => {
  it('measures every weave in the catalogue', () => {
    expect(measureAll().length).toBe(listWeaves().length);
  });

  it('never reports a float longer than the repeat it came from', () => {
    for (const row of measureAll()) {
      expect(row.longestFloat).toBeLessThanOrEqual(Math.max(row.picks, row.ends));
    }
  });

  it('reports an interlacement between nothing and one for every weave', () => {
    for (const row of measureAll()) {
      expect(row.interlacement).toBeGreaterThan(0);
      expect(row.interlacement).toBeLessThanOrEqual(1);
    }
  });
});

describe('the families', () => {
  it('puts every weave in exactly one family', () => {
    const byName = new Map();
    for (const weave of listWeaves()) {
      expect(byName.has(weave.name)).toBe(false);
      byName.set(weave.name, weave.family);
    }
    expect(byName.size).toBe(listWeaves().length);
  });

  it('has every family holding at least one weave', () => {
    for (const family of families()) {
      expect(weavesInFamily(family).length).toBeGreaterThan(0);
    }
  });

  it('accounts for every weave across the families', () => {
    const counted = families().reduce((total, family) => total + weavesInFamily(family).length, 0);
    expect(counted).toBe(listWeaves().length);
  });

  it('reads the figured family as long floating', () => {
    for (const weave of weavesInFamily('figured')) {
      expect(measure(weave.name).longestFloat).toBeGreaterThan(2);
    }
  });

  it('reads the block family as balanced cloth', () => {
    for (const weave of weavesInFamily('block')) {
      const reading = measure(weave.name);
      expect(reading.warpFace).toBeGreaterThan(0.4);
      expect(reading.warpFace).toBeLessThan(0.6);
    }
  });
});

describe('weavesWithin', () => {
  it('keeps only the weaves inside a float limit, firmest first', () => {
    const found = weavesWithin(2);
    expect(found.every((row) => row.longestFloat <= 2)).toBe(true);
    expect(found[0].name).toBe('plain');
  });

  it('lets everything through on a limit nothing exceeds', () => {
    expect(weavesWithin(99).length).toBe(listWeaves().length);
  });

  it('keeps nothing on a limit of nothing', () => {
    expect(weavesWithin(0)).toEqual([]);
  });
});

describe('addWeave and removeWeave', () => {
  it('takes a weave a shed adds and measures it like any other', () => {
    addWeave({ name: 'shed special', family: 'plain', build: () => plainWeave() });
    expect(measure('shed special').firmness).toBe('firm');
    expect(listWeaves().some((weave) => weave.name === 'shed special')).toBe(true);
  });

  it('takes it back out again', () => {
    addWeave({ name: 'shed special', family: 'plain', build: () => plainWeave() });
    expect(removeWeave('shed special')).toBe(true);
    expect(() => weaveByName('shed special')).toThrow(NotFound);
  });

  it('refuses an entry with no builder', () => {
    expect(() => addWeave({ name: 'shed special', family: 'plain' })).toThrow(NotFound);
    expect(() => addWeave({ name: '', family: 'plain', build: () => [] })).toThrow(InvalidArgument);
  });
});

describe('the new families read as their own kind of cloth', () => {
  it('reads the crepes as scattered rather than as lines', () => {
    // A crepe that scored a diagonal would be a twill wearing a crepe's name,
    // which is the one thing that can go wrong when a crepe is built by
    // overlaying weaves, so it is checked here rather than trusted.
    for (const weave of weavesInFamily('crepe')) {
      const cloth = weaveByName(weave.name);
      expect(diagonalScore(cloth)).toBeLessThan(1);
    }
  });

  it('reads mock leno as an open cloth beside a plain one', () => {
    const leno = measure('mock leno 3');
    const plain = measure('plain');
    expect(leno.longestFloat).toBeGreaterThan(plain.longestFloat);
    expect(leno.firmness).not.toBe(plain.firmness);
  });

  it('builds every block weave on an even number of ends and picks', () => {
    // Blocks come in pairs, so neither side of the repeat can be odd. Summer
    // and winter is 12 by 24 rather than square, which is why the check is on
    // the parity and not on the two being equal.
    for (const weave of weavesInFamily('block')) {
      const cloth = weaveByName(weave.name);
      expect(cloth.length % 2).toBe(0);
      expect(cloth[0].length % 2).toBe(0);
    }
  });

  it('sorts the short floating weaves firmest first', () => {
    const within = weavesWithin(3);
    expect(within[0].name).toBe('plain');
    for (const row of within) expect(row.longestFloat).toBeLessThanOrEqual(3);
    for (let i = 1; i < within.length; i += 1) {
      expect(within[i - 1].interlacement).toBeGreaterThanOrEqual(within[i].interlacement);
    }
  });

  it('takes the crepes and mock leno in under that limit, and leaves satin out', () => {
    const names = weavesWithin(3).map((row) => row.name);
    expect(names).toContain('crepe 8');
    expect(names).toContain('mock leno 2');
    expect(names.some((name) => name.startsWith('satin'))).toBe(false);
  });
});
